# Şifreleme Tasarımı — Güvenli Paylaşım

> **Durum:** v1 — 2026-11-05 (prototip). Kod bu dokümana uyar; tasarım değişecekse önce bu doküman güncellenir.
> **v2 (2026-11-06, onaylandı, henüz kodlanmadı):** roller, birimler ve e-posta kalkıyor; kimlik telefon, ilk temas
> davet koduyla. Bkz. [§7](#7-v2-herkes-kullanıcı-telefon-ve-davet-kodu). §1–§6 kodlanana kadar mevcut kodu anlatır.
> İstemci: `desktop/src/main/java/com/desktop/crypto/`. Sunucu: `server/.../modules/key`, `server/.../modules/document`.

## 1. Neden değiştirdik? (eski sistemle karşılaştırma)

Eski "Güvenli Bilgi Paylaşım Sistemi" (kullanıcı kılavuzu, Haziran 2016) "asimetrik şifreleme" kullandığını söyler,
ancak kılavuzdaki akış **parola tabanlı simetrik şifrelemedir**:

| Konu | Eski sistem (kılavuza göre) | Güvenli Paylaşım |
|---|---|---|
| Anahtar paylaşımı | Gönderen iki anahtar yazar; anahtarlar alıcılara **SMS ile açık metin** gider | Paylaşılan parola yoktur; her kişinin kendi anahtar çifti vardır |
| Anahtarın gücü | Kullanıcının yazdığı değer (kılavuzdaki örnek: `123`, `abc`) | Her belgeye rastgele 256 bit AES anahtarı |
| Alıcıya özel erişim | Aynı anahtarlar seçilen bütün birimlere gider | Belge anahtarı her alıcının açık anahtarına ayrı ayrı sarmalanır |
| Gönderenin kimliği | Kanıtlanamaz | Ed25519 imzası; sahte ya da değiştirilmiş belge açılmaz |
| Sunucu ele geçirilirse | Veritabanı + SMS kayıtları içeriği açmaya yeter | Sunucuda sadece şifreli veri vardır; açacak anahtar sunucuda yoktur |
| Parola | Sunucuya gönderilir | Bilgisayardan çıkmaz; sunucuya ondan türetilen bir değer gider |
| Silme | "Okudum ve sil" isteğe bağlı | Son alıcı da silince şifreli içerik veritabanından kalıcı olarak silinir |

Aynı karşılaştırma uygulamada **"Neden Güvenli?"** ekranında gösterilir (`WhyView`).

## 2. Parola: sunucu gerçek parolayı görmez

```
master    = PBKDF2-HMAC-SHA256(parola, tuz = "guvenli-paylasim/v1|" + e-posta, 600.000 tur, 256 bit)
authSecret = Base64url(HMAC-SHA256(master, "auth"))            → sunucuya "parola" olarak gider (bcrypt'lenir)
kek        = HMAC-SHA256(master, "key-encryption")             → özel anahtarları şifreler, bilgisayardan çıkmaz
```

- Sunucu `authSecret`'tan `kek`'i hesaplayamaz (farklı HMAC etiketleri).
- İstemci `PasswordKeys`, sunucu `AuthSecrets` aynı hesabı yapar. İkisini bağlayan test vektörü vardır
  (`CryptoTest.authSecretMatchesServerDerivation`, `AuthSecretsTest`); biri değişirse build kırılır.
- Tuz e-posta olduğu için **e-posta değiştirilemez** (`user_email_immutable`).
- Parola kuralları (en az 10 karakter, harf ve rakam) istemcide uygulanır; sunucu parolanın kendisini görmez.
- İlk admin ve demo kullanıcıları migration'da aynı türetmeyle oluşturulur (`V002`, `V007`).

## 3. Kullanıcı anahtarları

- İlk girişte **bu bilgisayarda** üretilir: şifreleme için **X25519**, imza için **Ed25519**.
- Sunucuya gidenler (`user_keys`):
  - açık anahtarlar (X.509, Base64),
  - özel anahtar paketi: `0x01 | AES-256-GCM(kek, [PKCS#8 X25519][PKCS#8 Ed25519], AAD = "gps-private-keys/v1|" + e-posta)`.
- Girişte paket `kek` ile açılır ve açılan özel anahtarların kayıtlı açık anahtarlarla eşleştiği kanıtlanır
  (imzala-doğrula, sarmala-aç). Özel anahtarlar sadece bellekte tutulur, diske yazılmaz.
- **Parola değişince** özel anahtarlar yeni `kek` ile yeniden şifrelenir; parola ve paket aynı istekte, aynı
  transaction'da kaydedilir. Açık anahtarlar değişmez, eski belgeler açılmaya devam eder.
- **Parmak izi**: `SHA-256(açık şifreleme anahtarı || açık imza anahtarı)`'nın ilk 16 baytı, 4'lü gruplar halinde.
  Kullanıcılar bunu telefonda karşılaştırarak sunucunun araya sahte anahtar koymadığını doğrulayabilir.

## 4. Belge

### 4.1 Şifreleme ve imza
```
K        = rastgele 256 bit
payload  = 0x01 | AES-256-GCM(K, içerik, AAD = "gps-doc/v1")
imza     = Ed25519(gönderenin özel imza anahtarı, "gps-doc-sig/v1|" || payload)
```
İçerik (`DocumentContent`): tür, **dosya adı**, MIME türü ve veri. Dosya adı da şifrelidir; sunucu bilmez.

### 4.2 Anahtarın alıcılara sarmalanması (ECIES benzeri)
Her alıcı için:
```
e          = yeni geçici X25519 anahtar çifti
paylaşılan = X25519(e.özel, alıcı.açık)
sarmaKey   = HKDF-SHA256(paylaşılan, tuz = e.açık || alıcı.açık, bilgi = "gps-wrap/v1", 32 bayt)
sarmalı    = 0x01 | len(e.açık) | e.açık | AES-256-GCM(sarmaKey, K, AAD = SHA-256(payload))
```
AAD sarmalanmış anahtarı **o belgeye** bağlar; başka bir belgenin anahtarıyla değiştirilemez.

### 4.3 Açma
1. Önce imza doğrulanır. Geçersizse içerik **hiç çözülmez**.
2. Gönderenin anahtarları sonradan sıfırlanmışsa imza doğrulanamaz; içerik açılır ama kullanıcıya açıkça uyarı
   gösterilir (`SignatureStatus.UNKNOWN`).
3. Sarmalı anahtar alıcının özel anahtarıyla açılır, içerik çözülür.

### 4.4 Alıcılar
- Belge **birimlere** gönderilir; birimdeki, anahtarı olan her aktif kullanıcı ayrı alıcıdır
  (`GET /api/keys/recipients?unitIds=`).
- Henüz ilk girişini yapmamış (anahtarı olmayan) kullanıcılar listede çıkmaz; belge onlara gönderilemez.
- Gönderen kendi belgesini açamaz (anahtar ona sarmalanmaz); "Gönderilenler"de sadece okunma durumu görünür.

### 4.5 Silme
- "Okudum ve sil": alıcının sarmalı anahtarı silinir, satır `deleted_at` alır.
- Son alıcı da silince `documents.payload` ve `signature` **null** olur (`purged_at`). Kim, kime, ne zaman bilgisi
  denetim için kalır. `documents` ve `document_recipients` bilerek soft delete kullanmaz.

## 5. Sunucunun gördükleri / göremedikleri

| Görür | Göremez |
|---|---|
| Kimin kime ne zaman belge gönderdiği | Belgenin içeriği ve dosya adı |
| Şifreli içeriğin boyutu, türü (yazı/dosya) | Belge anahtarları |
| Açık anahtarlar | Özel anahtarlar |
| Okundu / silindi bilgisi | Parolalar |

## 6. Bilinen sınırlar ve bilinçli kararlar

- **Parola sıfırlama = eski belgeler kaybolur.** Yönetici parolayı sıfırlayınca kullanıcının anahtarları silinir
  (`must_change_password = true`); kullanıcı yeni anahtar üretir, eski belgelerini açamaz. Uçtan uca şifrelemenin
  doğal sonucudur.
- **Anahtar dizinine güven.** İstemci açık anahtarları sunucudan alır. Kötü niyetli bir sunucu sahte anahtar
  verebilir; buna karşı parmak izi karşılaştırması vardır. Otomatik anahtar şeffaflığı (key transparency) kapsam dışı.
- **Meta veri** (kim kime ne zaman) şifrelenmez.
- **LDAP'a geçiş.** LDAP'ta parola kurumun dizinindedir; `authSecret` yaklaşımı kullanılamaz. O zaman özel
  anahtarlar Windows DPAPI ya da akıllı kart / e-imza ile korunmalıdır. Kripto katmanı (`UserKeys.seal/open`)
  bunun için tek değişiklik noktasıdır.
- **SMS.** SMS'te hiçbir anahtar ya da parola yoktur; sadece "yeni belgeniz var" bildirimi gider. SMS servisi
  bağlanana kadar `SimulatedSmsGateway` mesajı loglar, istemci bildirimi uygulama içinde gösterir.
- Bütün algoritmalar JDK 25'in içindedir (X25519, Ed25519, AES-GCM, PBKDF2, HKDF — `javax.crypto.KDF`);
  harici kripto kütüphanesi yoktur.

## 7. v2: herkes kullanıcı, telefon ve davet kodu

> Planlandı (2026-11-06). Kodlanınca §2–§6'daki ilgili kısımlar bu bölüme göre güncellenir.

### 7.1 Amaç ve kapsam

Kimsenin açamayacağı bir kutuya dosya ya da mesaj konur; kutu sadece alıcının bilgisayarında açılır. Bunun için:

- **Rol yok.** Herkes `USER`. Yönetici, süper yönetici, owner rolleri; yönetim ekranları ve API'leri
  (`/api/users` yönetimi, `/api/units`, rol tabanlı menüler) silinir.
- **Birim yok.** Alıcı bir kişidir; birime gönderim kalkar.
- **E-posta yok.** Kayıt ve giriş telefonla yapılır. E-posta hiçbir yerde istenmez ve tutulmaz.
- Sunucu internete açık çalışacak şekilde tasarlanır (tek ağa bağlı değildir).

### 7.2 Kayıt ve telefon doğrulama

- Kayıtta istenenler: **ad soyad, cep telefonu, parola**.
- Telefon 10 hane girilir: `5XXXXXXXXX` (başında `0` ve `+90` yok; doğrulama `^5\d{9}$`).
  Veritabanında E.164 olarak tutulur: `+905XXXXXXXXX`. Benzersizdir.
- Numara **SMS koduyla doğrulanmadan** hesap açılmaz. Doğrulama olmasaydı biri başkasının numarasıyla kayıt olup
  o numaraya bırakılan kutuları almaya çalışabilirdi.
  - `POST /api/auth/register/start {phone}` → 6 haneli SMS kodu (5 dk geçerli, en fazla 3 deneme).
  - `POST /api/auth/register/complete {phone, smsCode, fullName, authSecret, keyBundle}` → hesap açılır.
- Anahtarlar **kayıtta** üretilir (v1'de ilk girişteydi). Böylece "anahtarı olmayan kullanıcı" durumu kalkar.
- Doğrulama kodu **WhatsApp** ile gider: Vonage Messages API (`SmsGateway` arayüzünün yeni uygulaması). Bu belgede
  "SMS kodu" bu doğrulama mesajı anlamındadır. Demo'da Vonage sandbox kullanılır (alıcı numara önce sandbox'a
  katılır); gerçek kullanımda WhatsApp Business hesabı ve Meta onaylı doğrulama şablonu gerekir. Erişim bilgileri
  sadece ortam değişkeninden okunur, repoya girmez. Dev'de `SimulatedSmsGateway` kalır.
- `register/start` numaranın zaten kayıtlı olup olmadığını **söylemez**; kayıtlı numaraya SMS gitmez ama yanıt aynıdır.

### 7.3 Parola türetme (§2 değişikliği)

Tuz e-posta yerine telefon olur:
```
master = PBKDF2-HMAC-SHA256(parola, tuz = "guvenli-paylasim/v2|" + "+905XXXXXXXXX", 600.000 tur, 256 bit)
```
`authSecret` ve `kek` §2'deki gibi türetilir. Tuz telefon olduğu için **telefon numarası değiştirilemez** (v2'de).
İleride değiştirilebilir: istemci parolayı bildiği için yeni numarayla `authSecret`'ı ve özel anahtar paketini yeniden
üretebilir; bu ayrı bir iş olarak bırakılır.

- **Parola şartı:** en az 12 karakter; yaygın parola ve sözlük listesiyle (Türkçe dahil) kontrol. Parola sunucuya
  gitmediği için kontrol istemcide yapılır (Security S2).
- **Sunucuda ikinci kilit:** özel anahtar paketi ve `authSecret` özeti, veritabanına yazılmadan önce veritabanı
  dışında tutulan bir sunucu anahtarıyla AES-256-GCM ile bir kez daha şifrelenir. Sadece DB dökümünü ele geçiren
  biri parola deneyemez (Security S2).

### 7.4 Giriş

- Telefon + parola. Sunucuya `authSecret` gider (§2), parola bilgisayardan çıkmaz.
- **TOTP** (Authenticator uygulaması) ikinci adım olarak eklenecek; v2'nin ilk parçasında yok. SMS ile giriş kodu
  kullanılmaz (ücretli ve SIM kopyalamaya açık).

### 7.5 Parola sıfırlama

- Telefon + SMS kodu ile yeni parola belirlenir. İstemci **yeni anahtar çifti** üretir.
- Eski özel anahtarlar eski parolayla kilitliydi, yeni anahtarla açılamaz. Bu yüzden sıfırlamada:
  - kullanıcıya gelmiş bütün kutular silinir (sarmalı anahtarlar silinir, son alıcıysa içerik purge edilir, §4.5),
  - bütün oturumları kapatılır,
  - **kişi listesi kalır**,
  - kullanıcının telefonuna bırakılmış, henüz **kodu girilmemiş davetler kalır** (onlar kodla kilitli, §7.6).
- Kişilerinin listesinde **doğrulanmamış** olur; ona göndermek için yeni davet kodu gerekir (§7.8).

### 7.6 İlk temas: davet kodu

Birbirini kişi olarak eklememiş iki kişi arasında ilk kutu **davet kodu** ile gider. Kod hem izin hem anahtardır.

**Gönderen:**
1. Dosyayı ya da mesajı seçer, alıcının telefonunu girer, geçerlilik süresini seçer: 1 saat, 1 gün, 3 gün, 7 gün
   (en fazla 7 gün).
2. İstemci rastgele **16 karakterlik kod** üretir (Crockford Base32, 80 bit), ör. `K7M2-Q9XA-4TPB-W8RC`.
3. Kutu bilgisayarda kodla kilitlenir:
   ```
   K           = rastgele 256 bit; payload ve imza §4.1'deki gibi
   kodKey      = HKDF-SHA256(kod, tuz = "", bilgi = "gps-invite-key/v1", 32 bayt)
   doğrulayıcı = HKDF-SHA256(kod, tuz = "", bilgi = "gps-invite-verify/v1", 32 bayt)
   sarmalı     = 0x01 | AES-256-GCM(kodKey, K || gönderenin parmak izi, AAD = SHA-256(payload))
   ```
   Ayrıca kabul mührünü doğrulamak için (aşağıda, alıcı adım 5):
   ```
   mühürKey     = HKDF-SHA256(kod, tuz = "", bilgi = "gps-invite-mac/v1", 32 bayt)
   kendiSarmalı = mühürKey'in gönderenin kendi açık anahtarına sarmalanmış hali (§4.2)
   ```
   Gönderenin bilgisayarı kodu saklamaz; kabul günler sonra gelse bile `mühürKey`'i kendi özel anahtarıyla açar.
4. Sunucuya gidenler: alıcı telefonu, son geçerlilik zamanı, `payload`, imza, `sarmalı`, `kendiSarmalı`,
   `SHA-256(doğrulayıcı)`. Kodun kendisi, `kodKey` ve `mühürKey` sunucuya **gitmez**.
5. Kod gönderenin ekranında gösterilir (SMS ile gönderilmez). Gönderen kodu kendi kanalıyla (yüz yüze, telefon,
   başka bir mesajlaşma uygulaması) alıcıya iletir.

**Alıcı:**
1. Süre dolmadan kayıt olur (ya da zaten kayıtlıdır) ve **"Kod ile al"** ekranına kodu girer.
2. İstemci `doğrulayıcı`'yı hesaplayıp sunucuya gönderir. Sunucu sadece **bu telefona** bırakılmış, süresi dolmamış
   davetler arasında `SHA-256(doğrulayıcı)` eşleşmesi arar.
3. Eşleşirse sunucu kutuyu verir; istemci `kodKey` ile `K`'yı açar, imzayı doğrular, gönderenin sunucudan gelen açık
   anahtarlarının parmak izini kutunun içindekiyle karşılaştırır. Tutmazsa kutu açılmaz (sunucu araya sahte anahtar
   koymuş demektir).
4. İstemci `K`'yı kendi açık anahtarına yeniden sarmalar (§4.2) ve kaydeder; davetteki `sarmalı` silinir.
5. İstemci **kabul mührü** üretip sunucuya bırakır:
   ```
   mühür = HMAC-SHA256(mühürKey, "gps-invite-accept/v1" || gönderenin parmak izi || alıcının parmak izi)
   ```
6. B'nin listesine A hemen eklenir (A'nın anahtarını kutunun içindeki parmak iziyle doğruladı).

**Gönderen, kabulden sonra (iki yönlü doğrulama):**
1. A'nın istemcisi `kendiSarmalı`'yı açıp `mühürKey`'i alır, B'nin sunucudan gelen açık anahtarlarının parmak iziyle
   mührü yeniden hesaplar ve karşılaştırır.
2. Tutarsa B, A'nın listesine eklenir. Tutmazsa eklenmez ve A'ya "doğrulama başarısız" gösterilir (sunucu B yerine
   sahte anahtar vermiş demektir). Doğrulanana kadar A, B'ye kodsuz gönderemez.

Böylece sunucu iki yönde de araya sahte anahtar koyamaz (Security S3).

**Kurallar:**
- Kod girilene kadar alıcı **hiçbir şey görmez**: sunucu "bekleyen kutunuz var" bilgisini vermez, bildirim göndermez.
- Gönderen, numaranın kayıtlı olup olmadığını öğrenmez; davet oluşturma her iki durumda aynı yanıtı verir.
- Yanlış kod: kullanıcı başına 5 hatalı denemeden sonra 1 saat bekleme.
- Süre dolan davetler (içerikleriyle birlikte) zamanlanmış işle **kalıcı olarak silinir**.
- Kod 80 bit olduğu için sunucuyu ele geçiren biri de `SHA-256(doğrulayıcı)`'dan ya da şifreli kutudan kodu deneme
  yoluyla bulamaz. Bu yüzden kod kısaltılmaz (6 haneli bir kod saniyeler içinde bulunurdu).

### 7.7 Kişiler

- Kod girilince iki yönlü kayıt oluşur: A'nın listesinde B, B'nin listesinde A.
  Kayıtta karşı tarafın **doğrulanmış parmak izi** de tutulur. Kayıt, sahibinin Ed25519 anahtarıyla imzalanır
  (`sahip telefonu || kişi telefonu || parmak izi`); istemci kendi imzası tutmayan kaydı kabul etmez. Böylece kayıt
  sunucuda dursa da sunucu parmak izini değiştiremez.
- Kişiye gönderim kodsuzdur: §4.2'deki gibi alıcının açık anahtarına sarmalanır, kutu doğrudan gelen kutusuna
  düşer, uygulama içi bildirim gider. Sunucu gönderimi sadece kişi kaydı varsa kabul eder.
- Kişilere SMS bildirimi gönderilmez (SMS sadece doğrulama ve sıfırlama içindir).
- Bir taraf kişiyi silerse kayıt **iki yönde de** silinir. Tekrar göndermek için yeni davet kodu gerekir.

### 7.8 Anahtar değişikliği: yeni kod gerekir

Kişinin sunucudan gelen güncel parmak izi, kayıttaki imzalı parmak izinden farklıysa (parolasını sıfırlamıştır ya da
sunucu sahte anahtar veriyordur) kişi **doğrulanmamış** olur:

- İstemci ona kodsuz göndermez; "X'in anahtarı değişti, göndermek için yeni davet kodu gerekir" der.
- Sonraki gönderim §7.6'daki gibi yeni bir davet koduyla yapılır. Kod kabul edilince kayıttaki parmak izi yenisiyle
  değiştirilir ve yeniden imzalanır.
- Sunucu da anahtarı değişmiş kişiye kodsuz gönderimi reddeder (kayıt "doğrulanmamış" işaretlenir).

Gerekçe: sadece "Devam et" düğmesi olan bir uyarı kolayca geçilir. Kod dış kanaldan (yüz yüze, telefonla) gerçek kişiye
iletildiği için, numarasını kopyalayıp (SIM swap) parolasını sıfırlayan biri bunu geçemez (Security S5).

### 7.9 Kötüye kullanım sınırları

| Konu | Sınır |
|---|---|
| SMS (kayıt, sıfırlama) | sadece TR mobil (`^5\d{9}$`); numara başına saatte 3, IP başına saatte 10; sistem geneli günlük tavan (aşılınca SMS durur, alarm) |
| SMS kodu | 5 dk geçerli, 3 deneme |
| Davet oluşturma | kullanıcı başına günde 20 |
| Kod girme | kullanıcı başına 5 hatalı deneme → 1 saat bekleme |
| Giriş | mevcut brute-force koruması (`LoginAttemptService`) |

### 7.10 Sunucunun gördükleri (v2)

| Görür | Göremez |
|---|---|
| Kimin kime (kişiler arası) kutu gönderdiği, zamanı | İçerik ve dosya adı |
| Davetin hangi numaraya bırakıldığı | Davet kodu, `kodKey` |
| Kişi listeleri | Özel anahtarlar, parolalar |
| Ad soyad, telefon | E-posta (tutulmaz) |

### 7.11 Veri geçişi

v1 verisi (e-posta tuzlu parolalar, birimler, roller) v2'ye dönüştürülemez: tuz değiştiği için eski `authSecret`
ve özel anahtar paketleri yeni türetmeyle açılmaz. Henüz prod verisi olmadığı için geçişte şema sıfırlanır
(`DROP SCHEMA guvenlipaylasim CASCADE`); demo verisi (`V007`) v2'ye göre yeniden yazılır.

### 7.12 Kapsam dışı (sonraki parçalar)

- TOTP (§7.4), büyük dosyalar için parçalı yükleme.
- Gizli gönderen ve teslim jetonu (§8.6).
- Karşılıklı teslim (kasa): **yapılmaz.** İçerik doğrulanamaz, sunucuya saldırıya açık durumlu bir karar mantığı
  ekler ve kapsam dışı bırakılan takas sistemine dönüşür (gerekçe: Devam_Rehberi §3).
- Süreli ve açılınca silinen kutu §8'de tasarlandı; v2 ile birlikte kodlanır.

## 8. Gizlilik: sunucu daha kısa süre bilsin

> Konuşuldu (2026-11-06). v2 (§7) ile birlikte kodlanır.

### 8.1 Yaklaşım

Sunucu, kişiler arası gönderimi kabul etmek için gönderenin kim olduğunu bilmek zorundadır (§7.7). Bu yüzden v2'de
"sunucu daha az şey bilsin" yerine **"sunucu daha kısa süre bilsin"** hedeflenir. Kutuyla ilgili her bilgi (içerik,
kimden, kime, ne zaman, okundu mu) kutunun ömrü bitince sunucudan tamamen silinir. Kalıcı denetim kaydı tutulmaz.

### 8.2 Süreli kutu

- Her kutunun bir ömrü vardır. Gönderen şunlardan birini seçer: **1 saat, 1 gün, 7 gün (varsayılan), 30 gün**.
  Süresiz kutu yoktur.
- Ömür gönderim anından başlar. Sunucu `documents.expires_at` alanını tutar ve sınırı (en fazla 30 gün) kendisi
  denetler.
- Süre dolunca zamanlanmış iş, kutu açılmış olsa da olmasa da, `documents` ve `document_recipients` satırlarını
  **kalıcı olarak siler**. İçerikle birlikte kimden, kime ve ne zaman bilgisi de gider.
- Davet kodlu kutu (§7.6):
  - Kod girilmezse kutu, davetin süresi dolunca silinir.
  - Kod girilirse kutu kendi ömrünü yaşar. Bu ömür de gönderim anından sayılır; davet süresi kutunun ömründen uzun
    olamaz.

### 8.3 Açılınca sil

- Gönderen kutu başına **"açılınca sil"** seçeneğini işaretleyebilir. Varsayılan olarak kapalıdır.
- Açma iki adımda yapılır, böylece bağlantı koparsa kutu kaybolmaz:
  1. İstemci kutuyu indirir, şifresini çözer ve imzayı doğrular.
  2. İstemci `POST /api/documents/{id}/opened` ile onay gönderir. Sunucu kutuyu ancak bu onayla siler.
  Onay gelmezse kutu ömrü dolana kadar durur ve alıcı tekrar deneyebilir.
- Birden çok alıcılı kutuda, açan alıcının satırı silinir. Son alıcı da açınca kutu tamamen silinir.
- **Sınır:** Alıcı açılan dosyayı kaydedebilir ya da ekran görüntüsü alabilir. "Açılınca sil", kutunun sunucuda
  kalmayacağı anlamına gelir, kopyalanamayacağı anlamına gelmez. Arayüzde bu açıkça yazılır.
- Seçenek kapalıyken bugünkü "Okudum ve sil" düğmesi kalır. Alıcı silince kendi satırı tamamen silinir (§8.5).

### 8.4 Gönderilenler ve okunma durumu

- Kutu yaşadığı sürece gönderen her alıcı için "okundu" ya da "okunmadı" durumunu görür (`read_at`).
- Kutu silinince (ömrü dolunca, açılınca ya da bütün alıcılar silince) gönderenin listesinden de kalkar.
- Kalıcı gönderim geçmişi istenirse bu, ileride gönderenin kendi bilgisayarında yerel olarak tutulur. Sunucuya
  yazılmaz.

### 8.5 Kayıtların saklanma süresi (§4.5 değişikliği)

| Kayıt | Saklama |
|---|---|
| Kutu, alıcı satırları, `read_at` | Kutunun ömrü kadar, sonra tamamen silinir |
| Alıcının sildiği kutu | Alıcının satırı hemen silinir. Son alıcıysa kutu da hemen silinir |
| Davet (§7.6) | Kod girilince ya da süresi dolunca silinir |
| Giriş denemeleri | 24 saat (bugünkü gibi) |
| Kapatılmış oturumlar | 7 gün (bugünkü gibi) |
| SMS kodları ve sayaçları | Süresi dolunca |

§4.5'teki "`payload` null yapılır, kim-kime-ne zaman denetim için kalır" kuralı v2'de kalkar. `purged_at` ve
`deleted_at` alanlarına gerek kalmaz; satırlar doğrudan silinir.

### 8.6 Gizli gönderen ve teslim jetonu (ertelendi)

Bu yöntemle sunucu kutunun kimden geldiğini hiç bilmez. v2'de yapılmaz, ama v2'nin veri modeli buna geçişi
zorlaştırmayacak şekilde kurulur.

**Tasarım taslağı:**
- Davet kodu girilince her iki taraf, karşı tarafa **kişiye özel** rastgele bir jeton (128 bit) verir. Jeton uçtan
  uca şifreli gider. Sunucu alıcının yanında sadece `SHA-256(jeton)` değerlerini tutar.
- Gönderim oturumsuz yapılır: `POST /api/deliver {alıcı, jeton, kutu}`. Sunucu jetonun özetini alıcının kümesinde
  sabit zamanlı karşılaştırmayla arar. `sender_id` tutulmaz.
- Gönderenin kimliği ve imzası kutunun içindedir. Alıcı imzayı kişi kaydındaki açık anahtarla doğrular.
- Kişi silmek, o jetonun özetini silmek demektir.
- Kişi listesi sunucudan kalkar ve istemcide şifreli tutulur. Anahtar değişikliği uyarısı (§7.8) da istemciye geçer.

**Sınırlar (ertelemenin gerekçesi):**
- IP adresi ve zamanlama, oturumlu istekle oturumsuz gönderimi eşleştirmeye yeter. Tor ya da proxy olmadan bu
  sızıntı kapanmaz.
- Jeton özetinin sunucuya kaydedildiği an, hangi jetonun kime verildiğini ele verebilir.
- Hız sınırı ve kota kullanıcı başına uygulanamaz; jeton ya da alıcı başına uygulanır.
- Gönderilenler listesi sunucudan gelemez; istemci kutu kimliklerini yerel olarak tutar.
- Kazanç sadece kötü niyetli sunucu senaryosunda belirgindir. "Sunucu ele geçirilirse ne bulunur" sorusuna süreli
  kutu ve kayıt silme (§8.2, §8.5) çok daha ucuza benzer bir cevap verir.

**v2 için kural:** Kişi kaydı ayrı bir tabloda tutulur ve gönderim yetkisi tek bir yerde kontrol edilir. Böylece
ileride bu kontrol jeton kontrolüyle değiştirilebilir.

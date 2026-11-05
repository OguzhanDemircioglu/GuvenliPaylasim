# Şifreleme Tasarımı — Güvenli Paylaşım

> **Durum:** v1 — 2026-11-05 (prototip). Kod bu dokümana uyar; tasarım değişecekse önce bu doküman güncellenir.
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

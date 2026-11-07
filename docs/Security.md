# Güvenlik: Tehdit Modeli ve Test Planı

> Konuşuldu (2026-11-06), son güncelleme 2026-11-07. v2 (Crypto_Design §7, §8) bu belgeye göre kodlandı.
> Bir kodlama adımı, buradaki ilgili saldırı testleri yazılıp geçmeden "bitti" sayılmaz.

## 1. İlke

Uygulama kullanıcıya basit görünür; asıl iş arka plandadır. Bütün saldırı testlerine sokulacaktır. Bu yüzden:

- Her özellik için ilk soru: **"Hangi saldırı yüzeyini açıyor, bunu savunabilir miyiz?"** Savunamadığımız özellik
  yapılmaz (ör. kasa, Devam_Rehberi §3).
- Her iddia bu belgede bir saldırgana bağlanır ve bir testle kanıtlanır. Yazılı olmayan bir garanti verilmez.
- Kabul edilen sınırlar da açıkça yazılır. Bir bulgu, burada "kabul edildi" diye yazmıyorsa açıktır.

## 2. Korunan varlıklar

| Varlık | Nerede |
|---|---|
| Kutu içeriği ve dosya adı | Sunucuda şifreli, alıcının bilgisayarında açık |
| Kutu anahtarı `K` | Alıcı başına sarmalı ya da davet kodu ile kilitli |
| Özel anahtarlar (X25519, Ed25519) | Sunucuda parola + sunucu anahtarı ile kilitli paket; istemcide sadece bellekte |
| Parola | Sadece kullanıcının bilgisayarında, sunucuya hiç gitmez |
| Davet kodu | Sadece gönderen ve alıcıda; sunucuya hiç gitmez |
| Kim kiminle konuşuyor | Sunucuda, kutunun ömrü boyunca (§8 Crypto_Design) ve kişi listelerinde |
| Güncelleme imza anahtarı | Geliştiricide; ele geçerse bütün kullanıcılar etkilenir |

## 3. Saldırganlar

### S1. Ağdaki saldırgan
- **Garanti:** İçeriği ve isteği okuyamaz, değiştiremez, tekrar gönderemez.
- **Önlem:** Sadece HTTPS, TLS 1.3. Masaüstü uygulaması sunucunun açık anahtarını sabitler (**pinning**) ve bir yedek
  anahtar da taşır. Sertifika değişimi önce yedek anahtara geçilerek yapılır.
- **Kabul:** IP adresi ve trafik zamanı görünür.
- **Demo sınırı (Railway):** Sertifikayı Railway yönetir ve anahtarı yenilemede değişebilir. Demo'da sabitleme kendi
  anahtarımıza değil Let's Encrypt kök sertifikalarına yapılır. Kendi alan adı ve anahtarına geçince düzeltilir.
- **Uygulandı (2026-11-06, `PinnedTrust`):** sertifika önce Java güven deposuyla doğrulanır, sonra doğrulanmış
  zincirde (kök dahil) ISRG Root X1, ISRG Root X2 ya da ISRG Root YE anahtarı aranır (`server.pins`, SPKI SHA-256).
  Bağlantı sadece TLS 1.3. Sabit tutmazsa "Sunucunun kimliği doğrulanamadı" hatası verilir. Ara sertifikalara
  sabitleme yapılmaz (yıllık değişirler). Güncelleme kanalı (GitHub) bu sabitlemenin dışındadır; orada imza
  doğrulaması koruma sağlar (S7).

### S2. Veritabanını çalan
- **Parola kuralı (2026-11-07):** 12–20 karakter; en az bir büyük harf, küçük harf, rakam ve özel karakter (boşluk
  özel sayılmaz). Kural girişte de aranır (o tarihte aktif kullanıcı yoktu); yeni parolada (kayıt, sıfırlama, profil)
  ayrıca zayıf parola kontrolü (`PasswordStrength`). **Kural sadece istemcide uygulanır:** sunucu parolayı hiç görmez
  (sadece türevini), bu yüzden bileşimini denetleyemez; denetleyebilmesi için parolanın sunucuya gitmesi gerekirdi.
  En az 12 bilinçli olarak korundu: parola özel anahtar paketinin tek kilidi; sunucu ele geçirilirse (S3) paket çevrimdışı
  denemeye açıktır. Not: 20 karakter üst sınırı teknik olarak gerekmez (sunucuya giden türev sabit uzunlukta); uzun
  parola cümlelerini engeller, kullanıcı kararıyla konuldu.
- **Garanti:** İçeriği, davet kodlarını ve özel anahtarları göremez; parolaları kendi bilgisayarında deneyemez.
- **Risk:** Özel anahtar paketi parolayla kilitlidir ve tuz (telefon) bilinir. Sadece bu kilit olsaydı DB'yi çalan
  zayıf parolaları çevrimdışı kırabilirdi.
- **Önlemler:**
  - **Sunucuda ikinci kilit.** Özel anahtar paketi ve `authSecret` özeti, veritabanına yazılmadan önce veritabanı
    dışında tutulan bir sunucu anahtarıyla (ortam değişkeni ya da anahtar kasası) AES-256-GCM ile bir kez daha
    şifrelenir. Sadece DB dökümü ele geçerse deneme yapılamaz. **Uygulandı (2026-11-06):** `ServerSeal`,
    `SERVER_SEAL_KEY`; AAD kolon adıdır. Sınır: veritabanına yazabilen biri aynı kolondaki iki değeri satırlar arasında
    yer değiştirebilir (bu S2'nin değil S3'ün kapsamıdır). Anahtar kaybolursa bütün hesaplar açılamaz.
  - **Güçlü parola şartı.** En az 12 karakter ve harf + rakam; yaygın kelime/isim kökü (Türkçe dahil, gömülü sözlük,
    rakamla yazılmış harfler çözülür), klavye ve sıra dizileri, tekrar ve kişinin kendi adı/telefonu reddedilir
    (uygulandı, masaüstü `PasswordStrength`, `weak-words.txt`). Kontrol
    istemcide yapılır, çünkü parola sunucuya gitmez.
- **Kabul:** DB ile birlikte sunucu anahtarı da ele geçerse (S3) koruma PBKDF2 (600.000 tur) ve parola gücüne kalır.
  Argon2id dış kripto kütüphanesi gerektirdiği için şimdilik kullanılmaz.

### S3. Kötü niyetli ya da ele geçirilmiş sunucu
- **Garanti:** İçeriği okuyamaz. Kişiler arasına sahte anahtar koyamaz, ne alıcı tarafında ne gönderen tarafında.
- **Önlem: iki yönlü davet kodu** (Crypto_Design §7.6). Alıcı, gönderenin anahtarını kutunun içindeki parmak iziyle
  doğrular. Gönderen de alıcının anahtarını, alıcının kodla mühürlediği (HMAC) parmak iziyle doğrular. Mühür
  tutmazsa kişi eklenmez.
- **Kabul:**
  - Kutuyu silebilir ya da teslim etmeyebilir.
  - Kimin kime gönderdiğini kutunun ömrü boyunca bilir; kişi listelerini görür (gizli gönderen ertelendi,
    Crypto_Design §8.6).
  - Ele geçirilmiş bir sunucu DB'deki paketleri sunucu anahtarıyla açıp parola deneyebilir (bkz. S2).

### S4. Başka bir kullanıcı
- **Garanti:** Başkasının kutusuna, davetine ya da kişi listesine erişemez. Bir numaranın kayıtlı olup olmadığını
  öğrenemez. Davet kodunu deneyerek bulamaz.
- **Önlemler:**
  - Her uç noktada sahiplik kontrolü; kimlikler tahmin edilebilir olsa bile başkasınınki 404 döner.
  - `register/start`, parola sıfırlama ve davet oluşturma, numara kayıtlı olsa da olmasa da **aynı yanıtı aynı
    sürede** verir. SMS kuyruktan gönderilir, yanıtı beklemez.
  - Davet kodu 80 bit. Kullanıcı başına 5 hatalı denemeden sonra 1 saat bekleme (Crypto_Design §7.9).
  - Giriş: aynı IP + numara ile 5 yanlış denemede 1 saat kilit; IP değiştirerek denemeye karşı numara başına, bütün
    IP'lerden toplam 30 yanlış denemede de 1 saat kilit (2026-11-07). Bu sınır bilerek yüksek: düşük olsaydı başkası
    bir numarayı kolayca kilitleyebilirdi. Şifre 12+ karakter olduğu için çevrimiçi tahmin zaten pratik değil.
  - İstek gövdesi en fazla 16 MB (`RequestSizeFilter`); büyük isteklerle bellek doldurma engellenir (413).
- **Zararlı dosya göndermek:** Dosyalar uçtan uca şifreli olduğu için sunucu tarayamaz; denetim istemcilerde
  (`FileRisk`, UI_Standards §4.5). Gönderen program ve betik gönderemez (uzantı ve içerik imzası: MZ+PE, ELF, Mach-O,
  `#!/`). Alıcı göndericinin denetimine güvenmez (değiştirilmiş istemci atlayabilir): çözülen içerik yeniden denetlenir;
  makrolu Office, betikli PDF, içinde program olan arşiv, uzantıyla uyuşmayan içerik, gizli uzantı için uyarı ve
  kaydetmeden önce onay istenir. Kaydedilen her dosyaya Windows'un dış kaynak etiketi (Mark of the Web,
  `Zone.Identifier`, ZoneId=3) konur: Office makroları çalıştırmaz ve Korumalı Görünüm'de açar, SmartScreen ve
  antivirüs devreye girer. Sezgiseldir; okuyucudaki açığı kullanan bozuk bir dosyayı yakalamaz. Gönderenin kimliği
  imzayla bilinir (kim gönderdi belli).
- **"Kaydedilemez" belgeyi saklamak:** Garanti değil, caydırıcı (Crypto_Design §8.3.1). Alıcı uygulaması kaydetmeyi,
  kopyalamayı kapatır ve pencereyi ekran görüntüsünden hariç tutar (Win+Shift+S, PrintScreen, PowerToys Metin
  Ayıklayıcı, ekran paylaşımı boş görür; 2026-11-07'de ekrandan yakalamayla denendi). Değiştirilmiş istemci, telefon
  kamerası, sanal makine dışından görüntü engellenmez.

### S5. SIM kopyalayan
- **Garanti:** Eski kutuları açamaz. Kişileri, ona farkında olmadan dosya gönderemez.
- **Önlem: anahtar değişince yeni kod** (Crypto_Design §7.8). Bir kişinin anahtarı değişince o kişi
  **doğrulanmamış** olur. Ona tekrar göndermek için yeni davet kodu gerekir. Kod dış kanaldan (yüz yüze, telefonla)
  gerçek kişiye iletildiği için numarayı ele geçiren saldırgan bunu geçemez.
- **Kabul:** Parolayı SMS ile sıfırlayıp hesabı ele geçirebilir; boş bir hesap ve kişi listesi alır. Davet
  bekleyen kutuları açamaz, çünkü kodu bilmez.
- **İsteğe bağlı ek önlem: TOTP** (Crypto_Design §7.4). Kullanıcı açtıysa parola sıfırlama Authenticator ya da
  kurtarma kodu da ister; numarayı kopyalayan saldırgan hesabı ele geçiremez. Bedeli: Authenticator'ını ve kurtarma
  kodlarını kaybeden kullanıcı o numarayla bir daha giremez.

### S6. Bilgisayarı çalınan kullanıcı
- **Garanti:** Kapalı ya da oturumu kapalı bir bilgisayarda anahtar ve oturum yoktur.
- **Durum (kontrol edildi):** Özel anahtarlar ve erişim jetonu sadece bellekte. Refresh cookie bellekteki
  `CookieManager`'da. Diske (`Preferences`) sadece tema ve zorunlu güncelleme bilgisi yazılır.
- **Kabul:** Kullanıcının kendisinin kaydettiği dosyalar diskte kalır. Açık oturumdaki bilgisayar (S9) kapsam dışı.
- **"Beni hatırla" (isteğe bağlı, 2026-11-06; Crypto_Design §7.4):** seçilirse refresh token ve özel anahtarlar
  `%LOCALAPPDATA%\GuvenliPaylasim\remember.bin` dosyasında, Windows veri korumasıyla (DPAPI, kullanıcı kapsamı)
  şifreli durur. Garanti daralır: kapalı bilgisayarın diski başka yerde okunursa ya da dosya kopyalanırsa açılmaz
  (Windows hesabının parolası gerekir); ama **Windows oturumu açık bilgisayara erişen ya da o kullanıcı adına çalışan
  bir program kutuları açabilir.** Bu kabul edilmiştir; seçenek varsayılan kapalıdır ve ekranda "ortak kullanılan
  bilgisayarda seçmeyin" uyarısı vardır. Sınırlar: kayıt 30 gün geçerlidir (uzatılmaz), bu süre boyunca iki adımlı
  doğrulama kodu da sorulmaz; çıkışta, parola başka cihazda değişince ya da sıfırlanınca (sunucu oturumları kapatır)
  geçersizleşir ve silinir. Kontrol: `RememberedLoginTest` (dosyada token ve telefon düz metin olarak geçmez, başka
  entropiyle açılmaz) ve yerel sunucuya karşı uçtan uca 15 kontrol.

### S7. Güncelleme kanalını ele geçiren
- **Garanti:** İmzasız ya da daha eski bir sürüm kurulmaz.
- **Durum:** İmza doğrulaması var; `Version.isNewerThan` sadece daha yeni sürümü kabul eder.
- **Önlemler:**
  - **Acil:** İmza anahtarı (`release-keys/update-private-key.txt`) düz dosyada duruyor. Parolayla şifrelenmeli ya da
    donanım anahtarına taşınmalı; imzalama ayrı bir adım olmalı.
  - Güncelleme bildirimi bir son geçerlilik tarihi taşımalı; eski bir bildirim tekrar sunularak güncellemenin
    gizlenmesi (dondurma saldırısı) engellenir.
- **Kabul (2026-11-07):** Akıllı Uygulama Denetimi açık bilgisayarda MSI hiç başlatılmaz; uygulama "ZIP'i indir" ile
  kurulumsuz ZIP'in GitHub adresini tarayıcıda açar. Bu ZIP'i kullanıcı elle indirip açar; **ZIP'in özeti imzalı
  değildir**, yani GitHub hesabını ele geçiren biri bu yoldan değiştirilmiş bir ZIP sunabilir. Açılan adres sadece bu
  deponun `https://github.com/<depo>/` adresi olabilir (`UpdateService.safeRepositoryUrl`). Kapatmak için ZIP'in
  özeti de `release.json`'da imzalanmalı ve uygulama ZIP'i kendisi indirip doğrulamalı (Update_Design §2b).

### S8. Doğrulama mesajını (WhatsApp / SMS) kötüye kullanan
- **Garanti:** Mesaj servisi fatura saldırısına ve taciz amaçlı kullanıma kapalıdır. Kanal WhatsApp'tır
  (Crypto_Design §7.2); sınırlar aynıdır.
- **Önlemler:** SMS sadece `^5\d{9}$` (TR mobil) numaralara gider. Numara başına saatte 3, IP başına saatte 10,
  **sistem geneli günlük tavan** (aşılınca SMS durur, alarm verilir). SMS kuyruktan gönderilir.
- **Test modu (2026-11-07, `VERIFICATION_TEST_MODE`, varsayılan kapalı):** açıkken kayıt kodu gönderilmez, cevapta
  istemciye döner ve uygulama alana yazar; **telefon sahipliği doğrulanmaz** (biri başkasının numarasıyla kayıt
  olabilir). Sadece kayıtta geçerli; parola sıfırlama kodu asla dönmez (hesap ele geçirme olurdu). Açıkken sunucu her
  başlangıçta uyarı loglar, uygulama "TEST MODU" yazar. **Gerçek kullanıcıdan önce kapatılmalı.**

## 4. Kapsam dışı (kabul edilen)

| Konu | Neden |
|---|---|
| S9. Kullanıcının bilgisayarındaki zararlı yazılım, ele geçirilmiş işletim sistemi | Açık oturumdaki anahtarları ve ekranı okur; hiçbir istemci uygulaması buna karşı koruyamaz |
| Kullanıcının kodu ya da dosyayı kendisi başkasına vermesi | Sosyal mühendislik; arayüzde uyarılarla azaltılır |
| Trafik analizi (IP, zamanlama, boyut) | Tor ya da karıştırma ağı gerekir |
| Hizmet engelleme (DoS) | Sadece hız sınırı ve kota (Devam_Rehberi §4) |
| Açılan dosyanın kopyalanması, ekran görüntüsü | "Açılınca sil" sadece sunucuda kalmamayı garanti eder (Crypto_Design §8.3) |

## 5. Test planı

Ölçüt: **OWASP ASVS 4.0 Seviye 3** (yüksek güvence). Her kontrol maddesi için "uygulanır / uygulanmaz (gerekçe) /
test" listesi bu belgenin ekine yazılır.

| Alan | Test |
|---|---|
| Kripto | Test vektörleri (istemci ve sunucu aynı sonucu verir); davet kodu, kutu ve anahtar paketi formatları için fuzz; yanlış imza, yanlış AAD, kesilmiş veri, sürüm baytı değişikliği reddedilir |
| İki yönlü kod (S3) | Sunucu sahte alıcı ya da gönderen anahtarı verirse kişi eklenmez ve kutu açılmaz. **Var:** masaüstü `InviteCryptoTest` (sahte gönderen anahtarı, sahte alıcı anahtarı, sahte mühür, değiştirilmiş kişi kaydı ve içerik) |
| Anahtar değişikliği (S5) | Anahtarı değişen kişiye kodsuz gönderim istemcide ve sunucuda reddedilir. **Var:** sunucu `ContactServiceImplTest`, uçtan uca deneme (parola sıfırlama → `KEY_CHANGED` → sunucu reddi → yeni kodla yeniden doğrulama) |
| Yetki (S4) | Her uç nokta için başka kullanıcının kimliğiyle erişim denemesi (IDOR); oturumsuz erişim. **Kısmen var:** davetlerde başka kullanıcının kodu ve kabul/doğrulama/geri alma denemesi (`InviteServiceImplTest`); diğer uç noktalar için sistematik test henüz yok |
| Numara tarama (S4) | Kayıtlı ve kayıtsız numara için yanıt gövdesi aynı; yanıt süresi farkı istatistiksel olarak ayırt edilemez. İlk ölçüm (2026-11-06, yerel sunucu, uzak DB, 20'şer istek): `register/start` medyan farkı 2,6 ms, `password-reset/start` 10,2 ms (~690 ms yanıtta) |
| Hız sınırları (S4, S8) | Her sınır aşılınca reddedilir; IP değiştirerek ya da paralel isteklerle atlatılamaz |
| Silme (Crypto_Design §8) | Süresi dolan, açılınca silinen ve alıcının sildiği kutunun DB'de hiçbir satırı kalmaz. **Var:** `DocumentServiceImplTest`; uçtan uca deneme veritabanını doğrudan sorgulayarak üç durumda da satır kalmadığını kontrol eder |
| DB dökümü (S2) | DB'deki anahtar paketi ve `authSecret` özeti, sunucu anahtarı olmadan çözülemez. **Var:** `ServerSealTest` (yanlış anahtar, başka kolon, değiştirilmiş değer); yerel veritabanında bütün satırların `s1:` önekiyle kilitlendiği ve bcrypt özetinin görünmediği sorgulandı |
| TLS (S1) | Sabitlenmemiş sertifikayla bağlantı reddedilir; TLS 1.2 altı ve düz HTTP reddedilir. **Var:** `PinnedTrustTest`; canlı deneme: Railway'e bağlanır, yanlış sabitle ve başka otoritenin sertifikasıyla (`api.github.com`) el sıkışma reddedilir. Düz HTTP sadece yerel geliştirmede (`localhost`) kullanılır |
| Güncelleme (S7) | İmzasız, daha eski ve süresi dolmuş bildirim reddedilir |
| İstemci (S6) | Oturum kapatıldıktan sonra diskte jeton ya da anahtar yok |
| Bağımlılıklar | OWASP Dependency-Check her derlemede; yüksek önemli bulgu derlemeyi durdurur |
| Son aşama | Dışarıdan sızma testi |

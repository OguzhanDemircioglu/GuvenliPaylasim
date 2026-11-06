# Güvenlik: Tehdit Modeli ve Test Planı

> Konuşuldu (2026-11-06). v2 (Crypto_Design §7, §8) bu belgeye göre kodlanır.
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

### S7. Güncelleme kanalını ele geçiren
- **Garanti:** İmzasız ya da daha eski bir sürüm kurulmaz.
- **Durum:** İmza doğrulaması var; `Version.isNewerThan` sadece daha yeni sürümü kabul eder.
- **Önlemler:**
  - **Acil:** İmza anahtarı (`release-keys/update-private-key.txt`) düz dosyada duruyor. Parolayla şifrelenmeli ya da
    donanım anahtarına taşınmalı; imzalama ayrı bir adım olmalı.
  - Güncelleme bildirimi bir son geçerlilik tarihi taşımalı; eski bir bildirim tekrar sunularak güncellemenin
    gizlenmesi (dondurma saldırısı) engellenir.

### S8. Doğrulama mesajını (WhatsApp / SMS) kötüye kullanan
- **Garanti:** Mesaj servisi fatura saldırısına ve taciz amaçlı kullanıma kapalıdır. Kanal WhatsApp'tır
  (Crypto_Design §7.2); sınırlar aynıdır.
- **Önlemler:** SMS sadece `^5\d{9}$` (TR mobil) numaralara gider. Numara başına saatte 3, IP başına saatte 10,
  **sistem geneli günlük tavan** (aşılınca SMS durur, alarm verilir). SMS kuyruktan gönderilir.

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

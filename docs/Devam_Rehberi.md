# Devam Rehberi

> Son güncelleme: 2026-11-07 (teslim öncesi). Bu dosya "proje ne durumda, sırada ne var" sorusunun cevabıdır.
> Yeni bir oturuma başlarken önce bunu, sonra [Security.md](Security.md)'yi ve
> [Crypto_Design.md §7–§8](Crypto_Design.md#7-v2-herkes-kullanıcı-telefon-ve-davet-kodu)'i okuyun.

## Durum (2026-11-07)

**Sunucu Railway'de canlı (`main`), masaüstü 2.0.5 GitHub'da yayında.** Planlanan v2 işlerinin hepsi kodlandı ve
yayında. Testler: sunucu 160, masaüstü 72; hepsi geçiyor.

| Parça | Durum |
|---|---|
| Sunucu | Railway, `https://guvenlipaylasim.up.railway.app`; `main`'e her push deploy eder (aşağıda "Demo kurulumu") |
| Veritabanı | Railway Postgres (sunucuyla aynı proje, özel ağ). Aiven artık kullanılmıyor |
| Masaüstü | 2.0.5 (GitHub release `v2.0.5`: MSI, kurulumsuz ZIP, imzalı `release.json`; zorunlu değil) |
| Doğrulama mesajı | Vonage WhatsApp sandbox; Railway'de **test modu açık** (kayıt ve şifre sıfırlama kodu uygulamaya döner) |
| v1 (rollü, birimli prototip) | Masaüstü 1.0.5; v2 sunucusuna bağlanamaz, kullanılmıyor |

### Sürüm geçmişi (masaüstü)

| Sürüm | Tarih | Öne çıkanlar |
|---|---|---|
| 2.0.0 | 2026-11-06 | v2: telefonla kayıt (WhatsApp kodu), davet kodu ve kişiler, süreli kutu, açılınca sil, TOTP, sertifika sabitleme. Güncelleme imza anahtarı değişti (1.0.5 kullananlar bir kez elle kurar) |
| 2.0.1 | 2026-11-06 | Telefon maskesi `(5xx) xxx xx xx`, alan kısıtları, profilde ad değiştirme, "Beni hatırla" (DPAPI, 30 gün) |
| 2.0.2 | 2026-11-07 | Akıllı Uygulama Denetimi açıksa güncelleyici MSI yerine ZIP'i önerir (Update_Design §2b); sürüm etiketi |
| 2.0.3 | 2026-11-07 | Kod gönderme geri sayımı (300 sn), "Bu Cihaza Güven", logo, ad soyad 2–3 kelime, KVKK aydınlatma metni (taslak), düğmeler form geçerli olmadan kapalı, şifre 12–20 karakter + dört karakter türü |
| 2.0.4 | 2026-11-07 | Verdana ve büyük yazılar, "Şifre" ifadesi, şifre alanı 20 karakter, uyarı yerleri ([UI_Standards.md](UI_Standards.md)), "Hesabımı Sil", kayıt test modu |
| 2.0.6 | 2026-11-07 | Bakım: Gelen Kutusu listesi kendiliğinden yenilenir ("Yenile" kalktı). Sunucuda (deploy ile): Gönderilenler içerik okumadan listelenir, eksik indeksler, istek gövdesine 16 MB sınırı, numara başına toplam giriş denemesi sınırı (30/saat), Spring Boot 4.1.2 |
| 2.0.7 | 2026-11-10 | Kurum içi TLS denetimi (ör. Fortinet): Windows güven deposu da okunur; kurum kökünün pin'i o makinede `GPS_SERVER_PINS` ile eklenirse bağlanılır, varsayılan sabitleme değişmez (Security S1). Yayımlanmadı, 2.0.8'e dahil |
| 2.0.8 | 2026-11-10 | Güncellemeler ayrı, public `GuvenliPaylasim-releases` deposundan okunur (kod deposu private olabilsin diye). İmza anahtarı yine `…FnRb…` (Update_Design §2): 2.0.x kullananlar 2.0.8'i bir kez elle kurar |
| 2.0.9 | 2026-11-11 | Uygulamanın adı Lockpost |
| 2.0.10 | 2026-11-11 | Güncellemeler yine kod deposundan (`OguzhanDemircioglu/Lockpost`) okunur; ayrı sürüm deposu artık kullanılmaz (Update_Design §2) |
| 2.1.0 | 2026-11-12 | Kayıtta aydınlatma kutusu varsayılan işaretli; test modunda sıfırlama kodu otomatik dolar |
| 2.0.5 | 2026-11-07 | Geniş dosya türü listesi, programlar engellenir, alıcıya riskli dosya uyarısı ve dış kaynak etiketi (Security S4), **Kaydedilemez** belgeler (Crypto_Design §8.3.1), **Kişi Ekle**, tıklayınca açılan belge, kendiliğinden yenilenen Gönderilenler |

### Gerçek kullanıma geçmeden önce yapılacaklar

- **Doğrulama mesajı:** Railway'de `TEST_MODE` kapatılmalı (açıkken telefon sahipliği doğrulanmaz,
  Security S8). Türkiye'ye SMS için Vonage'da kayıtlı gönderen adı ya da Meta onaylı WhatsApp Business hesabı
  gerekir (ayrıntı: "Demo kurulumu").
- **KVKK:** `desktop/src/main/resources/com/desktop/aydinlatma-metni.txt` TASLAK. Avukat incelemesi; köşeli
  parantezli yerler (veri sorumlusu, iletişim, sunucu ülkesi, yurt dışı aktarım güvencesi); yurt dışı aktarım için
  KVKK m.9 (2024 değişikliği) ya da sunucuyu Türkiye'ye taşımak; gerekirse onayın sunucuda kaydı.
- **Kod imzalama:** imzasız MSI'ı Akıllı Uygulama Denetimi reddediyor; şimdilik kurulumsuz ZIP. Ücretsiz seçenek
  SignPath Foundation (2026-11-10 başlandı): `LICENSE` (Apache 2.0), MSI/ZIP GitHub Actions'ta kaynaktan
  (`.github/workflows/desktop-release.yml`), README'de "Code signing policy" ve gizlilik bölümü hazır. Kalan:
  GitHub'da MFA, https://signpath.org/apply başvurusu; onaydan sonra Actions değişkenleri
  `SIGNPATH_ORGANIZATION_ID`, `SIGNPATH_PROJECT_SLUG`, `SIGNPATH_SIGNING_POLICY_SLUG` ve gizli `SIGNPATH_API_TOKEN`.
  Kod deposu public kalmalı (kaynaktan doğrulanabilir üretim şartı). Şartlar: https://signpath.org/terms. Ücretli
  seçenek: Azure Trusted Signing.
- **Uygulama adı Lockpost** (2026-11-11, 2.0.9'dan itibaren): pencere, MSI/ZIP ve dosya adları (`Lockpost.exe`),
  TOTP ve doğrulama mesajı. Kod deposunun GitHub adı da `Lockpost` (eski adres yönlendirilir). Java paketleri,
  sunucu adresi ve `%LOCALAPPDATA%\GuvenliPaylasim` (hatırlanan oturum) aynı kaldı. Upgrade UUID değişmediği için MSI
  eski kurulumun yerine geçer.
- **Anahtar yedekleri:** `release-keys/update-private-key.txt` (git'te yok) düz dosya; parolayla şifrelenmeli ya da
  donanım anahtarına taşınmalı ve çevrimdışı yedeklenmeli. Railway'deki `SERVER_SEAL_KEY` veritabanı yedeğinden ayrı
  yedeklenmeli; kaybolursa bütün hesaplar açılamaz.
- **Güvenlik testleri** (Security.md §5): uç noktaların sistematik IDOR testi, OWASP Dependency-Check, fuzz,
  dışarıdan sızma testi.
- **Bilinen küçük açıklar:** günlük 200 kutu sınırı silinen kutuları saymıyor; "Kaydedilemez" belgeyi 2.0.4 ve
  öncesi açamaz (bir sonraki sürüm zorunlu yapılabilir).
- **Railway'de `FORWARD_HEADERS_STRATEGY=native` olmalı** (kontrol edilmedi). Yoksa sunucu bütün istekleri Railway
  proxy'sinden gelmiş sayar: IP başına sınırlar (saatte 10 doğrulama kodu, giriş kilidi) herkes için tek sınır olur.

- **Mac desteği (teslimden sonra, 2026-11-08 kararı):** şu an sadece Windows. Gerekenler: "Bu Cihaza Güven" için
  macOS Anahtar Zinciri (DPAPI yerine), ekran koruması için pencere paylaşım ayarı (`ScreenGuard`), dış kaynak
  etiketi için karantina etiketi (`WebMark`), güncelleyicide MSI yerine `.dmg`/`.zip`, paketin Mac'te ya da GitHub
  Actions macOS makinesinde derlenmesi, Apple imzası ve notarization (yıllık 99 $ geliştirici hesabı). Tahmini 1–2 gün.

### Başka bilgisayarda çalışmaya başlamak

- `git clone` / `git pull` (dal: `main`).
- JDK 25 (ör. `~/.jdks/corretto-25.0.4.1`). Maven wrapper kurum ağında sertifika kontrolü yüzünden indiremeyebilir;
  o zaman yerel Maven (ör. `C:	oolspache-maven-3.9.9`) kullanılır.
- Testler: `server/` ve `desktop/` klasörlerinde `mvn test`.
- Yerel sunucu: `server/.env` git'te yok. Gerekenler: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (yerel PostgreSQL),
  `DB_SCHEMA=gps_v2`. `SERVER_SEAL_KEY` dev'de verilmezse sabit dev anahtarı kullanılır. `MESSAGING_PROVIDER`
  verilmezse doğrulama kodları sunucu log'una yazılır. Çalıştırma: `serverun-dev.cmd`.
- Demo kullanıcıları (sadece dev): `5550000001`–`5550000004`, şifre `DemoParola2026!`.
- Masaüstü: `desktopun.cmd` yerel sunucuya bağlanır; Railway için `GPS_SERVER_URL=https://guvenlipaylasim.up.railway.app`.
- Ekran görüntüsü aracı: masaüstü testlerindeki `com.desktop.ui.views.ScreenshotTour` (yerel sunucu açıkken).
- Sürüm yayımlamak için `release-keys/update-private-key.txt` ve WiX 3.14 (`C:\Projeler\.tools\wix3`) gerekir;
  GitHub'a yükleme `gh` CLI ile (README › Yeni sürüm yayımlama).
- Railway: proje `faithful-enthusiasm`, servis `GuvenliPaylasim` (kök `/server`). Claude için Railway MCP
  `~/.claude.json`'da proje token'ıyla tanımlı; başka bilgisayarda panelden yeni token alınıp eklenir.

## Çalışma kuralı

**Önce konuşulur, sonra kod yazılır.** Tasarım kararları önce ilgili dokümana (Crypto_Design, Security,
UI_Standards) yazılır. Her değişiklikten sonra commit ve push sormadan yapılır.

## Ürünün amacı

Kimsenin açamayacağı bir kutuya dosya ya da mesaj konur; kutu sadece alıcının bilgisayarında açılır. Herkes eşit
kullanıcıdır; kapsam tek bir ağ ya da kurumla sınırlı değildir. Şifresini kaybeden, kendisine gelmiş kutuları da
kaybeder.

Kapsam dışı: para ya da kripto para transferi, izlenemez ödeme veya takas sistemi.

## 1. Temel dönüşüm: alınan kararlar (özet)

Ayrıntı Crypto_Design §7'de.

- Rol yok, birim yok, e-posta yok. Kayıtta ad soyad, telefon (`5XXXXXXXXX`), parola istenir. Telefon SMS ile
  doğrulanır (Vonage).
- Alıcı sadece telefonla seçilir. İlk gönderimde gönderenin ekranında 16 karakterlik kod üretilir; kutu bu kodla
  kilitlenir. Kodu gönderen kendi kanalıyla alıcıya iletir. Süreyi gönderen seçer (en fazla 7 gün). Alıcı o süre
  içinde kayıt olabilir.
- Alıcı kodu girene kadar hiçbir şey görmez. Kod girilince iki taraf birbirinin kişi listesine eklenir; sonraki
  gönderimler kodsuzdur ve bildirimle gelir. Kişi silinirse eşleşme iki yönde biter.
- Parola sıfırlama SMS ile yapılır. Kişiye gelmiş kutular silinir; kişi listesi ve kodu girilmemiş davetler kalır.
  Kişilere "anahtarı değişti" uyarısı çıkar.
- Telefon numarası değiştirilemez (parola telefonla türetiliyor). Geçişte veritabanı şeması sıfırlanır.

## 2. Gizlilik: alınan kararlar (özet)

Ayrıntı Crypto_Design §8'de. Yaklaşım: sunucu daha az şey değil, **daha kısa süre** bilir.

- **Süreli kutu.** Gönderen ömür seçer: 1 saat, 1 gün, 7 gün (varsayılan), 30 gün. Süresiz kutu yok. Süre dolunca
  kutu ve kimden-kime-ne zaman bilgisi sunucudan tamamen silinir.
- **Açılınca sil.** Gönderenin seçtiği bir seçenek, varsayılan kapalı. Alıcı kutuyu açıp doğruladıktan sonra onay
  gönderir, sunucu ancak o zaman siler (bağlantı koparsa kutu kaybolmaz).
- **Okunma durumu.** Kutu yaşadığı sürece gönderen görür. Kutu silinince gönderilenler listesinden de kalkar.
- **Kayıtlar.** Kalıcı denetim kaydı yok; §4.5'teki "kim-kime kalır" kuralı kalkar. Giriş denemeleri 24 saat,
  kapatılmış oturumlar 7 gün.
- **Gizli gönderen (teslim jetonu) ertelendi.** IP ve zamanlama sızıntısı yüzünden kazancı sınırlı. v2'de kişi
  kaydı ayrı tabloda tutulur, gönderim yetkisi tek yerde kontrol edilir; ileride jetona geçilebilir.

## 3. Karşılıklı teslim (kasa): yapılmayacak

- İçerik doğrulanamaz: sunucu kutunun içini göremez, kasa sadece "ikiniz de bir şey bıraktınız" garantisi verir.
- Sunucuya iki taraflı, zaman aşımlı, durumlu bir karar mantığı ekler; yarış durumu ve zamanlama saldırılarına açık
  yeni bir yüzey açar.
- Süreli kutu ve kayıt silme (§8) ile birleşince kapsam dışı bırakılan "izlenemez takas sistemi"ne dönüşür.
- Meşru kullanımlar başka özelliklerle daha iyi çözülür: sözleşme için karşılıklı imza, teklif zarfı için zaman
  kilidi. Gerekirse bunlar ayrıca konuşulur.

## Güvenlik ilkesi

Uygulama bütün saldırı testlerine sokulacak; geçemezse anlamı kalmaz. Bu yüzden:

- Her özellik için ilk soru: "Hangi saldırı yüzeyini açıyor, bunu savunabilir miyiz?"
- [Security.md](Security.md): saldırgan listesi (S1–S9), her biri için garanti ve kabul edilen sınırlar, test
  planı. Ölçüt **OWASP ASVS Seviye 3**; ek olarak kripto test vektörleri ve fuzz, uç nokta başına yetki
  (IDOR) ve hız sınırı testleri, bağımlılık taraması, en sonda dışarıdan sızma testi.
- Kodlama sırasındaki bir adım, ilgili saldırı testleri yazılıp geçmeden "bitti" sayılmaz.
- Alınan kararlar: sunucuda ikinci kilit ve 12+ karakter parola (S2), iki yönlü davet kodu (S3), anahtar değişince
  yeni kod (S5), sertifika sabitleme (S1), SMS sistem geneli tavanı (S8).

## 4. İnternete açılma: kararlar

**Alınan kararlar**
- **En büyük dosya 10 MB.** Parçalı şifreleme ve parçalı yükleme gerekmez; dosya tek parça, veritabanında kalır.
- **Doğrulama mesajı WhatsApp ile** (Vonage Messages API). Demo'da Vonage sandbox (Crypto_Design §7.2).
- **Demo sunucusu Railway'de** (aşağıda). Adres `https://guvenlipaylasim.up.railway.app`. DuckDNS kullanılamadı:
  Railway özel adres için CNAME ister, DuckDNS CNAME desteklemez. Kendi alan adı alınınca CNAME ile bağlanır.

**Onaylandı (2026-11-06)**
- Parmak izi için QR ekranı yapılmaz; iki yönlü davet kodu (§7.6) bu işi görür. Parmak izi kişi detayında görünür.
- Kota: kullanıcı başına aynı anda yaşayan gönderilmiş kutular toplamı 500 MB; kişilere günde 200 kutu.
- Yedekleme: kutular yedeklenmez (süreli silme sözü yedekte bozulmasın); kullanıcılar, anahtar paketleri ve kişiler
  şifreli yedeklenir, 7 gün tutulur. Sunucudaki ikinci kilidin anahtarı DB yedeğinin yanında tutulmaz.
- Gerçek kullanıcı verisinden önce KVKK: sunucunun yeri (Railway Türkiye dışında) ve aydınlatma metni.

## Demo kurulumu (Railway)

> 2026-11-06'da kuruldu; aynı gün `v2` `main`'e birleştirildi (`51e8ed6`). Railway'de çalışan kod **v2**'dir.
> Masaüstü 1.0.5 (v1) bu sunucuyla çalışmaz. 2026-11-07'den beri `TEST_MODE=true` (kayıt kodu
> uygulamaya döner; gerçek kullanımdan önce kapatılmalı).

| Konu | Değer |
|---|---|
| Proje | `faithful-enthusiasm`, ortam `production` |
| Servisler | `GuvenliPaylasim` (GitHub `main`, kök klasör `/server`, Railpack, JDK 25), `Postgres` (Railway şablonu, volume'lu) |
| Adres | `https://guvenlipaylasim.up.railway.app` (HTTP → HTTPS yönlendirilir), sağlık: `/actuator/health` |
| Profil | `prod`: Swagger ve dev-login kapalı, demo kullanıcıları (V001) yüklenmez; herkes kendisi kayıt olur |
| JWT anahtarları | `JWT_PRIVATE_KEY_PEM` / `JWT_PUBLIC_KEY_PEM` değişkenlerinde; başlangıç komutu bunları `/tmp/keys`'e yazar |
| İkinci kilit | `SERVER_SEAL_KEY` (rastgele 32 bayt, Base64). **Kaybolursa bütün hesaplar açılamaz:** Railway panelinden alınıp veritabanı yedeğinden ayrı, çevrimdışı bir yere yedeklenmeli |
| Şema | `DB_SCHEMA=gps_v2` (v1 tabloları eski `guvenlipaylasim` şemasında duruyor, kullanılmıyor) |
| Doğrulama mesajı | `MESSAGING_PROVIDER=vonage`, WhatsApp sandbox (`VONAGE_CHANNEL=whatsapp`) |
| Veritabanı | `DB_URL` vb. `${{Postgres.*}}` referanslarıyla, özel ağdan (`postgres.railway.internal`) |

- `main`'e her push otomatik deploy eder.
- Masaüstünden bağlanmak için: `GPS_SERVER_URL=https://guvenlipaylasim.up.railway.app`.
- Doğrulama mesajı için Railway panelinde servisin Variables kısmına girilecekler (repoya ve sohbete yazılmaz):
  `MESSAGING_PROVIDER=vonage`, `VONAGE_API_KEY`, `VONAGE_API_SECRET`, `VONAGE_CHANNEL` (`sms` | `whatsapp`),
  `VONAGE_FROM` (SMS'te gönderen adı, WhatsApp'ta gönderen numara), `VONAGE_MESSAGES_URL` (varsayılan
  `https://api.nexmo.com/v1/messages`; WhatsApp sandbox için `https://messages-sandbox.nexmo.com/v1/messages`).
  Basic auth kullanıldığı için application ID ve private key gerekmez. `MESSAGING_PROVIDER` verilmezse kod sadece
  sunucu log'una yazılır. İsteğe bağlı: `VERIFICATION_DAILY_LIMIT` (varsayılan 200).
- **SMS denemesi (2026-11-06):** Vonage Messages API'ye gönderilen kayıt SMS'i Vodafone Türkiye tarafından reddedildi
  (Reports API: `rejected`, hata `1330`). Vonage'ın Türkiye kuralları: harfli gönderen adı (ör. `GuvenliPay`) önceden
  kayıt ister, numaralı gönderen desteklenmez, `INFO`/`SMS` gibi genel adlar yasak. Kayıt Vonage panelindeki Global
  Sender ID Portal'dan yapılır. Kayıt olmadan Türkiye'ye SMS gitmez.
- **WhatsApp sandbox denemesi (2026-11-06): çalıştı.** Kayıt kodu WhatsApp'a geldi; kodla kayıt tamamlandı ve giriş
  yapıldı. Demo ayarları: `VONAGE_CHANNEL=whatsapp`, `VONAGE_FROM=14157386102`,
  `VONAGE_MESSAGES_URL=https://messages-sandbox.nexmo.com/v1/messages`. Sınır: sadece sandbox'a katılmış numaralara
  gider (Vonage paneli → Messages Sandbox → WhatsApp; telefondan katılma mesajı). Gerçek kullanım için kayıtlı SMS
  gönderen adı ya da Meta onaylı WhatsApp Business hesabı gerekir.
- Railway proje token'ı sohbette paylaşıldı; kurulum işleri bitince panelden yenilenmeli.

## Kodlama sırası

> v2 kodu `v2` dalında yazıldı ve 2026-11-06'da `main`'e birleştirildi. Birleştirmeden önce Railway'de
> `DB_SCHEMA=gps_v2` deploy tetiklenmeden ayarlandı; `ADMIN_EMAIL` / `ADMIN_PASSWORD` birleştirmeden sonra silindi.
> Bundan sonraki işler doğrudan `main`'de ya da kısa ömürlü dallarda yapılır; `main`'e her push deploy eder.

Kararlar netleşince güncellenir.

1. ✅ (2026-11-06, `v2` dalı) Sunucu: rolleri, birimleri ve yönetim API'lerini kaldırmak; `User` modelini ad soyad +
   telefon olarak yeniden kurmak; şemayı sıfırlamak (yeni şema `gps_v2`); demo verisini yeniden yazmak.
   Geçici: kayıt henüz telefon doğrulamasız (2. adım); `AuthSecrets` v2 tuzuna geçti, masaüstü istemci 3. adımda.
2. ✅ (2026-11-06, `v2` dalı) Sunucu: WhatsApp kodlu kayıt ve parola sıfırlama (`register/start|complete`,
   `password-reset/start|complete`); `VonageWhatsAppGateway`; numara/IP/günlük sınırlar; kuyruktan gönderim.
   Kayıtta anahtarlar da gelir. Sıfırlamada gelen kutular silinir, oturumlar kapanır. Yerelde uçtan uca denendi;
   start yanıt süresi kayıtlı ve kayıtsız numarada aynı (medyan farkı 3–10 ms / ~690 ms).
3. ✅ (2026-11-06, `v2` dalı) Kripto ve masaüstü girişi: telefon tuzlu türetme (istemci ve sunucu aynı test
   vektörü), anahtar paketi telefona bağlı (`gps-private-keys/v2|`), anahtarlar kayıtta üretilir. Masaüstünde telefonla
   giriş, WhatsApp kodlu kayıt, "Parolamı unuttum" (yeni anahtar çifti); yönetim ekranları, birimler ve ilk parola
   ekranı kaldırıldı; parola en az 12 karakter. Yeni Gönderi'de alıcı seçimi 4. adıma kadar kapalı. Masaüstü →
   sunucu uçtan uca denendi (kayıt, giriş, parola değiştirme aynı anahtarları korur, sıfırlama yeni anahtar üretir).
   Açık: yaygın parola listesi kontrolü (Security.md S2).
4. ✅ (2026-11-06, `v2` dalı) Davet kodu ve kişiler: `invites` ve `contacts` tabloları ve API'leri; iki yönlü
   doğrulama (kabul mührü), imzalı kişi kaydı; masaüstünde Yeni Gönderi'de "Kişilerim / Davet kodu ile", tek seferlik
   kod ekranı, Gelen Kutusu'nda "Kod ile al", yeni "Kişiler" ekranı (durum, parmak izi, silme, bekleyen davetler).
   Kodsuz gönderim sunucuda sadece doğrulanmış ve anahtarı değişmemiş kişiye kabul edilir. İki kullanıcıyla uçtan uca
   denendi (21 kontrol): yanlış ve başkasının kodu reddedilir, kod bir kez kullanılır, iki taraf doğrulanır, anahtarı
   değişen kişiye gönderim reddedilir, yeni kodla yeniden doğrulanır, silme iki yönlüdür.
5. ✅ (2026-11-06, `main`) Anahtar değişikliği (§7.8) 4. adımda yapıldı. Sunucuda ikinci kilit: parola özetleri ve
   özel anahtar paketleri `SERVER_SEAL_KEY` ile kilitli (V003 mevcut kayıtları kilitledi; Railway'de de uygulandı).
   Sertifika sabitleme: masaüstü sadece TLS 1.3 ve Let's Encrypt köklerine (X1, X2, YE) zincirlenen sertifika kabul
   eder; Railway'e karşı ve başka otoriteye karşı denendi.
6. ✅ (2026-11-06, `main`) Gizlilik (§8): kutunun ömrü (1 saat, 1 gün, 7 gün varsayılan, 30 gün), açılınca sil (iki
   adımlı onay), iz bırakmayan silme (satır silinir, son alıcıysa belge de), ömrü dolan kutuların zamanlanmış silinmesi,
   gönderilenlerde sadece yaşayan kutular. Kotalar: günde 200 kutu, 500 MB. Masaüstünde Yeni Gönderi'de "Kutunun ömrü"
   ve "Açılınca sil", Gelen Kutusu'nda kalan süre ve "Açılınca silinir" etiketi; açılınca silinen içerik kaydedilene
   kadar ekranda kalır. Uçtan uca denendi (18 kontrol, veritabanı doğrudan sorgulanarak): açılınca silinen,
   "okudum ve sil" ile silinen ve ömrü dolan kutunun hiçbir satırı kalmıyor.
7. ✅ (2026-11-06, `main`) TOTP, isteğe bağlı: Anahtarım ekranında "İki adımlı doğrulama" kartı (QR kod, elle
   girilecek anahtar, ilk kodla açma, 10 kurtarma kodu, parola + kodla kapatma). Girişte ve parola sıfırlamada kod
   istenir. RFC 6238 test değerleri sunucu testinde; uçtan uca 20 kontrol geçti (kod ve kurtarma kodu bir kez geçer,
   sıfırlama ikinci adım olmadan reddedilir, anahtar veritabanında kilitli, kurtarma kodlarının sadece özeti var).
8. Her adımda unit testler ve ilgili saldırı testleri (Security.md); sonunda yeni sürüm (MSI) ve README güncellemesi.

## Hatırlatmalar

- **Acil, v2'den bağımsız:** `release-keys/update-private-key.txt` düz dosya olarak duruyor. Ele geçiren herkese imzalı
  zararlı sürüm dağıtabilir. Parolayla şifrelenmeli ya da donanım anahtarına taşınmalı.
- Railway'deki `SERVER_SEAL_KEY` **yedeklenmeli** (veritabanı yedeğinden ayrı). Kaybolursa bütün parolalar ve
  anahtar paketleri açılamaz; herkes parola sıfırlamak zorunda kalır ve gelen kutuları silinir.
- `release-keys/update-private-key.txt` (git'e girmez, 2.0.0 ile değişti) güvenli bir yere **yedeklenmeli** ve sürüm
  sadece bu dosyanın olduğu bilgisayardan yayımlanmalı. Kaybolursa anahtar yine değişir ve kullanıcılar elle kurulum
  yapmak zorunda kalır (1.0.5'te ve 2.0.0'da olduğu gibi).
- 1.0.4 ve 1.0.5 kullananlar 2.0.0'ı bir kez elle kurmalı.
- Vonage erişim bilgileri sadece `server/.env`'e (yerel) ya da ortam değişkenine yazılır, repoya girmez.
- Commit ve push her değişiklikten sonra sormadan yapılır.

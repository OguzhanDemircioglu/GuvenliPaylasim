# Devam Rehberi

> Son güncelleme: 2026-11-06 (gece). Bu dosya "nerede kaldık, sırada ne var" sorusunun cevabıdır.
> Yeni bir oturuma başlarken önce bunu ve [Security.md](Security.md)'yi, sonra [Crypto_Design.md §7](Crypto_Design.md#7-v2-herkes-kullanıcı-telefon-ve-davet-kodu)'yi ve [§8](Crypto_Design.md#8-gizlilik-sunucu-daha-kısa-süre-bilsin)'i okuyun.

## Nerede kaldık (2026-11-06 gece) — buradan başlayın

**v2 sunucu tarafı ve masaüstü kodu bitti, `main`'de ve Railway'de canlı.** Kodlama sırasının 1–7. adımları tamam
(aşağıda ✅). Yaygın parola kontrolü de yapıldı. **v2 masaüstü 2.0.0 GitHub'da yayında.**

### 2.0.0 (2026-11-06 gece) — yapılanlar ve kalan
- Varsayılan sunucu Railway (`server.url=https://guvenlipaylasim.up.railway.app`, sabitleme açık). `desktop\run.cmd`
  `GPS_SERVER_URL` verilmezse yerel sunucuya (`http://localhost:8080`) bağlanır.
- **Güncelleme imza anahtarı değişti.** "OğuzhanMonster" bilgisayarındaki `release-keys/` 1.0.4'ün eski anahtarıydı;
  1.0.5'in anahtarı ("Oğuzhan Acer" bilgisayarında üretilmişti) burada yoktu. Yeni çift üretildi, açık anahtar
  uygulamaya gömüldü; eski anahtar `release-keys/retired-1.0.4/`. 1.0.5 kullananlar 2.0.0'ı bir kez elle kurar.
  Ayrıntı: [Update_Design.md §2](Update_Design.md).
- MSI üretildi ve **zorunlu** olarak imzalandı (`desktop/dist/GuvenliPaylasim-2.0.0.msi` + `release.json`, git'te
  yok); imza gömülü açık anahtarla doğrulandı. WiX 3.14.1 `C:\Projeler\.tools\wix3`'e açıldı.
- GitHub release `v2.0.0` açıldı (`gh` CLI, `OguzhanDemircioglu` hesabıyla girişli); GitHub'dan indirilen MSI'ın
  özeti `release.json` ile aynı. README'deki indirme düğmesi çalışıyor.
- **Akıllı Uygulama Denetimi (Smart App Control) imzasız MSI'ı reddediyor** ("Sistem yöneticisi, bu yüklemeyi
  engelleyecek ilkeler ayarladı"; msiexec 1625, günlükte 1718). Aynı uygulamanın kurulumsuz klasörü (jpackage
  app-image) internetten gelmiş işaretiyle bile çalıştı. Release'e `GuvenliPaylasim-2.0.0-kurulumsuz.zip` eklendi,
  `package.cmd` artık ZIP'i de üretir. Kalıcı çözüm kod imzalama (ör. Azure Trusted Signing, aylık ücretli); demo
  için ertelendi. **Güncelleyici (2026-11-07, 2.0.2 ile yayında):** Akıllı Uygulama Denetimi açıksa MSI başlatılmaz,
  "ZIP'i indir" gösterilir (Update_Design §2b). 2.0.1 ve öncesi bunu bilmez: onlarda "Şimdi güncelle" MSI'ı dener,
  Windows engeller; ZIP elle indirilir. Sürümler zorunlu yapılmamalı. ZIP'in özeti imzalı değil (Security S7).
- Giriş ekranında sürüm sağ altta, kayıt ekranında sol altta, lacivert zeminde beyaz; geçiş sırasında gizli (2.0.2).
- **2.0.3 için kodlandı, yayınlanmadı (2026-11-07):** sürüm `v2.0.3` biçiminde, kenarı bilgi yazılarıyla aynı hizada;
  "Kod gönder" sonrası 300 sn geri sayım; kayıtta ve girişte "Bu cihaza güven (30 gün)"; kenar çubuğunda kendi logomuz;
  ad soyad 2–3 kelime, kelime başları Türkçe kurala göre büyük (sunucu da 2–3 kelimeyi denetler).
- **Açık soru: KVKK.** Kayıtta aydınlatma metni ve yurt dışına aktarım için açık rıza gerekip gerekmediği konuşulacak
  (sunucu Railway, Türkiye dışında).
- **Ücretsiz kod imzalama: SignPath Foundation (ertelendi, 2026-11-06).** Demo şimdilik ZIP ile sürüyor. Başvurudan
  önce gerekenler: lisans (**karar: MIT**, `LICENSE` henüz eklenmedi), MSI/ZIP'i GitHub Actions'ta kaynaktan üretmek
  (doğrulanabilir derleme), README'de "Code signing policy" bölümü (roller; "Free code signing provided by
  SignPath.io, certificate by SignPath Foundation"), gizlilik politikası, GitHub hesabında MFA, depo açıklamasını v2'ye
  göre düzeltmek. Riskler: "doğrulanabilir itibar" isteniyor (depo yeni, yıldızsız); yayıncı "SignPath Foundation"
  görünür; Akıllı Uygulama Denetimi'nin bu imzayı kabul ettiği sayfalarda yazmıyor. Her sürüm SignPath'te elle
  onaylanır; `release.json` imzası imzalı MSI'a göre sonradan atılır. Şartlar: https://signpath.org/terms
- **Kalan:** yeni özel anahtar (`release-keys/update-private-key.txt`) bu bilgisayardan başka, çevrimdışı bir yere
  yedeklenmeli; Acer'daki 1.0.5 anahtarı artık kullanılmaz.

### Doküman borcu (2.0.0'dan sonra)
README'nin v1 bölümleri 2.0.0 ile düzeltildi. v2 kodlanırken §7, §8, Security.md ve bu rehber güncel tutuldu;
şunlar hâlâ v1'i anlatıyor:
- **Backend_Architecture.md** (en büyüğü): roller, menüler, birimler, ilk süperadmin, MapStruct, `BaseService`, soft
  delete, kayıt hız sınırı silindi; yeni modüller (invite, contact, verification, totp, `ServerSeal`, kutu ömrü,
  temizlik işleri, `SendQuota`) hiç anlatılmıyor.
- **Crypto_Design.md §1–§6**: e-posta tuzu, ilk girişte anahtar, yönetici sıfırlaması, birime gönderim, SMS
  bildirimi. §7 başlığındaki "Planlandı" notu da eski. §7–§8'e göre yeniden yazılmalı ya da "v1 (tarihçe)" diye
  ayrılmalı.
- **server/README.md**: v1 izleri.

### Bilinen açıklar ve sonraya kalanlar
- Günlük 200 kutu sınırı silinen kutuları saymıyor (sayım var olan kutulardan); kesin sayaç ayrı iş.
- Güncelleme imza anahtarı hâlâ düz dosya (acil madde, Hatırlatmalar).
- Security.md test planında eksik: uç noktaların sistematik IDOR testi, OWASP Dependency-Check, fuzz, sızma testi.
- Gerçek kullanıcıya açılmadan önce: kayıtlı SMS gönderen adı ya da Meta onaylı WhatsApp hesabı (şu an WhatsApp
  sandbox; sadece sandbox'a katılmış numaralar kod alır), KVKK (sunucu Türkiye dışında).

### Başka bilgisayarda (evde) çalışmaya başlamak
- `git pull` (dal: `main`). `v2` dalı birleştirildi, artık kullanılmıyor.
- JDK 25 gerekir. Bu makinede: `~/.jdks/corretto-25.0.4.1`. Maven wrapper (`mvnw`) kurum ağındaki sertifika iptal
  kontrolü yüzünden indiremedi; yerel Maven (`C:\tools\apache-maven-3.9.9`) kullanıldı. Evde `./mvnw` muhtemelen
  çalışır.
- Testler: `server/` ve `desktop/` klasörlerinde `mvn test` (son durum: sunucu 142, masaüstü 50 test, hepsi geçiyor).
- Yerel sunucu: `server/.env` git'te yok. Gerekenler: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (Aiven ya da yerel
  PostgreSQL), **`DB_SCHEMA=gps_v2`** (eski `guvenli_paylasim` şeması v1'dir). `SERVER_SEAL_KEY` dev'de verilmezse
  herkesin bildiği sabit dev anahtarı kullanılır. `MESSAGING_PROVIDER` verilmezse doğrulama kodları sunucu log'una
  yazılır (WhatsApp gerekmez). Çalıştırma: `mvn spring-boot:run -Dspring-boot.run.profiles=dev`.
- Demo kullanıcıları (sadece dev): `5550000001`–`5550000004`, parola `DemoParola2026!`.
- Masaüstünü yerel sunucuya bağlamak: varsayılan `http://localhost:8080`; Railway için
  `GPS_SERVER_URL=https://guvenlipaylasim.up.railway.app`.
- Ekran görüntüsü aracı: `desktop` testlerindeki `com.desktop.ui.views.ScreenshotTour` (yerel sunucu açıkken).
- Railway: proje `faithful-enthusiasm`, servis `GuvenliPaylasim` (kök `/server`), `main`'e her push deploy eder.
  Claude için Railway MCP bu bilgisayarın `~/.claude.json` dosyasında (proje token'ı ile) tanımlı; evde yoksa Railway
  panelinden yeni token alınıp `railway mcp local` aynı şekilde eklenir. Sohbette paylaşılan token yenilenmeli.

## Çalışma kuralı

**Önce konuşulur, sonra kod yazılır.** Aşağıdaki konuların hepsi konuşulup kararlar `Crypto_Design.md`'ye yazılmadan
v2 kodlamasına başlanmaz. Her kararda neyin gerekli, neyin gereksiz olduğu birlikte belirlenir.

## Ürünün amacı

Kimsenin açamayacağı bir kutuya dosya ya da mesaj konur; kutu sadece alıcının bilgisayarında açılır. Herkes eşit
kullanıcıdır; kapsam tek bir ağ ya da kurumla sınırlı değildir. Parolasını kaybeden, kendisine gelmiş kutuları da
kaybeder.

Kapsam dışı: para ya da kripto para transferi, izlenemez ödeme veya takas sistemi.

## Durum

| Konu | Durum |
|---|---|
| v1 (rollü, birimli prototip) | Masaüstü 1.0.5; sunucu artık v2, 1.0.5 bağlanamaz |
| v2 sunucu | **Canlı** (Railway, `main`) |
| v2 masaüstü | **2.0.0 yayında** (GitHub release `v2.0.0`) |
| 1. Temel dönüşüm (herkes kullanıcı, telefon, davet kodu, kişiler) | **Kodlandı**, Crypto_Design §7 |
| 2. Gizlilik | **Kodlandı**, Crypto_Design §8 |
| Yaygın parola kontrolü | **Yapıldı** (masaüstünde, `PasswordStrength`) |
| 3. Karşılıklı teslim (kasa) | **Konuşuldu: yapılmayacak** (gerekçe aşağıda) |
| Güvenlik: tehdit modeli ve test planı | **Konuşuldu**, [Security.md](Security.md)'de. §7 buna göre güncellendi |
| 4. İnternete açılma | **Konuşuldu**, kararlar aşağıda. Demo sunucusu Railway'de |
| Telefon maskesi `(5xx) xxx xx xx`, alan kısıtları, profilde ad değiştirme, "Beni hatırla" (DPAPI, 30 gün) | **Kodlandı** (2026-11-06 gece), Crypto_Design §7.4, Security S6. Masaüstü **2.0.1** ile yayında (GitHub release `v2.0.1`: MSI, kurulumsuz ZIP, `release.json`; zorunlu değil, çünkü Akıllı Uygulama Denetimi açık bilgisayarda ZIP kullanan MSI'ı kuramaz) |
| TOTP ile giriş | **Yapıldı, isteğe bağlı** (2026-11-06): profilden açılır; girişte ve sıfırlamada kod, kurtarma kodları (§7.4) |

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
> Yayındaki masaüstü 1.0.5 (v1) bu sunucuyla çalışmaz; v2 masaüstü sürümü (MSI) henüz yayınlanmadı.

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

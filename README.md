<p align="center">
  <img src="desktop/src/main/resources/com/desktop/icon.png" alt="Güvenli Paylaşım logosu" width="160">
</p>

<h1 align="center">Güvenli Paylaşım</h1>

<p align="center">
  <b>Uçtan uca şifreli</b> dosya ve mesaj paylaşımı
</p>

<p align="center">
  <a href="https://github.com/OguzhanDemircioglu/GuvenliPaylasim/releases/download/v2.0.5/GuvenliPaylasim-2.0.5.msi">
    <img src="docs/assets/download-windows.svg" alt="Windows için indir" width="560">
  </a>
  <br>
  <sub>Sürüm 2.0.5 · Tıklayınca kurulum dosyası doğrudan iner · Uygulama yeni sürümleri açılışta kendisi kurar ·
  <a href="https://github.com/OguzhanDemircioglu/GuvenliPaylasim/releases">Bütün sürümler</a></sub>
  <br>
  <sub>Kurulum "Sistem yöneticisi, bu yüklemeyi engelleyecek ilkeler ayarladı" derse:
  <a href="https://github.com/OguzhanDemircioglu/GuvenliPaylasim/releases/download/v2.0.5/GuvenliPaylasim-2.0.5-kurulumsuz.zip">kurulumsuz ZIP</a></sub>
</p>

---

## Uygulama ne yapıyor?

> **1.0.5 kullananlar:** 2.0.5'ü yukarıdaki düğmeden bir kez elle kurmalı. Güncelleme imza anahtarı değişti; 1.0.5
> yeni sürümü kendisi kuramaz ve v2 sunucusuna bağlanamaz.

Kimsenin açamayacağı bir kutuya dosya ya da mesaj koyarsınız. Kutu sadece alıcının bilgisayarında açılır. Sunucu
sadece taşıyıcıdır; içeriği hiçbir zaman göremez.

**Parçalar**
- **Masaüstü uygulaması (Java, MSI):** Şifreleme ve şifre çözme burada yapılır. Parola bilgisayardan hiç çıkmaz.
- **Sunucu (Spring + PostgreSQL):** Şifreli kutuları ve açık anahtarları tutar, kutuyu doğru kişiye teslim eder.

**Akış**
1. **Kayıt:** Ad soyad, telefon ve parola girilir; telefon WhatsApp'a gelen kodla doğrulanır. Anahtarlar bu sırada bilgisayarda
   üretilir.
2. **İlk gönderim:** Alıcının telefonu girilir. Uygulama 16 karakterlik bir kod gösterir ve kutu bu kodla kilitlenir.
   Kodu alıcıya siz iletirsiniz (yüz yüze, telefonla).
3. **Alıcı:** Kodu "Kod ile al" ekranına girer, kutu açılır ve iki taraf birbirinin kişi listesine eklenir.
4. **Sonraki gönderimler:** Kod gerekmez, kutu doğrudan kişinin gelen kutusuna düşer.
5. **Kutunun ömrü:** 1 saat ile 30 gün arasıdır. Süre dolunca her şey sunucudan silinir. İstenirse kutu "açılınca
   sil" olarak da gönderilebilir.
6. **Parolayı unutan:** WhatsApp koduyla sıfırlar, ama kendisine gelmiş kutuları kaybeder. Kişilerinin ekranında "anahtarı
   değişti" uyarısı çıkar.

## Nedir?

Eski "Güvenli Bilgi Paylaşım Sistemi"nden (2016) yola çıkan, herkesin eşit kullanıcı olduğu bir prototip. Kapsam
tek bir kurumla sınırlı değildir; rol ve birim yoktur. Kutular **gönderenin bilgisayarında** şifrelenip imzalanır,
sadece **alıcının bilgisayarında** çözülür. Sunucu dahil hiç kimse içeriği göremez; parola hiçbir kanaldan
gönderilmez.

## Neden güvenli?

| Konu | Eski sistem | Güvenli Paylaşım |
|---|---|---|
| Anahtar paylaşımı | Anahtarlar alıcılara **SMS ile açık metin** gider | Paylaşılan parola yok; her kişinin kendi anahtar çifti var |
| Anahtarın gücü | Kullanıcının yazdığı değer (`123`, `abc`) | Her belgeye rastgele 256 bit AES anahtarı |
| Alıcıya özel erişim | Aynı anahtar bütün birimlere gider | Kutunun anahtarı alıcının açık anahtarına sarmalanır |
| İlk temas | — | Tek seferlik davet koduyla iki taraf birbirini doğrular; anahtarı değişen kişiye yeni kod gerekir |
| Gönderenin kimliği | Kanıtlanamaz | Ed25519 imzası; sahte ya da değiştirilmiş kutu açılmaz |
| Sunucu ele geçirilirse | İçerik açılabilir | Sunucuda sadece şifreli veri var, açacak anahtar yok |
| Parola | Sunucuya gönderilir | Bilgisayardan çıkmaz; sunucudaki özeti ayrıca sunucu anahtarıyla kilitli |
| Silme | İsteğe bağlı | Her kutunun ömrü var (en fazla 30 gün); süre dolunca ya da "açılınca sil"de iz bırakmadan silinir |

Ayrıntılar: [docs/Crypto_Design.md](docs/Crypto_Design.md). Aynı karşılaştırma uygulamada **Neden Güvenli?**
ekranında da gösterilir.

## Kurulum (kullanıcılar)

1. Yukarıdaki **Windows için indir** butonuna tıklayın ve inen `.msi` dosyasını açın.
2. Kurulum kullanıcı başınadır, **yönetici yetkisi istemez**. Java ayrıca gerekmez; uygulamanın içinde gelir.
3. "Güvenli Paylaşım" Başlat menüsüne ve masaüstüne eklenir.
4. Yeni bir sürüm yayımlandığında uygulama açılışta haber verir; imzasını doğruladıktan sonra kendisi kurar.

> **Kurulum engellenirse** ("Sistem yöneticisi, bu yüklemeyi engelleyecek ilkeler ayarladı"): Windows'ta Akıllı
> Uygulama Denetimi açıktır ve kod imzalama sertifikası olmayan MSI'ı reddeder. Bunun yerine **kurulumsuz ZIP**'i
> indirin, bir klasöre çıkarın ve içindeki `GuvenliPaylasim.exe`'yi çalıştırın. Akıllı Uygulama Denetimi açıkken
> yeni sürümler de kendiliğinden kurulamaz; yeni sürümde ZIP yeniden indirilir.

> Uygulama varsayılan olarak demo sunucusuna (`https://guvenlipaylasim.up.railway.app`) bağlanır. Başka bir sunucu
> için `GPS_SERVER_URL` ortam değişkeni verilir (ör. `GPS_SERVER_URL=https://paylasim.ornek.com`). Demo sunucusunda
> kayıt kodu WhatsApp sandbox'ından gelir; telefonun önce Vonage sandbox numarasına katılma mesajı atmış olması
> gerekir.

---

## Geliştiriciler için

| Klasör | İçerik |
|---|---|
| [server/](server/) | Spring Boot 4.1, Java 25, PostgreSQL 18. StarterApp şablonundan türetildi. Kullanıcılar, anahtar dizini, davet kodları, kişiler, şifreli kutular |
| [desktop/](desktop/) | JavaFX 27 + AtlantaFX masaüstü istemci. Şifreleme, imza ve zorunlu güncelleme burada |
| [docs/Devam_Rehberi.md](docs/Devam_Rehberi.md) | Nerede kaldık, sırada ne var (v2 yol haritası) |
| [docs/Crypto_Design.md](docs/Crypto_Design.md) | Şifreleme tasarımı ve eski sistemle karşılaştırma |
| [docs/Update_Design.md](docs/Update_Design.md) | GitHub üzerinden imzalı, zorunlu güncelleme |
| [docs/UI_Standards.md](docs/UI_Standards.md) | Arayüz ve form standartları (StarterApp ile ortak): metinler, alan kuralları, uyarılar, düğmeler |
| [docs/Backend_Architecture.md](docs/Backend_Architecture.md) | Sunucunun bağlayıcı kuralları (StarterApp'ten; §20 bu projeye özgü eklemeler) |

### Gereksinimler

- JDK 25. `run`/`package` betikleri sırayla `%USERPROFILE%\.jdks\*25*` (IntelliJ'nin indirdiği JDK'lar) ve
  `Projeler\.tools\jdk` altında arar, bulduğunu kullanır; sistemdeki `JAVA_HOME` Java 17/21 olabilir.
- PostgreSQL (bilgisayara kurulu ya da Aiven gibi bir bulut veritabanı).
- Maven gerekmez (wrapper var). Kurum proxy'si için Maven ayarı `~/.m2/settings.xml`'de; HTTPS sertifikası için
  betikler `-Djavax.net.ssl.trustStoreType=Windows-ROOT` verir.

### Demo (tek bilgisayarda)

1. **JWT anahtarlarını üret** (sadece ilk sefer; `server/keys/` git'e girmez):
   ```bash
   mkdir -p server/keys
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out server/keys/private.pem
   openssl rsa -in server/keys/private.pem -pubout -out server/keys/public.pem
   ```
2. **PostgreSQL'i hazırla.** Kurulu PostgreSQL'de bir kez (`psql -U postgres` ya da pgAdmin):
   ```sql
   CREATE USER guvenlipaylasim WITH PASSWORD '...';
   CREATE DATABASE guvenlipaylasim OWNER guvenlipaylasim;
   ```
   Bağlantı bilgisi `server/.env` dosyasına yazılır (git'e girmez):
   ```properties
   DB_URL=jdbc:postgresql://localhost:5432/guvenlipaylasim
   DB_USERNAME=guvenlipaylasim
   DB_PASSWORD=...
   DB_SCHEMA=gps_v2
   ```
   Aiven gibi bulut veritabanlarında `DB_URL`'in sonuna `?sslmode=require` eklenir. `MESSAGING_PROVIDER` verilmezse
   doğrulama kodları WhatsApp'a gitmez, sunucu log'una yazılır. Ayrıntılar: [server/README.md](server/README.md).
3. **Sunucuyu başlat** (demo kullanıcıları ilk açılışta oluşur; http://localhost:8080):
   ```bash
   server\run-dev.cmd
   ```
4. **İstemciyi iki kez başlat** (iki pencere = iki kullanıcı; `run.cmd` yerel sunucuya bağlanır):
   ```bash
   desktop\run.cmd
   ```
5. **Demo hesapları** (sadece dev profilinde; parola `DemoParola2026!`):

   | Telefon | Ad soyad |
   |---|---|
   | `5550000001` | Ayşe Yılmaz |
   | `5550000002` | Mehmet Kaya |
   | `5550000003` | Zeynep Demir |
   | `5550000004` | Ali Çelik |

6. **Önerilen sunum akışı:** Mehmet ile giriş → **Neden Güvenli?** → **Yeni Gönderi** (davet kodu ile, Ayşe'nin
   telefonu; tek seferlik kodu göster) → Ayşe'nin penceresinde **Kod ile al** → kutu açılır, iki taraf kişi olur →
   ikinci gönderim kodsuz ve bildirimle gelir → **Okudum ve sil** → Mehmet'in **Gönderilenler**'inden de kalkar.

Demo verisini sıfırlamak için sunucu kapalıyken şemayı silin; bir sonraki açılışta yeniden oluşur:
```sql
DROP SCHEMA gps_v2 CASCADE;
```

### Testler

```bash
cd server && mvnw.cmd test
```
```bash
cd desktop && mvnw.cmd test
```

Testler veritabanı ya da Docker gerektirmez. Masaüstü ekranlarının görüntüsü, çalışan dev
sunucusuna karşı `ScreenshotTour` (test kaynakları) ile pencere açmadan alınabilir.

### Kurulum dosyası ve sürüm yayımlama

```bash
desktop\package.cmd
```

`desktop/dist/GuvenliPaylasim-<sürüm>.msi` ve `GuvenliPaylasim-<sürüm>-kurulumsuz.zip` üretilir; ikisi de release'e
eklenir. MSI (Java runtime içinde; kullanıcı başına kurulur, yönetici
yetkisi istemez). Uygulama Windows'ta "Güvenli Paylaşım" adıyla görünür (Başlat menüsü, masaüstü, yüklü
uygulamalar); Türkçe karakterler için WiX'e Türkçe kod sayfası (1254) `desktop/src/main/jpackage/` altından verilir.
WiX 3.14 gerekir (`Projeler\.tools\wix3` ya da PATH; `wix314-binaries.zip` oraya açılır). İmzalama ve GitHub'a yükleme:
[docs/Update_Design.md §4](docs/Update_Design.md). Uygulama güncellemeleri
`OguzhanDemircioglu/GuvenliPaylasim` deposunun Releases bölümünden okur.

Yeni sürüm yayımlanınca bu README'nin başındaki indirme bağlantısı ve sürüm numarası da güncellenir.

### Henüz yapılmayanlar

- Gerçek kullanıcı için doğrulama kanalı: kayıtlı SMS gönderen adı ya da Meta onaylı WhatsApp hesabı (demo şu an
  WhatsApp sandbox'ında; sadece sandbox'a katılmış numaralar kod alır).
- KVKK: demo sunucusu Türkiye dışında; gerçek veriden önce aydınlatma metni ve sunucu yeri.
- Sistematik IDOR testi, bağımlılık taraması, fuzz ve dışarıdan sızma testi ([docs/Security.md](docs/Security.md)).

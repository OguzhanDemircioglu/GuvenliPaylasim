<p align="center">
  <img src="desktop/src/main/resources/com/desktop/icon.png" alt="Güvenli Paylaşım logosu" width="160">
</p>

<h1 align="center">Güvenli Paylaşım</h1>

<p align="center">
  Kurum içi, <b>uçtan uca şifreli</b> belge paylaşım sistemi
</p>

<p align="center">
  <a href="https://github.com/OguzhanDemircioglu/GuvenliPaylasim/releases/download/v1.0.5/GuvenliPaylasim-1.0.5.msi">
    <img src="docs/assets/download-windows.svg" alt="Windows için indir" width="560">
  </a>
  <br>
  <sub>Sürüm 1.0.5 · Tıklayınca kurulum dosyası doğrudan iner · Uygulama yeni sürümleri açılışta kendisi kurar ·
  <a href="https://github.com/OguzhanDemircioglu/GuvenliPaylasim/releases">Bütün sürümler</a></sub>
</p>

---

## Uygulama ne yapıyor?

> Bu bölüm geliştirilmekte olan **v2**'yi anlatır. Yayındaki 1.0.5 sürümü hâlâ v1'dir (rollü, birimli prototip).

Kimsenin açamayacağı bir kutuya dosya ya da mesaj koyarsınız. Kutu sadece alıcının bilgisayarında açılır. Sunucu
sadece taşıyıcıdır; içeriği hiçbir zaman göremez.

**Parçalar**
- **Masaüstü uygulaması (Java, MSI):** Şifreleme ve şifre çözme burada yapılır. Parola bilgisayardan hiç çıkmaz.
- **Sunucu (Spring + PostgreSQL):** Şifreli kutuları ve açık anahtarları tutar, kutuyu doğru kişiye teslim eder.

**Akış**
1. **Kayıt:** Ad soyad, telefon ve parola girilir; telefon SMS koduyla doğrulanır. Anahtarlar bu sırada bilgisayarda
   üretilir.
2. **İlk gönderim:** Alıcının telefonu girilir. Uygulama 16 karakterlik bir kod gösterir ve kutu bu kodla kilitlenir.
   Kodu alıcıya siz iletirsiniz (yüz yüze, telefonla).
3. **Alıcı:** Kodu "Kod ile al" ekranına girer, kutu açılır ve iki taraf birbirinin kişi listesine eklenir.
4. **Sonraki gönderimler:** Kod gerekmez, kutu doğrudan kişinin gelen kutusuna düşer.
5. **Kutunun ömrü:** 1 saat ile 30 gün arasıdır. Süre dolunca her şey sunucudan silinir. İstenirse kutu "açılınca
   sil" olarak da gönderilebilir.
6. **Parolayı unutan:** SMS ile sıfırlar, ama kendisine gelmiş kutuları kaybeder. Kişilerinin ekranında "anahtarı
   değişti" uyarısı çıkar.

## Nedir?

Eski "Güvenli Bilgi Paylaşım Sistemi"nin (2016) yerini alacak prototip. Belgeler **gönderenin bilgisayarında**
şifrelenip imzalanır, sadece **seçilen alıcıların bilgisayarında** çözülür. Sunucu dahil hiç kimse içeriği göremez;
SMS ile parola gönderilmez.

## Neden güvenli?

| Konu | Eski sistem | Güvenli Paylaşım |
|---|---|---|
| Anahtar paylaşımı | Anahtarlar alıcılara **SMS ile açık metin** gider | Paylaşılan parola yok; her kişinin kendi anahtar çifti var |
| Anahtarın gücü | Kullanıcının yazdığı değer (`123`, `abc`) | Her belgeye rastgele 256 bit AES anahtarı |
| Alıcıya özel erişim | Aynı anahtar bütün birimlere gider | Belge anahtarı her alıcıya ayrı ayrı sarmalanır |
| Gönderenin kimliği | Kanıtlanamaz | Ed25519 imzası; sahte ya da değiştirilmiş belge açılmaz |
| Sunucu ele geçirilirse | İçerik açılabilir | Sunucuda sadece şifreli veri var, açacak anahtar yok |
| Parola | Sunucuya gönderilir | Bilgisayardan çıkmaz |
| Silme | İsteğe bağlı | Son alıcı da silince şifreli içerik kalıcı olarak silinir |

Ayrıntılar: [docs/Crypto_Design.md](docs/Crypto_Design.md). Aynı karşılaştırma uygulamada **Neden Güvenli?**
ekranında da gösterilir.

## Kurulum (kullanıcılar)

1. Yukarıdaki **Windows için indir** butonuna tıklayın ve inen `.msi` dosyasını açın.
2. Kurulum kullanıcı başınadır, **yönetici yetkisi istemez**. Java ayrıca gerekmez; uygulamanın içinde gelir.
3. "Güvenli Paylaşım" Başlat menüsüne ve masaüstüne eklenir.
4. Yeni bir sürüm yayımlandığında uygulama açılışta haber verir; imzasını doğruladıktan sonra kendisi kurar.

> Uygulama varsayılan olarak `http://localhost:8080` adresindeki sunucuya bağlanır. Başka bir sunucu için
> `GPS_SERVER_URL` ortam değişkeni verilir (ör. `GPS_SERVER_URL=https://paylasim.kurum.local`).
> Demo sunucusu: `GPS_SERVER_URL=https://guvenlipaylasim.up.railway.app`

---

## Geliştiriciler için

| Klasör | İçerik |
|---|---|
| [server/](server/) | Spring Boot 4.1, Java 25, PostgreSQL 18. StarterApp şablonundan türetildi. Birimler, kullanıcılar, anahtar dizini, şifreli belgeler |
| [desktop/](desktop/) | JavaFX 27 + AtlantaFX masaüstü istemci. Şifreleme, imza ve zorunlu güncelleme burada |
| [docs/Devam_Rehberi.md](docs/Devam_Rehberi.md) | Nerede kaldık, sırada ne var (v2 yol haritası) |
| [docs/Crypto_Design.md](docs/Crypto_Design.md) | Şifreleme tasarımı ve eski sistemle karşılaştırma |
| [docs/Update_Design.md](docs/Update_Design.md) | GitHub üzerinden imzalı, zorunlu güncelleme |
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
   DB_SCHEMA=guvenlipaylasim
   ```
   Aiven gibi bulut veritabanlarında `DB_URL`'in sonuna `?sslmode=require` eklenir. Ayrıntılar:
   [server/README.md](server/README.md).
3. **Sunucuyu başlat** (demo birimleri ve kullanıcıları ilk açılışta oluşur; http://localhost:8080):
   ```bash
   server\run-dev.cmd
   ```
4. **İstemciyi iki kez başlat** (iki pencere = iki kullanıcı):
   ```bash
   desktop\run.cmd
   ```
5. **Demo hesapları** (parola `Demo12345!`):

   | E-posta | Birim |
   |---|---|
   | `ayse.yilmaz@gps.local` | Ankara Valiliği |
   | `mehmet.kaya@gps.local` | İstanbul Valiliği |
   | `zeynep.demir@gps.local` | İstanbul Valiliği |
   | `ali.celik@gps.local` | İzmir Valiliği |
   | `admin@gps.local` (parola `Admin12345`) | Bilgi İşlem — yönetici |

   Her kullanıcının anahtarları **ilk girişinde** üretilir; belge gönderilebilmesi için alıcının bir kez giriş
   yapmış olması gerekir.

6. **Önerilen sunum akışı:** Mehmet ile giriş → **Neden Güvenli?** → **Yeni Gönderi** (Ankara Valiliği'ni seç,
   şifreleme adımlarını göster) → Ayşe'nin penceresinde bildirim → belgeyi aç (imza doğrulandı) → **Okudum ve sil** →
   Mehmet'te **Gönderilenler**'de "okudu ve sildi". Yönetici hesabıyla yeni kullanıcı açıp ilk girişte zorunlu parola
   değişikliğini ve anahtar üretimini göster.

Demo verisini sıfırlamak için sunucu kapalıyken şemayı silin; bir sonraki açılışta yeniden oluşur:
```sql
DROP SCHEMA guvenlipaylasim CASCADE;
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

`desktop/dist/GuvenliPaylasim-<sürüm>.msi` üretilir (Java runtime içinde; kullanıcı başına kurulur, yönetici
yetkisi istemez). Uygulama Windows'ta "Güvenli Paylaşım" adıyla görünür (Başlat menüsü, masaüstü, yüklü
uygulamalar); Türkçe karakterler için WiX'e Türkçe kod sayfası (1254) `desktop/src/main/jpackage/` altından verilir.
WiX 3.14 gerekir (`D:\Projeler\.tools\wix3` ya da PATH). İmzalama ve GitHub'a yükleme:
[docs/Update_Design.md §4](docs/Update_Design.md). Uygulama güncellemeleri
`OguzhanDemircioglu/GuvenliPaylasim` deposunun Releases bölümünden okur.

Yeni sürüm yayımlanınca bu README'nin başındaki indirme bağlantısı ve sürüm numarası da güncellenir.

### Henüz yapılmayanlar

- Kurum SMS servisi (`SmsGateway` arayüzü hazır; şimdilik simülasyon).
- LDAP girişi (bkz. Crypto_Design §6).
- Prod sunucusuna kurulum. Ortam değişkenleri `server/.env.example`'daki liste (prod `.env` okumaz), ama sunucu,
  HTTPS ve servis olarak çalıştırma henüz yok.

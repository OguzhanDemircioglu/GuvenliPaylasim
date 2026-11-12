<p align="center">
  <img src="desktop/src/main/resources/com/desktop/icon.png" alt="Lockpost logosu" width="160">
</p>

<h1 align="center">Lockpost</h1>

<p align="center">
  <b>Uçtan uca şifreli</b> dosya ve mesaj paylaşımı
</p>

<p align="center">
  <a href="https://github.com/OguzhanDemircioglu/Lockpost/releases/download/v2.1.0/Lockpost-2.1.0-kurulumsuz.zip">
    <img src="docs/assets/download-windows.svg" alt="Windows için indir" width="560">
  </a>
  <br>
  <sub>Sürüm 2.1.0 · Uygulama yeni sürümleri açılışta kendisi bulur ·
  <a href="https://github.com/OguzhanDemircioglu/Lockpost/releases">Bütün sürümler</a></sub>
  <br>
  <sub>Kurulum dosyası tercih ederseniz:
  <a href="https://github.com/OguzhanDemircioglu/Lockpost/releases/download/v2.1.0/Lockpost-2.1.0.msi">MSI ile kur</a>
  (Akıllı Uygulama Denetimi açıksa Windows engelleyebilir) · <a href="#code-signing-policy">Code signing policy</a></sub>
</p>

---

## Ne işe yarar?

Bir dosyayı ya da mesajı, sadece alıcının açabileceği bir kutuya koyarsınız. Kutu gönderenin bilgisayarında
şifrelenip imzalanır, sadece alıcının bilgisayarında açılır. Sunucu yalnızca taşıyıcıdır: içeriği, şifrenizi ve
anahtarlarınızı hiçbir zaman göremez.

## Nasıl çalışır?

![Lockpost sistem yaşam döngüsü](docs/assets/sistem_yasam_dongusu.png)

1. **Kayıt:** Ad soyad, cep telefonu ve şifre girilir; telefon WhatsApp'a gelen kodla doğrulanır. Şifreleme
   anahtarları bu sırada bilgisayarda üretilir.
2. **Kişi ekleme:** "Kişi Ekle" (ya da ilk gönderimde "Davet Kodu ile") 16 karakterlik bir kod üretir. Kodu karşı
   tarafa siz iletirsiniz (yüz yüze, telefonla). Karşı taraf kodu "Kod ile Al"a girince ikiniz birbirinizin
   kişileri olursunuz. Kod, sunucunun bile araya girememesini sağlar.
3. **Gönderim:** Kişilerinize kodsuz gönderirsiniz; kutu doğrudan gelen kutularına düşer.
4. **Kutunun ömrü:** 1 saat ile 30 gün arası. Süre dolunca kutu sunucudan iz bırakmadan silinir.

**Henüz kayıt olmamış birine göndermek:** Yeni Gönderi'de "Davet Kodu ile"yi seçip kişinin telefonunu yazarsınız.
Kutu bir davet koduyla kilitlenir ve kod size gösterilir; kodu kişiye kendiniz iletirsiniz. Kişi, kodun geçerlilik
süresi (1 saat–7 gün) içinde uygulamayı kurup aynı telefonla kayıt olur ve kodu "Kod ile Al"a girer: kutu açılır,
ikiniz kişi olursunuz. Kod girilmezse kutu süre dolunca içeriğiyle silinir. Kişiye SMS ya da bildirim gitmez; kod
girilene kadar kutuyu kimse göremez. Gönderen, numaranın kayıtlı olup olmadığını öğrenmez.

## Geliştirici Gözünden Nasıl Çalışır?

Bütün şifreleme masaüstü uygulamasında yapılır; sunucu (Spring Boot + PostgreSQL) sadece şifreli veriyi saklar ve
doğru kişiye teslim eder. Aşama aşama anlatım: [Sistem_Yasam_Dongusu.md](docs/Sistem_Yasam_Dongusu.md); ayrıntılı
tasarım: [Crypto_Design.md](docs/Crypto_Design.md) §2–§4, §7–§8.

| Adım | Ne olur |
|---|---|
| **Şifre** | `master = PBKDF2-HMAC-SHA256(şifre, tuz = telefon, 600.000 tur)`. Bundan iki değer türer: `authSecret` sunucuya giriş için gider (sunucu bcrypt'ler ve `SERVER_SEAL_KEY` ile bir kez daha AES-GCM'le kilitler), `kek` bilgisayardan hiç çıkmaz. Gerçek şifre sunucuya gitmez |
| **Anahtarlar** | Kayıtta bilgisayarda üretilir: şifreleme için X25519, imza için Ed25519. Özel anahtarlar `kek` ile AES-256-GCM şifrelenmiş paket olarak sunucuda durur; girişte bilgisayarda açılır, sadece bellekte tutulur |
| **Gönderim** | Her kutuya rastgele 256 bit anahtar `K`. İçerik (dosya adı dahil) `AES-256-GCM(K)` ile şifrelenir, gönderen Ed25519 ile imzalar. `K` her alıcı için ayrı sarmalanır: geçici X25519 + HKDF-SHA256 + AES-GCM (ECIES benzeri) |
| **Açma** | Alıcı önce imzayı doğrular (geçersizse içerik çözülmez), sonra `K`'yı kendi X25519 anahtarıyla açar, içeriği çözer |
| **Davet kodu** | 16 karakter (80 bit). Kutu anahtarı koddan HKDF ile türetilen anahtarla sarmalanır; sunucuya kodun kendisi değil, sadece özeti gider. Alıcı kodu girince iki taraf birbirinin açık anahtar parmak izini doğrular (kabul mührü, HMAC); sunucu araya sahte anahtar koyamaz |
| **Kişiler** | Doğrulanmış parmak izi, sahibinin Ed25519 imzasıyla saklanır; sunucu değiştiremez. Anahtarı değişen kişiye yeni davet kodu gerekir |
| **Sunucu ne görür?** | Kim kime ne zaman, ne boyutta gönderdi; içeriği, dosya adını, şifreyi ve özel anahtarları göremez. Bu bilgi de kutu silinince gider: ömrü dolan kutular, davetler ve oturumlar zamanlanmış işlerle kalıcı silinir (soft delete yok) |
| **Ağ** | Sadece TLS 1.3; uygulama yalnızca Let's Encrypt köklerine zincirlenen sertifikayı kabul eder (sertifika sabitleme). Oturum: 15 dk JWT (RS256) + dönen refresh token |
| **Güncelleme** | GitHub Releases'teki `release.json` Ed25519 ile imzalı; uygulama imzayı ve kurulum dosyasının SHA-256 özetini doğrulamadan kurmaz |
| **Kayıt doğrulama** | Telefon WhatsApp koduyla (Vonage) doğrulanır; numara, IP ve günlük sınırlar var. Kayıtlı ve kayıtsız numara aynı cevabı aynı sürede alır |

## Özellikler

- **Yazı ve dosya:** resim, Word/Excel/PowerPoint, PDF, metin, arşiv, ses ve görüntü (en fazla 10 MB). Programlar ve
  betikler gönderilemez.
- **Riskli dosya uyarısı:** makrolu Office, betikli PDF, içinde program olan arşiv gibi dosyalarda alıcı uyarılır ve
  kaydetmeden önce onay verir. Kaydedilen dosyayı Windows ve Office korumalı açar.
- **Açılınca Sil:** kutu alıcı açtığı anda sunucudan silinir.
- **Kaydedilemez:** alıcı yazıyı, resmi ya da PDF'i sadece uygulama içinde görür; kaydedemez, kopyalayamaz, ekran
  görüntüsü alamaz. (Telefonla ekranın fotoğrafını engellemez; caydırıcıdır.)
- **İmza:** her kutu gönderenin anahtarıyla imzalanır; sahte ya da yolda değiştirilmiş kutu açılmaz.
- **Hesap güvenliği:** 12–20 karakterlik güçlü şifre, isteğe bağlı iki adımlı doğrulama (Authenticator),
  "Bu Cihaza Güven (30 gün)", "Hesabımı Sil" (her şey kalıcı silinir).
- **Otomatik güncelleme:** yeni sürüm imzası doğrulanarak kurulur.

## Neden güvenli?

| Konu | Eski sistem (2016) | Lockpost |
|---|---|---|
| Anahtar paylaşımı | Anahtar alıcılara **SMS ile açık metin** gider | Paylaşılan şifre yok; herkesin kendi anahtar çifti var |
| Anahtarın gücü | Kullanıcının yazdığı değer (`123`) | Her kutuya rastgele 256 bit AES anahtarı |
| İlk temas | — | Tek seferlik davet koduyla iki taraf birbirini doğrular |
| Gönderenin kimliği | Kanıtlanamaz | Ed25519 imzası |
| Sunucu ele geçirilirse | İçerik açılabilir | Sunucuda sadece şifreli veri var, açacak anahtar yok |
| Şifre | Sunucuya gönderilir | Bilgisayardan çıkmaz |
| Silme | İsteğe bağlı | Her kutunun ömrü var; süre dolunca iz bırakmadan silinir |

Ayrıntı: [docs/Crypto_Design.md](docs/Crypto_Design.md). Aynı karşılaştırma uygulamadaki **Neden Güvenli?**
ekranında da var.

## Kurulum (kullanıcılar)

1. Yukarıdaki **Windows için indir** düğmesiyle kurulumsuz ZIP'i indirin, bir klasöre çıkarın, içindeki
   `Lockpost.exe`'yi çalıştırın (2.0.8 ve öncesinde `GuvenliPaylasim.exe`). Yönetici yetkisi ve ayrıca Java gerekmez; Windows ayarlarına dokunmanız
   gerekmez.
2. **Kurulum dosyası tercih ederseniz:** alttaki **MSI ile kur** bağlantısından `.msi`'yi indirip açın; "Güvenli
   Paylaşım" Başlat menüsüne ve masaüstüne eklenir. Windows'ta Akıllı Uygulama Denetimi açıksa imzasız MSI
   reddedilebilir; o durumda ZIP'i kullanın.

Uygulama demo sunucusuna (`https://guvenlipaylasim.up.railway.app`) bağlanır. Demo sunucusu **test modunda**:
kayıt ve şifre sıfırlama kodu WhatsApp'a gitmez, uygulamaya kendiliğinden yazılır.

---

## Geliştiriciler için

### Yapı

| Parça | Teknoloji | Nerede |
|---|---|---|
| [desktop/](desktop/) | Java 25, JavaFX 27 + AtlantaFX; şifreleme burada | GitHub Releases (MSI + ZIP) |
| [server/](server/) | Spring Boot 4.1, Java 25 | Railway (`main`'e her push otomatik yayına çıkar) |
| Veritabanı | PostgreSQL 18 | Railway (sunucuyla aynı projede) |
| Doğrulama mesajı | Vonage, WhatsApp sandbox | Demo'da test modu açık |

### Gereksinimler

- **JDK 25.** `run.cmd`, `run-dev.cmd` ve `package.cmd` JDK'yı `%USERPROFILE%\.jdks\*25*` ve `Projeler\.tools\jdk`
  altında kendileri bulur.
- **PostgreSQL** (yerel kurulum yeterli).
- **Maven** gerekmez (`mvnw` var). Kurum ağında wrapper indiremezse yerel Maven kullanılır.
- Sadece kurulum dosyası üretmek için: **WiX 3.14** (`Projeler\.tools\wix3` ya da PATH).

### Yerelde çalıştırma

1. **JWT anahtarları** (bir kez; `server/keys/` git'e girmez):
   ```bash
   mkdir -p server/keys
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out server/keys/private.pem
   openssl rsa -in server/keys/private.pem -pubout -out server/keys/public.pem
   ```
2. **Veritabanı.** PostgreSQL'de bir kez:
   ```sql
   CREATE USER guvenlipaylasim WITH PASSWORD '...';
   CREATE DATABASE guvenlipaylasim OWNER guvenlipaylasim;
   ```
   `server/.env` dosyasını oluşturun (git'e girmez):
   ```properties
   DB_URL=jdbc:postgresql://localhost:5432/guvenlipaylasim
   DB_USERNAME=guvenlipaylasim
   DB_PASSWORD=...
   DB_SCHEMA=gps_v2
   ```
   Tablolar ve demo kullanıcıları ilk açılışta oluşur. Doğrulama kodları sunucu log'una yazılır.
3. **Sunucu** (http://localhost:8080):
   ```bash
   server\run-dev.cmd
   ```
4. **Uygulama**, iki pencere = iki kullanıcı (`run.cmd` yerel sunucuya bağlanır):
   ```bash
   desktop\run.cmd
   ```
5. **Demo hesapları** (sadece yerelde; şifre `DemoParola2026!`): `5550000001` Ayşe Yılmaz, `5550000002` Mehmet
   Kaya, `5550000003` Zeynep Demir, `5550000004` Ali Çelik.

**Sunum akışı:** Mehmet ile giriş → **Neden Güvenli?** → **Kişiler › Kişi Ekle** (Zeynep'in telefonu, kodu göster)
→ Zeynep'in penceresinde **Gelen Kutusu › Kod ile Al** → ikisi kişi olur → Mehmet **Yeni Gönderi** ile bir PDF'i
**Kaydedilemez** gönderir → Zeynep açar, kaydedemez, ekran görüntüsü alamaz → **Okudum ve Sil**.

Demo verisini sıfırlamak için sunucu kapalıyken `DROP SCHEMA gps_v2 CASCADE;` çalıştırın.

### Testler

```bash
cd server && mvnw.cmd test
```
```bash
cd desktop && mvnw.cmd test
```

Veritabanı ya da Docker gerekmez. Son durum: sunucu 160, masaüstü 72 test, hepsi geçiyor.

### Yeni sürüm yayımlama

1. `desktop/pom.xml`'de sürümü artırın. Son hane 9'u geçmez: 2.1.9'dan sonra 2.2.0 gelir.
2. `desktop\package.cmd` → `desktop/dist/` altında MSI ve kurulumsuz ZIP.
3. `release.json`'u imzalayın (özel anahtar sadece sürümü yayımlayan bilgisayarda, git'te yok):
   ```bash
   java -cp desktop/target/classes com.desktop.update.ReleaseTool sign desktop/dist/Lockpost-<sürüm>.msi <sürüm> release-keys/update-private-key.txt
   ```
4. Bu depoda `v<sürüm>` release'ini açıp MSI, ZIP ve `release.json`'u ekleyin:
   ```bash
   gh release create v<sürüm> --title <sürüm> --notes-file notlar.md desktop/dist/Lockpost-<sürüm>.msi desktop/dist/Lockpost-<sürüm>-kurulumsuz.zip desktop/dist/release.json
   ```
5. Bu README'nin başındaki indirme bağlantılarını güncelleyin.

Ayrıntı: [docs/Update_Design.md](docs/Update_Design.md).

### Dokümanlar

| Doküman | İçerik |
|---|---|
| [Devam_Rehberi.md](docs/Devam_Rehberi.md) | Projenin durumu, alınan kararlar, Railway kurulumu, yapılacaklar |
| [Sistem_Yasam_Dongusu.md](docs/Sistem_Yasam_Dongusu.md) | Sistemin 7 aşamalı yaşam döngüsü (kayıttan silmeye); görsel üretmek için kaynak |
| [Crypto_Design.md](docs/Crypto_Design.md) | Şifreleme tasarımı (§7–§8 güncel tasarım) |
| [Security.md](docs/Security.md) | Tehdit modeli (S1–S8) ve test planı |
| [Update_Design.md](docs/Update_Design.md) | İmzalı otomatik güncelleme |
| [UI_Standards.md](docs/UI_Standards.md) | Arayüz ve form standartları |
| [Backend_Architecture.md](docs/Backend_Architecture.md) | Sunucunun genel kuralları (StarterApp'ten); §20 bu projeye özgü |
| [server/README.md](server/README.md) | Sunucu kurulumu ve ortam değişkenleri |

### Gerçek kullanıma geçmeden önce

- **Kod imzalama sertifikası:** olmadan Akıllı Uygulama Denetimi MSI'ı reddeder (şimdilik ZIP ile çözülüyor).
- **Doğrulama mesajı:** test modu kapatılmalı (`TEST_MODE`); kayıtlı SMS gönderen adı ya da Meta onaylı
  WhatsApp hesabı alınmalı (şu an WhatsApp sandbox).
- **KVKK:** aydınlatma metni taslak; avukat incelemesi ve sunucunun yeri (Railway Türkiye dışında).
- **Anahtar yedekleri:** güncelleme imza anahtarı ve sunucudaki `SERVER_SEAL_KEY` çevrimdışı yedeklenmeli.
- **Güvenlik testleri:** sistematik yetki (IDOR) testi, bağımlılık taraması, fuzz ve dışarıdan sızma testi
  ([Security.md](docs/Security.md)).

## Code signing policy

Free code signing provided by [SignPath.io](https://about.signpath.io), certificate by
[SignPath Foundation](https://signpath.org).

- Kurulum dosyaları (MSI ve kurulumsuz ZIP) bu depodaki kaynaktan GitHub Actions'ta üretilir
  ([desktop-release.yml](.github/workflows/desktop-release.yml)) ve her imza isteği elle onaylanır.
- Committers and reviewers: [OguzhanDemircioglu](https://github.com/OguzhanDemircioglu)
- Approvers: [OguzhanDemircioglu](https://github.com/OguzhanDemircioglu)

## Gizlilik (Privacy policy)

Uygulama yalnızca aşağıdaki durumlarda başka sistemlerle bağlantı kurar:

- **Uygulama sunucusu** (demo: Railway): kayıt, giriş, belge ve mesaj gönderme gibi kullanıcının başlattığı
  işlemler. Belgeler ve mesajlar cihazda şifrelenir; sunucu içeriklerini okuyamaz.
- **Telefon doğrulaması:** kayıt ve şifre sıfırlamada telefon numarası, doğrulama kodunu iletmesi için Vonage'a
  (WhatsApp) gönderilir.
- **Güncelleme kontrolü:** açılışta GitHub'dan en son sürüm bilgisi okunur
  ([Lockpost sürümleri](https://github.com/OguzhanDemircioglu/Lockpost/releases)); GitHub'a kullanıcıya
  ait bilgi gönderilmez.

Bunların dışında uygulama hiçbir bilgiyi başka bir sisteme göndermez. Kişisel verilerin işlenmesi KVKK aydınlatma
metninde anlatılır (uygulamada kayıt ekranında).

## Lisans

[Apache License 2.0](LICENSE) altında dağıtılır. Telif bilgisi için [NOTICE](NOTICE).

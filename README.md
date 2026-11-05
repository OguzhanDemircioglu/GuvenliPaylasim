# Güvenli Paylaşım

Kurum içi, **uçtan uca şifreli** belge paylaşım sistemi. Eski "Güvenli Bilgi Paylaşım Sistemi"nin (2016) yerini
alacak prototip: belgeler gönderenin bilgisayarında şifrelenip imzalanır, sadece seçilen alıcıların bilgisayarında
çözülür. Sunucu dahil hiç kimse içeriği göremez; SMS ile parola gönderilmez.

| Klasör | İçerik |
|---|---|
| [server/](server/) | Spring Boot 4.1, Java 25. StarterApp şablonundan türetildi; yerelde H2. Birimler, kullanıcılar, anahtar dizini, şifreli belgeler |
| [desktop/](desktop/) | JavaFX 27 + AtlantaFX masaüstü istemci. Şifreleme, imza ve zorunlu güncelleme burada |
| [docs/Crypto_Design.md](docs/Crypto_Design.md) | Şifreleme tasarımı ve eski sistemle karşılaştırma |
| [docs/Update_Design.md](docs/Update_Design.md) | GitHub üzerinden imzalı, zorunlu güncelleme |
| [docs/Backend_Architecture.md](docs/Backend_Architecture.md) | Sunucunun bağlayıcı kuralları (StarterApp'ten; §20 bu projeye özgü eklemeler) |

## Gereksinimler

- JDK 25 (bu makinede `D:\Projeler\.tools\jdk`; `run` betikleri varsa onu kullanır, sistemdeki `JAVA_HOME` Java 17 olabilir)
- Maven gerekmez (wrapper var). Kurum proxy'si için Maven ayarı `~/.m2/settings.xml`'de; HTTPS sertifikası için
  betikler `-Djavax.net.ssl.trustStoreType=Windows-ROOT` verir.

## Demo (tek bilgisayarda)

1. **Sunucuyu başlat** (H2, demo birimleri ve kullanıcıları):
   ```bash
   server\run-dev.cmd
   ```
   İlk sefer için JWT anahtarları gerekir (`server/keys/`, git'e girmez):
   ```bash
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out server/keys/private.pem
   openssl rsa -in server/keys/private.pem -pubout -out server/keys/public.pem
   ```
2. **İstemciyi iki kez başlat** (iki pencere = iki kullanıcı):
   ```bash
   desktop\run.cmd
   ```
3. Demo hesapları (parola `Demo12345!`):

   | E-posta | Birim |
   |---|---|
   | `ayse.yilmaz@gps.local` | Ankara Valiliği |
   | `mehmet.kaya@gps.local` | İstanbul Valiliği |
   | `zeynep.demir@gps.local` | İstanbul Valiliği |
   | `ali.celik@gps.local` | İzmir Valiliği |
   | `admin@gps.local` (parola `Admin12345`) | Bilgi İşlem — yönetici |

   Her kullanıcının anahtarları **ilk girişinde** üretilir; belge gönderilebilmesi için alıcının bir kez giriş
   yapmış olması gerekir.

4. Önerilen sunum akışı: Mehmet ile giriş → **Neden Güvenli?** → **Yeni Gönderi** (Ankara Valiliği'ni seç, şifreleme
   adımlarını göster) → Ayşe'nin penceresinde bildirim → belgeyi aç (imza doğrulandı) → **Okudum ve sil** →
   Mehmet'te **Gönderilenler**'de "okudu ve sildi". Yönetici hesabıyla yeni kullanıcı açıp ilk girişte zorunlu parola
   değişikliğini ve anahtar üretimini göster.

Demo verisini sıfırlamak için sunucu kapalıyken `server/data/` klasörünü silin.

## Testler

```bash
cd server && mvnw.cmd test
```
```bash
cd desktop && mvnw.cmd test
```

Sunucuda Docker gerektiren PostgreSQL testleri Docker yoksa atlanır. Masaüstü ekranlarının görüntüsü, çalışan dev
sunucusuna karşı `ScreenshotTour` (test kaynakları) ile pencere açmadan alınabilir.

## Kurulum dosyası ve sürüm yayımlama

```bash
desktop\package.cmd
```

`desktop/dist/GuvenliPaylasim-<sürüm>.msi` üretilir (Java runtime içinde; kullanıcı başına kurulur, yönetici
yetkisi istemez). WiX 3.14 gerekir (`D:\Projeler\.tools\wix3` ya da PATH). İmzalama ve GitHub'a yükleme:
[docs/Update_Design.md §4](docs/Update_Design.md). Uygulama güncellemeleri
`OguzhanDemircioglu/GuvenliPaylasim` deposunun Releases bölümünden okur.

## Henüz yapılmayanlar

- Kurum SMS servisi (`SmsGateway` arayüzü hazır; şimdilik simülasyon).
- LDAP girişi (bkz. Crypto_Design §6).
- PostgreSQL ile prod kurulumu.

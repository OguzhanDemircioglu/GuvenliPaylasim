# server

Güvenli Paylaşım'ın Spring Boot backend'i (StarterApp şablonundan türetildi). Şifreli kutuları, açık anahtarları,
davetleri ve kişileri tutar; içeriği göremez. Genel kurallar: [docs/Backend_Architecture.md](../docs/Backend_Architecture.md)
(§20 bu projeye özgü). Şifreleme: [docs/Crypto_Design.md](../docs/Crypto_Design.md).

| | |
|---|---|
| Java | 25 |
| Spring Boot | 4.1.x |
| Veritabanı | PostgreSQL 18 (yerelde kurulu; canlıda Railway Postgres) |
| Canlı | Railway, `https://guvenlipaylasim.up.railway.app` (`main`'e her push deploy eder) |
| Build | Maven (`./mvnw`, Windows PowerShell'de `.\mvnw.cmd`) |

## İlk kurulum

### 1. JWT anahtarları

Uygulama anahtar üretmez; anahtarlar repoya konmaz (`keys/` klasörü `.gitignore`'dadır). `server/` klasöründe:

```bash
mkdir -p keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/private.pem
openssl rsa -in keys/private.pem -pubout -out keys/public.pem
```

Windows'ta `openssl` Git Bash ile birlikte gelir.

### 2. PostgreSQL ve `.env`

Sunucu her profilde PostgreSQL ile çalışır; bağlantı bilgisi verilmezse açılmaz. Dev'de ayarlar `server/.env`
dosyasına yazılır. Bu dosya git'e girmez ve **sadece dev profilinde** okunur; testler ve prod onu hiçbir zaman
kullanmaz. Ortam değişkenleri her zaman `.env`'den önceliklidir.

```properties
DB_URL=jdbc:postgresql://localhost:5432/guvenlipaylasim
DB_USERNAME=guvenlipaylasim
DB_PASSWORD=...
DB_SCHEMA=gps_v2
```

Bilgisayara kurulu bir PostgreSQL'de veritabanı ve kullanıcı bir kez oluşturulur (`postgres` kullanıcısıyla, ör.
pgAdmin ya da `psql -U postgres`):

```sql
CREATE USER guvenlipaylasim WITH PASSWORD '...';
CREATE DATABASE guvenlipaylasim OWNER guvenlipaylasim;
```

- Bağlantı sadece `DB_URL`'den alınır (`DB_HOST`/`DB_PORT`/`DB_NAME` okunmaz). SSL isteyen uzak bir veritabanında
  `DB_URL`'in sonuna `?sslmode=require` eklenir.
- `DB_SCHEMA` küçük harf olmalıdır. Uygulamanın bütün tabloları bu şemada durur; şema yoksa ilk açılışta oluşturulur.
  Başka projelerle paylaşılan bir veritabanında diğer şemalara (ör. `public`) dokunulmaz. Dev profilinde varsayılanı
  `gps_v2`'dir (v2 şeması; v1 tabloları eski şemada kalır, Crypto_Design §7.11); prod'da **zorunludur**.
- Demo verisini sıfırlamak için şema silinir (`DROP SCHEMA gps_v2 CASCADE`); sunucu bir sonraki açılışta
  tabloları ve demo verisini yeniden oluşturur.

Tablolar ilk açılışta Hibernate tarafından (`ddl-auto=update`), demo verisi migration class'larıyla oluşturulur.
v2'de yönetici hesabı yoktur; herkes kendisi kayıt olur. `MESSAGING_PROVIDER` verilmezse doğrulama kodları
WhatsApp'a gitmez, sunucu log'una yazılır.

### 3. Çalıştırma

Windows'ta en kolayı `run-dev.cmd`'dir (JDK 25'i kendisi bulur):

```bash
run-dev.cmd
```

Profil bilerek varsayılan verilmez; Maven ile çalıştırırken `dev` ya da `prod` açıkça seçilmelidir.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Windows PowerShell'de parametre tırnak içinde yazılmalıdır (yoksa PowerShell onu noktadan böler):

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

IDE'den çalıştırırken environment variable olarak `SPRING_PROFILES_ACTIVE=dev` verin ve çalışma dizinini `server/`
yapın (`.env` ve `keys/` bu dizine göre aranır).

| | dev profili |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Demo verisi | 4 demo kullanıcısı, telefon `5550000001`–`5550000004`, parola `DemoParola2026!` (V001, sadece dev) |
| Dev-login | `POST /api/auth/dev-login` ilk demo kullanıcısıyla şifresiz oturum açar (sadece bu makineden) |

## Testler

```bash
./mvnw test
```

Testler unit ve kontrat testleridir (Mockito); veritabanı ya da Docker gerektirmez, `.env`'i okumaz. Neyin test
edildiği: [docs/Backend_Architecture.md §17](../docs/Backend_Architecture.md).

Prod'da gereken değişkenlerin listesi [.env.example](.env.example) dosyasındadır.

## Modüller (`com.server.modules`)

| Modül | Görev | Uç noktalar |
|---|---|---|
| `auth` | Giriş, kayıt, şifre sıfırlama, oturumlar (JWT + refresh cookie), giriş denemesi sınırı | `/api/auth/login`, `register/start|complete`, `password-reset/start|complete`, `refresh`, `logout`, `PUT /api/auth/password` |
| `verification` | WhatsApp/SMS doğrulama kodu, numara/IP/günlük sınırlar, test modu | (auth üzerinden) |
| `notification` | Mesaj gönderimi: `VonageMessageGateway` ya da log'a yazan `SimulatedSmsGateway` | — |
| `totp` | İsteğe bağlı iki adımlı doğrulama ve kurtarma kodları | `/api/auth/totp/setup|enable|disable` |
| `user` | Ad değiştirme, hesabı kalıcı silme | `PUT /api/users/me/name`, `POST /api/users/me/delete` |
| `key` | Açık anahtarlar ve istemcide şifrelenmiş özel anahtar paketi | `GET/POST /api/keys/me` |
| `invite` | Davet kodu ile ilk temas, iki yönlü doğrulama | `/api/invites`, `redeem`, `{id}/accept|verify|reject`, `verifications`, `sent`, `DELETE {id}` |
| `contact` | Doğrulanmış kişiler (imzalı kayıt) | `GET /api/contacts`, `DELETE /api/contacts/{userId}` |
| `document` | Şifreli kutular, alıcı başına anahtar, kutu ömrü, açılınca sil, kota, zamanlanmış silme | `/api/documents`, `inbox`, `sent`, `{id}`, `{id}/opened`, `DELETE {id}` |

Zamanlanmış işler: `DocumentCleanupJob`, `InviteCleanupJob`, `AuthCleanupJob` (süresi dolan kayıtları kalıcı siler).
Migration'lar: `V001_DemoData` (sadece dev), `V002_HardDelete`, `V003_SealSecrets`.

## Ortam değişkenleri (prod)

Tam liste: [.env.example](.env.example). En önemlileri:

| Değişken | Not |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_SCHEMA` | Railway'de `${{Postgres.*}}` referanslarıyla; şema `gps_v2` |
| `JWT_*` | RS256 anahtarları (Railway'de PEM değişkenleri, başlangıçta dosyaya yazılır) |
| `SERVER_SEAL_KEY` | Şifre özetleri ve anahtar paketleri için ikinci kilit (32 bayt, Base64). **Kaybolursa bütün hesaplar açılamaz; ayrı yedeklenmeli** |
| `MESSAGING_PROVIDER`, `VONAGE_*` | Doğrulama mesajı (Vonage WhatsApp/SMS) |
| `VERIFICATION_TEST_MODE` | `true` iken kayıt kodu mesajla gitmez, cevapta döner. Sadece demo; gerçek kullanımda `false` |

## Kurallar (özet)

- Uygulanmış bir migration class'ı **değiştirilmez**; yeni sürüm yazılır.
- Var olan kolonu değiştirmek (ad, tip, uzunluk) `ddl-auto` ile olmaz → migration class'ında `ALTER`.
- Yeni kolonlar nullable ya da default değerli olmalıdır (rolling update sırasında eski sürüm de çalışır).
- Response formatı `{ success, message, data, traceId }` **değişmez**.
- Sunucu içerik, şifre ya da özel anahtar görmez; yeni bir özellik bu kuralı bozmamalı (Security.md).

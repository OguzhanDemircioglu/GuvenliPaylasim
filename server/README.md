# server

StarterApp'in Spring Boot backend'i. Bağlayıcı kurallar ve tasarım kararları:
[docs/Backend_Architecture.md](../docs/Backend_Architecture.md).

| | |
|---|---|
| Java | 25 |
| Spring Boot | 4.1.x |
| Veritabanı | Prod: PostgreSQL 18, yerel geliştirme: H2 (dosyada kalıcı) |
| Build | Maven (`./mvnw`, Windows PowerShell'de `.\mvnw.cmd`) |

## İlk kurulum

### 1. JWT anahtarları

Uygulama anahtar üretmez; anahtarlar repoya konmaz (`keys/` klasörü `.gitignore`'dadır). `server/` klasöründe:

```bash
mkdir -p keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/private.pem
openssl rsa -in keys/private.pem -pubout -out keys/public.pem
```

Windows'ta `openssl` Git Bash ile birlikte gelir. Docker'da `keys/` klasörü container'a bağlandığı için Linux'ta
dosyaların okunabilir olması gerekir: `chmod 644 keys/*.pem`.

### 2. Veritabanı ve `.env`

Dev profili kurulum gerektirmeyen **H2** ile açılır: veri `server/data/` klasöründe durur (git'e girmez) ve yeniden
başlatmalarda korunur. Veritabanını sıfırlamak için server kapalıyken bu klasör silinir. Gömülü H2 dosyasını aynı anda tek
süreç açabilir; ikinci bir server ya da harici bir DB aracı, ilk süreç kapanana kadar bağlanamaz.

Ayarlar `server/.env` dosyasına yazılır. Bu dosya git'e girmez ve **sadece dev profilinde** okunur; testler ve prod onu
hiçbir zaman kullanmaz. Ortam değişkenleri her zaman `.env`'den önceliklidir. Yerelde PostgreSQL kullanmak için üç
`DB_` satırı daha yazılır; yazılmazsa H2 kullanılır:

```properties
# DB_URL=jdbc:postgresql://host:port/veritabani?ssl=require
# DB_USERNAME=...
# DB_PASSWORD=...
DB_SCHEMA=starterapp
ADMIN_EMAIL=admin@firma.com
ADMIN_PASSWORD=...
```

- `DB_SCHEMA` küçük harf olmalıdır. Uygulamanın bütün tabloları bu şemada durur; şema yoksa ilk açılışta oluşturulur.
  Başka projelerle paylaşılan bir veritabanında diğer şemalara (ör. `public`) dokunulmaz. Dev profilinde varsayılanı
  `starterapp`'tir; prod'da **zorunludur**.
- Aynı `.env`'yi `docker compose --profile app` da kullanır (server container'ına `env_file` ile verilir). `DB_URL`
  verilmişse IDE'den ve Docker'dan çalıştırınca aynı veritabanına bağlanılır; verilmemişse Docker'daki H2 `h2data`
  volume'ünde, IDE'deki `server/data/` klasöründe durur ve bu iki veri birbirinden ayrıdır.
- Docker ile yerel bir PostgreSQL için repo kökünde `docker compose --profile db up -d` → PostgreSQL 18, `localhost:5433`
  (`DB_URL=jdbc:postgresql://localhost:5433/starterapp`, kullanıcı/şifre `starterapp`). Bu adres sadece IDE'den
  çalıştırırken geçerlidir; compose'daki server container'ı için `jdbc:postgresql://host.docker.internal:5433/starterapp` yazılır.

Tablolar ilk açılışta Hibernate tarafından (`ddl-auto=update`), başlangıç verileri migration class'larıyla oluşturulur.
`ADMIN_EMAIL`/`ADMIN_PASSWORD` verilmezse dev profilinde `admin@starterapp.local` / `Admin12345` kullanılır.

### 3. Çalıştırma

Profil bilerek varsayılan verilmez; `dev` ya da `prod` açıkça seçilmelidir.

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
| İlk admin | `.env`'deki `ADMIN_EMAIL` / `ADMIN_PASSWORD` (yoksa `admin@starterapp.local` / `Admin12345`) |
| CORS | `http://localhost:4200` |

## Testler

```bash
./mvnw test
```

- Unit ve kontrat testleri her yerde çalışır.
- `H2IntegrationTest` uygulamayı bellekteki H2 ile açar; Docker gerektirmez.
- `AuthFlowIntegrationTest`, `SchemaIsolationIntegrationTest` ve `FirewallIntegrationTest` gerçek bir PostgreSQL 18'i
  Testcontainers ile açar; **Docker yoksa otomatik atlanır**.

## Docker

Server ve client'ı birlikte çalıştıran [docker-compose.yml](../docker-compose.yml) repo kökündedir:

```bash
# repo kökünde; server/.env ve server/keys/ hazır olmalı
docker compose --profile app up -d
```

Admin bilgileri ve varsa `DB_URL` `server/.env`'den okunur (bkz. "İlk kurulum").

Sadece server imajı için (`server/` klasöründe): `docker build -t starterapp-server .`

Prod'da gereken değişkenlerin listesi [.env.example](.env.example) dosyasındadır. Sunucuya Docker Compose ile kurulum
için: [deploy/README.md](../deploy/README.md) (orada değişkenler `deploy/.env`'den kurulur).

## Yeni modül ekleme

Referans modül `modules/category`'dir. Örnek olarak `Product` modülü:

1. **Klasör:** `modules/category` klasörünü `modules/product` olarak kopyalayın, sınıf ve paket adlarını değiştirin.
2. **Entity:** `Product extends BaseEntity`, sınıfa `@Entity`, `@SoftDelete`, `@Table(name = "products")` ekleyin.
   - `@Data` değil `@Getter`/`@Setter` kullanın.
   - Her `@ManyToOne` için `fetch = FetchType.LAZY`.
   - Kolon adlarını `@Column(name = "...")` ile açıkça yazın (migration SQL'leri bu adları kullanır).
   - Benzersiz alanlara `@Column(unique = true)` **koymayın** (bkz. 8. adım).
3. **DTO'lar (`dto/`):** `ProductCreateRequest`, `ProductUpdateRequest`, `ProductResponse` record olarak.
   - Update request `VersionedRequest`'i uygular ve `@NotNull(message = GlobalMessages.VERSION_REQUIRED) Long version` taşır.
   - Response `version` alanını döner (frontend güncellemede geri gönderir).
   - Her validation anotasyonuna `message = ProductMessages.XXX` verin; mesajı olmayan validation hataları `invalid_request` döner.
4. **Mesajlar:** `ProductMessages` sınıfı; anahtarlar `product_` ile başlar, snake_case'tir.
5. **Mapper:** `@Mapper(config = MapperSettings.class) interface ProductMapper extends BaseMapper<...>` — gövdesi yok.
   Eşlenmemiş bir alan kalırsa proje derlenmez.
6. **Servis:** `ProductService extends BaseService<...>` ve `ProductServiceImpl extends BaseServiceImpl<...>`.
   İş kurallarını kancalarda yazın: `validateCreate`, `prepareCreate`, `validateUpdate`, `validateDelete`.
   İş kuralı ihlalinde `throw new BusinessException(ProductMessages.XXX)` (422).
7. **Controller:** `CategoryController` kalıbı; dönüş tipi her zaman `ApiResponse<T>`.
   Sadece admin'e açıksa sınıfa `@PreAuthorize("hasRole('ADMIN')")`; herkes okuyup sadece admin yazıyorsa
   create/update/delete metotlarına.
8. **Migration:** `migration/versions` altına bir sonraki numarayla `V005_ProductModule` yazın
   (`V004_CategoryModule` örnek alınır):
   - Benzersiz alanlar için silinmemiş kayıtlara özel index (PostgreSQL ve H2'de çalışır):
     `SoftDeleteUniqueIndex.create(jdbc, "ux_products_code", "products", "code")`
   - Menü kaydı: `id` yazmadan, üst menüye alt sorguyla referans vererek;
     `version = 0, deleted = false, created_at = now()` açıkça verilir.
9. **Test:** `CategoryServiceImplTest` örnek alınarak iş kurallarının testleri.

### Kurallar (özet)

- Uygulanmış bir migration class'ı **değiştirilmez**; yeni sürüm yazılır.
- SQL'ler dev'deki H2'de de çalışmalıdır: `generate_series`, `ON CONFLICT DO UPDATE` ve partial index H2'de yoktur.
- Var olan kolonu değiştirmek (ad, tip, uzunluk) `ddl-auto` ile olmaz → migration class'ında `ALTER`.
- Enum'a yeni değer eklendiğinde Hibernate'in oluşturduğu `check` constraint güncellenmez → migration class'ında `ALTER`.
- Yeni kolonlar nullable ya da default değerli olmalıdır (Swarm'da rolling update sırasında eski sürüm de çalışır).
- Tablo boşaltılırken `TRUNCATE tablo RESTART IDENTITY` kullanılır.
- Response formatı `{ success, message, data, traceId }` **değişmez**.

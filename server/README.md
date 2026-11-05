# server

Güvenli Paylaşım'ın Spring Boot backend'i (StarterApp şablonundan türetildi). Bağlayıcı kurallar ve tasarım kararları:
[docs/Backend_Architecture.md](../docs/Backend_Architecture.md).

| | |
|---|---|
| Java | 25 |
| Spring Boot | 4.1.x |
| Veritabanı | PostgreSQL 18 (dev, test ve prod) |
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

### 2. PostgreSQL ve `.env`

Sunucu her profilde PostgreSQL ile çalışır; bağlantı bilgisi verilmezse açılmaz. Dev'de ayarlar `server/.env`
dosyasına yazılır. Bu dosya git'e girmez ve **sadece dev profilinde** okunur; testler ve prod onu hiçbir zaman
kullanmaz. Ortam değişkenleri her zaman `.env`'den önceliklidir.

```properties
DB_URL=jdbc:postgresql://localhost:5433/guvenlipaylasim
DB_USERNAME=guvenlipaylasim
DB_PASSWORD=guvenlipaylasim
DB_SCHEMA=guvenlipaylasim
# ADMIN_EMAIL=admin@firma.com
# ADMIN_PASSWORD=...
```

Yerelde Docker ile bir PostgreSQL 18 açmak için (yukarıdaki `.env` buna göredir):

```bash
docker run -d --name gps-postgres --restart unless-stopped -e POSTGRES_DB=guvenlipaylasim -e POSTGRES_USER=guvenlipaylasim -e POSTGRES_PASSWORD=guvenlipaylasim -p 5433:5432 -v gps-pgdata:/var/lib/postgresql postgres:18
```

- Aiven gibi bulut veritabanlarında `DB_URL`'in sonuna `?sslmode=require` eklenir. `DB_HOST`/`DB_PORT`/`DB_NAME` okunmaz;
  bağlantı sadece `DB_URL`'den alınır.
- `DB_SCHEMA` küçük harf olmalıdır. Uygulamanın bütün tabloları bu şemada durur; şema yoksa ilk açılışta oluşturulur.
  Başka projelerle paylaşılan bir veritabanında diğer şemalara (ör. `public`) dokunulmaz. Dev profilinde varsayılanı
  `guvenlipaylasim`'dir; prod'da **zorunludur**.
- Demo verisini sıfırlamak için şema silinir (`DROP SCHEMA guvenlipaylasim CASCADE`); sunucu bir sonraki açılışta
  tabloları ve demo verisini yeniden oluşturur.

Tablolar ilk açılışta Hibernate tarafından (`ddl-auto=update`), başlangıç verileri migration class'larıyla oluşturulur.
`ADMIN_EMAIL`/`ADMIN_PASSWORD` verilmezse dev profilinde `admin@gps.local` / `Admin12345` kullanılır.

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
| İlk admin | `.env`'deki `ADMIN_EMAIL` / `ADMIN_PASSWORD` (yoksa `admin@gps.local` / `Admin12345`) |
| Demo verisi | Birimler ve demo kullanıcıları (V007, sadece dev) |

## Testler

```bash
./mvnw test
```

- Unit ve kontrat testleri her yerde çalışır.
- Veritabanı testleri (`DatabaseIntegrationTest`, `AuthFlowIntegrationTest`, `DocumentFlowIntegrationTest`,
  `RoleAccessIntegrationTest`, `SchemaIsolationIntegrationTest`, `FirewallIntegrationTest`) gerçek bir PostgreSQL 18'i
  Testcontainers ile açar; **Docker yoksa otomatik atlanır**. `.env`'deki veritabanını kullanmazlar.

## Docker

Sadece server imajı için (`server/` klasöründe): `docker build -t guvenlipaylasim-server .`

Prod'da gereken değişkenlerin listesi [.env.example](.env.example) dosyasındadır.

## Yeni modül ekleme

Referans modül `modules/unit`'tir. Örnek olarak `Product` modülü:

1. **Klasör:** `modules/unit` klasörünü `modules/product` olarak kopyalayın, sınıf ve paket adlarını değiştirin.
2. **Entity:** `Product extends BaseEntity`, sınıfa `@Entity`, `@SoftDelete`, `@Table(name = "products")` ekleyin.
   - `@Data` değil `@Getter`/`@Setter` kullanın.
   - Her `@ManyToOne` için `fetch = FetchType.LAZY`.
   - Kolon adlarını `@Column(name = "...")` ile açıkça yazın (migration SQL'leri bu adları kullanır).
   - Benzersiz alanlara `@Column(unique = true)` **koymayın** (bkz. 8. adım).
3. **DTO'lar (`dto/`):** `ProductCreateRequest`, `ProductUpdateRequest`, `ProductResponse` record olarak.
   - Update request `VersionedRequest`'i uygular ve `@NotNull(message = GlobalMessages.VERSION_REQUIRED) Long version` taşır.
   - Response `version` alanını döner (istemci güncellemede geri gönderir).
   - Her validation anotasyonuna `message = ProductMessages.XXX` verin; mesajı olmayan validation hataları `invalid_request` döner.
4. **Mesajlar:** `ProductMessages` sınıfı; anahtarlar `product_` ile başlar, snake_case'tir.
5. **Mapper:** `@Mapper(config = MapperSettings.class) interface ProductMapper extends BaseMapper<...>` — gövdesi yok.
   Eşlenmemiş bir alan kalırsa proje derlenmez.
6. **Servis:** `ProductService extends BaseService<...>` ve `ProductServiceImpl extends BaseServiceImpl<...>`.
   İş kurallarını kancalarda yazın: `validateCreate`, `prepareCreate`, `validateUpdate`, `validateDelete`.
   İş kuralı ihlalinde `throw new BusinessException(ProductMessages.XXX)` (422).
7. **Controller:** `UnitController` kalıbı; dönüş tipi her zaman `ApiResponse<T>`.
   Sadece admin'e açıksa sınıfa `@PreAuthorize("hasRole('ADMIN')")`; herkes okuyup sadece admin yazıyorsa
   create/update/delete metotlarına.
8. **Migration:** `migration/versions` altına bir sonraki numarayla (ör. `V008_ProductModule`) yazın
   (`V004_UnitModule` örnek alınır):
   - Benzersiz alanlar için silinmemiş kayıtlara özel index (PostgreSQL partial index):
     `SoftDeleteUniqueIndex.create(jdbc, "ux_products_code", "products", "code")`
   - Menü kaydı: `id` yazmadan, üst menüye alt sorguyla referans vererek;
     `version = 0, deleted = false, created_at = now()` açıkça verilir.
9. **Test:** `UnitServiceImplTest` örnek alınarak iş kurallarının testleri.

### Kurallar (özet)

- Uygulanmış bir migration class'ı **değiştirilmez**; yeni sürüm yazılır.
- Var olan kolonu değiştirmek (ad, tip, uzunluk) `ddl-auto` ile olmaz → migration class'ında `ALTER`.
- Enum'a yeni değer eklendiğinde Hibernate'in oluşturduğu `check` constraint güncellenmez → migration class'ında `ALTER`.
- Yeni kolonlar nullable ya da default değerli olmalıdır (rolling update sırasında eski sürüm de çalışır).
- Tablo boşaltılırken `TRUNCATE tablo RESTART IDENTITY` kullanılır.
- Response formatı `{ success, message, data, traceId }` **değişmez**.

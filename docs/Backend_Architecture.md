# Backend Architecture — StarterApp

> **Durum:** v3 — 2026-10-18 (server iskeletiyle senkron)
> Bu doküman `server/` projesinin bağlayıcı kurallarıdır. Kod bu dokümana uyar; kural değişecekse önce bu doküman güncellenir.
> Frontend (`client/`) ayrı bir dokümanda anlatılacak.

---

## 1. Amaç ve kapsam

- Yeni projelere hazır başlangıç noktası olacak bir şablon. Yeni bir projeye bu repo kopyalanarak başlanır.
- Hedeflenen uygulama tipi: DB üzerinde veri listeleme, görüntüleme, ekleme, güncelleme ve silme ekranları. Faturalandırma gibi alanlar kapsam dışı.
- Monorepo: `client/` (Angular) + `server/` (Spring Boot) tek repoda.

---

## 2. Teknoloji ve sürümler

| Bileşen | Sürüm | Not |
|---|---|---|
| Spring Boot | 4.1.x (başlangıç: 4.1.1) | Kurulumda son patch alınır |
| Java | 25 (LTS) | |
| Build | Maven (wrapper ile) | |
| Veritabanı | PostgreSQL 18 | Yerel geliştirmede (dev profili) H2, PostgreSQL uyumluluk modunda (§10.6) |
| ORM | Hibernate 7 | Boot ile gelir |
| Güvenlik | Spring Security + OAuth2 Resource Server | JWT, RS256 |
| Mapper | MapStruct | Lombok ile birlikte `lombok-mapstruct-binding` gerekir |
| Kod azaltma | Lombok | |
| API dokümanı | springdoc-openapi 3.x | Boot 4.1 uyumu ilk build'de doğrulanır |
| JSON | Jackson 3 | Boot 4 ile gelir; paket adı `tools.jackson` |
| Test | JUnit 6 (Jupiter), Mockito, Testcontainers 2 | Boot ile gelir |

- Boot 4'te starter adları değişti (örneğin `spring-boot-starter-webmvc`). `pom.xml` start.spring.io'dan üretilir, elle yazılmaz.
- Lombok, MapStruct ve springdoc'un Java 25 ve Boot 4.1 ile uyumu ilk build'de kontrol edilir. Sorun çıkarsa Java 21'e inmek tek satırlık bir değişikliktir.

---

## 3. Paket yapısı

Kök paket: `com.server`.

```
com.server
├─ common/
│  ├─ entity/      BaseEntity
│  ├─ mapper/      BaseMapper, MapperSettings
│  ├─ service/     BaseService, BaseServiceImpl
│  ├─ response/    ApiResponse, ApiResponseWriter
│  ├─ exception/   BaseException ve alt sınıfları, GlobalExceptionHandler, ApiErrorController, StatusMessages
│  ├─ message/     GlobalMessages
│  ├─ validation/  MaxBytes (UTF-8 byte uzunluğu sınırı, ör. bcrypt'in 72 byte'ı)
│  └─ web/         TraceIdFilter
├─ config/         JpaAuditingConfig, OpenApiConfig, CorsConfig, SchedulingConfig
├─ security/       SecurityConfig, JwtKeyConfig, CurrentUser,
│                  RestAuthenticationEntryPoint, RestAccessDeniedHandler
├─ migration/      DataMigration, DataMigrationRunner
│  └─ versions/    V001_UniqueIndexes, V002_InitialAdmin, V003_InitialMenus, V004_CategoryModule,
│                  V005_HierarchicalMenuRoutes
└─ modules/
   ├─ auth/        login / refresh / logout / şifre değiştirme, UserSession, LoginAttempt, SessionInfo
   ├─ user/        kullanıcı yönetimi (sadece admin)
   ├─ menu/        menü ağacı
   ├─ report/      grafikleri besleyen özet endpoint'ler (§5.5)
   └─ category/    referans CRUD modülü (yeni modül açarken kopyalanır)
```

Bir modülün içi:

```
modules/category/
├─ Category.java                 entity
├─ CategoryRepository.java
├─ CategoryMapper.java           MapStruct interface (gövdesi yok)
├─ CategoryService.java          interface
├─ CategoryServiceImpl.java
├─ CategoryController.java
├─ CategoryMessages.java         modüle özel mesaj anahtarları
└─ dto/
   ├─ CategoryCreateRequest.java
   ├─ CategoryUpdateRequest.java
   └─ CategoryResponse.java
```

**İsimlendirme:**
- Servis implementasyonları `XxxServiceImpl` olarak adlandırılır.
- DTO'lar Java `record` olarak yazılır. Adları `XxxCreateRequest`, `XxxUpdateRequest` ve `XxxResponse` kalıbını izler.

---

## 4. Ortak taban

### 4.1 BaseEntity (`@MappedSuperclass`)

| Alan | Tip | Açıklama |
|---|---|---|
| `id` | `Long` | `GenerationType.IDENTITY` |
| `version` | `Long` | `@Version`, optimistic lock (§4.4) |
| `createdAt` / `updatedAt` | `Instant` | JPA Auditing doldurur |
| `createdBy` / `updatedBy` | `Long` | Kullanıcı id'si; `CurrentUser`'dan gelir, sistem işlemlerinde `null` |

BaseEntity'de `deleted` alanı **yoktur**: soft delete için her entity sınıfına ayrıca `@SoftDelete` eklenir (§10.4).
`deleted` kolonunu Hibernate oluşturur ve yönetir.

**Entity kuralları:**
- Entity'lerde `@Getter` ve `@Setter` kullanılır, **`@Data` kullanılmaz** (JPA ile `equals`, `hashCode` ve `toString` sorun çıkarır).
- `@ManyToOne` ilişkileri her zaman `fetch = FetchType.LAZY` olarak tanımlanır (JPA'nın varsayılanı EAGER'dır).
- Teknik tablolar BaseEntity'den türemez ve soft delete kullanmaz: `user_sessions`, `login_attempts`, `data_migration_history`.

### 4.2 Mapper (MapStruct)

```java
@MapperConfig(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        mappingInheritanceStrategy = MappingInheritanceStrategy.AUTO_INHERIT_FROM_CONFIG)
public interface MapperSettings {
    // Prototip metot: request -> entity eşlemelerinde BaseEntity alanları otomatik yok sayılır.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    BaseEntity toBaseEntity(Object source);
}

public interface BaseMapper<E extends BaseEntity, CREATE, UPDATE, RESPONSE> {
    E toEntity(CREATE request);
    RESPONSE toResponse(E entity);
    void update(UPDATE request, @MappingTarget E entity);
}

@Mapper(config = MapperSettings.class)
public interface CategoryMapper
        extends BaseMapper<Category, CategoryCreateRequest, CategoryUpdateRequest, CategoryResponse> {}
```

- Mapper'ın kodunu MapStruct derleme sırasında üretir. Mapper içinde repository ya da iş kuralı **olmaz**.
- `unmappedTargetPolicy = ERROR` sayesinde eşlenmemiş bir alan kalırsa proje derlenmez.
- BaseEntity alanları (`id`, `version`, audit alanları) request'ten entity'ye eşlenirken `MapperSettings`'teki prototip metot sayesinde yok sayılır. Request'teki `version` entity'ye kopyalanmaz; karşılaştırmayı servis yapar (§4.4).
- Bir modül ek alanları farklı eşlemek isterse ilgili metodu kendi mapper'ında yeniden tanımlar (ör. `UserMapper`: şifre eşlenmez, `active` varsayılan `true`); ortak yok saymalar yine geçerlidir.

### 4.3 BaseService (interface + impl)

```java
public interface BaseService<CREATE, UPDATE extends VersionedRequest, RESPONSE> {
    List<RESPONSE> getAll();
    RESPONSE getById(Long id);
    RESPONSE create(CREATE request);
    RESPONSE update(Long id, UPDATE request);
    void delete(Long id);
}
```

- `BaseServiceImpl` bu metotların genel davranışını tek bir yerde uygular. Transaction yönetimi (`@Transactional`) servis katmanındadır.
- Modülün iş kuralları için boş varsayılanlı dört kanca (hook) vardır. Modül yalnızca ihtiyaç duyduğunu override eder:
  - `validateCreate(request)`: eklemeden önceki iş kuralları.
  - `prepareCreate(entity, request)`: mapping'den sonra, kaydetmeden önce entity'yi tamamlamak için (ör. şifreyi hash'leyip atamak).
  - `validateUpdate(entity, request)`: güncellemeden önceki iş kuralları; `entity` henüz eski değerlerini taşır.
  - `validateDelete(entity)`: silmeden önceki iş kuralları (ör. kayıt başka kayıtlarca kullanılıyor mu).
- Kanca yetmiyorsa modül `create`/`update`/`delete` metodunu override edip `super`'i çağırır (ör. `UserServiceImpl.update`, pasife alınan kullanıcının oturumlarını kapatır).
- Modülün kendine özel metotları, kendi `XxxService` interface'ine eklenir.
- Kayıt yoksa `getById`, `update` ve `delete` `NotFoundException` (404) fırlatır.

### 4.4 Optimistic lock (`@Version`)

Amaç: İki kullanıcı aynı kaydı aynı anda düzenlerse, sonra kaydedenin öncekinin değişikliğini sessizce ezmemesi.

- Her `XxxResponse` içinde `version` alanı döner. Frontend kaydı düzenlerken bu değeri saklar.
- Her `XxxUpdateRequest` `VersionedRequest` interface'ini uygular ve `@NotNull Long version` alanını taşır.
- `BaseServiceImpl.update`, isteğin `version`'ını DB'deki `version` ile karşılaştırır. Farklıysa `ConflictException` fırlatır: **409, `record_modified`**.
- Not: `@Version` tek başına sadece milisaniyeler içinde çakışan transaction'ları yakalar. Kullanıcının ekranda eski veriyle kaydetmesini yakalayan şey, bu açık karşılaştırmadır.

### 4.5 Controller kalıbı

`BaseController` **yoktur**; her controller açıkça yazılır.

```java
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService service;

    @GetMapping
    public ApiResponse<List<CategoryResponse>> getAll() {
        return ApiResponse.success(service.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(service.getById(id));
    }

    @PostMapping
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CategoryCreateRequest request) {
        return ApiResponse.success(GlobalMessages.RECORD_CREATED, service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CategoryResponse> update(@PathVariable Long id, @Valid @RequestBody CategoryUpdateRequest request) {
        return ApiResponse.success(GlobalMessages.RECORD_UPDATED, service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(GlobalMessages.RECORD_DELETED, null);
    }
}
```

**Controller kuralları:**
- Controller'da `@Transactional`, `try/catch` ve iş kuralı **olmaz**.
- Request DTO'ları her zaman `@Valid` ile alınır.
- Dönüş tipi her zaman `ApiResponse<T>` olur.

---

## 5. API sözleşmesi — DEĞİŞMEZ

### 5.1 Response formatı

```json
{ "success": true,  "message": "record_updated", "data": { "id": 1, "version": 3 }, "traceId": "4f9a1c2b7d3e8a01" }
{ "success": false, "message": "category_name_exists", "data": null, "traceId": "4f9a1c2b7d3e8a01" }
```

| Alan | Kural |
|---|---|
| `success` | Başarılıysa `true`, hata varsa `false` |
| `message` | Her zaman bir **mesaj anahtarı** (snake_case). Backend metin göndermez; çeviriyi frontend yapar |
| `data` | Başarılı cevapta veri; yoksa veya hata varsa `null` |
| `traceId` | İsteğin izleme kodu; loglarda da aynı değer geçer |

**Kurallar:**
- Dört alan **her zaman** response'ta bulunur. Jackson'da global `NON_NULL` ayarı **yapılmaz**.
- Formata alan eklenmez, alan çıkarılmaz ve alanların adı değiştirilmez.
- Hata durumunda tek bir mesaj döner; alan listesi yoktur. Validation hatası birden fazlaysa sadece biri döner.
- Başarılı cevapların HTTP status'u her zaman **200**'dür (create dahil).

### 5.2 HTTP status ve mesaj anahtarları

| HTTP | Ne zaman | Örnek `message` |
|---|---|---|
| 200 | Başarılı işlem | `success`, `record_created`, `record_updated`, `record_deleted` |
| 400 | Validation hatası, bozuk JSON, yanlış parametre tipi, eksik parametre | Alanın anahtarı (`category_name_required`, `version_required`), `invalid_request`, `invalid_parameter` |
| 401 | Token yok ya da geçersiz; login ya da refresh başarısız | `unauthorized`, `auth_invalid_credentials`, `auth_session_expired`, `auth_user_inactive` |
| 403 | Rolün yetkisi yok | `access_denied` |
| 404 | Kayıt ya da URL bulunamadı | `record_not_found`, `url_not_found` |
| 405 | Yanlış HTTP metodu | `method_not_allowed` |
| 409 | Kayıt bu arada değişmiş; DB unique ya da FK ihlali | `record_modified`, `record_conflict` |
| 415 | Desteklenmeyen içerik tipi | `unsupported_media_type` |
| 422 | İş kuralı ihlali (`BusinessException`) | Modül anahtarı (`category_name_exists`) |
| 429 | Login engellendi (brute force) | `auth_login_blocked` |
| 500 | Beklenmeyen hata | `unexpected_error` |

### 5.3 Formatın korunduğu yerler

Response formatını bozabilecek üç çıkış noktası vardır. Üçü de aynı `ApiResponse` formatını döner:

1. **Controller'dan çıkan hatalar:** `GlobalExceptionHandler` (`@RestControllerAdvice`) yakalar.
2. **Spring Security hataları (401, 403):** Bunlar ControllerAdvice'a ulaşmaz. `RestAuthenticationEntryPoint` ve `RestAccessDeniedHandler` yakalar.
3. **Filtre seviyesindeki diğer hatalar:** Spring'in `/error` endpoint'ine düşerler. Onun yerine `ApiErrorController` cevap verir. Security firewall'unun reddettiği istekler de (ör. URL'de `;`) buraya düşer ve 400 `invalid_request` döner.

Formatın korunduğu **kontrat testleriyle** garanti edilir: §5.2'deki her status için bir test, dört alanın varlığını ve tipini kontrol eder (§17).

### 5.4 Endpoint kalıbı

| İşlem | Metot ve yol |
|---|---|
| Listele | `GET /api/{kaynak}` |
| Tek kayıt | `GET /api/{kaynak}/{id}` |
| Ekle | `POST /api/{kaynak}` |
| Güncelle | `PUT /api/{kaynak}/{id}` (body'de `version` zorunlu) |
| Sil (soft) | `DELETE /api/{kaynak}/{id}` |

- URL'lerde fiil kullanılmaz (`/add`, `/update` yok). Kaynak adları çoğul ve kebab-case yazılır.
- **Sayfalama yok (v1).** Listeler düz dizi döner. Sayfalama ileride eklenirse mevcut endpoint'lerin dönüşü değiştirilmez, ayrı endpoint olarak eklenir.

### 5.5 Rapor ve grafik endpoint'leri

Frontend'de çok sayıda grafik olacak. Grafikler ham kayıtlarla değil, backend'in hazırladığı **özet verilerle** beslenir.

- **Yol:** `GET /api/reports/{rapor-adi}`, filtreler query parametresiyle verilir. Örnek:
  `GET /api/reports/categories-created?days=30` → `data: [ { "period": "2026-10-01", "value": 3 }, ... ]`
- **Toplama SQL'de yapılır** (`GROUP BY`, `COUNT`, `SUM`). Ham satırlar frontend'e çekilip orada toplanmaz. Bu sayede rapor endpoint'leri az satır döner ve "sayfalama yok" kuralıyla çelişmez.
- **Gün, hafta ve ay gruplaması Türkiye saatine göre yapılır.** Tarihler UTC saklandığı için düz `date_trunc('day', created_at)` gece 00:00–03:00 arasındaki kayıtları bir önceki güne yazar. Doğrusu:
  `date_trunc('day', created_at AT TIME ZONE 'Europe/Istanbul')`
- **Boş günler** de cevapta 0 değeriyle bulunur: SQL toplamayı yapar, sonuçta olmayan günleri serviste 0 ile doldurur (`generate_series` H2'de olmadığı için, §10.6). Böylece grafiğin zaman ekseninde boşluk oluşmaz.
- **Soft delete filtresi:** Rapor sorguları native SQL olduğunda `deleted = false` koşulu elle yazılır; Hibernate'in otomatik filtresi native SQL'de çalışmaz.
- **Dönüş tipleri** her rapor için record olarak yazılır (`PeriodValue(String period, long value)` gibi); `ApiResponse` formatı aynen geçerlidir.

---

## 6. Mesajlar

- Mesajlar constant sınıflarında durur. Ortak olanlar `GlobalMessages`'ta, modüle özel olanlar modülün `XxxMessages` sınıfındadır (örneğin `CategoryMessages`, `AuthMessages`).
- Sabitin değeri snake_case bir **anahtardır**. Sabitin adı, anahtarın büyük harfli halidir:
  ```java
  public final class CategoryMessages {
      public static final String NAME_REQUIRED = "category_name_required";
      public static final String NAME_EXISTS   = "category_name_exists";
      private CategoryMessages() {}
  }
  ```
- Modül anahtarları modül adıyla başlar (`category_...`, `auth_...`, `user_...`). Global anahtarların ön eki yoktur.
- Validation'da da aynı sabitler kullanılır: `@NotBlank(message = CategoryMessages.NAME_REQUIRED)`.
- Backend'de çeviri dosyası **yoktur**; çeviriyi frontend yapar.
- Mesajlar parametre taşımaz. Anahtar, hatanın ne olduğunu kendi başına anlatmalıdır: `category_name_too_long` gibi. Gerekli sayıları ("15 karakter") frontend kendi çeviri metninde tutar.

---

## 7. Exception yönetimi

```
BaseException (abstract: HttpStatus + mesaj anahtarı)
├─ BusinessException      422   iş kuralı ihlali
├─ UnauthorizedException  401   yanlış e-posta/şifre, süresi dolmuş oturum
├─ NotFoundException      404
├─ ConflictException      409
└─ LoginBlockedException  429
```

| Kaynak | Status |
|---|---|
| `MethodArgumentNotValidException`, `HandlerMethodValidationException`, `ConstraintViolationException` | 400; ilk hatanın anahtarı döner |
| `HttpMessageNotReadableException` (bozuk JSON) | 400, `invalid_request` |
| `TypeMismatchException`, `ServletRequestBindingException` (yanlış tip, eksik parametre) | 400, `invalid_parameter` |
| `AuthenticationException` (security entry point) | 401 |
| `AccessDeniedException` (handler + `@PreAuthorize`) | 403 |
| `ObjectOptimisticLockingFailureException` | 409, `record_modified` |
| `DataIntegrityViolationException` | 409, `record_conflict` |
| `BaseException` alt sınıfları | Kendi status'u ve anahtarı |
| Spring MVC'nin kendi exception'ları (`ErrorResponse`: 404 `NoResourceFound`, 405, 415 ...) | Kendi status'u; anahtar status'tan türetilir (`url_not_found`, `method_not_allowed`, `unsupported_media_type`) |
| Diğer her şey (`Exception`) | 500, `unexpected_error` |

**Kurallar:**
- Validation mesajı bir anahtar formatında değilse (ör. `message` verilmesi unutulmuşsa ve Hibernate Validator'ın
  varsayılan metni geldiyse) frontend'e `invalid_request` gider; düz metin asla gitmez.
- Stacktrace, SQL ve sınıf adları response'a **asla** çıkmaz.
- 500 hataları stacktrace ile `ERROR` seviyesinde loglanır. Bütün 4xx cevapları status ve mesaj anahtarıyla `INFO` seviyesinde loglanır (`GlobalExceptionHandler`, 401/403 handler'ları, `/error`a düşenler için `ApiErrorController`); DB çakışmaları (`record_conflict`) ayrıca nedeniyle `WARN` seviyesinde. traceId her log satırında bulunur.
- NPE ve ClassCastException gibi hatalar kodun hatasıdır, 500 döner.
- İş kuralı ihlalinde her zaman `throw new BusinessException(XxxMessages.KEY)` kullanılır.

---

## 8. Kimlik doğrulama (JWT RS256)

### 8.1 Token'lar

| | Access token | Refresh token |
|---|---|---|
| Biçim | JWT, RS256 ile imzalı | JWT değil; 256 bit rastgele değer |
| Süre | 15 dakika | 90 gün, kayan süre: her kullanımda yeniden 90 gün |
| Client'ta | Bellekte (store); localStorage'a **yazılmaz** | `HttpOnly` cookie |
| Sunucuda | Saklanmaz | `user_sessions` tablosunda SHA-256 hash olarak |

**Access token içeriği:**
- Header: `kid` (anahtar kimliği).
- Claim'ler: `iss`, `sub` (userId), `email`, `role`, `sid` (oturum id'si), `iat`, `exp`.
- Token'a başka bir değer konmaz; ek bilgiler `SessionInfo` ile gönderilir (§8.6).

### 8.2 Anahtarlar

- RSA private ve public key PEM dosyalarından okunur. Dosya yolları environment variable'dan gelir. Uygulama **anahtar üretmez** ve anahtarlar repoya **konmaz**.
- Swarm'da anahtarlar Docker secret olarak verilir. Bütün replica'lar aynı anahtarı kullanmak zorundadır.
- Anahtar değişimi: Aktif anahtar imzalar. İsteğe bağlı olarak bir önceki public key de tanımlanabilir; doğrulama `kid`'e göre ikisini de kabul eder. Böylece anahtar değiştirildiğinde kullanıcılar oturumdan atılmaz.
- Geliştirme ortamı için anahtar üreten `openssl` komutları README'de yer alır. Anahtarların durduğu klasör `.gitignore`'dadır.

### 8.3 Refresh cookie

| Özellik | Değer |
|---|---|
| Ad | `refresh_token` |
| `HttpOnly` | true |
| `Secure` | prod: true, dev: false (`COOKIE_SECURE`) |
| `SameSite` | `Strict` |
| `Path` | `/api/auth` |
| `Max-Age` | 90 gün |

- Frontend ve backend **aynı site** üzerinde olmalıdır (aynı ana domain; port farkı sorun değildir). Aksi halde cookie gönderilmez.
- Frontend auth isteklerini `withCredentials: true` ile atar. CORS'ta `allowCredentials=true` açıktır ve izin verilen origin'ler açıkça listelenir.
- CSRF koruması kapalıdır. Cookie'nin `SameSite=Strict` olması ve CORS kısıtı, başka bir siteden gelen isteklerde cookie'nin gönderilmesini engeller.

### 8.4 Oturum tablosu `user_sessions`

Her satır bir oturumu temsil eder; bir tarayıcı, bir satır. Refresh token her yenilendiğinde yeni satır açılmaz, mevcut satır güncellenir.

| Kolon | Açıklama |
|---|---|
| `id` | Oturum id'si; access token'daki `sid` claim'i |
| `user_id` | |
| `current_token_hash` | Geçerli refresh token'ın hash'i |
| `previous_token_hash` | Bir önceki token'ın hash'i (çalınma tespiti için) |
| `rotated_at` | Son yenileme zamanı |
| `expires_at` | Son kullanımdan 90 gün sonrası |
| `revoked_at` | İptal edildiyse zamanı |
| `user_agent`, `ip`, `created_at`, `last_used_at` | Bilgi amaçlı ("aktif oturumlar" özelliği için hazır) |

### 8.5 Akışlar

**Login — `POST /api/auth/login` (herkese açık)**
1. Brute-force kontrolü yapılır (§13). Engelliyse doğru şifre girilse bile 429 döner.
2. E-posta ve şifre kontrol edilir. Yanlışsa 401 `auth_invalid_credentials` döner; kullanıcının var olup olmadığı belli edilmez. Kayıtlı olmayan e-postalarda da sahte bir hash ile şifre kontrolü yapılır, böylece cevap süresinden de anlaşılmaz.
3. Kullanıcı pasifse 401 `auth_user_inactive` döner. Bu cevabı yalnızca doğru şifreyi bilen biri görür.
4. Başarılıysa yeni bir `user_sessions` satırı açılır, refresh cookie yazılır ve `SessionInfo` döner.

**Kayıt — `POST /api/auth/register` (herkese açık)**
- Ad soyad, e-posta ve şifre alınır; kurallar `UserCreateRequest` ile aynıdır. Rol alanı yoktur, kayıt olan herkes `USER` olur. Oturum açmaz; client ardından login çağırır.
- Kayıtlı e-postada 422 `user_email_exists` döner.
- Aynı IP'den 10 denemeden sonra 1 saat boyunca 429 `auth_register_blocked` döner (§13.2).

**Yerel otomatik giriş — `POST /api/auth/dev-login` (sadece geliştirme)**
- `DevLoginController`, `ADMIN_EMAIL` kullanıcısıyla şifresiz oturum açar. Şifre koda ya da client'a girmez.
- Sadece `app.dev-login.enabled=true` iken oluşur. Bu ayar yalnızca `application-dev.yml`'dedir; prod'da adres 404 döner. Compose `DEV_LOGIN_ENABLED=false` verir.
- Dev profilli server ağa açık olabileceği için sadece bu makineden (loopback) gelen istekler kabul edilir, diğerleri 404 alır.
- **Prod ortamı `dev` profiliyle çalıştırılmaz.**

**Refresh — `POST /api/auth/refresh` (herkese açık; cookie ile çalışır)**

| Gelen token'ın hash'i | Sonuç |
|---|---|
| `current_token_hash` ile aynı | Token yenilenir (rotation): eski hash `previous`'a geçer, yeni token üretilir, süre 90 gün uzar. Yeni cookie ve `SessionInfo` döner |
| `previous_token_hash` ile aynı ve `rotated_at` üzerinden **30 saniye** geçmemiş | Birden fazla sekme aynı anda yenileme yapmıştır. Sadece yeni bir access token ve `SessionInfo` döner; **cookie'ye dokunulmaz** |
| `previous_token_hash` ile aynı ve 30 saniye geçmiş | Token çalınmış sayılır: oturum iptal edilir, 401 `auth_session_expired` döner |
| Hiçbiriyle eşleşmiyor, süresi dolmuş ya da iptal edilmiş | 401 `auth_session_expired` |

- Rotation, `UPDATE ... WHERE current_token_hash = ?` şeklinde atomik bir sorguyla yapılır. Aynı anda iki istek gelirse yalnızca biri yeniler; diğeri 30 saniyelik pencereye düşer.
- Login, refresh ve logout isteklerinde `Authorization` başlığı yok sayılır. Frontend süresi dolmuş bir access token'ı yanlışlıkla gönderse bile refresh çalışır.
- Login, refresh ve logout bilinçli olarak tek bir transaction içinde çalışmaz: yanlış deneme kaydı ve çalınma tespitindeki oturum iptali, hemen ardından fırlatılan 401 ile geri alınmamalıdır.
- Kullanıcı pasif ya da silinmişse refresh 401 döner.

**Logout — `POST /api/auth/logout` (herkese açık; cookie ile çalışır)**
- Oturum iptal edilir ve cookie silinir. Tekrar çağrılması sorun çıkarmaz; her zaman 200 döner.

**Kendi şifresini değiştirme — `PUT /api/auth/password` (giriş yapmış kullanıcı)**
- Mevcut şifre yanlışsa 422 döner.
- Başarılı olursa kullanıcının **diğer** bütün oturumları iptal edilir; şu anki oturum (`sid`) açık kalır.

**Admin işlemleri**
- Admin bir kullanıcının şifresini sıfırlarsa, rolünü değiştirirse, onu pasife alırsa ya da silerse, kullanıcının **bütün** oturumları iptal edilir.

**Bilinen sınır:** Rolü değişen ya da pasife alınan kullanıcının elindeki access token en fazla 15 dakika daha geçerli kalır. Stateless JWT'nin bilinen bedelidir ve kabul edilmiştir.

### 8.6 SessionInfo — login ve refresh'in ortak cevabı

```json
"data": {
  "accessToken": "eyJ...",
  "expiresIn": 900,
  "user":  { "id": 1, "email": "admin@firma.com", "fullName": "Sistem Yöneticisi", "role": "ADMIN" },
  "menus": [ { "id": 1, "titleKey": "menu_definitions", "route": null, "icon": "settings",
               "children": [ { "id": 2, "titleKey": "menu_categories", "route": "/categories", "icon": "tag", "children": [] } ] } ]
}
```

- Bu obje tek bir yerde, `AuthServiceImpl.buildSessionInfo(user, sessionId)` metodunda üretilir. Login ve refresh aynı metodu kullanır.
- Kullanıcıya bağlı yeni bir değer gerektiğinde DTO'ya bir alan, bu metoda bir satır eklenir. Login ve refresh ikisi birden yeni değeri döner.
- **Frontend akışı:** Login'de, sayfa açılışında (F5 dahil) ve 15 dakikada bir yapılan refresh'te aynı `setSession(sessionInfo)` fonksiyonu çağrılır. Logout'ta store temizlenir. Login tekrar çağrılmaz.
- Refresh token body'de **dönmez**, sadece cookie'dedir.

### 8.7 CurrentUser — backend'deki kullanıcı bilgisi

```java
@Component
public class CurrentUser {
    public Long id()         { ... }   // JWT'nin içinden okunur, DB'ye gidilmez
    public String email()    { ... }
    public Role role()       { ... }
    public Long sessionId()  { ... }
}
```

- Server session (`HttpSession`) **kullanılmaz**. Uygulama tamamen stateless çalışır; Swarm'da istek hangi replica'ya düşerse düşsün aynı sonuç alınır.
- Audit alanları (`createdBy`, `updatedBy`) `CurrentUser`'dan doldurulur.
- Token'da olmayan kullanıcı bilgileri gerektiğinde DB'den okunur.

### 8.8 Kullanıcı kuralları

- Ayrı bir kullanıcı adı yoktur; **giriş e-posta adresiyle** yapılır (`users.email`). İleride şifre sıfırlama ve 2FA bu adres üzerinden kurulur.
- E-posta `@Email` ile doğrulanır. **Küçük harfe çevrilip** (trim edilerek) saklanır ve karşılaştırılır. Aksi halde "Ali@firma.com" ile "ali@firma.com" iki ayrı kullanıcı sayılır ve brute-force anahtarı atlatılabilir.
- E-posta, silinmemiş kayıtlar arasında benzersizdir (§10.4).
- E-posta adresinin gerçekten kullanıcıya ait olduğu şimdilik doğrulanmaz (§19).
- Normalizasyon `Locale.ROOT` ile yapılır (Türkçe locale'de "I" → "ı" olurdu). Türkçe klavyelerin "i" harfini "İ" olarak büyütebilmesi nedeniyle (ör. telefonda otomatik büyük harf) "İ" de "i" sayılır.
- Şifreler `DelegatingPasswordEncoder` (bcrypt) ile hash'lenir. Şifre en az 8 karakter, en fazla **72 byte** (UTF-8) olabilir: bcrypt 72 byte'tan uzun şifreyi hash'lemez ve Türkçe harfler 2 byte'tır. Bu sınır `@Size` ile değil `@MaxBytes(72)` ile kontrol edilir; aksi halde uzun bir şifre doğrulamadan geçip 500 hatasına dönerdi.
- Sistemde kalan **son aktif süperadmin** silinemez ve pasife alınamaz: 422 `user_last_superadmin`.
- **Yönetim kapsamı** (`UserServiceImpl`, §9): ADMIN sadece `USER`'ları düzenler, siler ve şifresini sıfırlar; başka bir
  kullanıcıya dokunursa 422 `user_not_manageable`. ADMIN sadece `USER` rolü verebilir; SUPERADMIN `USER`, `ADMIN` ve
  `OWNER` arasında geçiş yapabilir. `SUPERADMIN` rolü API'den verilemez ve alınamaz. Kurala aykırı rol: 422
  `user_role_not_assignable`.
- Rolü değişen kullanıcının bütün oturumları iptal edilir; yeni rol bir sonraki girişte gelir.
- Kayıt formu (`/api/auth/register`) `UserService.register` ile her zaman `USER` oluşturur; giriş yapmış biri olmadığı
  için yönetim kapsamı kontrolüne girmez.

---

## 9. Yetkilendirme

- **Roller:** `SUPERADMIN`, `ADMIN`, `OWNER`, `USER`. Kodda `Role` enum'u olarak tanımlıdır. Her kullanıcının tek rolü olur ve `users.role` kolonunda tutulur. Rol tablosu yoktur.
- **Hiyerarşi:** `SUPERADMIN > ADMIN > USER` ve `SUPERADMIN > OWNER`. Üstteki rol alttakinin bütün yetkilerini taşır.
  `OWNER` (proje sahibi) bilerek `ADMIN`'in yanındadır, üstünde değil: salt okunurdur ve `ADMIN`/`USER` yetkilerini
  miras almaz (docs/Security_Roadmap.md §4.1). Hiyerarşinin tek kaynağı `Role.implies()`'dır; Spring Security'nin
  `RoleHierarchy` bean'i (`SecurityConfig`) ve menü süzme (§12) buradan üretilir.
- **Yol kuralları** (`SecurityConfig`, ilk katman):

  | Yol | Gereken rol |
  |---|---|
  | `/api/auth/**` | Giriş yapmış her rol |
  | `/api/system/**` | `SUPERADMIN` |
  | `/api/insights/**` | `OWNER` (ve hiyerarşiyle `SUPERADMIN`) |
  | `/api/reports/**` | `USER` ya da `OWNER` |
  | Diğer her şey | `USER` (hiyerarşiyle `ADMIN`, `SUPERADMIN`); **`OWNER` giremez** |

- **Daha yüksek rol gereken endpoint'ler:** `@PreAuthorize("hasRole('ADMIN')")` gibi **en düşük gereken rolle** işaretlenir; hiyerarşi sayesinde üst roller de geçer.
  - Controller'ın tamamı admin'e özelse işaret sınıfın üstüne konur.
  - Herkes okuyabilip sadece admin değiştirebiliyorsa, işaret yalnızca create, update ve delete metotlarına konur.
  - Yeni bir `/api/system/**` ya da `/api/insights/**` controller'ı yol kuralına ek olarak kendi `@PreAuthorize`'ını da taşır (çift katman).
- **Herkese açık endpoint'ler** config'te beyaz listededir: `/api/auth/login`, `/api/auth/register`, `/api/auth/dev-login` (sadece geliştirmede var), `/api/auth/refresh`, `/api/auth/logout`, `/actuator/health/**`. Swagger sadece dev profilinde açılır.
- DB'de endpoint ya da yetki tablosu **yoktur**.
- **Menü görünürlüğü güvenlik değildir.** Bir menüyü gizlemek arkasındaki endpoint'i korumaz; asıl koruma `@PreAuthorize`'dır.

---

## 10. Veri katmanı

### 10.1 Şema yönetimi: `ddl-auto=update` (tüm profillerde)

Şema değişiklikleri koddan, entity üzerinden yapılır. Uygulama açılırken eksik tabloları ve kolonları **ekler**. Ancak var olan bir kolonu **değiştirmez**. `update`'in sınırları:

| Durum | Ne olur | Ne yapılmalı |
|---|---|---|
| Alan adı değişti | Yeni kolon açılır, eski kolon ve verisi kalır | Migration class'ında veri taşınır, eski kolon silinir |
| Tip veya uzunluk değişti | DB'ye yansımaz | Migration class'ında `ALTER` yapılır |
| Alan silindi | Kolon DB'de kalır | Gerekirse migration class'ında silinir |
| Enum'a yeni değer eklendi | Hibernate'in oluşturduğu `check (role in (...))` constraint'i güncellenmez; yeni değer insert edilemez | Migration class'ında constraint `ALTER` ile yenilenir |

- Hibernate'in varsayılan davranışı unique constraint'leri her açılışta silip yeniden oluşturmaktır. Bu, `hibernate.schema_update.unique_constraint_strategy=RECREATE_QUIETLY` ile kapatılmıştır: sadece eksikse oluşturulur.

- **Rolling update kuralı (Swarm):** Güncelleme sırasında eski ve yeni sürüm birkaç saniye aynı DB'de çalışır. Bu yüzden yeni eklenen kolonlar **nullable ya da default değerli** olmalıdır; yoksa eski sürümün insert'leri hata verir.
- `spring.jpa.open-in-view=false` olarak ayarlanır. Lazy alanlara controller'da erişilmez.
- `show-sql` sadece dev profilinde açıktır.

### 10.2 Şema

- Uygulamanın bütün tabloları `DB_SCHEMA` şemasında durur. Ayar iki yere birden uygulanır:
  - `spring.datasource.hikari.schema`: bağlantının `search_path`'i; `JdbcTemplate`, native query ve migration'lar bu şemada çalışır.
  - `hibernate.default_schema`: Hibernate sadece bu şemadaki tablolara bakar ve onları günceller.
- Şema yoksa ilk açılışta Hibernate oluşturur (`hibernate.hbm2ddl.create_namespaces=true`).
- `DB_SCHEMA` küçük harf olmalıdır. PostgreSQL tırnaksız adları küçük harfe çevirdiği için `starterApp` gibi bir ad
  iki ayar arasında farklı şemalara işaret eder.
- Paylaşılan bir veritabanında diğer şemalardaki aynı adlı tablolara (ör. başka bir projenin `public.categories`'i)
  dokunulmaz. Bu güvence `SchemaIsolationIntegrationTest` ile test edilir.

### 10.3 ID'ler

- Her tabloda `Long` + `IDENTITY` kullanılır. PostgreSQL'de identity kolonu kendi sequence'ını kullanır; ayrıca `@SequenceGenerator` tanımlanmaz.
- Tablo boşaltılırken her zaman `TRUNCATE tablo RESTART IDENTITY` kullanılır. Düz `TRUNCATE` sayacı sıfırlamaz.

### 10.4 Soft delete

- Hibernate `@SoftDelete` kullanılır. Silme işlemi `deleted = true` yapar ve silinmiş kayıtlar bütün sorgulardan otomatik olarak filtrelenir. `@SoftDelete` BaseEntity'ye değil, **her entity sınıfına** eklenir (ör. `Category`, `User`, `Menu`). Entity'ye eklemeyi unutmak kaydın gerçekten silinmesine ve `deleted` kolonunun oluşmamasına yol açar.
- **Unique alanlar:** Soft delete kullanılan entity'lerde `@Column(unique = true)` **kullanılmaz**. Aksi halde silinmiş bir kayıt aynı değerle yeni kayıt açılmasını engeller. Bunun yerine:
  - Serviste `existsBy...` kontrolü yapılır; sorun varsa 422 döner.
  - DB'de silinmemiş kayıtlara özel bir unique index, migration class'ında `SoftDeleteUniqueIndex` ile oluşturulur:
    `SoftDeleteUniqueIndex.create(jdbc, "ux_users_email", "users", "email");`
    PostgreSQL'de bu `CREATE UNIQUE INDEX ... WHERE deleted = false` üretir (§10.6).
- **Kullanılan kayıtlar:** Başka bir kaydın referans verdiği bir kayıt silinmez. Bu kontrol modülün `validateDelete` metodunda yapılır ve 422 döner.

### 10.5 Tarih ve saat

- Java'da `Instant`, DB'de `timestamptz`, JSON'da ISO-8601 (UTC) kullanılır.
- JVM saat dilimi uygulama açılışında (`ServerApplication.main`) `Europe/Istanbul` olarak ayarlanır; container'da ayrıca `TZ=Europe/Istanbul` verilir. Loglar Türkiye saatiyle yazılır.
- Kullanıcıya saat gösterilirken frontend değeri kullanıcının saat dilimine çevirir.

### 10.6 Yerel geliştirmede H2

- Dev profili, `DB_URL` verilmezse kurulum gerektirmeyen gömülü H2 ile açılır. Veri `server/data/` klasöründe kalıcıdır (git'e girmez). Compose'daki server bunu `h2data` volume'ünde tutar; IDE'den çalıştırmayla aynı dosyayı paylaşmaz.
- Prod ve Testcontainers testleri PostgreSQL 18'de kalır. H2 birden çok replica'yı desteklemediği için prod'da kullanılmaz; `DB_URL` prod'da zorunludur ve varsayılanı yoktur.
- URL'deki `MODE=PostgreSQL` ve `DATABASE_TO_LOWER=TRUE` aynı SQL'in iki veritabanında da çalışmasını sağlar. Uyumluluk tam değildir; SQL yazılırken iki veritabanında ortak olan söz dizimi kullanılır:

| PostgreSQL'e özgü | Bu projede yerine geçen |
|---|---|
| `generate_series` | Boş günler serviste doldurulur (§5.5) |
| `ON CONFLICT DO UPDATE` | `ON CONFLICT DO NOTHING` + ayrı `UPDATE`, tek transaction'da |
| `TIMESTAMPTZ` | `TIMESTAMP WITH TIME ZONE` |
| Partial unique index | `SoftDeleteUniqueIndex`: H2'de üretilmiş `active_<kolon>` kolonu üzerinde unique index |
| Advisory lock | H2'de alınmaz; dosyayı tek süreç açabildiği için gerekmez |

- Yeni SQL'in H2 davranışı `H2IntegrationTest`, PostgreSQL davranışı Testcontainers testleriyle doğrulanır.

---

## 11. Data migration (veri versiyonlama)

Görev paylaşımı:

| İhtiyaç | Nerede yapılır |
|---|---|
| Tablo ya da kolon **eklemek** | Entity'de; `ddl-auto=update` DB'ye uygular |
| Var olan kolonu **değiştirmek** (ad, tip, uzunluk, silme) | Migration class'ında `ALTER` ile (§10.1: `update` bunu yapmaz) |
| Veri **eklemek / güncellemek** (menüler, ilk admin, sabit veriler) | Migration class'ında |
| Index (ör. §10.4'teki partial unique index) | Migration class'ında |

Flyway kullanılmaz: Flyway Hibernate'ten önce çalıştığı için, ilk açılışta tablolar henüz oluşmamışken insert atmaya çalışırdı. Bunun yerine kendi küçük runner'ımız var.

**Her sürüm bir Java class'ıdır:**

```java
public interface DataMigration {
    int version();
    String description();
    void migrate(JdbcTemplate jdbc);
}

@Component
public class V003_InitialMenus implements DataMigration {
    public int version()         { return 3; }
    public String description()  { return "Başlangıç menüleri"; }

    public void migrate(JdbcTemplate jdbc) {
        jdbc.update("""
            INSERT INTO menus (title_key, route, icon, sort_order, roles, version, deleted, created_at)
            VALUES ('menu_definitions', NULL, 'settings', 1, 'USER', 0, false, now())
            """);
    }
}
```

- **Yeri:** `com.server.migration.versions` paketi. Class adı `V001_...`, `V002_...` şeklindedir; böylece sıra dosya listesinden de görülür.
- **Ne zaman çalışır:** Hibernate şemayı oluşturduktan **sonra**, `DataMigrationRunner` (`ApplicationRunner`) tarafından çalıştırılır. Bütün `DataMigration` bean'leri toplanır ve `version()`'a göre sıralanır.
- **Geçmiş:** Uygulanan her sürüm `data_migration_history` tablosuna kaydedilir (`version`, `description`, `applied_at`). Uygulanmış bir sürüm bir daha çalışmaz. Runner bu tabloyu kendisi oluşturur.
- **Transaction:** Her sürüm kendi transaction'ında çalışır. Hata olursa o sürümün değişiklikleri geri alınır ve uygulama açılmaz.
- **Swarm kilidi:** Runner bir PostgreSQL advisory lock altında çalışır. Aynı anda açılan iki replica aynı sürümü iki kez çalıştıramaz. Dev'deki gömülü H2'de kilit alınmaz (§10.6).
- Aynı `version()` değerine sahip iki class varsa uygulama açılmaz.

**Kurallar:**
1. Uygulanmış bir migration class'ı **değiştirilmez**. Değişiklik gerekiyorsa yeni bir sürüm yazılır.
2. Migration'larda **repository ya da entity kullanılmaz**, sadece `JdbcTemplate` ile SQL yazılır. Entity ileride değiştiğinde eski migration'lar bozulmasın diye.
3. INSERT'lerde **id yazılmaz**, çünkü identity sayacı ilerlemez ve sonraki kayıtlar çakışır. Başka kayda referans verilirken alt sorgu kullanılır:
   `(SELECT id FROM menus WHERE title_key = 'menu_definitions')`.
4. SQL ile JPA devre dışı kaldığı için BaseEntity kolonları INSERT'te açıkça verilir: `version = 0`, `deleted = false`, `created_at = now()`.
5. **Eski migration'ların kullandığı kolonlar entity'den silinmez.** Boş bir veritabanında bütün migration'lar baştan
   çalışır ve şema o an entity'lerden kurulur; kolon entity'den kalkarsa ona yazan eski migration bozulur. Kolonun
   yerini başka bir alan alıyorsa eski alan entity'de getter/setter'sız bırakılır, yeni bir migration kolona varsayılan
   değer verir (yeni migration'lar onu yazmak zorunda kalmasın) ve veriyi yeni alana taşır. Örnek: `menus.admin_only`
   → `menus.roles` (`V006`).
6. **Enum'a değer eklemek:** Hibernate'in koyduğu check constraint'i (§10.1) `EnumCheckConstraint.replace(...)` ile
   yenilenir; değerler migration'a sabit olarak yazılır, enum'dan okunmaz.

### 11.1 İlk süperadmin

- İlk hesap `V002_InitialAdmin` ile `ADMIN` olarak oluşturulur, `V006_SuperadminAndOwnerRoles` onu `SUPERADMIN` yapar
  (`ADMIN_EMAIL`'deki hesap; bulunamazsa en eski aktif admin). Yeni bir kurulumda ikisi art arda çalışır, yani
  `ADMIN_EMAIL` ile açılan hesap süperadmindir.
- `V002` `PasswordEncoder`'ı ve environment variable'ları inject alır; şifreyi hash'leyip INSERT eder.
- `ADMIN_EMAIL` ve `ADMIN_PASSWORD` değişkenlerini kullanır. Sadece bir kez çalışır; sonradan bu değişkenleri değiştirmek mevcut hesabı etkilemez.
- Prod'da `ADMIN_PASSWORD` tanımlı değilse migration hata verir ve uygulama açılmaz; varsayılan bir şifreyle hesap oluşturulmaz.
- Süperadmin ekrandan ya da API'den oluşturulamaz; tek yolu bu migration'dır.

---

## 12. Menüler

Tablo: `menus` (BaseEntity alanları + aşağıdakiler)

| Kolon | Açıklama |
|---|---|
| `parent_id` | Üst menünün id'si; kök menülerde `null`. **Düz bir `Long` alandır**, JPA ilişkisi kurulmaz |
| `title_key` | Çeviri anahtarı; benzersizdir (`menu_categories`) |
| `route` | Frontend rotası; alt menüsü olan grup menülerinde `null` |
| `icon` | Ant Design ikon adı (`home`, `setting`, `tag`, `team`); frontend `nz-icon` ile çizer. Yeni bir ikon frontend'de de kayıt edilmelidir (Frontend_Architecture.md §11) |
| `sort_order` | Aynı seviyedeki menüler arasındaki sıra |
| `roles` | Menüyü görebilen **en düşük** roller, virgülle ayrılmış (`USER`, `ADMIN`, `OWNER,USER`, `SUPERADMIN`). Java'da `Set<Role>` (`RoleSetConverter`) |
| `admin_only` | Kullanılmaz; sadece V003–V005 boş kurulumda çalışabilsin diye durur, varsayılanı `false` (§11 kural 5) |

**Kurallar:**
- **Kim ne görür:** Hiyerarşi uygulanır (§9): `roles` = `USER` olan menüyü USER, ADMIN ve SUPERADMIN görür; `ADMIN`
  olanı ADMIN ve SUPERADMIN; `OWNER,USER` olanı sahip de görür. SUPERADMIN bütün menüleri (`roles` boş olanlar dahil) görür.
- Yeni menü eklenirken `roles` yazılır, `admin_only` yazılmaz.
- **Ağaç nasıl kurulur:** Tek bir sorgu yeterlidir. Kayıtlar `sort_order`'a göre çekilir, ağaç Java'da `parentId` üzerinden kurulur. Recursive SQL ya da `@OneToMany` self-ilişki kullanılmaz (lazy loading, N+1 sorgu ve JSON döngüsü sorunlarını önlemek için).
- **Gizli üst menü:** Görünmeyen bir menünün alt menüleri de görünmez.
- Menü ağacı `SessionInfo.menus` içinde döner (§8.6).
- Menüler şimdilik sadece data migration ile yönetilir; menü yönetim ekranı yoktur.

---

## 13. Brute-force koruması

Tablo: `login_attempts`

| Kolon | Açıklama |
|---|---|
| `attempt_key` | `IP_email` (e-posta küçük harfe çevrilmiş); primary key |
| `fail_count` | Yanlış deneme sayısı |
| `blocked_until` | Engelin bitiş zamanı |
| `updated_at` | Son deneme zamanı |

**Kurallar:**
- 5. yanlış denemede `blocked_until` şu andan 1 saat sonrasına ayarlanır. Engel süresince **doğru şifre girilse bile** 429 `auth_login_blocked` döner.
- Başarılı girişte kayıt silinir. Engel süresi dolduktan sonraki ilk yanlış denemede sayaç 1'den başlar.
- Kayıtlı olmayan e-postalarla yapılan denemeler de sayılır.
- Sayaç artırma tek transaction'da yapılır: satır yoksa `INSERT ... ON CONFLICT DO NOTHING`, ardından sayacı kendi üzerinden artıran `UPDATE`. `UPDATE` satırı kilitlediği için aynı anda gelen denemeler birbirini ezmez.
- Kayıtlar DB'de tutulduğu için Swarm'daki bütün replica'lar aynı engeli görür ve engeller restart'ta kaybolmaz.

**İstemci IP'si:**
- IP her zaman `request.getRemoteAddr()` ile okunur.
- Uygulama bir reverse proxy arkasındaysa `FORWARD_HEADERS_STRATEGY=native` ayarlanır. Bu durumda Tomcat gerçek IP'yi `X-Forwarded-For` başlığından alır ve yalnızca iç ağdaki proxy'lere güvenir.
- **Swarm uyarısı:** Swarm'ın ingress routing mesh'i kaynak IP'yi değiştirir. Gerçek IP'yi korumak için en öndeki servis `mode: host` ile yayınlanmalıdır. Aksi halde anahtar fiilen sadece e-posta olur ve bir kullanıcı başkası tarafından kilitlenebilir.

### 13.2 Kayıt hız sınırı

- `RegisterRateLimiter`, `login_attempts` tablosunu ve aynı atomik sayacı `register_IP` anahtarıyla kullanır; login engelinden bağımsızdır.
- Başarılı ya da başarısız her kayıt denemesi sayılır, böylece kayıtlı e-posta taraması da sınırlanır. Sadece format doğrulamasını (`@Valid`) geçen istekler sayılır.
- Ayarlar `app.register-protection` altındadır: 10 deneme, 1 saat engel.
- Sayaç zaman penceresi kullanmaz: engel oluşmazsa temizlik işi silene kadar artar (§13.1). Aynı IP'yi paylaşan kurumlarda bu sınır yükseltilebilir.
- Yukarıdaki "İstemci IP'si" kuralları burada da geçerlidir: gerçek IP gelmiyorsa bütün kullanıcılar aynı sayacı paylaşır.

### 13.1 Temizlik işi

`@Scheduled` ile her gün 03:00'te çalışır:
- 1 günden eski `login_attempts` kayıtlarını siler.
- Süresi dolmuş `user_sessions` kayıtlarını ve 7 günden önce iptal edilmiş olanları siler.

Swarm'da bu iş her replica'da ayrı ayrı çalışır. Bu zararsızdır, çünkü yaptığı tek şey eski kayıtları silmektir.

---

## 14. Loglama ve izlenebilirlik

- **TraceId:** `TraceIdFilter`, Security filtrelerinden **önce** çalışır. Her istek için 16 karakterlik bir id üretir, bunu MDC'ye ve `X-Trace-Id` response başlığına yazar. Böylece 401 ve 403 cevaplarında da traceId bulunur.
- `ApiResponse.traceId` değeri MDC'den okunur.
- **dev profili:** Okunabilir düz metin, konsola. Her satırda thread adından sonra `[traceId]` bulunur (`logging.pattern.correlation`); istek dışındaki satırlarda boş kalır.
- **prod profili:** Boot'un yerleşik JSON (structured) log formatı (`logstash`), stdout'a. Log dosyası **yazılmaz**: birden fazla replica aynı dosyaya yazamaz ve logun nereye gideceği uygulamanın değil, kurulumun işidir.
- **Logların toplanması uygulamanın dışındadır.** `deploy/compose.yml` Docker'ın `json-file` driver'ını container başına 5 x 10 MB ile sınırlar; loglar `docker compose logs` ile okunur. İsteğe bağlı eklenti `deploy/compose.observability.yml` (Alloy → Loki → Grafana) logları toplar, 30 gün saklar ve `traceId` ile aranabilir yapar. Uygulama hangi sistemin kullanıldığını bilmez; başka bir log sistemine geçmek kod değişikliği gerektirmez. Ayrı bir "logger servisi" yazılmaz.
- Audit kayıtları ve kullanım istatistikleri (Security_Roadmap §4.2, §4.11) log değildir; DB'de tutulur.
- Log zamanları `Europe/Istanbul` saat dilimiyle yazılır.
- Şifreler, token'lar ve cookie değerleri **loglanmaz**.

---

## 15. Konfigürasyon

Dosyalar: `application.yml` (ortak), `application-dev.yml`, `application-prod.yml`.

| Environment variable | Açıklama |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` ya da `prod`. **Varsayılan profil yoktur**: unutulursa uygulama dev ayarlarıyla (güvensiz cookie, bilinen admin şifresi) açılmaz, eksik değişkenler yüzünden hiç açılmaz |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL bağlantısı (dev profilinde verilmezse H2, §10.6) |
| `DB_SCHEMA` | **Zorunlu.** Uygulamanın şeması, küçük harf (§10.2) |
| `JWT_PRIVATE_KEY_PATH`, `JWT_PUBLIC_KEY_PATH`, `JWT_KEY_ID` | Aktif RSA anahtarı |
| `JWT_PREVIOUS_PUBLIC_KEY_PATH`, `JWT_PREVIOUS_KEY_ID` | İsteğe bağlı; anahtar değişimi sırasında bir önceki public key |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Sadece ilk hesap (süperadmin) oluşturulurken kullanılır (`V002_InitialAdmin`, `V006`; §11.1) |
| `CORS_ALLOWED_ORIGINS` | Virgülle ayrılmış origin listesi |
| `COOKIE_SECURE` | dev: `false`, prod: `true` |
| `FORWARD_HEADERS_STRATEGY` | `none` (varsayılan) ya da proxy arkasındaysa `native` |
| `TZ` | `Europe/Istanbul` |

- Kodda ve repoda secret **bulunmaz**. Repoda değişkenleri listeleyen bir `.env.example` dosyası durur: `server/.env.example` uygulamanın bütün değişkenlerini, `deploy/.env.example` Docker Compose kurulumunun ayarlarını listeler (compose bunlardan server'ın değişkenlerini kurar, §16.1).
- Lokal geliştirmede değerler `server/.env` dosyasına yazılır (git'e girmez). Bu dosya **sadece dev profilinde** okunur
  (`spring.config.import: optional:file:.env[.properties]`, `application-dev.yml`); testler ve prod onu kullanmaz.
  Profil `.env`'den seçilemez; çalıştırma ayarından verilir.
- `spring.main.allow-bean-definition-overriding` kullanılmaz.

---

## 16. Docker ve deploy

- **Dockerfile:**
  - Multi-stage: build aşamasında `maven:3.9-eclipse-temurin-25`, çalışma aşamasında `eclipse-temurin:25-jre` imajı.
  - Uygulama root olmayan bir kullanıcıyla çalışır.
  - Container belleğine uyum için `-XX:MaxRAMPercentage=75` verilir.
- **Healthcheck:** Docker container'a belirli aralıklarla "hazır mısın?" diye sorar. Bunun için `/actuator/health/readiness` kullanılır. Bu endpoint, Hibernate şemayı oluşturup bütün migration'lar bittikten **sonra** "hazırım" der. Swarm rolling update'te yeni sürüm "hazırım" demeden eski sürümü kapatmaz ve yeni sürüme trafik göndermez; böylece güncelleme sırasında kullanıcılar hata görmez. Actuator'da sadece `health` dışarı açılır ve health probe'ları açık olur.
- **Graceful shutdown:** Boot'ta varsayılan olarak açıktır. `spring.lifecycle.timeout-per-shutdown-phase=30s` ayarlanır; Swarm'daki `stop_grace_period` bundan uzun olmalıdır.
- **Geliştirme ortamı:** Repo kökündeki `docker-compose.yml`'de `app` profili server'ı ve client'ı çalıştırır. Server, admin bilgilerini ve varsa `DB_URL`'i IDE'den çalıştırmayla aynı `server/.env` dosyasından (`env_file`) alır; `DB_URL` yoksa H2'yi `h2data` volume'ünde tutar (§10.6). İsteğe bağlı yerel PostgreSQL 18 `db` profilindedir; PostgreSQL 18'de volume `/var/lib/postgresql` yoluna bağlanır.

### 16.1 Prod kurulumu (`deploy/`)

Template'in varsayılan kurulumu **tek sunucu + Docker Compose**'tur. Kurulum adımları, HTTPS, güncelleme, yedekleme ve
loglar: `deploy/README.md`.

- `deploy/compose.yml`: `postgres` (18), `server` (`prod` profili), `client` (nginx). Ayarlar `deploy/.env`'den gelir
  (`deploy/.env.example`); `DB_URL` compose'da postgres servisine göre kurulur.
- Sadece client'ın portu yayınlanır, varsayılan olarak `127.0.0.1:8080`'e. PostgreSQL ve server dışarı açılmaz.
- **HTTPS** compose'un içinde değil, sunucudaki reverse proxy'de (Caddy, nginx...) sonlanır. `COOKIE_SECURE=true`
  olduğu için HTTPS olmadan giriş yapılamaz.
- JWT anahtarları Compose `secrets` ile `deploy/keys/`'ten `/run/secrets/...`'e bağlanır; Swarm'a geçilirse aynı yollar
  Docker secret ile kullanılır.
- `stop_grace_period: 40s`: uygulamanın 30 saniyelik graceful shutdown süresinden uzun.
- **Boyut ayarı yapılmaz.** JVM belleği container limitine göre ayarlanır (`MaxRAMPercentage`), HikariCP'nin varsayılan
  havuzu (10) orta ölçeğe kadar yeter. Büyük bir projede gereken birkaç değer o projede değiştirilir; template'e
  kapasiteye göre parametre eklenmez.
- Tek instance olduğu için güncellemede server yeniden açılırken kısa bir kesinti olur. Kesintisiz güncelleme ya da
  yatay ölçekleme gerekirse Swarm'a (ya da birden fazla replica'ya) geçilir; uygulama buna hazırdır (aşağıdaki liste).

### 16.2 Birden fazla replica hazırlığı

Uygulama tek sunucuda çalışsa da birden fazla replica ile (Swarm) çalışabilecek şekilde yazılır. Bu bölümdeki ve
§10, §13'teki "Swarm" notları o durum içindir.

**Stateless kontrol listesi** (her replica eşit olmalı):

| Konu | Durum |
|---|---|
| `HttpSession` | Kullanılmaz |
| Refresh token / oturumlar | DB'de (`user_sessions`) |
| Brute-force kayıtları | DB'de (`login_attempts`) |
| RSA anahtarları | Secret ya da volume'den gelir, bütün replica'larda aynıdır |
| Data migration | Advisory lock altında |
| Loglar | stdout |

**Swarm'a geçilirse ayrıca yapılacaklar:**
- HTTPS için önüne Traefik gibi bir reverse proxy konur.
- Gerçek IP'nin korunması (`mode: host`, §13).

Angular client kendi container'ında nginx ile sunulur ve `/api` isteklerini server'a iletir (Frontend_Architecture.md §14). Bu yüzden compose'da server'a `FORWARD_HEADERS_STRATEGY=native` verilir ve CORS kapalıdır.

---

## 17. Test

| Tür | Kapsam |
|---|---|
| Unit (Mockito) | ServiceImpl iş kuralları, menü ağacı kurma |
| Entegrasyon (Testcontainers + PostgreSQL 18) | Login, refresh rotation, 30 saniyelik pencere, çalınma tespiti, logout; brute-force; soft delete; version çakışması (409); data migration runner |
| Entegrasyon (bellekte H2) | Migration'lar, silinmemiş kayıtlara özel unique index, yanlış deneme sayacı, rapor sorgusu |
| Kontrat | §5.2'deki her status için response'ta dört alanın bulunduğu ve tiplerinin doğru olduğu kontrol edilir. Formatı bozan bir değişiklik build'i kırar |

- Entegrasyon testi Docker yoksa otomatik olarak atlanır (`@Testcontainers(disabledWithoutDocker = true)`).
- Testler için RSA anahtarları çalışma anında üretilir (`TestJwtKeys`); repoda private key bulunmaz.

---

## 18. Yeni modül ekleme (özet; ayrıntılı rehber README'de)

1. `modules/category` klasörü kopyalanır ve yeniden adlandırılır.
2. Entity `BaseEntity`'den türetilir ve sınıfa `@SoftDelete` eklenir; DTO'lar record olarak yazılır. Update request `VersionedRequest` interface'ini uygular.
3. Mapper interface'i `BaseMapper`'dan türetilir (gövdesi yoktur).
4. `XxxService extends BaseService`, `XxxServiceImpl extends BaseServiceImpl` olarak yazılır. İş kuralları kancalarda (§4.3) uygulanır.
5. `XxxMessages` sınıfına modülün anahtarları eklenir.
6. Controller yazılır; USER'dan yüksek bir rol gerekiyorsa en düşük gereken rolle `@PreAuthorize` eklenir (§9).
7. Menü gerekiyorsa `migration/versions` altına yeni bir `Vxxx_...` migration class'ı yazılır (id belirtilmeden).
8. Unique alan varsa silinmemiş kayıtlara özel unique index de aynı migration class'ında `SoftDeleteUniqueIndex` ile oluşturulur.

---

## 19. Kapsam dışı / sonraya bırakılanlar

- Sayfalama.
- Mail ile şifre sıfırlama ("şifremi unuttum").
- "Aktif oturumlarım" ve "tüm cihazlardan çıkış" ekranları. Gerekli veriler `user_sessions` tablosunda hazır.
- Menü yönetim ekranı.
- Virtual threads; ihtiyaç olursa tek satırla açılır.
- CI pipeline.
- E-posta doğrulama (şifre sıfırlama ve 2FA'dan önce gerekecek).
- Dosya yükleme, cache, kullanıcı başına birden fazla rol.

# Güncelleme Tasarımı — Lockpost

> **Durum:** 2026-11-10 (masaüstü 2.0.8). Kod: `desktop/src/main/java/com/desktop/update/`.

## 1. Akış

1. Uygulama açılırken `UpdateGate` GitHub'dan en son sürümü sorar:
   `GET {update.api-url}/repos/{update.repository}/releases/latest`.
2. Sürümün `release.json` dosyası okunur:
   ```json
   { "version": "1.1.0", "installer": "GuvenliPaylasim-1.1.0.msi", "sha256": "…", "mandatory": false, "signature": "…" }
   ```
3. Sürüm, kullanılan sürümden yeniyse kullanıcıya gösterilir:
   - **Süre içinde:** "Şimdi güncelle" ya da "Daha sonra".
   - **Süre dolduysa ya da `mandatory: true` ise:** sadece "Şimdi güncelle"; uygulama güncellenmeden açılmaz.
   - Süre = GitHub'daki yayımlanma zamanı + `update.grace-days` (varsayılan 3 gün).
4. "Şimdi güncelle":
   - `release.json` imzası uygulamaya gömülü açık anahtarla doğrulanır,
   - kurulum dosyası indirilir ve SHA-256 özeti imzalı özetle karşılaştırılır,
   - ikisi de tutarsa kurulum başlatılır (`msiexec /i …`) ve uygulama kapanır.

## 2. İmza

- İmzalanan metin: `gps-release/v1|{sürüm}|{sha256}|{mandatory}` (Ed25519).
- Sürüm, dosya özeti ve zorunluluk bilgisi birlikte imzalandığı için GitHub hesabı ele geçirilse bile
  - imzasız ya da değiştirilmiş bir kurulum dosyası kurdurulamaz,
  - eski bir sürüm "yeni" diye kurdurulamaz,
  - güncelleme sahte yere "zorunlu" yapılamaz.
- **Özel anahtar repoda değildir.** Proje klasöründeki `release-keys\` altında durur; bu klasör `.gitignore`'dadır.
  Yedeklenmeli ve sadece sürüm yayımlayanda durmalıdır. Kaybolursa yeni anahtar üretilir ve uygulamanın bir sonraki
  sürümü yeni açık anahtarla elle dağıtılır.
- Açık anahtar: `desktop/src/main/resources/com/desktop/update-public-key.txt`.
- **Anahtar geçmişi.** 1.0.4'e kadar ilk anahtar (`MCowBQYDK2VwAyEAz5Cm…`); 1.0.5 başka bir bilgisayarda üretilen
  ikinci anahtar (`…FnRb…`); bu anahtarın özel kısmı sürüm yayımlanan bilgisayarda bulunamadığı için **2.0.0 üçüncü
  anahtarla** (`…BDhP…`) çıktı. 1.0.5 kullananlar 2.0.0'ı bir kez elle kurar. Eski özel anahtar
  `release-keys/retired-1.0.4/` altında durur, artık kullanılmaz. Ders: özel anahtar tek bilgisayarda ve yedekli
  tutulur; yayından önce `sign` sonucu gömülü açık anahtarla doğrulanır.
  **2.0.8 (2026-11-10):** üçüncü anahtarın özel kısmı sürümün hazırlandığı bilgisayarda yoktu; 2.0.8 yine ikinci
  anahtarla (`…FnRb…`) imzalandı ve uygulamaya o gömüldü. 2.0.0–2.0.7 kullananlar 2.0.8'i bir kez elle kurar.
- **Sürüm deposu.** 2.0.8'den itibaren uygulama sürümleri kod deposundan değil, ayrı ve public
  `OguzhanDemircioglu/GuvenliPaylasim-releases` deposundan okur (`update.repository`); böylece kod deposu private
  yapılabilir. 2.0.7 ve öncesi eski depoya bakar; o depo private olunca 404 alır ve "güncel" sayar.

## 2b. MSI engellenirse (Akıllı Uygulama Denetimi, 2026-11-07)

- Windows Akıllı Uygulama Denetimi açıksa (`HKLM\SYSTEM\CurrentControlSet\Control\CI\Policy`,
  `VerifiedAndReputablePolicyState = 1`) kod imzası olmayan MSI reddedilir (msiexec 1625, günlükte 1718). Engellendiğinde
  Windows önce bir uyarı penceresi açtığı için `msiexec`'in sonucunu beklemek güvenilir değil; bu yüzden durum
  açılışta kayıttan okunur (`UpdateService.installerBlocked`). Değerlendirme modu (2) engellemez.
- Açıksa güncelleme ekranında "Şimdi güncelle" yerine açıklama ve **"ZIP'i indir"** çıkar; tarayıcıda sürümdeki
  `*-kurulumsuz.zip` (yoksa sürüm sayfası) açılır. Uygulama kapanmaz; süre dolmadıysa "Daha sonra" kalır. Süre
  dolduysa uygulama yine açılmaz, ama kullanıcının ZIP'i indirme yolu vardır.
- Bu yüzden bir sonraki sürüm de **zorunlu** işaretlenmez (ZIP kullananların süresi tanınsın).
- Açılışta bir saatten eski `%TEMP%\gps-update*` indirmeleri silinir.
- Sınır: ZIP'in özeti imzalı değil (Security.md S7). Tam çözüm: `release.json`'a ZIP özeti ve ayrı imza, uygulamanın
  ZIP'i indirip doğrulaması ve kapanınca kendi klasörünün yerine açması (konuşulmadı).
- Kalıcı çözüm kod imzalama sertifikası (SignPath Foundation, Devam_Rehberi).

## 3. Ağa ulaşılamazsa

- Kurum ağı proxy üzerinden çıkar. Proxy `HTTPS_PROXY` ortam değişkeninden, yoksa Windows ayarından alınır.
- Kurum proxy'si HTTPS'i kendi sertifikasıyla yeniden imzaladığı için uygulama Windows sertifika deposunu kullanır
  (`javax.net.ssl.trustStoreType=Windows-ROOT`, `Launcher`).
- GitHub'a ulaşılamazsa daha önce görülen sürüm bilgisi kullanılır (Windows kullanıcı ayarlarında):
  süresi geçmiş bir güncelleme biliniyorsa uygulama açılmaz ve "tekrar dene" gösterir; bilinmiyorsa açılır.
- Gerçek ortamda istemciler GitHub yerine kurum içi bir aynadan okuyabilir: `update.api-url` ayarı GitHub API
  biçiminde cevap veren herhangi bir adrese yönlendirilebilir.

## 4. Sürüm yayımlama

SignPath onaylandıktan sonra MSI ve ZIP yerelde değil, GitHub Actions'ta üretilir ve kod imzası alır
(`.github/workflows/desktop-release.yml`, `v*` etiketi ya da elle çalıştırma). İmzalı dosyalar `signed`
artifact'ından indirilir; aşağıdaki 2. ve 3. adım o dosyalarla yapılır (özel anahtar CI'da durmaz). O zamana kadar:

```bash
# 1) Sürümü desktop/pom.xml'de artır; desktop\package.cmd → dist/ altında MSI ve kurulumsuz ZIP.
# 2) İmzala: release.json kurulum dosyasının yanına yazılır
java -cp desktop/target/classes com.desktop.update.ReleaseTool sign desktop/dist/Lockpost-2.0.9.msi 2.0.9 release-keys/update-private-key.txt
# 3) Sürüm deposunda v2.0.9 etiketiyle release aç; MSI, ZIP ve release.json'u ekle. Açıklama sürüm notu olarak görünür.
gh release create v2.0.9 -R OguzhanDemircioglu/GuvenliPaylasim-releases --title 2.0.9 --notes-file notlar.md desktop/dist/Lockpost-2.0.9.msi desktop/dist/Lockpost-2.0.9-kurulumsuz.zip desktop/dist/release.json
# 4) README'nin en üstündeki indirme bağlantılarındaki sürüm numaralarını güncelle.
```

Adım 4 unutulursa düğme bir önceki sürümü indirir; o da açılışta yeni sürüme güncellenir, yani ilk kurulum bozulmaz.

Zorunlu (süre tanınmayan) güncelleme için `sign` komutuna `--mandatory` eklenir.

## 5. Ayarlar

| Ayar (`app.properties`) | Ortam değişkeni | Varsayılan |
|---|---|---|
| `update.repository` | `GPS_UPDATE_REPOSITORY` | boş (kontrol kapalı) |
| `update.grace-days` | `GPS_UPDATE_GRACE_DAYS` | 3 |
| `update.api-url` | `GPS_UPDATE_API_URL` | `https://api.github.com` |

# Güncelleme Tasarımı — Güvenli Paylaşım

> **Durum:** v1 — 2026-11-05 (prototip). Kod: `desktop/src/main/java/com/desktop/update/`.

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
- **Özel anahtar repoda değildir.** Bu makinede `D:\Projeler\GuvenliPaylasim-release-keys\` altında üretildi;
  yedeklenmeli ve sadece sürüm yayımlayanda durmalıdır. Kaybolursa yeni anahtar üretilir ve uygulamanın bir sonraki
  sürümü yeni açık anahtarla elle dağıtılır.
- Açık anahtar: `desktop/src/main/resources/com/desktop/update-public-key.txt`.

## 3. Ağa ulaşılamazsa

- Kurum ağı proxy üzerinden çıkar. Proxy `HTTPS_PROXY` ortam değişkeninden, yoksa Windows ayarından alınır.
- Kurum proxy'si HTTPS'i kendi sertifikasıyla yeniden imzaladığı için uygulama Windows sertifika deposunu kullanır
  (`javax.net.ssl.trustStoreType=Windows-ROOT`, `Launcher`).
- GitHub'a ulaşılamazsa daha önce görülen sürüm bilgisi kullanılır (Windows kullanıcı ayarlarında):
  süresi geçmiş bir güncelleme biliniyorsa uygulama açılmaz ve "tekrar dene" gösterir; bilinmiyorsa açılır.
- Gerçek ortamda istemciler GitHub yerine kurum içi bir aynadan okuyabilir: `update.api-url` ayarı GitHub API
  biçiminde cevap veren herhangi bir adrese yönlendirilebilir.

## 4. Sürüm yayımlama

```bash
# 1) Sürümü pom.xml'de artır (desktop/pom.xml → <version>), kurulum dosyasını üret (jpackage, README).
# 2) İmzala: release.json kurulum dosyasının yanına yazılır
java -cp desktop/target/classes com.desktop.update.ReleaseTool sign dist/GuvenliPaylasim-1.1.0.msi 1.1.0 ../GuvenliPaylasim-release-keys/update-private-key.txt
# 3) GitHub'da v1.1.0 etiketiyle release aç; kurulum dosyasını ve release.json'u ekle. Açıklama sürüm notu olarak görünür.
```

Zorunlu (süre tanınmayan) güncelleme için `sign` komutuna `--mandatory` eklenir.

## 5. Ayarlar

| Ayar (`app.properties`) | Ortam değişkeni | Varsayılan |
|---|---|---|
| `update.repository` | `GPS_UPDATE_REPOSITORY` | boş (kontrol kapalı) |
| `update.grace-days` | `GPS_UPDATE_GRACE_DAYS` | 3 |
| `update.api-url` | `GPS_UPDATE_API_URL` | `https://api.github.com` |

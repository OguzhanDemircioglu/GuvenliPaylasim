# Güvenli Paylaşım — Sistemin Yaşam Döngüsü

> Bu belge, sistemin teknik işleyişini baştan sona, sırayla anlatır. Görsel (yaşam döngüsü diyagramı) üretmek için
> kaynak olarak hazırlandı; her aşama tek başına okunabilir. Ayrıntılı tasarım: [Crypto_Design.md](Crypto_Design.md).

## Genel bakış

Güvenli Paylaşım, uçtan uca şifreli dosya ve mesaj paylaşım uygulamasıdır. Üç taraf vardır:

- **Gönderen:** Windows masaüstü uygulaması. Şifreleme ve imzalama burada yapılır.
- **Sunucu:** Spring Boot + PostgreSQL. Sadece şifreli veriyi saklar ve doğru kişiye teslim eder. İçeriği, dosya
  adını, şifreleri ve özel anahtarları hiçbir zaman göremez.
- **Alıcı:** Windows masaüstü uygulaması. İmza doğrulama ve şifre çözme burada yapılır.

Bir kutunun (paylaşılan dosya ya da mesaj) yaşam döngüsü yedi aşamadan geçer:

1. Kayıt ve anahtar üretimi
2. Giriş
3. İlk temas: davet kodu
4. Gönderim: şifreleme ve imza
5. Teslim ve saklama
6. Açma: doğrulama ve şifre çözme
7. Silme

Döngü 4. aşamadan 7. aşamaya her yeni kutu için tekrarlanır. 1–3. aşamalar kullanıcı ve kişi başına bir kez yapılır.

---

## Aşama 1 — Kayıt ve anahtar üretimi

**Nerede:** Gönderenin bilgisayarı ve sunucu.

1. Kullanıcı ad soyad, cep telefonu ve şifre girer.
2. Sunucu telefona WhatsApp ile 6 haneli bir kod gönderir; kullanıcı kodu girerek telefonun kendisine ait olduğunu
   kanıtlar.
3. Bilgisayar şifreden bir ana değer türetir: **PBKDF2-HMAC-SHA256**, tuz olarak telefon numarası, 600.000 tur.
4. Ana değerden iki ayrı değer çıkar:
   - **authSecret:** sunucuya "şifre" yerine gider.
   - **kek (anahtar şifreleme anahtarı):** bilgisayardan hiç çıkmaz.
5. Bilgisayar iki anahtar çifti üretir:
   - **X25519:** şifreleme için.
   - **Ed25519:** imza için.
6. Özel anahtarlar `kek` ile **AES-256-GCM** şifrelenir ve paket hâlinde sunucuya gönderilir. Açık anahtarlar düz
   gönderilir.
7. Sunucu authSecret'ı **bcrypt** ile özetler. Hem bu özeti hem özel anahtar paketini veritabanı dışında tutulan
   ikinci bir sunucu anahtarıyla (`SERVER_SEAL_KEY`) bir kez daha şifreler.

**Sonuç:** Sunucuda şifre yok; özel anahtarlar sadece kullanıcının şifresiyle açılabilen bir paket olarak duruyor.

## Aşama 2 — Giriş

**Nerede:** Kullanıcının bilgisayarı ve sunucu.

1. Kullanıcı telefon ve şifresini girer; bilgisayar aynı türetmeyle authSecret'ı yeniden hesaplar.
2. Sunucu authSecret'ı doğrular (isteğe bağlı olarak Authenticator kodu da ister) ve iki belirteç verir:
   15 dakikalık **JWT (RS256)** erişim belirteci ve her kullanımda yenilenen bir yenileme belirteci.
3. Bilgisayar özel anahtar paketini indirir, `kek` ile açar ve anahtarları sadece bellekte tutar.
4. Bağlantı sadece **TLS 1.3** ile kurulur; uygulama yalnızca Let's Encrypt köklerine zincirlenen sertifikayı kabul
   eder (sertifika sabitleme).

**Sonuç:** Kullanıcının özel anahtarları bilgisayarında, bellekte. Diske yazılmaz.

## Aşama 3 — İlk temas: davet kodu

**Nerede:** İki kullanıcının bilgisayarları; kod sunucunun dışından iletilir.

Birbirini tanımayan iki kişi ilk kez eşleşirken sunucuya güvenmek gerekmesin diye davet kodu kullanılır.

1. Gönderen alıcının telefonunu yazar. Bilgisayar rastgele **16 karakterlik (80 bit) bir kod** üretir.
2. İlk kutunun anahtarı, koddan **HKDF** ile türetilen bir anahtarla kilitlenir. Sunucuya kodun kendisi değil,
   sadece özeti gider.
3. Gönderen kodu alıcıya **sunucunun dışından** iletir: yüz yüze, telefonla ya da başka bir uygulamayla.
4. Alıcı kayıtlı değilse kodun süresi içinde (1 saat–7 gün) kayıt olur. Ardından kodu uygulamaya girer.
5. Alıcının bilgisayarı kutuyu açar ve gönderenin açık anahtar parmak izini kutunun içindekiyle karşılaştırır.
6. Alıcı bir **kabul mührü (HMAC)** bırakır; gönderenin bilgisayarı bu mühürle alıcının anahtarını doğrular.
7. İki taraf birbirinin kişi listesine eklenir. Doğrulanmış parmak izi, sahibinin Ed25519 imzasıyla saklanır.

**Sonuç:** İki taraf birbirinin gerçek anahtarına sahip. Sunucu araya sahte anahtar koyamaz. Bundan sonraki
gönderimler kodsuzdur.

## Aşama 4 — Gönderim: şifreleme ve imza

**Nerede:** Gönderenin bilgisayarı.

1. Gönderen bir mesaj yazar ya da dosya seçer (en fazla 10 MB). Programlar ve betikler reddedilir.
2. İsteğe bağlı seçenekler: kutunun ömrü (1 saat–30 gün), **Açılınca Sil**, **Kaydedilemez**.
3. Bilgisayar bu kutu için rastgele **256 bit anahtar (K)** üretir.
4. İçerik (dosya adı ve seçenekler dahil) **AES-256-GCM** ile K kullanılarak şifrelenir.
5. Gönderen şifreli içeriği **Ed25519** özel anahtarıyla imzalar.
6. K, her alıcı için ayrı ayrı sarmalanır: geçici bir X25519 anahtarı + alıcının açık anahtarı → **HKDF-SHA256** →
   **AES-GCM** (ECIES benzeri). Sarmalama o kutuya bağlıdır, başka kutuya taşınamaz.
7. Şifreli içerik, imza ve alıcı başına sarmalanmış anahtarlar sunucuya gönderilir.

**Sonuç:** Sunucuya giden her şey şifreli ve imzalı. K hiçbir yere düz gitmez.

## Aşama 5 — Teslim ve saklama

**Nerede:** Sunucu.

1. Sunucu gönderimi sadece doğrulanmış kişilere kabul eder; kota ve istek boyutu sınırlarını uygular.
2. Şifreli kutuyu, imzayı ve alıcı başına sarmalanmış anahtarları veritabanına yazar.
3. Kutu alıcının gelen kutusunda görünür; alıcının uygulaması düzenli kontrolle yeni kutuyu fark eder ve bildirim
   gösterir.
4. Sunucunun bildikleri: kimin kime, ne zaman, ne boyutta gönderdiği. Bilmedikleri: içerik, dosya adı, K, şifreler,
   özel anahtarlar.

**Sonuç:** Kutu sunucuda şifreli bekler; açabilecek tek kişi alıcıdır.

## Aşama 6 — Açma: doğrulama ve şifre çözme

**Nerede:** Alıcının bilgisayarı.

1. Alıcı kutuya tıklar; şifreli içerik, imza ve kendisine sarmalanmış anahtar indirilir.
2. Önce **imza doğrulanır**. İmza geçersizse içerik hiç çözülmez (sahte ya da yolda değiştirilmiş kutu).
3. Alıcı K'yı kendi X25519 özel anahtarıyla açar ve içeriği çözer.
4. Dosya içeriği yeniden denetlenir: makrolu Office, betikli PDF, içinde program olan arşiv gibi riskli dosyalarda
   uyarı gösterilir ve kaydetmeden önce onay istenir. Kaydedilen dosyaya Windows'un dış kaynak etiketi konur.
5. Kutu **Kaydedilemez** ise içerik sadece uygulama içinde gösterilir: kaydetme ve kopyalama yoktur, pencere ekran
   görüntüsünden hariç tutulur.

**Sonuç:** İçerik sadece alıcının ekranında, düz hâliyle var.

## Aşama 7 — Silme

**Nerede:** Sunucu.

Kutu üç yoldan biriyle sunucudan **kalıcı olarak** silinir (geri alınamaz, "silindi" işareti tutulmaz):

1. **Açılınca Sil:** alıcı kutuyu açıp doğruladığını bildirdiği anda.
2. **Okudum ve Sil:** alıcı istediği zaman.
3. **Ömür dolunca:** zamanlanmış iş 10 dakikada bir ömrü dolan kutuları siler.

Son alıcı da silince kutu tamamen kalkar; "kim kime ne zaman gönderdi" bilgisi de kalmaz. Kullanılmayan davetler
ve eski oturumlar da süreleri dolunca aynı şekilde silinir.

**Sonuç:** Kutunun yaşam döngüsü biter; sunucuda iz kalmaz.

---

## Döngünün yan süreçleri

- **Şifre değiştirme:** özel anahtarlar yeni şifreyle yeniden paketlenir; eski kutular açılmaya devam eder.
- **Şifre sıfırlama (WhatsApp koduyla):** yeni anahtar çifti üretilir; kişiye gelmiş kutular açılamayacağı için
  silinir. Kişilerinin ekranında "anahtarı değişti" uyarısı çıkar; yeniden davet kodu gerekir.
- **Hesabı silme:** kullanıcı ve ona ait her şey kalıcı olarak silinir.
- **Uygulama güncellemesi:** GitHub'daki sürüm bilgisi Ed25519 ile imzalıdır; uygulama imzayı ve kurulum dosyasının
  SHA-256 özetini doğrulamadan kurmaz.

---

## Görsel için öneri (NotebookLM'e verilecek istem)

> Bu belgedeki yedi aşamayı soldan sağa ya da dairesel bir **yaşam döngüsü diyagramı** olarak çiz. Üç şerit kullan:
> **Gönderenin bilgisayarı**, **Sunucu**, **Alıcının bilgisayarı**. Her aşamayı bir kutu olarak göster; kutunun
> içine aşamanın adını ve en önemli 1–2 işlemi yaz (ör. "AES-256-GCM ile şifrele", "Ed25519 ile imzala"). Davet
> kodunun sunucunun dışından iletildiğini ayrı bir ok ile göster. Sunucu şeridinde "içeriği göremez" notunu vurgula.
> 4–7. aşamaların her yeni kutu için tekrarlandığını döngü okuyla belirt. Silme aşamasını üç kolla (Açılınca Sil,
> Okudum ve Sil, Ömür dolunca) göster. Sade, teknik, Türkçe etiketli olsun.

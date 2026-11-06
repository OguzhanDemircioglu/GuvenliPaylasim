# Devam Rehberi

> Son güncelleme: 2026-11-06. Bu dosya "nerede kaldık, sırada ne var" sorusunun cevabıdır.
> Yeni bir oturuma başlarken önce bunu ve [Security.md](Security.md)'yi, sonra [Crypto_Design.md §7](Crypto_Design.md#7-v2-herkes-kullanıcı-telefon-ve-davet-kodu)'yi ve [§8](Crypto_Design.md#8-gizlilik-sunucu-daha-kısa-süre-bilsin)'i okuyun.

## Çalışma kuralı

**Önce konuşulur, sonra kod yazılır.** Aşağıdaki konuların hepsi konuşulup kararlar `Crypto_Design.md`'ye yazılmadan
v2 kodlamasına başlanmaz. Her kararda neyin gerekli, neyin gereksiz olduğu birlikte belirlenir.

## Ürünün amacı

Kimsenin açamayacağı bir kutuya dosya ya da mesaj konur; kutu sadece alıcının bilgisayarında açılır. Herkes eşit
kullanıcıdır; kapsam tek bir ağ ya da kurumla sınırlı değildir. Parolasını kaybeden, kendisine gelmiş kutuları da
kaybeder.

Kapsam dışı: para ya da kripto para transferi, izlenemez ödeme veya takas sistemi.

## Durum

| Konu | Durum |
|---|---|
| v1 (rollü, birimli prototip) | Çalışıyor, sürüm 1.0.5 yayında |
| 1. Temel dönüşüm (herkes kullanıcı, telefon, davet kodu, kişiler) | **Konuşuldu**, kararlar Crypto_Design §7'de. Kodlanmadı |
| 2. Gizlilik | **Konuşuldu**, kararlar Crypto_Design §8'de. Kodlanmadı |
| 3. Karşılıklı teslim (kasa) | **Konuşuldu: yapılmayacak** (gerekçe aşağıda) |
| Güvenlik: tehdit modeli ve test planı | **Konuşuldu**, [Security.md](Security.md)'de. §7 buna göre güncellendi |
| 4. İnternete açılma | **Konuşuldu**, kararlar aşağıda. Demo sunucusu Railway'de |
| TOTP ile giriş | Konuşuldu: istenir; ilk parçadan sonra eklenecek (§7.4) |

## 1. Temel dönüşüm: alınan kararlar (özet)

Ayrıntı Crypto_Design §7'de.

- Rol yok, birim yok, e-posta yok. Kayıtta ad soyad, telefon (`5XXXXXXXXX`), parola istenir. Telefon SMS ile
  doğrulanır (Vonage).
- Alıcı sadece telefonla seçilir. İlk gönderimde gönderenin ekranında 16 karakterlik kod üretilir; kutu bu kodla
  kilitlenir. Kodu gönderen kendi kanalıyla alıcıya iletir. Süreyi gönderen seçer (en fazla 7 gün). Alıcı o süre
  içinde kayıt olabilir.
- Alıcı kodu girene kadar hiçbir şey görmez. Kod girilince iki taraf birbirinin kişi listesine eklenir; sonraki
  gönderimler kodsuzdur ve bildirimle gelir. Kişi silinirse eşleşme iki yönde biter.
- Parola sıfırlama SMS ile yapılır. Kişiye gelmiş kutular silinir; kişi listesi ve kodu girilmemiş davetler kalır.
  Kişilere "anahtarı değişti" uyarısı çıkar.
- Telefon numarası değiştirilemez (parola telefonla türetiliyor). Geçişte veritabanı şeması sıfırlanır.

## 2. Gizlilik: alınan kararlar (özet)

Ayrıntı Crypto_Design §8'de. Yaklaşım: sunucu daha az şey değil, **daha kısa süre** bilir.

- **Süreli kutu.** Gönderen ömür seçer: 1 saat, 1 gün, 7 gün (varsayılan), 30 gün. Süresiz kutu yok. Süre dolunca
  kutu ve kimden-kime-ne zaman bilgisi sunucudan tamamen silinir.
- **Açılınca sil.** Gönderenin seçtiği bir seçenek, varsayılan kapalı. Alıcı kutuyu açıp doğruladıktan sonra onay
  gönderir, sunucu ancak o zaman siler (bağlantı koparsa kutu kaybolmaz).
- **Okunma durumu.** Kutu yaşadığı sürece gönderen görür. Kutu silinince gönderilenler listesinden de kalkar.
- **Kayıtlar.** Kalıcı denetim kaydı yok; §4.5'teki "kim-kime kalır" kuralı kalkar. Giriş denemeleri 24 saat,
  kapatılmış oturumlar 7 gün.
- **Gizli gönderen (teslim jetonu) ertelendi.** IP ve zamanlama sızıntısı yüzünden kazancı sınırlı. v2'de kişi
  kaydı ayrı tabloda tutulur, gönderim yetkisi tek yerde kontrol edilir; ileride jetona geçilebilir.

## 3. Karşılıklı teslim (kasa): yapılmayacak

- İçerik doğrulanamaz: sunucu kutunun içini göremez, kasa sadece "ikiniz de bir şey bıraktınız" garantisi verir.
- Sunucuya iki taraflı, zaman aşımlı, durumlu bir karar mantığı ekler; yarış durumu ve zamanlama saldırılarına açık
  yeni bir yüzey açar.
- Süreli kutu ve kayıt silme (§8) ile birleşince kapsam dışı bırakılan "izlenemez takas sistemi"ne dönüşür.
- Meşru kullanımlar başka özelliklerle daha iyi çözülür: sözleşme için karşılıklı imza, teklif zarfı için zaman
  kilidi. Gerekirse bunlar ayrıca konuşulur.

## Güvenlik ilkesi

Uygulama bütün saldırı testlerine sokulacak; geçemezse anlamı kalmaz. Bu yüzden:

- Her özellik için ilk soru: "Hangi saldırı yüzeyini açıyor, bunu savunabilir miyiz?"
- [Security.md](Security.md): saldırgan listesi (S1–S9), her biri için garanti ve kabul edilen sınırlar, test
  planı. Ölçüt **OWASP ASVS Seviye 3**; ek olarak kripto test vektörleri ve fuzz, uç nokta başına yetki
  (IDOR) ve hız sınırı testleri, bağımlılık taraması, en sonda dışarıdan sızma testi.
- Kodlama sırasındaki bir adım, ilgili saldırı testleri yazılıp geçmeden "bitti" sayılmaz.
- Alınan kararlar: sunucuda ikinci kilit ve 12+ karakter parola (S2), iki yönlü davet kodu (S3), anahtar değişince
  yeni kod (S5), sertifika sabitleme (S1), SMS sistem geneli tavanı (S8).

## 4. İnternete açılma: kararlar

**Alınan kararlar**
- **En büyük dosya 10 MB.** Parçalı şifreleme ve parçalı yükleme gerekmez; dosya tek parça, veritabanında kalır.
- **Doğrulama mesajı WhatsApp ile** (Vonage Messages API). Demo'da Vonage sandbox (Crypto_Design §7.2).
- **Demo sunucusu Railway'de** (aşağıda). Adres `https://guvenlipaylasim.up.railway.app`. DuckDNS kullanılamadı:
  Railway özel adres için CNAME ister, DuckDNS CNAME desteklemez. Kendi alan adı alınınca CNAME ile bağlanır.

**Onaylandı (2026-11-06)**
- Parmak izi için QR ekranı yapılmaz; iki yönlü davet kodu (§7.6) bu işi görür. Parmak izi kişi detayında görünür.
- Kota: kullanıcı başına aynı anda yaşayan gönderilmiş kutular toplamı 500 MB; kişilere günde 200 kutu.
- Yedekleme: kutular yedeklenmez (süreli silme sözü yedekte bozulmasın); kullanıcılar, anahtar paketleri ve kişiler
  şifreli yedeklenir, 7 gün tutulur. Sunucudaki ikinci kilidin anahtarı DB yedeğinin yanında tutulmaz.
- Gerçek kullanıcı verisinden önce KVKK: sunucunun yeri (Railway Türkiye dışında) ve aydınlatma metni.

## Demo kurulumu (Railway)

> 2026-11-06'da kuruldu. Bugün çalışan kod **v1**'dir.

| Konu | Değer |
|---|---|
| Proje | `faithful-enthusiasm`, ortam `production` |
| Servisler | `GuvenliPaylasim` (GitHub `main`, kök klasör `/server`, Railpack, JDK 25), `Postgres` (Railway şablonu, volume'lu) |
| Adres | `https://guvenlipaylasim.up.railway.app` (HTTP → HTTPS yönlendirilir), sağlık: `/actuator/health` |
| Profil | `prod`: Swagger ve dev-login kapalı, demo kullanıcıları (V007) yüklenmez |
| JWT anahtarları | `JWT_PRIVATE_KEY_PEM` / `JWT_PUBLIC_KEY_PEM` değişkenlerinde; başlangıç komutu bunları `/tmp/keys`'e yazar |
| İlk admin | `ADMIN_EMAIL` / `ADMIN_PASSWORD` değişkenlerinde (Railway panelinde görülür) |
| Veritabanı | `DB_URL` vb. `${{Postgres.*}}` referanslarıyla, özel ağdan (`postgres.railway.internal`) |

- `main`'e her push otomatik deploy eder.
- Masaüstünden bağlanmak için: `GPS_SERVER_URL=https://guvenlipaylasim.up.railway.app`.
- WhatsApp için Railway panelinde servisin Variables kısmına girilecekler (repoya ve sohbete yazılmaz):
  `MESSAGING_PROVIDER=vonage`, `VONAGE_API_KEY`, `VONAGE_API_SECRET`, `VONAGE_WHATSAPP_FROM` (sandbox numarası),
  `VONAGE_MESSAGES_URL` (varsayılan sandbox: `https://messages-sandbox.nexmo.com/v1/messages`). Basic auth kullanıldığı
  için application ID ve private key gerekmez. `MESSAGING_PROVIDER` verilmezse kod sadece sunucu log'una yazılır.
  İsteğe bağlı: `VERIFICATION_DAILY_LIMIT` (varsayılan 200).
- Railway proje token'ı sohbette paylaşıldı; kurulum işleri bitince panelden yenilenmeli.

## Kodlama sırası

> v2 kodu `v2` dalında yazılır. Railway sadece `main`'i deploy eder; sunucu tarafı kullanılabilir olunca (en az 1–3.
> adımlar) `v2` `main`'e birleştirilir. Birleştirmeden önce Railway'de `DB_SCHEMA=gps_v2` yapılır ve `ADMIN_EMAIL` /
> `ADMIN_PASSWORD` değişkenleri silinir (v1 tabloları eski şemada kalır, Crypto_Design §7.11).

Kararlar netleşince güncellenir.

1. ✅ (2026-11-06, `v2` dalı) Sunucu: rolleri, birimleri ve yönetim API'lerini kaldırmak; `User` modelini ad soyad +
   telefon olarak yeniden kurmak; şemayı sıfırlamak (yeni şema `gps_v2`); demo verisini yeniden yazmak.
   Geçici: kayıt henüz telefon doğrulamasız (2. adım); `AuthSecrets` v2 tuzuna geçti, masaüstü istemci 3. adımda.
2. ✅ (2026-11-06, `v2` dalı) Sunucu: WhatsApp kodlu kayıt ve parola sıfırlama (`register/start|complete`,
   `password-reset/start|complete`); `VonageWhatsAppGateway`; numara/IP/günlük sınırlar; kuyruktan gönderim.
   Kayıtta anahtarlar da gelir. Sıfırlamada gelen kutular silinir, oturumlar kapanır. Yerelde uçtan uca denendi;
   start yanıt süresi kayıtlı ve kayıtsız numarada aynı (medyan farkı 3–10 ms / ~690 ms).
3. Kripto (istemci ve sunucu): telefon tuzlu türetme; anahtarların kayıtta üretilmesi; test vektörlerinin
   güncellenmesi.
4. Davet kodu ve kişiler: sunucu tabloları ve API'leri, istemcide "Kod ile al" ve kişi listesi ekranları.
5. Anahtar değişikliği: doğrulanmamış kişi ve yeni kod (§7.8). Sertifika sabitleme, sunucuda ikinci kilit.
6. Gizlilik (§8): süreli kutu, açılınca sil, kayıt silme işi. Konuşmalarda kabul edilen 4. kısım maddeleri.
7. TOTP.
8. Her adımda unit testler ve ilgili saldırı testleri (Security.md); sonunda yeni sürüm (MSI) ve README güncellemesi.

## Hatırlatmalar

- **Acil, v2'den bağımsız:** `release-keys/update-private-key.txt` düz dosya olarak duruyor. Ele geçiren herkese imzalı
  zararlı sürüm dağıtabilir. Parolayla şifrelenmeli ya da donanım anahtarına taşınmalı.
- `release-keys/update-private-key.txt` (git'e girmez) güvenli bir yere **yedeklenmeli**. Kaybolursa güncelleme
  anahtarı yine değişir ve kullanıcılar elle kurulum yapmak zorunda kalır (1.0.5'te olduğu gibi).
- 1.0.4 kullananlar 1.0.5'i bir kez elle kurmalı.
- Vonage erişim bilgileri sadece `server/.env`'e (yerel) ya da ortam değişkenine yazılır, repoya girmez.
- Commit ve push her değişiklikten sonra sormadan yapılır.

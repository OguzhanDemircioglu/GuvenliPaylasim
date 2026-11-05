# Devam Rehberi

> Son güncelleme: 2026-11-06. Bu dosya "nerede kaldık, sırada ne var" sorusunun cevabıdır.
> Yeni bir oturuma başlarken önce bunu, sonra [Crypto_Design.md §7](Crypto_Design.md#7-v2-herkes-kullanıcı-telefon-ve-davet-kodu)'yi okuyun.

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
| 2. Gizlilik | **Konuşulacak** |
| 3. Karşılıklı teslim (kasa) | **Konuşulacak** |
| 4. İnternete açılma | **Konuşulacak** |
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

## 2. Gizlilik: konuşulacaklar

Amaç: sunucu daha az şey bilsin.

- **Gizli gönderen.** Gönderenin kimliği ve imzası da kutunun içine şifrelenir; sunucu sadece "bu kutu şu alıcıya"
  bilgisini görür. Signal'in "sealed sender" yöntemine benzer.
  - Soru: Kişi listesi sunucuda tutulduğu sürece sunucu kimin kiminle konuştuğunu zaten biliyor. Gizli gönderen bu
    durumda ne kadar kazandırır? Kişi listesi de şifrelenmeli mi?
  - Soru: "Gönderilenler" ekranında okunma durumu nasıl gösterilecek?
- **Süreli kutu.** Gönderen bir ömür seçer (1 saat, 1 gün, 7 gün). Süre dolunca kutu, açılmamış olsa bile sunucudan
  kalıcı olarak silinir.
  - Soru: Varsayılan süre ne olsun? Süresiz kutu olacak mı?
- **Bir kez açılır.** Alıcı açtığı anda kutu sunucudan silinir; bugünkü "Okudum ve sil" otomatik olur.
  - Soru: Her kutu için mi geçerli, yoksa gönderenin seçtiği bir seçenek mi? Açarken bağlantı koparsa ne olacak?
- **Kalan bilgilerin azaltılması.** Okundu/silindi kayıtları bugün denetim için kalıcı tutuluyor; bunlar da süre
  dolunca silinir.
  - Soru: Hangi kayıtlar ne kadar süre tutulsun?

## 3. Karşılıklı teslim (kasa): konuşulacaklar

- İki taraf da kendi kutusunu bırakır. Sunucu, iki kutu da gelmeden hiçbir tarafa açma anahtarını vermez. İkisi de
  gelince iki taraf aynı anda açabilir.
- Belirlenen süre içinde karşı taraf kutusunu bırakmazsa takas iptal olur ve kutular silinir.
- Sınır: Bu kuralı sunucu uygular. İçeriği göremez, ama anahtarı erken vermemesi için sunucuya güvenmek gerekir.
- Sorular:
  - Gerçekten gerekli mi, hangi kullanım için?
  - Kutunun içeriğinin "beklenen şey" olduğunu kim doğrular? Sunucu içeriği göremediği için doğrulayamaz.
  - Sadece kişiler arasında mı, yoksa davet koduyla da mı?

## 4. İnternete açılma: konuşulacaklar

- **Parmak izi doğrulama.** Kişi eklerken parmak izini QR kodla ya da telefonda okuyarak karşılaştırma. Sunucunun
  araya sahte anahtar koymadığı böyle doğrulanır.
  - Soru: Davet kodu zaten parmak izini taşıyor (§7.6). Ek doğrulama sadece isteğe bağlı mı olsun?
- **Kötüye kullanım sınırları.** Kayıt ve gönderim için hız sınırı (taslak §7.9'da); kullanıcı başına depolama ve
  dosya boyutu kotası.
  - Soru: Kota ne kadar olsun?
- **Büyük dosyalar.** Bugün sınır yaklaşık 10 MB ve dosya tek parça gönderiliyor. Parçalı şifreleme ve parçalı
  yüklemeyle yüzlerce MB'lık dosyalar gönderilebilir.
  - Soru: Hedef en büyük dosya boyutu ne?
- **Prod kurulumu.** HTTPS, sunucunun internete kurulması, yedekleme.
  - Sorular: Sunucu nerede çalışacak? Alan adı ne olacak? Yedekler nerede tutulacak?

## Konuşmalar bitince: kodlama sırası (taslak)

Kararlar netleşince güncellenir.

1. Sunucu: rolleri, birimleri ve yönetim API'lerini kaldırmak; `User` modelini ad soyad + telefon olarak yeniden
   kurmak; şemayı sıfırlamak; demo verisini yeniden yazmak.
2. Sunucu: SMS doğrulamalı kayıt ve parola sıfırlama; Vonage `SmsGateway`; SMS sınırları.
3. Kripto (istemci ve sunucu): telefon tuzlu türetme; anahtarların kayıtta üretilmesi; test vektörlerinin
   güncellenmesi.
4. Davet kodu ve kişiler: sunucu tabloları ve API'leri, istemcide "Kod ile al" ve kişi listesi ekranları.
5. Anahtar değişikliği uyarısı.
6. Konuşmalarda kabul edilen 2. ve 4. kısım maddeleri.
7. TOTP.
8. Her adımda unit testler; sonunda yeni sürüm (MSI) ve README güncellemesi.

## Hatırlatmalar

- `release-keys/update-private-key.txt` (git'e girmez) güvenli bir yere **yedeklenmeli**. Kaybolursa güncelleme
  anahtarı yine değişir ve kullanıcılar elle kurulum yapmak zorunda kalır (1.0.5'te olduğu gibi).
- 1.0.4 kullananlar 1.0.5'i bir kez elle kurmalı.
- Vonage erişim bilgileri sadece `server/.env`'e (yerel) ya da ortam değişkenine yazılır, repoya girmez.
- Commit ve push her değişiklikten sonra sormadan yapılır.

# Arayüz ve Form Standartları

> **Durum:** v1 — 2026-11-07. Lockpost'ta (JavaFX masaüstü) uygulandı; StarterApp'e (Angular + NG-ZORRO) de
> aynı kurallarla uygulanacak. Kurallar teknolojiden bağımsızdır; her bölümün sonunda iki projeye özgü notlar var.
> Bir kural değişecekse önce bu belge güncellenir, sonra kod.

---

## 1. Amaç

Giriş, kayıt, şifre sıfırlama ve profil ekranları her projede aynı davranmalı: kullanıcı eksik ya da kurala uymayan bir
formu gönderemez, ne eksik olduğunu doğru yerde ve doğru zamanda görür, metinler tek bir dil ve yazım düzenindedir.

---

## 2. Dil ve yazım

| Kural | Doğru | Yanlış |
|---|---|---|
| "Parola" değil **"Şifre"** (ekleriyle: şifreniz, şifreyi, şifreler) | Şifremi Unuttum | Parolamı unuttum |
| **Kısa etiketlerde her kelime büyük harfle başlar**: düğme, bağlantı, sekme, alan adı, başlık, tablo başlığı, onay kutusu | Ad Soyad, WhatsApp Kodu, Kod Gönder, Giriş Yap, Kayıt Ol | Ad soyad, Kod gönder |
| Bağlaçlar küçük kalır: *ve, ile, ya, da, de* | Şifrele ve Gönder, Kod ile Al | Şifrele Ve Gönder |
| Parantez içi küçük kalır | Şifre (12–20 karakter), Bu Cihaza Güven (30 gün) | Şifre (12–20 Karakter) |
| Cümleler (açıklama, uyarı, hata, ilerleme mesajı) cümle düzeninde, noktayla biter | Şifreler uyuşmuyor. | Şifreler Uyuşmuyor |
| İlerleme mesajları "…" ile biter | Kod gönderiliyor… | Kod gönderiliyor... |
| Türkçe büyük harf kuralı: i → İ, ı → I | İki Adımlı Doğrulama | Iki Adımlı Doğrulama |

Hata mesajları sunucudan **anahtar** olarak gelir (`user_full_name_words`), metni istemci gösterir; aynı anahtar her
yerde aynı metni verir.

**Lockpost:** `desktop/.../api/Messages.java`. **StarterApp:** ngx-translate `tr.json` (Frontend_Architecture
§10).

---

## 3. Tipografi ve boyutlar

- Yazı tipi **Verdana** (yoksa Segoe UI, sans-serif). Eş aralıklı alanlar (parmak izi, kodlar): Cascadia Mono / Consolas.
- Temel boyut **15 px**; başlıklar 26–37 px, yardımcı metinler 12–13 px. Boyutlar tek bir stil dosyasında tutulur.
- **Metinli düğmeler** büyük: iç boşluk 10 × 18 px, en az 40 px yükseklik; birincil form düğmesi 46–48 px.
  Sadece ikonlu düğmeler (tema, çıkış) temanın boyutunda kalır.
- **Düğme metni hiçbir zaman kesilmez** ("Kod i…" olmaz): düğmenin en küçük genişliği metninin genişliğidir; yer
  daralırsa yanındaki yazı alt satıra kayar.
- Yazı tipi ya da boyut değişince **bütün ekranlar** görüntüyle kontrol edilir (kesilen metin, taşan alan); sabit
  genişlikli sütunlar gerekirse büyütülür.

**Lockpost:** `app.css` (`.root`, `.gps-button`), `Ui.button` (`gps-button` sınıfı + `minWidth = USE_PREF_SIZE`);
giriş kartı 1000×600, ana pencere 1440×900. **StarterApp:** global `styles.scss` (`font-family`, `font-size`), NG-ZORRO
tema değişkenleri (`@font-family`, `@font-size-base`), `nz-button` için `white-space: nowrap` ve `min-width: max-content`.

---

## 4. Alan kuralları

Kurallar **yazarken** uygulanır: kabul edilmeyen karakter alana hiç girmez. Kaydetmeden önce aynı kural bir kez daha
denetlenir; sunucu da (görebildiği her şeyi) denetler.

### 4.1 Cep telefonu

- Görünüm ve yazım `(5xx) xxx xx xx`; sadece rakam. Baştaki `0` ve `+90` atılır; ilk hane 5 değilse girilmez; 10
  haneden fazlası girilmez. Yapıştırılan `0532-123-45-67`, `+90 532 123 45 67` aynı biçime çevrilir.
- Sunucuya `5XXXXXXXXX` gider; sunucu `^5\d{9}$` ile denetler.

### 4.2 Ad Soyad

- Harf (Türkçe dahil), boşluk, kesme, nokta, tire; rakam ve başka işaret girilmez.
- **2–3 kelime**, en fazla **30 karakter**; başta ve art arda boşluk girilmez; üçüncü kelimeden sonra boşluk girilmez.
- Yazarken kelime ve tire başları Türkçe kurala göre büyütülür (`ismail ışık-çelik` → `İsmail Işık-Çelik`); diğer
  harflere dokunulmaz (`McDonald` bozulmaz).
- Sunucu: aynı karakter kuralı, 2–3 kelime, 30 karakter; boşlukları teke indirir.

### 4.3 Kod alanları

- WhatsApp/SMS kodu ve Authenticator kodu: **sadece rakam, 6 hane**.
- Kurtarma kodu harf içerebileceği için iki adımlı doğrulama kutusu harfi kabul eder.

### 4.4 Şifre

- **12–20 karakter**; en az bir büyük harf, bir küçük harf, bir rakam ve bir özel karakter (boşluk özel sayılmaz).
- Alana **20 karakterden fazlası yazılamaz**; yapıştırılan fazlası kesilir (giriş, kayıt, sıfırlama, profil, onay
  pencereleri — her şifre alanı).
- Yeni şifrede (kayıt, sıfırlama, profil) ayrıca zayıf şifre kontrolü: bilinen kelime/isim + yıl, klavye dizisi
  (`12345`, `qwerty`), tekrar, kişinin kendi adı ya da telefonu.
- **Sunucu denetimi:** sunucu şifreyi görüyorsa (StarterApp: bcrypt) aynı kural sunucuda da uygulanır. Sunucu şifreyi
  görmüyorsa (Lockpost: istemci şifreden türetilmiş değer gönderir) kural sadece istemcidedir ve bu belgelenir.
- En az 12 karakter bilinçli bir alt sınırdır; düşürülmeden önce güvenlik etkisi konuşulur.

### 4.5 Dosya türleri ve risk

- **Kabul edilen:** resim (jpg, png, gif, bmp, webp, tif, heic, svg, ico), PDF, metin (txt, csv, rtf, md, json,
  xml), Office ve OpenDocument (doc/docx/docm, xls/xlsx/xlsm, ppt/pptx/pptm, odt/ods/odp), arşiv (zip, 7z, rar), ses
  ve görüntü (mp3, wav, m4a, mp4, mov). Dosya seçicide "Desteklenen dosyalar" filtresi; ipucunda kısa liste.
- **Reddedilen:** programlar ve betikler (exe, msi, bat, cmd, ps1, vbs, js, lnk, jar, dll, html, sh, py, iso…) —
  uzantıdan ve içerikten (program başlığı) anlaşılır; adında yönü ters çeviren gizli karakter olan dosya. Gönderen
  uyarıyı toast olarak görür, dosya eklenmez.
- **Riskli (gönderilir, alıcı uyarılır):** makrolu Office, betik/başlatma/gömülü dosya içeren PDF, içinde program
  olan zip, içi denetlenemeyen 7z/rar, betikli SVG, iki uzantılı ad, uzantıyla uyuşmayan içerik, bilinmeyen tür.
- **Alıcıda:** dosya kartının altında sarı uyarı (program/betikse kırmızı) ve nedenler; "Bilgisayara Kaydet" önce
  onay penceresi açar ("Yine de Kaydet"). Uyarıda teknik terim yerine etki yazılır ("makrolar kendiliğinden
  çalışmaz"), "internetten geldi" gibi kullanıcıyı şaşırtan ifadeler kullanılmaz.
- Denetim her iki uçta da yapılır; alıcı göndericinin denetimine güvenmez.

### 4.6 Kaydedilemez belge

- Gönderende "Kaydedilemez" kutusu, "Açılınca Sil"in yanında. Sadece uygulamanın gösterebildiği içerikte açık: düz
  yazı, jpg/png/gif/bmp, PDF. Diğer türlerde kutu kapalı ve işareti kalkar; ipucu nedenini ve sınırını yazar
  ("caydırıcıdır").
- Alıcıda: kaydet düğmesi yok; içerik seçilemez/kopyalanamaz; şerit: "Gönderen kaydetmeye izin vermedi: belge sadece
  burada görüntülenir, kopyalanamaz. Ekran görüntüsü alınamaz." Ekran koruması kurulamazsa şerit bunu söyler.
- Ekran koruması sadece böyle bir belge ekrandayken açıktır; başka belgeye ya da sayfaya geçince kalkar.
- Bilgi şeritleri görüntüleyici büyüse de kısalmaz ("…" ile kesilmez): `minHeight = USE_PREF_SIZE`.

---

## 5. Uyarıların yeri ve zamanı

| Ekran | Kural |
|---|---|
| **Giriş** | Hiç uyarı gösterilmez. Telefon tam ve şifre kurala uygun olmadan düğme kapalıdır; neden yazılmaz. Sunucudan gelen hata ("Telefon ya da şifre hatalı.") düğmenin üstünde gösterilir. |
| **Kayıt — ilk şifre** | İlk karakterden itibaren alanın **hemen altında** kural uyarısı; kural tutunca kalkar. |
| **Kayıt — şifre tekrarı** | İlk şifre geçerliyse, tekrar alanına **ilk karakter yazılınca** alanın altında "Şifreler uyuşmuyor." — eşleşene kadar. İlk şifre geçersizken tekrar alanında uyarı yoktur. |
| **Kayıt — diğer alanlar** | Bütün alanlar doldurulmuş ama ad, telefon ya da onay kuralı tutmuyorsa, nedeni formun durum satırında yazılır (şifre uyarıları burada tekrarlanmaz). |
| **Şifremi Unuttum, Profil (şifre değiştir)** | Kayıttaki ilk şifre ve tekrar kuralının aynısı. |
| **Profil (ad)** | Ad geçersizse alanın altında neden yazılır. |

Uyarı alanın altında yer kaplamaz; görünmediğinde düzeni kaydırmaz.

**Lockpost:** `PasswordFields.limit`, `PasswordFields.hint`, `PasswordFields.bindHints`. **StarterApp:**
Reactive Forms + `nz-form-control [nzErrorTip]`; şifre tekrarı için form grubu doğrulayıcısı (`passwordsMatch`);
uyarının ne zaman görüneceği `dirty` (ilk karakter) ile belirlenir, `touched` ile değil.

---

## 6. Düğmelerin açık/kapalı kuralları

Düğme, gönderilebilecek durumda değilse **kapalıdır**; kullanıcı basıp hata almaz. Bir işlem sürerken (ağ isteği)
düğme meşguldür ve tekrar basılamaz.

| Düğme | Açık olması için |
|---|---|
| Giriş Yap | Telefon 10 hane + şifre kurala uygun |
| Kod Gönder (kayıt) | Ad, telefon, şifre kuralları, şifre tekrarı, aydınlatma metni onayı tamam; geri sayım yok |
| Kayıt Ol | Kod Gönder koşulları + kod 6 hane |
| Kod Gönder (şifre sıfırlama) | Telefon 10 hane; geri sayım yok |
| Şifreyi Sıfırla | Telefon, kod 6 hane, yeni şifre kuralları, tekrar eşleşiyor (iki adımlı kod isteğe bağlı) |
| Şifreyi Değiştir (profil) | Mevcut şifre kurala uygun, yeni şifre kuralları, tekrar eşleşiyor |
| Adı Kaydet | Ad değişmiş ve kurallara uygun |
| Hesabımı Kalıcı Olarak Sil | Şifre kurala uygun (+ iki adımlı doğrulama açıksa kod) |

**Lockpost:** `disableProperty().bind(meşgul.or(geçersiz))`; düğme kodda doğrudan `setDisable` ile açılıp
kapatılmaz. **StarterApp:** `[disabled]="form.invalid || loading()"`; `nzLoading` ile meşgul durumu.

---

## 7. Doğrulama kodu gönderme

- "Kod Gönder"den sonra **300 saniye** (kodun geçerlilik süresi) geri sayılır; bu sürede düğme kapalıdır. Dar yerde
  sadece kalan süre (`4:58`), düğmede `Tekrar Gönder (4:58)`; süre bitince "Tekrar Gönder".
- Sunucuda numara/IP başına saatlik sınır ve günlük tavan vardır; istemci sınırı sunucunun yerine geçmez.
- **Test modu** (`TEST_MODE`, varsayılan kapalı): kayıt ve şifre sıfırlama kodu mesajla gönderilmez, cevapta döner ve alana
  yazılır; ekranda "TEST MODU" yazar. Açıkken numarayı bilen herkes şifre sıfırlayabilir; sadece demo.
  Gerçek kullanıcıdan önce kapatılır.

---

## 8. Oturum: "Bu Cihaza Güven (30 gün)"

- Giriş ve kayıt ekranında aynı adla bulunur; seçilirse uygulama 30 gün boyunca şifre sormadan açılır.
- Seçilmeden yapılan giriş eski kaydı siler; "Güvenli Çıkış" kaydı siler; sunucu oturumu kabul etmezse kayıt silinir.
- Kayıt işletim sisteminin korumalı deposunda tutulur. **Lockpost:** Windows DPAPI
  (`%LOCALAPPDATA%\GuvenliPaylasim\remember.bin`). **StarterApp:** uzun ömürlü refresh token HttpOnly + Secure cookie'de;
  `localStorage`/`sessionStorage`'a token yazılmaz.
- İpucu: "Ortak kullanılan bilgisayarda seçmeyin."

---

## 9. Kayıt ve hesap

- **Aydınlatma metni** (KVKK m.10): kayıtta "Aydınlatma Metnini Onaylıyorum" zorunlu; bağlantı metni açar, "Onaylıyorum" kutuyu
  işaretler. Metin hukuki incelemeden geçmeden "TASLAK" diye işaretli kalır. Not: aydınlatma bir bilgilendirmedir,
  açık rıza değildir; açık rıza gereken bir işlem (ör. yurt dışına arızi aktarım) olursa ayrı bir kutuyla alınır.
- **Hesabımı Sil:** profilde; uyarı penceresinde neyin silineceği ve geri alınamayacağı yazar; şifre (+ iki adımlı
  doğrulama kodu) istenir; yanlış denemeler sayılır. Kişi ve ilgili her şey kalıcı silinir, "silindi" işareti tutulmaz.
- **Profil:** ad değiştirilebilir (aynı ad kuralı); telefon gibi kimliğe bağlı alanlar salt okunur gösterilir ve nedeni
  yazar.

---

## 10. Giriş/kayıt ekranının görünümü

- Sürüm numarası `v2.0.3` biçiminde; girişte sağ altta (sağ kenarı karşılama başlığıyla aynı hizada), kayıtta sol
  altta (sol kenarı karşılama başlığıyla aynı hizada); koyu zeminde beyaz, kalın. Ekranlar arası geçişte görünmez.
- Uygulama logosu kenar çubuğunun sol üstünde ve pencere başlığında.

---

## 11. Kontrol listesi (yeni form ya da ekran)

- [ ] Etiketler kısa ve her kelimesi büyük harfle başlıyor; cümleler cümle düzeninde; "parola" yok.
- [ ] Her alanın yazarken uygulanan kuralı var (telefon, ad, kod, şifre); şifre alanı 20 karakterde duruyor.
- [ ] Uyarılar §5'teki yerde ve zamanda; girişte uyarı yok.
- [ ] Gönder düğmesi form geçersizken ve işlem sürerken kapalı.
- [ ] Kod gönderen her düğmede 300 sn geri sayım var.
- [ ] Sunucu, görebildiği her kuralı ayrıca denetliyor; hata anahtarlarının Türkçe metni var.
- [ ] Bütün ekranlar görüntüyle kontrol edildi: kesilen metin, taşan alan yok.
- [ ] Davranışlar gerçek arayüz bileşenleri üzerinde test edildi (yazma, yapıştırma, silme).

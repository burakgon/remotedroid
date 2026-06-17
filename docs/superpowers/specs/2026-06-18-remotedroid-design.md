# RemoteDroid — Tasarım Dokümanı

- **Tarih:** 2026-06-18
- **Durum:** Onay bekliyor (taslak)
- **Lisans (öneri):** Apache-2.0
- **Proje adı:** RemoteDroid

---

## 1. Özet ve Amaç

Thomson Go Plus **32UE5M45**, içinde **gerçek Android** (Android TV değil) çalışan,
dokunmatik ekranlı, kumandası olmayan bir TV. Şu an her kullanımda fiziksel olarak
TV'nin yanına gidip ekrana dokunmak (hatta ekran kilidi desenini girmek) gerekiyor.

**RemoteDroid**, bu TV'yi koltuktan kontrol etmeyi sağlayan iki parçalı, açık kaynak
bir sistemdir:

- **Sunucu (TV tarafı):** TV'de çalışan native Android uygulaması. Bir **Erişilebilirlik
  Servisi** üzerinden sisteme dokunma/ses/yazı girdisi enjekte eder ve gömülü bir web
  sunucusu çalıştırır. **Root gerektirmez.**
- **Kumanda (client):** TV'nin sunduğu, telefonun tarayıcısından açılan bir **PWA**.
  Ayrı bir mağaza uygulaması yok; "ana ekrana ekle" ile kısayol olur. Android, iPhone
  ve bilgisayar tarayıcılarında çalışır.

Tasarım ilkesi: **touchpad-first** — kumandanın merkezinde, gerçek bir laptop trackpad'i
gibi davranan büyük bir dokunma yüzeyi vardır.

---

## 2. Hedefler ve Hedef Olmayanlar

### Hedefler (v1)
- **Göreli imleçli touchpad:** Parmak hareketi TV'de bir imleci kaydırır; tık/uzun
  basış/sürükle/iki-parmak-kaydırma desteklenir.
- **Ses kontrolü:** Sesi aç/kıs/sessize al.
- **Klavyeyle yazı:** Telefondaki kutuya yazılan metnin **tamamı tek seferde** TV'deki
  odaklı alana düşer; ayrı bir "Enter/Ara" eylemi.
- **Gezinme:** Home, Geri, Son uygulamalar, Bildirimler, Hızlı Ayarlar, Uyut.
- **QR ile eşleştirme:** TV'de QR; telefonla okutunca adres + token gelir, otomatik bağlanır.
- **Root yok, ek cihaz (PC) yok.**
- **Modern, şık arayüz** ve temiz açık kaynak proje yapısı.

### Hedef Olmayanlar (v1 dışı)
- Ekran yansıtma / mirroring (TV'ye bakarak kullanılır; "kör" touchpad).
- Kapalı TV'yi açma (WiFi üzerinden Wake-on-LAN yolu yok).
- Root tabanlı gelişmiş mod.
- İnternet üzerinden uzaktan erişim (yalnızca aynı yerel ağ).
- Çoklu kullanıcı / hesap yönetimi.

---

## 3. Mimari — Genel Görünüm

```
┌──────────────────────────┐      WiFi / LAN        ┌────────────────────────────────┐
│   TELEFON (Tarayıcı)      │  ws://tv-ip:8080       │         TV (Android)           │
│   ──  PWA Kumanda  ──     │   + token (QR'dan)     │   ──  RemoteDroid Sunucu  ──   │
│                          │ ◄───────────────────►  │                                │
│  • Touchpad yüzeyi        │   JSON komutları       │  Ktor sunucu (HTTP + WebSocket)│
│  • Ses +/−               │  move·tap·scroll       │        │                       │
│  • Yazı kutusu + Gönder    │  vol·text·home...      │        ▼                       │
│  • Home / Back / Recents  │                        │  Erişilebilirlik Servisi       │
│  • QR okutarak bağlanır    │                        │   • İmleç overlay'i (cursor)   │
└──────────────────────────┘                        │   • dispatchGesture (dokunma)  │
                                                      │   • ACTION_SET_TEXT (yazı)     │
                                                      │   • Global: Home/Back/Vol      │
                                                      │  Foreground Service (kalıcı)   │
                                                      │  Kurulum ekranı (Compose)+QR   │
                                                      └────────────────────────────────┘
```

**Veri akışı:** Kumanda parmak hareketini yakalar → göreli delta'ları JSON olarak
WebSocket üzerinden gönderir → sunucu komutu Erişilebilirlik Servisi'nin bir eylemine
çevirir (imleci kaydır, `dispatchGesture` ile dokun, metni yaz, sesi değiştir...).

---

## 4. Sunucu (TV) Bileşenleri

### 4.1 Erişilebilirlik Servisi (çekirdek)
Girdiyi sisteme enjekte eden katman:

- **İmleç overlay'i:** Servis, `WindowManager` ile ekrana küçük bir imleç görseli ekler
  (`TYPE_ACCESSIBILITY_OVERLAY`). Overlay **dokunmayı geçirgendir** (`FLAG_NOT_TOUCHABLE`,
  `FLAG_NOT_FOCUSABLE`) — yalnızca görseldir, altındaki uygulamaya dokunmayı engellemez.
  İmlecin (x, y) konumu serviste tutulur; gelen delta'larla güncellenir, ekran sınırlarına
  kıstırılır. Konum otoritesi sunucudadır.
- **Dokunma hareketleri** (`dispatchGesture`, hep imlecin konumunda):
  - `move` → sadece imleci kaydır (görsel; gesture dispatch edilmez).
  - `tap` → kısa dokunma. `longpress` → uzun basış (daha uzun süreli stroke).
  - `drag` → bas-sürükle-bırak; `willContinue` ile akıcı sürekli stroke.
  - `scroll` → **iki paralel stroke** ile gerçek iki-parmak kaydırma
    (`dispatchGesture` çoklu dokunmayı destekler).
- **Yazı:** `findFocus(FOCUS_INPUT)` ile odaklı düzenlenebilir düğümü bulur,
  `ACTION_SET_TEXT` ile metni **tek seferde** basar, imleci sona alır
  (`ACTION_SET_SELECTION`). "Enter/Ara" için `ACTION_IME_ENTER` (Android 11 / API 30+).
- **Genel eylemler:** `GLOBAL_ACTION_HOME`, `BACK`, `RECENTS`, `NOTIFICATIONS`,
  `QUICK_SETTINGS`, `LOCK_SCREEN` (uyut).
- **Ses:** `AudioManager.adjustStreamVolume(STREAM_MUSIC, RAISE/LOWER, FLAG_SHOW_UI)`;
  TV'de ses çubuğu da görünür. Sessize alma dahil.

### 4.2 Foreground Service (kalıcılık)
WebSocket/HTTP sunucusunu ayakta tutar, kalıcı bildirimle çalışır, cihaz açılışında
otomatik başlar. Pil optimizasyonunu devre dışı bırakma izni istenir (TV genelde prize
takılı olduğundan kritik değil ama OEM'lerin servisi öldürmesine karşı önlem).

### 4.3 Gömülü Sunucu (Ktor CIO)
- PWA dosyalarını APK `assets/web`'ten **statik** sunar (`GET /`).
- `GET /ws?t=TOKEN` → WebSocket komut kanalı (token bağlantı kurulurken doğrulanır).
- Yerel ağ IP'sine bağlanır, sabit port (varsayılan `8080`, çakışırsa ayarlanabilir).

### 4.4 Kurulum Ekranı (Jetpack Compose, Material 3)
Durum panosu:
- Erişilebilirlik servisi açık mı? (değilse ayarlara yönlendiren buton)
- **QR kodu** (adres + token), token'ı yenile butonu
- Port ayarı, sunucuyu başlat/durdur, IP adresi
- Adım adım kurulum yönergesi (TR)

---

## 5. Kumanda (PWA) Bileşenleri

**touchpad-first** yerleşim (dikey telefon ana hedef; yatay ve masaüstü uyumlu):

```
┌───────────────────────────┐
│ ● Bağlı            ⚙  ⌨    │  durum · ayarlar · klavye
├───────────────────────────┤
│                           │
│       T O U C H P A D     │  ekranın çoğu — büyük yüzey
│   sürükle · tık · iki     │
│     parmakla kaydır       │
│                           │
├───────────────────────────┤
│   ◀ Geri   ⌂ Home   ▣      │  gezinme
├───────────────────────────┤
│   🔉 −       🔇        🔊 + │  ses
└───────────────────────────┘
```

Klavye paneli (⌨ ile altan açılır):
```
│  Mesajını yaz:                  │
│  ┌───────────────────────────┐  │ ← telefonun kendi klavyesi
│  │ merhaba dünya|            │  │
│  └───────────────────────────┘  │
│  [  Gönder  ]   [ Enter / Ara ] │
│  [ Backspace ]  [   Temizle   ] │
```

### Etkileşim
- **Touchpad:** Pointer Events ile tek parmak hareketi → **göreli delta**; `requestAnimationFrame`
  ile birleştirilip ~60–120/sn üst sınırla gönderilir. Hızlı dokunuş = `tap`; basılı tutma =
  `longpress`; basılı tutup sürükle = `drag`; iki parmak = `scroll`. Ayarlarda hassasiyet/ivme.
- **Dokunsal geri bildirim:** `navigator.vibrate` (destekleyen cihazda).
- **Ses:** ekran düğmeleri (telefon fiziksel ses tuşları tarayıcıdan güvenilir yakalanamaz).

### Bağlantı Akışı (QR + kalıcılık)
> **İnce nokta:** PWA'nın içinden kamerayla QR okumak `http` LAN'da çalışmaz (kamera güvenli
> bağlam ister). Bu yüzden okuma **telefonun kendi kamera uygulamasıyla** yapılır.

1. TV, `http://tv-ip:8080/?t=TOKEN` adresini taşıyan QR'ı gösterir.
2. Telefon kamerasıyla okutulur → tarayıcı bu adresi açar → PWA token'la yüklenir.
3. PWA token'ı `localStorage`'a kaydeder, `ws://tv-ip:8080/ws?t=TOKEN`'e bağlanır.
4. Sonraki açılışlarda token hafızadan gelir → otomatik bağlanır. Kopunca otomatik yeniden
   bağlanma + durum göstergesi (bağlı / bağlanıyor / kopuk).

### Görsel Yön
Karanlık tema, geniş dokunma hedefleri, ortada hafif "ripple" geri bildirimli touchpad, tek
vurgu rengi, akıcı animasyonlar, sade ve modern. Görsel incelik uygulama aşamasında
`frontend-design` becerisiyle damıtılacak.

---

## 6. İletişim Protokolü

**Taşıma:** WebSocket üzerinden **JSON** mesajları (LAN'da bant genişliği bol; hata ayıklaması
kolay; TCP olduğundan sıralı ve güvenilir). Token, WS bağlantısı kurulurken doğrulanır.

### Kumanda → Sunucu

| `type` | Alanlar | Anlamı |
|---|---|---|
| `hello` | `token`, `client`, `v` | İlk mesaj; meta veri |
| `move` | `dx`, `dy` | Göreli imleç hareketi (birleştirilmiş) |
| `tap` | — | İmlecin yerine dokun |
| `longpress` | — | Uzun basış |
| `dragstart` / `dragmove` / `dragend` | (`dx`,`dy`) | Bas-sürükle-bırak |
| `scroll` | `dx`, `dy` | İki parmak kaydırma |
| `volume` | `dir`: `up`/`down` | Ses |
| `mute` | — | Sessize al/aç |
| `text` | `value` | Metni tek seferde odaklı alana yaz |
| `submit` | — | Enter / Ara (`ACTION_IME_ENTER`, A11+) |
| `backspace` / `clear` | — | Sil / temizle |
| `global` | `action`: `home`,`back`,`recents`,`notifications`,`quicksettings`,`sleep` | Genel eylemler |
| `ping` | — | Canlılık |

### Sunucu → Kumanda

| `type` | Alanlar | Anlamı |
|---|---|---|
| `welcome` | `screen{w,h}`, `android`, `features` | El sıkışma onayı + yetenekler |
| `error` | `code`, `message` | örn. `unauthorized`, `no_focus` |
| `status` | `accessibility`, `focus` | Servis/odak durumu değişti |
| `pong` | — | Canlılık yanıtı |

### Örnek Oturum
```jsonc
→ {"type":"hello","token":"a1b2…","client":"PWA","v":1}
← {"type":"welcome","screen":{"w":1920,"h":1080},"android":13,
   "features":{"imeEnter":true,"scroll":true}}
→ {"type":"move","dx":24,"dy":-8}        // imleç kayar
→ {"type":"tap"}                          // imlecin yerine dokunur
→ {"type":"scroll","dx":0,"dy":120}       // iki parmak aşağı
→ {"type":"text","value":"interstellar"}  // arama kutusuna komple yazı
→ {"type":"submit"}                       // Ara'ya bas
```

### Notlar
- **Move birleştirme:** Kumanda her kareye düşen delta'ları toplayıp gönderir → ağ boğulmaz.
- **Koordinat otoritesi:** Delta'lar kumandanın CSS px'i; sunucu hassasiyet × ivme eğrisiyle
  ölçekler, imleci ekran sınırına kıstırır.
- **Yetenek bildirimi:** `welcome.features` ile kumanda cihazda neyin desteklendiğini öğrenir
  (örn. Android 10'da `imeEnter:false` → "Ara" düğmesini gizle/uyar).
- **Sürüm alanı (`v`):** ileri uyumluluk.

---

## 7. Eşleştirme ve Keşif

- **Token:** Sunucu ilk açılışta rastgele güçlü bir token üretir, cihazda saklar.
- **QR içeriği:** `http://<tv-ip>:<port>/?t=<token>` — keşif (adres) + güvenlik (token) tek karede.
- **Doğrulama:** WS upgrade isteğinde `t` parametresi token ile karşılaştırılır; eşleşmezse
  bağlantı reddedilir.
- **Token yenileme:** Kurulum ekranından yenilenebilir; eski eşleşmeler geçersiz olur.
- **Kalıcılık:** PWA token + adresi `localStorage`'da tutar; sonraki açılışlarda otomatik bağlanır.
- **IP değişimi:** IP değişirse QR yeniden okutulur (v1). (Opsiyonel gelecek: mDNS/NSD ile keşif.)

---

## 8. Güvenlik ve Sınırlar

### Güvenlik modeli
- **Yalnızca LAN:** Sunucu yerel ağa bağlanır; internete açılmaz. Token korumalı.
- **Tehdit:** Aynı WiFi'daki bir saldırganın token'a ihtiyacı var (QR'ı görmedikçe bilemez).
- **Dürüst uyarı:** `http` LAN'da düz metindir → aynı ağda paket dinleyen biri teorik olarak
  token/komutları görebilir. Ev kullanımı için kabul edilebilir. **Opsiyonel gelecek:** güvenilir
  sertifikayla tam HTTPS + kurulabilir PWA.

### Bilinen sınırlar
- **Kapalı TV açılamaz:** WiFi üzerinden uyandırma yolu yok. Uyutmak/var.
- **Güvenli ekran kilidi:** Deseni/PIN'i imleçle açmak keyguard tarafından engellenebilir
  → **cihazda doğrulanacak.**
- **Android < 11:** `ACTION_IME_ENTER` yok → "Ara" için ekrandaki arama düğmesine basılır.
- **Erişilebilirlik yazısı:** Alanı komple set eder; arama/URL kutularında en iyi çalışır; bazı
  özel alanlar düzenlenebilir düğüm sunmayabilir.
- **Ekran yansıtma yok:** Kör touchpad; TV'ye bakılarak kullanılır.

---

## 9. Teknoloji Yığını

### Sunucu (TV — Android)
- **Dil:** Kotlin
- **UI:** Jetpack Compose, Material 3
- **Gömülü sunucu:** Ktor (CIO engine) — statik içerik + WebSocket
- **Serileştirme:** kotlinx.serialization
- **QR:** ZXing
- **minSdk 26 (Android 8)**, tam özellikler (IME enter) 30+; targetSdk güncel
- **Build:** Gradle (Kotlin DSL)

### Kumanda (PWA)
- **Svelte + TypeScript + Vite** (küçük paket, modern, hızlı)
- Scoped CSS (özgün, sade görsel)
- Web App Manifest (ana ekrana ekle); service worker yalnızca HTTPS'te anlamlı (gelecek)

### Build akışı
PWA derlenir (`vite build`) → çıktı `server-android/app/src/main/assets/web/`'e kopyalanır →
APK içine gömülür. Tek bir Gradle görevi / npm script bu kopyalamayı otomatikler.

---

## 10. Proje Yapısı

```
remotedroid/
├── server-android/                 # TV uygulaması (Kotlin/Compose)
│   ├── app/
│   │   └── src/main/
│   │       ├── java/.../accessibility/   # A11yService, cursor overlay, gesture mapper
│   │       ├── java/.../server/          # Ktor sunucu, WS handler, protokol modelleri
│   │       ├── java/.../service/         # Foreground service
│   │       ├── java/.../ui/              # Compose kurulum UI, QR üretimi
│   │       └── assets/web/               # PWA build çıktısı (gömülür)
│   └── build.gradle.kts
├── client-pwa/                     # Svelte/TS/Vite
│   ├── src/
│   │   ├── lib/transport/          # WebSocket istemci + protokol tipleri
│   │   ├── lib/touchpad/           # touchpad bileşeni + gesture algılama
│   │   ├── lib/ui/                 # ses, klavye paneli, nav, ayarlar
│   │   └── App.svelte
│   ├── vite.config.ts
│   └── manifest.webmanifest
├── docs/
├── LICENSE                         # Apache-2.0
└── README.md                       # TR + EN
```

---

## 11. Test Stratejisi

Saf mantık için **TDD** (testi önce yaz):
- **Sunucu (JVM unit):** protokol ayrıştırma (kotlinx.serialization round-trip), gesture matematiği
  (delta → koordinat, sınıra kıstırma, ivme eğrisi), token doğrulama.
- **Kumanda (Vitest):** gesture algılama eşikleri (tap / drag / scroll ayrımı), transport yeniden
  bağlanma mantığı, delta birleştirme.
- **Cihaz üstü manuel kontrol listesi:** Erişilebilirlik akışları (gerçek dokunma enjeksiyonu)
  birim test edilemez → gerçek TV'de adım adım doğrulanır.

---

## 12. Yol Haritası (Kilometre Taşları)

- **M1 — İskelet & ispat:** Android app + Erişilebilirlik Servisi; imleç overlay'i kaydırma +
  sabit koordinata `dispatchGesture` tap. *Cihazda dokunma enjeksiyonu çalışıyor mu?*
- **M2 — Uçtan uca touchpad:** Ktor sunucu + WS + minimal PWA touchpad → `move`/`tap` çalışır.
- **M3 — Tam hareketler:** `drag`, `scroll`, `longpress`, ses, gezinme düğmeleri.
- **M4 — Yazı:** `text` (set_text) + `submit` (ime_enter) + klavye paneli.
- **M5 — Eşleştirme:** QR + token + `localStorage` kalıcılık + otomatik yeniden bağlanma.
- **M6 — Kalıcılık & kurulum:** Foreground service, otomatik başlatma, pil optimizasyonu uyarısı,
  kurulum UI.
- **M7 — Cila:** Görsel inceltme (`frontend-design`), PWA manifest / ana ekrana ekle.
- **M8 — Yayın:** Dökümantasyon, LICENSE, README (TR+EN), ekran görüntüleri, sürüm.

---

## 13. Cihazda Doğrulanacaklar (Açık Sorular)

Gerçek TV'de teyit edilecek:
1. **Android sürümü** (IME enter / API seviyesi için).
2. **Ekran çözünürlüğü** (imleç/koordinat ölçekleme).
3. `dispatchGesture` **ekran kilidinde** çalışıyor mu (deseni açabiliyor muyuz)?
4. **Overlay** tüm uygulamaların üstünde görünüyor mu?
5. **Pil optimizasyonu** servisi öldürüyor mu?
6. Uygulamaların **yan yüklenmesi** (sideload) ve gerekli izinlerin verilebilmesi.

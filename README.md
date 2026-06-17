<div align="center">

# RemoteDroid

**Kumandası olmayan, düz Android (Android TV değil) çalışan TV'ler için touchpad-first uzaktan kumanda.**
*A touchpad-first remote for plain-Android TVs — no root, no PC, no extra remote.*

</div>

---

## Bu nedir?

Bazı TV'lerin içinde (örn. **Thomson Go Plus 32UE5M45**) Android TV değil, **dokunmatik bir Android tableti** gibi davranan gerçek Android vardır. Kumanda gelmez; her seferinde TV'nin yanına gidip dokunmak gerekir.

**RemoteDroid** bunu çözer: TV'ye kurulan bir uygulama, telefonunuzun tarayıcısından açtığınız bir web kumandayı (PWA) sunar. Parmağınızla TV'de bir **imleç** gezdirir (laptop trackpad'i gibi), ses açar/kısar, klavyeyle yazı gönderirsiniz — hepsi yerel ağ üzerinden, **root gerektirmeden**, Android **Erişilebilirlik Servisi** ile.

## Nasıl çalışır?

```
 Telefon (tarayıcı / PWA)  ──ws://tv-ip:8080──►  TV (Android + RemoteDroid)
   touchpad · ses · klavye      JSON komutları      Erişilebilirlik Servisi
                                                     dispatchGesture / ACTION_SET_TEXT
```

1. TV'deki uygulamayı açın, **Sunucuyu başlat** + **Erişilebilirlik servisini** etkinleştirin.
2. Ekrandaki **QR'ı telefon kamerasıyla okutun** → kumanda tarayıcıda açılır (token'la otomatik bağlanır).
3. Touchpad'le imleci sürün, tıklayın; ses/gezinme düğmeleri; klavye kutusuna yazıp **tek seferde** gönderin.

## Özellikler

- **Touchpad-first:** göreli imleç (TV'de overlay), tek tık, iki parmak kaydırma.
- **Ses:** aç / kıs / sessize.
- **Klavye:** kutuya yaz → tüm metin tek seferde TV'deki alana (Enter/Ara dahil, Android 11+).
- **Gezinme:** Home · Geri · Son uygulamalar.
- **QR ile eşleştirme:** adres + token tek karede; sadece eşleşmiş cihaz kontrol eder.
- **Root yok, ek cihaz yok.**

## Proje yapısı

| Dizin | İçerik |
|---|---|
| `client-pwa/` | Web kumanda — Svelte + TypeScript + Vite (TV tarafından sunulur) |
| `server-android/` | TV uygulaması — Kotlin, Compose, Ktor (CIO), Erişilebilirlik Servisi |
| `docs/superpowers/` | Tasarım dokümanı (spec) ve uygulama planları |

## Derleme & çalıştırma

**PWA (kumanda):**
```bash
cd client-pwa
npm install
npm run dev      # geliştirme
npm test         # birim testler
npm run build    # dist/ → Android assets'e gömülür
```

**Android sunucu (TV):**
```bash
cd server-android
# JDK 21 ve Android SDK gerekir; gradlew JDK 21'i kullanır
./gradlew :app:testDebugUnitTest   # birim testler
./gradlew :app:assembleDebug       # APK (PWA otomatik gömülür)
# Çıktı: app/build/outputs/apk/debug/app-debug.apk → TV'ye sideload
```

## Sınırlar

- Kapalı TV'yi **açamaz** (WiFi üzerinden uyandırma yolu yok); uyutabilir.
- Yalnızca aynı **yerel ağ**; `http` üzerinden (token korumalı). İnternet erişimi yok.
- **Ekran yansıtma yok** — TV'ye bakarak kullanılır (kör touchpad).
- Erişilebilirlik metni en iyi arama/URL kutularında çalışır.

## Lisans

[Apache-2.0](./LICENSE).

---

<div align="center">
<sub>Tasarım, plan ve testler <code>docs/superpowers/</code> altında.</sub>
</div>

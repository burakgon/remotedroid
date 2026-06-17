# RemoteDroid — Phase B: Android Sunucu (TV) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** TV'de çalışan, PWA kumandayı sunan ve gelen komutları Erişilebilirlik Servisi üzerinden sisteme uygulayan (imleç + dokunma + ses + metin + gezinme) native Android uygulamasını kurmak; QR ile eşleştirme.

**Architecture:** Tek Android uygulaması. Ktor (CIO) gömülü sunucu `assets/web`'teki PWA'yı statik sunar ve `/ws` üzerinden token'lı WebSocket açar. Gelen `ClientMessage`'lar bir `CommandExecutor` arayüzüne gider; bunu `RemoteAccessibilityService` uygular (`dispatchGesture`, global eylemler, `AudioManager`, `ACTION_SET_TEXT`/`ACTION_IME_ENTER`). Saf mantık (protokol, imleç matematiği, token doğrulama, asset MIME) JVM'de birim test edilir; enjeksiyon gerçek cihazda doğrulanır.

**Tech Stack:** Kotlin 2.0.21, AGP 8.7.2, Gradle 8.9 (wrapper), JDK 21, compileSdk 35 / minSdk 26 / targetSdk 35, Jetpack Compose (BOM 2024.09.x), Ktor 3.x (CIO), kotlinx.serialization 1.7.x, ZXing core (QR).

## Global Constraints

- `JAVA_HOME` = `/opt/homebrew/opt/openjdk@21`; `gradle.properties`'te `org.gradle.java.home` aynı yola sabitlenir (PATH'teki JDK 26 Gradle'ı kırar).
- Protokol `v=1`, mesaj şekilleri TS `messages.ts` + spec bölüm 6 ile **birebir** (JSON `type` discriminator).
- Erişilebilirlik servisi izinleri ve overlay kullanıcı tarafından elle etkinleştirilir; uygulama yönlendirir.
- minSdk 26; `ACTION_IME_ENTER` yalnızca API 30+ (cihaz Android 11+ → mevcut).
- DRY / YAGNI / TDD (saf mantıkta) / sık commit.

---

## Toolchain Önkoşulu (Task 0)

- [ ] **android-35 + build-tools;35.0.0 kur (lisans kabul):**
```bash
yes | "$HOME/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager" \
  --sdk_root="$HOME/Library/Android/sdk" "platforms;android-35" "build-tools;35.0.0"
```
- [ ] **Gradle bootstrap (wrapper üretmek için):** `brew install gradle`
- [ ] **Wrapper'ı 8.9'a sabitle:** proje oluşturulduktan sonra
  `JAVA_HOME=/opt/homebrew/opt/openjdk@21 gradle wrapper --gradle-version 8.9 --distribution-type bin`

---

## Dosya Yapısı

```
server-android/
├── settings.gradle.kts
├── build.gradle.kts                  # köke AGP/Kotlin plugin sürümleri
├── gradle.properties                 # org.gradle.java.home, AndroidX, JVM args
├── local.properties                  # sdk.dir (git-ignored)
├── gradle/libs.versions.toml         # sürüm kataloğu
├── gradlew / gradle/wrapper/...       # wrapper
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── assets/web/.gitkeep    # PWA dist buraya kopyalanır
        │   ├── res/xml/accessibility_service_config.xml
        │   ├── res/values/strings.xml
        │   └── java/com/remotedroid/
        │       ├── protocol/Messages.kt        # sealed ClientMessage/ServerMessage (+Json)
        │       ├── input/Cursor.kt             # saf imleç matematiği
        │       ├── input/CommandExecutor.kt    # arayüz
        │       ├── server/AssetContent.kt      # path→MIME (saf)
        │       ├── server/Auth.kt              # token doğrulama (saf)
        │       ├── server/RemoteServer.kt      # Ktor CIO: static + /ws
        │       ├── service/RemoteAccessibilityService.kt  # executor impl + overlay
        │       ├── service/ServerService.kt    # foreground service
        │       ├── store/Settings.kt           # token + port saklama
        │       └── ui/MainActivity.kt + SetupScreen.kt + Qr.kt
        └── test/java/com/remotedroid/          # JVM birim testleri
            ├── MessagesTest.kt
            ├── CursorTest.kt
            ├── AuthTest.kt
            └── AssetContentTest.kt
```

---

### Task 1: Gradle/Android iskeleti (boş uygulama derlenir + JVM test çalışır)

**Files:** settings/build/gradle.properties/libs.versions.toml/app build + Manifest + MainActivity (boş Compose) + bir trivial JVM test.

**Interfaces:** Produces — `./gradlew :app:assembleDebug` ve `./gradlew :app:testDebugUnitTest` çalışır.

- [ ] **Step 1:** `gradle/libs.versions.toml` sürüm kataloğu (agp 8.7.2, kotlin 2.0.21, ktor 3.0.3, serialization 1.7.3, composeBom 2024.09.03, zxing-core 3.5.3).
- [ ] **Step 2:** kök `build.gradle.kts` (plugins apply false), `settings.gradle.kts` (repos), `gradle.properties` (`org.gradle.java.home=/opt/homebrew/opt/openjdk@21`, `android.useAndroidX=true`, `org.gradle.jvmargs=-Xmx2g`).
- [ ] **Step 3:** `app/build.gradle.kts` (android plugin, kotlin, kotlin.plugin.compose, serialization; compileSdk 35, minSdk 26, targetSdk 35; compose buildFeature; deps: compose bom+ui+material3+activity-compose, ktor-server-core/cio/websockets, serialization-json, zxing core).
- [ ] **Step 4:** `AndroidManifest.xml` (application + MainActivity launcher), `MainActivity.kt` (Compose `setContent { Text("RemoteDroid") }`), `res/values/strings.xml`.
- [ ] **Step 5:** trivial JVM test `class SmokeTest { @Test fun ok() = assertEquals(2, 1+1) }`.
- [ ] **Step 6:** wrapper üret (Task 0), `./gradlew :app:assembleDebug :app:testDebugUnitTest` → BUILD SUCCESSFUL.
- [ ] **Step 7:** Commit `feat(android): gradle skeleton with compose + ktor deps`.

---

### Task 2: Protokol modelleri (Kotlin, kotlinx.serialization, TDD)

**Files:** `protocol/Messages.kt`, test `MessagesTest.kt`.

**Interfaces:** Produces —
- `sealed interface ClientMessage` alt tipleri: `Hello(token,client,v)`, `Move(dx:Double,dy:Double)`, `Tap`, `LongPress`, `DragStart`, `DragMove(dx,dy)`, `DragEnd`, `Scroll(dx,dy)`, `Volume(dir:String)`, `Mute`, `Text(value:String)`, `Submit`, `Backspace`, `Clear`, `Global(action:String)`, `Ping` — her biri `@SerialName("...")` ile TS string'ine eşli.
- `sealed interface ServerMessage`: `Welcome(screen:Screen,android:Int,features:Features)`, `ErrorMsg(code,message?)`, `Status(...)`, `Pong`.
- `val RdJson = Json { classDiscriminator = "type"; ignoreUnknownKeys = true; encodeDefaults = true }`
- `fun decodeClient(s:String): ClientMessage`, `fun encodeServer(m:ServerMessage): String`.

- [ ] **Step 1: Başarısız test**
```kotlin
class MessagesTest {
  @Test fun decodesMove() {
    val m = decodeClient("""{"type":"move","dx":12,"dy":-3}""")
    assertEquals(ClientMessage.Move(12.0, -3.0), m)
  }
  @Test fun decodesTapDataObject() {
    assertEquals(ClientMessage.Tap, decodeClient("""{"type":"tap"}"""))
  }
  @Test fun encodesWelcomeWithTypeDiscriminator() {
    val s = encodeServer(ServerMessage.Welcome(Screen(1920,1080), 13, Features(imeEnter=true, scroll=true)))
    assertTrue(s.contains(""""type":"welcome""""))
    assertTrue(s.contains(""""android":13"""))
  }
  @Test fun ignoresUnknownKeys() {
    assertEquals(ClientMessage.Ping, decodeClient("""{"type":"ping","extra":1}"""))
  }
}
```
- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest --tests "*MessagesTest"` → FAIL (derlenmez).
- [ ] **Step 3:** `Messages.kt` implementasyonu (sealed interface + data class/data object + Json). No-field mesajlar `data object`.
- [ ] **Step 4:** test → PASS.
- [ ] **Step 5:** Commit `feat(android): protocol models mirroring the PWA contract`.

---

### Task 3: İmleç matematiği (saf Kotlin, TDD)

**Files:** `input/Cursor.kt`, test `CursorTest.kt`.

**Interfaces:** Produces —
- `class Cursor(var x: Float, var y: Float, val width: Int, val height: Int, var sensitivity: Float = 1.5f)`
  - `fun move(dx: Float, dy: Float)` → `x,y`'yi `sensitivity` ile güncelle, `[0,width-1]×[0,height-1]`'e kıstır.
  - `fun center()` → ekran ortasına al.

- [ ] **Step 1: Başarısız test**
```kotlin
class CursorTest {
  @Test fun movesScaledBySensitivity() {
    val c = Cursor(100f,100f,1920,1080, sensitivity=2f); c.move(10f,-5f)
    assertEquals(120f, c.x); assertEquals(90f, c.y)
  }
  @Test fun clampsToScreenBounds() {
    val c = Cursor(0f,0f,1920,1080, sensitivity=1f); c.move(-50f,-50f)
    assertEquals(0f, c.x); assertEquals(0f, c.y)
    c.move(99999f,99999f); assertEquals(1919f, c.x); assertEquals(1079f, c.y)
  }
}
```
- [ ] **Step 2:** test → FAIL. **Step 3:** `Cursor.kt`. **Step 4:** test → PASS. **Step 5:** Commit `feat(android): pure cursor math with clamping`.

---

### Task 4: Sunucu yardımcıları — Auth + AssetContent (saf, TDD)

**Files:** `server/Auth.kt`, `server/AssetContent.kt`, tests `AuthTest.kt`, `AssetContentTest.kt`.

**Interfaces:** Produces —
- `object Auth { fun isValid(expected: String, provided: String?): Boolean }` (sabit-zamanlı eşitlik; null/boş reddet).
- `object AssetContent { fun mimeFor(path: String): String; fun normalize(path: String): String }` (`/`→`index.html`, `..` temizle; `.js→text/javascript`, `.css→text/css`, `.html→text/html`, `.webmanifest→application/manifest+json`, vs.).

- [ ] **Step 1–5:** Auth (eşit/eşitsiz/null) ve AssetContent (mime + normalize + path traversal) testleri → FAIL → impl → PASS → Commit `feat(android): pure auth + asset mime helpers`.

---

### Task 5: CommandExecutor arayüzü + RemoteServer (Ktor CIO)

**Files:** `input/CommandExecutor.kt`, `server/RemoteServer.kt`.

**Interfaces:**
- Consumes: `decodeClient`, `encodeServer`, `Auth`, `AssetContent`, `CommandExecutor`.
- Produces:
  - `interface CommandExecutor { fun execute(msg: ClientMessage); fun welcome(): ServerMessage.Welcome }`
  - `class RemoteServer(port:Int, token:String, assets:AssetManager, exec:CommandExecutor) { fun start(); fun stop() }` — Ktor CIO; `GET /{path...}` → AssetManager'dan `assets/web` servis; `webSocket("/ws")` → `?t` doğrula (Auth), açılışta `welcome` gönder, her text frame → `decodeClient` → `exec.execute`.

- [ ] **Step 1:** `CommandExecutor.kt` arayüzü.
- [ ] **Step 2:** `RemoteServer.kt` (Ktor CIO; static asset handler + ws). *(Not: Ktor route mantığı cihaz/emülatör gerektirir; burada derleme + sahte executor ile elle doğrulama. Token doğrulama zaten Task 4'te test edildi.)*
- [ ] **Step 3:** `./gradlew :app:assembleDebug` → derlenir. Commit `feat(android): ktor server serving PWA assets + websocket command channel`.

---

### Task 6: RemoteAccessibilityService (executor impl + imleç overlay)

**Files:** `service/RemoteAccessibilityService.kt`, `res/xml/accessibility_service_config.xml`, Manifest'e servis kaydı.

**Interfaces:** Consumes — `CommandExecutor`, `Cursor`, `ClientMessage`. Produces — çalışan enjeksiyon.

- [ ] **Step 1:** accessibility config xml (`canPerformGestures=true`, `canRetrieveWindowContent=true`, flags).
- [ ] **Step 2:** Servis:
  - `onServiceConnected`: ekran boyutunu al, `Cursor` kur, overlay imleç ekle (`WindowManager`, `TYPE_ACCESSIBILITY_OVERLAY`, `FLAG_NOT_TOUCHABLE or FLAG_NOT_FOCUSABLE`).
  - `execute(msg)`: `Move`→imleç güncelle+overlay taşı; `Tap`→`dispatchGesture` click; `Scroll`→iki paralel stroke; `Volume`→`AudioManager.adjustStreamVolume(STREAM_MUSIC,...,FLAG_SHOW_UI)`; `Mute`; `Text`→`findFocus(FOCUS_INPUT)`+`ACTION_SET_TEXT`(+`ACTION_SET_SELECTION`); `Submit`→`ACTION_IME_ENTER`; `Backspace`/`Clear`→SET_TEXT; `Global`→`performGlobalAction`.
  - `welcome()`: ekran w/h, `Build.VERSION.SDK_INT`, `features(imeEnter = SDK_INT>=30, scroll=true)`.
- [ ] **Step 3:** `./gradlew :app:assembleDebug` → derlenir. Commit `feat(android): accessibility service injecting gestures/volume/text + cursor overlay`.

---

### Task 7: ServerService (foreground) + Settings (token/port) + Manifest izinleri

**Files:** `service/ServerService.kt`, `store/Settings.kt`, Manifest (FOREGROUND_SERVICE, INTERNET, POST_NOTIFICATIONS).

**Interfaces:** Produces — `ServerService` foreground bildirimiyle `RemoteServer`'ı tutar; servis ile accessibility executor'ı köprüler (singleton/bound). `Settings`: token üret/sakla (`SharedPreferences`), port.

- [ ] **Step 1–3:** Settings (token üretimi `SecureRandom`), ServerService (foreground notification, start/stop), Manifest izinleri → derlenir → Commit `feat(android): foreground service + token store`.

---

### Task 8: Kurulum UI (Compose) + QR (ZXing)

**Files:** `ui/SetupScreen.kt`, `ui/Qr.kt`, `MainActivity.kt` güncelle.

**Interfaces:** Produces — durum (erişilebilirlik açık mı, IP, port), QR (`http://<ip>:<port>/?t=<token>`), token yenile, başlat/durdur, erişilebilirlik ayarlarına yönlendir.

- [ ] **Step 1:** `Qr.kt` — ZXing ile `Bitmap` üreten saf-ish fonksiyon (`fun qrBitmap(text:String, size:Int): Bitmap`).
- [ ] **Step 2:** `SetupScreen.kt` Compose — IP tespiti, QR göster, butonlar.
- [ ] **Step 3:** derlenir → Commit `feat(android): setup screen with pairing QR`.

---

### Task 9: PWA bundling Gradle görevi + APK

**Files:** `app/build.gradle.kts` (copy task).

**Interfaces:** Produces — `preBuild` öncesi `../client-pwa/dist` → `src/main/assets/web` kopyalanır.

- [ ] **Step 1:** Gradle `Copy` task (`from("../../client-pwa/dist") into("src/main/assets/web")`), `preBuild.dependsOn`.
- [ ] **Step 2:** `cd client-pwa && npm run build` sonra `./gradlew :app:assembleDebug` → APK içinde PWA. Commit `feat(android): bundle PWA build into app assets`.

---

### Task 10: Cihaz üstü manuel doğrulama (gerçek TV)

Spec bölüm 13 kontrol listesi — birim test edilemez, kullanıcı TV'de:
- [ ] APK'yı TV'ye sideload + erişilebilirlik servisini etkinleştir.
- [ ] Telefonla QR okut → PWA bağlanır (durum "Bağlı").
- [ ] İmleç hareket/tık/scroll; ses; metin gönder + Enter/Ara; Home/Back/Recents.
- [ ] Overlay tüm uygulamalarda görünür mü; pil optimizasyonu servisi öldürüyor mu; kilit ekranı davranışı.

**Açık riskler:** AGP/Gradle/JDK uyumu (gerekirse sürüm ayarı); accessibility enjeksiyonu yalnızca cihazda doğrulanır; bazı OEM kısıtları.

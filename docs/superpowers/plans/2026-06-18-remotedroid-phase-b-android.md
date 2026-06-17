# RemoteDroid — Phase B: Android Server (TV) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Build a native Android app that runs on the TV, serves the PWA remote, and applies incoming commands to the system through the Accessibility Service (cursor + tap + volume + text + navigation); pairing via QR.

**Architecture:** A single Android app. A Ktor (CIO) embedded server statically serves the PWA from `assets/web` and opens a token-authenticated WebSocket on `/ws`. Incoming `ClientMessage`s are routed to a `CommandExecutor` interface, which is implemented by `RemoteAccessibilityService` (`dispatchGesture`, global actions, `AudioManager`, `ACTION_SET_TEXT`/`ACTION_IME_ENTER`). Pure logic (protocol, cursor math, token validation, asset MIME) is unit tested on the JVM; injection is verified on a real device.

**Tech Stack:** Kotlin 2.0.21, AGP 8.7.2, Gradle 8.9 (wrapper), JDK 21, compileSdk 35 / minSdk 26 / targetSdk 35, Jetpack Compose (BOM 2024.09.x), Ktor 3.x (CIO), kotlinx.serialization 1.7.x, ZXing core (QR).

## Global Constraints

- `JAVA_HOME` = `/opt/homebrew/opt/openjdk@21`; `org.gradle.java.home` in `gradle.properties` is pinned to the same path (JDK 26 on the PATH breaks Gradle).
- Protocol `v=1`, message shapes match the TS `messages.ts` + spec section 6 **exactly** (JSON `type` discriminator).
- Accessibility service permissions and the overlay are enabled manually by the user; the app guides them there.
- minSdk 26; `ACTION_IME_ENTER` is only available on API 30+ (device on Android 11+ → available).
- DRY / YAGNI / TDD (for pure logic) / frequent commits.

---

## Toolchain Prerequisite (Task 0)

- [ ] **Install android-35 + build-tools;35.0.0 (accept licenses):**
```bash
yes | "$HOME/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager" \
  --sdk_root="$HOME/Library/Android/sdk" "platforms;android-35" "build-tools;35.0.0"
```
- [ ] **Gradle bootstrap (to generate the wrapper):** `brew install gradle`
- [ ] **Pin the wrapper to 8.9:** after the project is created,
  `JAVA_HOME=/opt/homebrew/opt/openjdk@21 gradle wrapper --gradle-version 8.9 --distribution-type bin`

---

## File Structure

```
server-android/
├── settings.gradle.kts
├── build.gradle.kts                  # AGP/Kotlin plugin versions at the root
├── gradle.properties                 # org.gradle.java.home, AndroidX, JVM args
├── local.properties                  # sdk.dir (git-ignored)
├── gradle/libs.versions.toml         # version catalog
├── gradlew / gradle/wrapper/...       # wrapper
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── assets/web/.gitkeep    # PWA dist is copied here
        │   ├── res/xml/accessibility_service_config.xml
        │   ├── res/values/strings.xml
        │   └── java/com/remotedroid/
        │       ├── protocol/Messages.kt        # sealed ClientMessage/ServerMessage (+Json)
        │       ├── input/Cursor.kt             # pure cursor math
        │       ├── input/CommandExecutor.kt    # interface
        │       ├── server/AssetContent.kt      # path→MIME (pure)
        │       ├── server/Auth.kt              # token validation (pure)
        │       ├── server/RemoteServer.kt      # Ktor CIO: static + /ws
        │       ├── service/RemoteAccessibilityService.kt  # executor impl + overlay
        │       ├── service/ServerService.kt    # foreground service
        │       ├── store/Settings.kt           # token + port storage
        │       └── ui/MainActivity.kt + SetupScreen.kt + Qr.kt
        └── test/java/com/remotedroid/          # JVM unit tests
            ├── MessagesTest.kt
            ├── CursorTest.kt
            ├── AuthTest.kt
            └── AssetContentTest.kt
```

---

### Task 1: Gradle/Android skeleton (empty app compiles + JVM tests run)

**Files:** settings/build/gradle.properties/libs.versions.toml/app build + Manifest + MainActivity (empty Compose) + one trivial JVM test.

**Interfaces:** Produces — `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` run.

- [ ] **Step 1:** `gradle/libs.versions.toml` version catalog (agp 8.7.2, kotlin 2.0.21, ktor 3.0.3, serialization 1.7.3, composeBom 2024.09.03, zxing-core 3.5.3).
- [ ] **Step 2:** root `build.gradle.kts` (plugins apply false), `settings.gradle.kts` (repos), `gradle.properties` (`org.gradle.java.home=/opt/homebrew/opt/openjdk@21`, `android.useAndroidX=true`, `org.gradle.jvmargs=-Xmx2g`).
- [ ] **Step 3:** `app/build.gradle.kts` (android plugin, kotlin, kotlin.plugin.compose, serialization; compileSdk 35, minSdk 26, targetSdk 35; compose buildFeature; deps: compose bom+ui+material3+activity-compose, ktor-server-core/cio/websockets, serialization-json, zxing core).
- [ ] **Step 4:** `AndroidManifest.xml` (application + MainActivity launcher), `MainActivity.kt` (Compose `setContent { Text("RemoteDroid") }`), `res/values/strings.xml`.
- [ ] **Step 5:** trivial JVM test `class SmokeTest { @Test fun ok() = assertEquals(2, 1+1) }`.
- [ ] **Step 6:** generate the wrapper (Task 0), `./gradlew :app:assembleDebug :app:testDebugUnitTest` → BUILD SUCCESSFUL.
- [ ] **Step 7:** Commit `feat(android): gradle skeleton with compose + ktor deps`.

---

### Task 2: Protocol models (Kotlin, kotlinx.serialization, TDD)

**Files:** `protocol/Messages.kt`, test `MessagesTest.kt`.

**Interfaces:** Produces —
- `sealed interface ClientMessage` subtypes: `Hello(token,client,v)`, `Move(dx:Double,dy:Double)`, `Tap`, `LongPress`, `DragStart`, `DragMove(dx,dy)`, `DragEnd`, `Scroll(dx,dy)`, `Volume(dir:String)`, `Mute`, `Text(value:String)`, `Submit`, `Backspace`, `Clear`, `Global(action:String)`, `Ping` — each mapped to its TS string via `@SerialName("...")`.
- `sealed interface ServerMessage`: `Welcome(screen:Screen,android:Int,features:Features)`, `ErrorMsg(code,message?)`, `Status(...)`, `Pong`.
- `val RdJson = Json { classDiscriminator = "type"; ignoreUnknownKeys = true; encodeDefaults = true }`
- `fun decodeClient(s:String): ClientMessage`, `fun encodeServer(m:ServerMessage): String`.

- [ ] **Step 1: Failing test**
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
- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest --tests "*MessagesTest"` → FAIL (does not compile).
- [ ] **Step 3:** `Messages.kt` implementation (sealed interface + data class/data object + Json). No-field messages are `data object`.
- [ ] **Step 4:** test → PASS.
- [ ] **Step 5:** Commit `feat(android): protocol models mirroring the PWA contract`.

---

### Task 3: Cursor math (pure Kotlin, TDD)

**Files:** `input/Cursor.kt`, test `CursorTest.kt`.

**Interfaces:** Produces —
- `class Cursor(var x: Float, var y: Float, val width: Int, val height: Int, var sensitivity: Float = 1.5f)`
  - `fun move(dx: Float, dy: Float)` → update `x,y` by `sensitivity`, clamp to `[0,width-1]×[0,height-1]`.
  - `fun center()` → move to the center of the screen.

- [ ] **Step 1: Failing test**
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

### Task 4: Server helpers — Auth + AssetContent (pure, TDD)

**Files:** `server/Auth.kt`, `server/AssetContent.kt`, tests `AuthTest.kt`, `AssetContentTest.kt`.

**Interfaces:** Produces —
- `object Auth { fun isValid(expected: String, provided: String?): Boolean }` (constant-time equality; reject null/empty).
- `object AssetContent { fun mimeFor(path: String): String; fun normalize(path: String): String }` (`/`→`index.html`, strip `..`; `.js→text/javascript`, `.css→text/css`, `.html→text/html`, `.webmanifest→application/manifest+json`, etc.).

- [ ] **Step 1–5:** Auth (equal/unequal/null) and AssetContent (mime + normalize + path traversal) tests → FAIL → impl → PASS → Commit `feat(android): pure auth + asset mime helpers`.

---

### Task 5: CommandExecutor interface + RemoteServer (Ktor CIO)

**Files:** `input/CommandExecutor.kt`, `server/RemoteServer.kt`.

**Interfaces:**
- Consumes: `decodeClient`, `encodeServer`, `Auth`, `AssetContent`, `CommandExecutor`.
- Produces:
  - `interface CommandExecutor { fun execute(msg: ClientMessage); fun welcome(): ServerMessage.Welcome }`
  - `class RemoteServer(port:Int, token:String, assets:AssetManager, exec:CommandExecutor) { fun start(); fun stop() }` — Ktor CIO; `GET /{path...}` → serve `assets/web` from the AssetManager; `webSocket("/ws")` → validate `?t` (Auth), send `welcome` on open, every text frame → `decodeClient` → `exec.execute`.

- [ ] **Step 1:** `CommandExecutor.kt` interface.
- [ ] **Step 2:** `RemoteServer.kt` (Ktor CIO; static asset handler + ws). *(Note: the Ktor route logic requires a device/emulator; here we verify the build + a fake executor manually. Token validation is already tested in Task 4.)*
- [ ] **Step 3:** `./gradlew :app:assembleDebug` → compiles. Commit `feat(android): ktor server serving PWA assets + websocket command channel`.

---

### Task 6: RemoteAccessibilityService (executor impl + cursor overlay)

**Files:** `service/RemoteAccessibilityService.kt`, `res/xml/accessibility_service_config.xml`, register the service in the Manifest.

**Interfaces:** Consumes — `CommandExecutor`, `Cursor`, `ClientMessage`. Produces — working injection.

- [ ] **Step 1:** accessibility config xml (`canPerformGestures=true`, `canRetrieveWindowContent=true`, flags).
- [ ] **Step 2:** Service:
  - `onServiceConnected`: read the screen size, set up `Cursor`, add the overlay cursor (`WindowManager`, `TYPE_ACCESSIBILITY_OVERLAY`, `FLAG_NOT_TOUCHABLE or FLAG_NOT_FOCUSABLE`).
  - `execute(msg)`: `Move`→update cursor + move overlay; `Tap`→`dispatchGesture` click; `Scroll`→two parallel strokes; `Volume`→`AudioManager.adjustStreamVolume(STREAM_MUSIC,...,FLAG_SHOW_UI)`; `Mute`; `Text`→`findFocus(FOCUS_INPUT)`+`ACTION_SET_TEXT`(+`ACTION_SET_SELECTION`); `Submit`→`ACTION_IME_ENTER`; `Backspace`/`Clear`→SET_TEXT; `Global`→`performGlobalAction`.
  - `welcome()`: screen w/h, `Build.VERSION.SDK_INT`, `features(imeEnter = SDK_INT>=30, scroll=true)`.
- [ ] **Step 3:** `./gradlew :app:assembleDebug` → compiles. Commit `feat(android): accessibility service injecting gestures/volume/text + cursor overlay`.

---

### Task 7: ServerService (foreground) + Settings (token/port) + Manifest permissions

**Files:** `service/ServerService.kt`, `store/Settings.kt`, Manifest (FOREGROUND_SERVICE, INTERNET, POST_NOTIFICATIONS).

**Interfaces:** Produces — `ServerService` holds `RemoteServer` alive with a foreground notification; bridges the service and the accessibility executor (singleton/bound). `Settings`: generate/store the token (`SharedPreferences`), port.

- [ ] **Step 1–3:** Settings (token generation with `SecureRandom`), ServerService (foreground notification, start/stop), Manifest permissions → compiles → Commit `feat(android): foreground service + token store`.

---

### Task 8: Setup UI (Compose) + QR (ZXing)

**Files:** `ui/SetupScreen.kt`, `ui/Qr.kt`, update `MainActivity.kt`.

**Interfaces:** Produces — status (is accessibility enabled, IP, port), QR (`http://<ip>:<port>/?t=<token>`), regenerate token, start/stop, redirect to accessibility settings.

- [ ] **Step 1:** `Qr.kt` — a pure-ish function that produces a `Bitmap` with ZXing (`fun qrBitmap(text:String, size:Int): Bitmap`).
- [ ] **Step 2:** `SetupScreen.kt` Compose — IP detection, show QR, buttons.
- [ ] **Step 3:** compiles → Commit `feat(android): setup screen with pairing QR`.

---

### Task 9: PWA bundling Gradle task + APK

**Files:** `app/build.gradle.kts` (copy task).

**Interfaces:** Produces — `../client-pwa/dist` → `src/main/assets/web` is copied before `preBuild`.

- [ ] **Step 1:** Gradle `Copy` task (`from("../../client-pwa/dist") into("src/main/assets/web")`), `preBuild.dependsOn`.
- [ ] **Step 2:** `cd client-pwa && npm run build` then `./gradlew :app:assembleDebug` → PWA inside the APK. Commit `feat(android): bundle PWA build into app assets`.

---

### Task 10: On-device manual verification (real TV)

Spec section 13 checklist — cannot be unit tested, user on the TV:
- [ ] Sideload the APK to the TV + enable the accessibility service.
- [ ] Scan the QR with a phone → PWA connects (status "Connected").
- [ ] Cursor move/click/scroll; volume; send text + Enter/Search; Home/Back/Recents.
- [ ] Is the overlay visible across all apps; does battery optimization kill the service; lock-screen behavior.

**Open risks:** AGP/Gradle/JDK compatibility (adjust versions if needed); accessibility injection is only verified on a device; some OEM restrictions.

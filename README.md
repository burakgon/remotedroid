<div align="center">

# RemoteDroid

**A touchpad-first remote control for plain-Android (not Android TV) TVs that ship without a remote.**
*No root, no PC, no extra remote.*

</div>

---

## What is this?

Some TVs (e.g. the **Thomson Go Plus 32UE5M45**) run plain Android — not Android TV — and behave like a **touchscreen Android tablet** inside. They come without a remote, so every time you have to walk up to the TV and tap the screen yourself.

**RemoteDroid** fixes that: an app installed on the TV serves a web remote (a PWA) that you open from your phone's browser. You glide a **cursor** around the TV with your finger (like a laptop trackpad), raise and lower the volume, and type text to send — all over the local network, **without root**, via Android's **Accessibility Service**.

## How it works

```
 Phone (browser / PWA)  ──ws://tv-ip:8080──►  TV (Android + RemoteDroid)
   touchpad · volume · keyboard    JSON commands     Accessibility Service
                                                      dispatchGesture / ACTION_SET_TEXT
```

1. Open the app on the TV, **Start the server** and enable the **Accessibility service**.
2. **Scan the on-screen QR with your phone camera** → the remote opens in the browser (auto-connects with the token).
3. Drag the cursor with the touchpad and tap; use the volume/navigation buttons; type into the keyboard box and send it **all at once**.

## Features

- **Touchpad-first:** relative cursor (overlay on the TV), single tap, two-finger scroll.
- **Volume:** up / down / mute.
- **Keyboard:** type into the box → the whole text lands in the TV's field at once (Enter/Search included, Android 11+).
- **Navigation:** Home · Back · Recents.
- **QR pairing:** address + token in a single code; only the paired device can control the TV.
- **No root, no extra device.**

## Project layout

| Directory | Contents |
|---|---|
| `client-pwa/` | Web remote — Svelte + TypeScript + Vite (served by the TV) |
| `server-android/` | TV app — Kotlin, Compose, Ktor (CIO), Accessibility Service |
| `docs/superpowers/` | Design spec and implementation plans |

## Build & run

**PWA (remote):**
```bash
cd client-pwa
npm install
npm run dev      # development
npm test         # unit tests
npm run build    # dist/ → embedded into the Android assets
```

**Android server (TV):**

Requirements: **JDK 17–21** and the Android SDK (compileSdk 35, build-tools 35). If your default `java`
is newer, point `JAVA_HOME` at a 17–21 JDK (Gradle 8.9 does not run on newer JDKs).
`local.properties` is not in the repo; create it on first open with `sdk.dir=/path/to/Android/SDK`
(or have `ANDROID_HOME` set).

```bash
cd server-android
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"   # macOS example; any JDK 17–21 works
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleDebug       # APK (PWA embedded automatically)
# Output: app/build/outputs/apk/debug/app-debug.apk → sideload to the TV
```

## Limitations

- It **can't power on** a TV that's off (no Wake-on-WiFi path); it can put one to sleep.
- Same **local network** only; over `http` (token-protected). No internet access.
- **No screen mirroring** — you use it while looking at the TV (blind touchpad).
- Accessibility text injection works best in search/URL boxes.

## License

[Apache-2.0](./LICENSE).

---

<div align="center">
<sub>Design, plans and tests live under <code>docs/superpowers/</code>.</sub>
</div>

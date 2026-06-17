# RemoteDroid — Design Document

- **Date:** 2026-06-18
- **Status:** Awaiting approval (draft)
- **License (proposed):** Apache-2.0
- **Project name:** RemoteDroid

---

## 1. Summary and Goals

The Thomson Go Plus **32UE5M45** is a touchscreen TV that runs **real Android** (not Android TV)
and ships without a remote. Today, every time you want to use it you have to physically walk
up to the TV and tap the screen (and even enter the screen-lock pattern).

**RemoteDroid** is a two-part, open-source system that lets you control this TV from the couch:

- **Server (TV side):** A native Android app that runs on the TV. It injects tap/volume/text
  input into the system through an **Accessibility Service** and runs an embedded web server.
  **No root required.**
- **Remote (client):** A **PWA** served by the TV and opened from the phone's browser.
  There's no separate store app; "add to home screen" turns it into a shortcut. It works on
  Android, iPhone, and desktop browsers.

Design principle: **touchpad-first** — at the center of the remote sits a large touch surface
that behaves just like a real laptop trackpad.

---

## 2. Goals and Non-Goals

### Goals (v1)
- **Relative-cursor touchpad:** Finger movement drives a cursor on the TV; tap / long-press /
  drag / two-finger scroll are supported.
- **Volume control:** Raise / lower / mute the volume.
- **Typing with the keyboard:** The **entire** text typed into the box on the phone lands in the
  focused field on the TV **in one shot**; a separate "Enter/Search" action.
- **Navigation:** Home, Back, Recents, Notifications, Quick Settings, Sleep.
- **QR pairing:** The TV shows a QR; scanning it with the phone delivers the address + token and
  connects automatically.
- **No root, no extra device (PC).**
- **A modern, polished UI** and a clean open-source project structure.

### Non-Goals (outside v1)
- Screen casting / mirroring (you use it while looking at the TV; a "blind" touchpad).
- Turning on a powered-off TV (no Wake-on-LAN path over WiFi).
- A root-based advanced mode.
- Remote access over the internet (local network only).
- Multi-user / account management.

---

## 3. Architecture — Overview

```
┌──────────────────────────┐      WiFi / LAN        ┌────────────────────────────────┐
│   PHONE (Browser)        │  ws://tv-ip:8080       │         TV (Android)           │
│   ──  PWA Remote  ──     │   + token (from QR)    │   ──  RemoteDroid Server  ──   │
│                          │ ◄───────────────────►  │                                │
│  • Touchpad surface      │   JSON commands        │  Ktor server (HTTP + WebSocket)│
│  • Volume +/−            │  move·tap·scroll       │        │                       │
│  • Text box + Send       │  vol·text·home...      │        ▼                       │
│  • Home / Back / Recents │                        │  Accessibility Service         │
│  • Connect via QR scan   │                        │   • Cursor overlay             │
└──────────────────────────┘                        │   • dispatchGesture (tap)      │
                                                    │   • ACTION_SET_TEXT (text)     │
                                                    │   • Global: Home/Back/Vol      │
                                                    │ Foreground Service (persistent)│
                                                    │  Setup screen (Compose) + QR   │
                                                    └────────────────────────────────┘
```

**Data flow:** The remote captures finger movement → sends relative deltas as JSON over the
WebSocket → the server translates the command into an Accessibility Service action (move the
cursor, tap via `dispatchGesture`, type text, change the volume, etc.).

---

## 4. Server (TV) Components

### 4.1 Accessibility Service (core)
The layer that injects input into the system:

- **Cursor overlay:** The service adds a small cursor graphic to the screen via `WindowManager`
  (`TYPE_ACCESSIBILITY_OVERLAY`). The overlay is **touch-transparent** (`FLAG_NOT_TOUCHABLE`,
  `FLAG_NOT_FOCUSABLE`) — it is purely visual and doesn't block taps from reaching the app
  beneath it. The cursor's (x, y) position is held by the service; it's updated with incoming
  deltas and clamped to the screen bounds. The server is the source of truth for position.
- **Tap gestures** (`dispatchGesture`, always at the cursor's position):
  - `move` → just move the cursor (visual; no gesture is dispatched).
  - `tap` → a short tap. `longpress` → a long press (a longer-duration stroke).
  - `drag` → press-drag-release; a smooth, continuous stroke via `willContinue`.
  - `scroll` → a real two-finger scroll using **two parallel strokes**
    (`dispatchGesture` supports multi-touch).
- **Typing:** It finds the focused editable node with `findFocus(FOCUS_INPUT)`, sets the text
  **in one shot** with `ACTION_SET_TEXT`, and moves the cursor to the end
  (`ACTION_SET_SELECTION`). For "Enter/Search" it uses `ACTION_IME_ENTER` (Android 11 / API 30+).
- **Global actions:** `GLOBAL_ACTION_HOME`, `BACK`, `RECENTS`, `NOTIFICATIONS`,
  `QUICK_SETTINGS`, `LOCK_SCREEN` (sleep).
- **Volume:** `AudioManager.adjustStreamVolume(STREAM_MUSIC, RAISE/LOWER, FLAG_SHOW_UI)`;
  the volume bar shows on the TV too. Muting included.

### 4.2 Foreground Service (persistence)
Keeps the WebSocket/HTTP server alive, runs with a persistent notification, and starts
automatically on boot. The app requests permission to disable battery optimization (not critical
since the TV is usually plugged in, but a safeguard against OEMs killing the service).

### 4.3 Embedded Server (Ktor CIO)
- Serves the PWA files **statically** from the APK's `assets/web` (`GET /`).
- `GET /ws?t=TOKEN` → the WebSocket command channel (the token is validated while the connection
  is established).
- Binds to the local network IP, on a fixed port (default `8080`, configurable on conflict).

### 4.4 Setup Screen (Jetpack Compose, Material 3)
A status dashboard:
- Is the accessibility service enabled? (if not, a button that takes you to Settings)
- **QR code** (address + token), a button to refresh the token
- Port setting, start/stop the server, IP address
- Step-by-step setup instructions

---

## 5. Remote (PWA) Components

A **touchpad-first** layout (portrait phone is the primary target; landscape and desktop friendly):

```
┌───────────────────────────┐
│ ● Connected        ⚙  ⌨   │  status · settings · keyboard
├───────────────────────────┤
│                           │
│       T O U C H P A D     │  most of the screen — large surface
│   drag · tap · two-finger │
│        scroll             │
│                           │
├───────────────────────────┤
│   ◀ Back   ⌂ Home   ▣     │  navigation
├───────────────────────────┤
│   🔉 −       🔇        🔊 +  │  volume
└───────────────────────────┘
```

Keyboard panel (slides up from the bottom via ⌨):
```
│  Type a message:                │
│  ┌───────────────────────────┐  │ ← the phone's own keyboard
│  │ hello world|              │  │
│  └───────────────────────────┘  │
│  [   Send   ]   [Enter / Search]│
│  [ Backspace ]  [    Clear    ] │
```

### Interaction
- **Touchpad:** Single-finger movement via Pointer Events → a **relative delta**; coalesced with
  `requestAnimationFrame` and sent at a ~60–120/s upper bound. A quick tap = `tap`; press-and-hold
  = `longpress`; press-hold-and-drag = `drag`; two fingers = `scroll`. Sensitivity/acceleration in
  settings.
- **Haptic feedback:** `navigator.vibrate` (on devices that support it).
- **Volume:** on-screen buttons (the phone's physical volume keys can't be reliably captured from
  the browser).

### Connection Flow (QR + persistence)
> **Subtle point:** Reading a QR with the camera from inside the PWA doesn't work on an `http` LAN
> (the camera requires a secure context). For that reason, scanning is done **with the phone's own
> camera app**.

1. The TV displays a QR carrying the address `http://tv-ip:8080/?t=TOKEN`.
2. You scan it with the phone's camera → the browser opens this address → the PWA loads with the
   token.
3. The PWA saves the token to `localStorage` and connects to `ws://tv-ip:8080/ws?t=TOKEN`.
4. On subsequent launches the token comes from storage → it connects automatically. On a drop, it
   reconnects automatically + shows a status indicator (connected / connecting / disconnected).

### Visual Direction
Dark theme, large tap targets, a touchpad with subtle "ripple" feedback in the center, a single
accent color, smooth animations, clean and modern. The visual finish will be refined during the
implementation phase with the `frontend-design` skill.

---

## 6. Communication Protocol

**Transport:** **JSON** messages over a WebSocket (bandwidth is plentiful on the LAN; easy to
debug; ordered and reliable since it's TCP). The token is validated while the WS connection is
established.

### Remote → Server

| `type` | Fields | Meaning |
|---|---|---|
| `hello` | `token`, `client`, `v` | First message; metadata |
| `move` | `dx`, `dy` | Relative cursor movement (coalesced) |
| `tap` | — | Tap at the cursor's position |
| `longpress` | — | Long press |
| `dragstart` / `dragmove` / `dragend` | (`dx`,`dy`) | Press-drag-release |
| `scroll` | `dx`, `dy` | Two-finger scroll |
| `volume` | `dir`: `up`/`down` | Volume |
| `mute` | — | Mute/unmute |
| `text` | `value` | Type the text into the focused field in one shot |
| `submit` | — | Enter / Search (`ACTION_IME_ENTER`, A11+) |
| `backspace` / `clear` | — | Delete / clear |
| `global` | `action`: `home`,`back`,`recents`,`notifications`,`quicksettings`,`sleep` | Global actions |
| `ping` | — | Liveness |

### Server → Remote

| `type` | Fields | Meaning |
|---|---|---|
| `welcome` | `screen{w,h}`, `android`, `features` | Handshake confirmation + capabilities |
| `error` | `code`, `message` | e.g. `unauthorized`, `no_focus` |
| `status` | `accessibility`, `focus` | Service/focus state changed |
| `pong` | — | Liveness response |

### Example Session
```jsonc
→ {"type":"hello","token":"a1b2…","client":"PWA","v":1}
← {"type":"welcome","screen":{"w":1920,"h":1080},"android":13,
   "features":{"imeEnter":true,"scroll":true}}
→ {"type":"move","dx":24,"dy":-8}        // cursor moves
→ {"type":"tap"}                          // taps at the cursor position
→ {"type":"scroll","dx":0,"dy":120}       // two-finger down
→ {"type":"text","value":"interstellar"}  // full text into the search box
→ {"type":"submit"}                       // press Search
```

### Notes
- **Move coalescing:** The remote sums the per-frame deltas and sends them together → the network
  doesn't get flooded.
- **Coordinate authority:** Deltas are in the remote's CSS px; the server scales them with a
  sensitivity × acceleration curve and clamps the cursor to the screen bounds.
- **Capability advertisement:** Via `welcome.features`, the remote learns what's supported on the
  device (e.g. on Android 10, `imeEnter:false` → hide/warn about the "Search" button).
- **Version field (`v`):** forward compatibility.

---

## 7. Pairing and Discovery

- **Token:** On first launch the server generates a strong random token and stores it on the
  device.
- **QR contents:** `http://<tv-ip>:<port>/?t=<token>` — discovery (address) + security (token) in
  a single frame.
- **Validation:** On the WS upgrade request, the `t` parameter is compared against the token; if it
  doesn't match, the connection is rejected.
- **Token refresh:** Can be refreshed from the setup screen; old pairings become invalid.
- **Persistence:** The PWA keeps the token + address in `localStorage`; on subsequent launches it
  connects automatically.
- **IP change:** If the IP changes, the QR is scanned again (v1). (Optional future: discovery via
  mDNS/NSD.)

---

## 8. Security and Limits

### Security model
- **LAN only:** The server binds to the local network; it's never exposed to the internet.
  Token-protected.
- **Threat:** An attacker on the same WiFi needs the token (which they can't know unless they've
  seen the QR).
- **Honest warning:** `http` is plaintext on the LAN → someone sniffing packets on the same network
  could theoretically see the token/commands. Acceptable for home use. **Optional future:** full
  HTTPS with a trusted certificate + an installable PWA.

### Known limits
- **A powered-off TV can't be turned on:** There's no path to wake it over WiFi. Sleep/sure.
- **Secure screen lock:** Unlocking the pattern/PIN with the cursor may be blocked by the keyguard
  → **to be verified on the device.**
- **Android < 11:** No `ACTION_IME_ENTER` → for "Search" you press the on-screen search button.
- **Accessibility typing:** It sets the whole field; works best in search/URL boxes; some custom
  fields may not expose an editable node.
- **No screen mirroring:** A blind touchpad; you use it while looking at the TV.

---

## 9. Technology Stack

### Server (TV — Android)
- **Language:** Kotlin
- **UI:** Jetpack Compose, Material 3
- **Embedded server:** Ktor (CIO engine) — static content + WebSocket
- **Serialization:** kotlinx.serialization
- **QR:** ZXing
- **minSdk 26 (Android 8)**, full features (IME enter) on 30+; current targetSdk
- **Build:** Gradle (Kotlin DSL)

### Remote (PWA)
- **Svelte + TypeScript + Vite** (small bundle, modern, fast)
- Scoped CSS (a distinctive, clean look)
- Web App Manifest (add to home screen); a service worker only makes sense over HTTPS (future)

### Build flow
The PWA is built (`vite build`) → the output is copied into
`server-android/app/src/main/assets/web/` → embedded into the APK. A single Gradle task / npm
script automates this copy.

---

## 10. Project Structure

```
remotedroid/
├── server-android/                 # TV app (Kotlin/Compose)
│   ├── app/
│   │   └── src/main/
│   │       ├── java/.../accessibility/   # A11yService, cursor overlay, gesture mapper
│   │       ├── java/.../server/          # Ktor server, WS handler, protocol models
│   │       ├── java/.../service/         # Foreground service
│   │       ├── java/.../ui/              # Compose setup UI, QR generation
│   │       └── assets/web/               # PWA build output (embedded)
│   └── build.gradle.kts
├── client-pwa/                     # Svelte/TS/Vite
│   ├── src/
│   │   ├── lib/transport/          # WebSocket client + protocol types
│   │   ├── lib/touchpad/           # touchpad component + gesture detection
│   │   ├── lib/ui/                 # volume, keyboard panel, nav, settings
│   │   └── App.svelte
│   ├── vite.config.ts
│   └── manifest.webmanifest
├── docs/
├── LICENSE                         # Apache-2.0
└── README.md                       # TR + EN
```

---

## 11. Test Strategy

**TDD** (write the test first) for pure logic:
- **Server (JVM unit):** protocol parsing (kotlinx.serialization round-trip), gesture math
  (delta → coordinate, clamping to bounds, acceleration curve), token validation.
- **Remote (Vitest):** gesture-detection thresholds (distinguishing tap / drag / scroll), transport
  reconnection logic, delta coalescing.
- **On-device manual checklist:** Accessibility flows (real tap injection) can't be unit tested →
  verified step by step on the real TV.

---

## 12. Roadmap (Milestones)

- **M1 — Skeleton & proof:** Android app + Accessibility Service; moving the cursor overlay +
  `dispatchGesture` tap at a fixed coordinate. *Does tap injection work on the device?*
- **M2 — End-to-end touchpad:** Ktor server + WS + a minimal PWA touchpad → `move`/`tap` work.
- **M3 — Full gestures:** `drag`, `scroll`, `longpress`, volume, navigation buttons.
- **M4 — Typing:** `text` (set_text) + `submit` (ime_enter) + the keyboard panel.
- **M5 — Pairing:** QR + token + `localStorage` persistence + automatic reconnection.
- **M6 — Persistence & setup:** Foreground service, auto-start, battery-optimization warning,
  setup UI.
- **M7 — Polish:** Visual refinement (`frontend-design`), PWA manifest / add to home screen.
- **M8 — Release:** Documentation, LICENSE, README (TR+EN), screenshots, versioning.

---

## 13. To Verify on the Device (Open Questions)

To be confirmed on the real TV:
1. **Android version** (for IME enter / API level).
2. **Screen resolution** (cursor/coordinate scaling).
3. Does `dispatchGesture` work **on the lock screen** (can we unlock the pattern)?
4. Does the **overlay** appear on top of all apps?
5. Does **battery optimization** kill the service?
6. **Sideloading** the apps and being able to grant the required permissions.

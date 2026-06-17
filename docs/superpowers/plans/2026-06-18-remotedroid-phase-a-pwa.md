# RemoteDroid — Phase A: Protokol + PWA Kumanda — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** TV'nin sunacağı PWA kumandanın çalışan, test edilmiş ilk sürümünü kurmak — göreli imleçli touchpad, ses, gezinme ve klavye komutlarını WebSocket/JSON protokolüyle gönderen, sahte sunucuya karşı uçtan uca doğrulanabilir bir istemci.

**Architecture:** Svelte + TypeScript + Vite tek sayfa uygulaması. Saf mantık (protokol kodlama, gesture algılama, WebSocket taşıma + yeniden bağlanma) framework'ten bağımsız modüller olarak yazılır ve Vitest ile TDD edilir; Svelte bileşenleri bu modülleri kullanır. WebSocket yapıcı (`WebSocketCtor`) enjekte edilebilir, böylece taşıma katmanı gerçek ağ olmadan test edilir.

**Tech Stack:** Svelte 5, TypeScript, Vite, Vitest, jsdom, Node 25 / npm 11.

## Global Constraints

- Protokol sürümü `v = 1`; mesaj şekilleri spec bölüm 6 ile birebir aynı (`docs/superpowers/specs/2026-06-18-remotedroid-design.md`).
- Taşıma: WebSocket üzerinden JSON; `ws://<host>:<port>/ws?t=<token>`.
- Varsayılan port 8080 (host/port `window.location`'dan türetilir; geliştirmede override edilebilir).
- Tüm modüller TypeScript `strict` modunda derlenir.
- Lisans Apache-2.0 (repo köküne LICENSE Phase B'de eklenir; bu plan client-pwa'ya odaklı).
- Saf mantık için TDD: önce başarısız test, sonra minimal implementasyon. Her task sonunda commit.
- DRY / YAGNI: longpress, drag ve QR-kamera Phase A kapsamı DIŞINDA (sonraki PWA planı). Phase A: `move`, `tap`, `scroll`, `volume`, `mute`, `global`, `text`, `submit`, `backspace`, `clear`.

---

## Dosya Yapısı

```
client-pwa/
├── package.json
├── tsconfig.json
├── svelte.config.js
├── vite.config.ts
├── index.html
├── public/manifest.webmanifest
├── src/
│   ├── main.ts                       # uygulama girişi
│   ├── App.svelte                    # kompozisyon + token bootstrap
│   ├── lib/
│   │   ├── protocol/messages.ts      # protokol tipleri + encode/decode (saf)
│   │   ├── touchpad/gestureDetector.ts  # pointer → gesture (saf)
│   │   ├── touchpad/Touchpad.svelte  # dokunma yüzeyi bileşeni
│   │   ├── transport/connection.ts   # WS istemci + yeniden bağlanma (saf çekirdek)
│   │   └── ui/
│   │       ├── StatusBar.svelte
│   │       ├── NavBar.svelte
│   │       ├── VolumeBar.svelte
│   │       └── KeyboardPanel.svelte
│   └── app.css
└── tests/
    ├── messages.test.ts
    ├── gestureDetector.test.ts
    ├── connection.test.ts
    └── keyboardPanel.test.ts
```

**Sorumluluklar:**
- `messages.ts` — protokolün tek TS kaynağı; sadece tipler + `encode`/`decodeServer`. Ağ/DOM bilmez.
- `gestureDetector.ts` — pointer örneklerinden semantik gesture üretir. Zaman, DOM, ağ bilmez; tamamen deterministik.
- `connection.ts` — WS yaşam döngüsü, hello el sıkışması, welcome saklama, üssel geri çekilmeli yeniden bağlanma. `WebSocketCtor` enjekte edilebilir.
- Svelte bileşenleri — yukarıdaki modülleri DOM'a bağlar.

---

### Task 1: PWA iskeleti (derlenir + test koşar)

**Files:**
- Create: `client-pwa/package.json`, `client-pwa/tsconfig.json`, `client-pwa/svelte.config.js`, `client-pwa/vite.config.ts`, `client-pwa/index.html`, `client-pwa/src/main.ts`, `client-pwa/src/App.svelte`, `client-pwa/src/app.css`

**Interfaces:**
- Produces: çalışan `npm run dev`, `npm run build`, `npm test` (Vitest, jsdom ortamı).

- [ ] **Step 1: package.json yaz**

```json
{
  "name": "remotedroid-client",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview",
    "check": "svelte-check --tsconfig ./tsconfig.json",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "devDependencies": {
    "@sveltejs/vite-plugin-svelte": "^5.0.0",
    "@testing-library/svelte": "^5.2.0",
    "@testing-library/jest-dom": "^6.4.0",
    "jsdom": "^25.0.0",
    "svelte": "^5.0.0",
    "svelte-check": "^4.0.0",
    "typescript": "^5.6.0",
    "vite": "^6.0.0",
    "vitest": "^2.1.0"
  }
}
```

- [ ] **Step 2: tsconfig.json, svelte.config.js, vite.config.ts yaz**

`tsconfig.json`:
```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "bundler",
    "strict": true,
    "verbatimModuleSyntax": true,
    "isolatedModules": true,
    "skipLibCheck": true,
    "types": ["svelte", "vitest/globals", "@testing-library/jest-dom"]
  },
  "include": ["src", "tests"]
}
```

`svelte.config.js`:
```js
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';
export default { preprocess: vitePreprocess() };
```

`vite.config.ts`:
```ts
import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

export default defineConfig({
  plugins: [svelte()],
  build: { outDir: 'dist', emptyOutDir: true },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['@testing-library/jest-dom/vitest'],
  },
});
```

- [ ] **Step 3: index.html, main.ts, App.svelte, app.css yaz**

`index.html`:
```html
<!doctype html>
<html lang="tr">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover" />
    <meta name="theme-color" content="#0b0f17" />
    <link rel="manifest" href="/manifest.webmanifest" />
    <title>RemoteDroid</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

`src/main.ts`:
```ts
import { mount } from 'svelte';
import './app.css';
import App from './App.svelte';

const app = mount(App, { target: document.getElementById('app')! });
export default app;
```

`src/App.svelte` (Task 6'da genişletilecek — şimdilik placeholder yerine gerçek minimal içerik):
```svelte
<script lang="ts">
</script>

<main>
  <h1>RemoteDroid</h1>
</main>

<style>
  main { display: grid; place-items: center; height: 100dvh; color: #e8eef7; }
</style>
```

`src/app.css`:
```css
:root { color-scheme: dark; }
* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
html, body { margin: 0; height: 100%; background: #0b0f17;
  font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; overscroll-behavior: none; }
```

- [ ] **Step 4: Bağımlılıkları kur ve derle**

Run: `cd client-pwa && npm install && npm run build`
Expected: `dist/` üretilir, hata yok.

- [ ] **Step 5: Test altyapısının koştuğunu doğrula (geçici smoke test)**

`tests/messages.test.ts` (geçici):
```ts
import { describe, it, expect } from 'vitest';
describe('smoke', () => { it('runs', () => { expect(1 + 1).toBe(2); }); });
```
Run: `cd client-pwa && npm test`
Expected: 1 passed.

- [ ] **Step 6: Commit**

```bash
git add client-pwa && git commit -m "feat(pwa): scaffold Svelte+Vite+Vitest project"
```

---

### Task 2: Protokol mesajları (saf, TDD)

**Files:**
- Create: `client-pwa/src/lib/protocol/messages.ts`
- Test: `client-pwa/tests/messages.test.ts` (smoke testin yerine geçer)

**Interfaces:**
- Produces:
  - `PROTOCOL_VERSION: number`
  - `type ClientMessage` (spec bölüm 6 birebir), `type ServerMessage`, `type GlobalAction`, `interface Features`
  - `encode(msg: ClientMessage): string`
  - `decodeServer(data: string): ServerMessage`

- [ ] **Step 1: Başarısız testi yaz**

`tests/messages.test.ts`:
```ts
import { describe, it, expect } from 'vitest';
import { encode, decodeServer, PROTOCOL_VERSION, type ClientMessage } from '../src/lib/protocol/messages';

describe('protocol', () => {
  it('encodes a move message to compact JSON', () => {
    const m: ClientMessage = { type: 'move', dx: 12, dy: -3 };
    expect(encode(m)).toBe('{"type":"move","dx":12,"dy":-3}');
  });

  it('encodes a hello with the protocol version', () => {
    const m: ClientMessage = { type: 'hello', token: 'abc', client: 'PWA', v: PROTOCOL_VERSION };
    expect(JSON.parse(encode(m))).toEqual({ type: 'hello', token: 'abc', client: 'PWA', v: 1 });
  });

  it('decodes a welcome message', () => {
    const w = decodeServer('{"type":"welcome","screen":{"w":1920,"h":1080},"android":13,"features":{"imeEnter":true,"scroll":true}}');
    expect(w).toEqual({ type: 'welcome', screen: { w: 1920, h: 1080 }, android: 13, features: { imeEnter: true, scroll: true } });
  });
});
```

- [ ] **Step 2: Testi koş, başarısız olduğunu doğrula**

Run: `cd client-pwa && npm test`
Expected: FAIL — `Cannot find module '../src/lib/protocol/messages'`.

- [ ] **Step 3: Minimal implementasyonu yaz**

`src/lib/protocol/messages.ts`:
```ts
export const PROTOCOL_VERSION = 1;

export type GlobalAction =
  | 'home' | 'back' | 'recents' | 'notifications' | 'quicksettings' | 'sleep';

export type ClientMessage =
  | { type: 'hello'; token: string; client: string; v: number }
  | { type: 'move'; dx: number; dy: number }
  | { type: 'tap' }
  | { type: 'longpress' }
  | { type: 'dragstart' }
  | { type: 'dragmove'; dx: number; dy: number }
  | { type: 'dragend' }
  | { type: 'scroll'; dx: number; dy: number }
  | { type: 'volume'; dir: 'up' | 'down' }
  | { type: 'mute' }
  | { type: 'text'; value: string }
  | { type: 'submit' }
  | { type: 'backspace' }
  | { type: 'clear' }
  | { type: 'global'; action: GlobalAction }
  | { type: 'ping' };

export interface Features { imeEnter: boolean; scroll: boolean }

export type ServerMessage =
  | { type: 'welcome'; screen: { w: number; h: number }; android: number; features: Features }
  | { type: 'error'; code: string; message?: string }
  | { type: 'status'; accessibility?: boolean; focus?: boolean }
  | { type: 'pong' };

export function encode(msg: ClientMessage): string { return JSON.stringify(msg); }
export function decodeServer(data: string): ServerMessage { return JSON.parse(data) as ServerMessage; }
```

- [ ] **Step 4: Testi koş, geçtiğini doğrula**

Run: `cd client-pwa && npm test`
Expected: 3 passed.

- [ ] **Step 5: Commit**

```bash
git add client-pwa && git commit -m "feat(pwa): protocol message types and encode/decode"
```

---

### Task 3: Gesture algılayıcı (saf, TDD) — touchpad'in kalbi

**Files:**
- Create: `client-pwa/src/lib/touchpad/gestureDetector.ts`
- Test: `client-pwa/tests/gestureDetector.test.ts`

**Interfaces:**
- Consumes: yok (saf).
- Produces:
  - `interface PointerSample { id: number; x: number; y: number; t: number }`
  - `type Gesture = { type:'move'; dx:number; dy:number } | { type:'tap' } | { type:'scroll'; dx:number; dy:number }`
  - `interface GestureOptions { tapMaxMs:number; tapMaxMovePx:number; sensitivity:number }`
  - `class GestureDetector { constructor(opts?: Partial<GestureOptions>); onPointerDown(s:PointerSample):Gesture[]; onPointerMove(s:PointerSample):Gesture[]; onPointerUp(s:PointerSample):Gesture[] }`

- [ ] **Step 1: Başarısız testi yaz**

`tests/gestureDetector.test.ts`:
```ts
import { describe, it, expect } from 'vitest';
import { GestureDetector } from '../src/lib/touchpad/gestureDetector';

const down = (id: number, x: number, y: number, t: number) => ({ id, x, y, t });

describe('GestureDetector', () => {
  it('emits a tap for a quick stationary press/release', () => {
    const g = new GestureDetector();
    expect(g.onPointerDown(down(1, 100, 100, 0))).toEqual([]);
    expect(g.onPointerUp(down(1, 101, 100, 120))).toEqual([{ type: 'tap' }]);
  });

  it('emits scaled move deltas for single-finger drag', () => {
    const g = new GestureDetector({ sensitivity: 2 });
    g.onPointerDown(down(1, 100, 100, 0));
    expect(g.onPointerMove(down(1, 110, 95, 16))).toEqual([{ type: 'move', dx: 20, dy: -10 }]);
  });

  it('does not emit a tap when the finger moved too far', () => {
    const g = new GestureDetector({ tapMaxMovePx: 8 });
    g.onPointerDown(down(1, 100, 100, 0));
    g.onPointerMove(down(1, 140, 100, 16));
    expect(g.onPointerUp(down(1, 140, 100, 60))).toEqual([]);
  });

  it('does not emit a tap when the press is too long', () => {
    const g = new GestureDetector({ tapMaxMs: 250 });
    g.onPointerDown(down(1, 100, 100, 0));
    expect(g.onPointerUp(down(1, 100, 100, 400))).toEqual([]);
  });

  it('emits scroll for two-finger movement, never a tap', () => {
    const g = new GestureDetector();
    g.onPointerDown(down(1, 100, 100, 0));
    g.onPointerDown(down(2, 200, 100, 5));
    expect(g.onPointerMove(down(2, 200, 130, 20))).toEqual([{ type: 'scroll', dx: 0, dy: 30 }]);
    expect(g.onPointerUp(down(2, 200, 130, 25))).toEqual([]);
    expect(g.onPointerUp(down(1, 100, 100, 30))).toEqual([]);
  });
});
```

- [ ] **Step 2: Testi koş, başarısız olduğunu doğrula**

Run: `cd client-pwa && npm test gestureDetector`
Expected: FAIL — modül yok.

- [ ] **Step 3: Minimal implementasyonu yaz**

`src/lib/touchpad/gestureDetector.ts`:
```ts
export interface PointerSample { id: number; x: number; y: number; t: number }

export type Gesture =
  | { type: 'move'; dx: number; dy: number }
  | { type: 'tap' }
  | { type: 'scroll'; dx: number; dy: number };

export interface GestureOptions { tapMaxMs: number; tapMaxMovePx: number; sensitivity: number }

const DEFAULTS: GestureOptions = { tapMaxMs: 250, tapMaxMovePx: 8, sensitivity: 1.5 };

export class GestureDetector {
  private opts: GestureOptions;
  private pointers = new Map<number, PointerSample>();
  private down: { t: number } | null = null;
  private moved = 0;
  private multi = false;

  constructor(opts: Partial<GestureOptions> = {}) { this.opts = { ...DEFAULTS, ...opts }; }

  onPointerDown(s: PointerSample): Gesture[] {
    this.pointers.set(s.id, s);
    if (this.pointers.size === 1) { this.down = { t: s.t }; this.moved = 0; this.multi = false; }
    else { this.multi = true; }
    return [];
  }

  onPointerMove(s: PointerSample): Gesture[] {
    const prev = this.pointers.get(s.id);
    this.pointers.set(s.id, s);
    if (!prev) return [];
    const dx = s.x - prev.x, dy = s.y - prev.y;
    if (this.pointers.size >= 2) return [{ type: 'scroll', dx, dy }];
    this.moved += Math.hypot(dx, dy);
    const k = this.opts.sensitivity;
    return [{ type: 'move', dx: dx * k, dy: dy * k }];
  }

  onPointerUp(s: PointerSample): Gesture[] {
    const isLast = this.pointers.size === 1;
    const isTap = isLast && !this.multi && this.down !== null
      && (s.t - this.down.t) <= this.opts.tapMaxMs
      && this.moved <= this.opts.tapMaxMovePx;
    this.pointers.delete(s.id);
    if (this.pointers.size === 0) { this.down = null; this.moved = 0; this.multi = false; }
    return isTap ? [{ type: 'tap' }] : [];
  }
}
```

- [ ] **Step 4: Testi koş, geçtiğini doğrula**

Run: `cd client-pwa && npm test gestureDetector`
Expected: 5 passed.

- [ ] **Step 5: Commit**

```bash
git add client-pwa && git commit -m "feat(pwa): pure gesture detector (tap/move/scroll) with tests"
```

---

### Task 4: WebSocket taşıma + yeniden bağlanma (saf çekirdek, TDD)

**Files:**
- Create: `client-pwa/src/lib/transport/connection.ts`
- Test: `client-pwa/tests/connection.test.ts`

**Interfaces:**
- Consumes: `encode`, `decodeServer`, `PROTOCOL_VERSION`, `ClientMessage`, `ServerMessage` (Task 2).
- Produces:
  - `type Status = 'connecting' | 'connected' | 'disconnected'`
  - `interface Connection { status: Readable<Status>; welcome: Readable<Welcome|null>; send(msg:ClientMessage):void; close():void }`
  - `function createConnection(opts: ConnectionOptions): Connection` — `ConnectionOptions { url:string; token:string; client?:string; WebSocketCtor?: typeof WebSocket; reconnectBaseMs?:number; reconnectMaxMs?:number }`

- [ ] **Step 1: Başarısız testi yaz (sahte WebSocket ile)**

`tests/connection.test.ts`:
```ts
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { get } from 'svelte/store';
import { createConnection } from '../src/lib/transport/connection';

class MockWS {
  static last: MockWS | null = null;
  url: string; readyState = 0; sent: string[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((e: { data: string }) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  constructor(url: string) { this.url = url; MockWS.last = this; }
  send(d: string) { this.sent.push(d); }
  close() { this.readyState = 3; this.onclose?.(); }
  _open() { this.readyState = 1; this.onopen?.(); }
  _msg(d: string) { this.onmessage?.({ data: d }); }
}

beforeEach(() => { vi.useFakeTimers(); MockWS.last = null; });
afterEach(() => { vi.useRealTimers(); });

describe('createConnection', () => {
  it('appends the token to the url and sends hello on open', () => {
    const c = createConnection({ url: 'ws://tv:8080/ws', token: 'TOK', WebSocketCtor: MockWS as unknown as typeof WebSocket });
    expect(get(c.status)).toBe('connecting');
    expect(MockWS.last!.url).toBe('ws://tv:8080/ws?t=TOK');
    MockWS.last!._open();
    expect(get(c.status)).toBe('connected');
    expect(JSON.parse(MockWS.last!.sent[0])).toEqual({ type: 'hello', token: 'TOK', client: 'PWA', v: 1 });
  });

  it('stores the welcome message', () => {
    const c = createConnection({ url: 'ws://tv:8080/ws', token: 'T', WebSocketCtor: MockWS as unknown as typeof WebSocket });
    MockWS.last!._open();
    MockWS.last!._msg('{"type":"welcome","screen":{"w":1,"h":2},"android":13,"features":{"imeEnter":true,"scroll":true}}');
    expect(get(c.welcome)?.android).toBe(13);
  });

  it('reconnects with backoff after an unexpected close', () => {
    createConnection({ url: 'ws://tv:8080/ws', token: 'T', reconnectBaseMs: 500, WebSocketCtor: MockWS as unknown as typeof WebSocket });
    const first = MockWS.last!;
    first._open();
    first.close();                      // beklenmedik kopma
    expect(MockWS.last).toBe(first);    // henüz yeni bağlantı yok
    vi.advanceTimersByTime(500);
    expect(MockWS.last).not.toBe(first); // yeni bağlantı kuruldu
  });

  it('does not reconnect after an explicit close()', () => {
    const c = createConnection({ url: 'ws://tv:8080/ws', token: 'T', WebSocketCtor: MockWS as unknown as typeof WebSocket });
    const first = MockWS.last!;
    first._open();
    c.close();
    vi.advanceTimersByTime(10000);
    expect(MockWS.last).toBe(first);
  });
});
```

- [ ] **Step 2: Testi koş, başarısız olduğunu doğrula**

Run: `cd client-pwa && npm test connection`
Expected: FAIL — modül yok.

- [ ] **Step 3: Minimal implementasyonu yaz**

`src/lib/transport/connection.ts`:
```ts
import { writable, type Readable } from 'svelte/store';
import { encode, decodeServer, PROTOCOL_VERSION, type ClientMessage, type ServerMessage } from '../protocol/messages';

export type Status = 'connecting' | 'connected' | 'disconnected';
type Welcome = Extract<ServerMessage, { type: 'welcome' }>;

export interface Connection {
  status: Readable<Status>;
  welcome: Readable<Welcome | null>;
  send: (msg: ClientMessage) => void;
  close: () => void;
}

export interface ConnectionOptions {
  url: string; token: string; client?: string;
  WebSocketCtor?: typeof WebSocket;
  reconnectBaseMs?: number; reconnectMaxMs?: number;
}

export function createConnection(opts: ConnectionOptions): Connection {
  const WS = opts.WebSocketCtor ?? WebSocket;
  const base = opts.reconnectBaseMs ?? 500, max = opts.reconnectMaxMs ?? 8000;
  const status = writable<Status>('connecting');
  const welcome = writable<Welcome | null>(null);
  let ws: WebSocket | null = null, closed = false, attempt = 0;

  const connect = () => {
    status.set('connecting');
    const sep = opts.url.includes('?') ? '&' : '?';
    ws = new WS(`${opts.url}${sep}t=${encodeURIComponent(opts.token)}`);
    ws.onopen = () => {
      attempt = 0; status.set('connected');
      ws!.send(encode({ type: 'hello', token: opts.token, client: opts.client ?? 'PWA', v: PROTOCOL_VERSION }));
    };
    ws.onmessage = (e: MessageEvent) => {
      const data = typeof e.data === 'string' ? e.data : '';
      try { const m = decodeServer(data); if (m.type === 'welcome') welcome.set(m); } catch { /* yoksay */ }
    };
    ws.onclose = () => { status.set('disconnected'); if (!closed) schedule(); };
    ws.onerror = () => { try { ws?.close(); } catch { /* yoksay */ } };
  };

  const schedule = () => {
    const delay = Math.min(max, base * 2 ** attempt); attempt++;
    setTimeout(() => { if (!closed) connect(); }, delay);
  };

  connect();
  return {
    status: { subscribe: status.subscribe },
    welcome: { subscribe: welcome.subscribe },
    send: (msg) => { if (ws && ws.readyState === 1) ws.send(encode(msg)); },
    close: () => { closed = true; ws?.close(); },
  };
}
```

- [ ] **Step 4: Testi koş, geçtiğini doğrula**

Run: `cd client-pwa && npm test connection`
Expected: 4 passed.

- [ ] **Step 5: Commit**

```bash
git add client-pwa && git commit -m "feat(pwa): websocket transport with reconnect and hello handshake"
```

---

### Task 5: Touchpad bileşeni (pointer olaylarını mesaja bağlar)

**Files:**
- Create: `client-pwa/src/lib/touchpad/Touchpad.svelte`

**Interfaces:**
- Consumes: `GestureDetector`, `Gesture` (Task 3); `ClientMessage` (Task 2).
- Produces: `<Touchpad onmessage={(m: ClientMessage) => void} sensitivity={number} />` — pointer olaylarını yakalar, gesture'ları `move`/`tap`/`scroll` ClientMessage'larına çevirip `onmessage` ile yukarı verir.

- [ ] **Step 1: Bileşeni yaz**

`src/lib/touchpad/Touchpad.svelte`:
```svelte
<script lang="ts">
  import { GestureDetector, type Gesture } from './gestureDetector';
  import type { ClientMessage } from '../protocol/messages';

  let { onmessage, sensitivity = 1.5 }: { onmessage: (m: ClientMessage) => void; sensitivity?: number } = $props();

  const detector = new GestureDetector({ sensitivity });
  let surface: HTMLDivElement;

  function emit(gestures: Gesture[]) {
    for (const g of gestures) onmessage(g as ClientMessage);
  }
  const sample = (e: PointerEvent) => ({ id: e.pointerId, x: e.clientX, y: e.clientY, t: e.timeStamp });

  function down(e: PointerEvent) { surface.setPointerCapture(e.pointerId); emit(detector.onPointerDown(sample(e))); }
  function move(e: PointerEvent) { emit(detector.onPointerMove(sample(e))); }
  function up(e: PointerEvent) {
    emit(detector.onPointerUp(sample(e)));
    if (navigator.vibrate) navigator.vibrate(8);
  }
</script>

<div
  bind:this={surface}
  class="pad"
  onpointerdown={down}
  onpointermove={move}
  onpointerup={up}
  onpointercancel={up}
  role="application"
  aria-label="Touchpad"
></div>

<style>
  .pad {
    flex: 1; touch-action: none; user-select: none;
    border-radius: 18px; margin: 12px;
    background:
      radial-gradient(120% 120% at 50% 0%, #18202e 0%, #0e1420 70%) ,
      repeating-linear-gradient(45deg, #ffffff03 0 2px, transparent 2px 14px);
    border: 1px solid #ffffff10;
    box-shadow: inset 0 1px 0 #ffffff10, 0 10px 30px #00000040;
  }
  .pad:active { background: radial-gradient(120% 120% at 50% 0%, #1d2740 0%, #0e1420 70%); }
</style>
```

- [ ] **Step 2: Derleme/type kontrolü**

Run: `cd client-pwa && npm run build`
Expected: hata yok (`dist/` üretilir).

- [ ] **Step 3: Commit**

```bash
git add client-pwa && git commit -m "feat(pwa): touchpad surface component wired to gesture detector"
```

---

### Task 6: UI kabuğu — App + StatusBar + NavBar + VolumeBar + KeyboardPanel

**Files:**
- Create: `client-pwa/src/lib/ui/StatusBar.svelte`, `NavBar.svelte`, `VolumeBar.svelte`, `KeyboardPanel.svelte`
- Modify: `client-pwa/src/App.svelte`
- Test: `client-pwa/tests/keyboardPanel.test.ts`

**Interfaces:**
- Consumes: `createConnection` (Task 4), `Touchpad` (Task 5), `ClientMessage`/`GlobalAction` (Task 2).
- Produces: tam çalışan kumanda; token'ı `?t=` veya `localStorage`'dan alır, ws URL'sini `window.location`'dan türetir, tüm kontrolleri tek `send(msg)` ile sunucuya iletir.

- [ ] **Step 1: KeyboardPanel için başarısız testi yaz**

`tests/keyboardPanel.test.ts`:
```ts
import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent } from '@testing-library/svelte';
import KeyboardPanel from '../src/lib/ui/KeyboardPanel.svelte';

describe('KeyboardPanel', () => {
  it('sends the whole text at once when Gönder is pressed', async () => {
    const send = vi.fn();
    const { getByRole, getByText } = render(KeyboardPanel, { props: { send } });
    await fireEvent.input(getByRole('textbox'), { target: { value: 'interstellar' } });
    await fireEvent.click(getByText('Gönder'));
    expect(send).toHaveBeenCalledWith({ type: 'text', value: 'interstellar' });
  });

  it('sends submit when Enter/Ara is pressed', async () => {
    const send = vi.fn();
    const { getByText } = render(KeyboardPanel, { props: { send } });
    await fireEvent.click(getByText('Enter / Ara'));
    expect(send).toHaveBeenCalledWith({ type: 'submit' });
  });
});
```

- [ ] **Step 2: Testi koş, başarısız olduğunu doğrula**

Run: `cd client-pwa && npm test keyboardPanel`
Expected: FAIL — bileşen yok.

- [ ] **Step 3: UI bileşenlerini yaz**

`src/lib/ui/KeyboardPanel.svelte`:
```svelte
<script lang="ts">
  import type { ClientMessage } from '../protocol/messages';
  let { send }: { send: (m: ClientMessage) => void } = $props();
  let value = $state('');
</script>

<div class="kb">
  <label for="msg">Mesajını yaz</label>
  <textarea id="msg" rows="2" bind:value placeholder="metni yaz, Gönder'e bas…"></textarea>
  <div class="row">
    <button onclick={() => send({ type: 'text', value })}>Gönder</button>
    <button onclick={() => send({ type: 'submit' })}>Enter / Ara</button>
    <button onclick={() => send({ type: 'backspace' })}>⌫</button>
    <button onclick={() => { value = ''; send({ type: 'clear' }); }}>Temizle</button>
  </div>
</div>

<style>
  .kb { display: grid; gap: 8px; padding: 12px; background: #0e1420; border-top: 1px solid #ffffff10; }
  label { color: #9fb0c8; font-size: 13px; }
  textarea { resize: none; border-radius: 10px; border: 1px solid #ffffff14; background: #131a28; color: #e8eef7; padding: 10px; font: inherit; }
  .row { display: flex; gap: 8px; flex-wrap: wrap; }
  button { flex: 1; min-width: 64px; padding: 12px; border-radius: 10px; border: 0; background: #1d2740; color: #e8eef7; font: inherit; }
  button:active { background: #27314f; }
</style>
```

`src/lib/ui/StatusBar.svelte`:
```svelte
<script lang="ts">
  import type { Readable } from 'svelte/store';
  import type { Status } from '../transport/connection';
  let { status, onkeyboard }: { status: Readable<Status>; onkeyboard: () => void } = $props();
  const label: Record<Status, string> = { connecting: 'Bağlanıyor…', connected: 'Bağlı', disconnected: 'Kopuk' };
</script>

<header>
  <span class="dot {$status}"></span><span>{label[$status]}</span>
  <button class="kbbtn" onclick={onkeyboard} aria-label="Klavye">⌨</button>
</header>

<style>
  header { display: flex; align-items: center; gap: 8px; padding: 12px 14px; color: #cdd9ec; }
  .dot { width: 10px; height: 10px; border-radius: 50%; background: #5a6b86; }
  .dot.connected { background: #36d399; } .dot.connecting { background: #fbbd23; } .dot.disconnected { background: #f87272; }
  .kbbtn { margin-left: auto; background: #1d2740; color: #e8eef7; border: 0; border-radius: 10px; padding: 8px 12px; font-size: 18px; }
</style>
```

`src/lib/ui/NavBar.svelte`:
```svelte
<script lang="ts">
  import type { ClientMessage, GlobalAction } from '../protocol/messages';
  let { send }: { send: (m: ClientMessage) => void } = $props();
  const g = (action: GlobalAction) => send({ type: 'global', action });
</script>

<nav>
  <button onclick={() => g('back')}>◀ Geri</button>
  <button onclick={() => g('home')}>⌂ Home</button>
  <button onclick={() => g('recents')}>▣ Son</button>
</nav>

<style>
  nav { display: flex; gap: 8px; padding: 8px 12px; }
  button { flex: 1; padding: 14px; border: 0; border-radius: 12px; background: #1d2740; color: #e8eef7; font: inherit; }
  button:active { background: #27314f; }
</style>
```

`src/lib/ui/VolumeBar.svelte`:
```svelte
<script lang="ts">
  import type { ClientMessage } from '../protocol/messages';
  let { send }: { send: (m: ClientMessage) => void } = $props();
</script>

<div class="vol">
  <button onclick={() => send({ type: 'volume', dir: 'down' })}>🔉 −</button>
  <button onclick={() => send({ type: 'mute' })}>🔇</button>
  <button onclick={() => send({ type: 'volume', dir: 'up' })}>🔊 +</button>
</div>

<style>
  .vol { display: flex; gap: 8px; padding: 8px 12px 14px; }
  button { flex: 1; padding: 14px; border: 0; border-radius: 12px; background: #1d2740; color: #e8eef7; font: inherit; }
  button:active { background: #27314f; }
</style>
```

- [ ] **Step 4: App.svelte'i bunları birleştirecek şekilde yaz**

`src/App.svelte`:
```svelte
<script lang="ts">
  import { createConnection } from './lib/transport/connection';
  import type { ClientMessage } from './lib/protocol/messages';
  import Touchpad from './lib/touchpad/Touchpad.svelte';
  import StatusBar from './lib/ui/StatusBar.svelte';
  import NavBar from './lib/ui/NavBar.svelte';
  import VolumeBar from './lib/ui/VolumeBar.svelte';
  import KeyboardPanel from './lib/ui/KeyboardPanel.svelte';

  // token: URL (?t=) > localStorage
  const params = new URLSearchParams(location.search);
  const urlToken = params.get('t');
  if (urlToken) localStorage.setItem('rd_token', urlToken);
  const token = urlToken ?? localStorage.getItem('rd_token') ?? '';
  const wsUrl = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`;

  const conn = createConnection({ url: wsUrl, token });
  const send = (m: ClientMessage) => conn.send(m);
  let showKeyboard = $state(false);
</script>

<div class="shell">
  <StatusBar status={conn.status} onkeyboard={() => (showKeyboard = !showKeyboard)} />
  <Touchpad onmessage={send} />
  <NavBar {send} />
  <VolumeBar {send} />
  {#if showKeyboard}<KeyboardPanel {send} />{/if}
</div>

<style>
  .shell { display: flex; flex-direction: column; height: 100dvh; }
</style>
```

- [ ] **Step 5: Testi koş + derle**

Run: `cd client-pwa && npm test && npm run build`
Expected: tüm testler PASS (keyboardPanel 2 + öncekiler), `dist/` üretilir.

- [ ] **Step 6: Tarayıcıda manuel doğrula**

Run: `cd client-pwa && npm run dev` → tarayıcıda aç.
Beklenen: Karanlık arayüz; üstte durum (sahte sunucu yoksa "Bağlanıyor…/Kopuk"), büyük touchpad, altında gezinme + ses, ⌨ ile klavye paneli. Konsol hatası yok.

- [ ] **Step 7: Commit**

```bash
git add client-pwa && git commit -m "feat(pwa): full remote UI shell (status, touchpad, nav, volume, keyboard)"
```

---

### Task 7: PWA manifest + istemci README

**Files:**
- Create: `client-pwa/public/manifest.webmanifest`, `client-pwa/README.md`

**Interfaces:**
- Produces: "ana ekrana ekle" için manifest; istemci geliştirme talimatları.

- [ ] **Step 1: manifest.webmanifest yaz**

```json
{
  "name": "RemoteDroid",
  "short_name": "RemoteDroid",
  "start_url": ".",
  "display": "standalone",
  "background_color": "#0b0f17",
  "theme_color": "#0b0f17",
  "orientation": "portrait",
  "icons": []
}
```

- [ ] **Step 2: README.md yaz (kısa)**

```markdown
# RemoteDroid — PWA Kumanda

TV'deki RemoteDroid sunucusunun sunduğu web kumanda.

## Geliştirme
```bash
npm install
npm run dev      # tarayıcıda aç
npm test         # birim testler
npm run build    # dist/ üretir (Android assets'e kopyalanır — Phase B)
```
Protokol: `../docs/superpowers/specs/2026-06-18-remotedroid-design.md` bölüm 6.
```

- [ ] **Step 3: Commit**

```bash
git add client-pwa && git commit -m "chore(pwa): web manifest and client README"
```

---

## Phase A — Tamamlanma Kriteri

- `npm test` → tüm birim testler yeşil (protokol, gesture, transport, klavye paneli).
- `npm run build` → `dist/` hatasız üretilir.
- `npm run dev` → tarayıcıda tam kumanda arayüzü görünür ve etkileşir.
- Çıktı, Phase B'de Android `assets/web`'e kopyalanmaya hazır.

---

## Phase B — Android Sunucu (sonraki plan, özet)

Ayrı plan dosyasında detaylandırılacak. Ön koşullar ve görev iskeleti:

**Ön koşullar (toolchain):**
- `openjdk@21` kur (AGP JDK 26'yı desteklemiyor olabilir).
- `sdkmanager` ile `platform-tools` (adb) + `platforms;android-34` + uygun `build-tools` kur; `ANDROID_HOME` ayarla.
- Gradle wrapper üret.

**Görev iskeleti:**
1. Gradle/Android proje iskeleti (Compose + Ktor + kotlinx.serialization bağımlılıkları), minSdk 26.
2. Protokol modelleri (Kotlin, kotlinx.serialization) — TS `messages.ts` ile birebir; JVM birim testleri (round-trip).
3. Gesture matematiği (delta → koordinat, ivme, ekran sınırına kıstırma) — saf Kotlin, JVM birim testleri.
4. `AccessibilityService`: imleç overlay'i (WindowManager) + `dispatchGesture` (move/tap/scroll) + global eylemler + ses (`AudioManager`).
5. Metin: `ACTION_SET_TEXT` (+ `ACTION_IME_ENTER`, API 30+ koşullu).
6. Ktor CIO sunucu: `assets/web` statik servis + `/ws` (token doğrulama) → komutları servise iletir.
7. Foreground Service + otomatik başlatma + pil optimizasyonu uyarısı.
8. Compose kurulum UI: durum, IP, QR (ZXing), token üret/yenile, başlat/durdur.
9. PWA build çıktısını `assets/web`'e kopyalayan Gradle görevi.
10. **Cihaz üstü manuel doğrulama** (spec bölüm 13): dispatchGesture, overlay, kilit ekranı, pil, sideload.

**Açık riskler:** JDK uyumu; `platform-tools` eksikliği; accessibility enjeksiyonu birim test edilemez → gerçek TV gerekir.

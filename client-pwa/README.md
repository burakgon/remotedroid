# RemoteDroid — PWA Remote

The web remote served by the RemoteDroid server on the TV. Touchpad-first; volume, navigation and
send-text-at-once. Connects to the TV over WebSocket/JSON.

## Development

```bash
npm install
npm run dev      # open in the browser (dev server)
npm test         # unit tests (protocol, gesture, transport, keyboard)
npm run build    # produces dist/ → copied into the Android assets/web in Phase B
```

## Architecture

- `src/lib/protocol/messages.ts` — protocol types + encode/decode (pure).
- `src/lib/touchpad/gestureDetector.ts` — pointer → gesture (tap/move/scroll), pure & tested.
- `src/lib/transport/connection.ts` — WebSocket + auto-reconnect.
- `src/lib/ui/*` , `src/lib/touchpad/Touchpad.svelte` — UI components.

Protocol contract: `../docs/superpowers/specs/2026-06-18-remotedroid-design.md` (section 6).

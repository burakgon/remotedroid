# RemoteDroid — PWA Kumanda

TV'deki RemoteDroid sunucusunun sunduğu web kumanda. Touchpad-first; ses, gezinme ve
tek-seferde metin gönderme. WebSocket/JSON ile TV'ye bağlanır.

## Geliştirme

```bash
npm install
npm run dev      # tarayıcıda aç (geliştirme sunucusu)
npm test         # birim testler (protokol, gesture, transport, klavye)
npm run build    # dist/ üretir → Phase B'de Android assets/web'e kopyalanır
```

## Mimari

- `src/lib/protocol/messages.ts` — protokol tipleri + encode/decode (saf).
- `src/lib/touchpad/gestureDetector.ts` — pointer → gesture (tap/move/scroll), saf & test edilmiş.
- `src/lib/transport/connection.ts` — WebSocket + otomatik yeniden bağlanma.
- `src/lib/ui/*` , `src/lib/touchpad/Touchpad.svelte` — arayüz bileşenleri.

Protokol sözleşmesi: `../docs/superpowers/specs/2026-06-18-remotedroid-design.md` (bölüm 6).

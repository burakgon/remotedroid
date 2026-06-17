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

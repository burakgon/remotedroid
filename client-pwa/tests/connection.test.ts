import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { get } from 'svelte/store';
import { createConnection } from '../src/lib/transport/connection';

class MockWS {
  static last: MockWS | null = null;
  url: string;
  readyState = 0;
  sent: string[] = [];
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

const ctor = MockWS as unknown as typeof WebSocket;

describe('createConnection', () => {
  it('appends the token to the url and sends hello on open', () => {
    const c = createConnection({ url: 'ws://tv:8080/ws', token: 'TOK', WebSocketCtor: ctor });
    expect(get(c.status)).toBe('connecting');
    expect(MockWS.last!.url).toBe('ws://tv:8080/ws?t=TOK');
    MockWS.last!._open();
    expect(get(c.status)).toBe('connected');
    expect(JSON.parse(MockWS.last!.sent[0])).toEqual({ type: 'hello', token: 'TOK', client: 'PWA', v: 1 });
  });

  it('stores the welcome message', () => {
    const c = createConnection({ url: 'ws://tv:8080/ws', token: 'T', WebSocketCtor: ctor });
    MockWS.last!._open();
    MockWS.last!._msg('{"type":"welcome","screen":{"w":1,"h":2},"android":13,"features":{"imeEnter":true,"scroll":true}}');
    expect(get(c.welcome)?.android).toBe(13);
  });

  it('reconnects with backoff after an unexpected close', () => {
    createConnection({ url: 'ws://tv:8080/ws', token: 'T', reconnectBaseMs: 500, WebSocketCtor: ctor });
    const first = MockWS.last!;
    first._open();
    first.close();
    expect(MockWS.last).toBe(first);
    vi.advanceTimersByTime(500);
    expect(MockWS.last).not.toBe(first);
  });

  it('does not reconnect after an explicit close()', () => {
    const c = createConnection({ url: 'ws://tv:8080/ws', token: 'T', WebSocketCtor: ctor });
    const first = MockWS.last!;
    first._open();
    c.close();
    vi.advanceTimersByTime(10000);
    expect(MockWS.last).toBe(first);
  });
});

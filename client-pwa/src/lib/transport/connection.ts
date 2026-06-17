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
  url: string;
  token: string;
  client?: string;
  WebSocketCtor?: typeof WebSocket;
  reconnectBaseMs?: number;
  reconnectMaxMs?: number;
}

export function createConnection(opts: ConnectionOptions): Connection {
  const WS = opts.WebSocketCtor ?? WebSocket;
  const base = opts.reconnectBaseMs ?? 500;
  const max = opts.reconnectMaxMs ?? 8000;
  const status = writable<Status>('connecting');
  const welcome = writable<Welcome | null>(null);
  let ws: WebSocket | null = null;
  let closed = false;
  let attempt = 0;

  const connect = () => {
    status.set('connecting');
    const sep = opts.url.includes('?') ? '&' : '?';
    ws = new WS(`${opts.url}${sep}t=${encodeURIComponent(opts.token)}`);
    ws.onopen = () => {
      attempt = 0;
      status.set('connected');
      ws!.send(encode({ type: 'hello', token: opts.token, client: opts.client ?? 'PWA', v: PROTOCOL_VERSION }));
    };
    ws.onmessage = (e: MessageEvent) => {
      const data = typeof e.data === 'string' ? e.data : '';
      try {
        const m = decodeServer(data);
        if (m.type === 'welcome') welcome.set(m);
      } catch {
        /* bozuk kare; yoksay */
      }
    };
    ws.onclose = () => {
      status.set('disconnected');
      if (!closed) schedule();
    };
    ws.onerror = () => {
      try { ws?.close(); } catch { /* yoksay */ }
    };
  };

  const schedule = () => {
    const delay = Math.min(max, base * 2 ** attempt);
    attempt++;
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

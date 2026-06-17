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

export function encode(msg: ClientMessage): string {
  return JSON.stringify(msg);
}

export function decodeServer(data: string): ServerMessage {
  return JSON.parse(data) as ServerMessage;
}

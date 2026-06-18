import { describe, it, expect } from 'vitest';
import { GestureDetector } from '../src/lib/touchpad/gestureDetector';

const p = (id: number, x: number, y: number, t: number) => ({ id, x, y, t });

describe('GestureDetector', () => {
  it('emits a tap for a quick stationary press/release', () => {
    const g = new GestureDetector();
    expect(g.onPointerDown(p(1, 100, 100, 0))).toEqual([]);
    expect(g.onPointerUp(p(1, 101, 100, 120))).toEqual([{ type: 'tap' }]);
  });

  it('emits scaled move deltas for single-finger drag', () => {
    const g = new GestureDetector({ sensitivity: 2 });
    g.onPointerDown(p(1, 100, 100, 0));
    expect(g.onPointerMove(p(1, 110, 95, 16))).toEqual([{ type: 'move', dx: 20, dy: -10 }]);
  });

  it('does not emit a tap when the finger moved too far', () => {
    const g = new GestureDetector({ tapMaxMovePx: 8 });
    g.onPointerDown(p(1, 100, 100, 0));
    g.onPointerMove(p(1, 140, 100, 16));
    expect(g.onPointerUp(p(1, 140, 100, 60))).toEqual([]);
  });

  it('does not emit a tap when the press is too long', () => {
    const g = new GestureDetector({ tapMaxMs: 250 });
    g.onPointerDown(p(1, 100, 100, 0));
    expect(g.onPointerUp(p(1, 100, 100, 400))).toEqual([]);
  });

  it('emits scroll for two-finger movement, never a tap', () => {
    const g = new GestureDetector();
    g.onPointerDown(p(1, 100, 100, 0));
    g.onPointerDown(p(2, 200, 100, 5));
    expect(g.onPointerMove(p(2, 200, 130, 20))).toEqual([{ type: 'scroll', dx: 0, dy: 30 }]);
    expect(g.onPointerUp(p(2, 200, 130, 25))).toEqual([]);
    expect(g.onPointerUp(p(1, 100, 100, 30))).toEqual([]);
  });

  it('starts a drag when the finger is held past holdMs before moving', () => {
    const g = new GestureDetector({ sensitivity: 1, holdMs: 500 });
    g.onPointerDown(p(1, 100, 100, 0));
    // first movement arrives after the hold threshold -> drag, not cursor move
    expect(g.onPointerMove(p(1, 110, 100, 600))).toEqual([
      { type: 'dragstart' },
      { type: 'dragmove', dx: 10, dy: 0 },
    ]);
  });

  it('continues a drag on subsequent moves and ends it on pointer up', () => {
    const g = new GestureDetector({ sensitivity: 1, holdMs: 500 });
    g.onPointerDown(p(1, 100, 100, 0));
    g.onPointerMove(p(1, 110, 100, 600)); // dragstart + dragmove
    expect(g.onPointerMove(p(1, 120, 100, 620))).toEqual([{ type: 'dragmove', dx: 10, dy: 0 }]);
    expect(g.onPointerUp(p(1, 120, 100, 640))).toEqual([{ type: 'dragend' }]);
  });

  it('treats an immediate move as a cursor move, not a drag', () => {
    const g = new GestureDetector({ sensitivity: 1, holdMs: 500 });
    g.onPointerDown(p(1, 100, 100, 0));
    expect(g.onPointerMove(p(1, 110, 100, 16))).toEqual([{ type: 'move', dx: 10, dy: 0 }]);
  });

  it('does not emit a tap after a drag', () => {
    const g = new GestureDetector({ holdMs: 500 });
    g.onPointerDown(p(1, 100, 100, 0));
    g.onPointerMove(p(1, 130, 100, 600)); // becomes a drag
    expect(g.onPointerUp(p(1, 130, 100, 650))).toEqual([{ type: 'dragend' }]);
  });
});

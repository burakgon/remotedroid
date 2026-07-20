export interface PointerSample { id: number; x: number; y: number; t: number }

export type Gesture =
  | { type: 'move'; dx: number; dy: number }
  | { type: 'tap' }
  | { type: 'scroll'; dx: number; dy: number }
  | { type: 'dragstart' }
  | { type: 'dragmove'; dx: number; dy: number }
  | { type: 'dragend' };

export interface GestureOptions { tapMaxMs: number; tapMaxMovePx: number; holdMs: number; sensitivity: number }

const DEFAULTS: GestureOptions = { tapMaxMs: 250, tapMaxMovePx: 8, holdMs: 500, sensitivity: 1.5 };

export class GestureDetector {
  private opts: GestureOptions;
  private pointers = new Map<number, PointerSample>();
  private down: { t: number } | null = null;
  private moved = 0;
  private multi = false;
  private dragging = false;
  private dragDecided = false;

  constructor(opts: Partial<GestureOptions> = {}) {
    this.opts = { ...DEFAULTS, ...opts };
  }

  setSensitivity(sensitivity: number): void {
    this.opts.sensitivity = sensitivity;
  }

  onPointerDown(s: PointerSample): Gesture[] {
    this.pointers.set(s.id, s);
    if (this.pointers.size === 1) {
      this.reset();
      this.down = { t: s.t };
    } else {
      this.multi = true;
    }
    return [];
  }

  onPointerMove(s: PointerSample): Gesture[] {
    const prev = this.pointers.get(s.id);
    this.pointers.set(s.id, s);
    if (!prev) return [];
    const dx = s.x - prev.x;
    const dy = s.y - prev.y;
    if (this.pointers.size >= 2) return [{ type: 'scroll', dx, dy }];
    this.moved += Math.hypot(dx, dy);
    const k = this.opts.sensitivity;
    if (this.dragging) return [{ type: 'dragmove', dx: dx * k, dy: dy * k }];
    // The first single-finger move decides the mode: if the finger was held
    // past holdMs before moving, it's a drag; otherwise a plain cursor move.
    if (!this.dragDecided) {
      this.dragDecided = true;
      if (this.down !== null && s.t - this.down.t >= this.opts.holdMs) {
        this.dragging = true;
        return [{ type: 'dragstart' }, { type: 'dragmove', dx: dx * k, dy: dy * k }];
      }
    }
    return [{ type: 'move', dx: dx * k, dy: dy * k }];
  }

  onPointerUp(s: PointerSample): Gesture[] {
    if (this.dragging) {
      this.pointers.delete(s.id);
      if (this.pointers.size === 0) this.reset();
      return [{ type: 'dragend' }];
    }
    const isLast = this.pointers.size === 1;
    const isTap =
      isLast &&
      !this.multi &&
      this.down !== null &&
      s.t - this.down.t <= this.opts.tapMaxMs &&
      this.moved <= this.opts.tapMaxMovePx;
    this.pointers.delete(s.id);
    if (this.pointers.size === 0) this.reset();
    return isTap ? [{ type: 'tap' }] : [];
  }

  private reset() {
    this.down = null;
    this.moved = 0;
    this.multi = false;
    this.dragging = false;
    this.dragDecided = false;
  }
}

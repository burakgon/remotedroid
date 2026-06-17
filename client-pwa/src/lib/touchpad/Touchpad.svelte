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

  function down(e: PointerEvent) {
    surface.setPointerCapture(e.pointerId);
    emit(detector.onPointerDown(sample(e)));
  }
  function move(e: PointerEvent) {
    emit(detector.onPointerMove(sample(e)));
  }
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
    flex: 1;
    touch-action: none;
    user-select: none;
    border-radius: 18px;
    margin: 12px;
    background:
      radial-gradient(120% 120% at 50% 0%, #18202e 0%, #0e1420 70%),
      repeating-linear-gradient(45deg, #ffffff03 0 2px, transparent 2px 14px);
    border: 1px solid #ffffff10;
    box-shadow: inset 0 1px 0 #ffffff10, 0 10px 30px #00000040;
    transition: background 120ms ease;
  }
  .pad:active {
    background: radial-gradient(120% 120% at 50% 0%, #1d2740 0%, #0e1420 70%);
  }
</style>

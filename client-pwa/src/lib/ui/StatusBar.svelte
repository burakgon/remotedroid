<script lang="ts">
  import type { Readable } from 'svelte/store';
  import type { Status } from '../transport/connection';

  let { status, onkeyboard }: { status: Readable<Status>; onkeyboard: () => void } = $props();
  const label: Record<Status, string> = { connecting: 'Connecting…', connected: 'Connected', disconnected: 'Disconnected' };
</script>

<header>
  <span class="dot {$status}"></span>
  <span>{label[$status]}</span>
  <button class="kbbtn" onclick={onkeyboard} aria-label="Keyboard">⌨</button>
</header>

<style>
  header { display: flex; align-items: center; gap: 8px; padding: 12px 14px; color: #cdd9ec; }
  .dot { width: 10px; height: 10px; border-radius: 50%; background: #5a6b86; transition: background 200ms; }
  .dot.connected { background: #36d399; }
  .dot.connecting { background: #fbbd23; }
  .dot.disconnected { background: #f87272; }
  .kbbtn { margin-left: auto; background: #1d2740; color: #e8eef7; border: 0; border-radius: 10px; padding: 8px 12px; font-size: 18px; }
  .kbbtn:active { background: #27314f; }
</style>

<script lang="ts">
  import type { ClientMessage } from '../protocol/messages';

  let { send }: { send: (m: ClientMessage) => void } = $props();
</script>

<div class="vol">
  <button onclick={() => send({ type: 'volume', dir: 'down' })} aria-label="Sesi azalt">🔉 −</button>
  <button onclick={() => send({ type: 'mute' })} aria-label="Sessize al">🔇</button>
  <button onclick={() => send({ type: 'volume', dir: 'up' })} aria-label="Sesi artır">🔊 +</button>
</div>

<style>
  .vol { display: flex; gap: 8px; padding: 8px 12px 14px; }
  button { flex: 1; padding: 14px; border: 0; border-radius: 12px; background: #1d2740; color: #e8eef7; font: inherit; }
  button:active { background: #27314f; }
</style>

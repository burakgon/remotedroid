<script lang="ts">
  import type { ClientMessage, GlobalAction } from '../protocol/messages';

  let { send }: { send: (m: ClientMessage) => void } = $props();
  const g = (action: GlobalAction) => send({ type: 'global', action });
</script>

<nav>
  <button onclick={() => g('back')}>◀ Geri</button>
  <button onclick={() => g('home')}>⌂ Home</button>
  <button onclick={() => g('recents')}>▣ Son</button>
</nav>

<style>
  nav { display: flex; gap: 8px; padding: 8px 12px; }
  button { flex: 1; padding: 14px; border: 0; border-radius: 12px; background: #1d2740; color: #e8eef7; font: inherit; }
  button:active { background: #27314f; }
</style>

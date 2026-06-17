<script lang="ts">
  import { createConnection } from './lib/transport/connection';
  import type { ClientMessage } from './lib/protocol/messages';
  import Touchpad from './lib/touchpad/Touchpad.svelte';
  import StatusBar from './lib/ui/StatusBar.svelte';
  import NavBar from './lib/ui/NavBar.svelte';
  import VolumeBar from './lib/ui/VolumeBar.svelte';
  import KeyboardPanel from './lib/ui/KeyboardPanel.svelte';

  // token önceliği: URL (?t=) > localStorage
  const params = new URLSearchParams(location.search);
  const urlToken = params.get('t');
  if (urlToken) localStorage.setItem('rd_token', urlToken);
  const token = urlToken ?? localStorage.getItem('rd_token') ?? '';
  const wsUrl = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`;

  const conn = createConnection({ url: wsUrl, token });
  const send = (m: ClientMessage) => conn.send(m);

  let showKeyboard = $state(false);
</script>

<div class="shell">
  <StatusBar status={conn.status} onkeyboard={() => (showKeyboard = !showKeyboard)} />
  <Touchpad onmessage={send} />
  <NavBar {send} />
  <VolumeBar {send} />
  {#if showKeyboard}<KeyboardPanel {send} />{/if}
</div>

<style>
  .shell { display: flex; flex-direction: column; height: 100dvh; }
</style>

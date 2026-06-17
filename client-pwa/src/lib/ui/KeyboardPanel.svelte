<script lang="ts">
  import type { ClientMessage } from '../protocol/messages';

  let { send }: { send: (m: ClientMessage) => void } = $props();
  let value = $state('');
</script>

<div class="kb">
  <label for="msg">Mesajını yaz</label>
  <textarea id="msg" rows="2" bind:value placeholder="metni yaz, Gönder'e bas…"></textarea>
  <div class="row">
    <button class="primary" onclick={() => send({ type: 'text', value })}>Gönder</button>
    <button onclick={() => send({ type: 'submit' })}>Enter / Ara</button>
    <button onclick={() => send({ type: 'backspace' })} aria-label="Backspace">⌫</button>
    <button onclick={() => { value = ''; send({ type: 'clear' }); }}>Temizle</button>
  </div>
</div>

<style>
  .kb { display: grid; gap: 8px; padding: 12px; background: #0e1420; border-top: 1px solid #ffffff10; }
  label { color: #9fb0c8; font-size: 13px; }
  textarea {
    resize: none; border-radius: 10px; border: 1px solid #ffffff14;
    background: #131a28; color: #e8eef7; padding: 10px; font: inherit;
  }
  .row { display: flex; gap: 8px; flex-wrap: wrap; }
  button {
    flex: 1; min-width: 64px; padding: 12px; border-radius: 10px; border: 0;
    background: #1d2740; color: #e8eef7; font: inherit;
  }
  button.primary { background: #2f6df0; }
  button:active { background: #27314f; }
  button.primary:active { background: #2a5fd0; }
</style>

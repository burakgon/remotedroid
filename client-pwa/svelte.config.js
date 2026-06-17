import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

// style: false → the Svelte compiler handles plain CSS; this avoids preprocessCSS
// (nested Vite copies) crashing under Vitest. TS script preprocessing stays on.
export default { preprocess: vitePreprocess({ script: true, style: false }) };

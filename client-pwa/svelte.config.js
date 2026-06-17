import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

// style: false → düz CSS'i Svelte derleyici kapsar; Vitest altında preprocessCSS
// (iç içe Vite kopyaları) çökmesini önler. TS script ön-işlemesi açık kalır.
export default { preprocess: vitePreprocess({ script: true, style: false }) };

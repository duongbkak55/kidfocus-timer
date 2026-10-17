import { defineConfig } from 'vite';
import { fileURLToPath } from 'node:url';

export default defineConfig({
  root: fileURLToPath(new URL('.', import.meta.url)),
  base: '/tu-duy/',
  server: { fs: { allow: ['../..'] } },
  build: {
    outDir: '../dist/tu-duy',
    emptyOutDir: false,
    commonjsOptions: { include: [/node_modules/, /shared\/sync\/sync-v2\.js/] },
    lib: { entry: 'src/learning-sync.ts', formats: ['es'], fileName: 'learning-sync' },
  },
});

import { defineConfig } from "vite";
import { fileURLToPath } from "node:url";

export default defineConfig({
  root: fileURLToPath(new URL(".", import.meta.url)),
  base: "/app/",
  publicDir: "public",
  server: { fs: { allow: ["../.."] } },
  build: {
    outDir: "../dist/app",
    emptyOutDir: true,
    commonjsOptions: { include: [/node_modules/, /shared\/sync\/sync-v2\.js/] },
  },
});

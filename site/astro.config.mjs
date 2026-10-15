// The site keeps every URL, header and byte it had before Astro (docs/ASTRO-MIGRATION.md):
// directories with a trailing slash, HTML left as written, assets hashed under /_astro/.
import { defineConfig } from 'astro/config';
import { mkdirSync, writeFileSync } from 'node:fs';

// Writes the catalog keys the pages looked up, so scripts/site-i18n-check.py can tell which
// en.json strings nothing uses any more.
const usedKeys = {
  name: 'tm-used-keys',
  hooks: {
    'astro:build:done': () => {
      const keys = [...(globalThis.__tmUsedKeys ?? [])].sort();
      mkdirSync(new URL('./.astro/', import.meta.url), { recursive: true });
      writeFileSync(new URL('./.astro/used-keys.json', import.meta.url), JSON.stringify(keys, null, 1) + '\n');
    },
  },
};

export default defineConfig({
  site: 'https://tmplayer.org',
  trailingSlash: 'always',
  build: { format: 'directory', inlineStylesheets: 'never' },
  compressHTML: false,
  outDir: 'dist',
  integrations: [usedKeys],
  devToolbar: { enabled: false },
  // The stylesheet blocks the first paint, so it ships minified; the comments stay in
  // src/styles/styles.css, where they are read.
  vite: { build: { cssMinify: true } },
});

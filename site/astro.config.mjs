// The site keeps every URL, header and byte it had before Astro (docs/ASTRO-MIGRATION.md):
// directories with a trailing slash, HTML left as written, assets hashed under /_astro/.
import { defineConfig } from 'astro/config';

export default defineConfig({
  site: 'https://tmplayer.org',
  trailingSlash: 'always',
  build: { format: 'directory', inlineStylesheets: 'never' },
  compressHTML: false,
  outDir: 'dist',
  devToolbar: { enabled: false },
  // styles.css is served as written; minifying it would be the one change nobody asked for.
  vite: { build: { cssMinify: false } },
});

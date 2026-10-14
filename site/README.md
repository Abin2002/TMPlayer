# tmplayer.org

The site is an [Astro](https://astro.build) project. Vercel builds it from `main` (`pnpm build`,
output `dist/`); everything else about serving it, headers, redirects and caching, is in
`vercel.json`.

```sh
pnpm -C site install
pnpm -C site dev        # http://localhost:4321/, every language at its own URL, hot reload
pnpm -C site build      # writes site/dist/
```

## Where things are

| Path | What it is |
|---|---|
| `src/pages/[...lang]/` | the localized pages; each renders in English at `/` and once per published language (`/es/`, `/zh-tw/`, ...) |
| `src/pages/<page>/`, `src/pages/404.astro` | the English-only pages (legal, privacy, security, signing, support, changelog) |
| `src/pages/sitemap.xml.ts`, `src/pages/changelog/feed.xml.ts` | the sitemap and the release feed |
| `src/layouts/Base.astro` | `<html>`, the whole `<head>` (SEO, Open Graph, hreflang), the notice bar and the scripts |
| `src/components/` | Header, Footer, the language picker, `<T>` and `<Icon>`, and the pieces pages share |
| `src/icons/*.svg` | every inline SVG, once, used as `<Icon name="...">` |
| `src/i18n/` | languages and pages (`langs.ts`), catalog lookups (`t.ts`), localized links, JSON-LD translation |
| `src/data/releases.json` | the releases, written by `scripts/release-data.py` (the release job runs it) |
| `src/data/lastmod.json` | each English page's sitemap entry: `lastmod`, `changefreq`, `priority`, images |
| `src/styles/styles.css`, `src/scripts/app.js` | the stylesheet and the script, served under `/_astro/` with a content hash |
| `i18n/<tag>.json` | the string catalogs; `en.json` is the source, the others are translations |
| `public/` | files served as they are: `latest.json` (the apps' update feed), `install.ps1`, screenshots, fonts, icons, `llms.txt`, `robots.txt` |

## Adding a string

Add the key and its English text to `i18n/en.json`, then use it: `{t('page.key')}` for plain text
and attributes, or `<T k="page.key">` when the sentence holds markup, with each piece as a named
slot matching a `{placeholder}` in the string:

```astro
<p><T k="home.quiet"><a slot="a1" href={l('/install/')}>{t('home.quiet__a1')}</a></T></p>
```

Translations go into the other catalogs under the same key, with the same placeholders. A missing
or broken translation falls back to English for that one string. A language is published only
once its catalog covers 95 per cent of the keys.

## Adding a page

A localized page is `src/pages/[...lang]/<path>/index.astro` with `export const getStaticPaths =
langPaths`, plus its id in `PAGES` in `src/i18n/langs.ts`. An English-only page is
`src/pages/<path>/index.astro`. Either way: pass the head to `<Base>` (title, description, and the
page id or path), give it JSON-LD with at least a BreadcrumbList, add its sitemap entry to
`src/data/lastmod.json`, and mention it in `public/llms.txt`.

## Adding a language

Add it to `LANGS` in `src/i18n/langs.ts` and put its catalog at `i18n/<tag>.json`. It appears in
the picker, the hreflang alternates and the sitemap once the catalog is complete enough.

## Checks

```sh
python3 scripts/site-i18n-check.py   # catalogs against the pages and app.js (after a build)
python3 scripts/site-links.py        # every internal link in dist/ resolves
scripts/site-review.sh               # the build against the old site, page by page (needs ../TMPlayer-baseline)
```

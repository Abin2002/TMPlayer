# Moving tmplayer.org to Astro

Written 2026-10-15 against `f71701c` (1.26.0 on the site). Work runs on the `astro` branch; baseline is `f71701c`.

## Goal and the one rule

Make the site easy to work on: one layout, real components, pages that read like pages, and
translations that are plain JSON looked up by key. A complete migration: when it is done there is no
generated HTML in git and no Python HTML parser in the build.

**The rule: nothing a visitor, a crawler, an installed app or the release job sees may change.**
Every URL, redirect, header, canonical, hreflang, JSON-LD block, sitemap entry, feed item, string
in every language, image and script behaviour stays as it is today. The plan is built around
proving that mechanically (the parity harness, AM02) before anything is switched, and switching in
one commit that can be reverted in one commit.

## What exists today (the inventory to preserve)

| Thing | Where | Who depends on it |
|---|---|---|
| 21 hand-written English pages | `site/**/index.html`, `site/404.html` | visitors, Google |
| 12 localized pages x 20 languages = 240 generated pages | `site/<lang>/**` by `scripts/build-site-i18n.py` (1,112 lines) | visitors, Google (hreflang) |
| Catalogs | `site/i18n/<tag>.json`; `en.json` is *extracted from the HTML* | translators; `/translate/` links to `tree/main/site/i18n` |
| Shared chrome copied into every page | `<!-- i18n:head|picker|footer|langcount|notice -->` regions | every page |
| Changelog, release pages, RSS | `site/changelog/**` by `scripts/build-changelog.py` (684 lines), committed by the release job | visitors, feed readers, IndexNow |
| Sitemap | `site/sitemap.xml`; English entries by hand, localized + release entries by the two scripts | Google, Bing (Search Console has `/sitemap.xml`) |
| Update feed | `site/latest.json`, written by `release.yml`, `Cache-Control 300`, `Access-Control-Allow-Origin *` | **installed apps** (`core/.../data/UpdateFeed.kt`) |
| `/apk` redirect | `site/vercel.json` `redirects`, rewritten by `scripts/point-apk-redirect.py` each release | TV Downloader app users |
| Windows installer | `site/install.ps1`, served `text/plain` | `irm tmplayer.org/install.ps1 \| iex` |
| IndexNow key | `site/af11d0ad703ff8c5a7006d0ccd768528.txt` | the release job's ping |
| Crawler files | `robots.txt`, `llms.txt`, `.well-known/security.txt` | crawlers, AI assistants |
| Brand files | `og.png`, `icon-512.png`, `favicon.svg`, `logo.svg`, `site.webmanifest`, `badges/` | social previews, PWA |
| Assets | `styles.css` (3,010 lines), `app.js` (1,342 lines, 48 `tmT()` strings, reads `<script id="tm-langs">`), `fonts/`, `screenshots/` | every page |
| Headers and caching | `site/vercel.json`: 10 header rules, `cleanUrls`, `trailingSlash` | everything |
| Deploy | Vercel git integration, project root `site/`, **no build step** | |
| CI | `style.yml` runs `build-site-i18n.py --check`; `ci.yml` skips the app build for `site/**` | |
| Writers into `site/` | `release.yml`, `point-apk-redirect.py`, `write-latest-json.py`, `annotate-shot.py`, `render-brand.sh`, `promo-art.py`, `og-card.html`, `devices-card.html` | |

## Target layout

The Astro project lives **in `site/` itself**, so the Vercel project's root directory does not change,
and `site/i18n/` stays where it is so translator links and habits keep working.

```
site/
  astro.config.mjs        site, trailingSlash 'always', build.format 'directory', compressHTML false
  package.json            exact pinned versions, "packageManager": "pnpm@11.x", engines.node
  pnpm-lock.yaml
  tsconfig.json
  vercel.json             unchanged rules + "framework": "astro", buildCommand, outputDirectory "dist"
  i18n/<tag>.json         unchanged files, unchanged keys; en.json is now hand-edited source
  public/                 copied byte for byte to dist/
    latest.json install.ps1 af11d0ad...txt robots.txt llms.txt .well-known/ og.png icon-512.png
    favicon.svg logo.svg site.webmanifest badges/ fonts/ screenshots/
  src/
    i18n/langs.ts         LANGS, ENGLISH, PUBLISH_THRESHOLD, PAGES, ENGLISH_ONLY (moved from the script)
    i18n/t.ts             t(lang, key, vars), English fallback per key, coverage(), publishedLangs()
    i18n/links.ts         l(lang, path): localized path if the page is localized, English otherwise
    lib/seo.ts            breadcrumb(), jsonLd(), absolute URLs, hreflang list
    lib/releases.ts       reads src/data/releases.json, sorting, summary(), markdown to HTML
    data/releases.json    written by the release job (replaces site/changelog/** in git)
    data/lastmod.json     page id -> real lastmod date (sitemap)
    layouts/Base.astro    <html lang dir>, the whole <head> from props, Header, Notice, Footer, scripts
    components/           Header, Footer, LangPicker, KaoNotice, SuggestBar, StarCount, T,
                          Band, Wave, Hero, PageHead, Shot, ShotPair, Icon, SupportCard, SupportSlim,
                          StarCard, Steps, FaqItem, DownloadCard, Breadcrumbs, ...
    icons/*.svg           every inline SVG once, used through <Icon name>
    styles/styles.css     today's file, imported by Base (hashed under /_astro/)
    scripts/app.js        today's file, loaded through Astro (hashed under /_astro/)
    pages/
      [...lang]/index.astro                 home, English at / and 20 languages
      [...lang]/features/index.astro        ... one file per localized page (12)
      [...lang]/guides/<slug>/index.astro
      legal/ privacy/ security/ signing/ support/   English only
      changelog/index.astro  changelog/[version]/index.astro  changelog/feed.xml.ts
      sitemap.xml.ts  404.astro
```

Decisions, each one made so output stays identical:

- **Own i18n routing, not Astro's `i18n` config.** Astro's router adds redirects and fallback rules
  we do not have today. `[...lang]` with `getStaticPaths()` returns `{lang: undefined}` for English
  and one entry per *published* language (coverage >= 0.95, same as today), using the URL prefixes
  in `LANGS` (`/zh-tw/` for `zh-TW`, `/pt/` for `pt-BR`, `/es/` for `es-419`).
- **`<T k="home.quiet">` keeps the `{a1}` placeholder format**, so no catalog changes and no
  retranslation: `<T k="home.quiet"><a slot="a1" href={l(lang,'/install/')}><T k="home.quiet__a1"/></a></T>`.
  It splits the string on `{name}` and drops in `Astro.slots.render(name)` or `vars[name]`.
- **Keys stay exactly as they are** (`home.lede`, `build.kao_bar`, `js.*`, the JSON-LD keys). The
  converter (AM03) writes them into the `.astro` files, so the catalogs are never touched by the
  migration.
- **Custom `sitemap.xml.ts`, not `@astrojs/sitemap`**, which writes `sitemap-index.xml` +
  `sitemap-0.xml` and would move the URL Search Console knows. Same entries, same `lastmod`s,
  same hreflang alternates.
- **`compressHTML: false`** while proving parity; switched on only after cutover, as its own
  commit with its own parity run, or left off.
- **`styles.css` and `app.js` go through Astro** so they get content-hashed names under `/_astro/`,
  which the CLAUDE.md caching rule has wanted ("do not raise it without adding a content hash").
  `/_astro/*` gets `max-age=31536000, immutable`. The old `/styles.css` and `/app.js` stay in
  `public/` for one release cycle, because cached HTML from before cutover still points at them.
  They come out in AM14.
- **The feed stays a hand-written XML endpoint** that copies the current `feed()` output format,
  rather than `@astrojs/rss`, so feed readers see the same GUIDs and no items come back as new.
- **The release data step stays Python** (`scripts/release-data.py`, cut down from
  `build-changelog.py`: `load_releases`, `tag_message`, `clean_notes`, `collect`). It writes only
  `site/src/data/releases.json`. Markdown to HTML moves to `lib/releases.ts`, a port of the
  script's `inline()`/`markdown()` (code, links, bold, lists) so the HTML matches it exactly. No
  general markdown library, because it would render differently.

## How the work runs

- **Branch:** `astro` on a worktree (`git worktree add ../TMPlayer-astro -b astro`). `main` keeps
  deploying the old site untouched until AM12. Vercel builds a preview of every push to `astro`,
  which is the staging site.
- **Parallel agents** in Waves 1 and 2, each in its own worktree branched off `astro`, each owning a
  disjoint set of files (listed per AM). Shared files (`Base.astro`, `src/components/*`,
  `src/i18n/*`, `lib/*`, `styles.css`, catalogs) are **read-only for wave agents**. An agent that
  needs a new shared piece builds it page-local under `src/components/<page>/` and names it in its
  report. AM08 promotes duplicates. That keeps merges conflict-free.
- **Python and coreutils over hand edits:** checks and bulk changes are Python stdlib scripts and
  shell pipelines, run in parallel (`ThreadPoolExecutor`, `xargs -P"$(nproc)"`): `grep -o`, `sed -i`, `perl -pi`, `jq`, `sort | uniq -c`, `comm`, `diff
  <(...) <(...)`, `find dist -name index.html`, `xargs -P"$(nproc)"`, `curl -sI` loops,
  `magick compare`. Commands for each step are written out below.
- **Each AM ends** with `scripts/site-review.sh` (local build, original and new served side by
  side, SEO, OG, sitemap, feed, locales, headers, screenshots) for what it touched, the dash grep
  from CLAUDE.md, a commit on its branch, and a report of five lines at most.
- **Timing:** AM00 to AM11 can happen any day; they never touch `main`. **AM12 (cutover) must not
  fall on a release day** and needs the release cadence window clear (next release not before
  2026-10-21). The `release.yml` change ships in the same merge as the cutover.

## Checkpoints

### Wave 0, serial (one session, me): the foundation everything else stands on

#### AM00 Baseline snapshot
- Freeze the current site as the reference: `git worktree add ../TMPlayer-baseline f71701c` (or the
  newest `main` on the day AM01 starts; record the hash here).
- Serve it locally the way Vercel does: `npx serve@14 --config` with a `serve.json` mirroring
  `cleanUrls`/`trailingSlash`, or simply read files from disk in the harness.
- Write the URL inventory, which is the contract:
  ```sh
  cd ../TMPlayer-baseline/site
  find . -name index.html | sed 's|^\.||; s|index\.html$||' | sort > $S/urls-baseline.txt   # $S = scratchpad
  grep -o '<loc>[^<]*' sitemap.xml | sed 's/<loc>//' | sort > $S/sitemap-baseline.txt
  find . -type f ! -name '*.html' ! -path './i18n/*' | sort > $S/static-baseline.txt
  sha256sum $(cat $S/static-baseline.txt) > $S/static-baseline.sha
  ```
- Record live production headers for every kind of URL (page, redirect, `/latest.json`,
  `/install.ps1`, font, screenshot, `/apk`, `/privacy` without slash, a 404):
  `while read u; do echo "== $u"; curl -sSI "https://tmplayer.org$u"; done < probe.txt > $S/headers-prod.txt`
- **Done when** the four files exist and the counts match: 267 HTML pages, every sitemap `<loc>`
  present as a file.

#### AM01 Scaffold
- In the `astro` worktree: `pnpm create astro@<pinned> site-new --template minimal --no-git
  --no-install --typescript strict`, then move the project files into `site/` beside the old
  files, so nothing old is deleted yet. Pin exact versions (`astro`, `@astrojs/check`, `typescript`); checks need
  nothing from npm (AM02 is Python).
- `git mv` every static file into `site/public/` (list from `static-baseline.txt`, minus
  `styles.css`, `app.js`, `vercel.json`, `sitemap.xml`, `changelog/`). Use `git mv` so history follows.
- `astro.config.mjs`: `site: 'https://tmplayer.org'`, `trailingSlash: 'always'`,
  `build: { format: 'directory' }`, `compressHTML: false`, `outDir: 'dist'`.
- Add `site/dist/`, `site/node_modules/`, `site/.astro/` to `.gitignore`.
- `vercel.json`: add `"framework": "astro"`, `"installCommand": "pnpm install --frozen-lockfile"`,
  `"buildCommand": "pnpm build"`, `"outputDirectory": "dist"`. Every existing rule stays byte
  for byte. Add `/_astro/(.*)` immutable.
- **Done when** `pnpm -C site build` works and `diff <(cd site/public && find . -type f | sort)
  <(cd site/dist && find . -type f ! -name '*.html' | sort)` shows only the expected additions.

#### AM02 Parity harness and local review server (the safety net; written before any page is ported)
All tooling is **Python standard library plus coreutils**, no new Node dependencies for checking:
the old build's own parser (`html.parser` in `build-site-i18n.py`) already reads this markup, and
Python with `concurrent.futures` over 267 small files finishes in a couple of seconds.

**`scripts/serve-vercel.py <dir> --port N`**: a local server that behaves like Vercel for this
site, so the original and the new build can be reviewed side by side on localhost:
- reads `site/vercel.json` and applies `cleanUrls` and `trailingSlash` (`/privacy` gives 308 to
  `/privacy/`, `/x.html` gives 308 to `/x/`), `redirects` (`/apk`), and every `headers` rule by its
  `source` pattern (CORS on `latest.json`, `text/plain` on `install.ps1`, cache lines);
- serves `404.html` with status 404 for anything missing;
- `http.server.ThreadingHTTPServer`, about 80 lines.
Run both: `serve-vercel.py ../TMPlayer-baseline/site --port 4001 &` (original, as committed) and
`serve-vercel.py site/dist --port 4002 &` (new build), using the new tree's `vercel.json` for 4002.

**`scripts/site-parity.py <baseURL|dir> <newURL|dir> [--page id] [--lang path] [--jobs N]`**,
fetching every URL in `urls-baseline.txt` from both, in parallel (`ThreadPoolExecutor`). It fails
on any difference in:
1. **URL set:** a missing or extra page in either `find` output or the sitemap (also `404.html`
   and its 404 status).
2. **SEO head:** `<html lang dir>`, `<title>`, `description`, `robots`, `author`, `canonical`
   (must equal the page's own trailing-slash URL), every `hreflang` alternate + `x-default` (the
   set must be identical and **reciprocal**: each alternate links back), icons, manifest,
   `preconnect`s. Every `<meta>` and `<link>` is compared as a sorted set of attribute tuples.
3. **Open Graph and Twitter:** `og:type`, `og:site_name`, `og:title`, `og:description`, `og:url`
   (= canonical), `og:image` + `:type` + `:width` + `:height` + `:alt`, `og:locale` + alternates,
   the four `twitter:` tags; and each `og:image` URL answers 200 with the stated type, and its
   pixel size (`identify -format '%w %h'`) matches the declared width and height.
4. **JSON-LD:** each block parsed with `json` and deep-compared, key order ignored; every page has
   a `BreadcrumbList`; types per page unchanged (Organization on home, FAQPage, BlogPosting...).
5. **Locales:** per language, the visible text of every page equals the baseline; no English
   fallback appears where the baseline had a translation (per text node, compared against
   `en.json` values); `lang`/`dir` right for `ar` and `fa`; the picker lists the same languages in the
   same order with the same links; the `tm-langs` JSON is deep-equal; unpublished languages
   (under 95 per cent) still produce no pages.
6. **Body:** the sequence of visible text nodes, whitespace collapsed, per element path (tag +
   class); every `href`, `src`, `srcset`, `alt`, `width`, `height`, `loading`, `decoding`, `aria-*`,
   `data-*` (minus the removed `data-i18n*`), `id`, `class`, in document order; exactly one `<h1>`.
7. **Scripts and styles:** the same set after mapping hashed `/_astro/` names back to
   `styles.css`/`app.js` by content (`sha256sum` of the served bodies).

It prints `url  check  expected  got` as TSV (so `cut`, `sort | uniq -c` and `grep` summarise a
run), exits non-zero on any diff, and accepts `allowed-diffs.json`, where every entry carries a
reason. The expected entries are: `data-i18n` attributes gone, asset URLs hashed, the "generated
by" comments gone.

**`scripts/site-parity-xml.py`**: `sitemap.xml` and `changelog/feed.xml` from both servers,
parsed with `xml.etree`. It checks that the `<url>` sets are equal, each `<loc>`, `<lastmod>` and
`xhtml:link` alternate matches, every `<loc>` answers 200 on the new server and is its own
canonical, the feed `<item>` sets are equal by GUID with the same title, link, date and
description, and `robots.txt` still names the sitemap.

**`scripts/site-parity-static.sh`**: `sha256sum -c static-baseline.sha` against `site/public/` and
`site/dist/`, plus the headers of the AM00 probe list from both local servers:
`diff <(probe 4001) <(probe 4002)` with date and etag stripped (`sed -E '/^(date|etag|age):/Id'`).

**`scripts/site-shots.sh`**: headless Chromium (`chromium-browser --headless=new --screenshot`)
of both servers for a page list at 390x844 and 1440x900, light and dark, fanned out with
`xargs -P"$(nproc)"`, then `magick compare -metric AE -fuzz 2%` per pair. It fails above a small
pixel count and writes a diff PNG for each failure into the scratchpad. Pages: all 21 English, then `ar`
and `fa` (RTL), `zh`, `ja`, `hi`, `ml` (scripts and font fallbacks), `de` (long words).

**`scripts/site-review.sh`**, the one command every checkpoint runs: build (`pnpm -C site build`),
start both servers, run parity, XML, static and shots, stop the servers, and print one summary
line per check. It must pass before any commit from AM04 on.

**Self-test:** baseline against baseline gives 0 diffs. A copy with one word, one `og:image`, one
hreflang and one sitemap `lastmod` changed gives exactly those 4. **Done when** both pass.

#### AM03 The converter (does 90 per cent of the porting mechanically)
`scripts/html-to-astro.py`, a one-off reusing `build-site-i18n.py`'s parser (`segments`,
`is_unit`, `template`, `flatten_unit`, `attr_targets`, `ld_strings`) so it understands the
markup exactly as the old build did:
- `<p data-i18n="k">English</p>` becomes `<p><T k="k"/></p>` (or `{t('k')}` when plain text).
- Inline placeholders become `<T k="k">` with `slot="a1"` children, links rewritten to
  `href={l(lang, '/install/')}`.
- `data-i18n-alt|content|title|aria-label|placeholder` become `alt={t('k')}` and so on.
- `<!-- i18n:region -->` blocks are dropped (the layout renders them).
- `<head>` is reduced to props for `Base` (title key, description key, canonical path, og type,
  image, JSON-LD objects with their translatable strings as keys).
- Inline SVGs are hashed; each distinct one is written to `src/icons/<name>.svg` once and replaced
  by `<Icon name>` (names from `aria-label`/class, or the agent names them in Wave 1).
- Output is `src/pages/.../index.astro` with the right `[...lang]` `getStaticPaths()` stub.
- **Done when** it converts every page with no unconverted `data-i18n` left
  (`grep -rl 'data-i18n' site/src` empty) and its output builds, even if the result is ugly.

#### AM04 Layout, i18n library and shared chrome
- `src/i18n/langs.ts`, `t.ts`, `links.ts` ported from the script (`LANGS`, `PAGES`,
  `ENGLISH_ONLY`, `BUILD_STRINGS` moved into `en.json` under the same `build.*` keys,
  `PUBLISH_THRESHOLD`, `coverage`, `page_url`, `localize_links`, `nest`/`unnest`).
- `Base.astro` renders exactly what `render()` + `regions()` + `notice()` produce: head tags,
  hreflang + `x-default`, `og:locale` + alternates, localized JSON-LD (`translate_ld`, `ld_url`),
  `<script id="tm-langs">` with the page's `js.*` strings, header picker, footer picker, language
  count, the Keep Android Open bar, the suggest bar strings.
- `Header`, `Footer`, `LangPicker`, `KaoNotice`, `T`, `Icon`, `SupportCard`, `SupportSlim`, `Band`,
  `Wave`, `Shot`, `Breadcrumbs`.
- Port **home in English and Spanish** end to end as the pattern every later page follows.
- **Done when** `scripts/site-review.sh --page home` is clean for `/`, `/es/` and `/ar/`
  (SEO, OG, JSON-LD, locales, screenshots).

### Wave 1, parallel (up to 6 agents, one worktree each, off `astro` after AM04)

Each agent: run the converter output for its pages, then hand-tidy it into readable components
(page-local in `src/components/<page>/`), using the shared ones from AM04. **Must not edit** shared
files or catalogs. Loop `pnpm -C site build && scripts/site-parity.py http://localhost:4001 http://localhost:4002 --page <id>` over all 21
languages until clean, then `site-review.sh --page <id>` for its pages. Commit, report the page-local components
that look reusable.

| AM | Agent owns | Watch for |
|---|---|---|
| AM05a | `features` | numbered tour markers matched to the numbered list; light/dark shot pairs |
| AM05b | `download`, `install` | GitHub API `preconnect`s, OS detection in app.js, checksum note, the Mac card, star card + support block |
| AM05c | `android-tv`, `fire-tv`, `formats`, `faq` | FAQPage JSON-LD on faq, `<details>` behaviour |
| AM05d | the three `guides/*` | HowTo/Article JSON-LD, guide breadcrumbs |
| AM05e | `translate`, `legal`, `privacy`, `security`, `signing`, `support`, `404` | English-only pages still get the notice bar; 404 stays `noindex`, no canonical; translate's links to `tree/main/site/i18n` stay valid |
| AM05f | `changelog/`, `changelog/[version]/`, `feed.xml.ts`, `sitemap.xml.ts`, `scripts/release-data.py`, `lib/releases.ts` | BlogPosting JSON-LD, prev/next links, `FIRST_MAC` note, feed GUIDs, release `<url>` entries; generate `releases.json` once from the API (`gh` is authenticated) and commit it |

### Wave 2, merge and harden (serial, then two parallel agents)

#### AM06 Merge Wave 1
- Merge each branch into `astro` in table order (`git merge --no-ff`), rebuilding after each.
- Full run: `scripts/site-review.sh` across **all 267 URLs** (parity, sitemap and feed, static
  files and headers, screenshots). **Done when** all three come back clean except the
  `allowed-diffs.json` entries.

#### AM07 Behaviour check of app.js (agent A)
In headless Chromium on both local servers (`chromium-browser --headless=new --dump-dom` for
states reachable by URL or cookie; a short Python DevTools script over `--remote-debugging-port`
for clicks), on baseline and new, compare: the language suggest bar
(set `Accept-Language: es`), its dismissal memory, the KAO bar close memory, the theme toggle
and saved theme, OS detection on the download page (user agent overrides for Android, Windows, Mac,
Linux), the GitHub star count, the copy buttons, `<details>` and menus, the lightbox or zoom on
screenshots, and no console errors on any page
(`--enable-logging=stderr --v=0 2>&1 | grep -i 'console'`).

#### AM08 Componentize and tidy (agent B, after AM06)
- Find duplicated page-local components: `find src/components -name '*.astro' -exec md5sum {} + |
  sort | uniq -w32 -D`, and near-duplicates by eye. Promote them to `src/components/`.
- Every `<img>` through `Shot` (enforces `alt`, `width`, `height`, `loading`/`decoding` below the
  fold), every inline SVG through `Icon`, every band through `Band`.
- Delete unused CSS? **No.** `styles.css` moves as it is. Splitting it per component is optional
  later work, outside this migration.
- Rerun `site-review.sh`. **Done when** clean and `src/pages/**` files read as page outlines.

#### AM09 Checks that replace `build-site-i18n.py --check`
`scripts/site-i18n-check.py`, run in CI:
- every key used in `src/` (`grep -rhoP "(?<=k=\")[^\"]+|(?<=t\\(')[^']+" src | sort -u`) exists in
  `en.json`; every `en.json` key is used (`comm -13` of the two lists), so nothing goes stale;
- every catalog's placeholders match English per key (`{a1}`, `{count}`, `{language}`);
- no catalog has keys English lacks;
- `--status` prints coverage per language (same output as today's `--status`);
- the `js.*` keys match the `tmT('key', 'English')` calls in `app.js`, and the English text in the
  call equals `en.json` (as the old extractor guaranteed).

`scripts/site-links.py`: every internal `href`/`src` in `dist/` resolves to a file in `dist/`, and
every localized page links to localized pages where they exist (as `localize_links` did).

### Wave 3, wiring and cutover

#### AM10 Scripts, CI and release job (prepared on `astro`, live only at AM12)
- `release.yml` "Publish the update feed and the changelog": write `site/public/latest.json`, run
  `scripts/release-data.py`, `git add site/public/latest.json site/vercel.json
  site/src/data/releases.json`. Update the comment ("no build step" is no longer true: Vercel builds
  the commit). The Telegram step reads `site/public/latest.json`. The IndexNow URLs stay the same.
- `point-apk-redirect.py` keeps `site/vercel.json` (path unchanged). `write-latest-json.py` output
  path changes. Check with `grep -rn "site/" scripts .github` that every path still exists:
  `grep -rhoE "site/[A-Za-z0-9_./-]+" scripts .github | sort -u | while read p; do [ -e "$p" ] || echo "MISSING $p"; done`.
- `annotate-shot.py` site output to `site/public/screenshots/`. `render-brand.sh` and
  `promo-art.py` read and write under `site/public/`. Same for `og-card.html` and
  `devices-card.html` font paths.
- `style.yml` `site-i18n` job becomes `site`: `pnpm/action-setup`, `actions/setup-node` with the
  pinned Node, `pnpm -C site install --frozen-lockfile`, `pnpm -C site astro check`, `pnpm -C site
  build`, `python3 scripts/site-i18n-check.py`, `python3 scripts/site-links.py`, `scripts/site-parity-xml.py` against `dist`, the dash grep.
  `ci.yml` path filters stay `site/**`.
- Dry run of the release step on the branch: run the exact shell from the step locally with a fake
  `$RUNNER_TEMP/latest.json` and `VERSION=1.26.0`; it must commit nothing (same bytes).
- **Done when** a CI run on the `astro` branch is green and the dry run is a no-op.

#### AM11 Preview verification (staging = the Vercel preview of `astro`)
Against the preview URL (`$P`), compared with production:
```sh
# every page answers 200, with the same headers that matter
sed "s|^|$P|" $S/urls-baseline.txt | xargs -P16 -I{} curl -so /dev/null -w '%{http_code} {}\n' {} | grep -v '^200'
# redirects: no slash -> slash (308), /apk -> the APK, unknown -> 404 page with 404 status
for u in /privacy /download /es/features /apk /nope/; do curl -sI "$P$u" | sed -n '1p;/^location/Ip'; done
# app-facing files: content-type, cache, CORS, bytes
for u in /latest.json /install.ps1 /af11d0ad703ff8c5a7006d0ccd768528.txt /robots.txt /sitemap.xml /changelog/feed.xml; do
  diff <(curl -s "https://tmplayer.org$u" | sha256sum) <(curl -s "$P$u" | sha256sum) >/dev/null || echo "BODY $u"
  curl -sI "$P$u" | grep -iE 'content-type|cache-control|access-control'; done
```
- Header diff for every probe from AM00: `diff headers-prod.txt headers-preview.txt` after
  stripping date, etag, age and `x-vercel-id`.
- `site-parity.py https://tmplayer.org $P` and `site-parity-xml.py https://tmplayer.org $P`:
  production against the preview, so Vercel's real handling (cleanUrls, 404, headers) is covered,
  not only the local server's imitation of it.
- The desktop app pointed at the preview feed once (`UpdateFeed` URL override if one exists,
  otherwise skip; the file's bytes are already proven identical).
- Rich results test and a mobile Lighthouse run on `/`, `/download/` and `/es/` (Lighthouse CLI with
  local Chromium); scores no lower than production.
- **Done when** every check is clean. List anything left in `allowed-diffs.json` in the report and
  get the user's sign-off before AM12.

#### AM12 Cutover (needs the user's go, not on a release day)
1. Confirm no release is running: `gh run list --workflow release.yml --limit 3`.
2. `git rm -r` the generated output: `site/<lang>/` for all 20 prefixes, `site/changelog/`, the old
   `site/**/index.html` sources (now `.astro`), `site/sitemap.xml`. Keep `site/public/styles.css`
   and `site/public/app.js` copies for cached HTML (removed in AM14).
3. Merge `astro` into `main` with `--no-ff`, push. Vercel builds main; watch the deployment
   (`vercel inspect` or the dashboard) until it is Ready.
4. Rerun the AM11 checks with `site-parity.py http://localhost:4001 https://tmplayer.org`
   (baseline served locally against new production) and `site-parity-xml.py` the same way.
5. **Rollback, if anything is off:** Vercel dashboard "Instant Rollback" to the previous
   production deployment (seconds), then `git revert -m 1 <merge>` and push. The old files come back
   whole, because nothing old was edited, only removed in the merge.

#### AM13 Search engines and feeds after cutover (day 1 to day 7)
- Search Console and Bing Webmaster: resubmit `/sitemap.xml`, check that coverage holds over the week.
- IndexNow ping for `/`, `/download/`, `/changelog/` (same curl as `release.yml`).
- Validate the feed (W3C validator) and check that a reader does not show old items as new.
- Vercel Analytics: no rise in 404s (`/_astro/` misses, old asset URLs).

#### AM14 Cleanup and docs
- Keep `serve-vercel.py`, `site-parity*.py`, `site-review.sh` (useful for any later site change,
  against the previous production). Delete `scripts/build-site-i18n.py`, `scripts/build-changelog.py` (kept as `release-data.py`),
  `scripts/html-to-astro.py`, `public/styles.css` and `public/app.js` (after one week of cached HTML).
- Rewrite CLAUDE.md "Screenshots" (new path `site/public/screenshots/`), "Onboarding pictures" (output
  path) and "Site SEO" for the new setup: the head checklist becomes "pass these props to `Base`",
  new page = `.astro` file + `PAGES` entry + `lastmod.json` entry, "run `pnpm -C site build` and
  `i18n-check`", changelog = `releases.json`, caching for `/_astro/`. Same for `docs/RELEASING.md`
  and any `docs/` mentions (`grep -rn 'build-site-i18n\|build-changelog' --exclude-dir=.git .`).
- `site/README.md`: how to run (`pnpm -C site dev`), add a page, add a string, add a language.
- `llms.txt`: content unchanged; only its path moved.
- Branch tidy per CLAUDE.md: `git worktree remove` the `astro`, wave and baseline worktrees,
  `git branch -d`, delete the pushed `astro` branch on GitHub, `git fetch --prune`.
- **Done when** `grep -rn 'build-site-i18n\|build-changelog' --exclude-dir=.git .` is empty
  apart from history, and only `main` is left.

## Everyday work after the move

| Task | Before | After |
|---|---|---|
| Change header or footer | edit 21 pages, run the i18n build, commit 267 files | edit `Header.astro` |
| New page | copy a page, fill the head checklist by hand, add to `PAGES`, sitemap, rebuild | new `.astro` with `<Base title=… description=… crumbs=…>`, add to `PAGES` and `lastmod.json` |
| New string | write English in HTML, run the extractor, translate | add the key to `en.json`, use `<T k>`, translate |
| See a change | open the file in a browser | `pnpm -C site dev` with hot reload, every language at its URL |
| Release | job regenerates HTML and commits it | job commits `releases.json`, Vercel renders it |

## Risks and how each is covered

| Risk | Cover |
|---|---|
| A page, string, SEO/OG tag, sitemap entry or locale silently differs | AM02 local review (original vs new, both served Vercel-style) on all 267 URLs, plus screenshots, before cutover and again on the preview and on production |
| Installed apps stop seeing updates | `latest.json` byte-identical with the same headers (AM11), release job dry run (AM10) |
| `/apk` or `install.ps1` breaks | probed in AM00, AM11 and AM12, path of `vercel.json` unchanged |
| Search ranking dip | same URLs, canonicals, hreflang and sitemap URL; resubmit and watch for a week (AM13) |
| Feed readers flood with "new" items | feed written in the same format with the same GUIDs, compared as XML in AM02 |
| Vercel build fails on main | the preview built the same commit in AM11; Instant Rollback in seconds |
| Release during the switch | cutover off release days, gated on `gh run list`; the release job change is in the same merge |
| Translator links break | `site/i18n/` stays put |
| Old cached HTML asks for `/styles.css` | the copies stay in `public/` for a week |
| Agents conflict | disjoint file ownership, shared files read-only in waves |

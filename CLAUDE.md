# Working on TMPlayer

## Screenshots

Every screenshot in the repository is a WebP, and stays one. A screenshot off the stick arrives as
a 1920x1080 PNG somewhere north of half a megabyte, which is a page weight the site does not need
and a blob nobody wants in the git history.

When a screenshot changes, or a new one is added, convert it before committing:

```sh
magick shot.png -define webp:method=6 -define webp:sharp-yuv=true -quality 88 docs/screenshots/name.webp
```

Quality 88 is the settled figure: it holds around 39 to 44 dB PSNR on this app's screens, so the
small text in the TV UI stays sharp at 1:1, while the file lands 60 to 90 per cent smaller than the
PNG. Do not commit the PNG alongside it, and do not lower the quality to save a few more kilobytes.

Where they live and who reads them:

- `docs/screenshots/` is the README. GitHub renders WebP.
- `site/public/screenshots/` is the site, referenced from the pages under `site/src/pages/`, the
  sitemap's image entries in `site/src/data/lastmod.json` and `site/public/site.webmanifest`.
  Changing a file name means changing all three (`grep -rn` the old name under `site/src`), and the
  manifest carries a `"type": "image/webp"` next to each one. Phones are sent smaller copies
  (`<name>-<width>w.webp`, made from the original with the same encoding, the original never
  touched): after adding or retaking a site screenshot, run
  `python3 scripts/site-shot-variants.py <name>` (`--check` lists missing copies).

Two images are deliberately still PNG. `site/public/og.png` is the social preview, and the crawlers that
fetch it (WhatsApp and LinkedIn among them) cannot be relied on to decode WebP, so a link would
arrive with no picture at all. `site/public/icon-512.png` is the PWA icon, where PNG is what installers
expect. Both are small. Leave them as they are.

## Onboarding pictures

The setup's answers and the quick tour's pages (CP67, CP69) show cropped, marked screenshots of the
promo build, made like the site's: one spec each in `scripts/annotations/` (`setup-android-*`,
`setup-desktop-*`, `tour-android-<page>[-dark|-tv]`, `tour-desktop-<page>-light|dark`), drawn by
`scripts/annotate-shot.py` into `app/src/main/res/drawable-nodpi/` and
`ui/src/jvmMain/resources/onboarding/`. A tour spec marks each thing its page talks about with one
`circle`, in the order of the page's numbered lines; the app draws the numbers over the picture, so
no number or word goes into an image. After retaking or re-marking a tour shot, run
`scripts/tour-badges.py`, which moves the numbers to match (`--check` says whether it is due).

## Writing

No em dashes or en dashes, anywhere: comments, KDoc, commit messages, UI strings, site copy. Write
the sentence with a colon, a comma or a full stop instead. Before committing:

```sh
grep -rInP '\x{2014}|\x{2013}' --exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle .
```

## Site SEO

tmplayer.org is an Astro site in `site/` (`site/README.md` says how to run it, add a page, a
string or a language). Every rule below exists so a new page or a release reaches search engines
the same way the existing ones do. Check them whenever a page under `site/src/` is added or
changed.

- **URLs end in a slash.** `vercel.json` sets `trailingSlash` and `cleanUrls`, and Astro builds
  with `trailingSlash: 'always'`, so `/privacy` redirects to `/privacy/`. Canonicals, `og:url`,
  sitemap `<loc>`s and internal links all use the slash form; a canonical that points at a
  redirect is ignored by Google. The one exception is a URL whose last segment has a dot: Vercel
  takes it for a file and answers `/changelog/1.26.0/` with a 308 to `/changelog/1.26.0`, so the
  release pages are named without the slash everywhere (`releaseUrl()` in `site/src/lib/releases.ts`).
- **Every page's head** comes from `site/src/layouts/Base.astro`, which writes the canonical,
  `robots`, `author`, the Open Graph tags with the image's type, width and height, `og:locale`,
  the four `twitter:` tags, the icons, the manifest and, on localized pages, the hreflang
  alternates. A page passes it a unique `title` (under about 60 characters, no "X - TMPlayer"
  where X already says TMPlayer) and a `description` of roughly 120 to 160 characters, plus its
  page id or path, and puts JSON-LD with at least a `BreadcrumbList` in the `head` slot.
  `site/src/pages/support/index.astro` is the smallest complete example. The 404 page is the one
  that is `noindex` and has no canonical (`notFound`).
- **One `<h1>` per page**; every `<img>` has `alt`, `width`, `height`, and below the fold
  `loading="lazy" decoding="async"`.
- **Strings.** Text a reader sees comes from `site/i18n/en.json` by key: `{t('key')}`, or
  `<T k="key">` with named slots for the markup inside a sentence. A new string is a new key in
  `en.json`; the other catalogs are translated with the same keys and placeholders. English-only
  pages (legal, privacy, security, signing, support, changelog) keep literal English.
- **Sitemap.** `site/src/pages/sitemap.xml.ts` writes it: English pages from
  `site/src/data/lastmod.json`, each with a real `lastmod` (the date of its last content change,
  not today by default), localized copies and hreflang alternates for every published language,
  and the releases from `releases.json`. A new indexable page gets an entry in `lastmod.json`.
- **Releases.** The release job runs `scripts/release-data.py`, which writes
  `site/src/data/releases.json`; the site renders `/changelog/`, the RSS feed, one page per
  release at `/changelog/<version>` (BlogPosting JSON-LD) and the sitemap's release entries from
  it. Do not hand-edit `releases.json`; rerun the script. Release notes are the tag message, so a
  good tag message is the SEO text for that release.
- **Caching** lives in `site/vercel.json`. Fonts are immutable (their names never change content),
  and so is `/_astro/`, where the stylesheet and `app.js` are served under content-hashed names
  (`site/src/styles/styles.css`, `site/src/scripts/app.js`). Screenshots and badges keep their
  names when retaken, so `Shot.astro` writes every one as `<file>?v=<content hash>`
  (`site/src/lib/asset-version.ts`) and `vercel.json` caches a URL with `?v=` for a year as
  immutable; the plain URL (sitemap image entries, the manifest) keeps the one day cache. Put a
  new page image through `Shot`, never a bare `<img>`, or it misses both. Images, the manifest,
  the sitemap and the feed have their own entries; a new top-level asset type gets one too.
- **Feature screenshots with markers.** A shot that points at one thing is cropped to the part
  that matters and zoomed, and its hand-drawn arrows, circles and underlines are drawn into the
  image by `scripts/annotate-shot.py` from a spec in `scripts/annotations/`, so the site and the
  app's onboarding share one look and a retaken shot is re-marked with one command (user,
  2026-10-14). No words go into an image: labels stay HTML with catalog keys (or app strings) so
  they get translated. The feature tour on `/features/` keeps its numbered HTML markers matched
  by a numbered list. Light and dark shots stay paired. Only demo-content screenshots are used,
  never a real account.
- **Files for crawlers and machines.** `site/public/llms.txt` is a hand-written summary of the
  site for AI assistants: when a page is added, renamed or its claims change, update it too. The
  home page carries `Organization` JSON-LD (logo, `sameAs` profiles); a new official profile goes
  there. `site/public/.well-known/security.txt` expires on the date in it, so renew it before
  then. `site/public/<key>.txt` is the IndexNow key the release job and the IndexNow workflow
  ping with; do not delete it. The workflow pings on every push to `main` under `site/`, once
  Vercel has deployed it, with the pages whose `lastmod` changed, so move it with the content.
  Every English page has a Markdown twin (`/download/index.md`, `/changelog/1.26.0.md`) and
  `/llms-full.txt` joins them; both are written from the built pages by
  `site/integrations/markdown-twins.mjs`, so never write or edit one by hand. `robots.txt` names
  the AI crawlers only to allow them, training ones included (user, 2026-10-15).
  `site/public/latest.json` is the installed apps' update feed: only the release job writes it.
- **Download page** keeps its `preconnect` hints to GitHub's API, github.com and
  `release-assets.githubusercontent.com`, the host the release files are served from.
- **Sponsoring.** Every content page ends with the "Support TMPlayer" offer: inside the page's
  star card where it has one, otherwise as a `support-slim` card in the last band. Use the shared
  `SponsorOffer` component, which carries the `home.support_tmplayer`, `home.support_no_ads`,
  `home.sponsor_on_github` and `home.buy_me_a_coffee` keys so it arrives translated; a new page
  gets it too. Its line about ads (`home.support_no_ads`) sits behind an info button beside the
  heading, a native `<details>` so the words stay in the HTML (indexed, in the Markdown twins,
  keyboard and screen reader friendly, no script needed); the release cards and the support page,
  where those words are the subject, keep them in the open (user, 2026-10-18). Buy Me a Coffee is the primary way to chip in (user, 2026-10-18): wherever it
  stands beside GitHub Sponsors (the offer, the support page, the footers, the QR codes) it is the
  larger button in Buy Me a Coffee's own brand (`CoffeeButton`: their yellow `#FFDD00`, their
  near-black text and their official cup from `site/public/brand/`, never redrawn or recoloured),
  and GitHub Sponsors is the quieter link under it. The links live in `site/src/lib/support.ts`;
  the page is `buymeacoffee.com/nevil.dev` (`/nevil` is somebody else). Every page but download,
  install, support and the 404 also has the floating `CoffeeCard`: a pill on a phone, a card on a
  wider screen, at the bottom of the start edge, shown by app.js once the page is idle, stepping
  aside for the footer, the page's own offer and the phone's download bar. The card is one link
  straight to the Buy Me a Coffee page in a new tab, with no scan code on it and no dialog (user,
  2026-10-18); the QR codes stay on the support page and the star cards. Its x is a separate
  button that hides it for that page view only and nothing is stored, so it is back on the next
  page or a reload (the user's choice). It stays an offer: no popup that opens by itself, no nag,
  nothing locked.
- **After changing the site**, run `pnpm -C site build`, `python3 scripts/site-i18n-check.py` and
  `python3 scripts/site-links.py` (CI runs all three).

## Site performance

Lighthouse runs locally against headless Chromium (never Google Chrome), on the home page,
`/features/` and `/download/`, mobile and desktop. Against the live site, and against a local
build served by `pnpm -C site preview` (the preview server sends none of `vercel.json`'s headers,
so the cache audit is only meaningful live):

```sh
export CHROME_PATH=/usr/bin/chromium-browser
for page in "" features/ download/; do
  npx -y lighthouse@12.8.2 "https://tmplayer.org/$page" --quiet \
    --chrome-flags="--headless=new --no-sandbox --disable-gpu" \
    --form-factor=mobile --output=json --output-path="lh-${page%/}-mobile.json"
  # desktop: the same with --preset=desktop
done
```

Run them one at a time (in parallel they slow each other down), read the failing audits out of
the JSON, and keep the reports out of the repository. Things to know when reading them:

- Each run spends GitHub API calls (60 an hour per address). Once they run out the download page
  falls back to `latest.json` and logs a 403, so its numbers stop being comparable; check
  `curl -s https://api.github.com/rate_limit` first. Locally `/_vercel/insights/script.js` is a
  404, which costs Best Practices a few points that the live site does not lose.
- "Improve image delivery" compares a file with its CSS size, ignoring the screen's pixel ratio.
  The srcset is right when a copy just covers the drawn width times the ratio (Lighthouse's phone
  is 412 px at 1.75x); no copy is made smaller than that. Widths live in
  `site/src/data/shot-widths.json`, the `sizes` in `site/src/components/shot-sizes.ts`, measured
  from the built page, and new copies come from `scripts/site-shot-variants.py`.
- A layout shift on `/download/` usually means something above the platform list changed after the
  first paint: a font weight that is not preloaded (`Base.astro` preloads 400, 500 and 600), or a
  note or link that app.js shows once GitHub answers. Draw such things at build time instead.
- app.js must not read layout (`getBoundingClientRect`, `innerHeight`, `scrollY`) while the page is
  being built: that is Lighthouse's forced reflow. Use an IntersectionObserver, or read after the
  first paint (`requestAnimationFrame` then `setTimeout`).

What the audit of 2026-10-18 found and fixed: screenshots cached for a day (now `?v=` and a year),
phones sent copies one size too large (640, 1080, 160 and 600 px copies added, the hero's `sizes`
tightened), app.js shipped with its comments (now minified at build, see `astro.config.mjs`), a
forced reflow in the scroll reveal and the parallax, the home page asking GitHub for the release
list (it reads `latest.json` now; the star count waits for idle), and a 0.13 layout shift on the
download page (font 500 preload, macOS card and note drawn from `latest.json`). Left on purpose:
the stylesheet stays one shared, render blocking, immutable file (about 14 KB compressed; inlining
it would add that to every page and every navigation, and the largest paint is the hero picture
anyway), its unused rules on any one page are the cost of sharing it, and the Obtainium badge PNG
stays as it is (no re-encoding images). Scores, local build, mobile and desktop:

| Page | Before | After |
| --- | --- | --- |
| Home, mobile | 99 (LCP 2.0 s, CLS 0) | 100 (LCP 1.7 s, CLS 0) |
| Home, desktop | 99 (LCP 0.7 s) | 100 (LCP 0.5 s) |
| Features, mobile | 99 (TBT 113 ms) | 100 (TBT 60 ms) |
| Features, desktop | 100 | 100 |
| Download, mobile | 97 (CLS 0.033) | 100 (CLS 0.002) |
| Download, desktop | 99 (CLS 0.074) | 100 (CLS 0.002) |

Performance, the median of three interleaved runs; Accessibility and SEO were 100 throughout.
The live site before the change scored 93 / 99 / 91 on mobile (home, features, download, the
download page with a 0.125 layout shift) and 99 to 100 on desktop.

## Branches and worktrees

`main` is the only long lived branch. Work done in a worktree or on a side branch (an agent's
`worktree-agent-*` branch, a `diag/*` test build, a branch held back until a release) is tidied
away as soon as it is merged or no longer needed:

- After merging: `git worktree remove <path>`, `git branch -d <branch>`, and if it was pushed,
  `gh api -X DELETE repos/dracu-lah/TMPlayer/git/refs/heads/<branch>`. Then `git fetch --prune`.
- A branch kept on purpose (such as `site-after-<version>`, merged on release day) is named in
  `docs/PLAN.md` with the day it goes, and deleted the same way once merged.
- Before deleting a branch that was never merged, say so, list its commit hashes in the report,
  and ask first.
- Check with `git worktree list`, `git branch` and
  `gh api repos/dracu-lah/TMPlayer/branches --jq '.[].name'` that only `main` (and any branch
  kept on purpose) is left.

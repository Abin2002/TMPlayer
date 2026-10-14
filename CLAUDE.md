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
- `site/public/screenshots/` is the site, referenced from `site/index.html`, `site/sitemap.xml` and
  `site/public/site.webmanifest`. Changing a file name means changing all three, and the manifest carries
  a `"type": "image/webp"` next to each one.

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

Every rule below exists so a new page or a release reaches search engines the same way the
existing ones do. Check them whenever a page under `site/` is added or changed.

- **URLs end in a slash.** `vercel.json` sets `trailingSlash` and `cleanUrls`, so `/privacy`
  redirects to `/privacy/`. Canonicals, `og:url`, sitemap `<loc>`s and internal links all use the
  slash form; a canonical that points at a redirect is ignored by Google.
- **Every page's head** carries: a unique `<title>` (under about 60 characters, no "X - TMPlayer"
  where X already says TMPlayer), a `description` of roughly 120 to 160 characters, `canonical`,
  `robots`, `author`, `og:type`, `og:site_name`, `og:title`, `og:description`, `og:url`, `og:image`
  with its type, width and height, `og:locale`, the four `twitter:` tags, the icons and the
  manifest, and JSON-LD with at least a `BreadcrumbList`. `site/support/index.html` is the
  smallest complete example. `404.html` is the one page that is `noindex` and has no canonical.
- **One `<h1>` per page**; every `<img>` has `alt`, `width`, `height`, and below the fold
  `loading="lazy" decoding="async"`.
- **Sitemap.** A new indexable page gets a `<url>` in `site/sitemap.xml` with a real `lastmod` (the
  date of its last content change, not today by default). Localized entries and their hreflang
  alternates are written by `scripts/build-site-i18n.py`; never edit those by hand.
- **Releases.** `scripts/build-changelog.py` writes `/changelog/`, the RSS feed, one page per
  release at `/changelog/<version>/` (BlogPosting JSON-LD) and the release entries in the sitemap.
  The release job commits all of it. Do not hand-edit anything under `site/changelog/`; change the
  script and rerun it. Release notes are the tag message, so a good tag message is the SEO text
  for that release.
- **Caching** lives in `site/vercel.json`. Fonts are immutable (their names never change content);
  `styles.css` and `app.js` keep a short cache because their names do not change between deploys,
  so do not raise it without adding a content hash to their URLs. Images, the manifest, the
  sitemap and the feed have their own entries; a new top-level asset type gets one too.
- **Feature screenshots with markers.** A shot that points at one thing is cropped to the part
  that matters and zoomed, and its hand-drawn arrows, circles and underlines are drawn into the
  image by `scripts/annotate-shot.py` from a spec in `scripts/annotations/`, so the site and the
  app's onboarding share one look and a retaken shot is re-marked with one command (user,
  2026-10-14). No words go into an image: labels stay HTML with `data-i18n` (or app strings) so
  they get translated. The feature tour on `/features/` keeps its numbered HTML markers matched
  by a numbered list. Light and dark shots stay paired. Only demo-content screenshots are used,
  never a real account.
- **Files for crawlers and machines.** `site/public/llms.txt` is a hand-written summary of the site for AI
  assistants: when a page is added, renamed or its claims change, update it too. The home page
  carries `Organization` JSON-LD (logo, `sameAs` profiles); a new official profile goes there.
  `site/public/.well-known/security.txt` expires on the date in it, so renew it before then.
  `site/public/<key>.txt` is the IndexNow key the release job pings with; do not delete it.
- **Download page** keeps its `preconnect` hints to GitHub's API, github.com and
  `release-assets.githubusercontent.com`, the host the release files are served from.
- **Sponsoring.** Every content page ends with the "Support TMPlayer" offer (GitHub Sponsors and
  Buy Me a Coffee), the block first written on the download page: inside the page's star card
  where it has one, otherwise as a `support-slim` card in the last band. Reuse its markup and its
  `home.support_tmplayer`, `home.support_no_ads`, `home.sponsor_on_github` and
  `home.buy_me_a_coffee` keys so it arrives translated; a new page gets it too. Release pages and
  the changelog carry it from `scripts/build-changelog.py`. It stays an offer: no popups, no nags,
  nothing locked.
- After changing any localized page, run `scripts/build-site-i18n.py` and `--check`.

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

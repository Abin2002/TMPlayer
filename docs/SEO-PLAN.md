# Search, AI answers and calls to action (CP72)

A plan, not yet started. Written 2026-10-15 from the Search Console export of the last three
months (Web, 2026-07-17 to 2026-10-14) and a look at what search engines and AI assistants
reward in 2026. Each item says what changes, where, and how we will know it worked.

## What the data says

Three months: 400 clicks from 2,738 impressions (CTR 14.6 per cent), average position about 6.
Impressions are rising fast (about 50 a day in September, 136 and 160 on 11 and 12 October) while
CTR falls (from around 20 per cent to 6 to 12), because the new impressions are broader queries
where the result does not yet look like the answer.

1. **People search for the long name.** TMPlayer stands for Telegram Media Player (the v1 design
   doc says so), and the long name is the second query: "telegram media player" 214 impressions at
   position 4.9, plus "... apk" (40, 4.6), "... for android tv" (15, 2.7, CTR 47 per cent),
   "tmp telegram media player" (28, 7.1) and "tmp - telegram media player" (15, 8.3). About 340
   impressions in all. The site never says "Telegram Media Player" in a title, heading or
   sentence; it appears once, inside a JSON-LD caption. We rank 5 to 8 for our own name.
2. **The short name is ambiguous.** "tmp player" (62, 2.7) and "tm player" (42, 1.7) work. "tmp
   apk" (51, 5.7), "tmp app" (7, 6.6, no clicks) and "tmp app download apk for android tv" (4,
   6.5, none) do not: other things called TMP sit above us. "tmp tv" and "tmp live" are probably
   someone else's product and are not worth chasing.
3. **Generic queries are where growth is, and where we are weak.** "telegram player" 55
   impressions at 7.7 with CTR 5 per cent; "telegram player apk" 12.5; "telegram video player"
   22; "telegram player for pc" 5 with no clicks; "tvgramplayer" (another app) 11.
4. **The home page carries everything.** `/` has 2,451 of the 2,738 impressions. `/download/` has
   437 at position 7.3 with CTR 6.2 per cent, the weakest big number on the site. `/install/`,
   `/android-tv/` and `/features/` sit at position 4 to 6 with 22 to 27 impressions and no clicks.
5. **The languages are barely found.** Every translated page together has under 250 impressions.
   The localized guides get some (it, id, ar, de, es) and no clicks.
6. **Where the readers are.** India 652 impressions (24 per cent), Myanmar 166 (CTR 25 per cent,
   no Burmese translation), United States 150 (CTR 5.3 per cent), Italy 121, Philippines 100,
   Malaysia 99, Germany 86 (CTR 4.7), Spain 82, Brazil 73. English readers in the US, UK and
   Germany click least: the snippet does not answer their query.
7. **Devices.** Mobile 1,863 impressions at position 5.0; desktop 741 at 9.0. People search on a
   phone for something to put on the TV.
8. **No rich results.** "Search appearance" is empty.

Still missing from the data, and asked of the user below: which queries each page ranks for, the
Page indexing report, and Bing.

## How search and AI answers work now (sources at the end)

- Google says AI Overviews and AI Mode run on its normal Search systems, with "query fan-out"
  across related searches; ordinary SEO is what earns a place in them. There is no separate AI
  index to submit to.
- ChatGPT search and Copilot answer from Bing's index. Bing Webmaster Tools has had an AI
  Performance report (Copilot citations, grounding queries) since February 2026. IndexNow is the
  way to tell Bing a page changed.
- Assistants quote passages, not pages: a section that answers one question in 40 to 180 words,
  under a heading that is the question, is what gets lifted.
- The same facts in the same words everywhere (site, GitHub, README, store listings, directories)
  make an assistant confident enough to name you.
- llms.txt is a community proposal, used by some assistants and coding agents, ignored by Google.
  Cheap to keep, so we keep it accurate, but it is not where the gains are.
- Agents increasingly ask for Markdown (`Accept: text/markdown`, or a `.md` twin linked from the
  page with `<link rel="alternate" type="text/markdown">`). A twin that 404s or redirects is worse
  than none.
- Mobile pop-ups that cover the content when a visitor arrives from search, or while they scroll,
  are a Google ranking demotion for that page. Slim banners are fine.

## The plan

Ordered by what moves the most for the least. Strings change in `site/i18n/en.json` only; the
translations follow through the usual catalog sync, which the user runs. Every title stays under
about 60 characters and every description 120 to 160 (CLAUDE.md).

### Phase 1: say our own name (titles, descriptions, entity)

| Page | Now | Proposed |
|---|---|---|
| `/` title | TMPlayer: free, unofficial Telegram video player for Android TV and PC (73) | TMPlayer: unofficial Telegram media player for TV and PC (56) |
| `/` description | Stream the videos in your own Telegram chats on ... | TMPlayer (Telegram Media Player) streams the videos in your own Telegram chats on Android TV, phones, Windows, macOS and Linux. Free, open source, no ads. |
| `/` hero line | A free, unofficial Telegram video player that starts in seconds ... | TMPlayer, short for Telegram Media Player, is a free, unofficial video player that starts in seconds and downloads while you watch. |
| `/download/` title | Download TMPlayer for Android TV, Windows, macOS and Linux | Download TMPlayer APK for Android TV, Windows, Mac, Linux (57) |
| `/download/` description | Download TMPlayer free: the APK for ... | Download the free TMPlayer (Telegram Media Player) APK for Android TV and phones, plus a Windows installer, a Linux AppImage and a macOS preview. |
| `/android-tv/` title | TMPlayer: unofficial Telegram video player for Android TV and Google TV | Telegram on Android TV: watch your chat videos with TMPlayer (59) |
| `/fire-tv/` title | How to install TMPlayer on Fire TV Stick and Fire TV Cube | Telegram on Fire TV Stick: how to install TMPlayer (50) |

"Unofficial" stays in the home title: "Telegram Media Player" in capitals reads like Telegram's own
product, and that confusion is worth less than the click.

Entity work, same phase:

- JSON-LD on `/`: `alternateName: ["Telegram Media Player", "TMP"]` on the SoftwareApplication,
  WebSite and Organization (today it is one long sentence, which is not what the field is for).
  Add both to `NO_TRANSLATE` in `site/src/i18n/ld.ts` so they are never translated; the old
  sentence's `ld.*` key is then unused and leaves every catalog (a removal, not a translation).
- `/download/` and `/android-tv/` get a SoftwareApplication of their own (they only carry a
  BreadcrumbList): name, alternateName, `softwareVersion` and `downloadUrl` from
  `releases.json`, `operatingSystem`, `offers` at 0. No ratings: there are none to show, and made
  up ones are a manual action waiting to happen.
- One sentence for what TMPlayer is, used word for word in: the GitHub repository description,
  the README's first line, `site.webmanifest`, `llms.txt`, the Telegram channel bio, and any
  listing (user does the GitHub description and the channel bio; I do the files).

Done when: Search Console shows "telegram media player" at position 1 to 2 and CTR on `/download/`
above 10 per cent, 28 days after the change.

### Phase 2: answer the questions people ask (content)

- **FAQ entries written as the question people type**, each answer complete in 40 to 180 words:
  "Can I watch Telegram videos on Android TV?", "Is there a Telegram app for Fire TV Stick?",
  "How do I play Telegram videos on a PC?", "Is TMPlayer free? Is there a paid version?",
  "Is TMPlayer safe? Does it see my messages?", "Does it play music and audio files?" (it plays
  MP3, FLAC and the rest inside video; say plainly what it does with audio-only files), "Is it
  the same as the official Telegram app?", "Can I use it without a phone?". Several come straight
  from the queries ("telegram para fire stick", "can i download telegram on tv", "alguna que no
  sea de pago", "telegram music player", "а для пк?"). FAQPage JSON-LD already exists on `/faq/`
  and picks them up.
- **A desktop page, `/desktop/`** ("Telegram video player for Windows, Mac and Linux"): desktop
  ranks 9 against mobile's 5, and the PC queries have nowhere to land but the home page. Keyboard
  shortcuts, mpv, the PowerShell one-liner, the MSI, the AppImage, the DMG. Localized like the
  other pages, in `lastmod.json`, `llms.txt` and the footer.
- **A comparison table in the existing guide `/guides/telegram-on-tv-without-casting/`** (not a
  new page, which would compete with it for the same queries): casting from the phone, screen
  mirroring, the regular Telegram Android app sideloaded on the TV, Telegram Web in the TV's
  browser, and TMPlayer, with what each needs and what breaks. Assistants lean on comparison
  tables, and it is where "alternative of this", "tvgramplayer" and "telegram to tv" readers end
  up. Facts only, no digs at other apps.
- **Home page: a "Telegram media player" section** that says in one paragraph what the long name
  means and what the app does on each device, so the phrase is in the page's text, not only its
  head.
- **Visible "Updated" dates** on the guides and FAQ (from `lastmod.json`), and `dateModified` in
  their JSON-LD, so freshness is visible to readers and machines.
- **Languages:** the data argues for Burmese (Myanmar: 166 impressions, the best CTR of any big
  country) and possibly Malay. Adding a language is the user's call and the user's sync; the site
  side is one entry in `LANGS`.

### Phase 3: calls to action (no pop-ups)

The site today: download is a plain nav link; four pages have no download button at all
(`/fire-tv/`, `/formats/`, the Fire TV guide, `/translate/`).

- **A "Download" button in the header**, styled as a button on every page, in every language
  (`common.download`), next to the language picker. The one change that reaches every visitor.
- **A closing download card** on the four pages without one, reusing the download band the guides
  have.
- **A slim sticky bar on phones**, shown after the visitor has scrolled past the hero, never on
  arrival: "Get TMPlayer for your TV" with one button, dismissible, the dismissal remembered like
  the notice bar's. A bar, not a modal, so it is not an intrusive interstitial.
- **After the download click, an inline panel on `/download/`** (not an overlay): the next three
  steps for the file just chosen (Downloader code `tmplayer.org/apk` for TV, the PowerShell line
  for Windows, "Open Anyway" for Mac), then "Join the Telegram channel for updates" and "Star it
  on GitHub". This is where a visitor is most willing to do one more thing, and it costs nothing
  in rankings.
- **Pop-ups: not recommended.** A modal on arrival or while scrolling is a Google demotion on
  mobile for the page that shows it, most of the traffic is mobile, and CLAUDE.md already rules
  out pop-ups and nags for sponsoring. Everything a pop-up would do is covered by the bar and the
  panel above.
- **Measure:** Vercel Web Analytics custom events on the header button, the bar, the download
  rows and the panel's links (if the plan allows events; otherwise outbound clicks), plus GitHub
  download counts (`scripts/download-counts.sh`).

### Phase 4: AI assistants and agents

- **robots.txt stays open to everyone**, and says so per bot for clarity: OAI-SearchBot,
  ChatGPT-User, GPTBot, Claude-SearchBot, Claude-User, ClaudeBot, PerplexityBot, Perplexity-User,
  Google-Extended, Applebot-Extended, Bingbot. Being in the training data is how an assistant
  knows TMPlayer without searching, so training bots stay allowed too (the user may decide
  otherwise; it is one line each).
- **Vercel's firewall must not challenge them.** During the cutover checks the Security Checkpoint
  started challenging scripted requests from one machine. If Bot Protection or Attack Challenge
  Mode is on, assistants fetching a page for a user (ChatGPT-User, Claude-User, Perplexity-User)
  can get the challenge page instead of the content. The user checks Firewall settings in the
  Vercel dashboard; verified search crawlers are exempt, user-triggered fetchers may not be.
- **Markdown twins.** An Astro endpoint writes `/index.md`, `/download/index.md` and so on for
  every English page: the page's own text, headings and tables, no navigation. Each HTML page
  links its twin with `<link rel="alternate" type="text/markdown">`, and `vercel.json` rewrites a
  request carrying `Accept: text/markdown` to the twin (a `has` header rule), with `Vary: Accept`.
  `scripts/site-links.py` checks every twin exists and answers 200 with `text/markdown`.
- **`llms.txt` and a new `llms-full.txt`.** `llms.txt` gains the long name and the short one,
  the one-sentence definition, the new pages, a "For agents" block (the stable machine addresses:
  `https://tmplayer.org/latest.json` for the newest version and its files, `/apk` for the
  Android APK, `install.ps1` for Windows, the RSS feed), and the eight FAQ answers above in
  short form. `llms-full.txt` is every English page's Markdown twin joined, built at deploy time
  so it never drifts.
- **IndexNow on every site change, not only releases.** A small job on pushes to `main` that touch
  `site/` pings IndexNow with the URLs whose content changed, so Bing (and through it ChatGPT
  search and Copilot) sees a change within hours.
- **Bing Webmaster Tools** is set up (data from 2026-10-09; the first four days: 34 impressions,
  3 clicks, about 9 per cent CTR, roughly a fortieth of Google's daily volume). Check the sitemap
  is submitted there, then read the AI Performance report every month next to Search Console.
- **Third-party presence** (user, ongoing): assistants cite what others say about you. Listings
  where people look for apps like this, all with the same sentence and link: AlternativeTo, the
  awesome-android-tv and awesome-telegram lists on GitHub, an XDA Developers thread, Reddit
  (r/AndroidTV, r/fireTV, r/Telegram) when someone asks, and the IzzyOnDroid or F-Droid listing
  if it ever happens. No paid placements, no fake reviews.

### Phase 5: measure and repeat

28 days after each phase ships: export Search Console again (same filters), compare position and
CTR for the queries above, Bing's AI Performance report, Vercel events, and GitHub downloads.
Keep what moved, revert what did not.

## What I need from the user

1. **Per-page queries**: in Search Console, Performance, click the page, then the Queries tab,
   and export, for `/`, `/download/`, `/install/` and `/android-tv/`. This says which page each
   query lands on, which the site-wide export cannot.
2. **Page indexing report** (Indexing, Pages): any "Crawled, currently not indexed", "Discovered,
   currently not indexed", "Duplicate without user-selected canonical" or "Alternate page with
   proper canonical" rows, exported. It shows whether the translated pages are indexed at all.
3. **The Vercel firewall settings** (Firewall, Bot Protection and Attack Challenge Mode: on or off).
4. **Bing**: the Queries and Pages tabs of Search Performance, the sitemap status, and the AI Performance export once it has a month of data.
5. **Decisions**: training bots allowed (recommended) or not; Burmese and Malay worth translating;
   the header button and the phone bar approved as described.

## Sources

- Google, [AI features and your website](https://developers.google.com/search/docs/appearance/ai-features)
- [Google's official guide to optimizing for generative AI features](https://gigazine.net/gsc_news/en/20260518-google-guide-optimizing-generative-ai) (May 2026)
- [Search Engine Journal on Google's AI Overviews and AI Mode advice](https://www.searchenginejournal.com/googles-official-advice-on-optimizing-for-ai-overviews-ai-mode/547341/)
- Vercel, [Make your documentation readable by AI agents](https://vercel.com/kb/guide/make-your-documentation-readable-by-ai-agents)
- [Serving Markdown to AI agents](https://flaviocopes.com/serving-markdown-to-ai-agents/), and [Lumar on Markdown twins](https://agentic-readiness.lumar.io/docs/content/markdown-twin)
- [AI crawler user agents in 2026](https://geo.tenten.co/en/blog/ai-crawler-user-agent-list-2026), [robots.txt for AI crawlers](https://specification.website/spec/agent-readiness/robots-for-ai-crawlers.md)
- [State of llms.txt 2026](https://presenc.ai/research/state-of-llms-txt-2026) (a vendor report)
- [Bing Webmaster Tools, IndexNow and the AI Performance report](https://geotoolbox.ai/blog/bing-webmaster-tools), [What 20,000 Copilot citations taught us](https://www.searchinfluence.com/blog/bing-ai-performance-report-copilot-citations/)
- [GEO best practices: what is actually proven in 2026](https://crowdo.net/blog/?p=2755)
- [Search Engine Journal on intrusive mobile pop-ups](https://www.searchenginejournal.com/google-devalue-pages-intrusive-pop-ups-mobile-search/171534/), [Guide to intrusive interstitials](https://seosly.com/blog/intrusive-interstitials/)

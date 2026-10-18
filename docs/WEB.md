# TMPlayer in the browser (CP92, web.tmplayer.org)

Status: planned for later, not started. Nothing of it is in the repository. A phase 1 prototype
was built on 2026-10-19 to settle the risky parts and then set aside on the user's word; what it
proved is under "Findings". This document is the plan to build it from.

Goal: as much of the app as a browser allows, for the videos already in the viewer's own Telegram
chats, at web.tmplayer.org, a static site with no server of ours in the path.

## Findings from the prototype (2026-10-19)

What ran:

- **A Kotlin/Wasm module with Compose Multiplatform for Web built**, against the repository's own
  Kotlin 2.4.21 and Compose Multiplatform 1.12.2 (wasmJs artifacts exist for ui, foundation,
  runtime and material3 1.12.0-alpha03). `:web:wasmJsBrowserDevelopmentRun` served it on
  localhost:8080 and `:web:wasmJsBrowserDistribution` wrote a static site. Run the two in separate
  Gradle invocations: together, Kotlin's development and production syncs share a directory and
  Gradle stops on an implicit dependency between them.
- **`:core` compiled for wasmJs** with a pure source set (below), and its common tests passed on
  Node (8 tests) and on the JVM, with the existing MediaNameTest (46), SplitPiecesTest,
  EpisodeCopies, VideoCopies and AuthReducer tests unchanged and green. The JVM regexes in
  MediaName (lookbehind, `\p{L}`, `\p{M}`) behaved the same on Wasm.
- **The Service Worker range streaming worked in Chromium 155**: a 60 second, 30 MB H.264/AAC MP4
  and a 23 MB VP9/Opus WebM, each served only through `stream/<id>` with 1 MiB 206 answers, played
  and seeked to 75%; the seek was answered by reads at 20.5 MB and 16 MB, not from what had
  played, so the element really asked for new ranges. The range logic also passed 10 Node tests
  (suffix ranges, 416, short reads answered as shorter ranges, a whole file reassembled chunk by
  chunk, a 200 streamed when there is no Range header).
- **tdweb loaded** from pinned, hash-checked files, and the bridge compiled; the sign in screen with
  a QR code was not yet screenshotted when the work stopped, and no real sign in was tried (it
  needs the user's phone).

What did not work, or needs care:

- **Gradle's repository rule.** `settings.gradle.kts` sets `FAIL_ON_PROJECT_REPOS`, and Kotlin's
  wasm tooling adds project repositories for Node.js, Yarn and Binaryen. Fixed by: Node from the
  machine (`WasmNodeJsEnvSpec.download = false`), npm instead of Yarn (`kotlin.js.yarn=false`), and
  Binaryen through an `exclusiveContent` ivy repository in settings with
  `BinaryenEnvSpec.downloadBaseUrl = null`. Kotlin then writes `kotlin-js-store/wasm/package-lock.json`,
  which is committed (`kotlinWasmUpgradePackageLock` refreshes it).
- **`js()` interop needs `kotlin.js.ExperimentalWasmJsInterop`** opted in (compiler option) on 2.4.
- **A custom `dependsOn` breaks the default hierarchy template** (a warning, and a Gradle 9.6
  deprecation for `by creating`). Use `applyDefaultHierarchyTemplate { common { group("jvmShared")
  { withJvm(); withCompilations { it.target.platformType == androidJvm } } } }` (opt in to
  `ExperimentalKotlinGradlePluginApi`); `withAndroidTarget()` does not match AGP's KMP library target.
- **The Compose compiler on the wasm target** is not needed for pure code; limit it with
  `composeCompiler.targetKotlinPlatforms = setOf(jvm, androidJvm)` in `:core`.
- **First load is heavy**: Brotli sizes of the production build were 2.6 MB (Skiko) + 0.9 MB (the
  app) + 3.3 MB (TDLib) + 0.1 MB of scripts, about 7 MB, and the TDLib wasm is 13.8 MB unpacked.
- **Headless Chromium on Fedora** plays H.264, VP9 and AV1, and says "" to HEVC and AC-3 and
  "maybe" to `video/x-matroska`, which it does not really play (see the codec matrix).

The prototype's whole diff (about 840 KB, most of it the npm lock file) and the two stream
screenshots were kept outside the repository, in the session's scratchpad, for reference.

## Architecture

```
 Compose for Web (Kotlin/Wasm)            page                       Service Worker
 screens, view models, :core pure  --->  td-bridge.js  ---> tdweb     sw.js + stream-range.js
          |   JSON strings                (TdClient)        worker      |  stream/<fileId>?size&mime
          |                                   ^              + wasm     |  Range -> 206
          v                                   |  readRange              v
     <video src="stream/42?...">  ----------  stream-host.js  <---  postMessage(MessageChannel)
```

### Telegram: tdweb

TDLib's own browser build (`example/web` in tdlib/td: emscripten, OpenSSL, the `tdweb` JS wrapper
with a worker and IndexedDB storage). Candidates checked on 2026-10-19:

| Build | TDLib | Date | Notes |
| --- | --- | --- | --- |
| `tdweb` (official npm) | 1.8.0 | 2022 | abandoned |
| `@wostafa/tdweb` | 1.8.10 | 2022-12 | abandoned |
| `@dibgram/tdweb` npm | 1.8.40 | 2024-11 | last npm publish |
| **DIBgram/tdweb repo, commit `af27a3b79f6dc7435d5ceeb31a998d531d845762`** | **1.8.64** | **2026-05-08** | built by the repo's public Actions workflow from tdlib/td with emsdk 3.1.1; not published to npm |
| `@aefen/tdweb` | 1.8.49 | 2025-05 | one anonymous publish |
| build it ourselves from tdlib/td `example/web` | 1.8.67 | any | about 1.5 h in CI |

The app uses TDLib 1.8.67 (read from tdl-coroutines 15.0.0's native library). Phase 1 takes
DIBgram's 1.8.64 files from the commit above, each checked by SHA-256 in Gradle:

| File | SHA-256 |
| --- | --- |
| `tdweb.js` | `747c02108e49b924c23bb556b40d203f11261c9e0bd419ba479ccd56e143d709` |
| `8a2ea5aea50674d967ce.worker.js` | `cc0789d76ddb74f647f0a6cc4c70420846f7792a117b251fb902a521cb7cae5e` |
| `1.8a2ea5aea50674d967ce.worker.js` | `eda4196bdad3d40fbe47a5a9cc75974f7204c497634cbfd1aff971ca0858c56e` |
| `b351db12a104e44cf97b09a3385b7077.wasm` | `33b823d03c4b7c59f82ca7527232d874ea3b28d52bebf738827e78cdfd6c7a98` |

The worker and the wasm are found relative to the page (webpack `publicPath` is ""), so they are
served from the site root. `tdweb.js` exposes `window.tdweb.default` (the `TdClient` class).

Licences: TDLib and tdweb's source are BSL-1.0 (the npm `package.json` says MIT, the code is
BSL-1.0); the bundle carries localforage (Apache-2.0), broadcast-channel, uuid and @babel/runtime
(MIT), zlib (zlib licence) and **OpenSSL 1.1.0l** (OpenSSL and SSLeay licences), the version TDLib's
`build-openssl.sh` still pins. OpenSSL 1.1.0 is long out of support; TLS to Telegram is MTProto's
own encryption on top, but this is the main reason to **build tdweb ourselves in phase 2** with a
current OpenSSL 3.x (Apache-2.0) and TDLib 1.8.67, in a workflow of our own with the outputs
hashed, rather than trust a third party's binary with the viewer's session. THIRD_PARTY_NOTICES.md
gets a "Web" section listing these when the web app first ships.

tdweb facts that shape the design (read from its source):

- `send(query)` returns a Promise; errors reject with `{ '@type': 'error', code, message }`.
- `readFilePart { file_id, offset, count }` is handled in the page: it checks
  `getFileDownloadedPrefixSize`, reads TDLib's partial download from the worker's in-memory file
  system, or slices the finished file's Blob from IndexedDB, and answers `{ data: Blob }`.
- **Files are kept in MEMFS while they download** and flushed to IndexedDB when complete. MEMFS
  files are dense arrays: a partial download at offset 1.5 GB allocates 1.5 GB of memory. This is
  the hard limit on streaming big files with stock tdweb (see Storage).
- Only one TdClient per `instanceName`; a second tab closes the first unless it is a background one.

### The bridge

Everything crosses the Wasm boundary as TDLib JSON text: `TmTd.send(json): Promise<string>` and
`TmTd.start(onUpdate: (string) -> Unit)` in `td-bridge.js`, parsed in Kotlin with
kotlinx-serialization-json (`JsonObject`, no generated DTOs). Video bytes never enter Wasm: the
Service Worker's reads are answered in JavaScript (`TmTd.readRange`). Small files (thumbnails,
chat photos) come to Kotlin as base64 and are decoded with Skia (`Image.makeFromEncoded`).

Typed calls for phase 1 (fields from td_api.tl 1.8.64; extra fields are ignored and missing ones
default, so it tolerates an older or newer TDLib): `setTdlibParameters`,
`requestQrCodeAuthentication`, `setAuthenticationPhoneNumber`, `checkAuthenticationCode`,
`checkAuthenticationPassword`, `setAuthenticationEmailAddress`, `checkAuthenticationEmailCode`,
`logOut`, `loadChats`, `getChats`, `getChat`, `searchChatMessages` (filters
`searchMessagesFilterVideo` and `searchMessagesFilterDocument`, merged, documents kept when the
name or type says video), `getFile`, `downloadFile` (offset, limit, synchronous),
`getFileDownloadedPrefixSize`, `readFilePart`.

Reads of one file are serialized: TDLib ends a waiting synchronous `downloadFile` when another one
with different bounds arrives for the same file.

### The core's web target

Proved in the prototype, to redo in phase 2: `:core` gets a `wasmJs { nodejs() }` target. Its
`commonMain` source set reads a new `src/pureMain/kotlin`; everything else becomes the
`jvmSharedMain` group (android plus jvm) that still reads `src/commonMain/kotlin`, so no file has
to move at once. Moved to pure in the prototype, with these changes:

| File | Change |
| --- | --- |
| `MediaName.kt` | `"%02d".format` to `padStart`; `lowercase(Locale.ROOT)` to `lowercase()` (already locale invariant); `Character.getType(...) == OTHER_SYMBOL` and code points through `expect fun isOtherSymbol(codePoint)` (JVM: `Character`; Wasm: `/\p{So}/u`); `Calendar` year through `expect fun currentYear()` |
| `SplitPieces.kt` | name half split into `SplitPieceNames` (pieceOf, isArchiveVolume, baseOf, keyOf, `SplitPiece`); `SplitPieces` keeps joining, reports and wording and delegates |
| `EpisodeCopies.kt`, `VideoCopies.kt` | `markers`, `closest<T>`, `languagesIn` and their tables moved to a pure `CopyMarkers`; both delegate |
| `MediaMapper.kt` | `VIDEO_EXTENSIONS` moved to a pure `VideoExtensions.ALL` |
| `AuthReducer.kt` | `AuthState`, `SignInMethod`, `CodeDelivery` moved to a pure `AuthState.kt` (they never touched TDLib) |

What blocks the rest of `src/commonMain` (159 files on 2026-10-19), and the change each kind needs:

- **TDLib DTOs** (16 files: Td, ChatRepository, MediaMapper, AuthReducer, ChatAudio, NewVideoAlerts,
  Proxies, PieceAlerts, ConnectHelp, Archives, ResumeSync, EpisodeAlerts,
  SponsoredMessageRepository, TdByteWindow, TdlResultExt, OnlineSubtitles). tdl-coroutines has no
  wasm target. Phase 2 puts a `TdGateway` interface in pure code, speaking the app's own models
  (MediaItem, chats, files, auth states), with two implementations: tdl-coroutines on the JVM and
  JSON over tdweb in the browser. MediaMapper's message reading becomes a mapper from a small
  common message model; AuthReducer reduces a TDLib-neutral auth state.
- **org.json** (13 files: Backup, MetadataCache, ResumeSyncDoc, MetaExtras, OpenSubtitlesApi,
  MetaModels, SkipTimes, SubtitleCache, SubdlApi, UpdateFeed, Translator, MetaApis,
  SubtitleModels): to kotlinx-serialization-json (`@Serializable` models or `JsonObject` reads),
  which also removes the Android/JVM org.json split in `:core`'s build.
- **java.io and java.nio** (31 files, among them the stores, DiskInfo, DownloadFiles, Failures,
  WatchCacheRules, MetadataStore, SubtitleText, MovieHash, CacheDirRename, the cast and remux code):
  the stores go behind a key value and blob storage interface (DataStore on the JVM, IndexedDB or
  OPFS in the browser); disk and cache code stays JVM only.
- **java.net / HttpURLConnection** (11 files: Http, OnlineMetadata, MetaApis, OpenSubtitlesApi,
  SubdlApi, Updates, UpdateFeed, Proxies, Feedback, CastServer, LanAddress): one `expect` HTTP
  client (fetch on the web, the current code on the JVM), or Ktor client if it earns its size.
  CORS decides what the browser can call directly: TMDB, TVmaze and OpenSubtitles send CORS
  headers; anything that does not needs a proxy we do not want, so it is left out on the web.
- **DataStore** (5 files: SettingsStore, BookmarkStore, WatchLaterStore, WatchedStore, Backup):
  behind the storage interface above; values stay the same keys, so Backup and resume sync read
  the same documents.
- **Locale, String.format, synchronized, @Volatile, threads** (about 35 and 25 files): mechanical;
  `kotlinx.atomicfu` or plain single threaded code on the web, `padStart` and the existing
  LocaleFormatter for numbers.
- **i18n**: `Translator` loads the catalogs with org.json from JVM resources; the web build ships
  the same `i18n/*.json` and loads them with fetch. Generated `Strings` and `L` move to pure.

About 75 files have no JVM import at all (the player rules such as AbLoop, Chapters, PieceMap,
Skip, SpeedMemory; HomeRows, WatchNext, SizeFilter and more), but many of them take MediaItem,
which lives beside TDLib types, so they move together once MediaItem is pure.

### Screens: reuse the desktop

`:ui` compiles `src/shared/kotlin` against Compose Multiplatform on the desktop. Phase 2 adds a
`wasmJs` target to `:ui` the same way (the shared directory compiled for it, with web actuals for
the few platform pieces: back handling, notices, the QR code, which on the web is drawn from
qrcode-generator 1.4.4, MIT, or a Kotlin encoder). The desktop's screens in
`desktop/src/main/kotlin/com/tmplayer/desktop/ui` (sign in, home, chats, grids, show pages,
search, settings) move into `:ui` behind small interfaces for what is desktop only (window drag,
title bar, mpv, file pickers, tray, MPRIS and SMTC). Phase 1's own screens are throwaway.

### Playback: a Service Worker serving byte ranges

`sw.js` answers one URL, `stream/<fileId>?size=<bytes>&mime=<type>`, and lets everything else go
to the network. `stream-range.js` (shared by the worker and the Node tests) turns the Range header
into an answer: `bytes=a-` and `bytes=a-b` give a 206 of at most 1 MiB, `bytes=-n` the last n
bytes, no header a 200 streamed in chunks, anything unsatisfiable a 416 with `Content-Range:
bytes */size`, and a read that comes back short a correct shorter 206 (the element asks for the
rest). Answers are `Cache-Control: no-store`; TDLib's storage is the cache.

The worker has no TDLib (it lives in the page), so each read is a message to the page that made
the request (`event.clientId`, else any open TMPlayer window) over a MessageChannel; the page
answers from `TmTd.readRange`: `getFileDownloadedPrefixSize`, a synchronous `downloadFile` with
offset and limit when the bytes are not there, then `readFilePart`, with the ArrayBuffer
transferred, not copied. The video element is the browser's own, over the Compose canvas.

Split pieces (`Name.mkv.001` and on): the worker maps one stream over the pieces in order (the
offset arithmetic SplitPieces already does for the apps), reading each piece's file in turn.

### Storage

| What | Where | Limit |
| --- | --- | --- |
| TDLib database (chats, messages, session) | IndexedDB via tdweb's IDBFS, synced every 5 s | small |
| Finished downloads | IndexedDB blobs (tdweb) | the browser's quota: up to about 60% of free disk in Chromium, 10% of disk in Firefox, about 1 GB before asking on Safari, and evictable unless `navigator.storage.persist()` is granted |
| Partial downloads while streaming | MEMFS in tdweb's worker, dense | RAM: a seek far into a large file allocates up to that offset |
| Settings, Watched, Watch later, bookmarks | IndexedDB through the storage interface | small |

The MEMFS limit means stock tdweb streams videos up to a few hundred MB well and fails on the
2 to 4 GB files common in channels. Phase 3 fixes it in our own tdweb build: TDLib's files
directory on OPFS (emscripten's WasmFS OPFS backend, or a custom sparse file backend that writes
each downloaded part with `FileSystemSyncAccessHandle` at its offset), and the files directory
trimmed the way the apps' WatchCacheRules trim theirs. Until then the web app says plainly that a
file over a set size needs the app.

## Codec matrix and the "needs the app" handoff

Decided before anything downloads, from Telegram's MIME type, the file name and its markers
(`CopyMarkers.markers`: x265, HEVC, AV1), asked of `video.canPlayType` and
`MediaSource.isTypeSupported`; a wrong guess is still caught when the element reports an error.

| File | Chrome / Edge | Firefox | Safari | Web app does |
| --- | --- | --- | --- | --- |
| MP4, H.264 + AAC | yes | yes | yes | plays (phase 1) |
| MP4, H.264 + AC-3 / E-AC-3 | video only | video only | yes (macOS, iOS) | phase 3: audio converted with WebCodecs where decoding exists, else needs the app |
| MP4, HEVC | where the OS has a decoder (Windows with the extension, macOS) | no | yes | plays where `canPlayType` says so, else needs the app |
| MP4 or WebM, AV1 | yes | yes | M3 and newer | plays |
| WebM, VP9 + Opus | yes | yes | yes (recent) | plays |
| MKV, H.264 + AAC | no (says "maybe") | no | no | phase 3: repacked to fragmented MP4 in the page and fed through Media Source (the cast code's `Matroska`, `MkvRemuxer` and `Fmp4` in `:core` are the starting point) |
| MKV, HEVC or with DTS / TrueHD | no | no | no | needs the app |
| AVI, WMV, FLV, TS | no | no | no | needs the app |
| ASS subtitles with styling | no | no | no | text kept as WebVTT, styling needs the app |

"Needs the app": the player layer says "This video needs the TMPlayer app", why in one line, and
"Get the TMPlayer app" linking to tmplayer.org/download/, which picks the right build for the
device. The grid marks such tiles "Needs the app" before they are opened.

## Feature parity

Ports as it is (phase 2, same code through `:core` and `:ui`): QR, phone, email code and password
sign in; Home with the carousel, Continue watching and rows; Chats with folders; chat grids, sort,
size filter; series and show pages with seasons and episodes; copies and qualities; Search with
@username and filters; the chat info sheet; online metadata (TMDB, TVmaze); Watched, Watch later,
favourites, history; rename and fix matches; resume across devices through Saved Messages; the kids
lock and PIN; themes including Black; the 21 languages; tooltips.

Changes for the browser: the player is the browser's `<video>` with TMPlayer's controls drawn over
it (phase 3): speed, chapters (from MP4 and MKV chapter readers in `:core`), bookmarks, A-B repeat,
frame step (`requestVideoFrameCallback`), skip intro, the desktop's keyboard shortcuts, picture in
picture (the element's own), audio track choice (`audioTracks` in Safari, one track elsewhere
unless repacked), subtitles as WebVTT text tracks including online subtitles; notifications through
the Notifications API when allowed and only while a tab is open; casting through the Remote
Playback API (Chrome's Cast) instead of the app's own cast server; proxies only if tdweb's
transport allows them (TDLib in a browser reaches Telegram over WebSockets; SOCKS5 and HTTP proxies
are not possible from a page, MTProto proxies to be checked in phase 2).

Cannot work in a browser: offline downloads beyond the storage quota, the Downloads folder, a
storage location, TV and remote control layouts, DTS, TrueHD and most AC-3 sound, background
playback after the tab closes, MPRIS/SMTC (the Media Session API covers the basics), the system
tray, self update (the site is the update), the LAN cast server.

## Phases

| Phase | Deliverables | Acceptance checks | Effort |
| --- | --- | --- | --- |
| 1. Proof | `web/` module (Kotlin/Wasm, Compose for Web); tdweb 1.8.64 pinned by hash; the JSON bridge; QR and phone sign in mapped onto `AuthState`; chat list; one chat's video grid with titles from MediaName, sizes and thumbnails; MP4 through the Service Worker; the codec check and "needs the app"; `:core` pure source set with a wasmJs target; dev and production Gradle tasks | `./gradlew build` green with `:core` wasmJs tests on Node; `node --test` range tests; Playwright with `/usr/bin/chromium-browser` plays and seeks a generated 30 MB MP4 and WebM through the worker; the sign in screen shows a QR code at 1280 and 390 wide; a real sign in by the user ends on the chat list and plays an MP4 from a chat | 3 to 4 days (about half was done in the prototype) |
| 2. Core and screens | tdweb built by our own workflow (TDLib 1.8.67, OpenSSL 3.x, hashes recorded); `TdGateway` with JVM and web implementations; org.json to kotlinx.serialization; storage and HTTP interfaces; `:ui` with a wasmJs target; the desktop screens moved into `:ui` and used by desktop and web; i18n catalogs loaded on the web | the Android and desktop builds and all `:core` jvmTest suites unchanged and green; `:core` common tests on Node; web Home, Chats, show pages and Search render with real data; every screen works by keyboard alone | 3 to 5 weeks |
| 3. Player and codecs | the player's controls over `<video>`; subtitles as WebVTT and online subtitles; split pieces as one stream; MKV with H.264/AAC repacked through Media Source; AC-3 / E-AC-3 converted with WebCodecs where it exists; OPFS backed partial downloads in our tdweb; size and codec handoff rules | a 4 GB MKV seeks to its end without the tab's memory growing past a set ceiling; each codec matrix row behaves as listed in Chrome, Firefox and Safari; keyboard shortcuts match the desktop | 3 to 4 weeks |
| 4. Ship | PWA (manifest, icons, install, offline shell); SEO and Markdown twin for the landing page, legal and privacy pages for the web app (what is stored in the browser, nothing on our servers), analytics as the site has; THIRD_PARTY_NOTICES "Web" section; the second Vercel project and CI | Lighthouse 100 for SEO and best practices on the landing page; installs as a PWA on Android Chrome and desktop Chrome; privacy page reviewed by the user | 1 week |

## Deployment

A second Vercel project, "tmplayer-web", with its root directory `web/` and the production build's
output `web/build/dist/wasmJs/productionExecutable`, on the domain web.tmplayer.org (a CNAME the
user adds). The site project stays as it is. Vercel builds nothing: GitHub Actions builds with
Gradle and deploys the static output with the Vercel CLI (`vercel deploy --prebuilt`), so the JDK
and Gradle never run on Vercel. Workflow `web.yml`, path filtered to `web/**`, `core/**`, `ui/**`,
`gradle/**`, `kotlin-js-store/**` and the build files; the existing workflows ignore `web/**`.
`web/vercel.json`: `Content-Type: application/wasm` and long immutable caching for the hashed
`.wasm` and `.js` names, `no-cache` for `index.html` and `sw.js` (a worker must update promptly),
and `Service-Worker-Allowed: /`. No COOP/COEP headers are needed (tdweb's build has no threads).
The Telegram API id and hash come from repository secrets at build time; they are visible in the
page's script, exactly as they are in the APK.

## Risks

- **Supply chain:** the TDLib wasm holds the viewer's Telegram session. Phase 1 trusts DIBgram's
  CI built files at a pinned commit and hash; phase 2 must replace them with our own build.
- **OpenSSL 1.1.0l** in every available tdweb build (TDLib's script pins it).
- **Large files:** stock tdweb keeps partial downloads in memory; without phase 3's OPFS work the
  web app cannot stream the files channels actually post.
- **Telegram's view of web clients:** a new api_id used from browsers may draw more flood waits and
  session checks; using the app's own id ties the web app's behaviour to the apps' standing.
- **One tab at a time:** tdweb closes older tabs' clients; a second tab must say so.
- **Safari:** Service Worker streaming to `<video>` works, but storage eviction after 7 days of no
  use (ITP) means signing in again; WebCodecs audio is partial.
- **Size:** about 7 MB compressed on first load (Skiko alone is 2.6 MB); a slow phone waits.

## Open decisions for the user

1. Build tdweb ourselves (recommended, about 1.5 h of CI per TDLib version) or accept DIBgram's
   CI builds at pinned commits.
2. The api_id: the apps' own, or a separate one registered for the web app.
3. A size ceiling for files the web app streams before phase 3 (for example 500 MB), and whether
   phase 3's OPFS work is worth it or big files simply hand off to the app.
4. Whether the web app needs a sign in warning ("you are signing in on a shared computer?") and a
   "sign out everywhere else" note in its privacy page.
5. web.tmplayer.org as a subdomain (a second Vercel project, as above) or tmplayer.org/app/ inside
   the site project.
6. Firefox and Safari as first class targets in phase 1, or Chromium first.

## Running it (once built)

```sh
./gradlew :web:wasmJsBrowserDevelopmentRun      # http://localhost:8080/
./gradlew :web:wasmJsBrowserDistribution        # web/build/dist/wasmJs/productionExecutable
./gradlew :web:streamTest                        # the worker's range logic on Node
cd web/stream-test && npm ci && node browser-test.mjs   # Chromium: stream and sign in checks
```

Node.js comes from the machine (tested with 24.21); the Telegram id and hash from local.properties
or `TG_API_ID` and `TG_API_HASH` in the environment, never committed.

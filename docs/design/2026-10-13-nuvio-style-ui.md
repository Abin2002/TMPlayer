# Cinematic UI refresh, after Nuvio (2026-10-13)

The user asked for "all the UI to look better, like Nuvio". This document studies what Nuvio
actually does, measures TMPlayer against it screen by screen, and sets a target for the phone, the
television and the desktop, then splits the work into six packages that can be built one after
another (or partly in parallel) without two of them editing the same file.

Nothing here copies Nuvio's name, logo, fonts or assets. We take the patterns: art first, quiet
chrome, deep neutral surfaces, generous gradients, one bright primary action, and focus that moves
the picture rather than repainting the card.

No app code changes with this document. It is the spec the work packages are built from.

## 1. What Nuvio is, and where the figures come from

Nuvio's mobile app (formerly `tapframe/NuvioStreaming`, now `NuvioMedia/NuvioMobile`, branch
`cmp-rewrite`) is a Kotlin and Compose Multiplatform app for Android, iOS and desktop. Its
television app is `NuvioMedia/NuvioTV` (Compose for TV). Every figure below was read from their
source unless it says otherwise. Paths are relative to
`composeApp/src/commonMain/kotlin/com/nuvio/app/` for mobile and `app/src/main/java/com/nuvio/tv/ui/`
for the television.

Because Nuvio is Compose too, most of its figures translate one to one into our `Modifier` code.

### 1.1 Palette (`core/ui/ThemeColors.kt`, `Tokens.kt`)

- Neutral, almost hueless dark: background `#0D0D0D`, elevated `#1A1A1A`, card `#222222`, an
  AMOLED option at `#000`. The television's steps are `#0D0D0D`, `#1A1A1A`, `#1E1E1E`, `#242424`,
  `#2D2D2D`.
- Text in three steps: primary `#F5F7F8`, secondary `#B8BEC5`, muted `#969CA3`, disabled at 38 per
  cent of muted.
- Borders: `#252A2A` default, the same at 55 per cent for a subtle edge, `#3A4040` strong, and
  the accent at 75 per cent for selected.
- The default accent is near white (`#F5F5F5` with `#111111` on it). Coloured presets (gold, jade,
  arctic blue and others) each carry an accent gradient that paints the seek bar.
- State overlays are white at 8, 12 and 15 per cent (hover, pressed, selected). Scrim is black at
  56 per cent. Skeleton blocks are white at 6 per cent, with a white 10 per cent band sweeping over
  them.

### 1.2 Home (`features/home/components/`)

- **Hero** (`HomeHeroSection.kt`):
  - Size: 82 per cent of the viewport on a phone, clamped to 360 to 760 dp. On tablets it is
    0.42 to 0.58 of the width, clamped to roughly 320 to 440 dp.
  - Shape: bottom corners rounded at 28 dp.
  - Motion: the backdrop is scaled to 1.14 so it can move. Scroll parallax is 0.3 and swipe
    parallax 0.055. It auto-advances every 8 s.
  - Gradients: a full-height vertical one, and over it a 220 dp bottom fade to the background.
  - Title: a title logo at 62 per cent of the width with an aspect of 2.6, falling back to bold
    display text on two lines. Under it a meta line (type, a 4 dp dot, genre, date) in labelLarge
    SemiBold.
  - Action: one pill button, radius 40, white with dark text.
  - Page dots: 8 dp tall, growing from 8 to 32 dp wide when active, opacity 0.35 to 0.92.
- **Rows** (`core/ui/ShelfComponents.kt`):
  - Tiles: 2:3 posters at 126 x 189 dp, radius 12, 10 dp apart.
  - Margins: the side margin is 16 dp on a phone, then 24, 28 and 32 dp at 768, 1024 and
    1440 dp wide.
  - Headings: row titles are 18 sp SemiBold. "View all" is a quiet 32 dp chip with a chevron.
  - Captions: the title on one line, the date under it.
- **Continue watching card** (`HomeContinueWatchingSection.kt`):
  - Size: 16:9 at about 247 x 140 dp.
  - Scrim: from 45 per cent of the height down to the background, at 70 per cent at 0.6 and
    95 per cent at the foot.
  - Text, over the art at the bottom left: "S1 E2" in labelMedium SemiBold, then the title.
  - Progress: a 3 dp bar inset 8 dp, accent fill on a white 30 per cent track.
  - Badges: a small badge for a new episode or season.

### 1.3 Details (`features/details/components/`)

- **Backdrop:**
  - Height: width x 1.33 on a phone, clamped to 420 to 760 dp.
  - Motion: parallax 0.5, scale 1.08.
  - Fade: a 320 dp bottom gradient with seven stops. At 0, .16, .32, .50, .68, .84 and 1 of its
    height, the background's alpha is 0, .04, .14, .34, .62, .84 and 1. That curve is why the text
    under it never sits on a hard edge.
- **Title and facts:**
  - Logo: 60 per cent of the width, up to 80 dp tall, falling back to display text.
  - Genres: up to three, dot separated, muted.
  - Facts: year and runtime in bold, then an outlined certification badge (1 dp at 55 per cent,
    radius 6).
- **Description:** three lines, tap to expand.
- **Actions:** a full-width Play pill, 52 dp tall, radius 40, at most 420 dp wide, in white. The
  secondary actions are round icon buttons beside it.
- **Seasons:** text chips (radius 12) or small posters. **Episodes:** a list on the phone, 120 dp
  tall with a 120 dp thumbnail, on a 45 per cent surface with a 1 dp white 10 per cent edge and a
  5 dp progress bar.
- **Cast:** 80 dp circles in 92 dp wide items, with initials when there is no photo.

### 1.4 Player, settings, the rest

- **Player:**
  - Scrims: a 160 dp top scrim (black 70 per cent to clear) and a 260 dp bottom scrim (clear to
    black 80 per cent).
  - Top bar: the title at 18 sp on a phone (up to 28 sp on large screens), with the episode line
    at 90 per cent and the metadata at 70 per cent.
  - Centre: bare icons with no discs.
  - Seek bar: a 6 dp track that grows to 10 dp while dragging. The extras sit in one pill (black
    50 per cent, radius 24, 1 dp white 20 per cent edge).
  - Timing: fade in 250 ms, fade out 200 ms, auto-hide after 3.5 s.
- **Settings:** grouped cards on the elevated surface, radius 12 (16 on a tablet), with a 0.5 dp
  edge and dividers inset 66 dp. Each row has a 36 dp icon chip (accent at 12 per cent, radius 12).
  Section labels are 12 sp Bold, muted, with +0.8 tracking.
- **Type:** one family, three weights. The scale runs 11, 12, 13, 14, 15, 16, 18, 22, 26, 28, 32,
  38 and 48 sp, and the page title is 38 sp Bold with -1.2 tracking. Nuvio locks the system font
  scale to 1.0. **We will not**: see section 5.
- **Radii:** 4, 6, 8, 12 (posters), 16 (buttons), 24 (cards, sheets, the player pill), 28
  (dialogs), and full for chips.
- **Motion:**
  - Durations: 150 fast, 220 normal, 300 / 250 sheet in and out, 400 slow, 700 cinematic.
  - Easing: the standard curve is `(0.2, 0, 0, 1)`.
  - Opening a poster: a 550 ms container transform, with the page behind receding to a 0.9 scale.
  - Images cross-fade in everywhere.
- **Television focus** (`MotionFocusTokens.kt`, `FocusRingStyle.kt`): a 2 dp ring in the focus
  colour, scale 1.02 (0.98 pressed), 180 ms FastOutSlowIn. Rows keep the focused card 42 per cent
  into the viewport. The "Modern" home gives the bottom 52 per cent of the screen to rows and the
  top to a backdrop of whatever is focused.
- **Missing art:** a surface card with the title centred in muted text, initials for people, and a
  plain surface for a continue card with no image. This is the weakest part of Nuvio, and the part
  TMPlayer needs most (section 3).

### 1.5 What makes it look good, in one list

1. **The picture is the interface.** Tiles have no card chrome at rest: art, a radius, a caption
   under it. Chrome appears only on focus or hover.
2. **One deep, neutral ground**, so artwork supplies the colour. The accent appears in three
   places: progress, selection, focus.
3. **Long, soft gradients** (many stops, hundreds of dp tall) under every piece of text that sits
   on a picture, instead of plates or boxes.
4. **One bright primary action** per screen, a tall pill. Everything else is quieter.
5. **Big type where it matters** (hero and page titles) and small, calm type everywhere else.
6. **Motion that moves pictures** (scale, parallax, cross-fade) rather than colours.

## 2. TMPlayer today, measured

TMPlayer already has a sound token layer: `ui/theme/Palette.kt` (`TmSchemes`, `Corner`, `Avatar`,
`Tv`), `ui/theme/Surfaces.kt` (`Floating`, `Focus`, `FloatingTone`) and `ui/theme/Tone.kt` (roles
by job). Every surface asks `Tone` for a colour, and dynamic colour on Android 12 and later can
replace the scheme. That architecture stays. The gaps are in the figures and in the composition of
each screen.

| Area | Today | Gap against the patterns above |
|---|---|---|
| Ground | Dark `#0F1417`, Telegram blue hue in every surface step; containers `#171C1F` to `#313539` | Reads blue-grey rather than cinematic; tile fills and the page are only one step apart, so cards look like panels |
| Tiles (TV, desktop) | `MediaCard` in `app/.../MediaGridScreen.kt`: 16:9 art, then a filled panel (`Tone.surface`) with a 1 dp hairline and a 3 dp accent border on focus, title `titleMedium` on two fixed lines, meta line; no scale on focus. Desktop `Poster` in `desktop/.../MediaGrid.kt` lifts to 1.05 on hover | Card chrome at rest; focus repaints the border but the picture does not move; two reserved title lines make every tile tall |
| Tiles (phone) | Dense: 16:9 art at `Corner.Small` (8), `bodySmall` title on two fixed lines, meta line, duration plate and a black quality plate on every tile | Plates on every picture ("1080p" repeated across a whole grid) are noise; the meta line repeats the duration that is already on the art |
| Missing art | `MediaPreview` (`app/.../components/Art.kt`) and `MediaArt` (`ui/.../components/MediaArt.kt`): the first letter of the title on `Tone.surface` | A grid of grey squares with one letter; lectures and screen recordings without thumbnails look broken |
| Blurry art | 40 px inline preview stretched, `blur(16.dp)` only where the platform has it | Fine as a stopgap; no fade from the mini to the full thumbnail (it pops) |
| Home | `app/.../HomePane.kt`, `desktop/.../Home.kt`: rows of 16:9 tiles (152 dp phone, 208 dp TV), Continue first; no hero | No spotlight: Home opens on a row heading. Nothing large, nothing that changes with focus |
| Continue | `MediaCard` with a `WatchPoint` bar; `ContinueCard` in `BrowseScreen.kt` for History | Text is under the art, not on it; no "time left"; the bar is 6 dp on a black band |
| Detail | `ui/.../MediaDetailScreen.kt`: already a cinema layout (backdrop on the top end, 70 x 80 per cent, two linear fades, title block at 55 per cent width, action pills, cast, more like this) | Closest to the target. Fades are two- and three-stop and visibly banded; the facts are one muted line; Play is one pill among equals; the description is not collapsible |
| Series | `ui/.../Series.kt` `SeriesPanel`: poster (when online), title, a muted summary line, overview, season tabs, episode rows with a 128 / 176 dp thumbnail; phone opens it in a sheet (`SeriesScreens.kt`), TV as a page | No backdrop; the heading looks like a list header, not a show page. Episode rows have no surface |
| Series tile | `SeriesArt`: thumbnail with two "stacked edges" above it and an episode count plate | The stack is clever but reads as a rendering glitch at small sizes |
| Chat list | TV: expanded rail (about 22 per cent of the width), large chat cards with an accent border on focus. Phone: Telegram-like rows | Rail always open on TV; content starts a fifth of the way across |
| Settings | Rows and section titles in `SettingsScreen.kt`, desktop `Settings.kt` | Flat list; no grouping cards, no icon chips |
| Player | Android: `player_controls.xml` with top and bottom scrim drawables, CP37 decluttered rows. Desktop: `PlayerOverlay.kt` | Structure is right. Title block, scrim heights and the button group can match the pattern without moving anything |
| Type | Material 3 scale on touch, a separate TV scale in `app/.../theme/Theme.kt` (headlineLarge 32 Bold, titleLarge 21) | No display style for a hero title; page headings vary per screen |
| Motion | `tween(140)` here, `animateFloatAsState` defaults there; no tokens | No shared durations or easing |

## 3. Constraints that shape the target

1. **Telegram art is 16:9 and often small.** Most videos have only a Telegram thumbnail (320 px
   wide at best) and a 40 px inline preview. Posters (2:3) exist only with online metadata on, and
   only for matched films and shows. **Tiles stay 16:9.** A 2:3 poster appears only on the
   detail and series pages, where it already does.
2. **Many files have no art, or the art is a slide.** Lectures, screen recordings, courses. The
   design must make a file with no art look intended: a **generated cover**, not a letter.
3. **Two sources of art, one look.** Every picture slot takes the best of this chain:
   1. the online wide picture (`MetaInfo.wideUrl`), when metadata is on and matched;
   2. the full Telegram thumbnail;
   3. the inline preview, upscaled (which is already a blur);
   4. the generated cover.

   The swap between them is a 220 ms cross-fade, never a pop.
4. **Low-end television sticks.** No `RenderEffect` blur in lists, no blur at all below Android
   12, no animated blur anywhere. A backdrop blur is made by **scaling down**: decode the
   thumbnail at 24 to 32 px wide and draw it stretched with bilinear filtering. That costs one
   tiny bitmap and no shader. On `isLowRamDevice` the hero does not auto-advance and nothing has
   parallax.
5. **Dynamic colour and light theme stay.** Every new colour is a `Tone` role, defined for both
   schemes. The phone's wallpaper scheme still wins when chosen.
6. **No new radius.** `Corner` keeps its five steps. Nuvio's 12 dp poster radius is our
   `Corner.Medium`, its 28 dp dialog is `Corner.ExtraLarge`; pills and chips are `CircleShape`.

## 4. Target design

### 4.1 Tokens

**Ground and surfaces** (`ui/theme/Palette.kt`, dark scheme only; the light scheme keeps its
values). Keep the Telegram hue in `primary`, but take most of the chroma out of the neutrals so
artwork supplies the colour:

| Role | Today | Target |
|---|---|---|
| background, surface, surfaceDim | `#0F1417` | `#0C0E10` |
| surfaceContainerLowest | `#0A0F12` | `#08090A` |
| surfaceContainerLow (`Tone.surface`) | `#171C1F` | `#15181B` |
| surfaceContainer | `#1B2124` | `#1A1D20` |
| surfaceContainerHigh (`Tone.surfaceHigh`) | `#262B2E` | `#23272A` |
| surfaceContainerHighest | `#313539` | `#2D3134` |
| outlineVariant (`Tone.outline`) | `#40484C` | `#2E3337` |
| onSurface | `#DEE3E7` | `#F2F4F5` |
| onSurfaceVariant (`Tone.muted`) | `#C0C8CD` | `#AEB5BB` (9.3:1 on the new background, 8.6:1 on `Tone.surface`) |

`primary` `#79D0F5` stays (11.2:1 on `#0C0E10`). A new AMOLED choice is out of scope.

**New roles in `Tone`:**

- `Tone.scrim`: background at full alpha, the colour every gradient fades into. It is the
  background, so it follows light and dark.
- `Tone.onArt` (`Color.White`) and `Tone.onArtMuted` (white at 72 per cent): text that sits on a
  picture under a scrim, in both themes. Pictures are dark under our scrims in both themes.
- `Tone.track` (white at 30 per cent): a progress track on art.
- `Tone.hero` and `Tone.onHero`: the primary pill. In dark, it is `onSurface` with `background`
  for the label. In light, it is `inverseSurface` with `inverseOnSurface`. It is a near-white pill
  on dark and a near-black pill on light, with a contrast of at least 15:1 either way.

**Scrims**, new file `ui/theme/Scrims.kt`, as brushes built from `Tone.scrim`:

- `Scrims.bottomLong(height)`, Nuvio's seven-stop curve: at 0, .16, .32, .50, .68, .84 and 1 of
  the height, alpha 0, .04, .14, .34, .62, .84 and 1. This is for the hero and the detail backdrop.
- `Scrims.startLong`: the same curve horizontally, from the start edge, for text on the start
  half of a wide backdrop. It must mirror in RTL.
- `Scrims.tileFoot`: from 45 per cent of the tile height, to 0.70 at 0.6 and to 0.95 at 1. It is
  in black, not the background, because it sits inside a tile. This is for continue cards and
  generated covers.

**Motion**, new file `ui/theme/Motion.kt`:

- Durations: `Fast` 150 ms, `Normal` 220 ms, `Slow` 400 ms.
- Easing: `Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)`.
- Focus scale: `FocusScale = 1.05f` for the TV and desktop tiles. 1.05 rather than Nuvio's 1.02:
  our tiles are smaller in dp and the sofa is further away. The desktop's hover already uses it.
  `PressScale = 0.97f`.
- Hero advance: `HeroAdvanceMs = 10_000`, first advance at 20 s, as on Nuvio's television. Off on
  low RAM devices and when animations are off (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`).
- `rememberReducedMotion()`, expect and actual: true when the system turns animations off, read
  by everything that moves.

**Type.** Add one style to both scales: `displaySmall` (touch 32/36 sp, TV 36/40 sp), Bold, -0.5
sp tracking, for the hero and page titles. Row headings become `titleMedium` SemiBold on touch
(16 sp) and `titleLarge` SemiBold on TV (21 sp, unchanged). The system font stays: we ship 21
languages with bundled Noto fallbacks, and a display font would break that.

**Focus** (`Surfaces.kt`). `Focus.Edge` stays at 3 dp and in the accent. Focus on a tile becomes
three things at once: the ring, `FocusScale`, and the caption going from muted to full text. Focus
is never colour alone.

**Spacing.** No token object. The figures to use are 4, 8, 12, 16, 24, 32 and 48, with screen
side margins as follows:

- phone: 16 dp;
- desktop: 24 dp, and 32 dp above 1440 dp;
- television: `Tv.SafeH` (48 dp).

Row gaps are 10 dp on touch and 16 dp on TV. Section gaps are 24 dp on touch and 28 dp on TV.

### 4.2 Shared pieces (new, in `ui/`)

- **`GeneratedCover`** (`ui/components/GeneratedCover.kt`). The art for a file with no
  thumbnail, and the backdrop of last resort.
  - **Colour:** a diagonal two-stop gradient picked from a table of eight hand-tuned dark pairs,
    by a stable hash of the chat id and the parsed title. Every pair keeps white text at 4.5:1 or
    more, and the colours are fixed in both themes.
  - **Text:** the parsed title in `titleMedium` Bold, white, up to three lines, bottom start, over
    `Scrims.tileFoot`.
  - **Numbers:** where `MediaName` finds an episode, part, lecture or chapter number, a large
    numeral (displaySmall, white at 22 per cent) sits at the top end. A course looks like a
    numbered set, not a pile of grey squares.
  - **Chat:** the chat's initial goes in a small circle at the top start, only on Home and in
    search, where the chat is not obvious.
  - **Usage:** it replaces the letter fallback in `MediaArt`, `MediaPreview`, the desktop
    `MediaTile` and `SeriesArt`.
- **`ArtBackdrop`** (`ui/components/ArtBackdrop.kt`). A large picture behind a hero or a page:
  - Chain: the online wide picture (decoded at 1280 px), else the full thumbnail, else the inline
    preview, else `GeneratedCover`.
  - Small art: when the best available picture is under 480 px wide, it does not stretch it
    sharp. It draws `Thumbnails.backdrop(fileId)`, a 32 px wide decode drawn with
    `FilterQuality.Low` across the frame, which is a soft colour field, plus the sharp thumbnail
    fitted at its natural aspect at the top end of the frame. The art then reads as art, not as a
    blur of pixels.
  - Cost: no `blur()` call.
  - Cross-fade: 220 ms between steps of the chain.
- **`Thumbnails.backdrop(fileId)`** (`ui/data/Thumbnails.kt`): a cached 32 px decode of the full
  thumbnail (falling back to the inline preview), keyed `backdrop:<fileId>`.
- **`MediaArt` cross-fade** (`ui/components/MediaArt.kt`): mini to full in `Motion.Normal`.
  Remove the quality plate from the default and keep it for the detail facts. Keep the letterbox
  rule.
- **`ProgressLine`**: 3 dp on touch, 4 dp on TV, inset 8 dp, `Tone.accent` on `Tone.track`, square
  ends. It replaces `Bar` in `Series.kt` and the bar in `MediaArt` (app).

### 4.3 Tiles and grids (phone, TV, desktop)

The video tile becomes **art first**. A sketch follows.

```
 ┌──────────────────────────┐   radius Corner.Medium (12)
 │                          │   16:9 art via the chain; GeneratedCover when empty
 │                    24m   │   duration plate: phone grid only (no meta line there)
 │▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀           │   ProgressLine, inset 8 dp, only with a saved place
 └──────────────────────────┘
  Coast walk, day 2            title: 1 line (2 in the phone grid), titleSmall / TV titleMedium
  24m · 428 MB                 meta: labelMedium, muted; turns Tone.text on focus
```

- **No panel at rest.** No fill and no hairline, on any platform. In light theme the art's own
  edge is enough, because the art is never white: `GeneratedCover` is dark and thumbnails are
  pictures.
- **TV focus:** scale `FocusScale` around the art's centre, with a 3 dp `Tone.accent` ring on the
  art only (`Focus.Edge`, `Corner.Medium`), and the caption goes to full text, all in
  `Motion.Fast`. `Tv.FocusClearance` (8 dp) covers the growth of a 208 dp tile (5.2 dp per side);
  a wider tile needs `FocusClearance` raised to 12 dp.
- **Desktop hover and focus:** the same scale and ring. The hover play button stays.
- **Phone:** press scale `PressScale` for 100 ms, and the ripple clipped to the art.
- **Badges:** at most one corner badge per tile, in this order: queue state, then Downloaded or
  Cached, then a watched tick. Quality ("1080p") leaves the tile for the meta line and the detail
  facts. Badges are 20 dp tall pills (`CircleShape`), black at 64 per cent, with white
  labelSmall.
- **Grid:** phone 3 columns portrait and 5 landscape, 10 dp gaps. TV adaptive at 208 dp minimum,
  16 dp gaps. Desktop keeps `PosterSizeStep`.
- **Series tile** (`SeriesArt`): drop the stacked edges. Put a 22 dp "12 episodes" pill at the
  bottom start over `Scrims.tileFoot`, and the progress line under it. With online metadata on,
  the backdrop as now.
- **Continue card**, wider: 1.25 x the tile width.
  - Art: the art with `Scrims.tileFoot`.
  - Text on the art, bottom start, in `Tone.onArt`: "S1 E3", "Part 4" or "Lecture 7" in
    labelMedium SemiBold, then the title in titleSmall SemiBold on one line.
  - Under the art: "24 min left" in labelMedium, muted.
  - Progress line at the foot.

### 4.4 Home

```
 phone (portrait)                         TV / desktop (wide)
 ┌──────────────────────┐                 ┌───────────────────────────────────────────┐
 │  ArtBackdrop         │ 56% of height   │ text column 45%      ArtBackdrop 65% x 70% │
 │                      │ clamp 320..520  │ TITLE (displaySmall)    fades start + foot │
 │                      │                 │ S2 E4 · 42m · Chat name                    │
 │ TITLE                │                 │ overview, 2 lines (online meta only)       │
 │ S2 E4 · 42m · Chat   │                 │ [ ▶ Resume ]  ( i )                        │
 │ [ ▶  Resume       ]  │                 ├───────────────────────────────────────────┤
 │ • ━━ • •             │ dots            │ Continue watching  ▸ tiles ...             │
 ├──────────────────────┤                 │ Favourite chat     ▸ tiles ...             │
 │ Continue watching  ▸ │                 └───────────────────────────────────────────┘
 │ [card][card][ca      │
```

- **Spotlight** (the hero). It holds up to five items, chosen in core (`HomeRows.spotlight`,
  unit tested):
  - first, the most recent unfinished Continue record;
  - then the newest unwatched video from each favourite chat (one per chat);
  - then the newest overall.

  Shows count once, by their next episode. With nothing to show (a fresh account with no
  favourites), there is no spotlight and Home looks as it does today.
- **Phone:**
  - Size: 56 per cent of the height, clamped to 320 to 520 dp. In landscape it takes the wide
    layout at 70 per cent of the height.
  - Shape: bottom corners at `Corner.ExtraLarge`.
  - Backdrop: `ArtBackdrop` with `Scrims.bottomLong(220.dp)`.
  - Text, centred: the title in displaySmall on two lines (a TMDB title when matched), then a
    meta line in labelLarge, muted, that combines the episode label, duration and chat name.
  - Action: one `Tone.hero` pill, 52 dp tall, at most 420 dp wide, reading Resume or Play.
  - Swiping changes the item. The dots are 6 dp, the active one 24 dp wide.
  - Auto-advance every 8 s; pressing or scrolling stops it until Home is reopened.
- **TV:**
  - Layout: the "focused backdrop" model. The top 45 per cent of the screen is the spotlight;
    rows sit under it. While focus is in the rows, the spotlight shows **the focused tile**: its
    backdrop, title and meta change after a 300 ms settle, so a fast D-pad sweep does not decode
    every tile.
  - Actions: while focus is on the spotlight's own Resume pill, it advances on
    `HeroAdvanceMs`. The pill and an info button are the only focusables there. Up from the first
    row lands on Resume.
  - Fades: `Scrims.startLong` and `bottomLong`.
  - Low RAM: the backdrop is the 32 px field only, and nothing advances.
- **Desktop:** like the TV, with the spotlight 360 to 440 dp tall. Hover over a tile does not move
  the spotlight (that would flicker under a moving pointer). Keyboard focus does.
- **Rows:**
  - Headings: titleMedium (touch) or titleLarge (TV) SemiBold. "See all" becomes a 32 dp round
    chevron chip on touch and stays a focusable text button on TV.
  - Spacing: rows 24 dp apart on touch and 28 dp on TV.
  - The first row overlaps the spotlight's fade by 24 dp, so the page reads as one surface.

### 4.5 Chat media grid

- The header stays. On a phone the chat avatar becomes a 40 dp `Avatar.Compact`. On TV the
  heading takes displaySmall and drops the "Videos from this chat" line.
- The tile from section 4.3.
- Topic tabs (`TopicTabs.kt`) become quiet pills: `CircleShape`, `Tone.surface` at rest, and
  `Tone.focusFill` when selected or focused, 36 dp on touch and 44 dp on TV.

### 4.6 Detail page (`MediaDetailScreen`)

The structure stays (cinema on wide, stacked on tall). What changes:

- **Backdrop:** `ArtBackdrop` replaces `MediaArt` plus `MetaPicture`. The two linear fades become
  `Scrims.startLong` and `Scrims.bottomLong(320.dp)`. The stacked layout's pinned 16:9 frame
  becomes a backdrop of width x 0.9 (clamped 280 to 520 dp) that scrolls with a 0.5 parallax
  instead of being pinned. With reduced motion it is pinned, as now.
- **Title:** displaySmall, up to 3 lines.
- **Facts row:** chips replace the muted line, as `labelLarge` text separated by 4 dp dots: year,
  runtime, then "S2 E4", then quality (an outlined chip, 1 dp `Tone.outline`, `Corner.ExtraSmall`),
  then size, then the rating when online. Genres sit on their own line, muted, dot separated, at
  most three. For a lecture with no metadata, the row reads "Part 4 · 52m · 1080p · 1.2 GB" and
  the chat name sits above the title in labelLarge, accent.
- **Synopsis:** three lines on touch and four on TV, then "More" expands it. On TV the synopsis is
  focusable only when it is cut.
- **Actions:**
  - Primary: one `Tone.hero` pill, 52 dp tall (TV 48 dp), at most 420 dp wide (full width when
    stacked): Resume, with "24 min left" in it, or Play.
  - Secondary actions (Start over, Download, Mark watched, Episodes, Trailer, Versions) become
    48 dp round buttons with labels under them on touch, and pills on TV.
  - TV focus: on the hero pill, focus swaps it to `Tone.focusFill` with the ring and scale. At
    rest it is `Tone.hero`, so the two states differ in fill, ring and size.
- **Cast:** circles 72 dp on touch and 88 dp on TV, initials in `GeneratedCover` colours when
  there is no photo.
- **More like this:** 2:3 posters at 112 dp touch and 140 dp TV, `Corner.Medium`.

### 4.7 Series page

The phone stops opening a show in a sheet. A show gets a page with the detail page's hero: it
uses `ArtBackdrop` (the show's backdrop when online, else the next episode's thumbnail), and the
2:3 poster at the start when online. Under the hero:

- **Heading:** the title in displaySmall, then the facts row: year, then "3 seasons · 24
  episodes", then "12 of 24 watched".
- **Primary action:** the hero pill "Resume S2 E4" or "Play S1 E1".
- **Season selector:** the existing `SeasonTabs`, as `CircleShape` chips, 36 dp touch and 44 dp
  TV. More than eight seasons become a dropdown on touch and the desktop. The desktop
  `SeasonDropdown` stays.
- **Episode rows:** a card on `Tone.surface` with a 1 dp `Tone.outline` edge and
  `Corner.Medium`. The thumbnail is 16:9 at 128 dp touch and 176 dp TV, with `ProgressLine` at its
  foot. The text is the number and title in titleSmall, then duration and size in labelMedium
  muted, then the overview on two lines when online. The next episode carries an "Up next" pill
  in accent.
- **Copies** keep `CopyChip`, restyled as `CircleShape` chips.
- **Lectures:** for a show parsed from "Lecture 1..n" or "Part 1..n" names there are no seasons.
  The header reads "Course · 18 parts", and the rows use `GeneratedCover` numerals when there are
  no thumbnails.

The television keeps `TvSeriesPage` as a page, restyled to match. The player's episode list
(`EpisodesPanel.kt`) takes the new `EpisodeRow` for free.

### 4.8 Chat list and navigation

- **TV rail:** it collapses to a 72 dp icon column (plus `Tv.SafeH`) whenever focus is in the
  content, and expands to today's width with labels when focus enters it, animated over
  `Motion.Normal`. Content gains about 20 per cent of the width. The selected destination keeps
  its `Tone.focusFill` pill when collapsed; the brand mark shows only when expanded.
- **TV chat cards:** a 64 dp avatar, the title in titleLarge, and a meta line. On focus: scale
  1.02 (a full-width row scaled 1.05 would leave the safe area), the ring, and `Tone.surfaceHigh`.
  At rest: no hairline.
- **Phone chat rows:** unchanged in structure (they are Telegram's idiom). The new ground and a
  10 dp gap rhythm apply.
- **Desktop sidebar:** unchanged in width; the selected item becomes a `CircleShape` pill on
  `Tone.surfaceHigh` with an accent icon, matching the TV rail.

### 4.9 Settings (phone, TV, desktop)

- **Groups:** sections become grouped cards on `Tone.surface` with `Corner.Large` and no hairline
  in dark (a 1 dp `Tone.outline` in light). Dividers inside are inset to the text start.
- **Rows:** 56 dp min touch and 64 dp TV. A 36 dp icon chip leads each row (`Tone.accent` at 12
  per cent, `Corner.Medium`, 20 dp icon in `Tone.accent`).
- **Section labels:** `labelMedium` Bold, muted, +0.8 sp tracking, uppercase only where the
  locale has case (`Locale`-aware `uppercase`).
- **TV focus:** the row fills with `Tone.focusFill` as today; the icon chip inverts.

### 4.10 Player

The CP37 layout stays exactly as it is (which buttons, which rows, where focus lands). Only the
chrome changes:

- **Scrims** (`bg_controls_scrim_top.xml`, `bg_controls_scrim_bottom.xml`): top 160 dp, black 70
  per cent to clear; bottom 260 dp, clear to black 80 per cent, with five stops rather than two.
  Same on the desktop overlay.
- **Title block, top start:** the title in 18 sp phone and 24 sp TV and desktop, SemiBold, white.
  Under it, "S2 E4 · Episode title" or "Lecture 7" plus the chat name, 14 sp, white at 72 per cent.
- **Seek bar:** a 4 dp track that grows to 8 dp while scrubbing (150 ms), the track in white at 30
  per cent, the buffered range in accent at 35 per cent, and the fill in accent. Times are white
  at 72 per cent.
- **Bottom buttons:** the existing row sits in one pill (black 50 per cent, `CircleShape`
  ends, 1 dp white 16 per cent edge). On TV a focused button fills white with a dark icon.
- **Timing:** fade in 250 ms and out 200 ms. Auto-hide stays at the current timeouts (the TV
  hide timer is tuned to the remote and is not touched).
- **`PlayerLoader`:** keep the drift. Use `ArtBackdrop` for the picture so a video with no
  thumbnail shows its `GeneratedCover` rather than the plain surface.

## 5. Accessibility and performance rules

- **Contrast:** text on a picture always sits on a scrim that reaches at least 0.84 background
  alpha under its baseline, which gives `onArt` and `Tone.text` 4.5:1 or more over any picture.
  Check it by drawing the hero over a pure white test image in the render test. Muted text on
  the ground stays at 4.5:1 or more in both themes (the dark target above is 9.3:1).
- **Font scale:** never lock it. Hero heights are minimums: text grows the hero rather than
  clipping. Titles have max lines with ellipsis, and buttons wrap their labels rather than
  truncating them. Check at 1.3 and 2.0 on the phone emulator.
- **TV focus:** every focusable shows a 3 dp ring and a scale or fill change, and focus is never
  carried by colour alone. Focus must stay inside `Tv.SafeH` / `Tv.SafeV` after scaling.
- **Motion:** respect reduced motion: no auto-advance, no parallax and no scale (the ring
  remains). Cross-fades stay, because they are not motion.
- **Performance:**
  - No `Modifier.blur` in a lazy list.
  - One hero backdrop decode at a time, cancelled on focus change, and none at all during a
    D-pad sweep (the 300 ms settle).
  - The 32 px backdrop decode goes through the existing `Thumbnails` cache.
  - `GeneratedCover` is drawn as a gradient plus text, with no bitmaps.
  - Budget: the D-pad scroll on the 1 GB TV emulator profile must stay within 1 point of the CP28
    figure (6 per cent janky frames on the grid, 5.1 on Home).
- **Right to left:** the start fades, the text column and the hero dots mirror.

## 6. Work packages

Six packages, ordered. WP1 lands first; after it, WP2 and WP6 can run in parallel; WP3 and WP4
need WP2's tile; WP5 can run beside WP3 and WP4. Each package owns the files listed. A file not
listed under a package is not edited by it, except that each package may **add** its own new
test files. Strings go into `en.json` only; the other locales are left for the user to sync.

### WP1 Foundations: tokens, scrims, motion, generated art, backdrop

Owns:
- `ui/src/shared/kotlin/com/tmplayer/ui/theme/Palette.kt` (dark neutrals, `displaySmall`)
- `ui/src/shared/kotlin/com/tmplayer/ui/theme/Tone.kt` (new roles)
- `ui/src/shared/kotlin/com/tmplayer/ui/theme/Surfaces.kt` (focus notes)
- `ui/src/shared/kotlin/com/tmplayer/ui/theme/Scrims.kt` (new)
- `ui/src/shared/kotlin/com/tmplayer/ui/theme/Motion.kt` (new, with the reduced motion
  expect and actual in `ui/src/androidMain` and the desktop source set)
- `app/src/main/java/com/tmplayer/ui/theme/Theme.kt` (TV `displaySmall`, TV scheme mapping)
- `ui/src/shared/kotlin/com/tmplayer/ui/components/GeneratedCover.kt` (new)
- `ui/src/shared/kotlin/com/tmplayer/ui/components/ArtBackdrop.kt` (new)
- `ui/src/shared/kotlin/com/tmplayer/ui/components/ProgressLine.kt` (new)
- `ui/src/shared/kotlin/com/tmplayer/ui/components/MediaArt.kt` (cross-fade, `GeneratedCover`
  as the default fallback)
- `ui/src/shared/kotlin/com/tmplayer/data/Thumbnails.kt` (`backdrop`)
- `app/src/main/java/com/tmplayer/ui/components/Art.kt`
- Mechanical move, no visual change: `SeriesArt`, `Plate` and `Bar` from
  `ui/.../browse/Series.kt` into a new `ui/.../browse/SeriesArt.kt`, so WP2 and WP4 do not
  share a file.

Acceptance:
- Unit tests:
  - the `GeneratedCover` pair is stable for a given chat and title;
  - every pair gives white 4.5:1 or more;
  - the numeral comes out of "Lecture 07", "Part 4" and "S01E03".
- A new desktop render test (`DesignTokensRenderTest`) writes, in light and dark, a sheet of
  `GeneratedCover`s, the three scrims over a white and a black image, and `ArtBackdrop` from each
  step of the chain. `ThemeAuditRenderTest` still passes.
- On the phone emulator (promo flavor) the chat list, grid and settings render with the new
  ground in dark and light, with no regressions. Wallpaper colour still applies when chosen.

### WP2 Video tiles and grids

Owns:
- `app/src/main/java/com/tmplayer/ui/browse/MediaGridScreen.kt` (`MediaCard`, `MediaCardBody`,
  the private `MediaArt`, `MetaLine`, grid spacing)
- `desktop/src/main/kotlin/com/tmplayer/desktop/ui/MediaGrid.kt` (`Poster`, `MediaTile`,
  `ContinueTile`, `Badge`)
- `ui/src/shared/kotlin/com/tmplayer/ui/browse/SeriesArt.kt`
- `ui/src/shared/kotlin/com/tmplayer/ui/components/WatchedBadge.kt`
- `ui/src/shared/kotlin/com/tmplayer/ui/components/Skeleton.kt` (tile skeletons match the new
  tile)
- `ui/src/shared/kotlin/com/tmplayer/ui/browse/TopicTabs.kt`

Acceptance:
- Phone emulator, promo flavor:
  - the grid (`--es screen media`), portrait and landscape, dark and light;
  - a file with no art shows a `GeneratedCover`;
  - font scale 1.3 and 2.0 do not clip.
- TV emulator:
  - the grid, with a D-pad walk across two rows: the focused tile scales and rings, its
    neighbours are not clipped, and the top row is not cut by the header;
  - jank on the 1 GB profile is within budget.
- Desktop: `PromoShots` grid and the Continue and Watched pages, hover and keyboard focus.

### WP3 Home spotlight and rows

Owns:
- `core/src/commonMain/kotlin/com/tmplayer/data/HomeRows.kt` (`spotlight`)
- `ui/src/shared/kotlin/com/tmplayer/ui/browse/HomeViewModel.kt`
- `ui/src/shared/kotlin/com/tmplayer/ui/browse/HomeSpotlight.kt` (new, shared hero composable
  with phone and wide layouts)
- `app/src/main/java/com/tmplayer/ui/browse/HomePane.kt`
- `desktop/src/main/kotlin/com/tmplayer/desktop/ui/Home.kt`
- `app/src/promo/java/com/tmplayer/PromoCaptureActivity.kt` (a Home fixture with a resume
  record, favourites, one file without art, and the meta switch)
- `desktop/src/test/kotlin/com/tmplayer/desktop/promo/PromoShots.kt` (a `home` shot, with and
  without online metadata)

Acceptance:
- Unit tests for `spotlight`:
  - order;
  - one entry per chat;
  - a show counts once;
  - empty when there is nothing to show;
  - at most five.
- Phone emulator, promo flavor, `--es screen home`:
  - portrait and landscape, dark and light;
  - with `--ez meta true` and without;
  - the swipe and the dots work;
  - with animations off there is no auto-advance.
- TV emulator:
  - Home opens on Resume;
  - Down enters the rows, and the spotlight follows focus after the settle;
  - Up returns to Resume;
  - a fast sweep shows no intermediate decodes (log the decode count);
  - on the 1 GB profile the backdrop is the soft field and Home's jank stays within budget.
- Desktop: `PromoShots` home in both themes, at 1280 and 1920 wide.

### WP4 Detail and series pages

Owns:
- `ui/src/shared/kotlin/com/tmplayer/ui/browse/MediaDetailScreen.kt`
- `ui/src/shared/kotlin/com/tmplayer/ui/browse/MediaDetail.kt`
- `ui/src/shared/kotlin/com/tmplayer/ui/browse/Series.kt` (`SeriesPanel`, `SeasonTabs`,
  `EpisodeRow`, `CopyChip`, the new series hero)
- `ui/src/shared/kotlin/com/tmplayer/ui/browse/SeasonsSection.kt`
- `ui/src/shared/kotlin/com/tmplayer/ui/online/MetaExtrasUi.kt` (cast, more like this,
  trailer pill)
- `ui/src/shared/kotlin/com/tmplayer/ui/player/EpisodesPanel.kt` (only if `EpisodeRow`'s
  signature changes)
- `app/src/main/java/com/tmplayer/ui/browse/SeriesScreens.kt` (phone page instead of sheet, TV
  page, `SeriesCard`)
- `app/src/main/java/com/tmplayer/ui/browse/MediaDetailSheet.kt`
- `desktop/src/main/kotlin/com/tmplayer/desktop/ui/DetailPane.kt`
- `desktop/src/main/kotlin/com/tmplayer/desktop/ui/SeriesViews.kt`

Acceptance:
- Phone emulator, promo flavor:
  - the detail of a matched film, an episode, a lecture with no metadata and a file with no art;
  - the series page with online metadata on and off;
  - portrait and landscape, dark and light, font scale 2.0;
  - Back and the system back gesture close the series page as they closed the sheet.
- TV emulator:
  - the detail opens on Resume;
  - a D-pad walk through the facts, the actions, cast and more like this, then Back;
  - the series page: seasons by Left and Right, Down into the episodes, OK hold marks watched;
  - the player's episode list still works.
- Desktop: `PromoShots` `series-open`, `series-dropdown`, and a new `detail` shot.

### WP5 Shell: navigation, chat list, settings

Owns:
- `app/src/main/java/com/tmplayer/ui/browse/BrowseScreen.kt` (rail collapse, chat rows and
  cards, chips, the History `ContinueCard` using `ProgressLine`)
- `app/src/main/java/com/tmplayer/ui/settings/SettingsScreen.kt`
- `ui/src/shared/kotlin/com/tmplayer/ui/components/ChoiceRow.kt`
- `desktop/src/main/kotlin/com/tmplayer/desktop/ui/Shell.kt`
- `desktop/src/main/kotlin/com/tmplayer/desktop/ui/Chats.kt`
- `desktop/src/main/kotlin/com/tmplayer/desktop/ui/Settings.kt`
- `desktop/src/main/kotlin/com/tmplayer/desktop/ui/Common.kt` (`PageHeader`)

Acceptance:
- TV emulator:
  - the rail collapses when focus enters the chat list and expands on Left;
  - the selected destination is visible in both states;
  - no focus is lost or trapped;
  - de, ru and ml labels still fit the expanded rail.
- Phone emulator, promo flavor: chat list and settings, portrait and landscape, dark and light,
  Arabic.
- Desktop: `PromoShots` chats and the `AppShellShotsTest` settings frames.
- The site screenshots are retaken from these (WebP, quality 88, as `CLAUDE.md` says), but only
  when the user asks.

### WP6 Player chrome

Owns:
- `app/src/main/res/layout/player_controls.xml`
- `app/src/main/res/drawable/bg_controls_scrim_top.xml` and `bg_controls_scrim_bottom.xml`
- `app/src/main/java/com/tmplayer/player/PlayerControls.kt` (title block, seek bar sizes, fade
  timing only)
- `desktop/src/main/kotlin/com/tmplayer/desktop/player/PlayerOverlay.kt`
- `ui/src/shared/kotlin/com/tmplayer/ui/player/PlayerLoader.kt`

Acceptance:
- Phone emulator, promo flavor (`PromoPlayerActivity`): controls shown and hidden, a scrub, the
  episode title with and without metadata, portrait and landscape.
- TV emulator: the CP37 focus path is unchanged (opens on the bar, Down to the row, More, then
  Episodes). Same check in the TV `PlayerDeclutterShotsTest` equivalent.
- Desktop: `PlayerDeclutterShotsTest`, `PlayerLoaderRenderTest`, `SkipPillRenderTest` and
  `PromoShots` player still pass, with frames reviewed.

## 7. Out of scope, and open questions

- **Title logos.** TMDB has them (the `images` endpoint, `logos`), but `core/.../online/` does
  not fetch them. Adding them is a core change with its own cache and licence check. Until then
  the hero uses text. This is worth a later package, since it is a large part of Nuvio's look.
- **Trailer autoplay** in the hero: no. It costs data and battery on a Telegram client, and
  YouTube embedding on a TV stick is heavy.
- **Custom fonts:** no, for the 21 locales and the font fallback work in CP24.
- **2:3 poster rows** for matched films: possible later as a "Films" row on Home when online
  metadata is on, but a row mixing 2:3 and 16:9 tiles is worse than either. Not in these packages.
- **Light theme:** the patterns above are dark-first. Light keeps today's palette with the new
  structure (no tile chrome, scrims fading to the light background, `Tone.hero` near black). It
  needs its own visual review in every package's acceptance.
- **Decision for the user:** the phone series page replacing the sheet (WP4) changes navigation,
  not only looks. If the sheet should stay, WP4 restyles the sheet with the same hero at a
  reduced height instead.

## 8. Addendum: portrait posters and measured values (2026-10-13)

The user asked for portrait cards like Nuvio's, and then: "only take good inspirations from them make sure
the essence of the project is maintained." So the earlier choice of 16:9 tiles everywhere is replaced:

- **Films and shows** use 2:3 poster cards. Art comes from the online poster when there is one, else a
  portrait Telegram thumbnail as it is, else a landscape thumbnail set inside the card over its own soft
  backdrop with a bottom scrim (never cropped badly), else the generated cover in portrait.
- **Everything else** (Continue watching, episodes, clips, lectures, topic videos) keeps wide 16:9 cards
  that lead with information.
- **TMPlayer stays TMPlayer:** its own colours, accent, icons, wording and sidebar; chat names, versions,
  captions, quality and size stay visible; no ratings, no "trending", nothing that hides what a file is.

Values measured from Nuvio's source (NuvioMobile, NuvioTV) and the screenshots on nuvio.tv, used as a guide:

| Item | Phone | TV |
| --- | --- | --- |
| Poster | 126 x 189 dp, radius 12 dp, title under it (bodyMedium) plus a labelSmall line | same size, radius 12 dp |
| Gap between cards | 10 dp | 12 dp |
| Screen inset | 16 dp | about 52 dp start |
| Row title | titleLarge SemiBold, 10 dp to the row | titleMedium SemiBold, 24 dp between rows |
| Wide card | 16:9, about 247 dp, bottom 40% fade, label and title bottom left, time-left chip top right, 3 dp pill progress on a black 30% track | similar |
| Hero | 82% of the viewport clamped 360 to 760 dp, 220 dp fade to the background, centred meta line, one pill button | backdrop on the right 72% with left and bottom fades |
| Detail backdrop | width x 1.33 clamped 420 to 760 dp, 320 dp fade, 52 dp pill Play plus round icon buttons 12 dp apart | |
| Episode card | 296 x 184 dp, full-bleed thumbnail, scrim from 42% to 0.92, radius 12 dp; or a list with the thumbnail on the left | 320 x 207 dp |
| Focus | | 2 dp ring in the focus colour, scale 1.02 |

About 2.8 posters show per Home row on a phone in portrait, the cut third one hinting at the scroll; a
grid uses three columns.

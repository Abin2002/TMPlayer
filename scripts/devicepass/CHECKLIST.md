# Device pass: chat grid, bottom bar, search, copies, names

One pass over every target before the batch is committed (memory: test-before-commit-device-pass).
Order: connected phone, then the phone and TV emulators, then stop them and do the win11 VM. One
emulator set or VM at a time. Fix what is confusing before committing, not after.

Automated parts: `flows_phone.py`, `flows_tv.py` (each writes screenshots and a `REPORT.md`; a
missing control is a finding, not a crash). Everything marked **[hand]** needs eyes. Shots go
outside the repo, e.g. `/tmp/claude-1000/.../scratchpad/pass/<target>/`.

Test chats (memory): Cinema_paradasio (each video posted 5 to 10 times, a show re-uploaded under a
longer name, two series), HomeLander (x265 MKV episodes), STRIVER TUF (forum topics, saving off:
hold menu is only Play, Details, Mark watched). The chat list keeps the last chip: tap All.

## 0. Prepare

1. Builds (JDK 21: `JAVA_HOME=~/.jdks/jdk-21.0.12+8`): unit tests green for core, ui, app, desktop;
   `:app:assembleRelease` for the phone, `:app:assembleDebug` for the AVDs, the keyed desktop jars,
   and a CI run for the MSI.
2. Check the APK is really new (a stale versionCode has fooled us before):
   `~/Android/Sdk/build-tools/36.0.0/aapt2 dump badging app/build/outputs/apk/release/app-arm64-v8a-release.apk | grep ^package`
3. No dashes in new strings: `grep -rInP '\x{2014}|\x{2013}' --exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle .`
4. **Data baseline [hand], on each target before installing the new build:** Settings, Backup,
   Back up to a file and keep it. Write down: the Continue watching list (count, and one show with
   several episodes started), two resume points (video and minute), Watch later count, favourites
   count, one download that exists in two copies (see D3), Remove after watching on or off.

## P. Phone, POCO d3f7da23, release build (portrait AND landscape on every screen)

The user shares this phone: ask first, check it is free, stop at once if someone needs it. Release
over release keeps the login; never a debug APK here.

```sh
adb devices -l                                   # d3f7da23 must say "device"
CONFIRM_REAL=1 scripts/devicepass/emulators.sh install d3f7da23 app/build/outputs/apk/release/app-arm64-v8a-release.apk
python3 -I scripts/devicepass/flows_phone.py --serial d3f7da23 --real-device \
    --out <scratch>/pass/poco --query "love under" --chat Cinema_paradasio
```

The script restores auto rotate at the end (`wm user-rotation free`, accelerometer_rotation 1).
Then by hand, each in portrait and landscape:

**P1. First start after the update**
- Opens on Home with the floating bottom bar: Home, Chats, Search, profile picture. Expect no
  drawer, no hamburger, no "Only my folders" step. Look for: the bar hiding the last row or the
  Continue watching progress, the bar over the gesture area, a blank avatar while the photo loads.

**P2. Bottom bar and profile popover**
- Each bar button opens its page and lights up; pressing it again on its own page scrolls to top or
  does nothing (never reloads). Avatar opens the popover with Favourites, Downloads, History,
  Watch later, Settings, About, and Update only when an update waits. Counts on Favourites and
  Downloads match the pages. Back closes the popover first, then works as before.
- Confusing UX: which tab is lit while in Settings or a chat opened from Home; Back from Downloads
  landing somewhere unexpected; popover clipped in landscape; tapping outside not closing it.

**P3. Chats tab and folder chips**
- Chips mirror the Telegram app's own tabs, same names, same order, Archived where Telegram has it.
  Compare side by side with Telegram on the same phone. Empty folder shows "Nothing in this folder
  yet. Folders are edited in Telegram, on your phone." Chip choice is remembered as before.
- Confusing UX: chips scrolled off in landscape with no hint there are more; selected chip not
  visible after returning.

**P4. Chat grid and rows (feature 1)**
- A chat's grid: every card is a portrait poster, in the chat's own order (newest first unless
  sorted), no Films/Videos split. Home rows: the same portrait cards.
- Rows layout: each row has a small 2:3 poster and the cleaned name. Toggle back and forth; the
  scroll position stays reasonable.
- Names: a channel handle joined by underscores (e.g. `@Some_Channel_Film_Name`) keeps the film's
  name; a name ending only in tags loses them; nothing shows as an empty or "Video" title where the
  file had a real name.
- Confusing UX: two cards that look identical (copies not grouped, or grouped wrongly); a poster
  for a home video; titles cut to one word in landscape.

**P5. Back to top (feature 2)**
- In a long chat and in Chats: scroll far down, no button while scrolling down; scroll up past a
  screenful, a small round button appears; it goes on scrolling down, at the top, or after a few
  idle seconds; tapping jumps then glides to the top. Check it never covers the bottom bar, the
  selection bar or a card's menu, in both orientations.

**P6. Global search (feature 4)**
- Search opens over the current page with recent searches and recent chats. Type a title posted
  several times: local results come at once, Telegram results follow ("Searching every chat…").
  One card per video even when posted in several chats ("N copies", "In <chats>"); tapping opens
  the details page, not the player. Chats section shows matching chats, "Show all N chats".
- Filters (Any quality, Hide small clips, Include archived) and sorts (Best match, Newest, Largest,
  Best quality) change the list; Clear filters resets; a hidden-by-filter note explains an empty
  list. Recent searches: remove one, Clear all, Undo. Settings, Remember searches off: nothing
  new is kept.
- Offline (airplane mode, emulator only, see E): banner "Offline. Showing the videos this device
  already knows…" and local results only; going back online reruns the search.
- Confusing UX: the keyboard covering the first result in landscape; Back closing search when it
  should only drop the keyboard (or the opposite); results jumping while the user is about to tap;
  a result whose chat is gone ("That video is no longer in its chat").

**P7. Details page and copies (feature 5)**
- A video with several copies: line "from <chat> · Change" under the title, picked automatically
  (on this device first, then the viewer's audio language, then quality). Change opens "Copies of
  this video" with chat, date, size, quality, "On this device", "Saving off" where the chat
  forbids saving. Picking another copy updates the line and Play uses it.
- Player: Copies button; switching copy keeps the position ("Switching keeps your place"), the
  audio and subtitle choice where the new file has them.
- Progress: start copy A, stop at 10 min; open the same video from another chat: it says resume at
  10 min, and the Continue watching card is one card, not two.
- Confusing UX: the picked copy not being the one already downloaded; Change hard to find on a
  small screen; two progress bars disagreeing.

**P8. Corrections, extras, parts, sort (feature 6)**
- Hold a grouped card: Not the same video splits it out, "Group with its copies again" undoes it.
  Wrong match: the card falls back to its own name and picture; "Use the online match again"
  undoes. Rename: title (and year), Save, "Use the file name" resets; the new name shows on Home,
  search and the player title. All three survive an app restart and a backup/restore (D5).
- Extras: trailers, samples, teasers show as "<title> · Trailer" etc., not as the film itself, and
  never become the picked copy. Split parts (CD1/CD2, Part 1/2) show "2 parts" and play in order.
- Chat sort menu: Newest, Oldest, Name A to Z, Size, Year, Unwatched first; the note "Sorted among
  the N videos loaded so far" appears while more are loading. The choice is kept per chat (or
  globally: note which, and whether that is what a user expects).
- Confusing UX: Rename dialog in landscape with the keyboard up; corrections labels ("Not same" on
  the round button) unclear without the long label.

**P9. Rotation and RTL sweep [hand]**
- Rotate on: Home, Chats, a chat (grid and rows), search with results and keyboard, details,
  copies list, profile popover, sort menu, Rename dialog. Nothing resets, closes or loses its
  scroll. One quick look in Arabic (RTL): bar order, chevrons, back to top on the correct side.

## E. Phone emulator `tmplayer_phone_api36`, debug build

```sh
scripts/devicepass/emulators.sh start phone        # foreground: Bash run_in_background
scripts/devicepass/emulators.sh wait phone
scripts/devicepass/emulators.sh install phone app/build/outputs/apk/debug/app-universal-debug.apk
python3 -I scripts/devicepass/flows_phone.py --serial emulator-5554 --out <scratch>/pass/phone-avd
```

- Signed in already (memory: emulators-signed-in). If it asks to sign in, stop and ask the user to
  scan the QR before testing anything else.
- The `offline` flow runs here only (airplane mode on, search, off). Check P6's offline items.
- Repeat P4 to P8 quickly where the POCO lacked data; a cold start with `-no-snapshot-load` doubles
  as the "fresh process" check for remembered sorts and corrections.

## T. TV emulator `tmplayer_tv_api36`, debug build (never the real stick)

```sh
scripts/devicepass/emulators.sh start tv           # foreground: Bash run_in_background
scripts/devicepass/emulators.sh wait tv
scripts/devicepass/emulators.sh install tv app/build/outputs/apk/debug/app-universal-debug.apk
python3 -I scripts/devicepass/flows_tv.py --serial emulator-5556 --out <scratch>/pass/tv
```

- T1. Rail: Home, Chats at top level, Search; no "Telegram" or "Folders" group heading. Focus starts
  on content, Left reaches the rail, Back from the rail goes to Home and then asks to leave.
- T2. Chats page: chips match Telegram's tabs; Down from the chips goes into the list; focus never
  lands on an invisible item.
- T3. Chat grid: portrait posters in chat order; focus ring visible on every card; holding OK opens
  the menu with Not the same video, Wrong match, Rename (the TV keyboard for Rename works).
- T4. Search page: field, voice button ("Search by voice"), hint "Type or dictate with the remote",
  recent searches; results reachable with Down; OK on a video opens details; Change and the copies
  list work with the D-pad. Voice: the emulator has no mic; check the button opens the system
  prompt and Back returns.
- T5. No back to top button on TV (left as it was). Long lists: holding Down scrolls smoothly.
- T6. Player: copies switch from the player keeps the position, as P7.
- Confusing UX: focus jumping to the rail after closing a dialog; the sort menu unreachable with
  the D-pad; text cut in the 1gb AVD's small font scale.

Then: `scripts/devicepass/emulators.sh stop all` (stops only what the script started).

## W. Windows 11 VM, MSI (after the emulators are stopped)

```sh
scripts/devicepass/vm.sh start
scripts/devicepass/vm.sh fetch-msi <ci run id>     # or copy-msi <file.msi>
scripts/devicepass/vm.sh copy-jars                 # keyed local jars, so sign in works
scripts/devicepass/vm.sh queue-install --jars      # upgrade in place; --clean for a fresh install
```

Inside the VM (http://127.0.0.1:8006), if the runner is not running:
`powershell -executionpolicy bypass -file \\host.lan\Data\runner.ps1`. Output lands in
`/home/dracu/vms/win11/shared/q/NN-devicepass-install.out`.

- W1. Upgrade in place from the previous MSI: still signed in, settings, favourites, resume points
  kept (the 1.26.0 MSI update used to delete them: issue #7). Installed under
  `%LOCALAPPDATA%\TMPlayer App`.
- W2. Side bar: Home and Chats top level, no Telegram group; Chats page chips are Telegram's
  filters then each folder; any folder page lights Chats.
- W3. Ctrl+K opens the palette: type, Up/Down, Enter opens details, Ctrl+Enter plays, Esc closes;
  "Go to" entries; Saved Messages is no longer in Go to. The hint line reads right.
- W4. Back to top appears with the mouse wheel scrolling up past a screenful, as P5.
- W5. Details, copies, player copy switch, corrections, sort: as P7 and P8 with mouse and keyboard
  (right click menus).
- W6. Dark theme, window resize to narrow and wide, 125 % and 150 % scaling.
- Then `scripts/devicepass/vm.sh stop` (only if this script started it).

## D. Data safety (every target where it applies)

- D1. **Continue watching does not collapse a show.** Start S01E02 and S01E05 of one show, and a
  film with the same words in its name. Continue watching keeps the show as one card at the latest
  episode (or as before the batch) and the film as its own card; grouping by copies must never
  merge two different episodes or the show with the film. Compare with the baseline list.
- D2. **Progress carries across copies.** Stop copy A at a known minute; open copy B (another chat):
  resume offered at the same minute; finish B: the video is watched everywhere, and A's card shows
  watched too. A copy a few per cent longer still carries; a different cut does not.
- D3. **Remove after watching deletes the right file.** With Remove after watching on and copy A
  downloaded, watch copy B to the end (streamed): A stays in Downloads unless the setting means
  "this video" (decide and note it); watch downloaded A to the end: A goes, B is untouched, nothing
  else in Downloads changes. Check the Downloads folder on disk too.
- D4. **Corrections stay put.** Not the same video, Wrong match and Rename survive restart, a chat
  refresh and an app update; undo works.
- D5. **Backup and restore of new settings.** Back up after changing: Remember searches, a chat
  sort, a copy pick and quality habit, a Rename, a Wrong match. Restore on a fresh AVD user or
  after clearing them: the restore summary lists them, and each is back. A backup from the
  previous release still restores (no "comes from a newer TMPlayer" error the other way round).
- D6. **No crash or ANR.** The flows check logcat for FATAL EXCEPTION and ANR in com.tmplayer after
  every step; at the end also `adb -s <serial> logcat -d -b crash`.

## UX watch list (note each, fix before commit)

- Labels that only make sense to us: "copies", "Not same", "Wrong match", "Use match".
- Dead ends: a page with no way back except system Back; Back that leaves the app from a sub page.
- Surprising jumps: lists reordering under the finger, search results shifting, the bar's lit tab
  not matching the page, the details page opening the player or the reverse.
- Two cards for one video, one card for two videos.
- Anything hidden by the floating bar, the keyboard, or the back to top button.
- Strings with a dash, cut text in German or Arabic, RTL mirror mistakes.

## Sign off

All flows' REPORT.md findings triaged, every [hand] item seen in both orientations on the phone,
TV and desktop items done, D1 to D6 pass, emulators and the VM stopped. Then commit, push to main,
build the local release APK and tell the user (no tag or release).

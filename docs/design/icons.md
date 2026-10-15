# Icons

Every icon in the app is a [Hugeicons](https://hugeicons.com/) glyph from the free set,
`@hugeicons/core-free-icons` 4.3.5 (MIT, Stroke Rounded style). The app logo, the launcher
icon, brand marks and flags are not icons in this sense and are not part of it.

`scripts/hugeicons-to-kotlin.py` writes `TmIcons` (Compose, shared by phone, TV and desktop),
the Android vector drawables the View based player controls and the notifications use, and
this page. To change or add an icon, edit the lists in the script and run it again against the
npm package; the script's docstring says how to fetch it without running anything from it.

Strokes are 1.5 units on the 24 unit grid with round caps and joins (1.75 in the
Android drawables, which are the player's controls over video and the notification glyphs).
Icons marked as mirrored flip in right to left languages.

The free set is stroke only. Where a filled glyph is the state (a favourite that is set) or has
to hold its weight at size (the player's big centre play and pause, play badges on artwork), the
set's own outline is filled in (`Star`, `PlaySolid`, `PauseSolid`); everywhere else a selected
state is carried by colour and background, as the sidebar pill already does.

## Compose: `com.tmplayer.ui.icons.TmIcons`

| TmIcons | Hugeicons | Mirrored | Replaces | Stands for |
| --- | --- | --- | --- | --- |
| `ArrowBack` | ArrowLeft02 | yes | Material ArrowBack | Back. |
| `ArrowForward` | ArrowRight02 | yes | Material ArrowForward | Forward, onward. |
| `ChevronLeft` | ArrowLeft01 | yes | Material KeyboardArrowLeft | A step to the previous item. |
| `ChevronRight` | ArrowRight01 | yes | Material KeyboardArrowRight | A row that opens a page. |
| `ChevronDown` | ArrowDown01 |  | Material KeyboardArrowDown, ArrowDropDown | A list that opens downwards. |
| `ChevronUp` | ArrowUp01 |  | Material KeyboardArrowUp | A list that folds back up. |
| `Close` | Cancel01 |  | Material Close, PlayerIcons.Close | Close, dismiss, cancel. |
| `Menu` | Menu01 |  | Material Menu | The navigation drawer. |
| `MoreVert` | MoreVertical |  | Material MoreVert, PlayerIcons.MoreVert | More actions. |
| `Home` | Home03 |  | Material Home | Home. |
| `Search` | Search01 |  | Material Search | Search. |
| `SearchRemove` | SearchRemove |  | TmIcons.Search | A search that found nothing. |
| `FilterHorizontal` | FilterHorizontal |  |  | The search's filters. |
| `ArrowUpLeft` | ArrowUpLeft01 | yes |  | Put a suggestion into the search field to go on typing. |
| `Settings` | Settings01 |  | Material Settings | Settings. |
| `Refresh` | Refresh |  | Material Refresh | Refresh, try again, start over. |
| `History` | History |  | TmIcons.History | Recent searches and watch history. |
| `Clock` | Clock01 |  | TmIcons.Clock | Recent. |
| `Download` | Download04 |  | TmIcons.Download | Downloads, what is held on this device. |
| `Folder` | Folder01 |  | TmIcons.Folder | A Telegram chat folder. |
| `Grid` | GridView |  | TmIcons.Grid | The grid arrangement. |
| `List` | LeftToRightListBullet | yes | Material List | The list arrangement. |
| `Sort` | SortingAZ02 |  | TmIcons.Sort | The chat list sorted by name. |
| `OpenExternal` | LinkSquare02 |  | Material ExitToApp | Open in another app. |
| `SignOut` | Logout05 | yes | Material ExitToApp | Sign out. |
| `WindowMinimize` | MinusSign |  | WindowGlyphs.Minimize | Minimise the window. |
| `WindowMaximize` | Stop |  | WindowGlyphs.Maximize | Maximise the window. |
| `WindowRestore` | Copy |  | WindowGlyphs.Restore | Restore a maximised window: one square behind another. |
| `WindowClose` | Cancel01 |  | WindowGlyphs.Close | Close the window. |
| `Add` | Add01 |  | Material Add | Add. |
| `Remove` | MinusSign |  | TmIcons.Remove | Take one away, on a stepper. |
| `Check` | Tick02 |  | Material Check, PlayerIcons.Check | Done, chosen. |
| `CheckCircle` | CheckmarkCircle02 |  | Material CheckCircle | Selected, downloaded, all clear. |
| `Checklist` | CheckList |  | TmIcons.Checklist | Pick several at once. |
| `Delete` | Delete02 |  | Material Delete | Delete, remove. |
| `Edit` | PencilEdit02 |  | Material Edit, PlayerIcons.Edit | Rename, edit. |
| `Info` | InformationCircle |  | Material Info | Details, about. |
| `Help` | HelpCircle |  | TmIcons.Help | The walkthrough, how this works. |
| `Warning` | Alert02 |  | Material Warning | Something needs attention. |
| `Lock` | SquareLock02 |  | Material Lock | Locked, private, the PIN. |
| `Star` | Star (filled) |  | Material Star | A favourite that is set. |
| `StarOutline` | Star |  | TmIcons.StarOutline | Favourites, a favourite that is not set. |
| `Favorite` | Favourite |  | Material Favorite | Support the project. |
| `Bookmark` | Bookmark02 |  | TmIcons.Bookmark | Saved Messages, bookmarks. |
| `Pin` | Pin |  | TmIcons.Pin | A pinned chat. |
| `Share` | Share08 |  | TmIcons.Share | Share. |
| `Send` | Sent | yes | Material Send | Send. |
| `Archive` | Archive03 |  | TmIcons.Archive | Archived chats. |
| `Bell` | Notification01 |  | TmIcons.Bell | Notifications on. |
| `BellOff` | NotificationOff01 |  | TmIcons.BellOff | Notifications off. |
| `Unread` | MessageNotification01 |  | TmIcons.Dot | Unread chats. |
| `Backspace` | Eraser01 | yes | TmIcons.Backspace | Delete the last digit on the PIN pad. |
| `VisibilityOff` | ViewOffSlash |  | TmIcons.VisibilityOff | Controls out of sight. |
| `Chats` | Message01 |  | TmIcons.List | The Chats section, every chat. |
| `Account` | UserCircle |  | Material AccountCircle | An account. |
| `Person` | User |  | Material Person | A person. |
| `Group` | UserGroup |  | TmIcons.Group | A group. |
| `Channel` | Megaphone02 |  | TmIcons.Channel | A broadcast channel. |
| `Phone` | SmartPhone01 |  | Material Phone | A phone. |
| `Email` | Mail01 |  | Material Email | Email. |
| `Language` | Globe02 |  | TmIcons.Language | The language setting. |
| `TextSize` | TextFont |  | TmIcons.TextSize | The interface size. |
| `Theme` | Moon02 |  | TmIcons.Theme | The theme. |
| `Palette` | PaintBoard |  | TmIcons.Palette | Wallpaper colours. |
| `Image` | Image01 |  | TmIcons.Image | Posters, thumbnails. |
| `Frames` | Album02 |  | TmIcons.Frames | Preview frames over the scrub bar. |
| `Logs` | File01 |  | TmIcons.Logs | Save logs. |
| `Bug` | Bug01 |  | TmIcons.Bug | Report a problem. |
| `Gavel` | Legal01 |  | TmIcons.Gavel | Lawful use. |
| `Vibration` | Vibrate |  | TmIcons.Vibration | Haptics. |
| `Wifi` | Wifi01 |  | TmIcons.Wifi | Downloads over Wi-Fi only. |
| `WifiOff` | WifiOff02 |  | TmIcons.WifiOff | Offline. |
| `Mic` | Mic01 |  | TmIcons.Mic | Voice search. |
| `Remote` | RemoteControl |  | TmIcons.Remote | The remote's keys. |
| `Play` | Play |  | Material PlayArrow, PlayerIcons.Play | Play. |
| `Pause` | Pause |  | TmIcons.Pause, PlayerIcons.Pause | Pause. |
| `PlaySolid` | Play (filled) |  | PlayerIcons.Play | The player's big centre play, and play badges on artwork. |
| `PauseSolid` | Pause (filled) |  | PlayerIcons.Pause | The player's big centre pause. |
| `SkipNext` | Next |  | PlayerIcons.SkipNext | Next episode. |
| `SkipPrevious` | Previous |  | PlayerIcons.SkipPrevious | Previous episode. |
| `Repeat` | Repeat |  | TmIcons.Repeat | A-B repeat. |
| `Episodes` | ListVideo |  | TmIcons.Episodes | The season's episodes. |
| `Subtitles` | Subtitle |  | PlayerIcons.Subtitles | Subtitles. |
| `Audio` | Headphones |  | PlayerIcons.Audio | Audio track. |
| `VolumeUp` | VolumeHigh |  | PlayerIcons.VolumeUp | Volume. |
| `VolumeDown` | VolumeLow |  | PlayerIcons.VolumeDown | Volume down. |
| `VolumeOff` | VolumeMute02 |  | PlayerIcons.VolumeOff | Muted. |
| `Fullscreen` | ArrowExpand |  | PlayerIcons.Fullscreen | Full screen. |
| `FullscreenExit` | ArrowShrink |  | PlayerIcons.FullscreenExit | Leave full screen. |
| `PictureInPicture` | PictureInPictureOn |  | PlayerIcons.PictureInPicture | Picture in picture. |
| `AspectRatio` | AspectRatio |  | PlayerIcons.AspectRatio | Fit, fill, zoom. |
| `Trailer` | Clapperboard |  | TrailerIcon | A title's trailer. |

## Android drawables: `app/src/main/res/drawable`

| Drawable | Hugeicons | Mirrored | Size | Used for |
| --- | --- | --- | --- | --- |
| `ic_arrow_back` | ArrowLeft02 | yes | 24 dp | The player's back button. |
| `ic_more_vert` | MoreVertical |  | 24 dp | The player's overflow. |
| `ic_player_next` | Next |  | 24 dp | Next episode on the player row. |
| `ic_player_previous` | Previous |  | 24 dp | Previous episode on the player row. |
| `ic_player_forward` | Forward02 |  | 24 dp | The seek step setting. |
| `ic_episodes` | ListVideo |  | 24 dp | The player's episodes button. |
| `ic_subtitles` | Subtitle |  | 36 dp | Subtitles, on the player row and in settings. |
| `ic_audio_language` | Headphones |  | 36 dp | Audio track, on the player row and in settings. |
| `ic_speed` | DashboardSpeed02 |  | 36 dp | Playback speed. |
| `ic_aspect` | AspectRatio |  | 36 dp | Fit, fill, zoom. |
| `ic_rotate_auto` | ScreenRotation |  | 36 dp | Rotation follows the phone. |
| `ic_rotate_landscape` | SmartPhoneLandscape |  | 36 dp | Rotation held in landscape. |
| `ic_rotate_portrait` | SmartPhone01 |  | 36 dp | Rotation held in portrait. |
| `ic_pip` | PictureInPictureOn |  | 24 dp | Picture in picture. |
| `ic_lock_open` | SquareUnlock02 |  | 24 dp | Unlock the locked player. |
| `ic_brightness` | Sun03 |  | 24 dp | The brightness swipe. |
| `ic_volume` | VolumeHigh |  | 24 dp | The volume swipe. |
| `ic_volume_off` | VolumeMute02 |  | 24 dp | The volume swipe at zero. |
| `ic_warning` | Alert02 |  | 24 dp | A playback error. |
| `ic_stat_download` | Download04 |  | 24 dp | The download notification's status bar icon. |
| `ic_notify_pause` | Pause |  | 24 dp | The download notification's Pause action. |
| `ic_notify_resume` | Play |  | 24 dp | The download notification's Resume action. |
| `ic_notify_cancel` | Cancel01 |  | 24 dp | The download notification's Cancel action. |

## Not from the set

- The phone player's big centre play and pause (`PlayPauseIcon.kt`) is drawn in code, because
  its two bars fold into the triangle as an animation; a filled glyph reads at that size.

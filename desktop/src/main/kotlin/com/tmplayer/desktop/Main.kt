package com.tmplayer.desktop

import com.tmplayer.online.OnlineSubtitles
import com.tmplayer.online.OnlineSubtitlesStore
import com.tmplayer.online.SubtitleCache
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import com.tmplayer.data.RemoveAfterWatching
import com.tmplayer.data.SettingsStore
import com.tmplayer.data.Td
import com.tmplayer.data.ThemeChoice
import com.tmplayer.desktop.os.AppExit
import com.tmplayer.desktop.os.DesktopLog
import com.tmplayer.desktop.os.DesktopTransferNotifier
import com.tmplayer.desktop.os.NativeFullscreen
import com.tmplayer.desktop.os.NonReparentingWm
import com.tmplayer.desktop.os.NativeInventory
import com.tmplayer.desktop.os.SingleInstance
import com.tmplayer.desktop.os.StartupFailure
import com.tmplayer.desktop.os.WindowMemory
import com.tmplayer.desktop.os.WindowsTitleBar
import com.tmplayer.desktop.os.UndecoratedFrame
import com.tmplayer.desktop.os.WindowChromeMode
import com.tmplayer.desktop.ui.LocalWindowChrome
import com.tmplayer.desktop.ui.ResizeEdges
import com.tmplayer.desktop.ui.WindowChrome
import com.tmplayer.desktop.ui.WindowControlsFallback
import com.tmplayer.ui.theme.Tone
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.tmplayer.desktop.ui.DesktopShell
import com.tmplayer.desktop.ui.ShellState
import com.tmplayer.i18n.Translator
import com.tmplayer.online.MetadataCache
import com.tmplayer.online.MetadataStore
import com.tmplayer.online.OnlineMetadata
import com.tmplayer.platform.Background
import com.tmplayer.ui.components.AppLogo
import com.tmplayer.ui.theme.TmMaterialTheme
import com.tmplayer.ui.theme.UiScaled
import com.tmplayer.ui.theme.rememberUiScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme
import kotlin.system.exitProcess

private const val QUIT_GRACE_MS = 3_000L

/** How long a fullscreen change is given to land before the window asks for the keyboard again. */
private const val FULLSCREEN_SETTLE_MS = 250L

fun main(args: Array<String>) {
    // Loads every native library, reports, exits: no window, no second instance check.
    args.firstOrNull(SelfTest::matches)?.let { exitProcess(SelfTest.run(it)) }
    // Before the first window: sway and its kind never reparent, which AWT has to be told.
    NonReparentingWm.applyIfNeeded()
    // TDLib's Windows DLL needs the C++ runtime; take the bundled JVM's copy (see WindowsRuntime).
    WindowsRuntime.preload()
    // TDLib's macOS build needs OpenSSL 3, which the app carries (see MacOpenSsl). A failure here
    // shows as TDLib's own load error at sign in, which names the library.
    runCatching { MacOpenSsl.preload() }
    // A second launch hands its arguments (later, tg: links) to the first and leaves.
    var raise: () -> Unit = {}
    if (!SingleInstance.acquire(args.toList()) { java.awt.EventQueue.invokeLater { raise() } }) return
    // Only the first instance, so a second launch never rotates the log of the one running.
    DesktopLog.install(java.io.File(DesktopPaths.dataDir, "logs"))
    // Until the window is up, an error ends the launch out loud instead of leaving a process with
    // no window that holds the single instance lock (see StartupFailure).
    StartupFailure.install()
    try {
        NativeInventory.log()
        Td.start(DesktopPaths, desktopDeviceInfo(), desktopCredentials())
        OnlineSubtitles.current = desktopOnlineSubtitles()
        val settings = DesktopServices.settings
        // The kids lock (CP57), read once here for every list of chats in the process.
        com.tmplayer.data.ChatGate.follow(settings)
        // The viewer's corrections to names and groups, for every screen that arranges a chat.
        com.tmplayer.data.VideoCorrections.follow(settings)
        OnlineMetadata.current = desktopOnlineMetadata()
        // What an earlier run was in the middle of comes back paused, once there is an account to
        // ask TDLib about.
        // A storage move a crash or a kill cut short is finished first: it needs the settings store
        // and the disk, not TDLib, which is already starting over the new folders.
        Background.scope.launch { runCatching { DesktopStorage.relocation.resumePending() } }
        // The UI language: the Settings choice, else the system's, else English. The en-XA
        // pseudo-locale is for development runs only (-Dtmplayer.pseudo=true).
        Translator.pseudoEnabled = System.getProperty("tmplayer.pseudo") == "true"
        Background.scope.launch {
            settings.language.collect { saved -> Translator.select(saved, listOf(Locale.getDefault().toLanguageTag())) }
        }
        // Lighter pictures and Reduce motion (CP68) have no rows on a computer, but a backup made
        // on a phone can carry them, and they mean the same here.
        Background.scope.launch { settings.lighterPictures.collect { com.tmplayer.data.Thumbnails.light = it } }
        Background.scope.launch { settings.reduceMotion.collect { com.tmplayer.ui.theme.Motion.reduced = it } }
        // `-Dtmplayer.lowend=true`: the low-end verdict, short of storage, for checking the card and the step.
        if (System.getProperty("tmplayer.lowend") == "true") {
            com.tmplayer.data.DeviceFitOverride.forced = com.tmplayer.data.DeviceFit(shortOfMemory = false, shortOfStorage = true)
        }
        Background.scope.launch {
            Td.awaitAuthorizedSession()
            DesktopServices.downloads.restore()
        }
        // "Remove after watching", for the life of the process: the grid, the player's button and the
        // player reaching the end all write the one watched list this reads.
        Background.scope.launch {
            RemoveAfterWatching(
                enabled = settings.removeAfterWatching,
                watched = DesktopServices.watched.watched,
                downloads = { settings.downloadsNow() },
                delete = { DownloadIndex.delete(settings, it) },
            ).run()
        }
        DesktopUpdates.configure()
        // Before anything can rewrite desktop.properties, which would drop the old update keys.
        Background.scope.launch { runCatching { DesktopUpdates.migrate(DesktopServices.prefs, settings) } }
        // Compose would end the process itself once the last window closes, with System.exit, which on
        // Linux can hang for good (see AppExit). It returns here instead, and AppExit ends it.
        // Who draws minimize, maximize and close: Windows gets an undecorated window with the app's
        // own buttons, a tiling Linux window manager gets the app's close and fullscreen buttons
        // over its plain window, everything else keeps the system's title bar (WindowChromeMode).
        val chromeMode = WindowChromeMode.current(DesktopServices.prefs.now.systemTitleBar)
        com.tmplayer.platform.Logger.i("Main", "window buttons: $chromeMode")
        val undecorated = chromeMode == WindowChromeMode.Custom
        application(exitProcessOnExit = false) {
            val windowState = remember { WindowMemory.load() }
            LaunchedEffect(windowState) { WindowMemory.follow(windowState) }
            val shell = remember { ShellState(settings, DesktopServices.downloads, DesktopServices.watched, bookmarks = DesktopServices.bookmarks) }
            val quit = {
                // Saving is a courtesy; a failure there must never keep the app from closing.
                runCatching { WindowMemory.save(windowState) }
                // Should taking the window down hang (a native player that will not let go), the
                // process still ends.
                Thread {
                    Thread.sleep(QUIT_GRACE_MS)
                    AppExit.now()
                }.apply { isDaemon = true }.start()
                exitApplication()
            }
            Window(
                onCloseRequest = quit,
                state = windowState,
                title = "TMPlayer",
                undecorated = undecorated,
                icon = rememberVectorPainter(AppLogo.Mark),
                onPreviewKeyEvent = shell::onPreviewKey,
                onKeyEvent = shell::onKey,
            ) {
                window.minimumSize = java.awt.Dimension(960, 600)
                // Before the window is first shown, so one that opens maximized stops at the taskbar.
                if (undecorated) UndecoratedFrame.holdToWorkArea(window)
                DisposableEffect(window) {
                    val follow = object : java.awt.event.ComponentAdapter() {
                        override fun componentMoved(e: java.awt.event.ComponentEvent) = UndecoratedFrame.holdToWorkArea(window)
                    }
                    if (undecorated) window.addComponentListener(follow)
                    onDispose { window.removeComponentListener(follow) }
                }
                LaunchedEffect(Unit) {
                    if (!undecorated) return@LaunchedEffect
                    repeat(100) { if (!window.isDisplayable) delay(50) }
                    UndecoratedFrame.restoreCaptionStyles(window)
                    UndecoratedFrame.holdToWorkArea(window)
                }
                val chrome = remember(window) {
                    if (!chromeMode.inApp) {
                        null
                    } else if (undecorated) {
                        WindowChrome(
                            mode = chromeMode,
                            window = window,
                            onMinimize = { windowState.isMinimized = true },
                            onToggleMaximize = {
                                UndecoratedFrame.holdToWorkArea(window)
                                windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                                    WindowPlacement.Floating
                                } else {
                                    WindowPlacement.Maximized
                                }
                            },
                            onClose = quit,
                            maximized = { windowState.placement == WindowPlacement.Maximized },
                        )
                    } else {
                        // A tiling window manager sizes the window itself: the middle button is
                        // fullscreen, and there is nowhere to minimize to.
                        WindowChrome(
                            mode = chromeMode,
                            window = window,
                            onMinimize = null,
                            onToggleMaximize = { shell.fullscreen = !shell.fullscreen },
                            onClose = quit,
                            maximized = { shell.fullscreen },
                        )
                    }
                }
                DesktopServices.selfUpdate.quit = quit
                shell.quit = quit
                // Transfers show outside the window too, once there is a window for the taskbar bar.
                LaunchedEffect(Unit) {
                    DesktopStorage.closePlayer = { shell.closePlayer() }
                    DesktopStorage.notifier = DesktopTransferNotifier.create({ window }) {
                        java.awt.EventQueue.invokeLater {
                            raise()
                            shell.go(com.tmplayer.desktop.ui.Destination.Downloads)
                        }
                    }
                }
                // The update check, on every launch and before sign in too: the first one ten seconds
                // after the first frame, then every six hours while the window is open.
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    DesktopServices.updates.run()
                }
                // Fullscreen is the shell's to ask for and the window's to do. The window system is
                // asked directly where it can be (see NativeFullscreen); elsewhere Compose's placement
                // does it, and leaving goes back to whatever the window was before.
                val native = remember(window) { NativeFullscreen(window) }
                var beforeFullscreen by remember { mutableStateOf(WindowPlacement.Floating) }
                LaunchedEffect(shell.fullscreen) {
                    WindowMemory.freeze(WindowMemory.Hold.Fullscreen, shell.fullscreen)
                    if (native.set(shell.fullscreen)) return@LaunchedEffect
                    if (shell.fullscreen && windowState.placement != WindowPlacement.Fullscreen) {
                        beforeFullscreen = windowState.placement
                        windowState.placement = WindowPlacement.Fullscreen
                    } else if (!shell.fullscreen && windowState.placement == WindowPlacement.Fullscreen) {
                        windowState.placement = beforeFullscreen
                    }
                }
                // The change remaps the window, and the window system may hand it back without the
                // keyboard (Esc and Space then reach nothing until a click). Once it has settled,
                // the window asks for focus again; the player takes it from there (PlayerScreen).
                var fullscreenSeen by remember { mutableStateOf(shell.fullscreen) }
                LaunchedEffect(shell.fullscreen) {
                    if (fullscreenSeen == shell.fullscreen) return@LaunchedEffect
                    fullscreenSeen = shell.fullscreen
                    delay(FULLSCREEN_SETTLE_MS)
                    if (!window.isShowing || window.isActive) return@LaunchedEffect
                    window.toFront()
                    window.requestFocus()
                }
                raise = {
                    window.isVisible = true
                    if (window.extendedState and java.awt.Frame.ICONIFIED != 0) {
                        window.extendedState = window.extendedState and java.awt.Frame.ICONIFIED.inv()
                    }
                    window.toFront()
                    window.requestFocus()
                }
                // The first frame is on screen: later launches may hand over to this one now.
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    SingleInstance.markReady()
                    StartupFailure.started()
                }
                DesktopTheme(settings) {
                    val colors = MaterialTheme.colorScheme
                    LaunchedEffect(colors.background, colors.onBackground) {
                        if (undecorated) return@LaunchedEffect
                        // The handle exists once the window is shown, which is just after the first frame.
                        repeat(100) { if (!window.isDisplayable) delay(50) }
                        WindowsTitleBar.apply(
                            window,
                            dark = colors.background.luminance() < 0.5f,
                            caption = colors.background.toArgb() and 0xFFFFFF,
                            text = colors.onBackground.toArgb() and 0xFFFFFF,
                        )
                    }
                    CompositionLocalProvider(LocalWindowChrome provides chrome) {
                        Box(Modifier.fillMaxSize()) {
                            DesktopShell(shell) { request, close ->
                                PlayerHost(
                                    request = request,
                                    onClose = {
                                        shell.fullscreen = false
                                        close()
                                    },
                                    settings = settings,
                                    fullscreen = shell.fullscreen,
                                    onToggleFullscreen = { shell.fullscreen = !shell.fullscreen },
                                    onToggleAlwaysOnTop = { window.isAlwaysOnTop = !window.isAlwaysOnTop },
                                    onQuit = quit,
                                    onRaise = raise,
                                )
                            }
                            // Nothing on screen with a page header (sign in, the PIN pad): the
                            // buttons on their own, top right. The player has its own in its bar.
                            if (shell.nowPlaying == null) WindowControlsFallback(Modifier.align(Alignment.TopEnd))
                            // Undecorated and floating: a hairline for the edge the system no
                            // longer draws, and the edges to resize by.
                            if (undecorated && windowState.placement == WindowPlacement.Floating && !shell.fullscreen) {
                                val hairline = with(LocalDensity.current) { 1.toDp() }
                                Box(Modifier.fillMaxSize().border(hairline, Tone.outline))
                                ResizeEdges(window)
                            }
                        }
                    }
                }
            }
        }
    } catch (e: Throwable) {
        StartupFailure.report(e)
    }
    // TDLib's, libmpv's and D-Bus's threads are not daemons; with the window gone, nothing is left
    // that should keep the process alive.
    AppExit.now()
}

/**
 * The theme the Settings choice asks for. "System" follows the OS where it says (Windows, macOS);
 * where it does not (most Linux desktops, until Phase 3 reads the portal), dark, which is the
 * right default for a window that plays video. Drawn at the viewer's interface size.
 */
@Composable
fun DesktopTheme(settings: SettingsStore, content: @Composable () -> Unit) {
    val choice by settings.themeChoice.collectAsState(initial = ThemeChoice.Default)
    val dark = when (choice) {
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
        ThemeChoice.System -> currentSystemTheme != SystemTheme.LIGHT
    }
    // The interface size (CP59) for the whole window, the player's controls included; the video
    // fills whatever space it is given, so it is the same at every size.
    val scale by rememberUiScale(settings)
    TmMaterialTheme(dark = dark) { UiScaled(scale, content) }
}

/**
 * Online subtitles with this build's OpenSubtitles key: the account beside the settings in the
 * data directory, the cache in the cache directory. A build without a key gets one that says so.
 */
internal fun desktopOnlineSubtitles(): OnlineSubtitles = OnlineSubtitles(
    apiKey = BuildInfo.OPENSUBTITLES_API_KEY,
    store = OnlineSubtitlesStore(java.io.File(DesktopPaths.dataDir, "online-subtitles.properties")),
    cache = SubtitleCache(java.io.File(DesktopPaths.cacheDir, "online-subtitles")),
    appVersion = BuildInfo.VERSION,
).also { online -> Background.scope.launch { runCatching { online.cache.prune() } } }

/**
 * Posters and overviews with this build's TMDB key: the switches beside the settings in the data
 * directory, the answers and pictures in the cache directory. Off until the viewer turns it on.
 */
internal fun desktopOnlineMetadata(): OnlineMetadata = OnlineMetadata(
    buildKey = BuildInfo.TMDB_API_KEY,
    store = MetadataStore(java.io.File(DesktopPaths.dataDir, "online-metadata.properties")),
    cache = MetadataCache(java.io.File(DesktopPaths.cacheDir, "online-metadata")),
    appVersion = BuildInfo.VERSION,
).also { metadata -> Background.scope.launch { runCatching { metadata.cache.prune() } } }

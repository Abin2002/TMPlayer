package com.tmplayer.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import com.tmplayer.ui.components.SettingsSectionLabel
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.theme.Corner
import com.tmplayer.ui.theme.LocalDarkTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmplayer.data.BackupRead
import com.tmplayer.data.BackupService
import com.tmplayer.data.BackupWords
import com.tmplayer.data.RestorePlan
import com.tmplayer.data.AppLockSession
import com.tmplayer.data.AppLockSettings
import com.tmplayer.data.ResumeRewind
import com.tmplayer.data.SettingsStore
import com.tmplayer.ui.lock.AppLockWords
import com.tmplayer.ui.lock.PinPad
import com.tmplayer.ui.lock.PinPurpose
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.produceState
import com.tmplayer.data.SizeFilter
import com.tmplayer.data.SupportReminder
import com.tmplayer.data.ThemeChoice
import com.tmplayer.data.UiScale
import com.tmplayer.data.LogExport
import com.tmplayer.data.UpdateState
import com.tmplayer.data.UpdateWords
import com.tmplayer.data.Updates
import com.tmplayer.data.WhatsNew
import com.tmplayer.data.release
import com.tmplayer.desktop.DesktopSettings
import com.tmplayer.desktop.os.FilePicker
import com.tmplayer.desktop.os.DesktopLog
import com.tmplayer.ui.theme.rememberUiScale
import com.tmplayer.desktop.os.OpenExternal
import com.tmplayer.online.OnlineMetadata
import com.tmplayer.player.SubtitlePosition
import com.tmplayer.player.SubtitleSize
import com.tmplayer.player.SubtitleStyle
import com.tmplayer.player.TouchPrefs
import com.tmplayer.player.TrackPreferences
import com.tmplayer.ui.about.About
import com.tmplayer.ui.components.TmAlertDialog
import com.tmplayer.ui.components.rememberToast
import com.tmplayer.ui.i18n.LocalStrings
import com.tmplayer.ui.i18n.languageRowDetail
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The settings that mean something on a desktop: theme, playback, the chat to open on launch,
 * the size limits, storage, history and signing out. Voice search is not here at all (no portable
 * speech API, B2.5), nor are the phone's touch and orientation settings.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsPage(
    state: ShellState,
    version: String = "",
    /** The chat list as loaded, for the favourites "Only my folders" would strand. */
    chatList: List<com.tmplayer.data.ChatSummary> = emptyList(),
) {
    val s = LocalStrings.current
    var showAbout by remember { mutableStateOf(false) }
    val supportCounters by state.settings.supportCounters.collectAsState(initial = SupportReminder.Counters())
    if (showAbout) {
        AboutPage(version, supporter = supportCounters.supporter, onBack = { showAbout = false })
        return
    }
    val settings = state.settings
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    // The app lock (CP57): a PIN being set, changed or removed takes the page's place.
    val appLock by settings.appLock.collectAsState(initial = AppLockSettings())
    var pinFlow by remember { mutableStateOf<DesktopPinFlow?>(null) }
    var pickingKidsChats by remember { mutableStateOf(false) }
    pinFlow?.let { flow ->
        PinFlowPad(flow, state, appLock, scope, toast, onNext = { pinFlow = it }, onKidsChats = { pickingKidsChats = true })
        return
    }
    val theme by settings.themeChoice.collectAsState(initial = ThemeChoice.Default)
    val autoplay by settings.autoplayNext.collectAsState(initial = true)
    val resumeRewindMs by settings.resumeRewindMs.collectAsState(initial = ResumeRewind.DEFAULT_MS)
    val downloadFirst by settings.downloadBeforePlaying.collectAsState(initial = false)
    val lowStorage by settings.lowStorageMode.collectAsState(initial = false)
    val openLast by settings.openLastChat.collectAsState(initial = false)
    val minSize by settings.minSizeBytes.collectAsState(initial = SizeFilter.DEFAULT_MIN)
    val maxSize by settings.maxSizeBytes.collectAsState(initial = SizeFilter.DEFAULT_MAX)
    val desktop by state.extras.prefs.state.collectAsState()
    val notifyUpdates by settings.updateNotify.collectAsState(initial = true)
    val updateState by Updates.state.collectAsState()
    var confirmSignOut by remember { mutableStateOf(false) }
    var supporting by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val touchPrefs by settings.touchPrefs.collectAsState(initial = TouchPrefs())
    val detailFirst by settings.detailFirst.collectAsState(initial = true)
    val subtitleStyle by settings.subtitleStyle.collectAsState(initial = SubtitleStyle())
    val trackPrefs by settings.trackPreferences.collectAsState(initial = TrackPreferences())
    val lastChatId by settings.lastChatId.collectAsState(initial = 0L)
    val history by settings.continueWatching.collectAsState(initial = emptyList())
    // Only those the kids lock shows, as the Favourites section counts them.
    val storedFavourites by settings.favorites.collectAsState(initial = emptySet())
    val allowedChats by com.tmplayer.data.ChatGate.allowed.collectAsState()
    val favourites = remember(storedFavourites, allowedChats) { com.tmplayer.data.KidsLock.favourites(storedFavourites, allowedChats) }
    val watchedList by state.watched.history.collectAsState(initial = emptyList())
    var confirmClearWatched by remember { mutableStateOf(false) }
    val folders by com.tmplayer.data.Td.folders.collectAsState()
    val language by settings.language.collectAsState(initial = "")
    var pickingLanguage by remember { mutableStateOf(false) }
    var stylingSubtitles by remember { mutableStateOf(false) }
    var whatsNew by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    val uiScale by rememberUiScale(settings)
    // A backup file picked, with what restoring it would change, while the prompt is up.
    var restorePlan by remember { mutableStateOf<RestorePlan?>(null) }
    val backups = remember(settings, state.watched, version) {
        BackupService(settings, state.watched, OnlineMetadata.current?.store, version, bookmarks = state.bookmarks)
    }

    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            PageHeader(s.settingsTitle, if (version.isBlank()) null else s.settingsAppVersion(version))
            SettingsColumn(
                Modifier.widthIn(max = 760.dp).padding(horizontal = pageMargin(), vertical = 8.dp),
            ) {
                // "Recommended for this device" (CP68): on a computer, Low storage mode when the
                // disk is short. It draws nothing otherwise.
                com.tmplayer.ui.onboarding.DeviceFitSettingsCard(
                    settings,
                    onApplied = { toast(s.deviceApplied) },
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Group(s.settingsAppearance)
                Setting(s.settingsTheme, if (theme == ThemeChoice.System) s.themeSystemDesktopDetail else theme.description) {
                    SingleChoiceSegmentedButtonRow {
                        ThemeChoice.entries.forEachIndexed { index, choice ->
                            SegmentedButton(
                                selected = theme == choice,
                                onClick = { scope.launch { settings.setThemeChoice(choice) } },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeChoice.entries.size),
                            ) { Text(choice.label) }
                        }
                    }
                }

                Setting(s.settingsLanguage, s.languageRowDetail(language)) {
                    OutlinedButton(onClick = { pickingLanguage = true }) { Text(s.commonChange) }
                }
                Setting(s.interfaceSize, s.interfaceSizeDetail) {
                    SingleChoiceSegmentedButtonRow {
                        UiScale.entries.forEachIndexed { index, choice ->
                            SegmentedButton(
                                selected = uiScale == choice,
                                onClick = { scope.launch { settings.setUiScale(choice) } },
                                shape = SegmentedButtonDefaults.itemShape(index, UiScale.entries.size),
                            ) { Text(choice.label) }
                        }
                    }
                }
                // Only where TMPlayer would draw window buttons of its own: Windows, and a tiling
                // window manager. Read at launch, so the change waits for a restart.
                if (remember { com.tmplayer.desktop.os.WindowChromeMode.current(systemTitleBar = false).inApp }) {
                    Toggle(
                        s.settingsSystemTitleBar,
                        if (desktop.systemTitleBar) s.settingsSystemTitleBarOn else s.settingsSystemTitleBarOff,
                        desktop.systemTitleBar,
                    ) { on -> state.extras.prefs.update { it.copy(systemTitleBar = on) } }
                }

                Group(s.settingsPlayback)
                Toggle(
                    s.settingsDetailFirst,
                    if (detailFirst) s.settingsDetailFirstClickOn else s.settingsDetailFirstClickOff,
                    detailFirst,
                ) { on -> scope.launch { settings.setDetailFirst(on) } }
                Toggle(s.settingsAutoplayNext, s.settingsAutoplayDetail, autoplay) {
                    scope.launch { settings.setAutoplayNext(it) }
                }
                Toggle(s.settingsDownloadFirst, s.settingsDownloadFirstDetail, downloadFirst) {
                    scope.launch { settings.setDownloadBeforePlaying(it) }
                }
                // A rolling window of the video on disk, for a drive with less room than the film;
                // the line under it says the cost.
                Toggle(
                    s.settingsLowStorage,
                    if (lowStorage) s.settingsLowStorageOn else s.settingsLowStorageOff,
                    lowStorage,
                ) { on -> scope.launch { settings.setLowStorageMode(on) } }
                Setting(
                    s.settingsHideControls,
                    s.settingsHideControlsDetail(TouchPrefs.timeoutLabel(touchPrefs.controlsTimeoutMs)),
                ) {
                    val choices = TouchPrefs.TIMEOUT_CHOICES_MS
                    Stepper(
                        onLess = { scope.launch { settings.updateTouchPrefs { it.copy(controlsTimeoutMs = TouchPrefs.step(choices, it.controlsTimeoutMs, -1)) } } },
                        onMore = { scope.launch { settings.updateTouchPrefs { it.copy(controlsTimeoutMs = TouchPrefs.step(choices, it.controlsTimeoutMs, 1)) } } },
                    )
                }
                // Four short choices, so all of them at once, as the theme row has them.
                Setting(s.settingsResumeRewind, s.settingsResumeRewindDetail) {
                    SingleChoiceSegmentedButtonRow {
                        ResumeRewind.CHOICES_MS.forEachIndexed { index, choice ->
                            SegmentedButton(
                                selected = resumeRewindMs == choice,
                                onClick = { scope.launch { settings.setResumeRewindMs(choice) } },
                                shape = SegmentedButtonDefaults.itemShape(index, ResumeRewind.CHOICES_MS.size),
                            ) { Text(ResumeRewind.label(choice)) }
                        }
                    }
                }

                Toggle(
                    s.settingsWheelSeeks,
                    if (desktop.wheelSeeks) s.settingsWheelSeeksOn else s.settingsWheelSeeksOff,
                    desktop.wheelSeeks,
                ) { on -> state.extras.prefs.update { it.copy(wheelSeeks = on) } }
                Toggle(s.playerDownmix, s.settingsDownmixDetail, desktop.downmix) { on ->
                    state.extras.prefs.update { it.copy(downmix = on) }
                }
                AudioLanguageSetting(settings, trackPrefs, scope)
                Toggle(
                    s.settingsSoftwareDecoding,
                    if (DesktopSettings.defaultSoftwareDecoding()) {
                        s.settingsSoftwareDecodingLinux
                    } else {
                        s.settingsSoftwareDecodingDetail
                    },
                    desktop.softwareDecoding,
                ) { on -> state.extras.prefs.update { it.copy(softwareDecoding = on) } }

                Group(s.playerSubtitles)
                // One row saying what is set; the preview and the three choices open over the page
                // (SubtitleStyleDialog), as on the phone and the TV.
                Setting(
                    s.settingsSubtitleStyle,
                    listOf(
                        subtitleStyle.size.label,
                        if (subtitleStyle.box) s.settingsSubtitleStyleBox else s.settingsSubtitleStyleNoBox,
                        subtitleStyle.position.label,
                    ).joinToString("  ·  "),
                ) {
                    OutlinedButton(onClick = { stylingSubtitles = true }) { Text(s.commonChange) }
                }
                SubtitleLanguageSettings(settings, trackPrefs, scope)

                OnlineSubtitlesGroup()
                OnlineMetadataGroup()

                Group(s.settingsLibrary)
                Toggle(s.settingsOpenLast, s.settingsOpenLastDetail, openLast) {
                    scope.launch { settings.setOpenLastChat(it) }
                }
                if (openLast && lastChatId != 0L) {
                    Setting(s.settingsForgetLast, s.settingsForgetLastDetail) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                settings.forgetLastChat()
                                // The row goes away on success, so the toast is the only sign.
                                toast(s.settingsForgetLastDone)
                            }
                        }) { Text(s.settingsForget) }
                    }
                }
                // Straight in the settings column, not wrapped: the column stacks them (in a Box the
                // two rows once drew over each other), and a wrapper would cut them off the card.
                Setting(s.settingsSmallest, SizeFilter.label(minSize)) {
                    Stepper(
                        onLess = { scope.launch { settings.setMinSizeBytes(SizeFilter.clampMin(SizeFilter.step(minSize, -1), maxSize)) } },
                        onMore = { scope.launch { settings.setMinSizeBytes(SizeFilter.clampMin(SizeFilter.step(minSize, 1), maxSize)) } },
                    )
                }
                Setting(s.settingsLargest, SizeFilter.label(maxSize)) {
                    Stepper(
                        onLess = { scope.launch { settings.setMaxSizeBytes(SizeFilter.clampMax(SizeFilter.step(maxSize, -1), minSize)) } },
                        onMore = { scope.launch { settings.setMaxSizeBytes(SizeFilter.clampMax(SizeFilter.step(maxSize, 1), minSize)) } },
                    )
                }
                // Reset means the defaults, as on the phone and TV: offering it at the defaults and
                // then clearing both ends contradicted the 50 MB the filter was still applying.
                if (!SizeFilter.isDefault(minSize, maxSize)) {
                    Setting(
                        s.settingsSizeLimitsReset,
                        s.settingsSizeLimitsResetDetail(SizeFilter.describe(SizeFilter.DEFAULT_MIN, SizeFilter.DEFAULT_MAX)),
                    ) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                // Widen first, so the clamp that stops the ends crossing cannot
                                // refuse the new floor on its way past the old ceiling.
                                settings.setMaxSizeBytes(SizeFilter.DEFAULT_MAX)
                                settings.setMinSizeBytes(SizeFilter.DEFAULT_MIN)
                            }
                        }) { Text(s.commonReset) }
                    }
                }

                StorageGroup(state)

                Group(s.settingsHistory)
                Setting(s.continueTitle, s.settingsContinueDetail) {
                    OutlinedButton(onClick = {
                        if (history.isEmpty()) {
                            toast(s.settingsContinueEmpty)
                            return@OutlinedButton
                        }
                        confirm = Confirm(
                            title = s.settingsContinueClearTitle,
                            message = s.settingsContinueClearMessage,
                            detail = s.settingsContinueClearDetail,
                        ) {
                            scope.launch {
                                val count = history.size
                                settings.clearWatchHistory()
                                toast(s.settingsContinueCleared(count))
                            }
                        }
                    }) { Text(s.commonClear) }
                }
                Setting(
                    s.settingsClearWatched,
                    s.settingsClearWatchedSummary(watchedList.size),
                ) {
                    OutlinedButton(onClick = {
                        if (watchedList.isEmpty()) toast(s.settingsWatchedEmpty) else confirmClearWatched = true
                    }) { Text(s.commonClear) }
                }
                Setting(s.navFavourites, s.settingsFavouritesDetail) {
                    OutlinedButton(onClick = {
                        if (favourites.isEmpty()) {
                            toast(s.settingsFavouritesEmpty)
                            return@OutlinedButton
                        }
                        confirm = Confirm(
                            title = s.settingsFavouritesClearTitle,
                            message = s.settingsFavouritesClearMessage(favourites.size),
                            detail = s.settingsFavouritesClearDetail,
                        ) {
                            scope.launch {
                                val count = favourites.size
                                settings.clearFavorites(allowedChats)
                                toast(s.settingsFavouritesCleared(count))
                            }
                        }
                    }) { Text(s.commonClear) }
                }

                // To a file and back: settings, lists and resume points, never the Telegram sign in.
                Group(s.backupSection)
                Setting(s.backupBackUp, s.backupBackUpDetail) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            val file = FilePicker.save(s.backupSaveTitle, backups.fileName(), "json") ?: return@launch
                            val saved = withContext(Dispatchers.IO) {
                                runCatching { file.writeText(backups.export()) }.isSuccess
                            }
                            toast(if (saved) s.backupSavedTo(file.absolutePath) else s.backupFailed)
                        }
                    }) { Text(s.backupBackUp) }
                }
                Setting(s.backupRestore, s.backupRestoreDetail) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            val file = FilePicker.open(s.backupPickTitle, "json") ?: return@launch
                            val text = withContext(Dispatchers.IO) {
                                // No backup comes near this; a film picked by mistake is not read whole.
                                runCatching { file.takeIf { it.length() <= 32L * 1024 * 1024 }?.readText() }.getOrNull()
                            }
                            if (text == null) {
                                toast(s.backupReadFailed)
                                return@launch
                            }
                            when (val read = backups.read(text)) {
                                is BackupRead.Ok -> {
                                    val plan = backups.plan(read.backup)
                                    if (plan.nothing) toast(s.backupRestoreNothing) else restorePlan = plan
                                }
                                else -> BackupWords.refusal(read)?.let { toast(it) }
                            }
                        }
                    }) { Text(s.backupRestore) }
                }
                Group(s.lockTitle)
                if (!appLock.pinSet) {
                    Setting(s.lockSetPin, s.lockSetPinDetail) {
                        OutlinedButton(onClick = { pinFlow = DesktopPinFlow.Set }) { Text(s.lockSetPin) }
                    }
                } else {
                    Setting(s.lockChangePin, s.lockChangePinDetail) {
                        OutlinedButton(onClick = { pinFlow = DesktopPinFlow.ChangeCheck }) { Text(s.commonChange) }
                    }
                    Toggle(s.lockOnOpen, s.lockOnOpenDetail, appLock.onOpen) { on ->
                        // Whoever switches it on has just given the PIN to reach this page.
                        if (on) AppLockSession.unlock()
                        scope.launch { settings.setLockOnOpen(on) }
                    }
                }
                Toggle(s.lockKids, AppLockWords.kidsDetail(appLock), appLock.kids) { on ->
                    when {
                        // The lock is only as good as the PIN that guards its switch.
                        !appLock.pinSet -> pinFlow = DesktopPinFlow.SetForKids
                        !on -> scope.launch { settings.setKidsLock(false) }
                        else -> {
                            scope.launch { settings.setKidsLock(true) }
                            if (appLock.kidsChats.isEmpty()) pickingKidsChats = true
                        }
                    }
                }
                Setting(s.lockAllowedChats, s.lockAllowedChatsDetail(count = appLock.kidsChats.size)) {
                    OutlinedButton(onClick = { pickingKidsChats = true }) { Text(s.commonChange) }
                }
                if (appLock.pinSet) {
                    Setting(s.lockRemovePin, s.lockRemovePinDetail) {
                        OutlinedButton(onClick = { pinFlow = DesktopPinFlow.Remove }) { Text(s.commonRemove) }
                    }
                }
                Text(
                    s.lockForgot,
                    style = MaterialTheme.typography.bodySmall,
                    color = Tone.muted,
                    modifier = Modifier.padding(horizontal = SETTING_INSET),
                )

                state.extras.updates?.let { updates ->
                    Group(s.settingsUpdates)
                    Toggle(
                        s.settingsNotifyUpdates,
                        s.settingsNotifyUpdatesDetail,
                        notifyUpdates,
                    ) { on -> scope.launch { settings.setUpdateNotify(on) } }
                    val offered = updateState.release
                    if (offered != null) {
                        Setting(UpdateWords.settingsRow(offered), UpdateWords.youHave(version), titleColor = Tone.caution) {
                            OutlinedButton(onClick = { state.updatePopup = true }) { Text(s.commonOpen, color = Tone.caution) }
                        }
                    }
                    Setting(s.settingsCheckUpdates, s.settingsAppVersion(version)) {
                        var checking by remember { mutableStateOf(false) }
                        OutlinedButton(enabled = !checking, onClick = {
                            checking = true
                            scope.launch {
                                updates.checkNow()
                                checking = false
                                // Anything to offer opens the popup, a skipped version included;
                                // the rest is a sentence.
                                when (val after = Updates.state.value) {
                                    is UpdateState.Available -> state.updatePopup = true
                                    is UpdateState.Failed -> {
                                        toast(after.message)
                                        Updates.dismiss()
                                    }
                                    else -> toast(s.settingsUpToDate(version))
                                }
                            }
                        }) { Text(if (checking) s.settingsChecking else s.settingsCheck) }
                    }
                }

                Group(s.settingsHelp)
                Setting(s.settingsWalkthrough, s.settingsWalkthroughBody) {
                    OutlinedButton(onClick = { scope.launch { settings.replaySetup() } }) { Text(s.commonOpen) }
                }
                Setting(s.settingsTour, s.settingsTourBody) {
                    OutlinedButton(onClick = { scope.launch { settings.openTour() } }) { Text(s.commonOpen) }
                }
                Setting(s.settingsWhatsNew, s.settingsWhatsNewDetail(WhatsNew.VERSION)) {
                    OutlinedButton(onClick = { whatsNew = true }) { Text(s.commonShow) }
                }
                Setting(s.settingsChangelog, s.settingsChangelogDetail) {
                    OutlinedButton(onClick = { OpenExternal.browse(WhatsNew.CHANGELOG) }) { Text(s.commonOpen) }
                }
                Setting(s.settingsReportProblem, s.settingsReportProblemDetail) {
                    OutlinedButton(onClick = { reporting = true }) { Text(s.commonOpen) }
                }
                // The logs to attach to that report (CP59), scrubbed of names and ids before they are written.
                Setting(s.logsSave, s.logsSaveDetail) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            val lines = withContext(Dispatchers.IO) { DesktopLog.recentLines(LogExport.MAX_LINES) }
                            if (lines.isEmpty()) {
                                toast(s.logsEmpty)
                                return@launch
                            }
                            val file = FilePicker.save(s.logsSaveTitle, LogExport.fileName(), "txt") ?: return@launch
                            val saved = withContext(Dispatchers.IO) {
                                runCatching {
                                    val known = LogExport.knownNames(settings, state.watched)
                                    file.writeText(LogExport.report(desktopFeedbackFacts(), lines, known))
                                }.isSuccess
                            }
                            toast(if (saved) s.logsSavedTo(file.absolutePath) else s.logsFailed)
                        }
                    }) { Text(s.logsSave) }
                }
                Setting(s.settingsPrivacy, s.settingsPrivacyComputer) {
                    OutlinedButton(onClick = { OpenExternal.browse(About.PRIVACY) }) { Text(s.commonOpen) }
                }
                Setting(s.settingsLawfulUse, s.settingsLawfulUseDetail) {
                    OutlinedButton(onClick = { OpenExternal.browse(About.LEGAL) }) { Text(s.commonOpen) }
                }
                if (SupportReminder.enabled) {
                    Setting(if (supportCounters.supporter) About.SUPPORT_THANKS_TITLE else About.SUPPORT_TITLE, s.settingsSupportDetail) {
                        OutlinedButton(onClick = { supporting = true }) { Text(s.commonShow) }
                    }
                }
                Setting(s.settingsAbout, s.settingsAboutDetail(version)) {
                    OutlinedButton(onClick = { showAbout = true }) { Text(s.commonOpen) }
                }

                Group(s.settingsAccount)
                Setting(s.settingsSignOut, s.settingsSignOutLoses) {
                    OutlinedButton(onClick = { confirmSignOut = true }) { Text(s.signoutConfirm, color = Tone.danger) }
                }
                Text(
                    s.settingsPrivacyNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tone.muted,
                    modifier = Modifier.padding(start = SETTING_INSET, end = SETTING_INSET, top = 12.dp, bottom = 24.dp),
                )
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }

    if (confirmSignOut) SignOutDialog(state, onDismiss = { confirmSignOut = false })
    if (pickingKidsChats) {
        KidsChatsDialog(chatList, appLock.kidsChats, settings, onClose = { pickingKidsChats = false })
    }
    if (supporting) SupportPopup(from = "settings", onClose = { supporting = false })
    if (pickingLanguage) LanguagePopup(settings, onClose = { pickingLanguage = false })
    if (stylingSubtitles) SubtitleStyleDialog(settings, subtitleStyle, onClose = { stylingSubtitles = false })
    if (whatsNew) WhatsNewPopup(onClose = { whatsNew = false })
    if (reporting) FeedbackPopup(onClose = { reporting = false })
    if (confirmClearWatched) {
        ClearWatchedDialog(state, watchedList.size, scope, onDismiss = { confirmClearWatched = false })
    }

    restorePlan?.let { plan ->
        ConfirmDialog(
            title = s.backupRestoreTitle,
            message = BackupWords.restoreMessage(plan),
            detail = s.backupRestoreUntouched,
            confirmLabel = s.backupRestoreConfirm,
            destructive = false,
            onConfirm = {
                restorePlan = null
                scope.launch {
                    val done = runCatching { backups.restore(plan.backup) }.isSuccess
                    toast(if (done) s.backupRestored else s.backupReadFailed)
                }
            },
            onDismiss = { restorePlan = null },
        )
    }

    confirm?.let { asked ->
        ConfirmDialog(
            title = asked.title,
            message = asked.message,
            detail = asked.detail,
            confirmLabel = s.commonClear,
            onConfirm = {
                confirm = null
                asked.action()
            },
            onDismiss = { confirm = null },
        )
    }
}

/**
 * A small stand in for the picture, with a line drawn the way mpv will draw it: mpv's font size is
 * in pixels of a 720 line frame and its `sub-pos` is the share of the height the line's foot sits
 * at, above a 22 pixel margin, so both scale straight onto the preview's height.
 */
@Composable
private fun SubtitlePreview(style: SubtitleStyle, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    BoxWithConstraints(
        modifier.width(360.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3A4A5C), Color(0xFF1A1F26)))),
    ) {
        val frame = maxHeight
        val fontSize = with(LocalDensity.current) { (frame * (style.size.mpvFontSize / 720f)).toSp() }
        val foot = frame * (1f - style.position.mpvSubPos / 100f) + frame * (22f / 720f)
        val text = TextStyle(
            color = Color.White,
            fontSize = fontSize,
            textAlign = TextAlign.Center,
            // mpv's outline, near enough; the box replaces it, as mpv's does.
            shadow = if (style.box) null else Shadow(Color.Black, blurRadius = 3f),
        )
        Text(
            s.subtitlesPreview,
            style = text,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = foot)
                .then(if (style.box) Modifier.background(Color(0xB3000000)).padding(horizontal = 4.dp, vertical = 1.dp) else Modifier),
        )
    }
}

/** A PIN being set, changed or removed from the App lock group. See the phone's `PinFlow`. */
private enum class DesktopPinFlow { Set, SetForKids, ChangeCheck, ChangeChoose, Remove }

/**
 * The pad for [flow], in the page's place: Cancel and Esc go back to the page.
 *
 * [scope] and [toast] are the page's: a finished PIN takes the pad away at once, and work launched
 * on the pad's own scope was cancelled with it, halfway through saving the PIN.
 */
@Composable
private fun PinFlowPad(
    flow: DesktopPinFlow,
    state: ShellState,
    appLock: AppLockSettings,
    scope: kotlinx.coroutines.CoroutineScope,
    toast: (String) -> Unit,
    onNext: (DesktopPinFlow?) -> Unit,
    onKidsChats: () -> Unit,
) {
    val s = LocalStrings.current
    val settings = state.settings
    val checking = flow == DesktopPinFlow.ChangeCheck || flow == DesktopPinFlow.Remove
    PinPad(
        settings = settings,
        purpose = if (checking) PinPurpose.Check else PinPurpose.Choose,
        title = if (checking) s.lockEnterCurrent else s.lockNewPin,
        onDone = { pin ->
            when (flow) {
                DesktopPinFlow.ChangeCheck -> onNext(DesktopPinFlow.ChangeChoose)
                DesktopPinFlow.Remove -> {
                    onNext(null)
                    scope.launch {
                        settings.removePin()
                        toast(s.lockPinRemovedToast)
                    }
                }
                DesktopPinFlow.Set, DesktopPinFlow.ChangeChoose, DesktopPinFlow.SetForKids -> {
                    onNext(null)
                    scope.launch {
                        settings.setPin(pin)
                        if (flow == DesktopPinFlow.SetForKids) {
                            settings.setKidsLock(true)
                            if (appLock.kidsChats.isEmpty()) onKidsChats()
                        }
                        toast(s.lockPinSetToast)
                    }
                }
            }
        },
        onCancel = { onNext(null) },
        showForgot = false,
    )
}

/**
 * The kids lock's chats (CP57): every chat in the account with a box each, saved as it is ticked.
 * The live list is what the lock already filters, so the last sync's whole list is read beside it.
 */
@Composable
private fun KidsChatsDialog(
    chatList: List<com.tmplayer.data.ChatSummary>,
    chosen: Set<Long>,
    settings: SettingsStore,
    onClose: () -> Unit,
) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val every by produceState(initialValue = chatList, chatList) {
        val remembered = runCatching { settings.cachedChatSnapshot() }.getOrNull().orEmpty()
        value = (chatList + remembered).distinctBy { it.id }
    }
    TmAlertDialog(
        onDismissRequest = onClose,
        title = { Text(s.lockAllowedChats) },
        text = {
            Column(Modifier.widthIn(max = 520.dp)) {
                Text(s.lockAllowedChatsNote, style = MaterialTheme.typography.bodyMedium, color = Tone.muted)
                val scroll = rememberScrollState()
                Column(Modifier.padding(top = 12.dp).heightIn(max = 420.dp).verticalScroll(scroll)) {
                    every.forEach { chat ->
                        val ticked = chat.id in chosen
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                scope.launch { settings.setKidsChats(if (ticked) chosen - chat.id else chosen + chat.id) }
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = ticked, onCheckedChange = null)
                            Text(chat.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(s.commonClose) } },
    )
}

/** A clear waiting on its yes: what the prompt says, and what happens on Clear. */
private class Confirm(val title: String, val message: String, val detail: String?, val action: () -> Unit)

/**
 * The label over a group of settings, in line with the words of the rows under it. Inside a
 * [SettingsColumn] the rows that follow it, up to the next label or anything that is not a
 * [Setting], are drawn as one card.
 */
@Composable
internal fun Group(title: String) {
    SettingsSectionLabel(
        title,
        Modifier.layoutId(SettingsPart.Label).padding(start = SETTING_INSET, end = SETTING_INSET, top = 16.dp),
    )
}

/**
 * One setting: what it is and what it is set to, with its control at the end. Inside a
 * [SettingsColumn] it is one row of its group's card; anywhere else it is the same row on the page.
 */
@Composable
internal fun Setting(title: String, detail: String, titleColor: Color = Color.Unspecified, control: @Composable () -> Unit) {
    // The words and the control side by side, unless the control would leave the words under
    // half the row and they need more than that: a row of four segments in German or Malayalam
    // squeezed the title to a word a line, so there the control goes under the words instead.
    Layout(
        content = {
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Tone.muted)
            }
            Box { control() }
        },
        modifier = Modifier
            .layoutId(SettingsPart.Row)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = SETTING_INSET, vertical = 10.dp),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = 16.dp.roundToPx()
        val loose = Constraints(maxWidth = width)
        val controlPlaced = measurables[1].measure(loose)
        val beside = width - controlPlaced.width - gap
        val stacked = beside < width / 2 && measurables[0].maxIntrinsicWidth(constraints.maxHeight) > beside
        val words = measurables[0].measure(
            Constraints(maxWidth = if (stacked) width else beside.coerceAtLeast(0)),
        )
        if (stacked) {
            val below = words.height + 8.dp.roundToPx()
            val height = maxOf(constraints.minHeight, below + controlPlaced.height)
            layout(width, height) {
                words.placeRelative(0, 0)
                controlPlaced.placeRelative(0, below)
            }
        } else {
            val height = maxOf(constraints.minHeight, words.height, controlPlaced.height)
            layout(width, height) {
                words.placeRelative(0, (height - words.height) / 2)
                controlPlaced.placeRelative(width - controlPlaced.width, (height - controlPlaced.height) / 2)
            }
        }
    }
}

@Composable
private fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Setting(title, detail) { Switch(checked = checked, onCheckedChange = onChange) }
}

/** What a child of a [SettingsColumn] is, which decides the gap above it and whether it is carded. */
internal enum class SettingsPart { Label, Row }

/** How far a setting's words sit in from its card's edge, and the labels from the page's. */
private val SETTING_INSET = 16.dp

/**
 * The settings page's column: the children stacked as a [Column] would, with each run of
 * consecutive [Setting] rows drawn as one grouped card on [Tone.surface] (rounded to
 * [Corner.Large], dividers between the rows inset to where their words start, and in daylight a
 * hairline round the outside). A [Group] label gets room above it and sits 8 dp over its card;
 * anything else (a note, the storage bar, a progress line) sits 8 dp from its neighbours and ends
 * the card it follows.
 *
 * One layout rather than a card composable round each group, so the dozen groups (several of them
 * in their own files and shared with the About page) need no wrapping at every call.
 */
@Composable
internal fun SettingsColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val cards = remember { mutableStateOf(emptyList<SettingsCard>()) }
    val fill = Tone.surface
    val line = Tone.outline
    val hairline = !LocalDarkTheme.current
    Layout(
        content = content,
        modifier = modifier.drawBehind {
            val radius = CornerRadius(Corner.Large.toPx())
            val stroke = 1.dp.toPx()
            val inset = SETTING_INSET.toPx()
            val rtl = layoutDirection == LayoutDirection.Rtl
            cards.value.forEach { card ->
                val topLeft = Offset(0f, card.top.toFloat())
                val box = Size(size.width, (card.bottom - card.top).toFloat())
                drawRoundRect(fill, topLeft, box, radius)
                card.dividers.forEach { y ->
                    if (rtl) {
                        drawLine(line, Offset(0f, y.toFloat()), Offset(size.width - inset, y.toFloat()), stroke)
                    } else {
                        drawLine(line, Offset(inset, y.toFloat()), Offset(size.width, y.toFloat()), stroke)
                    }
                }
                if (hairline) {
                    drawRoundRect(
                        line,
                        topLeft + Offset(stroke / 2, stroke / 2),
                        Size(box.width - stroke, box.height - stroke),
                        radius,
                        style = Stroke(stroke),
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val placeables = measurables.map {
            it.measure(Constraints(minWidth = 0, maxWidth = width, minHeight = 0))
        }
        val kinds = measurables.map { it.layoutId as? SettingsPart }
        val gap = 8.dp.roundToPx()
        val labelGap = 16.dp.roundToPx()
        val ys = IntArray(placeables.size)
        val runs = mutableListOf<SettingsCard>()
        var y = 0
        var runTop = -1
        var dividers = mutableListOf<Int>()
        fun closeRun(bottom: Int) {
            if (runTop >= 0) runs += SettingsCard(runTop, bottom, dividers)
            runTop = -1
            dividers = mutableListOf()
        }
        placeables.forEachIndexed { index, placeable ->
            val kind = kinds[index]
            val previous = kinds.getOrNull(index - 1)
            if (index > 0) {
                y += when {
                    kind == SettingsPart.Row && previous == SettingsPart.Row -> 0
                    kind == SettingsPart.Label -> labelGap
                    else -> gap
                }
            }
            if (kind == SettingsPart.Row) {
                if (runTop < 0) runTop = y else dividers += y
            } else {
                if (index > 0) closeRun(ys[index - 1] + placeables[index - 1].height)
            }
            ys[index] = y
            y += placeable.height
        }
        closeRun(y)
        if (cards.value != runs) cards.value = runs
        layout(width, y) {
            placeables.forEachIndexed { index, placeable -> placeable.placeRelative(0, ys[index]) }
        }
    }
}

/** One group's card in a [SettingsColumn], in pixels from the column's top. */
internal data class SettingsCard(val top: Int, val bottom: Int, val dividers: List<Int>)

@Composable
internal fun Stepper(onLess: () -> Unit, onMore: () -> Unit) {
    val s = LocalStrings.current
    Row {
        IconButton(onClick = onLess) { Icon(TmIcons.Remove, contentDescription = s.commonLess) }
        IconButton(onClick = onMore) { Icon(TmIcons.Add, contentDescription = s.commonMore) }
    }
}

/**
 * Settings, Subtitle style, on the desktop: the preview at the top, where each change shows the
 * moment it is made, and the size, the background box and the position under it. Each is saved as
 * it changes; Close leaves.
 */
@Composable
private fun SubtitleStyleDialog(settings: SettingsStore, subtitleStyle: SubtitleStyle, onClose: () -> Unit) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    TmAlertDialog(
        onDismissRequest = onClose,
        title = { Text(s.settingsSubtitleStyle) },
        text = {
            Column(Modifier.widthIn(max = 520.dp)) {
                SubtitlePreview(subtitleStyle, Modifier.padding(bottom = 8.dp))
                Setting(s.subtitlesSize, s.settingsSubtitleSizeHint(subtitleStyle.size.label)) {
                    val sizes = SubtitleSize.entries
                    fun size(by: Int) {
                        val next = sizes[(subtitleStyle.size.ordinal + by).coerceIn(0, sizes.lastIndex)]
                        scope.launch { settings.setSubtitleStyle(subtitleStyle.copy(size = next)) }
                    }
                    Stepper(onLess = { size(-1) }, onMore = { size(1) })
                }
                Toggle(s.subtitlesBox, s.settingsSubtitleBoxDetail, subtitleStyle.box) { on ->
                    scope.launch { settings.setSubtitleStyle(subtitleStyle.copy(box = on)) }
                }
                Setting(s.subtitlesPosition, s.settingsSubtitlePositionHint) {
                    SingleChoiceSegmentedButtonRow {
                        SubtitlePosition.entries.forEachIndexed { index, place ->
                            SegmentedButton(
                                selected = subtitleStyle.position == place,
                                onClick = { scope.launch { settings.setSubtitleStyle(subtitleStyle.copy(position = place)) } },
                                shape = SegmentedButtonDefaults.itemShape(index, SubtitlePosition.entries.size),
                            ) { Text(place.label) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(s.commonClose) } },
    )
}

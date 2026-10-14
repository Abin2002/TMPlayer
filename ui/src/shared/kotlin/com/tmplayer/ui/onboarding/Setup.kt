package com.tmplayer.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.tmplayer.data.AuthState
import com.tmplayer.i18n.Strings

/**
 * The short setup after signing in (CP67, CP69), on the phone, the television and the desktop
 * alike. It replaced the two page tour before sign in and the one card after it (CP42): the sign
 * in screen comes first, and once the account is in, a few steps ask one thing each, every step
 * with a picture of what each answer looks like, Skip, and Skip all. The quick tour ([Tour])
 * follows it.
 *
 * Who sees it: a sign in screen arms it (`SettingsStore.armSetup`), so an install that was signed
 * in before this existed never does, and a fresh sign in always does. Settings can run it again.
 */
enum class SetupStep {
    /** Which chats the sidebar lists: everything, or only the account's Telegram folders. */
    Library,

    /** Posters and overviews looked up online by the cleaned title, or plain tiles. */
    Posters,

    /** Low storage mode: stream big videos without keeping them whole. */
    Storage,

    /** Full pictures and motion, or Lighter pictures, Reduce motion and no thumbnails while seeking. */
    Performance,

    /** On a phone only: Save mobile data and downloads that wait for Wi-Fi, or no limits. */
    MobileData,

    /**
     * A device short of memory or storage (CP68): apply the recommended settings, or not now. It
     * takes the storage step's place, since Low storage mode is among them when storage is short.
     */
    Device,
    ;

    /** The two answers, in the order they are shown. The first is the recommended one. */
    val choices: List<SetupChoice> get() = SetupChoice.entries.filter { it.step == this }
}

/** One answer on a [SetupStep]. */
enum class SetupChoice(val step: SetupStep) {
    Everything(SetupStep.Library),
    OnlyFolders(SetupStep.Library),
    PostersOn(SetupStep.Posters),
    PostersOff(SetupStep.Posters),
    KeepWhilePlaying(SetupStep.Storage),
    LowStorage(SetupStep.Storage),
    FullQuality(SetupStep.Performance),
    Lighter(SetupStep.Performance),
    SaveData(SetupStep.MobileData),
    NoLimits(SetupStep.MobileData),
    ApplyRecommended(SetupStep.Device),
    NotNow(SetupStep.Device),
    ;

    /**
     * True for the answer the setup suggests. That is every setting's default, except on Mobile
     * data, where saving it is suggested although the settings start off: an install updated from
     * a version without the step keeps behaving as it did.
     */
    val recommended: Boolean get() = step.choices.first() == this

    /**
     * Whether this answer is pictured with a screenshot. The storage and mobile data steps draw
     * theirs, since a frame of the app says nothing about what is kept or fetched.
     */
    val screenshot: Boolean
        get() = step == SetupStep.Library || step == SetupStep.Posters || step == SetupStep.Performance
}

/** The settings the setup reads and writes, as they are now. */
data class SetupValues(
    val onlyFolders: Boolean,
    val posters: Boolean,
    val lowStorage: Boolean,
    val lighter: Boolean = false,
    val saveData: Boolean = false,
) {
    /** The answer on [step] that matches what is set, the one a remote starts on. */
    fun current(step: SetupStep): SetupChoice = when (step) {
        SetupStep.Library -> if (onlyFolders) SetupChoice.OnlyFolders else SetupChoice.Everything
        SetupStep.Posters -> if (posters) SetupChoice.PostersOn else SetupChoice.PostersOff
        SetupStep.Storage -> if (lowStorage) SetupChoice.LowStorage else SetupChoice.KeepWhilePlaying
        SetupStep.Performance -> if (lighter) SetupChoice.Lighter else SetupChoice.FullQuality
        // A remote starts on the suggestion here, the settings being off until somebody chooses.
        SetupStep.MobileData -> SetupChoice.SaveData
        // Apply is what the step is for; Not now is the press that changes nothing.
        SetupStep.Device -> SetupChoice.ApplyRecommended
    }
}

object Setup {

    /**
     * The steps this account, this build and this device get. Library only when the account has
     * Telegram folders (with none there is nothing to choose between); Posters only where the
     * build looks them up at all; Storage wherever Low storage mode exists; Performance on every
     * device; Mobile data only on a phone, the one device that is ever on a metered connection
     * for long.
     *
     * A device short of memory or storage, with settings still to recommend, gets the Device step
     * in Storage's place, since Low storage mode is among them when storage is short. When those
     * settings include the lighter ones ([devicePerformance]), it takes Performance's place too,
     * so nothing is asked twice.
     */
    fun steps(
        folderCount: Int,
        posters: Boolean,
        storage: Boolean = STORAGE_STEP,
        device: Boolean = false,
        devicePerformance: Boolean = device,
        phone: Boolean = false,
    ): List<SetupStep> =
        buildList {
            if (folderCount > 0) add(SetupStep.Library)
            if (posters) add(SetupStep.Posters)
            if (device) add(SetupStep.Device) else if (storage) add(SetupStep.Storage)
            if (!(device && devicePerformance)) add(SetupStep.Performance)
            if (phone) add(SetupStep.MobileData)
        }

    /**
     * Whether the storage step is offered. Low storage mode is core's (`TdByteWindow`), so the
     * phone, the television and the desktop all have it.
     */
    const val STORAGE_STEP = true

    /** What the shell does about the setup right now. */
    enum class Decision {
        /** Nothing to do: not armed, or already done. */
        None,

        /** Armed, but the chat list (and with it the folder list) is not in yet. */
        Wait,

        /** Show it. */
        Show,
    }

    /**
     * The setup's next move. [pending] is the armed flag; [chatsLoaded] whether the chat list has
     * come from TDLib, which sends the folders before it, so an empty folder list then really
     * means none. [waitedOut] gives up waiting on a list that is slow to come, and the setup goes
     * ahead with the folders known so far.
     */
    fun decide(pending: Boolean, chatsLoaded: Boolean, waitedOut: Boolean = false): Decision = when {
        !pending -> Decision.None
        !chatsLoaded && !waitedOut -> Decision.Wait
        else -> Decision.Show
    }

    /** How long the setup waits for the chat list before going ahead without it. */
    const val WAIT_FOR_CHATS_MS = 8_000L

    /** Whether [auth] is a screen of the sign in itself, which is what arms the setup. */
    fun isSignInStep(auth: AuthState): Boolean = when (auth) {
        AuthState.ChooseMethod, is AuthState.Qr, is AuthState.Phone, is AuthState.Code, is AuthState.Password -> true
        AuthState.Connecting, AuthState.Ready, is AuthState.Failed -> false
    }
}

/**
 * Where the setup is. Picking an answer or Skip moves on and finishes after the last step; Back
 * goes to the step before, and from the first leaves the setup as Skip all does. Snapshot state,
 * so a screen that reads [index] recomposes when it moves.
 */
class SetupState(val steps: List<SetupStep>, start: Int = 0) {
    init {
        require(steps.isNotEmpty()) { "a setup with no steps is not shown" }
    }

    var index by mutableIntStateOf(start.coerceIn(0, steps.lastIndex))
        private set

    val step: SetupStep get() = steps[index]
    val isFirst: Boolean get() = index == 0
    val isLast: Boolean get() = index == steps.lastIndex

    /** Skip all is offered while more than this step is left; on the last, Skip says the same. */
    val canSkipAll: Boolean get() = !isLast

    /** Moves on a step; false when this was the last, which means the setup is done. */
    fun next(): Boolean {
        if (isLast) return false
        index++
        return true
    }

    /** Moves back a step; false on the first, which means leaving the setup. */
    fun back(): Boolean {
        if (isFirst) return false
        index--
        return true
    }
}

/** A step's heading and the sentence under it. */
data class SetupCopy(val title: String, val body: String)

/** An answer's name and the line under it. */
data class ChoiceCopy(val title: String, val detail: String)

fun Strings.setupCopy(step: SetupStep): SetupCopy = when (step) {
    SetupStep.Library -> SetupCopy(setupLibraryTitle, setupLibraryBody)
    SetupStep.Posters -> SetupCopy(setupPostersTitle, setupPostersBody)
    SetupStep.Storage -> SetupCopy(setupStorageTitle, setupStorageBody)
    SetupStep.Performance -> SetupCopy(setupPerformanceTitle, setupPerformanceBody)
    SetupStep.MobileData -> SetupCopy(setupMobileTitle, setupMobileBody)
    // The screen says which: memory, storage or both (fitHeadline).
    SetupStep.Device -> SetupCopy(deviceShortMemory, deviceQuestion)
}

fun Strings.choiceCopy(choice: SetupChoice): ChoiceCopy = when (choice) {
    SetupChoice.Everything -> ChoiceCopy(setupLibraryEverything, setupLibraryEverythingDetail)
    SetupChoice.OnlyFolders -> ChoiceCopy(setupLibraryFolders, setupLibraryFoldersDetail)
    SetupChoice.PostersOn -> ChoiceCopy(setupPostersOn, setupPostersOnDetail)
    SetupChoice.PostersOff -> ChoiceCopy(setupPostersOff, setupPostersOffDetail)
    SetupChoice.KeepWhilePlaying -> ChoiceCopy(setupStorageNormal, setupStorageNormalDetail)
    SetupChoice.LowStorage -> ChoiceCopy(setupStorageLow, setupStorageLowDetail)
    SetupChoice.FullQuality -> ChoiceCopy(setupPerformanceFull, setupPerformanceFullDetail)
    SetupChoice.Lighter -> ChoiceCopy(setupPerformanceLighter, setupPerformanceLighterDetail)
    SetupChoice.SaveData -> ChoiceCopy(setupMobileSave, setupMobileSaveDetail)
    SetupChoice.NoLimits -> ChoiceCopy(setupMobileNoLimits, setupMobileNoLimitsDetail)
    SetupChoice.ApplyRecommended -> ChoiceCopy(deviceApply, deviceQuestion)
    SetupChoice.NotNow -> ChoiceCopy(deviceNotNow, settingsSavesAsYouGo)
}

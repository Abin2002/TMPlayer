package com.tmplayer.data

import com.tmplayer.platform.Background
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The app lock's PIN (CP57): four digits, kept only as a salted PBKDF2 hash.
 *
 * Four digits are ten thousand guesses, so no hash makes a stolen settings file safe on its own;
 * what the hash buys is that the PIN itself is never written down, and somebody reading the file
 * over a shoulder learns nothing they could type. The real guard against guessing is [PinBackoff],
 * which makes ten thousand tries at the keypad take weeks.
 *
 * Stored as `v1:<iterations>:<salt>:<hash>`, Base64 for the two byte strings, so a later version
 * can raise the iteration count and still read a PIN set under this one.
 */
object PinHash {

    /** How many digits a PIN has. Fixed, so the pad can tell when one is complete. */
    const val LENGTH = 4

    /**
     * Enough to take a visible moment on a desktop and well under a frame's budget of patience on
     * a stick: the pad hashes once per complete PIN, never per key.
     */
    private const val ITERATIONS = 20_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val VERSION = "v1"

    /** Exactly [LENGTH] ASCII digits. */
    fun isValid(pin: String): Boolean = pin.length == LENGTH && pin.all { it in '0'..'9' }

    /** The line to store for [pin], with a fresh random salt unless one is handed in (tests). */
    fun hash(pin: String, salt: ByteArray = newSalt()): String {
        require(isValid(pin)) { "A PIN is $LENGTH digits" }
        val encoder = Base64.getEncoder()
        return listOf(VERSION, ITERATIONS.toString(), encoder.encodeToString(salt), encoder.encodeToString(derive(pin, salt, ITERATIONS)))
            .joinToString(":")
    }

    /**
     * Whether [pin] is the one [stored] was made from. A line that cannot be read is never a
     * match, so a damaged setting locks rather than opens; signing out clears it either way.
     * The comparison takes the same time however many bytes agree.
     */
    fun verify(pin: String, stored: String?): Boolean {
        if (stored == null || !isValid(pin)) return false
        val parts = stored.split(':')
        if (parts.size != 4 || parts[0] != VERSION) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        val decoder = Base64.getDecoder()
        val salt = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(parts[3]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(derive(pin, salt, iterations), expected)
    }

    private fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

/**
 * The clock the PIN's waits are timed on: one that only moves forward, so setting the device's
 * date ahead does not end a wait. Milliseconds from an arbitrary start; the Android app points it
 * at `SystemClock.elapsedRealtime`, which keeps counting through deep sleep.
 */
object PinClock {
    @Volatile
    var source: () -> Long = { System.nanoTime() / 1_000_000L }

    fun now(): Long = source()
}

/**
 * Wrong guesses and the wait they earn, as stored beside the PIN.
 *
 * [failures] counts wrong PINs since the last right one; [waitUntil] is the [PinClock] time before
 * which the pad takes no PIN at all. Both survive a restart, or closing the app would be the way
 * round the wait: what is stored is the wait left and the [PinClock] time it was written at (see
 * [stored] and [restored]), since that clock starts again from nothing after a reboot.
 */
data class PinAttempts(val failures: Int = 0, val waitUntil: Long = 0L) {

    /** How long is left of the wait at [now], or 0 when a PIN may be tried. */
    fun waitMs(now: Long): Long = (waitUntil - now).coerceAtLeast(0L)

    /** After one more wrong PIN at [now]. */
    fun failed(now: Long): PinAttempts {
        val count = failures + 1
        val wait = PinBackoff.waitAfter(count)
        return PinAttempts(count, if (wait > 0) now + wait else 0L)
    }

    /** How many more wrong PINs are free before the next one earns a wait. */
    val freeLeft: Int get() = (PinBackoff.FREE_TRIES - failures).coerceAtLeast(0)

    companion object {
        /**
         * The attempts from what was stored: [waitLeftMs] as of [stampedAt]. A clock behind the
         * stamp has started again (a reboot, a new desktop session), and then none of the wait
         * counts as served; it is never longer than [PinBackoff.MAX_WAIT_MS] either way.
         */
        fun restored(failures: Int, waitLeftMs: Long, stampedAt: Long, now: Long): PinAttempts {
            val served = if (now >= stampedAt) now - stampedAt else 0L
            val left = (waitLeftMs.coerceIn(0L, PinBackoff.MAX_WAIT_MS) - served).coerceAtLeast(0L)
            return PinAttempts(failures, if (left > 0) now + left else 0L)
        }
    }
}

/**
 * How long a run of wrong PINs makes the pad wait: nothing for the first [FREE_TRIES], which is
 * room for a child's honest attempt or a fumbled remote, then 30 seconds, doubling with every
 * further miss up to a quarter of an hour. Ten thousand guesses at that ceiling is over three
 * months of trying.
 */
object PinBackoff {
    const val FREE_TRIES = 3
    private const val FIRST_WAIT_MS = 30_000L
    const val MAX_WAIT_MS = 15 * 60_000L

    /** The wait earned by the [failures]th wrong PIN in a row. */
    fun waitAfter(failures: Int): Long {
        if (failures < FREE_TRIES) return 0L
        val doublings = (failures - FREE_TRIES).coerceAtMost(10)
        return (FIRST_WAIT_MS shl doublings).coerceAtMost(MAX_WAIT_MS)
    }
}

/** What the pad hears back for a complete PIN. */
sealed interface PinCheck {
    /** The right PIN: the wrong guesses are forgotten. */
    data object Right : PinCheck

    /** A wrong PIN; [attempts] is the new count, with any wait it earned. */
    data class Wrong(val attempts: PinAttempts) : PinCheck

    /** Still inside an earlier wait: the PIN was not even looked at. */
    data class Waiting(val waitMs: Long) : PinCheck

    /** There is no PIN to check against: nothing is locked. */
    data object NoPin : PinCheck
}

/**
 * The kids lock (CP57): while it is on, browsing lists only the chats the viewer allowed.
 *
 * [allowed] is null when the lock is off, so every caller can ask one question, [allows], and the
 * answer for an unlocked app is always yes. An empty set is a lock with nothing chosen yet, which
 * shows no chats at all rather than all of them: a lock that fails open is not a lock.
 */
object KidsLock {

    /** Whether a chat may be shown under [allowed] (null: the lock is off). */
    fun allows(allowed: Set<Long>?, chatId: Long): Boolean = allowed == null || chatId in allowed

    /** [items] without those from chats the lock leaves out, in their order. */
    fun <T> filter(items: List<T>, allowed: Set<Long>?, chatId: (T) -> Long): List<T> =
        if (allowed == null) items else items.filter { chatId(it) in allowed }

    /** The chat list itself. */
    fun chats(chats: List<ChatSummary>, allowed: Set<Long>?): List<ChatSummary> = filter(chats, allowed) { it.id }

    /** Videos, wherever they came from: Home's rows, search across chats. */
    fun media(items: List<MediaItem>, allowed: Set<Long>?): List<MediaItem> = filter(items, allowed) { it.chatId }

    /** Continue watching, downloads and the other records of a video. */
    fun records(records: List<ResumeRecord>, allowed: Set<Long>?): List<ResumeRecord> = filter(records, allowed) { it.chatId }

    /**
     * Whether the lock is in force, from what is stored: switched on, and with a PIN to guard it.
     * A lock without a PIN could be switched off by anyone, so it is not honoured; Settings never
     * lets the two come apart, and this is the second line behind that.
     */
    fun inForce(on: Boolean, pinSet: Boolean): Boolean = on && pinSet
}

/**
 * The kids lock as the running process sees it, for the code that lists chats without a settings
 * store to hand: the repository's search and recent videos, and the shared chat list.
 *
 * Filled from the store by [follow], once per process (the Android `App`, the desktop `main`).
 * Until the first value arrives [awaitReady] holds a caller for a moment, so a cold start does
 * not flash every chat before the lock is read; in a process that never follows a store (the
 * tests, the promo fixture) it does not wait at all.
 */
object ChatGate {
    private val state = MutableStateFlow<Set<Long>?>(null)
    private val ready = MutableStateFlow(false)
    @Volatile
    private var following = false

    /** The allowed chats, or null while the kids lock is off. */
    val allowed: StateFlow<Set<Long>?> = state.asStateFlow()

    /** Keeps [allowed] in step with [store] for the life of the process. */
    fun follow(store: SettingsStore) {
        if (following) return
        following = true
        Background.scope.launch {
            store.kidsAllowedChats.collect { chats ->
                state.value = chats
                ready.value = true
            }
        }
    }

    /** Sets the gate directly. For tests, and for a process with no store to follow. */
    fun set(chats: Set<Long>?) {
        state.value = chats
        ready.value = true
    }

    /** Waits, briefly, for the stored lock to be read, then answers with it. */
    suspend fun awaitReady(): Set<Long>? {
        if (following && !ready.value) withTimeoutOrNull(READY_WAIT_MS) { ready.first { it } }
        return state.value
    }

    private const val READY_WAIT_MS = 2_000L
}

/**
 * Whether the app has been unlocked since it was last opened, for the "Ask when the app opens"
 * option. Process wide, so the player (another activity on Android) coming and going does not
 * lock it; the Android app locks it again when its last activity leaves the screen.
 */
object AppLockSession {
    private val state = MutableStateFlow(false)

    val unlocked: StateFlow<Boolean> = state.asStateFlow()

    fun unlock() {
        state.value = true
    }

    fun lock() {
        state.value = false
    }
}

/** The app lock as Settings shows it. [kids] is only true with a PIN set ([KidsLock.inForce]). */
data class AppLockSettings(
    val pinSet: Boolean = false,
    val onOpen: Boolean = false,
    val kids: Boolean = false,
    val kidsChats: Set<Long> = emptySet(),
)

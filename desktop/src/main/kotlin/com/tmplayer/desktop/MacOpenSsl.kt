package com.tmplayer.desktop

import com.tmplayer.desktop.os.OsInfo
import java.io.File

/**
 * The TDLib that tdl-coroutines 15.0.0 ships for macOS links OpenSSL 3 dynamically, by the path it
 * had on the machine that built it (`/opt/homebrew/opt/openssl@3/...`), and macOS has no public
 * libssl of its own: on a Mac without that Homebrew formula, sign in failed with "Library not
 * loaded" (upstream g000sha256/tdl-coroutines#163). The build rewrites those references, and the
 * one to the build machine's libjvm, to `@rpath/<name>`, and ships `libcrypto.3.dylib` and
 * `libssl.3.dylib` with the same `@rpath` install names in the app's macOS resources. Loading them
 * here, before the first TdlClient, is what TDLib's references then resolve to; `@rpath/libjvm.dylib`
 * is the JVM already running.
 */
object MacOpenSsl {

    /** libcrypto first: libssl needs it. */
    private val NAMES = listOf("libcrypto.3.dylib", "libssl.3.dylib")

    /** Loads both libraries from the app's resources; says where from, or why nothing was loaded. */
    fun preload(): String {
        if (!OsInfo.isMac) return "not macOS"
        // Set by the packaged app's launcher; a `gradle run` on a Mac has Homebrew's OpenSSL anyway.
        val dir = System.getProperty("compose.application.resources.dir")?.let(::File)
            ?: return "not a packaged app"
        NAMES.forEach { name ->
            val file = File(dir, name)
            check(file.isFile) { "$name is not in $dir" }
            System.load(file.absolutePath)
        }
        return "preloaded ${NAMES.joinToString()} from $dir"
    }
}

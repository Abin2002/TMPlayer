package com.tmplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashRecordTest {

    @get:Rule
    val dir = TemporaryFolder()

    private fun crash(): Throwable =
        IllegalStateException("outer", IllegalArgumentException("inner"))

    @Test
    fun `a record survives the trip to the disk`() {
        val file = dir.root.resolve("crash/pending.json")
        CrashRecord.of(crash(), "1.26.0", 12600).write(file)

        val read = CrashRecord.read(file)!!
        assertEquals("1.26.0", read.versionName)
        assertEquals(12600L, read.versionCode)
        assertEquals(
            listOf("java.lang.IllegalStateException", "java.lang.IllegalArgumentException"),
            read.chain.map { it.type },
        )
        assertEquals("outer", read.chain[0].message)
        assertEquals(crash().stackTrace.first().methodName, read.chain[0].frames.first().methodName)
        assertFalse("no temporary file is left behind", dir.root.resolve("crash/pending.json.part").exists())
    }

    @Test
    fun `a record without a message reads back without one`() {
        val file = dir.root.resolve("pending.json")
        CrashRecord.of(RuntimeException(), "1.26.0", 12600).write(file)
        assertNull(CrashRecord.read(file)!!.chain.single().message)
    }

    @Test
    fun `a missing or broken file is no record`() {
        val file = dir.root.resolve("pending.json")
        assertNull(CrashRecord.read(file))
        file.writeText("{ half")
        assertNull(CrashRecord.read(file))
    }

    @Test
    fun `the event puts the cause first and the oldest frame first, as Sentry does`() {
        val record = CrashRecord.of(crash(), "1.26.0", 12600)
        val event = record.toEvent()
        val exceptions = event.exceptions!!

        assertEquals(listOf("IllegalArgumentException", "IllegalStateException"), exceptions.map { it.type })
        assertEquals("java.lang", exceptions.last().module)
        assertEquals("tmplayer@1.26.0", event.release)
        assertEquals(false, exceptions.last().mechanism?.isHandled)
        val frames = exceptions.last().stacktrace!!.frames!!
        assertEquals(record.chain[0].frames.last().methodName, frames.first().function)
        assertTrue(frames.any { it.isInApp == true })
    }
}

package com.tmplayer.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopCrashReportsTest {

    @Test
    fun `a version reads as the phone's version code for it`() {
        assertEquals(12702L, DesktopCrashReports.versionCode("1.27.2"))
        assertEquals(12800L, DesktopCrashReports.versionCode("1.28.0"))
        assertEquals(10000L, DesktopCrashReports.versionCode("1.0.0-dev"))
        assertEquals(0L, DesktopCrashReports.versionCode("dev"))
    }
}

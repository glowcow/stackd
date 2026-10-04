package dev.glowcow.stackd.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {

    @Test
    fun parsesReleaseVersionsOnly() {
        assertEquals(listOf(0, 3, 4), AppUpdater.parse("0.3.4"))
        assertNull(AppUpdater.parse("424b6275"))
        assertNull(AppUpdater.parse("0.3"))
        assertNull(AppUpdater.parse("0.3.4-rc1"))
        assertNull(AppUpdater.parse(""))
    }

    @Test
    fun comparesByNumberNotByText() {
        assertTrue(AppUpdater.isNewer("0.3.10", "0.3.9"))
        assertTrue(AppUpdater.isNewer("1.0.0", "0.9.9"))
        assertFalse(AppUpdater.isNewer("0.3.4", "0.3.4"))
        assertFalse(AppUpdater.isNewer("0.3.3", "0.3.4"))
    }

    @Test
    fun aDevBuildIsNeverOffered() {
        assertFalse(AppUpdater.isNewer("0.3.5", "424b6275"))
        assertFalse(AppUpdater.isNewer("nightly", "0.3.4"))
    }

    @Test
    fun releaseNotesLoseMarkdownMarks() {
        assertEquals("Changes since v0.3.3\n\n- fix(ui): a b.apk", AppUpdater.plain("## Changes since v0.3.3\n\n- fix(ui): a `b.apk`\n"))
    }
}

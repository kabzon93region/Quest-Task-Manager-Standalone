package com.quest3.taskmanager

import org.junit.Assert.assertEquals
import org.junit.Test

class RunningAppsProbeTest {

    @Test
    fun `normalizePackageName strips Activity suffix`() {
        val probe = RunningAppsProbe
        assertEquals("com.example.app", probe.normalizePackageName("com.example.app.MainActivity"))
    }

    @Test
    fun `normalizePackageName strips Service suffix`() {
        val probe = RunningAppsProbe
        assertEquals("com.example.app", probe.normalizePackageName("com.example.app.MyService"))
    }

    @Test
    fun `normalizePackageName strips Application suffix`() {
        val probe = RunningAppsProbe
        assertEquals("com.example.app", probe.normalizePackageName("com.example.app.App"))
    }

    @Test
    fun `normalizePackageName does not strip mixed case package`() {
        val probe = RunningAppsProbe
        assertEquals("com.example.myApp", probe.normalizePackageName("com.example.myApp"))
    }

    @Test
    fun `normalizePackageName handles slash separator`() {
        val probe = RunningAppsProbe
        assertEquals("com.example.app", probe.normalizePackageName("com.example.app/SomeActivity"))
    }

    @Test
    fun `normalizePackageName handles colon separator`() {
        val probe = RunningAppsProbe
        assertEquals("com.example.app", probe.normalizePackageName("com.example.app:service"))
    }

    @Test
    fun `normalizePackageName handles IntoTheRadius style`() {
        val probe = RunningAppsProbe
        assertEquals("com.IntoTheRadius", probe.normalizePackageName("com.IntoTheRadius"))
    }

    @Test
    fun `normalizePackageName strips multiple class segments`() {
        val probe = RunningAppsProbe
        assertEquals("com.example", probe.normalizePackageName("com.example.app.ui.MainActivity"))
    }

    @Test
    fun `normalizePackageName returns plain name without dots`() {
        val probe = RunningAppsProbe
        assertEquals("native_process", probe.normalizePackageName("native_process"))
    }

    @Test
    fun `isLikelyPackageName recognizes valid package`() {
        val probe = RunningAppsProbe
        assertEquals(true, probe.isLikelyPackageName("com.example.app"))
    }

    @Test
    fun `isLikelyPackageName rejects native process`() {
        val probe = RunningAppsProbe
        assertEquals(false, probe.isLikelyPackageName("media.codec"))
    }

    @Test
    fun `isLikelyPackageName rejects short name`() {
        val probe = RunningAppsProbe
        assertEquals(false, probe.isLikelyPackageName("app"))
    }
}

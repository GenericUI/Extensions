package com.nexustools.genericui.ext.pip.desktop

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.desktop.core.DesktopInteractiveSurfaceSpec
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.ext.pip.FragmentPipHelper
import com.nexustools.genericui.ext.pip.FragmentPipRegistry
import com.nexustools.genericui.ext.pip.PipRegistry
import com.nexustools.genericui.ext.pip.PipSessionConfig
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopFloatingPipBackendTest {

    @BeforeTest
    fun setUp() {
        PipRegistry.resetForTest()
        FragmentPipRegistry.clearForTest()
        UiTimerScheduler.clearAllTimers()
    }

    @AfterTest
    fun tearDown() {
        UiTimerScheduler.clearAllTimers()
        FragmentPipRegistry.clearForTest()
        PipRegistry.resetForTest()
    }

    @Test
    fun surfaceMirrorForwardsVBlankDeduplicatesSimultaneousTicksAndRestoresOriginal() {
        var dispatchCount = 0
        val receivedTimestamps = mutableListOf<Double?>()
        val originalVBlank: (Double?) -> Unit = { ts ->
            dispatchCount++
            receivedTimestamps.add(ts)
        }

        val spec = DesktopInteractiveSurfaceSpec(
            surfaceWidth = { 640 },
            surfaceHeight = { 360 },
            swapCount = { 42L }
        )
        val sourceWidget = DesktopNativeWidget("TestToolkit", "VideoCanvas")
        spec.attachTo(sourceWidget)
        sourceWidget.vblankDispatch = originalVBlank

        val mirrorWidget = DesktopNativeWidget("TestToolkit", "PipMirror")
        val mirror = DesktopPipSurfaceMirror()

        assertTrue(mirror.mount(sourceWidget, mirrorWidget))
        assertTrue(mirror.isMounted)
        assertSame(spec, mirrorWidget.interactiveSurfaceSpec)
        assertNotNull(mirrorWidget.vblankDispatch)

        // Mirrored widget drives source VBlank when main window is minimized.
        mirrorWidget.vblankDispatch?.invoke(16.67)
        assertEquals(1, dispatchCount)

        // Simultaneous macOS tick with the same timestamp on source widget is deduplicated.
        sourceWidget.vblankDispatch?.invoke(16.67)
        assertEquals(1, dispatchCount)

        // Distinct frame timestamp passes cleanly.
        mirrorWidget.vblankDispatch?.invoke(33.34)
        assertEquals(2, dispatchCount)
        assertEquals(listOf<Double?>(16.67, 33.34), receivedTimestamps)

        mirror.unmount()
        assertFalse(mirror.isMounted)
        assertNull(mirrorWidget.vblankDispatch)
        assertNull(mirrorWidget.interactiveSurfaceSpec)
        assertSame(originalVBlank, sourceWidget.vblankDispatch)
    }

    @Test
    fun desktopBackendMinimizeAutoEnterAndRestoreCycle() {
        DesktopPipProvider.register()
        assertTrue(PipRegistry.hasRegisteredFactory())

        val backend = DesktopFloatingPipBackend().apply {
            headlessImmediateTransitions = true
        }
        PipRegistry.registerBackendFactory { backend }

        val window = Window("Desktop Main", Size(1280, 720)).apply {
            windowBounds = Rect(50, 50, 1280, 720)
        }
        val fragment = Fragment()
        val target = Container("desktop-video").apply {
            htmlTag = "video"
            bounds = Rect(0, 0, 640, 360)
        }
        fragment.addChild(target)
        window.setContent(fragment)

        val helper = FragmentPipHelper(fragment).apply {
            pipTarget = target
            isActivelyPlayingProvider = { true }
            configProvider = { PipSessionConfig(title = "Desktop Clip") }
        }

        try {
            helper.bindWindow(window)
            window.minimize()
            assertTrue(helper.isInPipMode)
            assertNotNull(backend.activePipWindow)

            window.restore()
            assertFalse(helper.isInPipMode)
            assertNull(backend.activePipWindow)
            assertSame(fragment, target.parent)
        } finally {
            helper.dispose()
            window.close()
        }
    }
}

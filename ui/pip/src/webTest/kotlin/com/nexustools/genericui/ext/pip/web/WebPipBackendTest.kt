package com.nexustools.genericui.ext.pip.web

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.ext.pip.FragmentPipHelper
import com.nexustools.genericui.ext.pip.FragmentPipRegistry
import com.nexustools.genericui.ext.pip.PipRegistry
import com.nexustools.genericui.ext.pip.PipSessionConfig
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

class WebPipBackendTest {

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
    fun webBackendEntersInPlaceFallbackAndRestoresComponentOnExit() {
        WebPipProvider.register()
        assertTrue(PipRegistry.hasRegisteredFactory())

        val backend = WebPipBackend()
        PipRegistry.registerBackendFactory { backend }

        val caps = backend.capabilities
        assertTrue(caps.isSupported)
        assertTrue(caps.supportsInPlaceWindowPip)
        assertTrue(caps.supportsAutoEnterOnTabSwitch)
        assertTrue(caps.supportsCustomControlOverlay)

        val window = Window("Web Player", Size(1024, 600))
        val fragment = Fragment()
        val stack = VStack(gapPx = 8)
        val videoComp = Container("web-video").apply {
            htmlTag = "video"
        }
        stack.addChild(videoComp)
        fragment.addChild(stack)
        window.setContent(fragment)

        var paused = false
        val helper = FragmentPipHelper(fragment).apply {
            pipTarget = videoComp
            configProvider = { PipSessionConfig(title = "Web Stream") }
            onPauseRequested = { paused = true }
        }

        try {
            helper.bindWindow(window)
            assertTrue(helper.enterPip())
            assertTrue(helper.isInPipMode)
            assertNotNull(backend.activeFloatingRoot)
            assertFalse(stack.children.any { it === videoComp })
            assertEquals("true", stack.children[0].getAttribute("data-pip-placeholder"))

            val overlay = assertNotNull(backend.activeControlOverlay)
            assertEquals("Web Stream", overlay.titleText)
            overlay.closeButton.onClick?.invoke()

            assertTrue(paused)
            assertFalse(helper.isInPipMode)
            assertNull(backend.activeFloatingRoot)
            assertSame(stack, videoComp.parent)
            assertSame(videoComp, stack.children[0])
        } finally {
            helper.dispose()
            window.close()
        }
    }
}

package com.nexustools.genericui.ext.pip.dsl

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.ext.pip.FragmentPipHelper
import com.nexustools.genericui.ext.pip.FragmentPipRegistry
import com.nexustools.genericui.ext.pip.PipAction
import com.nexustools.genericui.ext.pip.PipActionIcon
import com.nexustools.genericui.ext.pip.PipBackend
import com.nexustools.genericui.ext.pip.PipCapabilities
import com.nexustools.genericui.ext.pip.PipContentType
import com.nexustools.genericui.ext.pip.PipRegistry
import com.nexustools.genericui.ext.pip.PipSessionConfig
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.window.IWindow
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PipDslTest {

    @BeforeTest
    fun setUp() {
        PipRegistry.resetForTest()
        FragmentPipRegistry.clearForTest()
    }

    @AfterTest
    fun tearDown() {
        FragmentPipRegistry.clearForTest()
        PipRegistry.resetForTest()
    }

    @Test
    fun installPipHelperConfiguresAndUpdatesExistingHelper() {
        PipRegistry.registerBackendFactory { TestDslPipBackend() }

        val fragment = Fragment()
        val window = Window("DSL Host", Size(960, 640))
        window.setContent(fragment)
        val targetView = Container("video-view")
        fragment.addChild(targetView)

        val installed = fragment.installPipHelper {
            pipTarget = targetView
            configProvider = {
                PipSessionConfig(title = "Dsl Video", preferredWidthPx = 480, preferredHeightPx = 270)
            }
            actionsProvider = {
                listOf(PipAction(id = "play", label = "Play", icon = PipActionIcon.PLAY))
            }
        }

        assertSame(installed, fragment.pipHelper)
        assertSame(installed, FragmentPipRegistry.findForFragment(fragment))
        assertSame(targetView, installed.resolveEffectiveTarget())
        assertEquals("Dsl Video", installed.resolveEffectiveConfig().title)

        // Second call updates in-place instead of replacing with a new instance.
        val updatedTarget = Container("canvas-view")
        val secondRef = fragment.installPipHelper {
            pipTarget = updatedTarget
            isActivelyPlayingProvider = { true }
        }
        assertSame(installed, secondRef)
        assertSame(updatedTarget, installed.resolveEffectiveTarget())
        assertTrue(installed.isEffectivelyPlaying())
        assertTrue(installed.enterPip())
        assertTrue(installed.isInPipMode)
        assertTrue(installed.exitPip())

        installed.dispose()
        assertNull(fragment.pipHelper)
        window.close()
    }

    private class TestDslPipBackend : PipBackend {
        override val capabilities: PipCapabilities = PipCapabilities(
            isSupported = true,
            supportedContentTypes = setOf(PipContentType.ANY_COMPONENT)
        )
        override var isInPipMode: Boolean = false
            private set
        private var helper: FragmentPipHelper? = null

        override fun supportsComponent(component: AbstractComponent): Boolean = true

        override fun attachHost(window: IWindow?, fragment: Fragment?, helper: FragmentPipHelper) {
            this.helper = helper
        }

        override fun detachHost() {
            helper = null
        }

        override fun syncSessionState() {}

        override fun enterPip(): Boolean {
            isInPipMode = true
            helper?.dispatchPipModeChangedFromBackend(true)
            return true
        }

        override fun exitPip(): Boolean {
            isInPipMode = false
            helper?.dispatchPipModeChangedFromBackend(false)
            return true
        }
    }
}

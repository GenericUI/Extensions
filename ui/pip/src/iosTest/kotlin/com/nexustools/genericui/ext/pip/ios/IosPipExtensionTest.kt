@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.pip.ios

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.ext.pip.FragmentPipHelper
import com.nexustools.genericui.ext.pip.FragmentPipRegistry
import com.nexustools.genericui.ext.pip.PipContentType
import com.nexustools.genericui.ext.pip.PipRegistry
import com.nexustools.genericui.ext.pip.PipSessionConfig
import com.nexustools.genericui.ios.IosBaseNativeImpl
import com.nexustools.genericui.ios.IosNativeFactory
import com.nexustools.genericui.ios.UikitEnvironment
import com.nexustools.genericui.ios.UikitViewHandle
import platform.AVFoundation.AVPlayerLayer
import platform.AVKit.AVPictureInPictureController
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CALayer
import platform.UIKit.UIView
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IosPipExtensionTest {

    private class TestVideoTargetComponent(
        private val customView: UIView
    ) : AbstractComponent("video-player") {
        override fun createNativeImpl(factory: NativeFactory): NativeImpl<*> =
            IosBaseNativeImpl(UikitViewHandle(customView), "video-player")
    }

    private class TestVideoFragment(
        target: AbstractComponent
    ) : Fragment() {
        val pipHelper = FragmentPipHelper(this).apply {
            pipTarget = target
        }

        init {
            addChild(target)
        }
    }

    private var savedFactory: NativeFactory? = null

    @BeforeTest
    fun setUp() {
        savedFactory = GenericUI.activeFactory
        Application.current?.dispose()
        UikitEnvironment.registeredKeyWindow = null
        UikitEnvironment.registeredRootViewController = null
        FragmentPipRegistry.clearForTest()
        PipRegistry.resetForTest()
    }

    @AfterTest
    fun tearDown() {
        Application.current?.dispose()
        UikitEnvironment.registeredKeyWindow = null
        UikitEnvironment.registeredRootViewController = null
        FragmentPipRegistry.clearForTest()
        PipRegistry.resetForTest()
        GenericUI.activeFactory = savedFactory
    }

    @Test
    fun iosPipProviderRegistersBackendAndReportsDeviceCapabilitiesSafely() {
        IosPipProvider.register()
        IosPipProvider.register()
        assertTrue(PipRegistry.hasRegisteredFactory())

        val backend = PipRegistry.createBackend()
        assertNotNull(backend)
        assertTrue(backend is IosPipBackend)

        val expectedSupported = runCatching {
            AVPictureInPictureController.isPictureInPictureSupported()
        }.getOrDefault(false)

        val caps = PipRegistry.capabilities()
        assertEquals(expectedSupported, caps.isSupported)
        assertFalse(caps.supportsInPlaceWindowPip)
        assertFalse(caps.supportsCustomControlOverlay)
        assertEquals(expectedSupported, caps.supportsAutoEnterOnBackground)
        if (expectedSupported) {
            assertEquals(setOf(PipContentType.VIDEO), caps.supportedContentTypes)
        } else {
            assertTrue(caps.supportedContentTypes.isEmpty())
        }
    }

    @Test
    fun layerFinderDiscoversNestedAvPlayerLayerAndRejectsNonVideoComponents() {
        IosPipProvider.register()
        val factory = IosNativeFactory()
        GenericUI.initialize(factory)

        val outerView = UIView(CGRectMake(0.0, 0.0, 320.0, 180.0))
        val innerView = UIView(CGRectMake(0.0, 0.0, 320.0, 180.0))
        val intermediateLayer = CALayer()
        val playerLayer = AVPlayerLayer()
        intermediateLayer.addSublayer(playerLayer)
        innerView.layer.addSublayer(intermediateLayer)
        outerView.addSubview(innerView)

        assertSame(playerLayer, IosPipLayerFinder.findPlayerLayer(outerView))
        assertSame(playerLayer, IosPipLayerFinder.findPlayerLayer(innerView.layer))

        val plainView = UIView(CGRectMake(0.0, 0.0, 100.0, 100.0))
        plainView.layer.addSublayer(CALayer())
        assertNull(IosPipLayerFinder.findPlayerLayer(plainView))

        val videoTarget = TestVideoTargetComponent(outerView)
        val nonVideoButton = Button("Not a Video")
        val rootContainer = Container().apply {
            addChild(videoTarget)
            addChild(nonVideoButton)
        }
        val window = Window(title = "iOS PiP Layer Test")
        window.setContent(rootContainer)
        window.show(factory)

        val backend = IosPipBackend()
        assertTrue(backend.supportsComponent(videoTarget))
        assertFalse(backend.supportsComponent(nonVideoButton))
        assertFalse(backend.supportsComponent(rootContainer))

        // Unsupported non-video target must fail enterPip gracefully
        val nonVideoFragment = TestVideoFragment(Button("Static"))
        val nonVideoBackend = IosPipBackend()
        nonVideoBackend.attachHost(window, nonVideoFragment, nonVideoFragment.pipHelper)
        assertFalse(nonVideoBackend.enterPip())
        nonVideoBackend.detachHost()

        window.close()
        window.dispose()
    }

    @Test
    fun controllerDelegateTransitionsModeAndDistinguishesRestoreFromClose() {
        IosPipProvider.register()
        val factory = IosNativeFactory()
        GenericUI.initialize(factory)

        val hostView = UIView(CGRectMake(0.0, 0.0, 320.0, 180.0))
        val playerLayer = AVPlayerLayer()
        hostView.layer.addSublayer(playerLayer)

        val videoTarget = TestVideoTargetComponent(hostView)
        val fragment = TestVideoFragment(videoTarget)
        var pauseRequests = 0
        val modeChanges = mutableListOf<Boolean>()
        fragment.pipHelper.configProvider = { PipSessionConfig(enabled = true, pauseOnCloseButton = true) }
        fragment.pipHelper.onPauseRequested = { pauseRequests++ }
        fragment.pipHelper.onPipModeChanged = { modeChanges.add(it) }

        val window = Window(title = "iOS PiP Delegate Test")
        window.setContent(fragment)
        window.show(factory)
        fragment.pipHelper.bindWindow(window)

        val supported = runCatching { AVPictureInPictureController.isPictureInPictureSupported() }.getOrDefault(false)
        val controller = if (supported) {
            runCatching { AVPictureInPictureController(playerLayer = playerLayer) }.getOrNull()
                ?.takeIf { (it as Any?) != null }
        } else {
            null
        }

        if (controller != null) {
            val backend = IosPipBackend()
            backend.attachHost(window, fragment, fragment.pipHelper)

            // Start -> Restore path should NOT trigger pauseOnCloseButton
            backend.controllerDelegate.pictureInPictureControllerWillStartPictureInPicture(controller)
            backend.controllerDelegate.pictureInPictureControllerDidStartPictureInPicture(controller)
            assertTrue(backend.isInPipMode)
            assertTrue(fragment.pipHelper.isInPipMode)

            var completionResult: Boolean? = null
            backend.controllerDelegate.pictureInPictureController(
                controller,
                restoreUserInterfaceForPictureInPictureStopWithCompletionHandler = { restored ->
                    completionResult = restored
                }
            )
            assertEquals(true, completionResult)
            backend.controllerDelegate.pictureInPictureControllerWillStopPictureInPicture(controller)
            backend.controllerDelegate.pictureInPictureControllerDidStopPictureInPicture(controller)
            assertFalse(backend.isInPipMode)
            assertFalse(fragment.pipHelper.isInPipMode)
            assertEquals(0, pauseRequests)

            // Start -> Close (no restoreUserInterface callback) SHOULD dispatch close -> onPauseRequested
            backend.controllerDelegate.pictureInPictureControllerWillStartPictureInPicture(controller)
            backend.controllerDelegate.pictureInPictureControllerDidStartPictureInPicture(controller)
            assertTrue(backend.isInPipMode)
            backend.controllerDelegate.pictureInPictureControllerWillStopPictureInPicture(controller)
            backend.controllerDelegate.pictureInPictureControllerDidStopPictureInPicture(controller)
            assertFalse(backend.isInPipMode)
            assertEquals(1, pauseRequests)
            assertEquals(listOf(true, false, true, false), modeChanges)
            backend.detachHost()
        }

        fragment.pipHelper.unbindWindow()
        fragment.pipHelper.dispose()
        window.close()
        window.dispose()
    }
}

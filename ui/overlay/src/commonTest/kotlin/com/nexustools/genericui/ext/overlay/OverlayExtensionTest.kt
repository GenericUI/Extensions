package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.animation.Animator
import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.composite.Composite
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.component.layout.LayerConstraint
import com.nexustools.genericui.component.layout.LayerSizingMode
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.enums.css.CssCursor
import com.nexustools.genericui.enums.css.CssPointerEvents
import com.nexustools.genericui.enums.window.TitleBarControlsPlacement
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.ext.overlay.res.R
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.sass.SassCompiler
import com.nexustools.genericui.window.TitleCapabilities
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OverlayExtensionTest {

    @BeforeTest
    fun setUp() {
        Animator.cancelAllAnimations()
        UiTimerScheduler.clearAllTimers()
        OverlayExtension.activeApplication = null
        OverlayExtension.defaultThemeColorHex = "#0d6efd"
    }

    @AfterTest
    fun tearDown() {
        Animator.cancelAllAnimations()
        UiTimerScheduler.clearAllTimers()
        OverlayExtension.activeApplication = null
        OverlayExtension.defaultThemeColorHex = "#0d6efd"
    }

    @Test
    fun testOverlayTimeFormatterMinutesAndForcedHoursAndUnknown() {
        assertEquals("0:05", OverlayTimeFormatter.formatPosition(5.0, 120.0))
        assertEquals("2:00", OverlayTimeFormatter.formatDuration(5.0, 120.0))
        assertEquals("0:05 / 2:00", OverlayTimeFormatter.formatPositionAndDuration(5.0, 120.0))
        assertEquals("--:--", OverlayTimeFormatter.formatDuration(5.0, 0.0))
        assertEquals("0:05:00", OverlayTimeFormatter.formatPosition(300.0, 3600.0))
        assertEquals("1:00:00", OverlayTimeFormatter.formatDuration(300.0, 3600.0))
    }

    @Test
    fun testOverlayExtensionPureOverlayAndHostBoxAreTransparentWithNoDefaultOverlay() {
        val target = Container("gui-target").apply {
            widthPx = 1280
            heightPx = 720
        }
        val host = OverlayHostBox(content = target)
        host.applyStyleSheet(OverlayExtension.defaultStyleSheet())
        assertNull(host.overlay)
        assertNull(host.activeOverlay)
        assertSame(target, host.targetComponent)
        assertSame(target, host.contentComponent)
        assertNull(host.style.backgroundColor, "OverlayHostBox must rely on style.scss rather than inline backgroundColor")
        assertEquals(RgbaColor.TRANSPARENT, host.computedStyle?.backgroundColor)
        assertEquals(RgbaColor.TRANSPARENT, host.overlayRoot.computedStyle?.backgroundColor)
        assertEquals(RgbaColor.TRANSPARENT, host.persistentOverlayRoot.computedStyle?.backgroundColor)
        assertEquals(1.0f, host.persistentOverlayRoot.computedStyle?.opacity ?: -1f, 0.001f)
        assertEquals(false, host.persistentOverlayRoot.computedStyle?.pointerEventsEnabled)
        assertEquals(10, host.overlayRoot.style.zIndex)
        assertEquals(20, host.persistentOverlayRoot.style.zIndex)
        assertEquals(CssPointerEvents.NONE, host.overlayRoot.style.pointerEvents)
        assertEquals(1280, host.overlayRoot.widthPx)
        assertEquals(720, host.overlayRoot.heightPx)
        assertEquals(1280, host.persistentOverlayRoot.widthPx)
        assertEquals(720, host.persistentOverlayRoot.heightPx)

        val overlay = OverlayExtension.createOverlay(tagName = "test-overlay", host = host)
        host.applyStyleSheet(OverlayExtension.defaultStyleSheet())
        assertTrue(overlay.hasClass("overlay-fading-root"))
        assertNull(overlay.style.backgroundColor, "Overlay must rely on style.scss rather than inline backgroundColor")
        assertEquals(RgbaColor.TRANSPARENT, overlay.computedStyle?.backgroundColor)
        assertEquals(10, host.overlayRoot.style.zIndex)
        assertNull(host.overlayRoot.style.pointerEvents)
        assertTrue(host.overlayRoot.pointerEventsEnabled)
        assertSame(overlay, host.overlay)
        assertSame(overlay, host.activeOverlay)
        assertEquals(1280, overlay.targetWidthPx)
        assertEquals(720, overlay.targetHeightPx)
        assertEquals(1280, overlay.widthPx)
        assertEquals(720, overlay.heightPx)

        host.detachOverlay()
        assertNull(host.overlay)
        assertNull(host.activeOverlay)
        host.dispose()
    }

    @Test
    fun testOverlayFillAndFloatingLayersAndRenderResizeSynchronization() {
        val overlay = Overlay()
        val fillChild = Container("gui-fill-child")
        val floatChild = Container("gui-float-child")

        val fillElement = overlay.addElement(
            component = fillChild,
            constraint = LayerConstraint.fill(zIndex = 2, passThroughPointerEvents = true)
        )
        var lastRenderFrameTs = -1L
        var lastResizeW = -1
        var lastResizeH = -1

        val floatElement = object : OverlayElement(
            elementId = "floating-bubble",
            component = floatChild,
            constraint = LayerConstraint.float(
                alignment = LayerAlignment.BOTTOM_START,
                offsetXPx = 24,
                offsetYPx = -36,
                zIndex = 5
            )
        ) {
            override fun onTargetRenderFrame(frameTimestampMs: Long, targetWidthPx: Int, targetHeightPx: Int) {
                lastRenderFrameTs = frameTimestampMs
            }

            override fun onTargetResize(targetWidthPx: Int, targetHeightPx: Int) {
                lastResizeW = targetWidthPx
                lastResizeH = targetHeightPx
            }
        }
        overlay.addElement(floatElement)

        assertEquals(2, overlay.elements.size)
        assertSame(fillElement, overlay.elements[0])
        assertSame(floatElement, overlay.getElement("floating-bubble"))
        assertEquals(LayerSizingMode.FILL, overlay.getLayerConstraint(fillChild)?.sizingMode)
        assertEquals(LayerSizingMode.FLOAT, overlay.getLayerConstraint(floatChild)?.sizingMode)
        assertEquals(LayerAlignment.BOTTOM_START, overlay.getLayerConstraint(floatChild)?.alignment)

        val host = OverlayHostBox(initialOverlay = overlay)
        host.syncWithTargetResize(1920, 1080)
        assertEquals(1920, overlay.targetWidthPx)
        assertEquals(1080, overlay.targetHeightPx)
        assertEquals(1920, lastResizeW)
        assertEquals(1080, lastResizeH)

        host.syncWithTargetRender(frameTimestampMs = 42_000L)
        assertEquals(42_000L, overlay.lastFrameTimestampMs)
        assertEquals(42_000L, lastRenderFrameTs)

        assertTrue(overlay.removeElement("floating-bubble"))
        assertEquals(1, overlay.elements.size)
        assertNull(overlay.getElement("floating-bubble"))
        host.dispose()
    }

    @Test
    fun testAutoHideAndKeyboardShortcutRouting() {
        val overlay = Overlay()
        var playPauseCount = 0
        var fullscreenCount = 0
        var muteCount = 0
        var lastSeekSec = -1.0

        overlay.updateState(
            OverlayState(
                title = "Demo Video",
                isPlaying = true,
                positionSec = 30.0,
                durationSec = 120.0,
                volumePercent = 80
            )
        )
        overlay.updateActions(
            OverlayActions(
                onPlayPause = { playPauseCount++ },
                onToggleFullscreen = { fullscreenCount++ },
                onMuteToggle = { muteCount++ },
                onSeekToSec = { lastSeekSec = it }
            )
        )

        assertTrue(overlay.inputRouter.handleKeyDown(KeyEvent(key = "Space"), overlay.state, overlay.actions))
        assertEquals(1, playPauseCount)
        assertTrue(overlay.inputRouter.handleKeyDown(KeyEvent(key = "f"), overlay.state, overlay.actions))
        assertEquals(1, fullscreenCount)
        assertTrue(overlay.inputRouter.handleKeyDown(KeyEvent(key = "m"), overlay.state, overlay.actions))
        assertEquals(1, muteCount)
        assertTrue(overlay.inputRouter.handleKeyDown(KeyEvent(key = "ArrowRight"), overlay.state, overlay.actions))
        assertEquals(35.0, lastSeekSec)

        overlay.autoHideController.setPinned("scrubbing", true)
        assertTrue(overlay.autoHideController.isPinned)
        overlay.autoHideController.hideOverlay()
        assertTrue(overlay.autoHideController.isOverlayVisible)

        overlay.autoHideController.setPinned("scrubbing", false)
        assertFalse(overlay.autoHideController.isPinned)
        overlay.autoHideController.hideOverlay(instant = true)
        assertFalse(overlay.autoHideController.isOverlayVisible)

        overlay.dispose()
    }

    @Test
    fun testAutoHideTogglesHiddenClassAndResolvesCssKeyframesCleanly() {
        val sheet = OverlayExtension.defaultStyleSheet()
        assertTrue(sheet.keyframes.containsKey("overlayFadeIn"))
        assertTrue(sheet.keyframes.containsKey("overlayFadeOut"))
        val rawScss = Resources.getRawText(R.style.style)
        assertTrue(!rawScss.isNullOrBlank())
        assertFalse(rawScss.contains("\$accent"), "style.scss must stay 100% theme-independent")
        assertFalse(rawScss.contains("\$primary"), "style.scss must stay 100% theme-independent")
        assertEquals(sheet.rules.size, SassCompiler.compileToStyleSheet(rawScss).rules.size)

        val hudLayer = Container("hud-layer").apply {
            addClass("overlay-fading-root")
        }
        hudLayer.applyStyleSheet(sheet)
        val controller = AutoHideController(
            spec = AutoHideSpec(delayMs = 3000, fadeInMs = 200, fadeOutMs = 280, hideCursorWhenOverlayHidden = true),
            targetComponent = hudLayer
        )

        assertTrue(hudLayer.isVisible)
        assertFalse(hudLayer.hasClass(AutoHideController.HIDDEN_CLASS))
        assertEquals(1.0f, hudLayer.computedStyle?.opacity ?: -1f, 0.001f)
        assertEquals(true, hudLayer.computedStyle?.pointerEventsEnabled)

        // Hide toggles .hidden CSS class cleanly while keeping target mounted with CSS opacity: 0 & cursor: none
        controller.hideOverlay(instant = false)
        assertTrue(hudLayer.isVisible)
        assertFalse(controller.isOverlayVisible)
        assertTrue(hudLayer.hasClass(AutoHideController.HIDDEN_CLASS))
        assertFalse(hudLayer.hasClass(AutoHideController.INSTANT_FADE_CLASS))
        assertEquals("0.0", hudLayer.getAttribute("data-hud-opacity"))
        assertEquals(0.0f, hudLayer.computedStyle?.opacity ?: -1f, 0.001f)
        assertEquals(false, hudLayer.computedStyle?.pointerEventsEnabled)
        assertEquals("none", hudLayer.computedStyle?.cursor)

        // Show removes .hidden CSS class cleanly and restores pointer-events & opacity: 1
        controller.showOverlay(scheduleHide = false)
        assertTrue(controller.isOverlayVisible)
        assertTrue(hudLayer.isVisible)
        assertFalse(hudLayer.hasClass(AutoHideController.HIDDEN_CLASS))
        assertEquals("1.0", hudLayer.getAttribute("data-hud-opacity"))
        assertEquals(1.0f, hudLayer.computedStyle?.opacity ?: -1f, 0.001f)
        assertEquals(true, hudLayer.computedStyle?.pointerEventsEnabled)

        // Instant hide/show toggles .overlay-instant-fade and .hidden cleanly
        controller.hideOverlay(instant = true)
        assertFalse(controller.isOverlayVisible)
        assertTrue(hudLayer.hasClass(AutoHideController.HIDDEN_CLASS))
        assertTrue(hudLayer.hasClass(AutoHideController.INSTANT_FADE_CLASS))
        controller.showOverlay(scheduleHide = false)
        assertTrue(controller.isOverlayVisible)
        assertFalse(hudLayer.hasClass(AutoHideController.HIDDEN_CLASS))

        // Zero-duration AutoHideSpec marks instant fade
        val zeroFadeHud = Container("zero-fade").apply {
            addClass("overlay-fading-root")
        }
        zeroFadeHud.applyStyleSheet(sheet)
        val zeroFadeController = AutoHideController(
            spec = AutoHideSpec(delayMs = 1000, fadeInMs = 0, fadeOutMs = 0),
            targetComponent = zeroFadeHud
        )
        zeroFadeController.hideOverlay(instant = false)
        assertFalse(zeroFadeController.isOverlayVisible)
        assertTrue(zeroFadeHud.hasClass(AutoHideController.HIDDEN_CLASS))
        assertTrue(zeroFadeHud.hasClass(AutoHideController.INSTANT_FADE_CLASS))
        zeroFadeController.showOverlay(scheduleHide = false)
        assertTrue(zeroFadeHud.isVisible)
        assertFalse(zeroFadeHud.hasClass(AutoHideController.HIDDEN_CLASS))
        assertTrue(zeroFadeHud.hasClass(AutoHideController.INSTANT_FADE_CLASS))

        zeroFadeController.dispose()
        controller.dispose()
    }

    @Test
    fun testFullscreenEntryAndExitClearNativeTitleBarHoverPinSoOverlayHides() {
        val window = object : Window(title = "Fullscreen Pin Stage") {
            override val titleCapabilities: TitleCapabilities = TitleCapabilities(
                isTitleBarRequired = true,
                defaultControlsPlacement = TitleBarControlsPlacement.LEADING,
                prefersBlurredTranslucentOverlayTitleBar = true,
                usesNativeOverlayTitleBarControls = true
            )
        }
        val overlay = Overlay().apply {
            windowTitleBarEnabled = true
        }
        val host = OverlayHostBox(
            content = Container("stage").apply {
                widthPx = 1280
                heightPx = 720
            },
            initialOverlay = overlay
        )
        window.setContent(host)

        val bar = overlay.windowTitleBar
        val autoHide = overlay.autoHideController
        assertEquals("true", bar.getAttribute("data-titlebar-enabled"))
        assertEquals("true", bar.getAttribute("data-native-titlebar-controls"))
        assertEquals("true", bar.getAttribute("data-titlebar-active"))

        // Pin via native titlebar hover, then enter & exit fullscreen.
        bar.setNativeTitleBarHoverActive(true)
        assertTrue(autoHide.activePinReasons.contains("native-titlebar-hover"))
        autoHide.hideOverlay(instant = true)
        assertTrue(autoHide.isOverlayVisible, "Overlay stays visible while pinned")

        window.setFullscreen(true)
        assertEquals("false", bar.getAttribute("data-titlebar-active"))
        assertEquals("true", bar.getAttribute("data-titlebar-enabled"))
        assertFalse(autoHide.activePinReasons.contains("native-titlebar-hover"))

        // Even if setPinned was re-asserted out-of-band before syncFromWindow completes on exit, unpins cleanly.
        autoHide.setPinned("native-titlebar-hover", true)
        window.setFullscreen(false)
        assertEquals("true", bar.getAttribute("data-titlebar-active"))
        assertFalse(autoHide.activePinReasons.contains("native-titlebar-hover"))
        assertFalse(autoHide.isPinned)

        autoHide.hideOverlay(instant = true)
        assertFalse(autoHide.isOverlayVisible, "Overlay must hide once fullscreen exit clears hover pin")

        window.dispose()
        host.dispose()
    }

    @Test
    fun testUnpinningInactiveReasonAndClearingEmptyPinsDoNotResetAutoHideTimer() {
        val hud = Container("hud-layer").apply {
            addClass("overlay-fading-root")
        }
        val controller = AutoHideController(
            spec = AutoHideSpec(delayMs = 3000, fadeInMs = 0, fadeOutMs = 0),
            targetComponent = hud
        )

        controller.showOverlay(scheduleHide = true)
        assertTrue(controller.isOverlayVisible)
        assertFalse(hud.hasClass(AutoHideController.HIDDEN_CLASS))

        // Advance 1000ms, then unpin an inactive reason 3 times -> must NOT reset 3000ms countdown
        UiTimerScheduler.advanceTimeBy(1000)
        repeat(3) {
            controller.setPinned("native-titlebar-hover", false)
        }
        assertTrue(controller.isOverlayVisible)
        assertFalse(hud.hasClass(AutoHideController.HIDDEN_CLASS))

        UiTimerScheduler.advanceTimeBy(1000)
        assertTrue(controller.isOverlayVisible, "Overlay should still be visible at 2000ms")

        UiTimerScheduler.advanceTimeBy(1000)
        assertFalse(controller.isOverlayVisible, "Overlay must hide at 3000ms total")
        assertTrue(hud.hasClass(AutoHideController.HIDDEN_CLASS))

        // Clearing pins when already empty must also NOT reset an active countdown
        controller.showOverlay(scheduleHide = true)
        assertTrue(controller.isOverlayVisible)
        UiTimerScheduler.advanceTimeBy(1500)
        controller.clearAllPins()
        assertTrue(controller.isOverlayVisible)
        UiTimerScheduler.advanceTimeBy(1500)
        assertFalse(controller.isOverlayVisible, "clearAllPins() when empty must not reset 3000ms timer")
        assertTrue(hud.hasClass(AutoHideController.HIDDEN_CLASS))

        // Unpinning an active pin DOES schedule the auto-hide countdown cleanly
        controller.setPinned("native-titlebar-hover", true)
        assertTrue(controller.isOverlayVisible)
        UiTimerScheduler.advanceTimeBy(3000)
        assertTrue(controller.isOverlayVisible, "Active pin keeps overlay visible")

        controller.setPinned("native-titlebar-hover", false)
        UiTimerScheduler.advanceTimeBy(2999)
        assertTrue(controller.isOverlayVisible)
        UiTimerScheduler.advanceTimeBy(1)
        assertFalse(controller.isOverlayVisible)
        assertTrue(hud.hasClass(AutoHideController.HIDDEN_CLASS))

        controller.dispose()
    }

    @Test
    fun testFullscreenPeriodicStateUpdatesAllowOverlayAutoHideAtDelayMs() {
        val window = object : Window(title = "Fullscreen Auto-Hide Stage") {
            override val titleCapabilities: TitleCapabilities = TitleCapabilities(
                isTitleBarRequired = true,
                defaultControlsPlacement = TitleBarControlsPlacement.LEADING,
                prefersBlurredTranslucentOverlayTitleBar = true,
                usesNativeOverlayTitleBarControls = true
            )
        }
        val overlay = Overlay(
            autoHideSpec = AutoHideSpec(delayMs = 3000, fadeInMs = 0, fadeOutMs = 0)
        ).apply {
            windowTitleBarEnabled = true
        }
        val host = OverlayHostBox(
            content = Container("stage").apply {
                widthPx = 1280
                heightPx = 720
            },
            initialOverlay = overlay
        )
        window.setContent(host)
        window.setFullscreen(true)

        val autoHide = overlay.autoHideController
        autoHide.showOverlay(scheduleHide = true)
        assertTrue(autoHide.isOverlayVisible)
        assertFalse(overlay.hasClass(AutoHideController.HIDDEN_CLASS))

        // Periodic state updates every 1000ms trigger syncWindowTitleBar() -> syncFromWindow(),
        // which must NOT reset the 3000ms auto-hide countdown in fullscreen mode.
        host.updateState { it.copy(positionSec = it.positionSec + 1.0) }
        UiTimerScheduler.advanceTimeBy(1000)
        assertTrue(autoHide.isOverlayVisible)
        assertFalse(overlay.hasClass(AutoHideController.HIDDEN_CLASS))

        host.updateState { it.copy(positionSec = it.positionSec + 1.0) }
        UiTimerScheduler.advanceTimeBy(1000)
        assertTrue(autoHide.isOverlayVisible)
        assertFalse(overlay.hasClass(AutoHideController.HIDDEN_CLASS))

        host.updateState { it.copy(positionSec = it.positionSec + 1.0) }
        UiTimerScheduler.advanceTimeBy(1000)
        assertFalse(autoHide.isOverlayVisible, "Overlay must auto-hide at 3000ms despite periodic updateState calls")
        assertTrue(overlay.hasClass(AutoHideController.HIDDEN_CLASS))

        window.dispose()
        host.dispose()
    }

    @Test
    fun testIsolateInteractiveComponentRecursesIntoCompositeChildrenOnly() {
        class TestComposite(vararg children: AbstractComponent) : Composite(classPrefix = "test-composite") {
            init {
                for (child in children) {
                    addCompositeChild(child)
                }
            }
        }

        var playPauseCount = 0
        var dragCount = 0
        val overlay = Overlay().apply {
            windowDragEnabled = true
            onWindowDragMove = { dragCount++ }
            updateActions(OverlayActions(onPlayPause = { playPauseCount++ }))
        }

        var leafClickCount = 0
        val leafButton = Button("Leaf").apply {
            onClick = { leafClickCount++ }
        }
        var nestedClickCount = 0
        val nestedButton = Button("Nested").apply {
            onClick = { nestedClickCount++ }
        }
        val innerComposite = TestComposite(nestedButton)
        val outerComposite = TestComposite(leafButton, innerComposite)

        overlay.inputRouter.isolateInteractiveComponent(outerComposite)

        assertEquals("true", outerComposite.getAttribute("data-overlay-input-isolated"))
        assertEquals("true", leafButton.getAttribute("data-overlay-input-isolated"))
        assertEquals("true", innerComposite.getAttribute("data-overlay-input-isolated"))
        assertEquals("true", nestedButton.getAttribute("data-overlay-input-isolated"))

        // Non-Composite AbstractContainer children must NOT be recursively isolated.
        val plainContainer = Container("plain-container")
        val plainChild = Button("PlainChild")
        plainContainer.addChild(plainChild)
        overlay.inputRouter.isolateInteractiveComponent(plainContainer)

        assertEquals("true", plainContainer.getAttribute("data-overlay-input-isolated"))
        assertNull(plainChild.getAttribute("data-overlay-input-isolated"))

        // Interacting with Composite's internal children must not trigger stage drag or play/pause.
        leafButton.onPointerDown?.invoke(PointerEvent(x = 10f, y = 10f))
        overlay.onPointerDown?.invoke(PointerEvent(x = 10f, y = 10f))
        leafButton.onPointerMove?.invoke(PointerEvent(x = 40f, y = 10f))
        overlay.onPointerMove?.invoke(PointerEvent(x = 40f, y = 10f))
        leafButton.onPointerUp?.invoke(PointerEvent(x = 40f, y = 10f))
        leafButton.onClick?.invoke()
        overlay.onClick?.invoke()
        UiTimerScheduler.advanceTimeBy(OverlayInputRouter.DEFAULT_CLICK_DEBOUNCE_MS + 10)

        assertEquals(1, leafClickCount)
        assertEquals(0, dragCount)
        assertEquals(0, playPauseCount)

        nestedButton.onPointerDown?.invoke(PointerEvent(x = 12f, y = 12f))
        overlay.onPointerDown?.invoke(PointerEvent(x = 12f, y = 12f))
        nestedButton.onPointerMove?.invoke(PointerEvent(x = 50f, y = 12f))
        overlay.onPointerMove?.invoke(PointerEvent(x = 50f, y = 12f))
        nestedButton.onPointerUp?.invoke(PointerEvent(x = 50f, y = 12f))
        nestedButton.onClick?.invoke()
        overlay.onClick?.invoke()
        UiTimerScheduler.advanceTimeBy(OverlayInputRouter.DEFAULT_CLICK_DEBOUNCE_MS + 10)

        assertEquals(1, nestedClickCount)
        assertEquals(0, dragCount)
        assertEquals(0, playPauseCount)

        plainContainer.dispose()
        outerComposite.dispose()
        overlay.dispose()
    }
}

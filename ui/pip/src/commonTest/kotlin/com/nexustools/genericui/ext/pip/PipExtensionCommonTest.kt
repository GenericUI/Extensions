package com.nexustools.genericui.ext.pip

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.component.layout.LayerConstraint
import com.nexustools.genericui.component.layout.LayeredContainer
import com.nexustools.genericui.component.layout.StackConstraint
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.UiTimerScheduler
import com.nexustools.genericui.enums.css.CssPointerEvents
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.window.IWindow
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PipExtensionCommonTest {

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
    fun componentReparentSlotPreservesVStackIndexAndStackConstraint() {
        val parent = VStack(gapPx = 8)
        val first = Button(text = "First")
        val target = Container("video-slot")
        val last = Button(text = "Last")
        val targetConstraint = StackConstraint(weight = 2f, flexShrink = 0f, fillCrossAxis = true)

        parent.addChild(first)
        parent.addChild(target, targetConstraint)
        parent.addChild(last)

        val pipHost = LayeredContainer()
        val slot = ComponentReparentSlot()

        assertTrue(slot.moveInto(target, pipHost))
        assertTrue(slot.isMoved)
        assertSame(pipHost, target.parent)
        assertEquals(3, parent.children.size)
        assertSame(first, parent.children[0])
        assertSame(last, parent.children[2])
        assertEquals("true", parent.children[1].getAttribute("data-pip-placeholder"))
        assertEquals(targetConstraint, parent.getChildConstraint(parent.children[1]))

        // Duplicate move into the same host is a safe idempotent no-op.
        assertTrue(slot.moveInto(target, pipHost))
        assertSame(pipHost, target.parent)

        assertTrue(slot.restore())
        assertFalse(slot.isMoved)
        assertSame(parent, target.parent)
        assertEquals(3, parent.children.size)
        assertSame(first, parent.children[0])
        assertSame(target, parent.children[1])
        assertSame(last, parent.children[2])
        assertEquals(targetConstraint, parent.getChildConstraint(target))
        assertFalse(pipHost.children.any { it === target })
    }

    @Test
    fun componentReparentSlotPreservesLayeredContainerAndRejectsCycles() {
        val layered = LayeredContainer()
        val childBox = Container("child-box")
        val nestedHost = LayeredContainer()
        childBox.addChild(nestedHost)

        val constraint = LayerConstraint.float(
            alignment = LayerAlignment.BOTTOM_END,
            offsetXPx = 12,
            offsetYPx = 16,
            zIndex = 5
        )
        layered.addLayer(childBox, constraint)

        val slot = ComponentReparentSlot()
        // Cannot move an ancestor into its own descendant.
        assertFalse(slot.moveInto(childBox, nestedHost))
        assertSame(layered, childBox.parent)

        val externalHost = Container()
        assertTrue(slot.moveInto(childBox, externalHost))
        assertSame(externalHost, childBox.parent)

        assertTrue(slot.restore())
        assertSame(layered, childBox.parent)
        assertEquals(constraint, layered.getLayerConstraint(childBox))
    }

    @Test
    fun targetBoundsTrackerIgnoresMinimizedOffscreenSentinelCoordinates() {
        val window = Window("Owner", Size(1280, 720)).apply {
            windowBounds = Rect(100, 50, 1280, 720)
        }
        val root = Container()
        val videoSurface = Container("video-target").apply {
            bounds = Rect(40, 60, 640, 360)
        }
        root.addChild(videoSurface)
        window.setContent(root)

        val tracker = PipTargetBoundsTracker()
        tracker.configure(window, videoSurface, 16, 9)

        assertEquals(Rect(140, 110, 640, 360), tracker.lastVisibleTargetScreenRect)

        // Simulate Win32 iconic minimize (-32000, -32000). Cached visible rect must be preserved.
        window.setWindowState(WindowState.MINIMIZED)
        window.windowBounds = Rect(-32000, -32000, 160, 28)
        val retained = tracker.captureIfVisible()
        assertEquals(Rect(140, 110, 640, 360), retained)

        // Zero-size target falls back cleanly using configured aspect ratio when window is normal.
        val zeroTarget = Container("zero-target").apply {
            bounds = Rect(20, 30, 0, 0)
        }
        root.addChild(zeroTarget)
        window.restore()
        window.windowBounds = Rect(100, 50, 1000, 600)
        tracker.configure(window, zeroTarget, 4, 3)
        val fallbackRect = assertNotNull(tracker.lastVisibleTargetScreenRect)
        assertTrue(fallbackRect.width >= 160)
        assertTrue(fallbackRect.height >= 90)
        assertEquals(120, fallbackRect.x)
        assertEquals(80, fallbackRect.y)

        window.close()
    }

    @Test
    fun windowTransitionAnimatorInterpolatesAndSupportsCancellation() {
        val animator = PipWindowTransitionAnimator()
        val steps = mutableListOf<Rect>()
        var completedCount = 0

        animator.animateBounds(
            fromRect = Rect(0, 0, 800, 450),
            toRect = Rect(1000, 600, 400, 225),
            durationMs = 160,
            immediate = false,
            onStep = { steps.add(it) },
            onComplete = { completedCount++ }
        )

        assertTrue(animator.isAnimating)
        assertEquals(Rect(0, 0, 800, 450), steps.first())

        UiTimerScheduler.advanceTimeBy(48)
        assertTrue(steps.size >= 3)
        val mid = steps.last()
        assertTrue(mid.x in 1..999)
        assertTrue(mid.width in 401..799)
        assertEquals(0, completedCount)

        UiTimerScheduler.advanceTimeBy(160)
        assertFalse(animator.isAnimating)
        assertEquals(1, completedCount)
        assertEquals(Rect(1000, 600, 400, 225), steps.last())

        // Verify cancellation suppresses completion callback.
        var cancelledComplete = 0
        animator.animateBounds(
            fromRect = Rect(100, 100, 400, 225),
            toRect = Rect(500, 500, 200, 112),
            durationMs = 180,
            immediate = false,
            onStep = {},
            onComplete = { cancelledComplete++ }
        )
        assertTrue(animator.isAnimating)
        animator.cancel()
        assertFalse(animator.isAnimating)
        UiTimerScheduler.advanceTimeBy(300)
        assertEquals(0, cancelledComplete)

        animator.dispose()
    }

    @Test
    fun pipControlOverlayAutoHidesAfter2000msAndDispatchesActions() {
        var restoreCount = 0
        var closeCount = 0
        var dragCount = 0
        var playClicks = 0
        var disabledClicks = 0

        val overlay = PipControlOverlay(
            onRestoreRequested = { restoreCount++ },
            onCloseRequested = { closeCount++ },
            onDragMoveRequested = { dragCount++ },
            actions = listOf(
                PipAction(id = "play", label = "Play", icon = PipActionIcon.PLAY) { playClicks++ },
                PipAction(id = "next", label = "Next", icon = PipActionIcon.NEXT, enabled = false) { disabledClicks++ }
            )
        )
        overlay.setOverlayTitle("Big Buck Bunny")

        assertTrue(overlay.isChromeVisible)
        assertEquals(PipControlOverlay.AUTO_HIDE_DELAY_MS, overlay.autoHideController.delayMs)
        assertEquals("Big Buck Bunny", overlay.titleLabel.text)
        assertTrue(overlay.titleLabel.isVisible)
        assertEquals(2, overlay.actionButtons.size)

        // Action clicks work and wake HUD.
        overlay.actionButtons[0].onClick?.invoke()
        overlay.actionButtons[1].onClick?.invoke()
        assertEquals(1, playClicks)
        assertEquals(0, disabledClicks)

        // Advance 2000ms -> HUD chrome fades out while root stays hit-testable for background drag.
        UiTimerScheduler.advanceTimeBy(2000)
        assertFalse(overlay.isChromeVisible)
        assertEquals(1.0f, overlay.opacity)
        assertEquals(CssPointerEvents.AUTO, overlay.style.pointerEvents)

        // Background drag wakes HUD and invokes drag callback.
        overlay.dragSurface.onPointerDown?.invoke(
            com.nexustools.genericui.event.PointerEvent(10f, 10f)
        )
        assertEquals(1, dragCount)
        assertTrue(overlay.isChromeVisible)

        overlay.restoreButton.onClick?.invoke()
        overlay.closeButton.onClick?.invoke()
        assertEquals(1, restoreCount)
        assertEquals(1, closeCount)

        overlay.dispose()
    }

    @Test
    fun fragmentPipHelperDelegationAndLifecycleCallbacks() {
        val backend = FakePipBackend()
        PipRegistry.registerBackendFactory { backend }

        val hostFragment = RecordingPipFragment()
        val childFragment = RecordingPipFragment()
        val grandchildCycleFragment = RecordingPipFragment()

        val videoTarget = Container("video-node").apply {
            htmlTag = "video"
        }

        val hostHelper = FragmentPipHelper(hostFragment)
        val childHelper = FragmentPipHelper(childFragment).apply {
            pipTarget = videoTarget
            configProvider = {
                PipSessionConfig(
                    title = "Child Episode",
                    aspectRatioNumerator = 21,
                    aspectRatioDenominator = 9,
                    pauseOnCloseButton = true
                )
            }
            actionsProvider = {
                listOf(PipAction(id = "pause", label = "Pause", icon = PipActionIcon.PAUSE))
            }
        }
        val cycleHelper = FragmentPipHelper(grandchildCycleFragment)

        var hostPauseCalls = 0
        var childPauseCalls = 0
        hostHelper.onPauseRequested = { hostPauseCalls++ }
        childHelper.onPauseRequested = { childPauseCalls++ }

        // Wire cyclic delegation: host -> child -> cycle -> child (cycle guard must not hang).
        hostHelper.childDelegateResolver = { childHelper }
        childHelper.childDelegateResolver = { cycleHelper }
        cycleHelper.childDelegateResolver = { childHelper }

        assertSame(cycleHelper, hostHelper.effectiveHelper())
        // Test clean parent -> child delegation cleanly without cycle:
        childHelper.childDelegateResolver = null
        assertSame(childHelper, hostHelper.effectiveHelper())
        assertSame(videoTarget, hostHelper.resolveEffectiveTarget())
        assertEquals(21 to 9, hostHelper.resolveEffectiveAspectRatio())
        assertEquals("Child Episode", hostHelper.resolveEffectiveConfig().title)
        assertEquals(1, hostHelper.resolveEffectiveActions().size)

        val window = Window("Main", Size(800, 600))
        hostHelper.bindWindow(window)
        assertSame(hostHelper, FragmentPipRegistry.findForFragment(hostFragment))
        assertSame(hostHelper, FragmentPipRegistry.findForWindow(window))

        assertTrue(hostHelper.enterPip())
        assertTrue(hostHelper.isEnteringPip)
        backend.simulateEntered()
        assertFalse(hostHelper.isEnteringPip)
        assertTrue(hostHelper.isInPipMode)
        assertTrue(childHelper.isInPipMode)
        assertEquals(listOf(true), hostFragment.pipModeEvents)
        assertEquals(listOf(true), childFragment.pipModeEvents)

        hostHelper.dispatchCloseRequestedFromBackend()
        assertEquals(1, childPauseCalls)
        assertEquals(0, hostPauseCalls)

        assertTrue(hostHelper.exitPip())
        assertFalse(hostHelper.isInPipMode)
        assertFalse(childHelper.isInPipMode)
        assertEquals(listOf(true, false), hostFragment.pipModeEvents)
        assertEquals(listOf(true, false), childFragment.pipModeEvents)

        hostHelper.dispose()
        childHelper.dispose()
        cycleHelper.dispose()
        assertNull(FragmentPipRegistry.findForFragment(hostFragment))
        assertNull(FragmentPipRegistry.findForWindow(window))
        window.close()
    }

    @Test
    fun fragmentPipHelperRejectsUnsupportedOrDisabledEnter() {
        val videoOnlyBackend = FakePipBackend(
            capabilities = PipCapabilities(
                isSupported = true,
                supportedContentTypes = setOf(PipContentType.VIDEO)
            )
        )
        PipRegistry.registerBackendFactory { videoOnlyBackend }

        val fragment = RecordingPipFragment()
        val helper = FragmentPipHelper(fragment)
        val plainButton = Button("Not Video")

        // No target yet -> false
        assertFalse(helper.enterPip())

        // Plain button rejected by video-only backend -> false
        helper.pipTarget = plainButton
        assertFalse(helper.isTargetSupported())
        assertFalse(helper.enterPip())

        // Video target accepted unless disabled
        val videoTarget = Container().apply { htmlTag = "video" }
        helper.pipTarget = videoTarget
        assertTrue(helper.isTargetSupported())
        helper.isEnabledProvider = { false }
        assertFalse(helper.enterPip())

        helper.dispose()
    }

    private class RecordingPipFragment : Fragment() {
        val pipModeEvents = mutableListOf<Boolean>()
        override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
            pipModeEvents.add(isInPictureInPictureMode)
        }
    }

    private class FakePipBackend(
        override val capabilities: PipCapabilities = PipCapabilities(
            isSupported = true,
            supportedContentTypes = setOf(
                PipContentType.VIDEO,
                PipContentType.CANVAS,
                PipContentType.ANY_COMPONENT
            ),
            supportsCustomControlOverlay = true,
            supportsAutoEnterOnMinimize = true
        )
    ) : PipBackend {
        override var isInPipMode: Boolean = false
            private set

        private var attachedHelper: FragmentPipHelper? = null

        override fun supportsComponent(component: AbstractComponent): Boolean =
            PipComponentClassifier.isSupportedForCapabilities(component, capabilities)

        override fun attachHost(window: IWindow?, fragment: Fragment?, helper: FragmentPipHelper) {
            attachedHelper = helper
        }

        override fun detachHost() {
            attachedHelper = null
        }

        override fun syncSessionState() {}

        override fun enterPip(): Boolean = attachedHelper != null

        fun simulateEntered() {
            isInPipMode = true
            attachedHelper?.dispatchPipModeChangedFromBackend(true)
        }

        override fun exitPip(): Boolean {
            if (!isInPipMode) return false
            isInPipMode = false
            attachedHelper?.dispatchPipModeChangedFromBackend(false)
            return true
        }
    }
}

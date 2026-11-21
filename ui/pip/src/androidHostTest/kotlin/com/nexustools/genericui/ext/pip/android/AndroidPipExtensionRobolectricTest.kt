package com.nexustools.genericui.ext.pip.android

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Base64
import android.util.Rational
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.nexustools.genericui.android.AndroidActivityRegistry
import com.nexustools.genericui.android.AndroidBaseNativeImpl
import com.nexustools.genericui.android.AndroidFileBrowserDialogImpl
import com.nexustools.genericui.android.AndroidNativeFactory
import com.nexustools.genericui.android.AndroidPreferenceStoreImpl
import com.nexustools.genericui.android.AndroidViewHandle
import com.nexustools.genericui.android.GenericUiFragmentActivity
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.navigation.BottomNavigationBar
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.ext.pip.FragmentPipHelper
import com.nexustools.genericui.ext.pip.FragmentPipRegistry
import com.nexustools.genericui.ext.pip.PipAction
import com.nexustools.genericui.ext.pip.PipActionIcon
import com.nexustools.genericui.ext.pip.PipContentType
import com.nexustools.genericui.ext.pip.PipRegistry
import com.nexustools.genericui.ext.pip.PipSessionConfig
import com.nexustools.genericui.window.TitleBar
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidPipExtensionRobolectricTest {

    private class PlayerScreenFragment : Fragment() {
        val headerText = Text("Now Playing Header")
        val sidebarText = Text("Episode Queue Sidebar")
        val playerTarget = Container().apply {
            widthPx = 320
            heightPx = 180
        }
        val bodyRow = HStack().apply {
            addChild(sidebarText)
            addChild(playerTarget)
        }
        val rootStack = VStack().apply {
            addChild(headerText)
            addChild(bodyRow)
        }
        val bottomNav = BottomNavigationBar().apply {
            addItem("player", "Player")
            addItem("queue", "Queue")
        }

        val pipHelper = FragmentPipHelper(this).apply {
            pipTarget = playerTarget
        }

        var playingState: Boolean = true
        var pipEnabledState: Boolean = true
        var autoEnterBgState: Boolean = true
        var pauseActionCount: Int = 0
        var nextActionCount: Int = 0
        var closePauseRequestedCount: Int = 0
        val modeHistory = mutableListOf<Boolean>()

        init {
            pageTitle = "Pip Player"
            setTitleBar(TitleBar(showWindowTitle = true))
            setBottomNavigationBar(bottomNav)
            addChild(rootStack)

            pipHelper.isEnabledProvider = { pipEnabledState }
            pipHelper.isActivelyPlayingProvider = { playingState }
            pipHelper.configProvider = {
                PipSessionConfig(
                    enabled = pipEnabledState,
                    aspectRatioNumerator = 16,
                    aspectRatioDenominator = 9,
                    autoEnterOnBackground = autoEnterBgState,
                    pauseOnCloseButton = true
                )
            }
            pipHelper.aspectRatioProvider = { 16 to 9 }
            pipHelper.actionsProvider = {
                listOf(
                    PipAction(
                        id = "play_pause",
                        label = if (playingState) "Pause" else "Play",
                        icon = if (playingState) PipActionIcon.PAUSE else PipActionIcon.PLAY,
                        onTrigger = {
                            pauseActionCount++
                            playingState = !playingState
                        }
                    ),
                    PipAction(
                        id = "next",
                        label = "Next",
                        icon = PipActionIcon.NEXT,
                        onTrigger = {
                            nextActionCount++
                        }
                    )
                )
            }
            pipHelper.onPauseRequested = {
                closePauseRequestedCount++
                playingState = false
            }
            pipHelper.onPipModeChanged = { inPip ->
                modeHistory.add(inPip)
            }
        }

        override fun onCreate(savedState: FragmentBundle?) {
            super.onCreate(savedState)
            FragmentPipRegistry.register(this, pipHelper)
        }

        override fun onStart() {
            super.onStart()
            pipHelper.bindWindow(hostingWindow)
        }

        override fun onDestroy() {
            pipHelper.unbindWindow()
            FragmentPipRegistry.unregister(this, pipHelper)
            pipHelper.dispose()
            super.onDestroy()
        }
    }

    private class PipHostTestActivity : GenericUiFragmentActivity() {
        val playerFragment = PlayerScreenFragment()
        val pipDelegate = AndroidPipActivityDelegate(this)

        override fun createFragment(): Fragment = playerFragment

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            pipDelegate.onActivityCreated(savedInstanceState)
        }

        public override fun onUserLeaveHint() {
            super.onUserLeaveHint()
            pipDelegate.onUserLeaveHint()
        }

        override fun onPictureInPictureModeChanged(
            isInPictureInPictureMode: Boolean,
            newConfig: Configuration
        ) {
            super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
            pipDelegate.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        }

        override fun onDestroy() {
            pipDelegate.onActivityDestroyed()
            super.onDestroy()
        }
    }

    private lateinit var context: Context

    @BeforeTest
    fun setUp() {
        Application.current?.dispose()
        GenericUI.activeFactory = null
        context = RuntimeEnvironment.getApplication()
        AndroidViewHandle.activeContext = context
        AndroidPreferenceStoreImpl.applicationContext = context
        AndroidFileBrowserDialogImpl.activeHostActivity = null
        AndroidActivityRegistry.clear()
        FragmentPipRegistry.clearForTest()
        PipRegistry.resetForTest()
    }

    @AfterTest
    fun tearDown() {
        Application.current?.dispose()
        GenericUI.activeFactory = null
        AndroidViewHandle.activeContext = null
        AndroidPreferenceStoreImpl.applicationContext = null
        AndroidFileBrowserDialogImpl.activeHostActivity = null
        AndroidActivityRegistry.clear()
        FragmentPipRegistry.clearForTest()
        PipRegistry.resetForTest()
    }

    @Test
    fun androidPipProviderRegistrationAndCapabilitiesReportingOnRobolectric() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, false)
        AndroidPipProvider.register()
        AndroidPipProvider.register()
        assertTrue(PipRegistry.hasRegisteredFactory())

        val unsupportedCaps = PipRegistry.capabilities()
        assertFalse(unsupportedCaps.isSupported)
        assertTrue(unsupportedCaps.supportsInPlaceWindowPip)
        assertTrue(unsupportedCaps.supportsAutoEnterOnBackground)
        assertFalse(unsupportedCaps.supportsCustomControlOverlay)
        assertTrue(PipContentType.VIDEO in unsupportedCaps.supportedContentTypes)
        assertTrue(PipContentType.CANVAS in unsupportedCaps.supportedContentTypes)
        assertTrue(PipContentType.ANY_COMPONENT in unsupportedCaps.supportedContentTypes)

        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        val supportedCaps = PipRegistry.capabilities()
        assertTrue(supportedCaps.isSupported)
        assertTrue(PipRegistry.supportsComponent(Container()))
    }

    @Test
    fun iconRendererGeneratesCrispVectorBitmapsAndResolvesCustomDataUri() {
        val hashes = LinkedHashSet<Int>()
        for (icon in PipActionIcon.entries) {
            val bmp = AndroidPipIconRenderer.renderBitmap(context, icon, sizePx = 64)
            assertEquals(64, bmp.width)
            assertEquals(64, bmp.height)

            var hasOpaquePixel = false
            var hash = 17
            for (y in 0 until bmp.height) {
                for (x in 0 until bmp.width) {
                    val px = bmp.getPixel(x, y)
                    hash = 31 * hash + px
                    if (Color.alpha(px) > 0) {
                        hasOpaquePixel = true
                    }
                }
            }
            assertTrue(hasOpaquePixel, "Rendered icon $icon must contain visible non-transparent pixels")
            hashes.add(hash)

            val androidIcon = AndroidPipIconRenderer.renderIcon(
                context = context,
                action = PipAction(id = "act_${icon.name}", label = icon.name, icon = icon),
                sizePx = 64
            )
            assertNotNull(androidIcon)
        }
        assertTrue(hashes.size >= PipActionIcon.entries.size - 1)

        // Custom data:image/png;base64 URI must decode and scale cleanly
        val customSrc = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.CYAN)
        }
        val baos = ByteArrayOutputStream()
        assertTrue(customSrc.compress(Bitmap.CompressFormat.PNG, 100, baos))
        val dataUri = "data:image/png;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        val customRendered = AndroidPipIconRenderer.renderBitmap(
            context = context,
            icon = PipActionIcon.CUSTOM,
            customResourceUri = dataUri,
            sizePx = 48
        )
        assertEquals(48, customRendered.width)
        assertEquals(48, customRendered.height)
        assertEquals(Color.CYAN, customRendered.getPixel(24, 24))

        // Built-in GenericUI SVG drawable URI ("res://drawable/loading") also resolves
        val loadingResBmp = AndroidPipIconRenderer.renderBitmap(
            context = context,
            icon = PipActionIcon.CUSTOM,
            customResourceUri = "res://drawable/loading",
            sizePx = 48
        )
        assertEquals(48, loadingResBmp.width)
        assertEquals(48, loadingResBmp.height)
    }

    @Test
    fun sourceRectTrackerComputesWindowSpaceRectClampsExtremeAspectRatiosAndDeduplicates() {
        assertEquals(Rational(239, 100), AndroidPipSourceRectTracker.clampAspectRatio(1000, 100))
        assertEquals(Rational(100, 239), AndroidPipSourceRectTracker.clampAspectRatio(100, 1000))
        assertEquals(Rational(239, 100), AndroidPipSourceRectTracker.clampAspectRatio(239, 100))
        assertEquals(Rational(100, 239), AndroidPipSourceRectTracker.clampAspectRatio(100, 239))
        assertEquals(Rational(16, 9), AndroidPipSourceRectTracker.clampAspectRatio(1600, 900))
        assertEquals(Rational(16, 9), AndroidPipSourceRectTracker.clampAspectRatio(0, 0))

        val activityController = Robolectric.buildActivity(PipHostTestActivity::class.java).setup()
        val activity = activityController.get()
        shadowOf(activity.mainLooper).idle()

        val parentHost = FrameLayout(activity)
        val trackedView = View(activity)
        parentHost.addView(
            trackedView,
            FrameLayout.LayoutParams(320, 180).apply {
                leftMargin = 40
                topMargin = 60
            }
        )
        activity.setContentView(parentHost)
        shadowOf(activity.mainLooper).idle()

        val recorded = mutableListOf<Pair<Rect, Rational>>()
        val tracker = AndroidPipSourceRectTracker { rect, rational ->
            recorded.add(Rect(rect) to rational)
        }
        tracker.trackView(trackedView)

        parentHost.measure(
            MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(1920, MeasureSpec.EXACTLY)
        )
        parentHost.layout(0, 0, 1080, 1920)
        shadowOf(activity.mainLooper).idle()

        val expectedCoords = IntArray(2)
        trackedView.getLocationInWindow(expectedCoords)
        val firstUpdate = recorded.lastOrNull()
        assertNotNull(firstUpdate)
        assertEquals(
            Rect(expectedCoords[0], expectedCoords[1], expectedCoords[0] + 320, expectedCoords[1] + 180),
            firstUpdate.first
        )
        assertEquals(Rational(16, 9), firstUpdate.second)

        val countAfterFirstLayout = recorded.size
        tracker.refresh()
        assertEquals(countAfterFirstLayout, recorded.size, "Identical source rect must be deduplicated")

        // Extreme wide (10:1 -> clamped to 239:100)
        trackedView.layoutParams = FrameLayout.LayoutParams(1000, 100).apply {
            leftMargin = 10
            topMargin = 20
        }
        parentHost.measure(
            MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(1920, MeasureSpec.EXACTLY)
        )
        parentHost.layout(0, 0, 1080, 1920)
        shadowOf(activity.mainLooper).idle()
        val wideUpdate = recorded.last()
        assertEquals(1000, wideUpdate.first.width())
        assertEquals(100, wideUpdate.first.height())
        assertEquals(Rational(239, 100), wideUpdate.second)

        // Extreme tall (1:10 -> clamped to 100:239)
        trackedView.layoutParams = FrameLayout.LayoutParams(100, 1000).apply {
            leftMargin = 10
            topMargin = 20
        }
        parentHost.measure(
            MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(1920, MeasureSpec.EXACTLY)
        )
        parentHost.layout(0, 0, 1080, 1920)
        shadowOf(activity.mainLooper).idle()
        val tallUpdate = recorded.last()
        assertEquals(100, tallUpdate.first.width())
        assertEquals(1000, tallUpdate.first.height())
        assertEquals(Rational(100, 239), tallUpdate.second)

        // After detach(), further layout mutations must not emit callbacks
        val countBeforeDetach = recorded.size
        tracker.detach()
        trackedView.layoutParams = FrameLayout.LayoutParams(500, 500)
        parentHost.measure(
            MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(1920, MeasureSpec.EXACTLY)
        )
        parentHost.layout(0, 0, 1080, 1920)
        shadowOf(activity.mainLooper).idle()
        assertEquals(countBeforeDetach, recorded.size)

        activityController.pause().stop().destroy()
    }

    @Test
    fun targetIsolatorHidesChromeAndSiblingsInPlaceWithoutDetachingAndRestoresExactState() {
        val controller = Robolectric.buildActivity(PipHostTestActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(activity.mainLooper).idle()

        val frag = activity.playerFragment
        val targetView = (frag.playerTarget.ensureNativeImpl() as? AndroidBaseNativeImpl)?.nativeWidget?.androidView
        val headerView = (frag.headerText.ensureNativeImpl() as? AndroidBaseNativeImpl)?.nativeWidget?.androidView
        val sidebarView = (frag.sidebarText.ensureNativeImpl() as? AndroidBaseNativeImpl)?.nativeWidget?.androidView
        val bodyRowView = (frag.bodyRow.ensureNativeImpl() as? AndroidBaseNativeImpl)?.nativeWidget?.androidView as? ViewGroup
        assertNotNull(targetView)
        assertNotNull(headerView)
        assertNotNull(sidebarView)
        assertNotNull(bodyRowView)

        val expectedParent = targetView.parent
        assertNotNull(expectedParent)
        bodyRowView.setPadding(12, 16, 12, 16)
        val origWidth = targetView.layoutParams.width
        val origHeight = targetView.layoutParams.height
        assertEquals(View.VISIBLE, activity.toolbarView?.visibility)
        assertEquals(View.VISIBLE, activity.bottomNavigationView?.visibility)
        assertEquals(View.VISIBLE, headerView.visibility)
        assertEquals(View.VISIBLE, sidebarView.visibility)

        val isolator = AndroidPipTargetIsolator()
        isolator.isolateTarget(activity, targetView)

        assertTrue(isolator.isIsolated)
        assertSame(expectedParent, targetView.parent, "Target View must NEVER be detached from its parent")
        assertEquals(View.VISIBLE, targetView.visibility)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, targetView.layoutParams.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, targetView.layoutParams.height)
        assertEquals(View.GONE, activity.toolbarView?.visibility)
        assertEquals(View.GONE, activity.bottomNavigationView?.visibility)
        assertEquals(View.GONE, headerView.visibility)
        assertEquals(View.GONE, sidebarView.visibility)
        assertEquals(0, bodyRowView.paddingLeft)
        assertEquals(0, bodyRowView.paddingTop)
        assertEquals(0, bodyRowView.paddingRight)
        assertEquals(0, bodyRowView.paddingBottom)

        // Repeated isolateTarget call must remain idempotent without losing original snapshot
        isolator.isolateTarget(activity, targetView)
        assertSame(expectedParent, targetView.parent)

        isolator.restore(activity)
        shadowOf(activity.mainLooper).idle()

        assertFalse(isolator.isIsolated)
        assertSame(expectedParent, targetView.parent)
        assertEquals(origWidth, targetView.layoutParams.width)
        assertEquals(origHeight, targetView.layoutParams.height)
        assertEquals(View.VISIBLE, activity.toolbarView?.visibility)
        assertEquals(View.VISIBLE, activity.bottomNavigationView?.visibility)
        assertEquals(View.VISIBLE, headerView.visibility)
        assertEquals(View.VISIBLE, sidebarView.visibility)
        assertEquals(12, bodyRowView.paddingLeft)
        assertEquals(16, bodyRowView.paddingTop)
        assertEquals(12, bodyRowView.paddingRight)
        assertEquals(16, bodyRowView.paddingBottom)

        controller.pause().stop().destroy()
    }

    @Test
    fun activityDelegateBuildsParamsFiresRemoteActionReceiverTriggersUserLeaveHintAndTracksPipLifecycle() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        AndroidPipProvider.register()

        val controller = Robolectric.buildActivity(PipHostTestActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(activity.mainLooper).idle()

        val frag = activity.playerFragment
        val root = activity.rootLayout
        assertNotNull(root)
        root.measure(
            MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(1920, MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, 1080, 1920)
        shadowOf(activity.mainLooper).idle()

        val targetView = (frag.playerTarget.ensureNativeImpl() as? AndroidBaseNativeImpl)?.nativeWidget?.androidView
        assertNotNull(targetView)
        val originalTargetParent = targetView.parent
        assertNotNull(originalTargetParent)

        activity.pipDelegate.syncNow()
        shadowOf(activity.mainLooper).idle()

        val params = activity.pipDelegate.currentPictureInPictureParams
        assertNotNull(params)
        assertEquals(Rational(16, 9), params.aspectRatio)
        assertNotNull(params.sourceRectHint)
        assertFalse(params.sourceRectHint!!.isEmpty)
        assertTrue(params.isAutoEnterEnabled)
        assertTrue(params.isSeamlessResizeEnabled)
        assertEquals(2, params.actions.size)
        assertEquals("Pause", params.actions[0].title.toString())
        assertEquals("Next", params.actions[1].title.toString())

        // Fire RemoteAction broadcast through PendingIntent -> BroadcastReceiver -> PipAction.onTrigger()
        val pausePendingIntent = params.actions[0].actionIntent
        val pauseShadowPi = shadowOf(pausePendingIntent)
        assertTrue(pauseShadowPi.isBroadcast)
        activity.sendBroadcast(pauseShadowPi.savedIntent)
        shadowOf(activity.mainLooper).idle()

        assertEquals(1, frag.pauseActionCount)
        assertFalse(frag.playingState)
        // syncNow() ran after action triggered, so action label should have toggled to "Play" and autoEnter disabled
        val updatedParams = activity.pipDelegate.currentPictureInPictureParams
        assertNotNull(updatedParams)
        assertEquals("Play", updatedParams.actions[0].title.toString())
        assertFalse(updatedParams.isAutoEnterEnabled)

        val nextPendingIntent = updatedParams.actions[1].actionIntent
        activity.sendBroadcast(shadowOf(nextPendingIntent).savedIntent)
        shadowOf(activity.mainLooper).idle()
        assertEquals(1, frag.nextActionCount)

        // When paused, onUserLeaveHint must NOT enter PiP
        activity.onUserLeaveHint()
        assertFalse(frag.pipHelper.isEnteringPip)

        // When playing + enabled + autoEnterOnBackground, onUserLeaveHint MUST trigger PiP entry
        frag.playingState = true
        frag.pipHelper.notifyStateChanged()
        shadowOf(activity.mainLooper).idle()
        activity.onUserLeaveHint()
        assertTrue(frag.pipHelper.isEnteringPip)

        // Enter PiP mode callback: isolates targetView in-place and updates helper/delegate state
        activity.onPictureInPictureModeChanged(true, Configuration(activity.resources.configuration))
        shadowOf(activity.mainLooper).idle()
        assertTrue(activity.pipDelegate.isCurrentlyInPipMode)
        assertTrue(frag.pipHelper.isInPipMode)
        assertFalse(frag.pipHelper.isEnteringPip)
        assertSame(originalTargetParent, targetView.parent)
        assertEquals(View.GONE, activity.toolbarView?.visibility)

        // Expand back to fullscreen (Activity still STARTED/RESUMED) -> must NOT invoke onPauseRequested
        activity.onPictureInPictureModeChanged(false, Configuration(activity.resources.configuration))
        shadowOf(activity.mainLooper).idle()
        assertFalse(activity.pipDelegate.isCurrentlyInPipMode)
        assertFalse(frag.pipHelper.isInPipMode)
        assertEquals(0, frag.closePauseRequestedCount)
        assertEquals(View.VISIBLE, activity.toolbarView?.visibility)

        // Re-enter PiP via onUserLeaveHint, then simulate PiP close ('✕') while Activity transitions to STOPPED
        assertTrue(frag.playingState)
        activity.onUserLeaveHint()
        activity.onPictureInPictureModeChanged(true, Configuration(activity.resources.configuration))
        shadowOf(activity.mainLooper).idle()
        assertTrue(activity.pipDelegate.isCurrentlyInPipMode)

        controller.pause().stop()
        activity.onPictureInPictureModeChanged(false, Configuration(activity.resources.configuration))
        shadowOf(activity.mainLooper).idle()
        assertFalse(activity.pipDelegate.isCurrentlyInPipMode)
        assertEquals(1, frag.closePauseRequestedCount)
        assertFalse(frag.playingState)
        assertEquals(listOf(true, false, true, false), frag.modeHistory)

        controller.destroy()
    }
}

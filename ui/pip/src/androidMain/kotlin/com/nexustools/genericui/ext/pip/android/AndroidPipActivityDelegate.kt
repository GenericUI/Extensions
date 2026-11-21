package com.nexustools.genericui.ext.pip.android

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.nexustools.genericui.android.AndroidBaseNativeImpl
import com.nexustools.genericui.android.AndroidViewHandle
import com.nexustools.genericui.android.GenericUiFragmentActivity
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.helper.FragmentHostFragment
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.ext.pip.FragmentPipHelper
import com.nexustools.genericui.ext.pip.FragmentPipRegistry
import com.nexustools.genericui.ext.pip.PipAction
import com.nexustools.genericui.window.IWindow
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Activity-scoped Android Picture-in-Picture controller bound to a [GenericUiFragmentActivity].
 *
 * Implements Contract B (`onActivityCreated`, `onActivityDestroyed`, `enterFragmentPictureInPicture`,
 * `updatePictureInPictureParams`, `onUserLeaveHint`, `onPictureInPictureModeChanged`, `syncNow`),
 * builds API 26+/31+ `PictureInPictureParams` with [AndroidPipIconRenderer] `RemoteAction`s and
 * [AndroidPipSourceRectTracker] bounds, isolates `pipTarget`'s `View` in place via
 * [AndroidPipTargetIsolator], and detects PiP close (`✕`) vs fullscreen expand transitions.
 */
class AndroidPipActivityDelegate(
    val activity: GenericUiFragmentActivity
) {
    private val instanceId: Int = NEXT_INSTANCE_ID.incrementAndGet()
    private val actionIntentAction: String = "$ACTION_PREFIX.$instanceId"
    private val mainHandler: Handler? = runCatching {
        Looper.getMainLooper()?.let { Handler(it) }
    }.getOrNull()

    private val isolator: AndroidPipTargetIsolator = AndroidPipTargetIsolator()
    private val sourceRectTracker: AndroidPipSourceRectTracker = AndroidPipSourceRectTracker { _, _ ->
        if (!destroyed && !isCurrentlyInPipMode && !isSyncingTracker) {
            syncNow()
        }
    }

    private var boundBackendRef: WeakReference<AndroidPipBackend>? = null
    private var attachedBackendHelper: FragmentPipHelper? = null
    private var lastTrackedTarget: AbstractComponent? = null
    private var receiverRegistered: Boolean = false
    private var destroyed: Boolean = false
    private var isSyncingTracker: Boolean = false
    private var closeDispatchedForCurrentExit: Boolean = false
    private var stalePlatformInPipFlag: Boolean = false
    private var pendingCloseCheckRunnable: Runnable? = null

    @Volatile
    private var lastBuiltParamsObj: Any? = null

    val isPipSupportedOnDevice: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && runCatching {
            activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        }.getOrDefault(false)

    var isCurrentlyInPipMode: Boolean = false
        private set

    val currentPictureInPictureParams: PictureInPictureParams?
        @RequiresApi(Build.VERSION_CODES.O)
        get() = lastBuiltParamsObj as? PictureInPictureParams

    private val actionReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (destroyed || intent?.action != actionIntentAction) return
            val actionId = intent.getStringExtra(EXTRA_ACTION_ID) ?: return
            AndroidViewHandle.runOnMainThread {
                if (destroyed) return@runOnMainThread
                val helper = resolveActiveHelper() ?: return@runOnMainThread
                val matched = helper.resolveEffectiveActions().firstOrNull { it.id == actionId }
                    ?: return@runOnMainThread
                if (matched.enabled) {
                    matched.onTrigger()
                    syncNow()
                }
            }
        }
    }

    init {
        registerDelegate(activity, this)
    }

    fun onActivityCreated(savedInstanceState: Bundle?) {
        destroyed = false
        registerDelegate(activity, this)
        ensureReceiverRegistered()
        syncNow()
        postOnMain {
            if (!destroyed) {
                syncNow()
            }
        }
    }

    fun onActivityDestroyed() {
        destroyed = true
        pendingCloseCheckRunnable?.let { mainHandler?.removeCallbacks(it) }
        pendingCloseCheckRunnable = null
        mainHandler?.removeCallbacksAndMessages(null)
        sourceRectTracker.detach()
        if (isolator.isIsolated) {
            isolator.restore(activity)
        }
        unregisterReceiverSafely()
        lastTrackedTarget = null
        attachedBackendHelper = null
        boundBackendRef = null
        unregisterDelegate(activity, this)
    }

    fun enterFragmentPictureInPicture(): Boolean {
        if (destroyed || Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isPipSupportedOnDevice) {
            return false
        }
        if (activity.isDestroyed || activity.isFinishing) {
            return false
        }
        ensureReceiverRegistered()
        val helper = resolveActiveHelper()
        if (helper != null && !helper.isEffectivelyEnabled()) {
            return false
        }
        if (helper != null) {
            ensureTrackerAttached(helper)
        }
        val (aspectW, aspectH) = helper?.resolveEffectiveAspectRatio() ?: (16 to 9)
        val playing = helper?.isEffectivelyPlaying() ?: false
        val params = buildPictureInPictureParams(
            helper = helper,
            playing = playing,
            aspectWidth = aspectW,
            aspectHeight = aspectH
        ) ?: return false
        lastBuiltParamsObj = params

        markHierarchyEnteringPip(helper, true)
        val entered = runCatching {
            activity.enterPictureInPictureMode(params)
        }.getOrDefault(false)
        if (entered) {
            stalePlatformInPipFlag = false
        } else {
            markHierarchyEnteringPip(helper, false)
        }
        return entered
    }

    fun updatePictureInPictureParams(
        playing: Boolean = false,
        aspectWidth: Int = 16,
        aspectHeight: Int = 9
    ) {
        if (destroyed || activity.isDestroyed) return
        ensureReceiverRegistered()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val helper = resolveActiveHelper()
        if (helper != null && !isCurrentlyInPipMode) {
            ensureTrackerAttached(helper)
        }
        val params = buildPictureInPictureParams(
            helper = helper,
            playing = playing,
            aspectWidth = aspectWidth,
            aspectHeight = aspectHeight
        ) ?: return
        lastBuiltParamsObj = params
        if (isPipSupportedOnDevice && !activity.isFinishing) {
            runCatching {
                activity.setPictureInPictureParams(params)
            }
        }
    }

    fun onActivityPaused() {
        if (destroyed) return
        val systemInPip = !stalePlatformInPipFlag &&
            runCatching { Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInPictureInPictureMode }.getOrDefault(false)
        val helper = resolveActiveHelper()
        val autoEnterArmed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            isPipSupportedOnDevice &&
            !activity.isFinishing &&
            !activity.isChangingConfigurations &&
            helper != null &&
            helper.isEffectivelyEnabled() &&
            helper.resolveEffectiveConfig().let { it.enabled && it.autoEnterOnBackground } &&
            helper.isEffectivelyPlaying()
        if (isCurrentlyInPipMode || systemInPip || autoEnterArmed) {
            markHierarchyEnteringPip(resolveRootHelper() ?: helper, true)
        }
    }

    fun onActivityStopped() {
        if (destroyed) return
        val systemInPip = !stalePlatformInPipFlag &&
            runCatching { Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInPictureInPictureMode }.getOrDefault(false)
        if (!isCurrentlyInPipMode && !systemInPip) {
            markHierarchyEnteringPip(resolveRootHelper() ?: resolveActiveHelper(), false)
        }
    }

    fun onUserLeaveHint() {
        if (destroyed || Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isPipSupportedOnDevice) {
            return
        }
        val systemInPip = if (stalePlatformInPipFlag) {
            false
        } else {
            runCatching { activity.isInPictureInPictureMode }.getOrDefault(false)
        }
        if (isCurrentlyInPipMode || systemInPip) {
            return
        }
        val helper = resolveActiveHelper() ?: return
        val config = helper.resolveEffectiveConfig()
        if (helper.isEffectivelyEnabled() &&
            config.enabled &&
            config.autoEnterOnBackground &&
            helper.isEffectivelyPlaying()
        ) {
            enterFragmentPictureInPicture()
        }
    }

    fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        if (destroyed) return
        val helper = resolveActiveHelper()
        val dispatchHelper = resolveRootHelper() ?: helper
        if (isInPictureInPictureMode) {
            pendingCloseCheckRunnable?.let { mainHandler?.removeCallbacks(it) }
            pendingCloseCheckRunnable = null
            closeDispatchedForCurrentExit = false
            stalePlatformInPipFlag = false
            isCurrentlyInPipMode = true

            val targetComp = helper?.resolveEffectiveTarget()
            val targetView = resolveTargetAndroidView(targetComp)
            if (targetView != null) {
                isolator.isolateTarget(activity, targetView)
            }

            dispatchHelper?.dispatchPipModeChangedFromBackend(true)
            markHierarchyEnteringPip(dispatchHelper ?: helper, false)
            notifyOuterHostedFragmentIfDistinct(dispatchHelper ?: helper, true)
        } else {
            val wasInPip = isCurrentlyInPipMode ||
                (helper?.isInPipMode == true) ||
                (dispatchHelper?.isInPipMode == true)
            isCurrentlyInPipMode = false
            closeDispatchedForCurrentExit = false
            stalePlatformInPipFlag = runCatching { activity.isInPictureInPictureMode }.getOrDefault(false)
            isolator.restore(activity)

            markHierarchyEnteringPip(dispatchHelper ?: helper, false)
            dispatchHelper?.dispatchPipModeChangedFromBackend(false)
            notifyOuterHostedFragmentIfDistinct(dispatchHelper ?: helper, false)

            postOnMain {
                if (!destroyed && !isCurrentlyInPipMode) {
                    syncNow()
                }
            }

            if (wasInPip) {
                evaluatePipExitCloseState(dispatchHelper ?: helper)
            }
        }
    }

    fun syncNow() {
        if (destroyed || activity.isDestroyed) return
        ensureReceiverRegistered()
        val helper = resolveActiveHelper()
        if (helper == null) {
            lastTrackedTarget = null
            sourceRectTracker.detach()
            return
        }
        if (!isCurrentlyInPipMode) {
            ensureTrackerAttached(helper)
        }
        val (aspectW, aspectH) = helper.resolveEffectiveAspectRatio()
        val playing = helper.isEffectivelyPlaying()
        updatePictureInPictureParams(
            playing = playing,
            aspectWidth = aspectW,
            aspectHeight = aspectH
        )
    }

    internal fun attachBackend(backend: AndroidPipBackend, helper: FragmentPipHelper) {
        boundBackendRef = WeakReference(backend)
        attachedBackendHelper = helper
        if (!destroyed) {
            AndroidViewHandle.runOnMainThread {
                if (!destroyed) {
                    syncNow()
                }
            }
        }
    }

    internal fun detachBackend(backend: AndroidPipBackend) {
        if (boundBackendRef?.get() === backend) {
            boundBackendRef = null
            attachedBackendHelper = null
        }
    }

    internal fun exitPictureInPictureIfActive(): Boolean {
        if (destroyed || !isCurrentlyInPipMode) return false
        return runCatching {
            val restoreIntent = Intent(activity, activity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            activity.startActivity(restoreIntent)
            true
        }.getOrDefault(false)
    }

    internal fun resolveActiveHelper(): FragmentPipHelper? {
        val registryHelper = resolveHelperFromFragmentHierarchy(activity.hostedFragment)
            ?: FragmentPipRegistry.findForWindow(activity.activityWindow)
        val raw = registryHelper ?: attachedBackendHelper ?: return null
        return runCatching { raw.effectiveHelper() }.getOrDefault(raw)
    }

    private fun resolveRootHelper(): FragmentPipHelper? =
        activity.hostedFragment?.let { FragmentPipRegistry.findForFragment(it) }
            ?: FragmentPipRegistry.findForWindow(activity.activityWindow)
            ?: attachedBackendHelper

    private fun resolveHelperFromFragmentHierarchy(rootFragment: Fragment?): FragmentPipHelper? {
        var current: Fragment? = rootFragment
        var depth = 0
        val chain = mutableListOf<Fragment>()
        while (current != null && depth < 16) {
            chain.add(current)
            current = (current as? FragmentHostFragment)?.currentFragment
            depth++
        }
        for (frag in chain.asReversed()) {
            FragmentPipRegistry.findForFragment(frag)?.let { return it }
        }
        return null
    }

    private fun markHierarchyEnteringPip(helper: FragmentPipHelper?, entering: Boolean) {
        helper?.markEnteringPip(entering)
        attachedBackendHelper?.markEnteringPip(entering)
        FragmentPipRegistry.findForWindow(activity.activityWindow)?.markEnteringPip(entering)
        var current: Fragment? = activity.hostedFragment
        var depth = 0
        while (current != null && depth < 16) {
            FragmentPipRegistry.findForFragment(current)?.markEnteringPip(entering)
            current = (current as? FragmentHostFragment)?.currentFragment
            depth++
        }
    }

    private fun ensureTrackerAttached(helper: FragmentPipHelper) {
        val target = helper.resolveEffectiveTarget()
        val fallbackHost: View? = activity.contentContainer ?: activity.window?.decorView
        isSyncingTracker = true
        try {
            if (target !== lastTrackedTarget) {
                lastTrackedTarget = target
            }
            sourceRectTracker.track(
                component = target,
                fallbackHostView = fallbackHost,
                preferredAspectRatio = null
            )
            sourceRectTracker.refresh()
        } finally {
            isSyncingTracker = false
        }
    }

    private fun resolveTargetAndroidView(component: AbstractComponent?): View? {
        if (component == null) return null
        val impl = runCatching { component.ensureNativeImpl() }.getOrNull() as? AndroidBaseNativeImpl
        return impl?.nativeWidget?.androidView
    }

    private fun notifyOuterHostedFragmentIfDistinct(helper: FragmentPipHelper?, inPip: Boolean) {
        val hosted = activity.hostedFragment ?: return
        if (helper == null || hosted !== helper.fragment) {
            runCatching { hosted.onPictureInPictureModeChanged(inPip) }
        }
    }

    private fun evaluatePipExitCloseState(helper: FragmentPipHelper?) {
        if (isActivityStoppedOrStopping()) {
            dispatchCloseOnce(helper)
            return
        }
        pendingCloseCheckRunnable?.let { mainHandler?.removeCallbacks(it) }
        val check = Runnable {
            pendingCloseCheckRunnable = null
            if (!destroyed && !isCurrentlyInPipMode && isActivityStoppedOrStopping()) {
                dispatchCloseOnce(resolveActiveHelper() ?: helper)
            }
        }
        pendingCloseCheckRunnable = check
        postOnMain(check)
    }

    private fun isActivityStoppedOrStopping(): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return true
        val state = runCatching { activity.lifecycle.currentState }.getOrNull() ?: return false
        return !state.isAtLeast(Lifecycle.State.STARTED)
    }

    private fun dispatchCloseOnce(helper: FragmentPipHelper?) {
        if (closeDispatchedForCurrentExit) return
        closeDispatchedForCurrentExit = true
        helper?.dispatchCloseRequestedFromBackend()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPictureInPictureParams(
        helper: FragmentPipHelper?,
        playing: Boolean,
        aspectWidth: Int,
        aspectHeight: Int
    ): PictureInPictureParams? = runCatching {
        val builder = PictureInPictureParams.Builder()
        val rational = AndroidPipSourceRectTracker.clampAspectRatio(aspectWidth, aspectHeight)
        builder.setAspectRatio(rational)

        val rect = sourceRectTracker.currentSourceRect
        if (rect != null && !rect.isEmpty) {
            builder.setSourceRectHint(rect)
        }

        val actions = helper?.resolveEffectiveActions().orEmpty()
        builder.setActions(buildRemoteActions(actions))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val enabled = helper?.isEffectivelyEnabled() ?: false
            val autoEnterBg = helper?.resolveEffectiveConfig()?.autoEnterOnBackground ?: false
            builder.setAutoEnterEnabled(enabled && autoEnterBg && playing)
            builder.setSeamlessResizeEnabled(true)
        }
        builder.build()
    }.getOrNull()

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildRemoteActions(actions: List<PipAction>): List<RemoteAction> {
        if (actions.isEmpty()) return emptyList()
        val maxActions = runCatching { activity.maxNumPictureInPictureActions }.getOrDefault(0)
        val bounded = if (maxActions > 0) actions.take(maxActions) else actions
        val result = ArrayList<RemoteAction>(bounded.size)
        for ((index, action) in bounded.withIndex()) {
            val icon = AndroidPipIconRenderer.renderIcon(activity, action)
            val title = action.label.ifBlank { action.id }
            val intent = Intent(actionIntentAction).apply {
                setPackage(activity.packageName)
                putExtra(EXTRA_ACTION_ID, action.id)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                activity,
                (instanceId shl 8) + index,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val remoteAction = RemoteAction(icon, title, title, pendingIntent).apply {
                isEnabled = action.enabled
            }
            result.add(remoteAction)
        }
        return result
    }

    private fun ensureReceiverRegistered() {
        if (receiverRegistered || destroyed || activity.isDestroyed) return
        runCatching {
            ContextCompat.registerReceiver(
                activity,
                actionReceiver,
                IntentFilter(actionIntentAction),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
    }

    private fun unregisterReceiverSafely() {
        if (!receiverRegistered) return
        receiverRegistered = false
        runCatching {
            activity.unregisterReceiver(actionReceiver)
        }
    }

    private fun postOnMain(runnable: Runnable) {
        val handler = mainHandler
        if (handler != null) {
            handler.post(runnable)
        } else {
            AndroidViewHandle.runOnMainThread { runnable.run() }
        }
    }

    private fun postOnMain(block: () -> Unit) {
        val handler = mainHandler
        if (handler != null) {
            handler.post(block)
        } else {
            AndroidViewHandle.runOnMainThread(block)
        }
    }

    companion object {
        internal const val EXTRA_ACTION_ID: String = "com.nexustools.genericui.ext.pip.EXTRA_ACTION_ID"
        private const val ACTION_PREFIX: String = "com.nexustools.genericui.ext.pip.ACTION"
        private val NEXT_INSTANCE_ID: AtomicInteger = AtomicInteger(1)

        private val delegatesByActivity: MutableMap<GenericUiFragmentActivity, AndroidPipActivityDelegate> =
            Collections.synchronizedMap(WeakHashMap())

        private fun registerDelegate(
            activity: GenericUiFragmentActivity,
            delegate: AndroidPipActivityDelegate
        ) {
            delegatesByActivity[activity] = delegate
        }

        private fun unregisterDelegate(
            activity: GenericUiFragmentActivity,
            delegate: AndroidPipActivityDelegate
        ) {
            synchronized(delegatesByActivity) {
                if (delegatesByActivity[activity] === delegate) {
                    delegatesByActivity.remove(activity)
                }
            }
        }

        internal fun findForActivity(activity: Activity?): AndroidPipActivityDelegate? {
            val fragActivity = activity as? GenericUiFragmentActivity ?: return null
            return delegatesByActivity[fragActivity]
        }

        internal fun findForWindow(window: IWindow?): AndroidPipActivityDelegate? {
            if (window == null) return null
            synchronized(delegatesByActivity) {
                for ((act, delegate) in delegatesByActivity) {
                    if (!act.isDestroyed && act.activityWindow === window) {
                        return delegate
                    }
                }
            }
            return null
        }

        internal fun findForFragment(fragment: Fragment?): AndroidPipActivityDelegate? {
            if (fragment == null) return null
            synchronized(delegatesByActivity) {
                for ((act, delegate) in delegatesByActivity) {
                    if (!act.isDestroyed && containsFragment(act.hostedFragment, fragment)) {
                        return delegate
                    }
                }
            }
            return null
        }

        private fun containsFragment(root: Fragment?, target: Fragment): Boolean {
            var current: Fragment? = root
            var depth = 0
            while (current != null && depth < 16) {
                if (current === target) return true
                current = (current as? FragmentHostFragment)?.currentFragment
                depth++
            }
            return false
        }
    }
}

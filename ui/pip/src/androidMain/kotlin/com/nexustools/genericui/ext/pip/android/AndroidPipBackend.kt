package com.nexustools.genericui.ext.pip.android

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import com.nexustools.genericui.android.AndroidActivityRegistry
import com.nexustools.genericui.android.AndroidPreferenceStoreImpl
import com.nexustools.genericui.android.AndroidViewHandle
import com.nexustools.genericui.android.GenericUiFragmentActivity
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.ext.pip.FragmentPipHelper
import com.nexustools.genericui.ext.pip.PipBackend
import com.nexustools.genericui.ext.pip.PipCapabilities
import com.nexustools.genericui.ext.pip.PipContentType
import com.nexustools.genericui.threading.GenericUiThreading
import com.nexustools.genericui.window.IWindow

/**
 * Android [PipBackend] backed by Android `PictureInPictureParams` and [AndroidPipActivityDelegate].
 */
class AndroidPipBackend : PipBackend {

    private var boundWindow: IWindow? = null
    private var boundFragment: Fragment? = null
    private var boundHelper: FragmentPipHelper? = null
    private var attachedDelegate: AndroidPipActivityDelegate? = null

    override val capabilities: PipCapabilities
        get() = PipCapabilities(
            isSupported = isDevicePipSupported(),
            supportedContentTypes = setOf(
                PipContentType.VIDEO,
                PipContentType.CANVAS,
                PipContentType.ANY_COMPONENT
            ),
            supportsCustomControlOverlay = false,
            supportsInPlaceWindowPip = true,
            supportsAutoEnterOnMinimize = false,
            supportsAutoEnterOnBackground = true,
            supportsAutoEnterOnTabSwitch = false
        )

    override val isInPipMode: Boolean
        get() = resolveDelegate()?.isCurrentlyInPipMode ?: (boundHelper?.isInPipMode == true)

    override fun supportsComponent(component: AbstractComponent): Boolean = true

    override fun attachHost(
        window: IWindow?,
        fragment: Fragment?,
        helper: FragmentPipHelper
    ) {
        boundWindow = window
        boundFragment = fragment
        boundHelper = helper
        val delegate = resolveDelegate()
        if (delegate != null) {
            attachedDelegate = delegate
            delegate.attachBackend(this, helper)
        }
    }

    override fun detachHost() {
        attachedDelegate?.detachBackend(this)
        attachedDelegate = null
        boundWindow = null
        boundFragment = null
        boundHelper = null
    }

    override fun syncSessionState() {
        val delegate = resolveDelegate() ?: return
        boundHelper?.let { helper ->
            attachedDelegate = delegate
            delegate.attachBackend(this, helper)
        }
        AndroidViewHandle.runOnMainThread {
            delegate.syncNow()
        }
    }

    override fun enterPip(): Boolean {
        val delegate = resolveDelegate() ?: return false
        boundHelper?.let { helper ->
            attachedDelegate = delegate
            delegate.attachBackend(this, helper)
        }
        return runOnUiBlocking {
            delegate.enterFragmentPictureInPicture()
        }
    }

    override fun exitPip(): Boolean {
        val delegate = resolveDelegate() ?: return false
        return runOnUiBlocking {
            delegate.exitPictureInPictureIfActive()
        }
    }

    private fun resolveDelegate(): AndroidPipActivityDelegate? {
        val existing = attachedDelegate?.takeIf { !it.activity.isDestroyed }
        if (existing != null) return existing

        val resolved = AndroidPipActivityDelegate.findForWindow(boundWindow)
            ?: AndroidPipActivityDelegate.findForFragment(boundFragment)
            ?: AndroidPipActivityDelegate.findForActivity(AndroidActivityRegistry.resolveActiveActivity())
            ?: (boundHelper?.fragment?.let { AndroidPipActivityDelegate.findForFragment(it) })
            ?: (boundHelper?.fragment?.hostingWindow?.let { AndroidPipActivityDelegate.findForWindow(it) })

        if (resolved != null) {
            attachedDelegate = resolved
            boundHelper?.let { resolved.attachBackend(this, it) }
        }
        return resolved
    }

    private fun isDevicePipSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        resolveDelegate()?.let { return it.isPipSupportedOnDevice }
        val context = resolveAndroidContext() ?: return false
        return runCatching {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        }.getOrDefault(false)
    }

    private fun resolveAndroidContext(): Context? =
        (AndroidActivityRegistry.resolveActiveActivity() as? GenericUiFragmentActivity)
            ?: AndroidActivityRegistry.resolveActiveActivity()
            ?: AndroidViewHandle.activeContext
            ?: AndroidPreferenceStoreImpl.applicationContext

    private fun runOnUiBlocking(block: () -> Boolean): Boolean {
        val mainLooper = runCatching { Looper.getMainLooper() }.getOrNull()
        val mainThread = runCatching { mainLooper?.thread }.getOrNull()
        if (mainLooper == null || mainThread == null || mainThread === Thread.currentThread()) {
            return block()
        }
        if (GenericUI.activeFactory != null && GenericUiThreading.isSplitThreadingEnabled) {
            return GenericUiThreading.runOnUiSync(block)
        }
        return block()
    }
}

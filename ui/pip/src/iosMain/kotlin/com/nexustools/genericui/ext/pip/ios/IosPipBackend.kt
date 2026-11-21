@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.pip.ios

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.ext.pip.FragmentPipHelper
import com.nexustools.genericui.ext.pip.PipBackend
import com.nexustools.genericui.ext.pip.PipCapabilities
import com.nexustools.genericui.ext.pip.PipContentType
import com.nexustools.genericui.window.IWindow
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayerLayer
import platform.AVKit.AVPictureInPictureController
import platform.objc.sel_registerName

/**
 * iOS & iPadOS [PipBackend] backed by `AVPictureInPictureController` and `AVAudioSession`.
 *
 * Automatically locates an `AVPlayerLayer` in `pipTarget`'s `UIView`/`CALayer` subtree via
 * [IosPipLayerFinder] without depending on `:extensions:media:media`.
 */
class IosPipBackend : PipBackend {

    private var attachedWindow: IWindow? = null
    private var attachedFragment: Fragment? = null
    private var attachedHelper: FragmentPipHelper? = null

    private var activePlayerLayer: AVPlayerLayer? = null
    var pipController: AVPictureInPictureController? = null
        private set

    private var restoreRequestedForStop: Boolean = false
    private var programmaticStopRequested: Boolean = false

    val controllerDelegate: IosPipControllerDelegate = IosPipControllerDelegate(
        onWillStart = {
            restoreRequestedForStop = false
            programmaticStopRequested = false
            attachedHelper?.markEnteringPip(true)
        },
        onDidStart = {
            restoreRequestedForStop = false
            programmaticStopRequested = false
            isInPipMode = true
            attachedHelper?.dispatchPipModeChangedFromBackend(true)
            attachedHelper?.markEnteringPip(false)
        },
        onFailedToStart = { _ ->
            restoreRequestedForStop = false
            programmaticStopRequested = false
            val wasActive = isInPipMode
            isInPipMode = false
            attachedHelper?.markEnteringPip(false)
            if (wasActive) {
                attachedHelper?.dispatchPipModeChangedFromBackend(false)
            }
        },
        onWillStop = {},
        onDidStop = {
            val wasInPip = isInPipMode
            isInPipMode = false
            attachedHelper?.markEnteringPip(false)
            attachedHelper?.dispatchPipModeChangedFromBackend(false)
            if (wasInPip && !restoreRequestedForStop && !programmaticStopRequested) {
                attachedHelper?.dispatchCloseRequestedFromBackend()
            }
            restoreRequestedForStop = false
            programmaticStopRequested = false
        },
        onRestoreUserInterface = { completionHandler ->
            restoreRequestedForStop = true
            completionHandler(true)
        }
    )

    override val capabilities: PipCapabilities
        get() {
            val supported = isPipSupportedBySystem()
            return PipCapabilities(
                isSupported = supported,
                supportedContentTypes = if (supported) setOf(PipContentType.VIDEO) else emptySet(),
                supportsCustomControlOverlay = false,
                supportsInPlaceWindowPip = false,
                supportsAutoEnterOnMinimize = false,
                supportsAutoEnterOnBackground = supported,
                supportsAutoEnterOnTabSwitch = false
            )
        }

    override var isInPipMode: Boolean = false
        private set

    override fun supportsComponent(component: AbstractComponent): Boolean =
        IosPipLayerFinder.findPlayerLayer(component) != null

    override fun attachHost(
        window: IWindow?,
        fragment: Fragment?,
        helper: FragmentPipHelper
    ) {
        attachedWindow = window
        attachedFragment = fragment
        attachedHelper = helper
        syncSessionState()
    }

    override fun detachHost() {
        pipController?.delegate = null
        pipController = null
        activePlayerLayer = null
        attachedWindow = null
        attachedFragment = null
        attachedHelper = null
        isInPipMode = false
        restoreRequestedForStop = false
        programmaticStopRequested = false
    }

    override fun syncSessionState() {
        val helper = attachedHelper ?: return
        ensureControllerForTarget(helper.resolveEffectiveTarget())
        val ctrl = pipController ?: return
        val enabled = helper.isEffectivelyEnabled()
        val config = helper.resolveEffectiveConfig()
        val supportsAutoInline = runCatching {
            ctrl.respondsToSelector(sel_registerName("setCanStartPictureInPictureAutomaticallyFromInline:"))
        }.getOrDefault(false)
        if (supportsAutoInline) {
            runCatching {
                ctrl.canStartPictureInPictureAutomaticallyFromInline =
                    config.autoEnterOnBackground && enabled
            }
        }
    }

    override fun enterPip(): Boolean {
        if (!isPipSupportedBySystem()) return false
        val helper = attachedHelper ?: return false
        if (!helper.isEffectivelyEnabled()) return false

        configurePlaybackAudioSession()
        ensureControllerForTarget(helper.resolveEffectiveTarget())

        val ctrl = pipController ?: return false
        if (isInPipMode || runCatching { ctrl.pictureInPictureActive }.getOrDefault(false)) {
            return true
        }
        val possible = runCatching { ctrl.pictureInPicturePossible }.getOrDefault(false)
        if (!possible) {
            return false
        }

        restoreRequestedForStop = false
        programmaticStopRequested = false
        helper.markEnteringPip(true)
        return runCatching {
            ctrl.startPictureInPicture()
            true
        }.onFailure {
            helper.markEnteringPip(false)
        }.getOrDefault(false)
    }

    override fun exitPip(): Boolean {
        val ctrl = pipController ?: return false
        val active = isInPipMode || runCatching { ctrl.pictureInPictureActive }.getOrDefault(false)
        if (!active) return false
        programmaticStopRequested = true
        return runCatching {
            ctrl.stopPictureInPicture()
            true
        }.onFailure {
            programmaticStopRequested = false
        }.getOrDefault(false)
    }

    private fun ensureControllerForTarget(target: AbstractComponent?) {
        val layer = IosPipLayerFinder.findPlayerLayer(target)
        if (layer == null) {
            if (!isInPipMode) {
                pipController?.delegate = null
                pipController = null
                activePlayerLayer = null
            }
            return
        }
        if (layer === activePlayerLayer && pipController != null) {
            return
        }

        pipController?.delegate = null
        pipController = null
        activePlayerLayer = layer

        if (!isPipSupportedBySystem()) {
            return
        }
        configurePlaybackAudioSession()
        val created = runCatching {
            val c = AVPictureInPictureController(playerLayer = layer)
            if ((c as Any?) != null) {
                c.delegate = controllerDelegate
                c
            } else {
                null
            }
        }.getOrNull()
        pipController = created
    }

    private fun configurePlaybackAudioSession() {
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, error = null)
            session.setActive(true, error = null)
        }
    }

    private fun isPipSupportedBySystem(): Boolean =
        runCatching { AVPictureInPictureController.isPictureInPictureSupported() }.getOrDefault(false)
}

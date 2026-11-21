package com.nexustools.genericui.ext.pip

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.window.IWindow

/**
 * Coordinates Picture-in-Picture configuration, delegation, window binding, and lifecycle state
 * for an owning [Fragment].
 */
class FragmentPipHelper(val fragment: Fragment) : Disposable {

    var pipTarget: AbstractComponent? = null
    var pipTargetProvider: (() -> AbstractComponent?)? = null
    var childDelegateResolver: (() -> FragmentPipHelper?)? = null

    var configProvider: () -> PipSessionConfig = { PipSessionConfig() }
    var isEnabledProvider: () -> Boolean = { true }
    var isActivelyPlayingProvider: () -> Boolean = { false }
    var aspectRatioProvider: () -> Pair<Int, Int> = {
        val cfg = configProvider()
        cfg.aspectRatioNumerator to cfg.aspectRatioDenominator
    }
    var actionsProvider: () -> List<PipAction> = { emptyList() }

    var onPauseRequested: (() -> Unit)? = null
    var onPipModeChanged: ((Boolean) -> Unit)? = null

    var isInPipMode: Boolean = false
        private set

    var isEnteringPip: Boolean = false
        internal set

    override var isDisposed: Boolean = false
        private set

    private var boundWindow: IWindow? = null
    private var backend: PipBackend? = null

    val capabilities: PipCapabilities
        get() = backend?.capabilities ?: PipRegistry.capabilities()

    init {
        FragmentPipRegistry.register(fragment, this)
    }

    fun markEnteringPip(entering: Boolean) {
        isEnteringPip = entering
    }

    fun effectiveHelper(): FragmentPipHelper {
        val visited = mutableListOf(this)
        var current = this
        while (true) {
            val next = current.childDelegateResolver?.invoke() ?: break
            if (next.isDisposed || visited.any { it === next }) break
            visited.add(next)
            current = next
        }
        return current
    }

    fun resolveEffectiveTarget(): AbstractComponent? {
        val effective = effectiveHelper()
        return effective.pipTargetProvider?.invoke() ?: effective.pipTarget
    }

    fun resolveEffectiveConfig(): PipSessionConfig =
        effectiveHelper().configProvider()

    fun resolveEffectiveActions(): List<PipAction> =
        effectiveHelper().actionsProvider()

    fun resolveEffectiveAspectRatio(): Pair<Int, Int> {
        val effective = effectiveHelper()
        val raw = effective.aspectRatioProvider()
        if (raw.first > 0 && raw.second > 0) {
            return raw
        }
        val cfg = effective.configProvider()
        return if (cfg.aspectRatioNumerator > 0 && cfg.aspectRatioDenominator > 0) {
            cfg.aspectRatioNumerator to cfg.aspectRatioDenominator
        } else {
            16 to 9
        }
    }

    fun isEffectivelyEnabled(): Boolean {
        val effective = effectiveHelper()
        return !isDisposed && effective.isEnabledProvider() && effective.configProvider().enabled
    }

    fun isEffectivelyPlaying(): Boolean =
        effectiveHelper().isActivelyPlayingProvider()

    fun isTargetSupported(): Boolean {
        val target = resolveEffectiveTarget() ?: return false
        val active = backend ?: PipRegistry.createBackend()
        return active?.supportsComponent(target) ?: PipRegistry.supportsComponent(target)
    }

    fun bindWindow(
        window: IWindow? = fragment.hostingWindow ?: (fragment.application ?: Application.current)?.mainWindow
    ) {
        if (isDisposed) return
        val previous = boundWindow
        if (previous != null && previous !== window) {
            FragmentPipRegistry.unregisterWindow(previous, this)
            backend?.detachHost()
        }
        boundWindow = window
        if (window == null) {
            backend?.detachHost()
            return
        }
        FragmentPipRegistry.registerWindow(window, this)
        val active = ensureBackend() ?: return
        active.attachHost(window, fragment, this)
        active.syncSessionState()
    }

    fun unbindWindow() {
        val previous = boundWindow
        boundWindow = null
        if (previous != null) {
            FragmentPipRegistry.unregisterWindow(previous, this)
        }
        backend?.detachHost()
    }

    fun enterPip(): Boolean {
        if (isDisposed || fragment.isDisposed) return false
        if (!isEffectivelyEnabled()) return false
        val target = resolveEffectiveTarget() ?: return false

        if (boundWindow == null) {
            val discovered = fragment.hostingWindow ?: (fragment.application ?: Application.current)?.mainWindow
            if (discovered != null) {
                bindWindow(discovered)
            }
        }

        val active = ensureBackend() ?: return false
        if (!active.supportsComponent(target)) return false
        if (active.isInPipMode || isInPipMode) return true

        active.attachHost(boundWindow, fragment, this)
        active.syncSessionState()

        markEnteringPip(true)
        val effective = effectiveHelper()
        if (effective !== this) {
            effective.markEnteringPip(true)
        }

        val entered = active.enterPip()
        if (!entered) {
            markEnteringPip(false)
            if (effective !== this) {
                effective.markEnteringPip(false)
            }
        }
        return entered
    }

    fun exitPip(): Boolean {
        val active = backend ?: return false
        markEnteringPip(false)
        val effective = effectiveHelper()
        if (effective !== this) {
            effective.markEnteringPip(false)
        }
        return active.exitPip()
    }

    fun togglePip(): Boolean =
        if (isInPipMode || backend?.isInPipMode == true) exitPip() else enterPip()

    fun notifyStateChanged() {
        if (isDisposed || fragment.isDisposed) return
        val active = backend ?: run {
            val window = boundWindow
                ?: fragment.hostingWindow
                ?: (fragment.application ?: Application.current)?.mainWindow
            if (window != null) {
                bindWindow(window)
            }
            backend
        }
        active?.syncSessionState()
    }

    fun dispatchPipModeChangedFromBackend(inPip: Boolean) {
        isInPipMode = inPip
        isEnteringPip = false
        onPipModeChanged?.invoke(inPip)
        fragment.onPictureInPictureModeChanged(inPip)

        val effective = effectiveHelper()
        if (effective !== this) {
            effective.isInPipMode = inPip
            effective.isEnteringPip = false
            effective.onPipModeChanged?.invoke(inPip)
            effective.fragment.onPictureInPictureModeChanged(inPip)
        }
    }

    fun dispatchCloseRequestedFromBackend() {
        if (!resolveEffectiveConfig().pauseOnCloseButton) return
        val effective = effectiveHelper()
        val pauseHandler = effective.onPauseRequested ?: onPauseRequested
        pauseHandler?.invoke()
    }

    override fun dispose() {
        if (isDisposed) return
        if (isInPipMode || backend?.isInPipMode == true) {
            runCatching { exitPip() }
        }
        isDisposed = true
        unbindWindow()
        FragmentPipRegistry.unregister(fragment, this)
        backend = null
    }

    private fun ensureBackend(): PipBackend? {
        val existing = backend
        if (existing != null) return existing
        val created = PipRegistry.createBackend() ?: return null
        backend = created
        return created
    }
}

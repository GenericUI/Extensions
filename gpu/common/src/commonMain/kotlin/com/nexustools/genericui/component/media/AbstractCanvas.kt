package com.nexustools.genericui.component.media

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.ext.gpu.VBlankWatcher
import com.nexustools.genericui.nativeimpl.media.AbstractCanvasNativeImpl

/**
 * Non-unified abstract base class for all GenericUI hardware-accelerated canvases:
 * - `Canvas2D`: Immediate-mode 2D vector/raster drawing canvas backed by a double-buffered `ImageBuffer`.
 * - `GLCanvas`: Dedicated OpenGL ES 2.0/3.0/3.2 3D/2D rendering surface (using Google ANGLE on Windows).
 * - `VKCanvas`: Dedicated Vulkan 1.1/1.2/1.3 rendering surface.
 *
 * Provides [registerVBlankRender] (which on `:web` registers `requestAnimationFrame` over and over
 * on each render frame, and on native platforms hooks into hardware VBlank/VSync) and a high-precision
 * [VBlankTimer] reporting `deltaMs` (milliseconds elapsed since the last render) so scripts and engines
 * can accurately animate within the render callback.
 */
abstract class AbstractCanvas<C> : AbstractComponent() {
    val vblankTimer: VBlankTimer = VBlankTimer()

    private val vblankCallbacks = mutableListOf<RegisteredCallback<C>>()
    private var vblankLoopRunning = false

    var pointerEventsPassThrough: Boolean = false
        set(value) {
            field = value
            typedCanvasNative()?.setPointerEventsPassThrough(value)
        }

    val surfaceWidth: Int
        get() = typedCanvasNative()?.getSurfaceWidth() ?: widthPx ?: 0

    val surfaceHeight: Int
        get() = typedCanvasNative()?.getSurfaceHeight() ?: heightPx ?: 0

    val devicePixelRatio: Float
        get() = typedCanvasNative()?.getDevicePixelRatio() ?: 1f

    val lastFrameDeltaMs: Double
        get() = vblankTimer.lastDeltaMs

    val currentFps: Double
        get() = vblankTimer.smoothedFps

    /**
     * Registers a [VBlankRenderCallback] that fires on every VBlank / VSync interval, passing
     * `(context, deltaMs, totalElapsedMs, frameNumber)`.
     *
     * On `:web`, the underlying implementation re-registers `window.requestAnimationFrame` on each frame.
     */
    fun registerVBlankRender(callback: VBlankRenderCallback<C>): VBlankSubscription {
        val entry = RegisteredCallback(callback)
        vblankCallbacks.add(entry)
        updateNativeVBlankLoopState()
        return object : VBlankSubscription {
            override val isActive: Boolean get() = entry.active && !entry.disposed
            override fun pause() {
                entry.active = false
                updateNativeVBlankLoopState()
            }
            override fun resume() {
                if (!entry.disposed) {
                    entry.active = true
                    updateNativeVBlankLoopState()
                }
            }
            override fun dispose() {
                entry.disposed = true
                entry.active = false
                vblankCallbacks.remove(entry)
                updateNativeVBlankLoopState()
            }
        }
    }

    /**
     * Requests a single on-demand render frame without starting a continuous VBlank loop.
     */
    fun requestRender() {
        val impl = typedCanvasNative()
        if (impl != null) {
            impl.requestSingleRender()
        } else {
            dispatchVBlankFrame(null)
        }
    }

    /**
     * Captures the current canvas framebuffer as tightly-packed 32-bit RGBA pixels.
     */
    fun captureRgbaBytes(): ByteArray =
        typedCanvasNative()?.captureFrameToRgba() ?: ByteArray(0)

    /**
     * Captures the current canvas framebuffer as a compressed JPEG byte array.
     */
    fun captureJpegBytes(quality: Int = 80): ByteArray =
        typedCanvasNative()?.captureFrameToJpeg(quality) ?: ByteArray(0)

    /**
     * Invoked by platform backends (or tests) on each VBlank tick.
     *
     * Sets [VBlankWatcher.isInVBlankCallback] to `true` while executing registered VBlank render
     * callbacks and flushes any queued end-of-callback surface presentation actions immediately
     * after each callback completes.
     */
    fun dispatchVBlankFrame(explicitTimestampMs: Double? = null) {
        val activeEntries = vblankCallbacks.filter { it.active && !it.disposed }
        if (activeEntries.isEmpty()) return
        val context = resolveRenderingContext() ?: return
        val (deltaMs, totalElapsedMs) = vblankTimer.stepFrame(explicitTimestampMs)
        val frameNum = vblankTimer.frameNumber

        val previousInVBlank = VBlankWatcher.isInVBlankCallback
        VBlankWatcher.isInVBlankCallback = true
        try {
            for (entry in activeEntries) {
                if (entry.active && !entry.disposed) {
                    entry.callback.onRender(context, deltaMs, totalElapsedMs, frameNum)
                    VBlankWatcher.flushEndOfCallbackActions()
                }
            }
            VBlankWatcher.flushEndOfCallbackActions()
        } finally {
            VBlankWatcher.isInVBlankCallback = previousInVBlank
        }
    }

    protected open fun resolveRenderingContext(): C? =
        typedCanvasNative()?.getRenderingContext()

    @Suppress("UNCHECKED_CAST")
    protected fun typedCanvasNative(): AbstractCanvasNativeImpl<*, C>? =
        nativeImpl as? AbstractCanvasNativeImpl<*, C>

    protected fun updateNativeVBlankLoopState() {
        val shouldRun = vblankCallbacks.any { it.active && !it.disposed }
        val impl = typedCanvasNative() ?: return
        if (shouldRun != vblankLoopRunning) {
            vblankLoopRunning = shouldRun
            if (shouldRun) {
                vblankTimer.reset()
                impl.setVBlankLoopActive(true) { ts -> dispatchVBlankFrame(ts) }
            } else {
                impl.setVBlankLoopActive(false) {}
            }
        }
    }

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedCanvasNative() ?: return
        impl.setPointerEventsPassThrough(pointerEventsPassThrough)
        vblankLoopRunning = false
        updateNativeVBlankLoopState()
    }

    private class RegisteredCallback<C>(
        val callback: VBlankRenderCallback<C>,
        var active: Boolean = true,
        var disposed: Boolean = false
    )
}

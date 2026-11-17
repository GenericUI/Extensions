package com.nexustools.genericui.ext.engine.animation

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.FramePresentTarget
import kotlin.time.TimeSource

/**
 * Binds an [AnimationScheduler] directly to the rendering thread's VBlank pre-present signal:
 * 1. `NativeImpl.setOnBeforeTargetFramePresent` on any [AbstractComponent] (zero canvas required).
 * 2. `:common`'s [FramePresentTarget.addBeforeFramePresentListener] (used by `Canvas2D` & `Canvas3D`).
 */
class VBlankAnimationDriver(
    val scheduler: AnimationScheduler = AnimationScheduler()
) {
    private var boundComponent: AbstractComponent? = null
    private var boundFramePresentTarget: FramePresentTarget? = null
    private val clockStart = TimeSource.Monotonic.markNow()
    private val postEvaluateListeners = mutableListOf<(frameIndex: Long, deltaSeconds: Float, timestampMs: Double) -> Unit>()
    private var lastFrameTimestampMs: Double? = null

    var evaluatedFrameCount: Long = 0L
        private set

    private val framePresentListener: (Long, Double) -> Unit = { frameIndex, timestampMs ->
        onBeforeFramePresent(frameIndex, timestampMs)
    }

    private fun currentMonotonicMs(): Double =
        clockStart.elapsedNow().inWholeMicroseconds / 1000.0

    fun addPostEvaluateListener(listener: (frameIndex: Long, deltaSeconds: Float, timestampMs: Double) -> Unit) {
        if (listener !in postEvaluateListeners) {
            postEvaluateListeners.add(listener)
        }
    }

    fun removePostEvaluateListener(listener: (frameIndex: Long, deltaSeconds: Float, timestampMs: Double) -> Unit) {
        postEvaluateListeners.remove(listener)
    }

    /**
     * Binds VBlank animation evaluation directly onto [component]'s `NativeImpl.setOnBeforeTargetFramePresent`.
     */
    fun bindToComponent(component: AbstractComponent) {
        if (boundComponent === component) {
            syncComponentNativeHook()
            return
        }
        unbindComponent()
        boundComponent = component
        syncComponentNativeHook()
    }

    /**
     * Re-attaches the native pre-present hook if [boundComponent] was realized after [bindToComponent].
     */
    fun syncComponentNativeHook() {
        val comp = boundComponent ?: return
        comp.nativeImpl?.setOnBeforeTargetFramePresent { swapCount, tsMs ->
            val resolvedTs = tsMs ?: currentMonotonicMs()
            onBeforeFramePresent(swapCount, resolvedTs)
        }
    }

    /**
     * Binds VBlank animation evaluation directly to a [FramePresentTarget] (`Canvas2D`, `Canvas3D`).
     */
    fun bindToFramePresentTarget(target: FramePresentTarget) {
        if (boundFramePresentTarget === target) return
        boundFramePresentTarget?.removeBeforeFramePresentListener(framePresentListener)
        boundFramePresentTarget = target
        target.addBeforeFramePresentListener(framePresentListener)
    }

    /**
     * Evaluates all active [AnimationScheduler] clips on the rendering thread immediately before
     * frame presentation and notifies post-evaluation listeners.
     */
    fun onBeforeFramePresent(frameIndex: Long, timestampMs: Double) {
        val resolvedTs = if (timestampMs.isNaN()) currentMonotonicMs() else timestampMs
        val prev = lastFrameTimestampMs
        val deltaSec = if (prev != null && resolvedTs >= prev) {
            ((resolvedTs - prev) / 1000.0).toFloat()
        } else {
            0f
        }
        lastFrameTimestampMs = resolvedTs
        evaluatedFrameCount++
        scheduler.evaluateAtVBlank(frameIndex, resolvedTs)
        if (postEvaluateListeners.isNotEmpty()) {
            for (listener in postEvaluateListeners.toList()) {
                listener(frameIndex, deltaSec, resolvedTs)
            }
        }
    }

    fun unbindComponent() {
        boundComponent?.nativeImpl?.setOnBeforeTargetFramePresent(null)
        boundComponent = null
    }

    fun unbindFramePresentTarget() {
        boundFramePresentTarget?.removeBeforeFramePresentListener(framePresentListener)
        boundFramePresentTarget = null
    }

    fun unbind() {
        unbindComponent()
        unbindFramePresentTarget()
    }
}

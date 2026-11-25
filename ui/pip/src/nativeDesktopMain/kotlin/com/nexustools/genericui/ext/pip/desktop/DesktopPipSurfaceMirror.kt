package com.nexustools.genericui.ext.pip.desktop

import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import kotlin.math.abs
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Mirrors an active [DesktopNativeWidget]'s [com.nexustools.genericui.desktop.core.DesktopInteractiveSurfaceSpec]
 * onto a PiP mirror widget so hardware/raster video and 3D surfaces keep rendering smoothly
 * even when the owning window is minimized, while deduplicating simultaneous macOS VBlank callbacks.
 */
class DesktopPipSurfaceMirror {

    private var sourceWidget: DesktopNativeWidget? = null
    private var mirroredWidget: DesktopNativeWidget? = null
    private var savedSourceVBlank: ((Double?) -> Unit)? = null
    private var installedSourceWrapper: ((Double?) -> Unit)? = null

    private var lastDispatchedTimestampMs: Double? = null
    private var lastDispatchMark: TimeMark? = null

    val isMounted: Boolean
        get() = sourceWidget != null && mirroredWidget != null

    fun mount(
        source: DesktopNativeWidget,
        mirror: DesktopNativeWidget
    ): Boolean {
        val spec = source.interactiveSurfaceSpec ?: return false
        unmount()

        sourceWidget = source
        mirroredWidget = mirror
        lastDispatchedTimestampMs = null
        lastDispatchMark = null

        spec.attachTo(mirror)

        val originalVBlank = source.vblankDispatch
        savedSourceVBlank = originalVBlank
        if (originalVBlank != null) {
            val dedupDispatcher: (Double?) -> Unit = { timestampMs ->
                dispatchDeduplicated(timestampMs, originalVBlank)
            }
            installedSourceWrapper = dedupDispatcher
            source.vblankDispatch = dedupDispatcher
            mirror.vblankDispatch = dedupDispatcher
        }
        return true
    }

    fun unmount() {
        val src = sourceWidget
        val mirror = mirroredWidget
        val originalVBlank = savedSourceVBlank
        val wrapper = installedSourceWrapper

        sourceWidget = null
        mirroredWidget = null
        savedSourceVBlank = null
        installedSourceWrapper = null
        lastDispatchedTimestampMs = null
        lastDispatchMark = null

        if (mirror != null && !mirror.disposed) {
            mirror.vblankDispatch = null
            mirror.interactiveSurfaceSpec = null
            for (key in MIRRORED_ATTRIBUTE_KEYS) {
                mirror.attributes.remove(key)
            }
        }
        if (src != null && !src.disposed && wrapper != null && src.vblankDispatch === wrapper) {
            src.vblankDispatch = originalVBlank
        }
    }

    private fun dispatchDeduplicated(
        timestampMs: Double?,
        target: (Double?) -> Unit
    ) {
        val nowMark = TimeSource.Monotonic.markNow()
        val prevTs = lastDispatchedTimestampMs
        val prevMark = lastDispatchMark

        val isSameTimestamp = timestampMs != null &&
            prevTs != null &&
            abs(timestampMs - prevTs) <= TIMESTAMP_EPSILON_MS
        val isWithinDedupWindow = timestampMs == null &&
            prevMark != null &&
            prevMark.elapsedNow().inWholeMilliseconds < MIN_VBLANK_INTERVAL_MS

        if (isSameTimestamp || isWithinDedupWindow) {
            return
        }

        if (prevMark != null && prevMark.elapsedNow().inWholeMilliseconds < MIN_VBLANK_INTERVAL_MS && prevTs == timestampMs) {
            return
        }

        lastDispatchedTimestampMs = timestampMs
        lastDispatchMark = nowMark
        target.invoke(timestampMs)
    }

    private companion object {
        const val TIMESTAMP_EPSILON_MS = 0.25
        const val MIN_VBLANK_INTERVAL_MS = 8L
        val MIRRORED_ATTRIBUTE_KEYS = listOf(
            "surfaceWidth",
            "surfaceHeight",
            "swapCount",
            "preferHardwareStretchBlit",
            "suppressDefaultBorder",
            "onBeforePresentFrame",
            "defaultAspectRatio",
            "contentFitMode",
            "contentAspectRatio",
            "captureBgraPtr",
            "captureBgra",
            "captureRgba",
            "resizeSurface"
        )
    }
}

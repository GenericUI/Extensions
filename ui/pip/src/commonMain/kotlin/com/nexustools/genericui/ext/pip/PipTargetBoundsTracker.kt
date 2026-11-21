package com.nexustools.genericui.ext.pip

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.window.IWindow
import com.nexustools.genericui.window.WindowAspectRatioHelper
import kotlin.math.roundToInt

/**
 * Tracks a target [AbstractComponent]'s screen-space rectangle inside its owning [IWindow]
 * while the owner window is visibly open ([WindowState.NORMAL] or [WindowState.MAXIMIZED]),
 * ignoring off-screen minimized sentinel coordinates (such as `-32000, -32000` on Win32).
 */
class PipTargetBoundsTracker {

    var ownerWindow: IWindow? = null
        private set

    var target: AbstractComponent? = null
        private set

    var aspectRatio: Double = DEFAULT_ASPECT_RATIO
        private set

    var lastVisibleTargetScreenRect: Rect? = null
        private set

    var lastVisibleWindowBounds: Rect? = null
        private set

    fun configure(
        ownerWindow: IWindow?,
        target: AbstractComponent?,
        aspectRatioNumerator: Int = 16,
        aspectRatioDenominator: Int = 9
    ) {
        this.ownerWindow = ownerWindow
        this.target = target
        updateAspectRatio(aspectRatioNumerator, aspectRatioDenominator)
        captureIfVisible()
    }

    fun updateAspectRatio(numerator: Int, denominator: Int) {
        aspectRatio = if (numerator > 0 && denominator > 0) {
            numerator.toDouble() / denominator.toDouble()
        } else {
            DEFAULT_ASPECT_RATIO
        }
    }

    fun captureIfVisible(): Rect? {
        val window = ownerWindow ?: return lastVisibleTargetScreenRect
        val state = window.getWindowState()
        if (state != WindowState.NORMAL && state != WindowState.MAXIMIZED) {
            return lastVisibleTargetScreenRect
        }
        val winBounds = window.windowBounds
        if (!isValidVisibleWindowBounds(winBounds)) {
            return lastVisibleTargetScreenRect
        }
        lastVisibleWindowBounds = winBounds

        val targetComponent = target ?: return lastVisibleTargetScreenRect
        val computed = computeScreenRect(targetComponent, window, winBounds)
        lastVisibleTargetScreenRect = computed
        return computed
    }

    fun resolveCurrentOrCachedScreenRect(config: PipSessionConfig): Rect {
        captureIfVisible()?.let { return it }
        val fallbackBounds = lastVisibleWindowBounds ?: Rect(80, 80, 1280, 800)
        val clampedSize = clampPipSize(
            requestedWidth = config.preferredWidthPx,
            requestedHeight = config.preferredHeightPx,
            numerator = config.aspectRatioNumerator,
            denominator = config.aspectRatioDenominator
        )
        return Rect(
            x = fallbackBounds.x + ((fallbackBounds.width - clampedSize.width) / 2).coerceAtLeast(0),
            y = fallbackBounds.y + ((fallbackBounds.height - clampedSize.height) / 2).coerceAtLeast(0),
            width = clampedSize.width,
            height = clampedSize.height
        )
    }

    fun resolveDefaultDockRect(config: PipSessionConfig, workArea: Rect? = null): Rect {
        val area = workArea
            ?: GenericUI.activeFactory?.getPrimaryMonitorBounds()
            ?: lastVisibleWindowBounds?.takeIf { it.width >= 320 && it.height >= 240 }
            ?: Rect(0, 0, 1280, 800)
        val clamped = clampPipSize(
            requestedWidth = config.preferredWidthPx,
            requestedHeight = config.preferredHeightPx,
            numerator = config.aspectRatioNumerator,
            denominator = config.aspectRatioDenominator
        )
        val margin = config.screenMarginPx.coerceAtLeast(0)
        val dockX = (area.x + area.width - clamped.width - margin).coerceIn(
            minimumValue = area.x + margin.coerceAtMost((area.width - clamped.width).coerceAtLeast(0)),
            maximumValue = area.x + (area.width - clamped.width).coerceAtLeast(0)
        )
        val dockY = (area.y + area.height - clamped.height - margin).coerceIn(
            minimumValue = area.y + margin.coerceAtMost((area.height - clamped.height).coerceAtLeast(0)),
            maximumValue = area.y + (area.height - clamped.height).coerceAtLeast(0)
        )
        return Rect(dockX, dockY, clamped.width, clamped.height)
    }

    fun reset() {
        ownerWindow = null
        target = null
        lastVisibleTargetScreenRect = null
        lastVisibleWindowBounds = null
        aspectRatio = DEFAULT_ASPECT_RATIO
    }

    private fun computeScreenRect(
        targetComponent: AbstractComponent,
        window: IWindow,
        winBounds: Rect
    ): Rect {
        val targetBounds = targetComponent.getBounds()
        var offsetX = 0
        var offsetY = 0
        var cur: AbstractComponent? = targetComponent
        val contentRoot = window.getContent()
        while (cur != null && cur !== window) {
            val b = cur.getBounds()
            offsetX += b.x
            offsetY += b.y
            if (cur === contentRoot) break
            cur = cur.parent
        }

        val pref = targetComponent.getPreferredSize()
        val hasMeasuredSize = targetBounds.width > 0 && targetBounds.height > 0
        val resolvedSize = if (hasMeasuredSize) {
            Size(targetBounds.width, targetBounds.height)
        } else {
            val baseW = when {
                targetBounds.width > 0 -> targetBounds.width
                pref != null && pref.width > 0 -> pref.width
                else -> (winBounds.width * 0.5f).roundToInt().coerceAtLeast(DEFAULT_MIN_WIDTH)
            }
            val baseH = when {
                targetBounds.height > 0 -> targetBounds.height
                pref != null && pref.height > 0 -> pref.height
                else -> (baseW.toDouble() / aspectRatio).roundToInt().coerceAtLeast(DEFAULT_MIN_HEIGHT)
            }
            WindowAspectRatioHelper.clampClientSize(
                size = Size(baseW, baseH),
                aspectRatio = aspectRatio,
                minSize = Size(DEFAULT_MIN_WIDTH, DEFAULT_MIN_HEIGHT),
                preferWidth = true
            )
        }

        return Rect(
            x = winBounds.x + offsetX,
            y = winBounds.y + offsetY,
            width = resolvedSize.width,
            height = resolvedSize.height
        )
    }

    private fun clampPipSize(
        requestedWidth: Int,
        requestedHeight: Int,
        numerator: Int,
        denominator: Int
    ): Size {
        val ratio = if (numerator > 0 && denominator > 0) {
            numerator.toDouble() / denominator.toDouble()
        } else {
            aspectRatio
        }
        val baseW = requestedWidth.takeIf { it > 0 } ?: 420
        val baseH = requestedHeight.takeIf { it > 0 } ?: (baseW.toDouble() / ratio).roundToInt()
        return WindowAspectRatioHelper.clampClientSize(
            size = Size(baseW, baseH),
            aspectRatio = ratio,
            minSize = Size(DEFAULT_MIN_WIDTH, DEFAULT_MIN_HEIGHT),
            preferWidth = true
        )
    }

    private fun isValidVisibleWindowBounds(bounds: Rect): Boolean =
        bounds.width > 0 &&
            bounds.height > 0 &&
            bounds.x > OFFSCREEN_SENTINEL_THRESHOLD &&
            bounds.y > OFFSCREEN_SENTINEL_THRESHOLD

    private companion object {
        const val DEFAULT_ASPECT_RATIO = 16.0 / 9.0
        const val DEFAULT_MIN_WIDTH = 160
        const val DEFAULT_MIN_HEIGHT = 90
        const val OFFSCREEN_SENTINEL_THRESHOLD = -16000
    }
}

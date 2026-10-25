package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.ext.alien.AlienExtensionLayerMode
import com.nexustools.genericui.ext.alien.AlienRenderSession

/**
 * Scene-graph proxy node wrapping a non-Alien [NativeImpl] (`UnalienContainer` or extension widget
 * such as `GLCanvas`, `VKCanvas`, `VideoPlayer`, `AudioAnalyzer`, or `WebView`) inside an
 * `AlienContainer` or `AlienFragment`.
 *
 * - Attaches [wrappedNativeImpl] to the host OS container **below** the `Canvas2D`.
 * - Cuts a transparent hole into the `Canvas2D` (`CUT_TRANSPARENT_HOLE`) or allows drawing over (`DRAW_OVER`).
 * - Routes passthrough mouse/pointer/wheel/keyboard events to [wrappedNativeImpl] when no higher-Z
 *   Alien component covers the hit point.
 */
class AlienUnderlayExtensionSlotNode(
    val wrappedNativeImpl: NativeImpl<*>,
    layerMode: AlienExtensionLayerMode = AlienExtensionLayerMode.CUT_TRANSPARENT_HOLE,
    session: AlienRenderSession? = null
) : AlienImpl(
    componentClassPrefix = wrappedNativeImpl.componentClassPrefix,
    tagName = "alien-underlay-slot",
    session = session
) {
    var passthroughPointerDownCount: Int = 0
        private set

    var passthroughPointerMoveCount: Int = 0
        private set

    var passthroughPointerUpCount: Int = 0
        private set

    var passthroughWheelCount: Int = 0
        private set

    var lastPassthroughPoint: Pair<Float, Float>? = null
        private set

    init {
        node.wrappedUnderlayNativeImpl = wrappedNativeImpl
        node.underlayLayerMode = resolveInitialLayerMode(layerMode)

        val initialBounds = wrappedNativeImpl.getBounds()
        if (initialBounds.width > 0 && initialBounds.height > 0) {
            node.bounds = initialBounds
        }

        // Default passthrough listeners track and forward events to the underlay native widget
        node.onPointerDown = { ev: PointerEvent ->
            passthroughPointerDownCount++
            lastPassthroughPoint = ev.x to ev.y
        }
        node.onPointerMove = { ev: PointerEvent ->
            passthroughPointerMoveCount++
            lastPassthroughPoint = ev.x to ev.y
        }
        node.onPointerUp = { ev: PointerEvent ->
            passthroughPointerUpCount++
            lastPassthroughPoint = ev.x to ev.y
        }
        node.onWheel = { _: WheelEvent ->
            passthroughWheelCount++
        }
        node.onKeyDown = { _: KeyEvent -> }
        node.onKeyUp = { _: KeyEvent -> }
    }

    var layerMode: AlienExtensionLayerMode
        get() = node.underlayLayerMode
        set(value) {
            if (node.underlayLayerMode != value) {
                node.underlayLayerMode = value
                node.invalidateBounds()
            }
        }

    private fun resolveInitialLayerMode(defaultMode: AlienExtensionLayerMode): AlienExtensionLayerMode {
        val attr = node.attributes["data-alien-layer-mode"]
        return if (attr != null) {
            AlienExtensionLayerMode.entries.firstOrNull { it.name.equals(attr, ignoreCase = true) } ?: defaultMode
        } else {
            defaultMode
        }
    }

    override fun dispose() {
        node.session?.unregisterUnderlaySlot(this)
        super.dispose()
    }
}

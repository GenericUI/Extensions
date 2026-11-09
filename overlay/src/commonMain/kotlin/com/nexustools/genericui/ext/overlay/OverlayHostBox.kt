package com.nexustools.genericui.ext.overlay

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.LayerConstraint
import com.nexustools.genericui.component.layout.LayeredContainer
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.FramePresentTarget
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssLength

/**
 * Pure, background-free [LayeredContainer] implementing [OverlayHost].
 *
 * Mounts [targetComponent] as a full-bleed base layer (`zIndex = 0`) and mounts
 * [overlay] inside [overlayRoot] as a full-bleed top layer (`zIndex = 10`) without
 * any default preset overlay, background color, or scrim.
 */
open class OverlayHostBox(
    content: AbstractComponent? = null,
    initialOverlay: AbstractOverlay? = null
) : LayeredContainer("genericui:overlay-host", "gui-overlay-host"), OverlayHost {

    private var fallbackTarget: Container? = if (content == null) {
        Container("genericui-overlay-host-target").apply {
            style.width = CssLength.percent(100f)
            style.height = CssLength.percent(100f)
            style.backgroundColor = RgbaColor.TRANSPARENT
        }
    } else {
        null
    }

    var contentComponent: AbstractComponent? = content
        private set

    override val targetComponent: AbstractComponent
        get() = contentComponent ?: fallbackTarget!!

    override val overlayRoot: LayeredContainer = LayeredContainer(
        tagName = "genericui:overlay-host-root",
        classPrefix = "gui-overlay-host-root"
    ).apply {
        style.width = CssLength.percent(100f)
        style.height = CssLength.percent(100f)
        style.backgroundColor = RgbaColor.TRANSPARENT
    }

    private var overlayBacking: AbstractOverlay? = null

    override var overlay: AbstractOverlay?
        get() = overlayBacking
        set(value) {
            if (overlayBacking === value) return
            if (value == null) {
                detachOverlay()
            } else {
                attachOverlay(value)
            }
        }

    override var activeOverlay: AbstractOverlay?
        get() = overlay
        set(value) {
            overlay = value
        }

    private var stateBacking: OverlayState = OverlayState()
    override var state: OverlayState
        get() = stateBacking
        set(value) {
            stateBacking = value
            if (value.isInPictureInPictureMode != pipBacking) {
                pipBacking = value.isInPictureInPictureMode
                overlayRoot.isVisible = !pipBacking
            }
            overlay?.updateState(value)
        }

    private var actionsBacking: OverlayActions = OverlayActions()
    override var actions: OverlayActions
        get() = actionsBacking
        set(value) {
            actionsBacking = value
            overlay?.updateActions(value)
        }

    private var pipBacking: Boolean = false
    override var isInPictureInPictureMode: Boolean
        get() = pipBacking
        set(value) {
            setPictureInPictureMode(value)
        }

    private var themeColorHexBacking: String? = null
    override var themeColorHex: String?
        get() = themeColorHexBacking ?: OverlayExtension.effectiveDefaultThemeColorHex()
        set(value) {
            themeColorHexBacking = value
            overlay?.themeColorHex = value
        }

    private var boundPresentTarget: FramePresentTarget? = null
    private val beforePresentListener: (Long, Double) -> Unit = { frameIndex, timestampMs ->
        dispatchBeforeTargetFramePresent(frameIndex, timestampMs)
    }

    init {
        setAttribute("data-overlay-host", "true")
        overlayRoot.setAttribute("data-overlay-root", "true")
        style.width = CssLength.percent(100f)
        style.height = CssLength.percent(100f)
        style.backgroundColor = RgbaColor.TRANSPARENT
        addLayer(targetComponent, LayerConstraint.fill(zIndex = 0))
        addLayer(overlayRoot, LayerConstraint.fill(zIndex = 10, passThroughPointerEvents = true))
        val initW = content?.widthPx ?: 0
        val initH = content?.heightPx ?: 0
        if (initW > 0 && initH > 0) {
            overlayRoot.widthPx = initW
            overlayRoot.heightPx = initH
        }
        if (content is FramePresentTarget) {
            bindToFramePresentTarget(content)
        }
        if (initialOverlay != null) {
            attachOverlay(initialOverlay)
        }
    }

    protected override fun onAttachNative(impl: NativeImpl<*>) {
        super.onAttachNative(impl)
        impl.setOnBeforeTargetFramePresent { sc, ts ->
            dispatchBeforeTargetFramePresent(sc, ts ?: 0.0)
        }
        overlayRoot.ensureNativeImpl()?.setOnBeforeTargetFramePresent { sc, ts ->
            dispatchBeforeTargetFramePresent(sc, ts ?: 0.0)
        }
    }

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        syncWithTargetResize(viewportWidth, viewportHeight)
    }

    fun setContent(component: AbstractComponent?) {
        val currentTarget = targetComponent
        removeLayer(currentTarget)
        contentComponent = component
        if (component is FramePresentTarget) {
            bindToFramePresentTarget(component)
        } else {
            boundPresentTarget?.removeBeforeFramePresentListener(beforePresentListener)
            boundPresentTarget = null
        }
        if (component == null && fallbackTarget == null) {
            fallbackTarget = Container("genericui-overlay-host-target").apply {
                style.width = CssLength.percent(100f)
                style.height = CssLength.percent(100f)
                style.backgroundColor = RgbaColor.TRANSPARENT
            }
        }
        addLayer(targetComponent, LayerConstraint.fill(zIndex = 0))
        setLayerConstraint(
            overlayRoot,
            LayerConstraint.fill(zIndex = 10, passThroughPointerEvents = overlay == null)
        )
        val w = component?.widthPx ?: 0
        val h = component?.heightPx ?: 0
        if (w > 0 && h > 0) {
            syncWithTargetResize(w, h)
        }
    }

    override fun attachOverlay(overlay: AbstractOverlay) {
        val current = this.overlayBacking
        if (current === overlay) return
        if (current != null) {
            detachOverlay()
        }
        overlayBacking = overlay
        if (themeColorHexBacking != null) {
            overlay.themeColorHex = themeColorHexBacking
        }
        val targetW = overlayRoot.widthPx ?: contentComponent?.widthPx ?: widthPx ?: 0
        val targetH = overlayRoot.heightPx ?: contentComponent?.heightPx ?: heightPx ?: 0
        if (targetW > 0 && targetH > 0) {
            overlay.notifyTargetResize(targetW, targetH)
        }
        overlayRoot.addLayer(overlay, LayerConstraint.fill(zIndex = 10))
        setLayerConstraint(
            overlayRoot,
            LayerConstraint.fill(zIndex = 10, passThroughPointerEvents = false)
        )
        overlay.onAttachToHost(this)
        if (pipBacking) {
            overlay.setPictureInPictureMode(true)
            overlayRoot.isVisible = false
        }
    }

    override fun detachOverlay() {
        val current = this.overlayBacking ?: return
        overlayBacking = null
        current.onDetachFromHost()
        overlayRoot.removeLayer(current)
        setLayerConstraint(
            overlayRoot,
            LayerConstraint.fill(zIndex = 10, passThroughPointerEvents = true)
        )
    }

    override fun syncWithTargetRender(frameTimestampMs: Long) {
        val targetW = overlayRoot.widthPx ?: contentComponent?.widthPx ?: widthPx ?: 0
        val targetH = overlayRoot.heightPx ?: contentComponent?.heightPx ?: heightPx ?: 0
        overlay?.notifyTargetRenderFrame(frameTimestampMs, targetW, targetH)
    }

    override fun dispatchBeforeTargetFramePresent(frameIndex: Long, timestampMs: Double) {
        overlay?.onBeforeTargetFramePresent(frameIndex, timestampMs)
        syncWithTargetRender(timestampMs.toLong().coerceAtLeast(0L))
    }

    override fun bindToFramePresentTarget(target: FramePresentTarget) {
        if (boundPresentTarget === target) return
        boundPresentTarget?.removeBeforeFramePresentListener(beforePresentListener)
        boundPresentTarget = target
        target.addBeforeFramePresentListener(beforePresentListener)
    }

    override fun syncWithTargetResize(widthPx: Int, heightPx: Int) {
        if (widthPx > 0 && heightPx > 0) {
            overlayRoot.widthPx = widthPx
            overlayRoot.heightPx = heightPx
        }
        overlay?.notifyTargetResize(widthPx, heightPx)
    }

    override fun updateState(transform: (OverlayState) -> OverlayState) {
        state = transform(state)
    }

    override fun setPictureInPictureMode(enabled: Boolean) {
        pipBacking = enabled
        stateBacking = stateBacking.copy(isInPictureInPictureMode = enabled)
        overlayRoot.isVisible = !enabled
        overlay?.setPictureInPictureMode(enabled)
    }

    override fun dispose() {
        boundPresentTarget?.removeBeforeFramePresentListener(beforePresentListener)
        boundPresentTarget = null
        val current = overlay
        detachOverlay()
        current?.dispose()
        super.dispose()
    }
}

package com.nexustools.genericui.ext.canvas3d.dsl

import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.component.media.Canvas3DConfig
import com.nexustools.genericui.component.media.GpuRenderContext
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.canvas3d.ScrollCanvas3D
import com.nexustools.genericui.ext.canvas3d.ScrollCanvas3DListener
import com.nexustools.genericui.ext.canvas3d.overlay.Canvas3DOverlayHost
import com.nexustools.genericui.ext.canvas3d.overlay.ScrollCanvas3DOverlayHost
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.gpu.GpuAccelerationPolicy
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.overlay.AbstractOverlay

/**
 * In-place [Canvas3D] builder for OpenGL ES or Vulkan 3D rendering.
 */
fun AbstractContainer.canvas3D(
    backend: GpuBackendType = GpuBackendType.VULKAN,
    acceleration: GpuAccelerationPolicy = GpuAccelerationPolicy.HARDWARE_PREFERRED,
    config: Canvas3DConfig = Canvas3DConfig(),
    width: Int = 800,
    height: Int = 600,
    id: String? = null,
    classes: String? = null,
    onRender: ((GpuRenderContext, Double, Double, Long) -> Unit)? = null,
    block: (@GenericUiDslMarker Canvas3D.() -> Unit)? = null
): Canvas3D {
    val canvas = Canvas3D(
        backend = backend,
        acceleration = acceleration,
        config = config,
        width = width,
        height = height
    )
    if (!classes.isNullOrBlank()) {
        canvas.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        canvas.id = id
    }
    if (onRender != null) {
        canvas.registerVBlankRender { ctx, deltaMs, elapsedMs, frameIdx ->
            onRender(ctx, deltaMs, elapsedMs, frameIdx)
        }
    }
    if (block != null) {
        canvas.block()
    }
    addChild(canvas)
    return canvas
}

/**
 * In-place [ScrollCanvas3D] builder with configurable viewport and virtual content dimensions
 * and optional [onScroll] and [onRender] listeners.
 */
fun AbstractContainer.scrollCanvas3D(
    viewportWidth: Int = 800,
    viewportHeight: Int = 600,
    contentWidth: Int = viewportWidth,
    contentHeight: Int = viewportHeight,
    backend: GpuBackendType = GpuBackendType.VULKAN,
    acceleration: GpuAccelerationPolicy = GpuAccelerationPolicy.HARDWARE_PREFERRED,
    config: Canvas3DConfig = Canvas3DConfig(),
    id: String? = null,
    classes: String? = null,
    onScroll: ScrollCanvas3DListener? = null,
    onRender: ((GpuRenderContext, Double, Double, Long) -> Unit)? = null,
    block: (@GenericUiDslMarker ScrollCanvas3D.() -> Unit)? = null
): ScrollCanvas3D {
    val scrollCanvas = ScrollCanvas3D(
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        contentWidth = contentWidth,
        contentHeight = contentHeight,
        backend = backend,
        acceleration = acceleration,
        config = config
    )
    if (!classes.isNullOrBlank()) {
        scrollCanvas.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        scrollCanvas.id = id
    }
    if (onScroll != null) {
        scrollCanvas.onScroll(onScroll)
    }
    if (onRender != null) {
        scrollCanvas.registerVBlankRender { ctx, deltaMs, elapsedMs, frameIdx ->
            onRender(ctx, deltaMs, elapsedMs, frameIdx)
        }
    }
    if (block != null) {
        scrollCanvas.block()
    }
    addChild(scrollCanvas)
    return scrollCanvas
}

/**
 * In-place [Canvas3DOverlayHost] builder wrapping an existing 3D [canvas] ([Canvas3D]
 * or any [AbstractComponent]) with an optional [overlay] (`null` by default unless explicitly supplied).
 */
fun AbstractContainer.canvas3DOverlayHost(
    canvas: AbstractComponent,
    overlay: AbstractOverlay? = null,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker Canvas3DOverlayHost.() -> Unit)? = null
): Canvas3DOverlayHost {
    val host = Canvas3DOverlayHost(canvas = canvas, initialOverlay = overlay)
    if (!classes.isNullOrBlank()) {
        host.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        host.id = id
    }
    if (block != null) {
        host.block()
    }
    addChild(host)
    return host
}

/**
 * In-place [Canvas3DOverlayHost] builder wrapping a new [Canvas3D] with an optional [overlay]
 * (`null` by default unless explicitly supplied).
 */
fun AbstractContainer.canvas3DOverlayHost(
    backend: GpuBackendType = GpuBackendType.VULKAN,
    acceleration: GpuAccelerationPolicy = GpuAccelerationPolicy.HARDWARE_PREFERRED,
    config: Canvas3DConfig = Canvas3DConfig(),
    width: Int = 800,
    height: Int = 600,
    overlay: AbstractOverlay? = null,
    id: String? = null,
    classes: String? = null,
    onRender: ((GpuRenderContext, Double, Double, Long) -> Unit)? = null,
    block: (@GenericUiDslMarker Canvas3DOverlayHost.() -> Unit)? = null
): Canvas3DOverlayHost {
    val canvas = Canvas3D(
        backend = backend,
        acceleration = acceleration,
        config = config,
        width = width,
        height = height
    )
    if (onRender != null) {
        canvas.registerVBlankRender { ctx, deltaMs, elapsedMs, frameIdx ->
            onRender(ctx, deltaMs, elapsedMs, frameIdx)
        }
    }
    return canvas3DOverlayHost(
        canvas = canvas,
        overlay = overlay,
        id = id,
        classes = classes,
        block = block
    )
}

/**
 * In-place [ScrollCanvas3DOverlayHost] builder wrapping a [ScrollCanvas3D] where `overlayRoot`
 * covers only the viewport area (`viewportWidth` x `viewportHeight`), excluding the scrollbars.
 */
fun AbstractContainer.scrollCanvas3DOverlayHost(
    viewportWidth: Int = 800,
    viewportHeight: Int = 600,
    contentWidth: Int = viewportWidth,
    contentHeight: Int = viewportHeight,
    backend: GpuBackendType = GpuBackendType.VULKAN,
    acceleration: GpuAccelerationPolicy = GpuAccelerationPolicy.HARDWARE_PREFERRED,
    config: Canvas3DConfig = Canvas3DConfig(),
    overlay: AbstractOverlay? = null,
    id: String? = null,
    classes: String? = null,
    onScroll: ScrollCanvas3DListener? = null,
    onRender: ((GpuRenderContext, Double, Double, Long) -> Unit)? = null,
    block: (@GenericUiDslMarker ScrollCanvas3DOverlayHost.() -> Unit)? = null
): ScrollCanvas3DOverlayHost {
    val scrollCanvas = ScrollCanvas3D(
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        contentWidth = contentWidth,
        contentHeight = contentHeight,
        backend = backend,
        acceleration = acceleration,
        config = config
    )
    if (onScroll != null) {
        scrollCanvas.onScroll(onScroll)
    }
    if (onRender != null) {
        scrollCanvas.registerVBlankRender { ctx, deltaMs, elapsedMs, frameIdx ->
            onRender(ctx, deltaMs, elapsedMs, frameIdx)
        }
    }
    return scrollCanvas3DOverlayHost(
        scrollCanvas = scrollCanvas,
        overlay = overlay,
        id = id,
        classes = classes,
        block = block
    )
}

/**
 * In-place [ScrollCanvas3DOverlayHost] builder wrapping an existing [scrollCanvas] with an optional [overlay]
 * positioned over the viewport area only (excluding scrollbars).
 */
fun AbstractContainer.scrollCanvas3DOverlayHost(
    scrollCanvas: ScrollCanvas3D,
    overlay: AbstractOverlay? = null,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker ScrollCanvas3DOverlayHost.() -> Unit)? = null
): ScrollCanvas3DOverlayHost {
    val host = ScrollCanvas3DOverlayHost(scrollCanvas = scrollCanvas, initialOverlay = overlay)
    if (!classes.isNullOrBlank()) {
        host.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        host.id = id
    }
    if (block != null) {
        host.block()
    }
    addChild(host)
    return host
}

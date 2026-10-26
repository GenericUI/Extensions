package com.nexustools.genericui.ext.canvas3d.dsl

import com.nexustools.genericui.component.media.GLCanvas
import com.nexustools.genericui.component.media.VKCanvas
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.canvas3d.GLCanvasExt
import com.nexustools.genericui.ext.canvas3d.ScrollCanvas3D
import com.nexustools.genericui.ext.canvas3d.ScrollCanvas3DListener
import com.nexustools.genericui.ext.canvas3d.ScrollGLCanvas
import com.nexustools.genericui.ext.canvas3d.ScrollVKCanvas
import com.nexustools.genericui.ext.canvas3d.VKCanvasExt
import com.nexustools.genericui.ext.canvas3d.overlay.Canvas3DOverlayHost
import com.nexustools.genericui.ext.canvas3d.overlay.ScrollCanvas3DOverlayHost
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.gpu.GLCanvasConfig
import com.nexustools.genericui.ext.gpu.GLContext
import com.nexustools.genericui.ext.gpu.VKCanvasConfig
import com.nexustools.genericui.ext.gpu.VKContext
import com.nexustools.genericui.ext.overlay.AbstractOverlay

/**
 * In-place [ScrollCanvas3D] builder with configurable viewport and virtual content dimensions
 * and optional [onScroll] listener.
 */
fun AbstractContainer.scrollCanvas3D(
    viewportWidth: Int = 800,
    viewportHeight: Int = 600,
    contentWidth: Int = viewportWidth,
    contentHeight: Int = viewportHeight,
    preferVulkan: Boolean = true,
    glConfig: GLCanvasConfig = GLCanvasConfig(),
    vkConfig: VKCanvasConfig = VKCanvasConfig(),
    id: String? = null,
    classes: String? = null,
    onScroll: ScrollCanvas3DListener? = null,
    block: (@GenericUiDslMarker ScrollCanvas3D.() -> Unit)? = null
): ScrollCanvas3D {
    val scrollCanvas = ScrollCanvas3D(
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        contentWidth = contentWidth,
        contentHeight = contentHeight,
        preferVulkan = preferVulkan,
        glConfig = glConfig,
        vkConfig = vkConfig
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
    if (block != null) {
        scrollCanvas.block()
    }
    addChild(scrollCanvas)
    return scrollCanvas
}

/**
 * In-place [ScrollGLCanvas] OpenGL ES 3D scrollable canvas builder.
 */
fun AbstractContainer.scrollGlCanvas(
    config: GLCanvasConfig = GLCanvasConfig(),
    viewportWidth: Int = 800,
    viewportHeight: Int = 600,
    contentWidth: Int = viewportWidth,
    contentHeight: Int = viewportHeight,
    id: String? = null,
    classes: String? = null,
    onScroll: ScrollCanvas3DListener? = null,
    onRender: ((GLContext, Double, Double, Long) -> Unit)? = null,
    block: (@GenericUiDslMarker ScrollGLCanvas.() -> Unit)? = null
): ScrollGLCanvas {
    val scrollCanvas = ScrollGLCanvas(
        config = config,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        contentWidth = contentWidth,
        contentHeight = contentHeight
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
 * In-place [ScrollVKCanvas] Vulkan 3D scrollable canvas builder.
 */
fun AbstractContainer.scrollVkCanvas(
    config: VKCanvasConfig = VKCanvasConfig(),
    viewportWidth: Int = 800,
    viewportHeight: Int = 600,
    contentWidth: Int = viewportWidth,
    contentHeight: Int = viewportHeight,
    id: String? = null,
    classes: String? = null,
    onScroll: ScrollCanvas3DListener? = null,
    onRender: ((VKContext, Double, Double, Long) -> Unit)? = null,
    block: (@GenericUiDslMarker ScrollVKCanvas.() -> Unit)? = null
): ScrollVKCanvas {
    val scrollCanvas = ScrollVKCanvas(
        config = config,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        contentWidth = contentWidth,
        contentHeight = contentHeight
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
 * In-place [GLCanvasExt] OpenGL ES 3D canvas builder.
 */
fun AbstractContainer.glCanvasExt(
    config: GLCanvasConfig = GLCanvasConfig(),
    width: Int = 800,
    height: Int = 600,
    id: String? = null,
    classes: String? = null,
    onRender: ((GLContext, Double, Double, Long) -> Unit)? = null,
    block: (@GenericUiDslMarker GLCanvasExt.() -> Unit)? = null
): GLCanvasExt {
    val canvas = GLCanvasExt(config = config, width = width, height = height)
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
 * In-place [GLCanvas] OpenGL ES 3D canvas builder.
 */
fun AbstractContainer.glCanvas(
    config: GLCanvasConfig = GLCanvasConfig(),
    width: Int = 800,
    height: Int = 600,
    id: String? = null,
    classes: String? = null,
    onRender: ((GLContext, Double, Double, Long) -> Unit)? = null,
    block: (@GenericUiDslMarker GLCanvas.() -> Unit)? = null
): GLCanvas {
    val canvas = GLCanvas(config = config, width = width, height = height)
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
 * In-place [VKCanvasExt] Vulkan 3D canvas builder.
 */
fun AbstractContainer.vkCanvasExt(
    config: VKCanvasConfig = VKCanvasConfig(),
    width: Int = 800,
    height: Int = 600,
    id: String? = null,
    classes: String? = null,
    onRender: ((VKContext, Double, Double, Long) -> Unit)? = null,
    block: (@GenericUiDslMarker VKCanvasExt.() -> Unit)? = null
): VKCanvasExt {
    val canvas = VKCanvasExt(config = config, width = width, height = height)
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
 * In-place [VKCanvas] Vulkan 3D canvas builder.
 */
fun AbstractContainer.vkCanvas(
    config: VKCanvasConfig = VKCanvasConfig(),
    width: Int = 800,
    height: Int = 600,
    id: String? = null,
    classes: String? = null,
    onRender: ((VKContext, Double, Double, Long) -> Unit)? = null,
    block: (@GenericUiDslMarker VKCanvas.() -> Unit)? = null
): VKCanvas {
    val canvas = VKCanvas(config = config, width = width, height = height)
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
 * In-place [Canvas3DOverlayHost] builder wrapping an existing 3D [canvas] ([GLCanvasExt], [VKCanvasExt],
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
 * In-place [Canvas3DOverlayHost] builder wrapping a new [GLCanvasExt] with an optional [overlay]
 * (`null` by default unless explicitly supplied).
 */
fun AbstractContainer.glCanvasOverlayHost(
    config: GLCanvasConfig = GLCanvasConfig(),
    width: Int = 800,
    height: Int = 600,
    overlay: AbstractOverlay? = null,
    id: String? = null,
    classes: String? = null,
    onRender: ((GLContext, Double, Double, Long) -> Unit)? = null,
    block: (@GenericUiDslMarker Canvas3DOverlayHost.() -> Unit)? = null
): Canvas3DOverlayHost {
    val canvas = GLCanvasExt(config = config, width = width, height = height)
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
 * In-place [Canvas3DOverlayHost] builder wrapping a new [VKCanvasExt] with an optional [overlay]
 * (`null` by default unless explicitly supplied).
 */
fun AbstractContainer.vkCanvasOverlayHost(
    config: VKCanvasConfig = VKCanvasConfig(),
    width: Int = 800,
    height: Int = 600,
    overlay: AbstractOverlay? = null,
    id: String? = null,
    classes: String? = null,
    onRender: ((VKContext, Double, Double, Long) -> Unit)? = null,
    block: (@GenericUiDslMarker Canvas3DOverlayHost.() -> Unit)? = null
): Canvas3DOverlayHost {
    val canvas = VKCanvasExt(config = config, width = width, height = height)
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
    preferVulkan: Boolean = true,
    glConfig: GLCanvasConfig = GLCanvasConfig(),
    vkConfig: VKCanvasConfig = VKCanvasConfig(),
    overlay: AbstractOverlay? = null,
    id: String? = null,
    classes: String? = null,
    onScroll: ScrollCanvas3DListener? = null,
    block: (@GenericUiDslMarker ScrollCanvas3DOverlayHost.() -> Unit)? = null
): ScrollCanvas3DOverlayHost {
    val scrollCanvas = ScrollCanvas3D(
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        contentWidth = contentWidth,
        contentHeight = contentHeight,
        preferVulkan = preferVulkan,
        glConfig = glConfig,
        vkConfig = vkConfig
    )
    if (onScroll != null) {
        scrollCanvas.onScroll(onScroll)
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

/**
 * In-place [ScrollCanvas3DOverlayHost] builder wrapping a new [ScrollGLCanvas] where `overlayRoot`
 * covers only the viewport area (`viewportWidth` x `viewportHeight`), excluding the scrollbars.
 */
fun AbstractContainer.scrollGlCanvasOverlayHost(
    config: GLCanvasConfig = GLCanvasConfig(),
    viewportWidth: Int = 800,
    viewportHeight: Int = 600,
    contentWidth: Int = viewportWidth,
    contentHeight: Int = viewportHeight,
    overlay: AbstractOverlay? = null,
    id: String? = null,
    classes: String? = null,
    onScroll: ScrollCanvas3DListener? = null,
    onRender: ((GLContext, Double, Double, Long) -> Unit)? = null,
    block: (@GenericUiDslMarker ScrollCanvas3DOverlayHost.() -> Unit)? = null
): ScrollCanvas3DOverlayHost {
    val scrollCanvas = ScrollGLCanvas(
        config = config,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        contentWidth = contentWidth,
        contentHeight = contentHeight
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
 * In-place [ScrollCanvas3DOverlayHost] builder wrapping a new [ScrollVKCanvas] where `overlayRoot`
 * covers only the viewport area (`viewportWidth` x `viewportHeight`), excluding the scrollbars.
 */
fun AbstractContainer.scrollVkCanvasOverlayHost(
    config: VKCanvasConfig = VKCanvasConfig(),
    viewportWidth: Int = 800,
    viewportHeight: Int = 600,
    contentWidth: Int = viewportWidth,
    contentHeight: Int = viewportHeight,
    overlay: AbstractOverlay? = null,
    id: String? = null,
    classes: String? = null,
    onScroll: ScrollCanvas3DListener? = null,
    onRender: ((VKContext, Double, Double, Long) -> Unit)? = null,
    block: (@GenericUiDslMarker ScrollCanvas3DOverlayHost.() -> Unit)? = null
): ScrollCanvas3DOverlayHost {
    val scrollCanvas = ScrollVKCanvas(
        config = config,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        contentWidth = contentWidth,
        contentHeight = contentHeight
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

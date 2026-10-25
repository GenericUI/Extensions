package com.nexustools.genericui.ext.canvas3d.dsl

import com.nexustools.genericui.component.media.GLCanvas
import com.nexustools.genericui.component.media.VKCanvas
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.canvas3d.GLCanvasExt
import com.nexustools.genericui.ext.canvas3d.VKCanvasExt
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.gpu.GLCanvasConfig
import com.nexustools.genericui.ext.gpu.GLContext
import com.nexustools.genericui.ext.gpu.VKCanvasConfig
import com.nexustools.genericui.ext.gpu.VKContext

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

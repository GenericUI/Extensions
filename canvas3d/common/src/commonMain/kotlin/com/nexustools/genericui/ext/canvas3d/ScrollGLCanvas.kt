package com.nexustools.genericui.ext.canvas3d

import com.nexustools.genericui.component.media.GLCanvas
import com.nexustools.genericui.component.media.GLCanvasConfig
import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.component.media.GLTexture
import com.nexustools.genericui.component.media.VBlankRenderCallback
import com.nexustools.genericui.component.media.VBlankSubscription
import com.nexustools.genericui.image.ImageBuffer

/**
 * Type-safe scrollable OpenGL ES 3.0+ / Google ANGLE / WebGL2 3D canvas ([ScrollCanvas3D] with `preferVulkan = false`).
 *
 * The underlying [glCanvas] (`GLCanvas`) only allocates a swapchain for the visible viewport
 * (`viewportWidth` x `viewportHeight`), while scrolling over `contentWidth` x `contentHeight` is
 * simulated via [onScroll] / [ScrollCanvas3DListener].
 */
open class ScrollGLCanvas(
    config: GLCanvasConfig = GLCanvasConfig(),
    viewportWidth: Int = 800,
    viewportHeight: Int = 600,
    contentWidth: Int = viewportWidth,
    contentHeight: Int = viewportHeight
) : ScrollCanvas3D(
    viewportWidth = viewportWidth,
    viewportHeight = viewportHeight,
    contentWidth = contentWidth,
    contentHeight = contentHeight,
    preferVulkan = false,
    glConfig = config
) {
    val config: GLCanvasConfig
        get() = glConfig

    val glCanvas: GLCanvas
        get() = viewportGlCanvas

    fun getContext(): GLContext? = resolveGLContext()

    fun uploadImageBufferTexture(imageBuffer: ImageBuffer): GLTexture =
        viewportGlCanvas.uploadImageBufferTexture(imageBuffer)

    fun registerVBlankRender(callback: VBlankRenderCallback<GLContext>): VBlankSubscription =
        registerGLVBlankRender(callback)

    fun dispatchVBlankFrame(explicitTimestampMs: Double? = null) {
        viewportGlCanvas.dispatchVBlankFrame(explicitTimestampMs)
    }
}

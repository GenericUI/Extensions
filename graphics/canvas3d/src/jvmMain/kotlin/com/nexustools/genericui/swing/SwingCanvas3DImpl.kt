package com.nexustools.genericui.swing

import com.nexustools.genericui.component.media.Canvas3DConfig
import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.component.media.GLReadFormat
import com.nexustools.genericui.component.media.GpuRenderContext
import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.desktop.canvas.DesktopGLContextImpl
import com.nexustools.genericui.desktop.canvas.DesktopVKContextImpl
import com.nexustools.genericui.ext.gpu.GpuAcceleration
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.media.Canvas3DNativeImpl
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Java Swing Vulkan / OpenGL ES (`LWJGL` runtime-detected via [SwingVKContextImpl] / [SwingGLContextImpl])
 * implementation of [Canvas3DNativeImpl], blitting rendered framebuffer pixels inside Swing's `paintComponent`.
 */
class SwingCanvas3DImpl(
    override val config: Canvas3DConfig = Canvas3DConfig(),
    override val activeBackend: GpuBackendType = GpuBackendType.VULKAN,
    override val activeAcceleration: GpuAcceleration = GpuAcceleration.HARDWARE,
    initialWidth: Int = 800,
    initialHeight: Int = 600
) : SwingAbstractCanvasImpl<GpuRenderContext>(SwingCanvas3DPanel()), Canvas3DNativeImpl<JComponent> {

    private class SwingCanvas3DPanel : JPanel() {
        var framebufferProvider: (() -> ByteArray)? = null
        var widthProvider: (() -> Int)? = null
        var heightProvider: (() -> Int)? = null
        private var backingImage: BufferedImage? = null
        private var argbScratch: IntArray = IntArray(0)

        init {
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val w = (widthProvider?.invoke() ?: width).coerceAtLeast(1)
            val h = (heightProvider?.invoke() ?: height).coerceAtLeast(1)
            val rgba = framebufferProvider?.invoke() ?: return
            val pixelCount = w * h
            if (rgba.size < pixelCount * 4) return

            if (argbScratch.size != pixelCount) {
                argbScratch = IntArray(pixelCount)
            }
            val argb = argbScratch
            var srcIdx = 0
            for (i in 0 until pixelCount) {
                val r = rgba[srcIdx].toInt() and 0xFF
                val gCh = rgba[srcIdx + 1].toInt() and 0xFF
                val b = rgba[srcIdx + 2].toInt() and 0xFF
                val a = rgba[srcIdx + 3].toInt() and 0xFF
                argb[i] = (a shl 24) or (r shl 16) or (gCh shl 8) or b
                srcIdx += 4
            }
            var img = backingImage
            if (img == null || img.width != w || img.height != h) {
                img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                backingImage = img
            }
            img.setRGB(0, 0, w, h, argb, 0, w)
            g.drawImage(img, 0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1), null)
        }
    }

    private var surfaceW: Int = initialWidth.coerceAtLeast(1)
    private var surfaceH: Int = initialHeight.coerceAtLeast(1)

    private val renderContext: GpuRenderContext = when (activeBackend) {
        GpuBackendType.VULKAN -> SwingVKContextImpl(
            config = config.vulkan,
            initialWidth = surfaceW,
            initialHeight = surfaceH,
            acceleration = activeAcceleration
        )
        GpuBackendType.OPENGL_ES -> SwingGLContextImpl(
            config = config.gl,
            initialWidth = surfaceW,
            initialHeight = surfaceH,
            acceleration = activeAcceleration
        )
    }

    override val vkContext: VKContext?
        get() = renderContext as? VKContext

    override val glContext: GLContext?
        get() = renderContext as? GLContext

    private var createdListener: ((GpuRenderContext, Int, Int) -> Unit)? = null
    private var resizedListener: ((GpuRenderContext, Int, Int) -> Unit)? = null
    private var onBeforeFramePresentListener: ((Long, Double) -> Unit)? = null
    private var presentedFrameCounter: Long = 0L

    init {
        val panel = nativeWidget as SwingCanvas3DPanel
        panel.framebufferProvider = { captureFrameToRgba() }
        panel.widthProvider = { getSurfaceWidth() }
        panel.heightProvider = { getSurfaceHeight() }
    }

    override fun getSurfaceWidth(): Int = surfaceW
    override fun getSurfaceHeight(): Int = surfaceH

    override fun setPreferredSize(size: Size?) {
        super.setPreferredSize(size)
        if (size != null && size.width > 0 && size.height > 0) {
            val prevW = surfaceW
            val prevH = surfaceH
            val nextW = size.width.coerceAtLeast(1)
            val nextH = size.height.coerceAtLeast(1)
            surfaceW = nextW
            surfaceH = nextH
            when (val ctx = renderContext) {
                is DesktopGLContextImpl -> ctx.resizeSurface(nextW, nextH)
                is DesktopVKContextImpl -> ctx.resizeSwapchain(nextW, nextH)
                else -> Unit
            }
            if (prevW != nextW || prevH != nextH) {
                resizedListener?.invoke(renderContext, nextW, nextH)
            }
        }
    }

    override fun getRenderingContext(): GpuRenderContext = renderContext

    override fun setOnSurfaceCreated(listener: ((GpuRenderContext, Int, Int) -> Unit)?) {
        createdListener = listener
        listener?.invoke(renderContext, getSurfaceWidth(), getSurfaceHeight())
    }

    override fun setOnSurfaceResized(listener: ((GpuRenderContext, Int, Int) -> Unit)?) {
        resizedListener = listener
    }

    override fun setOnBeforeFramePresentListener(listener: ((frameIndex: Long, timestampMs: Double) -> Unit)?) {
        onBeforeFramePresentListener = listener
    }

    override fun onAfterVBlankFrame() {
        presentedFrameCounter++
        val nowMs = System.nanoTime() / 1_000_000.0
        onBeforeFramePresentListener?.invoke(presentedFrameCounter, nowMs)
    }

    override fun captureFrameToRgba(): ByteArray = when (val ctx = renderContext) {
        is GLContext -> ctx.readPixels(0, 0, getSurfaceWidth(), getSurfaceHeight(), GLReadFormat.RGBA8)
        is VKContext -> ctx.readPixels(0, 0, getSurfaceWidth(), getSurfaceHeight())
        else -> super.captureFrameToRgba()
    }

    override fun dispose() {
        createdListener = null
        resizedListener = null
        onBeforeFramePresentListener = null
        super.dispose()
    }
}

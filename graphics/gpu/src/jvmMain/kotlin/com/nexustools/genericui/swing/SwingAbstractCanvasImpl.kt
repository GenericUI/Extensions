package com.nexustools.genericui.swing

import com.nexustools.genericui.animation.Animator
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageEncodeOptions
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.nativeimpl.media.AbstractCanvasNativeImpl
import com.nexustools.genericui.threading.GenericUiThreading
import java.awt.GraphicsEnvironment
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Shared Swing base implementation for `SwingCanvas2DImpl`, `SwingGLCanvasImpl`, and `SwingVKCanvasImpl`,
 * driven by monitor-synchronized [Animator.watchVBlank] / [Animator.unwatchVBlank].
 */
abstract class SwingAbstractCanvasImpl<C>(
    panel: JPanel = JPanel()
) : SwingBaseNativeImpl(panel, "canvas"), AbstractCanvasNativeImpl<JComponent, C> {
    override val nativeWidget: JPanel get() = super.nativeWidget as JPanel

    @Volatile
    private var canvasDisposed: Boolean = false

    @Volatile
    protected var vblankActive: Boolean = false

    @Volatile
    protected var vblankCallback: ((Double?) -> Unit)? = null

    protected var passThroughFlag: Boolean = false

    private val vblankLock = Any()
    private var vblankWatcherId: Int = -1
    @Volatile
    private var wasShowingInHierarchy: Boolean = false
    @Volatile
    private var wasAttachedToParent: Boolean = false

    override val isDisposed: Boolean
        get() = canvasDisposed

    private val hierarchyListener = HierarchyListener { e ->
        val flags = e.changeFlags
        if ((flags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L) {
            if (nativeWidget.isShowing) {
                wasShowingInHierarchy = true
            }
            syncVBlankSubscription()
        }
        if ((flags and HierarchyEvent.PARENT_CHANGED.toLong()) != 0L) {
            if (nativeWidget.parent != null || nativeWidget.isDisplayable) {
                wasAttachedToParent = true
            } else if (wasAttachedToParent) {
                wasShowingInHierarchy = false
            }
            syncVBlankSubscription()
        }
    }

    private val componentVisibilityListener = object : ComponentAdapter() {
        override fun componentShown(e: ComponentEvent?) {
            syncVBlankSubscription()
        }

        override fun componentHidden(e: ComponentEvent?) {
            syncVBlankSubscription()
        }
    }

    init {
        nativeWidget.addHierarchyListener(hierarchyListener)
        nativeWidget.addComponentListener(componentVisibilityListener)
    }

    private fun shouldRunVBlankLoop(): Boolean {
        if (canvasDisposed || !vblankActive || vblankCallback == null) return false
        if (!nativeWidget.isVisible) return false
        if (wasShowingInHierarchy && !nativeWidget.isShowing) return false
        if (wasAttachedToParent && nativeWidget.parent == null && !nativeWidget.isDisplayable) return false
        return true
    }

    private fun syncVBlankSubscription() {
        val idToUnwatch: Int
        val shouldStart: Boolean
        synchronized(vblankLock) {
            if (shouldRunVBlankLoop()) {
                if (vblankWatcherId > 0) return
                shouldStart = true
                idToUnwatch = -1
            } else {
                shouldStart = false
                idToUnwatch = vblankWatcherId
                vblankWatcherId = -1
            }
        }
        if (idToUnwatch > 0) {
            Animator.unwatchVBlank(idToUnwatch)
        }
        if (shouldStart) {
            val newId = Animator.watchVBlank { _, timestampMs, _ ->
                if (!shouldRunVBlankLoop()) return@watchVBlank
                vblankCallback?.invoke(timestampMs)
                onAfterVBlankFrame()
                if (GraphicsEnvironment.isHeadless() || SwingUtilities.isEventDispatchThread()) {
                    nativeWidget.repaint()
                } else {
                    GenericUiThreading.runOnUi {
                        nativeWidget.repaint()
                    }
                }
            }
            var undoNewId = false
            synchronized(vblankLock) {
                if (shouldRunVBlankLoop() && vblankWatcherId <= 0) {
                    vblankWatcherId = newId
                } else {
                    undoNewId = true
                }
            }
            if (undoNewId && newId > 0) {
                Animator.unwatchVBlank(newId)
            }
        }
    }

    private fun stopVBlankWatcher() {
        val idToUnwatch = synchronized(vblankLock) {
            val current = vblankWatcherId
            vblankWatcherId = -1
            current
        }
        if (idToUnwatch > 0) {
            Animator.unwatchVBlank(idToUnwatch)
        }
    }

    override fun setVisible(visible: Boolean) {
        super.setVisible(visible)
        syncVBlankSubscription()
    }

    override fun setVBlankLoopActive(active: Boolean, onVBlankFrame: (explicitTimestampMs: Double?) -> Unit) {
        vblankActive = active
        vblankCallback = if (active) onVBlankFrame else null
        if (active) {
            wasShowingInHierarchy = nativeWidget.isShowing
            wasAttachedToParent = nativeWidget.parent != null || nativeWidget.isDisplayable
            syncVBlankSubscription()
        } else {
            stopVBlankWatcher()
        }
    }

    protected open fun onAfterVBlankFrame() {}

    override fun requestSingleRender() {
        val nowMs = System.nanoTime() / 1_000_000.0
        vblankCallback?.invoke(nowMs)
        onAfterVBlankFrame()
        nativeWidget.repaint()
    }

    override fun setPointerEventsPassThrough(passThrough: Boolean) {
        passThroughFlag = passThrough
    }

    override fun getSurfaceWidth(): Int =
        nativeWidget.width.takeIf { it > 0 } ?: getBounds().width.coerceAtLeast(1)

    override fun getSurfaceHeight(): Int =
        nativeWidget.height.takeIf { it > 0 } ?: getBounds().height.coerceAtLeast(1)

    override fun getDevicePixelRatio(): Float = 1.0f

    override fun captureFrameToRgba(): ByteArray {
        val w = getSurfaceWidth().coerceAtLeast(1)
        val h = getSurfaceHeight().coerceAtLeast(1)
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            if (nativeWidget.width != w || nativeWidget.height != h) {
                nativeWidget.setSize(w, h)
                nativeWidget.doLayout()
            }
            nativeWidget.printAll(g)
        } finally {
            g.dispose()
        }
        val argb = IntArray(w * h)
        img.getRGB(0, 0, w, h, argb, 0, w)
        val rgba = ByteArray(w * h * 4)
        var i = 0
        var j = 0
        while (i < argb.size) {
            val px = argb[i++]
            rgba[j++] = ((px ushr 16) and 0xFF).toByte()
            rgba[j++] = ((px ushr 8) and 0xFF).toByte()
            rgba[j++] = (px and 0xFF).toByte()
            rgba[j++] = ((px ushr 24) and 0xFF).toByte()
        }
        return rgba
    }

    override fun captureFrameToJpeg(quality: Int): ByteArray {
        val w = getSurfaceWidth().coerceAtLeast(1)
        val h = getSurfaceHeight().coerceAtLeast(1)
        val rgba = captureFrameToRgba()
        val buf = ImageBuffer(width = w, height = h, pixelFormat = PixelFormat.RGBA_8888, bytes = rgba)
        return ImageLoader.encode(buf, ImageEncodeOptions(format = ImageFormat.JPEG, quality = quality))
    }

    override fun dispose() {
        canvasDisposed = true
        vblankActive = false
        vblankCallback = null
        stopVBlankWatcher()
        nativeWidget.removeHierarchyListener(hierarchyListener)
        nativeWidget.removeComponentListener(componentVisibilityListener)
        super.dispose()
    }
}

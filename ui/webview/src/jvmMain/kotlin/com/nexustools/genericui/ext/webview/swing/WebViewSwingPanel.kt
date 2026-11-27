package com.nexustools.genericui.ext.webview.swing

import com.nexustools.genericui.ext.webview.desktop.ipc.WebViewWorkerHostSession
import java.awt.Graphics
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.JPanel

/**
 * Custom Swing viewport panel (without `JEditorPane`) that composites RGBA framebuffer pixels
 * delivered by the isolated worker JVM process over `:extensions:system:ipc` and forwards
 * resize, pointer, wheel, focus, and keyboard events back to the worker.
 */
class WebViewSwingPanel(
    private val session: WebViewWorkerHostSession
) : JPanel() {
    private var backingImage: BufferedImage? = null
    private var argbScratch: IntArray = IntArray(0)

    init {
        isOpaque = true
        isFocusable = true

        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                val w = width
                val h = height
                if (w > 0 && h > 0) {
                    session.resizeSmart(w, h)
                    repaint()
                }
            }
        })

        addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) {
                session.dispatchFocusEventSmart(true)
            }

            override fun focusLost(e: FocusEvent) {
                session.dispatchFocusEventSmart(false)
            }
        })

        val mouseAdapter = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (!hasFocus()) {
                    requestFocusInWindow()
                }
                session.dispatchPointerEventSmart("down", e.x.toFloat(), e.y.toFloat(), swingButtonToDom(e.button))
            }

            override fun mouseReleased(e: MouseEvent) {
                session.dispatchPointerEventSmart("up", e.x.toFloat(), e.y.toFloat(), swingButtonToDom(e.button))
            }

            override fun mouseMoved(e: MouseEvent) {
                session.dispatchPointerEventSmart("move", e.x.toFloat(), e.y.toFloat(), 0)
            }

            override fun mouseDragged(e: MouseEvent) {
                session.dispatchPointerEventSmart("move", e.x.toFloat(), e.y.toFloat(), swingButtonToDom(e.button))
            }

            override fun mouseEntered(e: MouseEvent) {
                session.dispatchPointerEventSmart("enter", e.x.toFloat(), e.y.toFloat(), 0)
            }

            override fun mouseExited(e: MouseEvent) {
                session.dispatchPointerEventSmart("leave", e.x.toFloat(), e.y.toFloat(), 0)
            }
        }
        addMouseListener(mouseAdapter)
        addMouseMotionListener(mouseAdapter)

        addMouseWheelListener { e ->
            val deltaY = (e.preciseWheelRotation * 40.0).toFloat()
            val deltaX = if (e.isShiftDown) deltaY else 0f
            val finalY = if (e.isShiftDown) 0f else deltaY
            session.dispatchWheelEventSmart(e.x.toFloat(), e.y.toFloat(), deltaX, finalY)
        }

        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                val keyName = swingKeyToDom(e)
                session.dispatchKeyEventSmart(
                    kind = "down",
                    key = keyName,
                    code = KeyEvent.getKeyText(e.keyCode),
                    keyCode = e.keyCode,
                    shift = e.isShiftDown,
                    ctrl = e.isControlDown,
                    alt = e.isAltDown,
                    meta = e.isMetaDown
                )
            }

            override fun keyReleased(e: KeyEvent) {
                val keyName = swingKeyToDom(e)
                session.dispatchKeyEventSmart(
                    kind = "up",
                    key = keyName,
                    code = KeyEvent.getKeyText(e.keyCode),
                    keyCode = e.keyCode,
                    shift = e.isShiftDown,
                    ctrl = e.isControlDown,
                    alt = e.isAltDown,
                    meta = e.isMetaDown
                )
            }

            override fun keyTyped(e: KeyEvent) {
                val ch = e.keyChar
                if (ch != KeyEvent.CHAR_UNDEFINED && !ch.isISOControl()) {
                    session.dispatchKeyEventSmart(
                        kind = "input",
                        key = ch.toString(),
                        code = ch.toString(),
                        keyCode = ch.code,
                        shift = e.isShiftDown,
                        ctrl = e.isControlDown,
                        alt = e.isAltDown,
                        meta = e.isMetaDown,
                        text = ch.toString()
                    )
                }
            }
        })
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val (w, h) = session.framebuffer.copySize()
        val rgba = session.framebuffer.copyRgbaSnapshot()
        val pixelCount = w * h
        if (pixelCount <= 0 || rgba.size < pixelCount * 4) return

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
        val drawW = width.takeIf { it > 0 } ?: w
        val drawH = height.takeIf { it > 0 } ?: h
        g.drawImage(img, 0, 0, drawW, drawH, null)
    }

    private fun swingButtonToDom(button: Int): Int = when (button) {
        MouseEvent.BUTTON2 -> 1
        MouseEvent.BUTTON3 -> 2
        else -> 0
    }

    private fun swingKeyToDom(e: KeyEvent): String = when (e.keyCode) {
        KeyEvent.VK_ENTER -> "Enter"
        KeyEvent.VK_ESCAPE -> "Escape"
        KeyEvent.VK_BACK_SPACE -> "Backspace"
        KeyEvent.VK_TAB -> "Tab"
        KeyEvent.VK_UP -> "ArrowUp"
        KeyEvent.VK_DOWN -> "ArrowDown"
        KeyEvent.VK_LEFT -> "ArrowLeft"
        KeyEvent.VK_RIGHT -> "ArrowRight"
        else -> if (e.keyChar != KeyEvent.CHAR_UNDEFINED && !e.keyChar.isISOControl()) {
            e.keyChar.toString()
        } else {
            KeyEvent.getKeyText(e.keyCode)
        }
    }
}

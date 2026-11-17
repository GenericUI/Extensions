package com.nexustools.genericui.swing

import com.nexustools.genericui.nativeimpl.media.SeekBarNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.roundToInt

class SwingSeekBarImpl : SwingBaseNativeImpl(
    baseWidget = JPanel(null),
    componentClassPrefix = "seek-bar"
), SeekBarNativeImpl<JComponent> {

    private var minVal: Double = 0.0
    private var maxVal: Double = 100.0
    private var stepVal: Double = 0.25
    private var currentValue: Double = 0.0
    private var bufferedRanges: List<Pair<Double, Double>> = emptyList()

    private var accentColor: RgbaColor = RgbaColor(13, 110, 253, 255)
    private var bufferedColor: RgbaColor = RgbaColor(255, 255, 255, 128)
    private var trackColor: RgbaColor = RgbaColor(255, 255, 255, 56)
    private var thumbColor: RgbaColor = RgbaColor(13, 110, 253, 255)

    private var trackHeightPx: Float = 5f
    private var thumbRadiusPx: Float = 7f

    private var onValueChangedListener: ((Double) -> Unit)? = null
    private var onSeekStartListener: ((Double) -> Unit)? = null
    private var onSeekCommitListener: ((Double) -> Unit)? = null

    private val customPaintedTrack = object : JPanel(null) {
        init {
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

                val w = width.toFloat().coerceAtLeast(1f)
                val h = height.toFloat().coerceAtLeast(1f)
                val pad = (thumbRadiusPx + 2f).coerceAtLeast(3f)
                val trackLeft = pad
                val trackRight = (w - pad).coerceAtLeast(trackLeft + 1f)
                val trackWidth = (trackRight - trackLeft).coerceAtLeast(1f)
                val trackY = (h - trackHeightPx) * 0.5f
                val arc = trackHeightPx

                // 1. Unloaded track
                g2.color = trackColor.toAwtColor()
                g2.fill(RoundRectangle2D.Float(trackLeft, trackY, trackWidth, trackHeightPx, arc, arc))

                val span = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0

                // 2. Buffered / preloaded segments
                g2.color = bufferedColor.toAwtColor()
                for ((rangeStart, rangeEnd) in bufferedRanges) {
                    val startFrac = (((rangeStart - minVal) / span).toFloat()).coerceIn(0f, 1f)
                    val endFrac = (((rangeEnd - minVal) / span).toFloat()).coerceIn(0f, 1f)
                    if (endFrac > startFrac) {
                        val segX = trackLeft + startFrac * trackWidth
                        val segW = (endFrac - startFrac) * trackWidth
                        g2.fill(RoundRectangle2D.Float(segX, trackY, segW, trackHeightPx, arc, arc))
                    }
                }

                // 3. Played progress segment
                val playedFrac = (((currentValue - minVal) / span).toFloat()).coerceIn(0f, 1f)
                val playedW = playedFrac * trackWidth
                if (playedW > 0f) {
                    g2.color = accentColor.toAwtColor()
                    g2.fill(RoundRectangle2D.Float(trackLeft, trackY, playedW, trackHeightPx, arc, arc))
                }

                // 4. Circular thumb
                if (thumbRadiusPx > 0f) {
                    val cx = trackLeft + playedW
                    val cy = h * 0.5f
                    val d = thumbRadiusPx * 2f
                    g2.color = thumbColor.toAwtColor()
                    g2.fill(Ellipse2D.Float(cx - thumbRadiusPx, cy - thumbRadiusPx, d, d))
                }
            } finally {
                g2.dispose()
            }
        }
    }

    init {
        nativeWidget.isOpaque = false
        nativeWidget.layout = java.awt.BorderLayout()
        nativeWidget.preferredSize = Dimension(240, 20)
        nativeWidget.minimumSize = Dimension(60, 18)
        nativeWidget.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        nativeWidget.add(customPaintedTrack, java.awt.BorderLayout.CENTER)

        val mouseHandler = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                updateFromMouseX(e.x)
                onSeekStartListener?.invoke(currentValue)
                onValueChangedListener?.invoke(currentValue)
            }

            override fun mouseDragged(e: MouseEvent) {
                updateFromMouseX(e.x)
                onValueChangedListener?.invoke(currentValue)
            }

            override fun mouseReleased(e: MouseEvent) {
                updateFromMouseX(e.x)
                onValueChangedListener?.invoke(currentValue)
                onSeekCommitListener?.invoke(currentValue)
            }
        }
        customPaintedTrack.addMouseListener(mouseHandler)
        customPaintedTrack.addMouseMotionListener(mouseHandler)
        nativeWidget.addMouseListener(mouseHandler)
        nativeWidget.addMouseMotionListener(mouseHandler)

        syncClientPropertiesAndRepaint()
    }

    override fun setRange(min: Double, max: Double, step: Double) {
        minVal = min
        maxVal = max.coerceAtLeast(min)
        stepVal = step.coerceAtLeast(0.001)
        currentValue = currentValue.coerceIn(minVal, maxVal)
        syncClientPropertiesAndRepaint()
    }

    override fun setValue(value: Double) {
        currentValue = value.coerceIn(minVal, maxVal)
        syncClientPropertiesAndRepaint()
    }

    override fun getValue(): Double = currentValue

    override fun setBufferedRanges(ranges: List<Pair<Double, Double>>, max: Double) {
        if (max > minVal) {
            maxVal = max
        }
        bufferedRanges = ranges.mapNotNull { (s, e) ->
            val clampedStart = s.coerceIn(minVal, maxVal)
            val clampedEnd = e.coerceIn(minVal, maxVal)
            if (clampedEnd > clampedStart) clampedStart to clampedEnd else null
        }
        syncClientPropertiesAndRepaint()
    }

    override fun setColors(
        accentColor: RgbaColor,
        bufferedColor: RgbaColor,
        trackColor: RgbaColor,
        thumbColor: RgbaColor
    ) {
        this.accentColor = accentColor
        this.bufferedColor = bufferedColor
        this.trackColor = trackColor
        this.thumbColor = thumbColor
        syncClientPropertiesAndRepaint()
    }

    override fun setDimensions(trackHeightPx: Float, thumbRadiusPx: Float) {
        this.trackHeightPx = trackHeightPx.coerceAtLeast(1f)
        this.thumbRadiusPx = thumbRadiusPx.coerceAtLeast(0f)
        syncClientPropertiesAndRepaint()
    }

    override fun setOnValueChanged(listener: ((Double) -> Unit)?) {
        this.onValueChangedListener = listener
    }

    override fun setOnSeekStart(listener: ((Double) -> Unit)?) {
        this.onSeekStartListener = listener
    }

    override fun setOnSeekCommit(listener: ((Double) -> Unit)?) {
        this.onSeekCommitListener = listener
    }

    private fun updateFromMouseX(mouseX: Int) {
        val w = nativeWidget.width.toFloat().coerceAtLeast(1f)
        val pad = (thumbRadiusPx + 2f).coerceAtLeast(3f)
        val trackLeft = pad
        val trackWidth = (w - pad * 2f).coerceAtLeast(1f)
        val frac = ((mouseX - trackLeft) / trackWidth).coerceIn(0f, 1f).toDouble()
        val raw = minVal + frac * (maxVal - minVal)
        val stepped = if (stepVal > 0.0) {
            (minVal + ((raw - minVal) / stepVal).roundToInt() * stepVal).coerceIn(minVal, maxVal)
        } else {
            raw.coerceIn(minVal, maxVal)
        }
        currentValue = stepped
        syncClientPropertiesAndRepaint()
    }

    private fun syncClientPropertiesAndRepaint() {
        val bufferedEnd = bufferedRanges.maxOfOrNull { it.second } ?: 0.0
        nativeWidget.putClientProperty("value", currentValue)
        nativeWidget.putClientProperty("min", minVal)
        nativeWidget.putClientProperty("max", maxVal)
        nativeWidget.putClientProperty("step", stepVal)
        nativeWidget.putClientProperty("bufferedRanges", bufferedRanges)
        nativeWidget.putClientProperty("bufferedEnd", bufferedEnd)
        nativeWidget.putClientProperty("accentColor", accentColor)
        nativeWidget.putClientProperty("bufferedColor", bufferedColor)
        nativeWidget.putClientProperty("trackColor", trackColor)
        nativeWidget.putClientProperty("thumbColor", thumbColor)
        customPaintedTrack.repaint()
        nativeWidget.repaint()
    }

    private fun RgbaColor.toAwtColor(): Color =
        Color(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255), a.coerceIn(0, 255))
}

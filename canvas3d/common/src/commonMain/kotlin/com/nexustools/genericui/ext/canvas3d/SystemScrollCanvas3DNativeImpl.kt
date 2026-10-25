package com.nexustools.genericui.ext.canvas3d

import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.ext.gpu.GLCanvasConfig
import com.nexustools.genericui.ext.gpu.GLContext
import com.nexustools.genericui.ext.gpu.VKCanvasConfig
import com.nexustools.genericui.ext.gpu.VKContext
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.media.ScrollCanvas3DNativeImpl
import com.nexustools.genericui.style.css.ComputedStyle

/**
 * System fallback implementation of [ScrollCanvas3DNativeImpl] for non-Web targets (`:desktop`,
 * `:mobile`, `:swing`) where [ScrollCanvas3D], [ScrollGLCanvas], and [ScrollVKCanvas] compose a
 * viewport-sized 3D canvas with right and bottom scrollbar components (`RangeSlider`).
 */
class SystemScrollCanvas3DNativeImpl(
    initialViewportWidth: Int = 800,
    initialViewportHeight: Int = 600,
    initialContentWidth: Int = initialViewportWidth,
    initialContentHeight: Int = initialViewportHeight,
    val preferVulkan: Boolean = true,
    val glConfig: GLCanvasConfig = GLCanvasConfig(),
    val vkConfig: VKCanvasConfig = VKCanvasConfig()
) : ScrollCanvas3DNativeImpl<Any, Any> {

    override val nativeWidget: Any = "system-scroll-canvas-3d"
    override val componentClassPrefix: String = "scroll-canvas-3d"
    override val isWebScrollContainer: Boolean = false

    private var viewportW: Int = initialViewportWidth.coerceAtLeast(1)
    private var viewportH: Int = initialViewportHeight.coerceAtLeast(1)
    private var contentW: Int = initialContentWidth.coerceAtLeast(1)
    private var contentH: Int = initialContentHeight.coerceAtLeast(1)
    private var currentScrollX: Int = 0
    private var currentScrollY: Int = 0
    private var scrollListener: ((Int, Int) -> Unit)? = null
    private var vblankCallback: ((Double?) -> Unit)? = null
    private var passThrough: Boolean = false
    private var classStr: String = componentClassPrefix
    private var visibleFlag: Boolean = true
    private var enabledFlag: Boolean = true
    private var opacityVal: Float = 1f
    private var tooltipVal: String? = null
    private var preferredSizeVal: Size? = null

    val glContext: GLContext by lazy {
        Canvas3DExtension.createGLContext(glConfig, viewportW, viewportH)
    }

    val vkContext: VKContext by lazy {
        Canvas3DExtension.createVKContext(vkConfig, viewportW, viewportH)
    }

    override fun setScrollableContentSize(contentWidth: Int, contentHeight: Int) {
        contentW = contentWidth.coerceAtLeast(1)
        contentH = contentHeight.coerceAtLeast(1)
        val maxX = (contentW - viewportW).coerceAtLeast(0)
        val maxY = (contentH - viewportH).coerceAtLeast(0)
        currentScrollX = currentScrollX.coerceIn(0, maxX)
        currentScrollY = currentScrollY.coerceIn(0, maxY)
    }

    override fun setViewportSize(viewportWidth: Int, viewportHeight: Int) {
        viewportW = viewportWidth.coerceAtLeast(1)
        viewportH = viewportHeight.coerceAtLeast(1)
        val maxX = (contentW - viewportW).coerceAtLeast(0)
        val maxY = (contentH - viewportH).coerceAtLeast(0)
        currentScrollX = currentScrollX.coerceIn(0, maxX)
        currentScrollY = currentScrollY.coerceIn(0, maxY)
    }

    override fun setScrollPosition(scrollX: Int, scrollY: Int) {
        val maxX = (contentW - viewportW).coerceAtLeast(0)
        val maxY = (contentH - viewportH).coerceAtLeast(0)
        val clampedX = scrollX.coerceIn(0, maxX)
        val clampedY = scrollY.coerceIn(0, maxY)
        if (clampedX != currentScrollX || clampedY != currentScrollY) {
            currentScrollX = clampedX
            currentScrollY = clampedY
            scrollListener?.invoke(currentScrollX, currentScrollY)
        }
    }

    override fun getScrollX(): Int = currentScrollX
    override fun getScrollY(): Int = currentScrollY

    override fun setOnScrollListener(listener: ((scrollX: Int, scrollY: Int) -> Unit)?) {
        scrollListener = listener
    }

    override fun getRenderingContext(): Any = if (preferVulkan) vkContext else glContext

    override fun setVBlankLoopActive(active: Boolean, onVBlankFrame: (explicitTimestampMs: Double?) -> Unit) {
        vblankCallback = if (active) onVBlankFrame else null
    }

    override fun requestSingleRender() {
        vblankCallback?.invoke(null)
    }

    override fun setPointerEventsPassThrough(passThrough: Boolean) {
        this.passThrough = passThrough
    }

    override fun getSurfaceWidth(): Int = viewportW
    override fun getSurfaceHeight(): Int = viewportH
    override fun getDevicePixelRatio(): Float = 1.0f

    override fun captureFrameToRgba(): ByteArray =
        ByteArray((viewportW * viewportH * 4).coerceAtLeast(4))

    override fun captureFrameToJpeg(quality: Int): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())

    override fun setClass(className: String) { classStr = className }
    override fun getClass(): String = classStr
    override fun addClass(className: String) {
        classStr = "$classStr $className".trim()
    }
    override fun removeClass(className: String) {
        classStr = classStr.split(Regex("\\s+")).filter { it != className }.joinToString(" ")
    }
    override fun hasClass(className: String): Boolean =
        className in classStr.split(Regex("\\s+"))

    override fun setType(type: BootstrapType?) {}
    override fun getType(): BootstrapType? = null
    override fun setOutline(outline: Boolean) {}
    override fun isOutline(): Boolean = false
    override fun setSize(size: BootstrapSize?) {}
    override fun getSize(): BootstrapSize? = null
    override fun setBackgroundType(type: BootstrapType?) {}
    override fun getBackgroundType(): BootstrapType? = null
    override fun setTextColorType(type: BootstrapType?) {}
    override fun getTextColorType(): BootstrapType? = null
    override fun setBorderType(type: BootstrapType?) {}
    override fun getBorderType(): BootstrapType? = null
    override fun setTextAlignment(alignment: TextAlignment?) {}
    override fun getTextAlignment(): TextAlignment? = null
    override fun setFontWeight(weight: FontWeight?) {}
    override fun getFontWeight(): FontWeight? = null
    override fun setTextTransform(transform: TextTransform?) {}
    override fun getTextTransform(): TextTransform? = null
    override fun setTextOverflow(overflow: TextOverflow?) {}
    override fun getTextOverflow(): TextOverflow? = null
    override fun setBorderRadius(radius: BorderRadiusStyle?) {}
    override fun getBorderRadius(): BorderRadiusStyle? = null
    override fun setShadow(shadow: ShadowElevation?) {}
    override fun getShadow(): ShadowElevation? = null
    override fun setDisplay(display: DisplayMode?) {}
    override fun getDisplay(): DisplayMode? = null
    override fun setFlexDirection(direction: FlexDirection?) {}
    override fun getFlexDirection(): FlexDirection? = null
    override fun setFlexWrap(wrap: FlexWrap?) {}
    override fun getFlexWrap(): FlexWrap? = null
    override fun setJustifyContent(justify: JustifyContent?) {}
    override fun getJustifyContent(): JustifyContent? = null
    override fun setAlignItems(align: AlignItems?) {}
    override fun getAlignItems(): AlignItems? = null
    override fun setAlignSelf(align: AlignSelf?) {}
    override fun getAlignSelf(): AlignSelf? = null
    override fun setOverflow(overflow: OverflowBehavior?) {}
    override fun getOverflow(): OverflowBehavior? = null
    override fun setPosition(position: PositionMode?) {}
    override fun getPosition(): PositionMode? = null
    override fun setMarginAll(scale: SpacingScale?) {}
    override fun getMarginAll(): SpacingScale? = null
    override fun setPaddingAll(scale: SpacingScale?) {}
    override fun getPaddingAll(): SpacingScale? = null
    override fun setVisible(visible: Boolean) { visibleFlag = visible }
    override fun isVisible(): Boolean = visibleFlag
    override fun setEnabled(enabled: Boolean) { enabledFlag = enabled }
    override fun isEnabled(): Boolean = enabledFlag
    override fun setOpacity(opacity: Float) { opacityVal = opacity }
    override fun getOpacity(): Float = opacityVal
    override fun setTooltipText(tooltip: String?) { tooltipVal = tooltip }
    override fun getTooltipText(): String? = tooltipVal
    override fun setAttribute(name: String, value: String?) {}
    override fun applyComputedStyle(style: ComputedStyle) {}
    override fun setPreferredSize(size: Size?) { preferredSizeVal = size }
    override fun getBounds(): Rect = Rect(0, 0, viewportW, viewportH)
    override fun setPadding(padding: EdgeInsets) {}
    override fun setMargin(margin: EdgeInsets) {}
    override fun setOnClick(listener: (() -> Unit)?) {}
    override fun setOnDoubleClick(listener: (() -> Unit)?) {}
    override fun setOnContextMenu(listener: ((PointerEvent) -> Unit)?) {}
    override fun setOnPointerDown(listener: ((PointerEvent) -> Unit)?) {}
    override fun setOnPointerMove(listener: ((PointerEvent) -> Unit)?) {}
    override fun setOnPointerUp(listener: ((PointerEvent) -> Unit)?) {}
    override fun setOnWheel(listener: ((WheelEvent) -> Unit)?) {}
    override fun setOnKeyDown(listener: ((KeyEvent) -> Unit)?) {}
    override fun setOnKeyUp(listener: ((KeyEvent) -> Unit)?) {}
    override fun dispose() {}
}

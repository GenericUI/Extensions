package com.nexustools.genericui.ext.svg

import com.nexustools.genericui.component.htmlcompat.Div
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.ext.svg.system.SystemSvgNativeImpl
import com.nexustools.genericui.ext.svg.system.SystemSvgProvider
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.nativeimpl.content.ImageNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SystemSvgNativeImplTest {

    private class FakeImageNativeImpl : ImageNativeImpl<String> {
        override val nativeWidget: String = "fake-system-image"
        override val componentClassPrefix: String = "img-fluid"
        override var isDisposed: Boolean = false

        var lastBuffer: ImageBuffer? = null
        var objectFitVal: ObjectFit = ObjectFit.CONTAIN
        var boundsVal: Rect = Rect.ZERO
        var preferredSizeVal: Size? = null
        var onClickCb: (() -> Unit)? = null
        var onMoveCb: ((PointerEvent) -> Unit)? = null
        var onDownCb: ((PointerEvent) -> Unit)? = null
        var onUpCb: ((PointerEvent) -> Unit)? = null
        var onLeaveCb: ((PointerEvent) -> Unit)? = null

        private var cls: String = ""
        private var visible = true
        private var enabled = true
        private var opacity = 1f
        private var tooltip: String? = null

        override fun setSourceUrl(url: String?) {}
        override fun getSourceUrl(): String? = null
        override fun setImageBuffer(buffer: ImageBuffer?) {
            lastBuffer = buffer
        }
        override fun setFallbackText(fallback: String?) {}
        override fun setObjectFit(fit: ObjectFit) {
            objectFitVal = fit
        }
        override fun getObjectFit(): ObjectFit = objectFitVal

        override fun setClass(className: String) { cls = className }
        override fun getClass(): String = cls
        override fun addClass(className: String) { cls = "$cls $className".trim() }
        override fun removeClass(className: String) {}
        override fun hasClass(className: String): Boolean = className in cls.split(' ')
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
        override fun setVisible(visible: Boolean) { this.visible = visible }
        override fun isVisible(): Boolean = visible
        override fun setEnabled(enabled: Boolean) { this.enabled = enabled }
        override fun isEnabled(): Boolean = enabled
        override fun setOpacity(opacity: Float) { this.opacity = opacity }
        override fun getOpacity(): Float = opacity
        override fun setTooltipText(tooltip: String?) { this.tooltip = tooltip }
        override fun getTooltipText(): String? = tooltip
        override fun setPreferredSize(size: Size?) { preferredSizeVal = size }
        override fun getBounds(): Rect = boundsVal
        override fun setPadding(padding: EdgeInsets) {}
        override fun setMargin(margin: EdgeInsets) {}
        override fun applyComputedStyle(style: ComputedStyle) {}
        override fun setOnClick(listener: (() -> Unit)?) { onClickCb = listener }
        override fun setOnDoubleClick(listener: (() -> Unit)?) {}
        override fun setOnContextMenu(listener: ((PointerEvent) -> Unit)?) {}
        override fun setOnPointerDown(listener: ((PointerEvent) -> Unit)?) { onDownCb = listener }
        override fun setOnPointerMove(listener: ((PointerEvent) -> Unit)?) { onMoveCb = listener }
        override fun setOnPointerUp(listener: ((PointerEvent) -> Unit)?) { onUpCb = listener }
        override fun setOnPointerLeave(listener: ((PointerEvent) -> Unit)?) { onLeaveCb = listener }
        override fun setOnWheel(listener: ((WheelEvent) -> Unit)?) {}
        override fun setOnKeyDown(listener: ((KeyEvent) -> Unit)?) {}
        override fun setOnKeyUp(listener: ((KeyEvent) -> Unit)?) {}
        override fun dispose() { isDisposed = true }
    }

    private class FakeHostRoot(private val factory: NativeFactory) : Div() {
        override fun resolveActiveFactory(): NativeFactory? = factory
    }

    private fun createProxyFactory(
        fakeImage: FakeImageNativeImpl = FakeImageNativeImpl(),
        extensionOverride: SvgNativeImpl<*>? = null
    ): NativeFactory = Proxy.newProxyInstance(
        NativeFactory::class.java.classLoader,
        arrayOf(NativeFactory::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "getBackendTarget" -> BackendTarget.Swing
            "getBackendName" -> "TestSwing"
            "getIsStyleSheetEngineEnabled" -> false
            "getIgnoresAestheticStylesOnStandardComponents" -> false
            "createImage" -> fakeImage
            "createExtensionNativeImpl" -> extensionOverride
            "toString" -> "TestProxyNativeFactory"
            else -> when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Float.TYPE -> 0f
                java.lang.Double.TYPE -> 0.0
                else -> null
            }
        }
    } as NativeFactory

    @AfterTest
    fun tearDown() {
        SvgExtension.resetProviderForTests()
    }

    @Test
    fun systemProviderRasterizesMutatesResizesAndDispatchesPointerEvents() {
        SystemSvgProvider.install()
        val fakeImage = FakeImageNativeImpl()
        val factory = createProxyFactory(fakeImage = fakeImage)

        val svg = Svg(viewBox = SvgViewBox(0f, 0f, 100f, 100f), svgWidth = 100f, svgHeight = 100f)
        var groupClicks = 0
        var circleClicks = 0
        var circleEntered = 0
        var circleLeft = 0

        val group = SvgGroup().apply {
            onClick = { groupClicks++ }
        }
        val box = SvgRect(x = 10f, y = 10f, width = 80f, height = 80f).apply {
            id = "bg"
            fill = SvgPaint.SolidColor(RgbaColor(255, 0, 0, 255))
        }
        val dot = SvgCircle(cx = 50f, cy = 50f, r = 20f).apply {
            id = "dot"
            fill = SvgPaint.SolidColor(RgbaColor(0, 0, 255, 255))
            onClick = { circleClicks++ }
            onPointerEnter = { circleEntered++ }
            onPointerLeave = { circleLeft++ }
        }
        group.addChild(dot)
        svg.addChild(box)
        svg.addChild(group)

        val host = FakeHostRoot(factory)
        host.addChild(svg)

        val impl = assertIs<SystemSvgNativeImpl>(svg.ensureNativeImpl())
        assertEquals(ObjectFit.FILL, fakeImage.objectFitVal)
        val initialBuffer = assertNotNull(fakeImage.lastBuffer)
        assertEquals(100, initialBuffer.width)
        assertEquals(100, initialBuffer.height)
        assertTrue(initialBuffer.getPixel(50, 50).b > 240)
        assertTrue(initialBuffer.getPixel(15, 15).r > 240)

        // Mutating dot fill immediately triggers dirty re-rasterization onto delegateImage!
        dot.fill = SvgPaint.SolidColor(RgbaColor(0, 255, 0, 255))
        val updatedBuffer = assertNotNull(fakeImage.lastBuffer)
        assertTrue(updatedBuffer.getPixel(50, 50).g > 240)

        // Resizing bounds re-rasterizes vector scene at crisp 200x200 resolution!
        impl.setBounds(0, 0, 200, 200)
        val resizedBuffer = assertNotNull(fakeImage.lastBuffer)
        assertEquals(200, resizedBuffer.width)
        assertEquals(200, resizedBuffer.height)
        assertEquals(Size(50, 50), impl.measureIntrinsicSize(availableWidthPx = 50))

        // Hover & click dispatch through delegateImage listeners!
        fakeImage.onMoveCb?.invoke(PointerEvent(x = 100f, y = 100f))
        assertEquals(1, circleEntered)
        assertEquals(0, circleLeft)

        fakeImage.onClickCb?.invoke()
        assertEquals(1, circleClicks)
        assertEquals(1, groupClicks)

        fakeImage.onMoveCb?.invoke(PointerEvent(x = 25f, y = 25f))
        assertEquals(1, circleLeft)
    }

    @Test
    fun factoryExtensionHookTakesPrecedenceOverDefaultProvider() {
        SystemSvgProvider.install()
        val fakeAlienSvg = SystemSvgNativeImpl(Svg(), createProxyFactory())
        val factory = createProxyFactory(extensionOverride = fakeAlienSvg)

        val svg = Svg()
        val host = FakeHostRoot(factory)
        host.addChild(svg)

        val attached = svg.ensureNativeImpl()
        assertTrue(attached === fakeAlienSvg)
    }
}

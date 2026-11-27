package com.nexustools.genericui.ext.svg.system

import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.ext.svg.Svg
import com.nexustools.genericui.ext.svg.SvgElement
import com.nexustools.genericui.ext.svg.SvgNativeImpl
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.nativeimpl.content.ImageNativeImpl
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssCursorValue
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.transition.CssTransitionSpec

/**
 * Universal `:system` implementation of [SvgNativeImpl].
 *
 * Wraps `hostFactory.createImage()` (`ImageNativeImpl<*>`) so `:extensions:graphics:svg` runs
 * seamlessly across all 7 system targets (Win32, macOS, GTK, KDE, Swing, Android, iOS) without
 * any dependency on `:extensions:graphics:canvas2d`, `:gpu`, `:imageprocessing`, or `:overlay`.
 *
 * Renders the mutable [Svg] scene graph into RGBA8888 [ImageBuffer]s using pure-Kotlin
 * [com.nexustools.genericui.ext.svg.SvgSoftwareRasterizer], automatically re-rasterizes when
 * the scene graph mutates or viewport bounds change, and dispatches pointer click / hover events
 * to hit [SvgElement] nodes via inverse-transform hit-testing.
 */
class SystemSvgNativeImpl(
    val svgComponent: Svg,
    val hostFactory: NativeFactory
) : SvgNativeImpl<Any?> {

    val delegateImage: ImageNativeImpl<*> = hostFactory.createImage()

    override val nativeWidget: Any?
        get() = delegateImage.nativeWidget

    override val componentClassPrefix: String
        get() = delegateImage.componentClassPrefix

    private var disposed: Boolean = false
    override val isDisposed: Boolean
        get() = disposed || delegateImage.isDisposed

    var renderedBuffer: ImageBuffer? = null
        private set

    private var renderedWidthPx: Int = 0
    private var renderedHeightPx: Int = 0
    private var lastRenderedSceneVersion: Long = -1L
    private var dirty: Boolean = true

    private var currentBounds: Rect = Rect.ZERO
    private var preferredSizeVal: Size? = null

    private var lastPointerX: Float = 0f
    private var lastPointerY: Float = 0f
    private var hasLastPointer: Boolean = false
    private var lastDownElement: SvgElement? = null
    private var hoveredElement: SvgElement? = null

    private var componentOnClick: (() -> Unit)? = null
    private var componentOnPointerDown: ((PointerEvent) -> Unit)? = null
    private var componentOnPointerMove: ((PointerEvent) -> Unit)? = null
    private var componentOnPointerUp: ((PointerEvent) -> Unit)? = null
    private var componentOnPointerLeave: ((PointerEvent) -> Unit)? = null

    init {
        delegateImage.setObjectFit(ObjectFit.FILL)
        delegateImage.setPlaceholderUrl(null)
        delegateImage.setAntialiasing(true)

        delegateImage.setOnPointerMove { event ->
            handleDelegatePointerMove(event)
            componentOnPointerMove?.invoke(event)
        }
        delegateImage.setOnPointerDown { event ->
            lastPointerX = event.x
            lastPointerY = event.y
            hasLastPointer = true
            lastDownElement = hitTestViewport(event.x, event.y)
            updateHoverTarget(lastDownElement, event)
            componentOnPointerDown?.invoke(event)
        }
        delegateImage.setOnPointerUp { event ->
            lastPointerX = event.x
            lastPointerY = event.y
            hasLastPointer = true
            updateHoverTarget(hitTestViewport(event.x, event.y), event)
            componentOnPointerUp?.invoke(event)
        }
        delegateImage.setOnPointerLeave { event ->
            hasLastPointer = false
            updateHoverTarget(null, event)
            componentOnPointerLeave?.invoke(event)
        }
        delegateImage.setOnClick {
            val hit = if (hasLastPointer) {
                hitTestViewport(lastPointerX, lastPointerY) ?: lastDownElement
            } else {
                lastDownElement
            }
            lastDownElement = null
            dispatchElementClick(hit)
            componentOnClick?.invoke()
        }
    }

    fun measureIntrinsicSize(availableWidthPx: Int = -1, availableHeightPx: Int = -1): Size {
        val maxW = availableWidthPx.takeIf { it > 0 }
        val maxH = availableHeightPx.takeIf { it > 0 }
        return svgComponent.resolveIntrinsicPixelSize(maxW, maxH)
    }

    fun setBounds(bounds: Rect) {
        val prev = currentBounds
        currentBounds = bounds
        if (bounds.width > 0 && bounds.height > 0 &&
            (bounds.width != prev.width || bounds.height != prev.height ||
                bounds.width != renderedWidthPx || bounds.height != renderedHeightPx || dirty)
        ) {
            renderNow()
        }
    }

    fun setBounds(x: Int, y: Int, width: Int, height: Int) {
        setBounds(Rect(x, y, width, height))
    }

    override fun syncSvgScene(svg: Svg) {
        if (isDisposed) return
        val target = resolveTargetPixelSize()
        if (dirty ||
            lastRenderedSceneVersion != svg.sceneVersion ||
            renderedWidthPx != target.width ||
            renderedHeightPx != target.height
        ) {
            renderAtSize(target.width, target.height)
        }
    }

    override fun invalidateSvgScene() {
        dirty = true
        if (!isDisposed) {
            renderNow()
        }
    }

    fun renderNow(): ImageBuffer? {
        if (isDisposed) return renderedBuffer
        val target = resolveTargetPixelSize()
        return renderAtSize(target.width, target.height)
    }

    override fun hitTestViewport(viewportX: Float, viewportY: Float): SvgElement? {
        val size = resolveTargetPixelSize()
        return svgComponent.hitTest(
            viewportX = viewportX,
            viewportY = viewportY,
            viewportWidth = size.width.toFloat(),
            viewportHeight = size.height.toFloat()
        )
    }

    fun simulatePointerMove(event: PointerEvent): SvgElement? =
        handleDelegatePointerMove(event)

    fun simulatePointerClick(viewportX: Float, viewportY: Float): SvgElement? {
        val ev = PointerEvent(x = viewportX, y = viewportY, button = 0)
        handleDelegatePointerMove(ev)
        val hit = hitTestViewport(viewportX, viewportY)
        dispatchElementClick(hit)
        componentOnClick?.invoke()
        return hit
    }

    private fun handleDelegatePointerMove(event: PointerEvent): SvgElement? {
        lastPointerX = event.x
        lastPointerY = event.y
        hasLastPointer = true
        val hit = hitTestViewport(event.x, event.y)
        updateHoverTarget(hit, event)
        return hit
    }

    private fun resolveTargetPixelSize(): Size {
        val delegateBounds = delegateImage.getBounds()
        val chosen = when {
            currentBounds.width > 0 && currentBounds.height > 0 ->
                Size(currentBounds.width, currentBounds.height)
            delegateBounds.width > 0 && delegateBounds.height > 0 ->
                Size(delegateBounds.width, delegateBounds.height)
            preferredSizeVal != null && !preferredSizeVal!!.isEmpty ->
                preferredSizeVal!!
            else ->
                svgComponent.resolveIntrinsicPixelSize()
        }
        return Size(
            width = chosen.width.coerceIn(1, MAX_RASTER_DIM_PX),
            height = chosen.height.coerceIn(1, MAX_RASTER_DIM_PX)
        )
    }

    private fun renderAtSize(widthPx: Int, heightPx: Int): ImageBuffer {
        val buffer = svgComponent.renderToImageBuffer(widthPx, heightPx)
        renderedBuffer = buffer
        renderedWidthPx = widthPx
        renderedHeightPx = heightPx
        lastRenderedSceneVersion = svgComponent.sceneVersion
        dirty = false
        delegateImage.setImageBuffer(buffer)
        return buffer
    }

    private fun updateHoverTarget(newTarget: SvgElement?, event: PointerEvent) {
        val prevTarget = hoveredElement
        if (prevTarget === newTarget) return

        val prevChain = collectAncestorChain(prevTarget)
        val newChain = collectAncestorChain(newTarget)
        val newSet = newChain.toHashSet()
        val prevSet = prevChain.toHashSet()

        // Leave bottom-up for ancestors no longer hovered.
        for (el in prevChain) {
            if (el !in newSet) {
                el.onPointerLeave?.invoke(event)
            }
        }
        // Enter top-down for newly entered ancestors.
        for (i in newChain.indices.reversed()) {
            val el = newChain[i]
            if (el !in prevSet) {
                el.onPointerEnter?.invoke(event)
            }
        }
        hoveredElement = newTarget
    }

    private fun collectAncestorChain(leaf: SvgElement?): List<SvgElement> {
        if (leaf == null) return emptyList()
        val chain = ArrayList<SvgElement>()
        var cur: SvgElement? = leaf
        while (cur != null) {
            chain.add(cur)
            cur = cur.parentElement
        }
        return chain
    }

    private fun dispatchElementClick(target: SvgElement?) {
        var cur: SvgElement? = target
        while (cur != null) {
            cur.onClick?.invoke()
            cur = cur.parentElement
        }
    }

    // NativeImpl forwarding to delegateImage

    override fun setClass(className: String) = delegateImage.setClass(className)
    override fun getClass(): String = delegateImage.getClass()
    override fun getClasses(): Set<String> = delegateImage.getClasses()
    override fun addClass(className: String) = delegateImage.addClass(className)
    override fun removeClass(className: String) = delegateImage.removeClass(className)
    override fun hasClass(className: String): Boolean = delegateImage.hasClass(className)

    override fun setType(type: BootstrapType?) = delegateImage.setType(type)
    override fun getType(): BootstrapType? = delegateImage.getType()
    override fun setOutline(outline: Boolean) = delegateImage.setOutline(outline)
    override fun isOutline(): Boolean = delegateImage.isOutline()
    override fun setSize(size: BootstrapSize?) = delegateImage.setSize(size)
    override fun getSize(): BootstrapSize? = delegateImage.getSize()
    override fun setBackgroundType(type: BootstrapType?) = delegateImage.setBackgroundType(type)
    override fun getBackgroundType(): BootstrapType? = delegateImage.getBackgroundType()
    override fun setTextColorType(type: BootstrapType?) = delegateImage.setTextColorType(type)
    override fun getTextColorType(): BootstrapType? = delegateImage.getTextColorType()
    override fun setBorderType(type: BootstrapType?) = delegateImage.setBorderType(type)
    override fun getBorderType(): BootstrapType? = delegateImage.getBorderType()
    override fun setTextAlignment(alignment: TextAlignment?) = delegateImage.setTextAlignment(alignment)
    override fun getTextAlignment(): TextAlignment? = delegateImage.getTextAlignment()
    override fun setFontWeight(weight: FontWeight?) = delegateImage.setFontWeight(weight)
    override fun getFontWeight(): FontWeight? = delegateImage.getFontWeight()
    override fun setTextTransform(transform: TextTransform?) = delegateImage.setTextTransform(transform)
    override fun getTextTransform(): TextTransform? = delegateImage.getTextTransform()
    override fun setTextOverflow(overflow: TextOverflow?) = delegateImage.setTextOverflow(overflow)
    override fun getTextOverflow(): TextOverflow? = delegateImage.getTextOverflow()
    override fun setBorderRadius(radius: BorderRadiusStyle?) = delegateImage.setBorderRadius(radius)
    override fun getBorderRadius(): BorderRadiusStyle? = delegateImage.getBorderRadius()
    override fun setShadow(shadow: ShadowElevation?) = delegateImage.setShadow(shadow)
    override fun getShadow(): ShadowElevation? = delegateImage.getShadow()
    override fun setDisplay(display: DisplayMode?) = delegateImage.setDisplay(display)
    override fun getDisplay(): DisplayMode? = delegateImage.getDisplay()
    override fun setFlexDirection(direction: FlexDirection?) = delegateImage.setFlexDirection(direction)
    override fun getFlexDirection(): FlexDirection? = delegateImage.getFlexDirection()
    override fun setFlexWrap(wrap: FlexWrap?) = delegateImage.setFlexWrap(wrap)
    override fun getFlexWrap(): FlexWrap? = delegateImage.getFlexWrap()
    override fun setJustifyContent(justify: JustifyContent?) = delegateImage.setJustifyContent(justify)
    override fun getJustifyContent(): JustifyContent? = delegateImage.getJustifyContent()
    override fun setAlignItems(align: AlignItems?) = delegateImage.setAlignItems(align)
    override fun getAlignItems(): AlignItems? = delegateImage.getAlignItems()
    override fun setAlignSelf(align: AlignSelf?) = delegateImage.setAlignSelf(align)
    override fun getAlignSelf(): AlignSelf? = delegateImage.getAlignSelf()
    override fun setOverflow(overflow: OverflowBehavior?) = delegateImage.setOverflow(overflow)
    override fun getOverflow(): OverflowBehavior? = delegateImage.getOverflow()
    override fun setPosition(position: PositionMode?) = delegateImage.setPosition(position)
    override fun getPosition(): PositionMode? = delegateImage.getPosition()
    override fun setMarginAll(scale: SpacingScale?) = delegateImage.setMarginAll(scale)
    override fun getMarginAll(): SpacingScale? = delegateImage.getMarginAll()
    override fun setPaddingAll(scale: SpacingScale?) = delegateImage.setPaddingAll(scale)
    override fun getPaddingAll(): SpacingScale? = delegateImage.getPaddingAll()

    override fun setVisible(visible: Boolean) = delegateImage.setVisible(visible)
    override fun isVisible(): Boolean = delegateImage.isVisible()
    override fun setEnabled(enabled: Boolean) = delegateImage.setEnabled(enabled)
    override fun isEnabled(): Boolean = delegateImage.isEnabled()
    override fun setOpacity(opacity: Float) = delegateImage.setOpacity(opacity)
    override fun getOpacity(): Float = delegateImage.getOpacity()
    override fun setTooltipText(tooltip: String?) = delegateImage.setTooltipText(tooltip)
    override fun getTooltipText(): String? = delegateImage.getTooltipText()
    override fun setAttribute(name: String, value: String?) = delegateImage.setAttribute(name, value)

    override fun setPreferredSize(size: Size?) {
        preferredSizeVal = size
        delegateImage.setPreferredSize(size)
        if (!isDisposed && size != null && !size.isEmpty) {
            val target = resolveTargetPixelSize()
            if (target.width != renderedWidthPx || target.height != renderedHeightPx || dirty) {
                renderAtSize(target.width, target.height)
            }
        }
    }

    override fun getBounds(): Rect {
        val delegateBounds = delegateImage.getBounds()
        return if (delegateBounds.width > 0 && delegateBounds.height > 0) {
            delegateBounds
        } else {
            currentBounds
        }
    }

    override fun setPadding(padding: EdgeInsets) = delegateImage.setPadding(padding)
    override fun setMargin(margin: EdgeInsets) = delegateImage.setMargin(margin)
    override fun applyComputedStyle(style: ComputedStyle) = delegateImage.applyComputedStyle(style)
    override fun applyComponentStyle(style: ComponentStyle) = delegateImage.applyComponentStyle(style)
    override fun applyComponentBootstrap(bootstrap: ComponentBootstrap) =
        delegateImage.applyComponentBootstrap(bootstrap)

    override fun setCustomCursor(cursor: CssCursorValue?) = delegateImage.setCustomCursor(cursor)
    override fun beginBatchAttach() = delegateImage.beginBatchAttach()
    override fun endBatchAttach() = delegateImage.endBatchAttach()
    override fun setBackgroundGradient(gradient: Gradient?) = delegateImage.setBackgroundGradient(gradient)
    override fun getBackgroundGradient(): Gradient? = delegateImage.getBackgroundGradient()
    override fun setHoveredState(hovered: Boolean) = delegateImage.setHoveredState(hovered)
    override fun isHoveredState(): Boolean = delegateImage.isHoveredState()
    override fun setFocusedState(focused: Boolean) = delegateImage.setFocusedState(focused)
    override fun isFocusedState(): Boolean = delegateImage.isFocusedState()
    override fun setFocusable(focusable: Boolean) = delegateImage.setFocusable(focusable)
    override fun isFocusable(): Boolean = delegateImage.isFocusable()
    override fun setActivePressedState(active: Boolean) = delegateImage.setActivePressedState(active)
    override fun isActivePressedState(): Boolean = delegateImage.isActivePressedState()
    override fun supportsNativeTransitions(): Boolean = delegateImage.supportsNativeTransitions()
    override fun configureNativeTransitions(specs: List<CssTransitionSpec>) =
        delegateImage.configureNativeTransitions(specs)

    override fun runNativeTransition(
        property: String,
        fromValue: String?,
        toValue: String,
        spec: CssTransitionSpec,
        onComplete: () -> Unit
    ): Boolean = delegateImage.runNativeTransition(property, fromValue, toValue, spec, onComplete)

    override fun scheduleDelayed(delayMs: Int, action: () -> Unit): Boolean =
        delegateImage.scheduleDelayed(delayMs, action)

    override fun setOnBeforeTargetFramePresent(
        listener: ((frameSwapCount: Long, timestampMs: Double?) -> Unit)?
    ) = delegateImage.setOnBeforeTargetFramePresent(listener)

    override fun setOnClick(listener: (() -> Unit)?) {
        componentOnClick = listener
    }

    override fun setOnDoubleClick(listener: (() -> Unit)?) = delegateImage.setOnDoubleClick(listener)
    override fun setOnContextMenu(listener: ((PointerEvent) -> Unit)?) =
        delegateImage.setOnContextMenu(listener)

    override fun setOnPointerDown(listener: ((PointerEvent) -> Unit)?) {
        componentOnPointerDown = listener
    }

    override fun setOnPointerMove(listener: ((PointerEvent) -> Unit)?) {
        componentOnPointerMove = listener
    }

    override fun setOnPointerUp(listener: ((PointerEvent) -> Unit)?) {
        componentOnPointerUp = listener
    }

    override fun setOnPointerEnter(listener: ((PointerEvent) -> Unit)?) =
        delegateImage.setOnPointerEnter(listener)

    override fun setOnPointerLeave(listener: ((PointerEvent) -> Unit)?) {
        componentOnPointerLeave = listener
    }

    override fun setOnWheel(listener: ((WheelEvent) -> Unit)?) = delegateImage.setOnWheel(listener)
    override fun setOnKeyDown(listener: ((KeyEvent) -> Unit)?) = delegateImage.setOnKeyDown(listener)
    override fun setOnKeyUp(listener: ((KeyEvent) -> Unit)?) = delegateImage.setOnKeyUp(listener)

    override fun dispose() {
        if (disposed) return
        disposed = true
        hoveredElement = null
        lastDownElement = null
        renderedBuffer = null
        delegateImage.dispose()
    }

    private companion object {
        const val MAX_RASTER_DIM_PX = 8192
    }
}

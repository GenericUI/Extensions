package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.ext.alien.AlienLayoutEngine
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.gradient.Gradient
import com.nexustools.genericui.style.mapper.BootstrapClassMapper
import com.nexustools.genericui.style.transition.CssTransitionSpec

/**
 * Base class for all self-rendered `*AlienImpl` component implementations in `:extensions:alien`.
 *
 * Implements [NativeImpl] over an [AlienWidgetNode] and reports dirty regions to [AlienRenderSession.repaintManager]
 * whenever visual properties, Bootstrap enums, CSS classes, or bounds change.
 */
abstract class AlienImpl(
    override val componentClassPrefix: String = "",
    tagName: String = "div",
    session: AlienRenderSession? = null,
    initialNode: AlienWidgetNode? = null
) : NativeImpl<Any?> {

    val node: AlienWidgetNode = (initialNode ?: AlienWidgetNode(
        componentClassPrefix = componentClassPrefix,
        tagName = tagName,
        session = session
    )).also {
        it.ownerImpl = this
    }

    override val nativeWidget: Any?
        get() = node

    // String-based CSS Class API
    override fun setClass(className: String) {
        node.styleSnapshot = BootstrapClassMapper.fromClassString(className, componentClassPrefix)
        node.markLayoutDirty()
    }

    override fun getClass(): String = BootstrapClassMapper.toClassString(node.styleSnapshot)

    override fun addClass(className: String) {
        val combined = (getClass() + " " + className).trim()
        setClass(combined)
    }

    override fun removeClass(className: String) {
        val toRemove = className.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
        val remaining = BootstrapClassMapper.toClassTokens(node.styleSnapshot).filter { it !in toRemove }
        setClass(remaining.joinToString(" "))
    }

    override fun hasClass(className: String): Boolean =
        className.trim() in BootstrapClassMapper.toClassTokens(node.styleSnapshot)

    // Bootstrap Enum Helper API
    override fun setType(type: BootstrapType?) {
        node.styleSnapshot = node.styleSnapshot.copy(type = type)
        node.invalidateBounds()
    }

    override fun getType(): BootstrapType? = node.styleSnapshot.type

    override fun setOutline(outline: Boolean) {
        node.styleSnapshot = node.styleSnapshot.copy(outline = outline)
        node.invalidateBounds()
    }

    override fun isOutline(): Boolean = node.styleSnapshot.outline

    override fun setSize(size: BootstrapSize?) {
        node.styleSnapshot = node.styleSnapshot.copy(size = size)
        node.markLayoutDirty()
    }

    override fun getSize(): BootstrapSize? = node.styleSnapshot.size

    override fun setBackgroundType(type: BootstrapType?) {
        node.styleSnapshot = node.styleSnapshot.copy(backgroundType = type)
        node.invalidateBounds()
    }

    override fun getBackgroundType(): BootstrapType? = node.styleSnapshot.backgroundType

    override fun setTextColorType(type: BootstrapType?) {
        node.styleSnapshot = node.styleSnapshot.copy(textColorType = type)
        node.invalidateBounds()
    }

    override fun getTextColorType(): BootstrapType? = node.styleSnapshot.textColorType

    override fun setBorderType(type: BootstrapType?) {
        node.styleSnapshot = node.styleSnapshot.copy(borderType = type)
        node.invalidateBounds()
    }

    override fun getBorderType(): BootstrapType? = node.styleSnapshot.borderType

    override fun setTextAlignment(alignment: TextAlignment?) {
        node.styleSnapshot = node.styleSnapshot.copy(textAlignment = alignment)
        node.invalidateBounds()
    }

    override fun getTextAlignment(): TextAlignment? = node.styleSnapshot.textAlignment

    override fun setFontWeight(weight: FontWeight?) {
        node.styleSnapshot = node.styleSnapshot.copy(fontWeight = weight)
        node.markLayoutDirty()
    }

    override fun getFontWeight(): FontWeight? = node.styleSnapshot.fontWeight

    override fun setTextTransform(transform: TextTransform?) {
        node.styleSnapshot = node.styleSnapshot.copy(textTransform = transform)
        node.markLayoutDirty()
    }

    override fun getTextTransform(): TextTransform? = node.styleSnapshot.textTransform

    override fun setTextOverflow(overflow: TextOverflow?) {
        node.styleSnapshot = node.styleSnapshot.copy(textOverflow = overflow)
        node.invalidateBounds()
    }

    override fun getTextOverflow(): TextOverflow? = node.styleSnapshot.textOverflow

    override fun setBorderRadius(radius: BorderRadiusStyle?) {
        node.styleSnapshot = node.styleSnapshot.copy(borderRadius = radius)
        node.invalidateBounds()
    }

    override fun getBorderRadius(): BorderRadiusStyle? = node.styleSnapshot.borderRadius

    override fun setShadow(shadow: ShadowElevation?) {
        node.styleSnapshot = node.styleSnapshot.copy(shadow = shadow)
        node.invalidateBounds()
    }

    override fun getShadow(): ShadowElevation? = node.styleSnapshot.shadow

    override fun setDisplay(display: DisplayMode?) {
        node.styleSnapshot = node.styleSnapshot.copy(display = display)
        node.markLayoutDirty()
    }

    override fun getDisplay(): DisplayMode? = node.styleSnapshot.display

    override fun setFlexDirection(direction: FlexDirection?) {
        node.styleSnapshot = node.styleSnapshot.copy(flexDirection = direction)
        node.markLayoutDirty()
    }

    override fun getFlexDirection(): FlexDirection? = node.styleSnapshot.flexDirection

    override fun setFlexWrap(wrap: FlexWrap?) {
        node.styleSnapshot = node.styleSnapshot.copy(flexWrap = wrap)
        node.markLayoutDirty()
    }

    override fun getFlexWrap(): FlexWrap? = node.styleSnapshot.flexWrap

    override fun setJustifyContent(justify: JustifyContent?) {
        node.styleSnapshot = node.styleSnapshot.copy(justifyContent = justify)
        node.markLayoutDirty()
    }

    override fun getJustifyContent(): JustifyContent? = node.styleSnapshot.justifyContent

    override fun setAlignItems(align: AlignItems?) {
        node.styleSnapshot = node.styleSnapshot.copy(alignItems = align)
        node.markLayoutDirty()
    }

    override fun getAlignItems(): AlignItems? = node.styleSnapshot.alignItems

    override fun setAlignSelf(align: AlignSelf?) {
        node.styleSnapshot = node.styleSnapshot.copy(alignSelf = align)
        node.markLayoutDirty()
    }

    override fun getAlignSelf(): AlignSelf? = node.styleSnapshot.alignSelf

    override fun setOverflow(overflow: OverflowBehavior?) {
        node.styleSnapshot = node.styleSnapshot.copy(overflow = overflow)
        node.invalidateBounds()
    }

    override fun getOverflow(): OverflowBehavior? = node.styleSnapshot.overflow

    override fun setPosition(position: PositionMode?) {
        node.styleSnapshot = node.styleSnapshot.copy(position = position)
        node.markLayoutDirty()
    }

    override fun getPosition(): PositionMode? = node.styleSnapshot.position

    override fun setMarginAll(scale: SpacingScale?) {
        node.styleSnapshot = node.styleSnapshot.copy(marginAll = scale)
        node.markLayoutDirty()
    }

    override fun getMarginAll(): SpacingScale? = node.styleSnapshot.marginAll

    override fun setPaddingAll(scale: SpacingScale?) {
        node.styleSnapshot = node.styleSnapshot.copy(paddingAll = scale)
        node.markLayoutDirty()
    }

    override fun getPaddingAll(): SpacingScale? = node.styleSnapshot.paddingAll

    // Geometry, Visibility, State & Custom CSS
    override fun setVisible(visible: Boolean) {
        if (node.visible != visible) {
            node.visible = visible
            node.markLayoutDirty()
            node.invalidateBounds()
        }
    }

    override fun isVisible(): Boolean = node.visible

    override fun setEnabled(enabled: Boolean) {
        if (node.enabled != enabled) {
            node.enabled = enabled
            node.invalidateBounds()
        }
    }

    override fun isEnabled(): Boolean = node.enabled

    override fun setOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0f, 1f)
        if (node.opacity != clamped) {
            node.opacity = clamped
            node.invalidateBounds()
        }
    }

    override fun getOpacity(): Float = node.opacity

    override fun setTooltipText(tooltip: String?) {
        node.tooltipText = tooltip
    }

    override fun getTooltipText(): String? = node.tooltipText

    override fun setAttribute(name: String, value: String?) {
        if (value == null) {
            node.attributes.remove(name)
        } else {
            node.attributes[name] = value
            if (name == "data-genericui-tag" && value.isNotBlank()) {
                node.tagName = value
                node.markLayoutDirty()
            }
        }
        node.invalidateBounds()
    }

    override fun setPreferredSize(size: Size?) {
        node.preferredSize = size
        if (size != null && size.width > 0 && size.height > 0) {
            node.bounds = Rect(node.bounds.x, node.bounds.y, size.width, size.height)
        }
        node.markLayoutDirty()
    }

    private fun findRootNode(): AlienWidgetNode {
        var cur = node
        while (cur.parent != null) {
            cur = cur.parent!!
        }
        return cur
    }

    override fun getBounds(): Rect {
        if (node.layoutWidth <= 0f || node.layoutHeight <= 0f || node.layoutDirty) {
            val root = findRootNode()
            val session = node.session ?: root.session
            if (session != null && session.viewportWidth > 0 && session.viewportHeight > 0) {
                AlienLayoutEngine.layoutTree(
                    root = session.rootNode,
                    viewportWidth = session.viewportWidth.toFloat(),
                    viewportHeight = session.viewportHeight.toFloat(),
                    updateTracker = session.repaintManager.updateTracker
                )
                session.isLayoutDirty = false
            } else if (root !== node && root.bounds.width > 0 && root.bounds.height > 0) {
                AlienLayoutEngine.layoutTree(
                    root = root,
                    viewportWidth = root.bounds.width.toFloat(),
                    viewportHeight = root.bounds.height.toFloat()
                )
            }
        }
        return node.bounds
    }

    override fun setPadding(padding: EdgeInsets) {
        node.padding = padding
        node.markLayoutDirty()
    }

    override fun setMargin(margin: EdgeInsets) {
        node.margin = margin
        node.markLayoutDirty()
    }

    override fun applyComputedStyle(style: ComputedStyle) {
        node.computedStyle = style
        style.transition?.let { raw ->
            node.transitionAnimator.configureTransitions(CssTransitionSpec.parseList(raw))
        }
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun setTint(tint: RgbaColor?) {
        node.tint = tint
        node.invalidateBounds()
    }

    override fun getTint(): RgbaColor? = node.tint

    override fun setBackgroundGradient(gradient: Gradient?) {
        node.backgroundGradient = gradient
        node.invalidateBounds()
    }

    override fun getBackgroundGradient(): Gradient? = node.backgroundGradient

    override fun setHoveredState(hovered: Boolean) {
        if (node.hoveredState != hovered) {
            node.hoveredState = hovered
            node.invalidateBounds()
        }
    }

    override fun isHoveredState(): Boolean = node.hoveredState

    override fun setFocusedState(focused: Boolean) {
        if (node.focusedState != focused) {
            node.focusedState = focused
            node.invalidateBounds()
        }
    }

    override fun isFocusedState(): Boolean = node.focusedState

    override fun setActivePressedState(active: Boolean) {
        if (node.activePressedState != active) {
            node.activePressedState = active
            node.invalidateBounds()
        }
    }

    override fun isActivePressedState(): Boolean = node.activePressedState

    // Native CSS Transition Processing
    override fun supportsNativeTransitions(): Boolean = true

    override fun configureNativeTransitions(specs: List<CssTransitionSpec>) {
        node.transitionAnimator.configureTransitions(specs)
    }

    override fun runNativeTransition(
        property: String,
        fromValue: String?,
        toValue: String,
        spec: CssTransitionSpec,
        onComplete: () -> Unit
    ): Boolean {
        node.transitionAnimator.startTransition(property, spec, onComplete)
        node.invalidateBounds()
        return true
    }

    // Event Listeners
    override fun setOnClick(listener: (() -> Unit)?) {
        node.onClick = listener
    }

    override fun setOnDoubleClick(listener: (() -> Unit)?) {
        node.onDoubleClick = listener
    }

    override fun setOnContextMenu(listener: ((PointerEvent) -> Unit)?) {
        node.onContextMenu = listener
    }

    override fun setOnPointerDown(listener: ((PointerEvent) -> Unit)?) {
        node.onPointerDown = listener
    }

    override fun setOnPointerMove(listener: ((PointerEvent) -> Unit)?) {
        node.onPointerMove = listener
    }

    override fun setOnPointerUp(listener: ((PointerEvent) -> Unit)?) {
        node.onPointerUp = listener
    }

    override fun setOnWheel(listener: ((WheelEvent) -> Unit)?) {
        node.onWheel = listener
    }

    override fun setOnKeyDown(listener: ((KeyEvent) -> Unit)?) {
        node.onKeyDown = listener
    }

    override fun setOnKeyUp(listener: ((KeyEvent) -> Unit)?) {
        node.onKeyUp = listener
    }

    override fun dispose() {
        node.parent?.removeChild(node)
    }
}

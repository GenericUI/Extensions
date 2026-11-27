package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.ext.alien.impl.AlienImpl
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssCursorValue
import com.nexustools.genericui.style.css.CssSelectorNode
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.mapper.BootstrapStyleSnapshot
import com.nexustools.genericui.style.transition.ContainerAnimationFrameState
import com.nexustools.genericui.style.transition.CssTransitionSpec

/**
 * Retained scene-graph node (`W` in `NativeImpl<AlienWidgetNode>`) for an [AlienImpl] or
 * [com.nexustools.genericui.ext.alien.impl.AlienUnderlayExtensionSlotNode].
 *
 * Holds the node's layout bounds, Bootstrap 5.3 style snapshot, `ComputedStyle`, interactive states,
 * [AlienTransitionAnimator], child node list, and custom painting/measurement delegates.
 */
class AlienWidgetNode(
    val componentClassPrefix: String = "",
    var tagName: String = "div",
    var session: AlienRenderSession? = null
) : CssSelectorNode {
    var ownerImpl: AlienImpl? = null
    var parent: AlienWidgetNode? = null

    private val _children = mutableListOf<AlienWidgetNode>()
    val children: List<AlienWidgetNode>
        get() = _children

    /**
     * When non-null, this node represents a non-Alien underlay component (`UnalienContainer` or
     * extension widget such as `Canvas3D`, `VideoPlayer`, `WebView`) attached to the
     * host container **below** the `Canvas2D`.
     */
    var wrappedUnderlayNativeImpl: NativeImpl<*>? = null
    var underlayLayerMode: AlienExtensionLayerMode = AlienExtensionLayerMode.CUT_TRANSPARENT_HOLE

    // Style & State
    var styleSnapshot: BootstrapStyleSnapshot = BootstrapStyleSnapshot(componentPrefix = componentClassPrefix)
    var inlineComponentStyle: ComponentStyle? = null
    var computedStyle: ComputedStyle? = null
    var styledVisualOverride: com.nexustools.genericui.style.css.StyledVisualSpec? = null
    var customCursor: CssCursorValue? = null
    var visible: Boolean = true
    var enabled: Boolean = true
    var hoveredState: Boolean = false
    var focusedState: Boolean = false
    var activePressedState: Boolean = false
    var checkedState: Boolean = false
    var focusRingEnabled: Boolean = false
    var opacity: Float = 1.0f
    var tooltipText: String? = null
    val attributes: MutableMap<String, String> = linkedMapOf()
    var preferredSize: Size? = null
    var padding: EdgeInsets = EdgeInsets.ZERO
    var margin: EdgeInsets = EdgeInsets.ZERO
    var backgroundGradient: Gradient? = null

    // Layout & Bounds
    var bounds: Rect = Rect(0, 0, 0, 0)
    var measuredSize: Size = Size(0, 0)
    var intrinsicWidth: Float = 0f
    var intrinsicHeight: Float = 0f
    val layoutWidth: Float
        get() = bounds.width.toFloat()
    val layoutHeight: Float
        get() = bounds.height.toFloat()
    var scrollOffsetX: Int = 0
    var scrollOffsetY: Int = 0
    var scrollWidth: Int = 0
    var scrollHeight: Int = 0
    var listItemIndex: Int = 0
    var layoutDirty: Boolean = true
    var textContentForSelector: Boolean = false
    internal var resolvedCustomProperties: Map<String, String> = emptyMap()
    internal var lastResolvedVisualStyle: AlienResolvedVisualStyle? = null
    private var cachedStylePassId: Long = -1L
    private var cachedStylePassValue: AlienResolvedVisualStyle? = null
    private var cachedSelectorClassesPassId: Long = -1L
    private var cachedSelectorClassesValue: Set<String>? = null

    override val selectorTagName: String
        get() = tagName.ifEmpty { "div" }

    override val selectorId: String?
        get() = attributes["id"]?.takeIf { it.isNotEmpty() }

    override val selectorClasses: Set<String>
        get() {
            val passId = session?.activeStylePassId ?: -1L
            if (passId >= 0L && cachedSelectorClassesPassId == passId) {
                cachedSelectorClassesValue?.let { return it }
            }
            val classes = AlienPrecompiledBootstrapStyleSheet.collectBootstrapClassesForNode(this)
            if (passId >= 0L) {
                cachedSelectorClassesPassId = passId
                cachedSelectorClassesValue = classes
            }
            return classes
        }

    override val selectorAttributes: Map<String, String>
        get() = attributes

    override val selectorParent: CssSelectorNode?
        get() = parent

    override val selectorChildren: List<CssSelectorNode>
        get() = _children

    override val selectorHasTextContent: Boolean
        get() = textContentForSelector || attributes["text"]?.isNotEmpty() == true

    override val selectorIsHovered: Boolean
        get() = hoveredState && enabled

    override val selectorIsFocused: Boolean
        get() = focusedState && enabled

    override val selectorIsActive: Boolean
        get() = activePressedState && enabled

    override val selectorIsEnabled: Boolean
        get() = enabled

    override val selectorIsChecked: Boolean
        get() = checkedState

    override val selectorIsValid: Boolean
        get() = attributes["aria-invalid"] != "true" && "is-invalid" !in styleSnapshot.customClasses

    override val selectorIsPlaceholderShown: Boolean
        get() = attributes["placeholder"]?.isNotEmpty() == true &&
            attributes["value"].isNullOrEmpty()

    override val selectorInheritedCustomProperties: Map<String, String>
        get() = parent?.resolvedCustomProperties ?: emptyMap()

    fun scrollTo(x: Int, y: Int) {
        val maxX = (scrollWidth - bounds.width).coerceAtLeast(0)
        val maxY = (scrollHeight - bounds.height).coerceAtLeast(0)
        val clampedX = x.coerceIn(0, maxX)
        val clampedY = y.coerceIn(0, maxY)
        if (scrollOffsetX != clampedX || scrollOffsetY != clampedY) {
            scrollOffsetX = clampedX
            scrollOffsetY = clampedY
            invalidateBounds()
        }
    }

    // Transitions, Animations & Offscreen Container Cache
    val transitionAnimator: AlienTransitionAnimator = AlienTransitionAnimator()
    private var lastResolvedHovered: Boolean = false
    private var lastResolvedFocused: Boolean = false
    private var lastResolvedActive: Boolean = false
    var hasContinuousAnimation: Boolean = false
    var hardwareLayerCacheEnabled: Boolean = false
    var cachedOffscreenBuffer: ImageBuffer? = null
    var animatedFrameState: ContainerAnimationFrameState? = null

    // Event Listeners
    var onClick: (() -> Unit)? = null
    var onDoubleClick: (() -> Unit)? = null
    var onContextMenu: ((PointerEvent) -> Unit)? = null
    var onPointerDown: ((PointerEvent) -> Unit)? = null
    var onPointerMove: ((PointerEvent) -> Unit)? = null
    var onPointerUp: ((PointerEvent) -> Unit)? = null
    var onWheel: ((WheelEvent) -> Unit)? = null
    var onKeyDown: ((KeyEvent) -> Unit)? = null
    var onKeyUp: ((KeyEvent) -> Unit)? = null

    // Component-Specific Measure & Paint Hooks
    var customMeasure: ((availableWidth: Int, availableHeight: Int, style: AlienResolvedVisualStyle) -> Size)? = null
    var customPaint: ((ctx: ImageBuffer2DContext, bounds: Rect, style: AlienResolvedVisualStyle) -> Unit)? = null

    val isUnderlayExtensionSlot: Boolean
        get() = wrappedUnderlayNativeImpl != null

    val contentBounds: Rect
        get() {
            val style = resolveVisualStyle()
            val pad = style.padding
            val cx = bounds.x + pad.left
            val cy = bounds.y + pad.top
            val cw = (bounds.width - pad.left - pad.right).coerceAtLeast(0)
            val ch = (bounds.height - pad.top - pad.bottom).coerceAtLeast(0)
            return Rect(cx, cy, cw, ch)
        }

    fun resolveVisualStyle(): AlienResolvedVisualStyle {
        val activePassId = session?.activeStylePassId ?: -1L
        if (activePassId >= 0L && cachedStylePassId == activePassId) {
            cachedStylePassValue?.let { return it }
        }
        val target = AlienCssStyleResolver.resolve(this)
        resolvedCustomProperties = target.customProperties
        val inlineSpecs = inlineComponentStyle?.getTransitions().orEmpty()
        val computedSpecs = computedStyle?.resolveEffectiveTransitions().orEmpty()
        if (inlineSpecs.isNotEmpty()) {
            transitionAnimator.configureTransitions(inlineSpecs)
        } else if (computedSpecs.isNotEmpty() && !transitionAnimator.hasConfiguredTransitions) {
            transitionAnimator.configureTransitions(computedSpecs)
        }
        val activeAnims = target.animations
        if (activeAnims.isNotEmpty()) {
            transitionAnimator.configureKeyframeAnimations(activeAnims, session?.styleSheets.orEmpty())
        }
        val interactiveChanged = lastResolvedHovered != hoveredState ||
            lastResolvedFocused != focusedState ||
            lastResolvedActive != activePressedState
        lastResolvedHovered = hoveredState
        lastResolvedFocused = focusedState
        lastResolvedActive = activePressedState
        val resolved = transitionAnimator.resolveVisualStyleForFrame(
            targetStyle = target,
            enableDefaultBootstrapHoverTransition = interactiveChanged
        )
        lastResolvedVisualStyle = resolved
        if (activePassId >= 0L) {
            cachedStylePassId = activePassId
            cachedStylePassValue = resolved
        }
        return resolved
    }

    fun addChild(child: AlienWidgetNode, index: Int = -1) {
        if (child.parent != null && child.parent !== this) {
            child.parent?.removeChild(child)
        }
        child.parent = this
        child.propagateSession(session)
        if (index < 0 || index >= _children.size) {
            _children.add(child)
        } else {
            _children.add(index, child)
        }
        markLayoutDirty()
        invalidateBounds()
    }

    fun removeChild(child: AlienWidgetNode): Boolean {
        val oldBounds = child.bounds
        val removed = _children.remove(child)
        if (removed) {
            child.parent = null
            markLayoutDirty()
            session?.repaintManager?.markDirty(oldBounds)
            invalidateBounds()
        }
        return removed
    }

    fun clearChildren() {
        for (c in _children) {
            c.parent = null
        }
        _children.clear()
        markLayoutDirty()
        invalidateBounds()
    }

    fun propagateSession(newSession: AlienRenderSession?) {
        this.session = newSession
        for (child in _children) {
            child.propagateSession(newSession)
        }
    }

    fun markLayoutDirty() {
        layoutDirty = true
        parent?.layoutDirty = true
        session?.requestLayoutAndRepaint(bounds)
    }

    /**
     * Marks this node and its ancestor chain dirty for measurement and layout (e.g., when an image
     * finishes decoding or intrinsic content dimensions change), scheduling a full layout + repaint pass.
     */
    fun markMeasureDirty() {
        var cur: AlienWidgetNode? = this
        while (cur != null) {
            cur.layoutDirty = true
            cur = cur.parent
        }
        session?.requestLayoutAndRepaint(bounds)
    }

    fun invalidateBounds() {
        session?.repaintManager?.markDirty(bounds)
    }
}

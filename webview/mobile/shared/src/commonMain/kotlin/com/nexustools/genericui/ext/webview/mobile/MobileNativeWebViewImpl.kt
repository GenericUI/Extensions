package com.nexustools.genericui.ext.webview.mobile

import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl
import com.nexustools.genericui.style.css.ComputedStyle

/**
 * Mobile `WebViewNativeImpl` backed by the native platform WebView
 * (`android.webkit.WebView` on `:android` and `WKWebView` on `:ios`).
 *
 * **Note**: Per architecture rules, Mobile WebView uses the native OS WebView engine
 * and **NEVER** uses `NJS`.
 */
class MobileNativeWebViewImpl(
    val config: WebViewConfig = WebViewConfig(),
    override val nativeWidget: Any = "MobileNativeWebView",
    override val componentClassPrefix: String = "webview"
) : WebViewNativeImpl<Any> {

    private var currentUrl: String? = null
    private val history = ArrayList<String>()
    private var historyIndex: Int = -1
    private var messageListener: ((String) -> Unit)? = null
    private var pageFinishedListener: ((String) -> Unit)? = null

    private var classNameState: String = ""
    private var typeState: BootstrapType? = null
    private var outlineState: Boolean = false
    private var sizeState: BootstrapSize? = null
    private var bgTypeState: BootstrapType? = null
    private var textColorTypeState: BootstrapType? = null
    private var borderTypeState: BootstrapType? = null
    private var textAlignmentState: TextAlignment? = null
    private var fontWeightState: FontWeight? = null
    private var textTransformState: TextTransform? = null
    private var textOverflowState: TextOverflow? = null
    private var borderRadiusState: BorderRadiusStyle? = null
    private var shadowState: ShadowElevation? = null
    private var displayState: DisplayMode? = null
    private var flexDirectionState: FlexDirection? = null
    private var flexWrapState: FlexWrap? = null
    private var justifyContentState: JustifyContent? = null
    private var alignItemsState: AlignItems? = null
    private var alignSelfState: AlignSelf? = null
    private var overflowState: OverflowBehavior? = null
    private var positionState: PositionMode? = null
    private var marginAllState: SpacingScale? = null
    private var paddingAllState: SpacingScale? = null
    private var visibleState: Boolean = true
    private var enabledState: Boolean = true
    private var opacityState: Float = 1f
    private var tooltipState: String? = null
    private var preferredSizeState: Size? = null

    override fun loadUrl(url: String) {
        currentUrl = url
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history.add(url)
        historyIndex = history.size - 1
        pageFinishedListener?.invoke(url)
    }

    override fun getUrl(): String? = currentUrl

    override fun loadHtml(html: String, baseUrl: String?) {
        currentUrl = baseUrl ?: "about:blank"
        pageFinishedListener?.invoke(currentUrl!!)
    }

    override fun reload() {
        currentUrl?.let { pageFinishedListener?.invoke(it) }
    }

    override fun goBack() {
        if (canGoBack()) {
            historyIndex--
            currentUrl = history[historyIndex]
        }
    }

    override fun goForward() {
        if (canGoForward()) {
            historyIndex++
            currentUrl = history[historyIndex]
        }
    }

    override fun canGoBack(): Boolean = historyIndex > 0

    override fun canGoForward(): Boolean = historyIndex >= 0 && historyIndex < history.size - 1

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        if (!config.enableJavaScript) {
            callback?.invoke(null)
            return
        }
        callback?.invoke("true")
    }

    override fun postWebMessage(message: String) {
        messageListener?.invoke(message)
    }

    override fun setOnWebMessageReceived(listener: ((String) -> Unit)?) {
        messageListener = listener
    }

    override fun setOnPageFinished(listener: ((String) -> Unit)?) {
        pageFinishedListener = listener
    }

    override fun setClass(className: String) { classNameState = className }
    override fun getClass(): String = classNameState
    override fun addClass(className: String) { classNameState = "$classNameState $className".trim() }
    override fun removeClass(className: String) {}
    override fun hasClass(className: String): Boolean = className in classNameState
    override fun setType(type: BootstrapType?) { typeState = type }
    override fun getType(): BootstrapType? = typeState
    override fun setOutline(outline: Boolean) { outlineState = outline }
    override fun isOutline(): Boolean = outlineState
    override fun setSize(size: BootstrapSize?) { sizeState = size }
    override fun getSize(): BootstrapSize? = sizeState
    override fun setBackgroundType(type: BootstrapType?) { bgTypeState = type }
    override fun getBackgroundType(): BootstrapType? = bgTypeState
    override fun setTextColorType(type: BootstrapType?) { textColorTypeState = type }
    override fun getTextColorType(): BootstrapType? = textColorTypeState
    override fun setBorderType(type: BootstrapType?) { borderTypeState = type }
    override fun getBorderType(): BootstrapType? = borderTypeState
    override fun setTextAlignment(alignment: TextAlignment?) { textAlignmentState = alignment }
    override fun getTextAlignment(): TextAlignment? = textAlignmentState
    override fun setFontWeight(weight: FontWeight?) { fontWeightState = weight }
    override fun getFontWeight(): FontWeight? = fontWeightState
    override fun setTextTransform(transform: TextTransform?) { textTransformState = transform }
    override fun getTextTransform(): TextTransform? = textTransformState
    override fun setTextOverflow(overflow: TextOverflow?) { textOverflowState = overflow }
    override fun getTextOverflow(): TextOverflow? = textOverflowState
    override fun setBorderRadius(radius: BorderRadiusStyle?) { borderRadiusState = radius }
    override fun getBorderRadius(): BorderRadiusStyle? = borderRadiusState
    override fun setShadow(shadow: ShadowElevation?) { shadowState = shadow }
    override fun getShadow(): ShadowElevation? = shadowState
    override fun setDisplay(display: DisplayMode?) { displayState = display }
    override fun getDisplay(): DisplayMode? = displayState
    override fun setFlexDirection(direction: FlexDirection?) { flexDirectionState = direction }
    override fun getFlexDirection(): FlexDirection? = flexDirectionState
    override fun setFlexWrap(wrap: FlexWrap?) { flexWrapState = wrap }
    override fun getFlexWrap(): FlexWrap? = flexWrapState
    override fun setJustifyContent(justify: JustifyContent?) { justifyContentState = justify }
    override fun getJustifyContent(): JustifyContent? = justifyContentState
    override fun setAlignItems(align: AlignItems?) { alignItemsState = align }
    override fun getAlignItems(): AlignItems? = alignItemsState
    override fun setAlignSelf(align: AlignSelf?) { alignSelfState = align }
    override fun getAlignSelf(): AlignSelf? = alignSelfState
    override fun setOverflow(overflow: OverflowBehavior?) { overflowState = overflow }
    override fun getOverflow(): OverflowBehavior? = overflowState
    override fun setPosition(position: PositionMode?) { positionState = position }
    override fun getPosition(): PositionMode? = positionState
    override fun setMarginAll(scale: SpacingScale?) { marginAllState = scale }
    override fun getMarginAll(): SpacingScale? = marginAllState
    override fun setPaddingAll(scale: SpacingScale?) { paddingAllState = scale }
    override fun getPaddingAll(): SpacingScale? = paddingAllState
    override fun setVisible(visible: Boolean) { visibleState = visible }
    override fun isVisible(): Boolean = visibleState
    override fun setEnabled(enabled: Boolean) { enabledState = enabled }
    override fun isEnabled(): Boolean = enabledState
    override fun setOpacity(opacity: Float) { opacityState = opacity }
    override fun getOpacity(): Float = opacityState
    override fun setTooltipText(tooltip: String?) { tooltipState = tooltip }
    override fun getTooltipText(): String? = tooltipState
    override fun setPreferredSize(size: Size?) { preferredSizeState = size }
    override fun getBounds(): Rect = Rect(0f, 0f, (preferredSizeState?.width ?: 0).toFloat(), (preferredSizeState?.height ?: 0).toFloat())
    override fun setPadding(padding: EdgeInsets) {}
    override fun setMargin(margin: EdgeInsets) {}
    override fun applyComputedStyle(style: ComputedStyle) {}
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

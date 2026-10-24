package com.nexustools.genericui.swing

import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl
import javax.swing.JComponent
import javax.swing.JEditorPane

/**
 * Java Swing `JEditorPane` / embedded browser implementation of [WebViewNativeImpl].
 */
class SwingWebViewImpl : SwingBaseNativeImpl(JEditorPane("text/html", ""), "webview"), WebViewNativeImpl<JComponent> {
    override val nativeWidget: JEditorPane = super.nativeWidget as JEditorPane

    private var currentUrl: String? = "about:blank"
    private var pageLoadedListener: ((String) -> Unit)? = null
    private var bridgeMessageListener: ((String) -> Unit)? = null

    init {
        nativeWidget.isEditable = false
    }

    override fun loadUrl(url: String) {
        currentUrl = url
        pageLoadedListener?.invoke(url)
    }

    override fun getUrl(): String? = currentUrl

    override fun loadHtml(html: String, baseUrl: String?) {
        currentUrl = baseUrl ?: "about:blank"
        nativeWidget.text = html
        pageLoadedListener?.invoke(currentUrl ?: "about:blank")
    }

    override fun reload() {
        pageLoadedListener?.invoke(currentUrl ?: "about:blank")
    }

    override fun goBack() {}
    override fun goForward() {}
    override fun canGoBack(): Boolean = false
    override fun canGoForward(): Boolean = false

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        callback?.invoke("null")
    }

    override fun postWebMessage(message: String) {
        bridgeMessageListener?.invoke(message)
    }

    override fun setOnWebMessageReceived(listener: ((String) -> Unit)?) {
        bridgeMessageListener = listener
    }

    override fun setOnPageFinished(listener: ((String) -> Unit)?) {
        pageLoadedListener = listener
    }
}

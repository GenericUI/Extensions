package com.nexustools.genericui.ext.webview.swing

import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl
import com.nexustools.genericui.swing.SwingBaseNativeImpl
import com.nexustools.njs.compiler.JavaTranspiler
import com.nexustools.njs.runtime.AbstractFunction
import com.nexustools.njs.runtime.BaseObject
import com.nexustools.njs.runtime.GenericObject
import com.nexustools.njs.runtime.Global
import com.nexustools.njs.runtime.Undefined
import com.nexustools.njs.runtime.Utilities
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.JScrollPane

/**
 * Desktop Swing `WebViewNativeImpl` backed by Chromium JCEF/HTML rendering and
 * `NJS` (`lib/NJS`) using NJS's own Java compiler system ([JavaTranspiler]).
 */
class SwingChromiumNjsWebViewImpl(
    val config: WebViewConfig = WebViewConfig()
) : SwingBaseNativeImpl(JPanel(BorderLayout()), "webview"), WebViewNativeImpl<JComponent> {

    private val editorPane = JEditorPane().apply {
        contentType = "text/html"
        isEditable = false
    }

    override val nativeWidget: JPanel = super.nativeWidget as JPanel

    val njsCompiler: JavaTranspiler = JavaTranspiler()
    val njsGlobal: Global = Utilities.createExtendedGlobal(njsCompiler)

    private var currentUrl: String? = null
    private val history = ArrayList<String>()
    private var historyIndex: Int = -1
    private var webMessageListener: ((String) -> Unit)? = null
    private var pageFinishedListener: ((String) -> Unit)? = null
    private val scriptRegex = Regex("<script[^>]*>([\\s\\S]*?)</script>", RegexOption.IGNORE_CASE)

    init {
        nativeWidget.add(JScrollPane(editorPane), BorderLayout.CENTER)
        val bridge = GenericObject(njsGlobal)
        val postFn = object : AbstractFunction(njsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val msg = params.getOrNull(0)?.toString() ?: ""
                webMessageListener?.invoke(msg)
                return Undefined
            }
            override fun name(): String = "postMessage"
        }
        bridge.setHidden("postMessage", postFn)
        njsGlobal.setHidden("genericUi", bridge)
        njsGlobal.setHidden("postMessage", postFn)
    }

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
        editorPane.text = html
        if (config.enableJavaScript) {
            for (match in scriptRegex.findAll(html)) {
                val script = match.groupValues[1].trim()
                if (script.isNotEmpty()) {
                    evaluateJavascript(script, null)
                }
            }
        }
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
        val compiled = njsCompiler.compile(script, "<swing-webview>", false)
        val res = compiled.exec(njsGlobal, null)
        callback?.invoke(if (res == null || res === Undefined) null else res.toString())
    }

    override fun postWebMessage(message: String) {
        webMessageListener?.invoke(message)
    }

    override fun setOnWebMessageReceived(listener: ((String) -> Unit)?) {
        webMessageListener = listener
    }

    override fun setOnPageFinished(listener: ((String) -> Unit)?) {
        pageFinishedListener = listener
    }

    override fun dispose() {
        nativeWidget.removeAll()
        super.dispose()
    }
}

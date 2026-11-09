package com.nexustools.genericui.ext.webview.swing

import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.ext.webview.WebViewConsoleMessage
import com.nexustools.genericui.ext.webview.desktop.ChromiumBlinkEngine
import com.nexustools.genericui.ext.webview.desktop.NjsDesktopWebRuntime
import com.nexustools.genericui.ext.webview.desktop.process.ChromiumBrowserProcessHost
import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl
import com.nexustools.genericui.swing.SwingBaseNativeImpl
import com.nexustools.njs.compiler.JavaTranspiler
import com.nexustools.njs.runtime.Global
import java.awt.BorderLayout
import java.io.File
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.JScrollPane

/**
 * Desktop Swing `WebViewNativeImpl` backed by multi-process Chromium
 * ([ChromiumBrowserProcessHost]: Browser + Renderer + GPU + Network processes,
 * [ChromiumBlinkEngine] + `JEditorPane`) and `NJS` (`lib/NJS`) using NJS's own
 * Java compiler system ([JavaTranspiler]).
 */
class SwingChromiumNjsWebViewImpl(
    val config: WebViewConfig = WebViewConfig()
) : SwingBaseNativeImpl(JPanel(BorderLayout()), "webview"), WebViewNativeImpl<JComponent> {

    private val editorPane = JEditorPane().apply {
        contentType = "text/html"
        isEditable = false
    }

    override val nativeWidget: JPanel get() = super.nativeWidget as JPanel

    val njsCompiler: JavaTranspiler = JavaTranspiler()

    private var currentBaseDir: File? = null
    private var lastLoadedHtml: String? = null
    private val history = ArrayList<String>()
    private var historyIndex: Int = -1
    private var webMessageListener: ((String) -> Unit)? = null
    private var pageFinishedListener: ((String) -> Unit)? = null
    private var consoleMessageListener: ((WebViewConsoleMessage) -> Unit)? = null
    private var jsErrorListener: ((String) -> Unit)? = null

    val browserProcessHost: ChromiumBrowserProcessHost = ChromiumBrowserProcessHost(
        resourceLoader = { resourceUrl -> loadLocalTextResource(resourceUrl) },
        onPostMessage = { msg -> webMessageListener?.invoke(msg) },
        onConsoleMessage = { consoleMsg -> consoleMessageListener?.invoke(consoleMsg) },
        onJavaScriptError = { errMsg -> jsErrorListener?.invoke(errMsg) },
        onPageFinished = { url -> pageFinishedListener?.invoke(url) }
    )

    val blinkEngine: ChromiumBlinkEngine
        get() = browserProcessHost.blinkEngine

    val njsRuntime: NjsDesktopWebRuntime
        get() = browserProcessHost.njsRuntime

    val njsGlobal: Global
        get() = njsRuntime.global

    init {
        nativeWidget.add(JScrollPane(editorPane), BorderLayout.CENTER)
    }

    override fun loadUrl(url: String) {
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history.add(url)
        historyIndex = history.size - 1

        val localHtml = resolveHtmlFromUrlOrPath(url)
        val htmlText = if (localHtml != null) {
            currentBaseDir = localHtml.first.parentFile
            lastLoadedHtml = localHtml.second
            runCatching { editorPane.text = localHtml.second }
            localHtml.second
        } else {
            lastLoadedHtml = null
            null
        }
        browserProcessHost.loadUrl(
            url = url,
            resolvedHtml = htmlText,
            enableJavaScript = config.enableJavaScript
        )
    }

    override fun getUrl(): String? = blinkEngine.currentUrl?.ifEmpty { null }

    override fun loadHtml(html: String, baseUrl: String?) {
        val effectiveUrl = baseUrl ?: "about:blank"
        lastLoadedHtml = html
        if (!baseUrl.isNullOrBlank() && baseUrl != "about:blank") {
            val clean = stripUrlSchemeAndQuery(baseUrl)
            if (clean.isNotEmpty()) {
                val f = File(clean)
                currentBaseDir = if (f.isDirectory) f else f.parentFile
            }
        }
        runCatching { editorPane.text = html }
        browserProcessHost.loadHtml(
            html = html,
            baseUrl = effectiveUrl,
            enableJavaScript = config.enableJavaScript
        )
    }

    override fun reload() {
        val url = getUrl() ?: "about:blank"
        val cached = lastLoadedHtml
        if (cached != null) {
            runCatching { editorPane.text = cached }
            browserProcessHost.loadHtml(
                html = cached,
                baseUrl = url,
                enableJavaScript = config.enableJavaScript
            )
        } else {
            browserProcessHost.loadUrl(
                url = url,
                resolvedHtml = null,
                enableJavaScript = config.enableJavaScript
            )
        }
    }

    override fun goBack() {
        if (canGoBack()) {
            historyIndex--
            val targetUrl = history[historyIndex]
            val localHtml = resolveHtmlFromUrlOrPath(targetUrl)
            val htmlText = if (localHtml != null) {
                currentBaseDir = localHtml.first.parentFile
                lastLoadedHtml = localHtml.second
                runCatching { editorPane.text = localHtml.second }
                localHtml.second
            } else {
                null
            }
            browserProcessHost.loadUrl(
                url = targetUrl,
                resolvedHtml = htmlText,
                enableJavaScript = config.enableJavaScript
            )
        }
    }

    override fun goForward() {
        if (canGoForward()) {
            historyIndex++
            val targetUrl = history[historyIndex]
            val localHtml = resolveHtmlFromUrlOrPath(targetUrl)
            val htmlText = if (localHtml != null) {
                currentBaseDir = localHtml.first.parentFile
                lastLoadedHtml = localHtml.second
                runCatching { editorPane.text = localHtml.second }
                localHtml.second
            } else {
                null
            }
            browserProcessHost.loadUrl(
                url = targetUrl,
                resolvedHtml = htmlText,
                enableJavaScript = config.enableJavaScript
            )
        }
    }

    override fun canGoBack(): Boolean = historyIndex > 0

    override fun canGoForward(): Boolean = historyIndex >= 0 && historyIndex < history.size - 1

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        if (!config.enableJavaScript) {
            callback?.invoke(null)
            return
        }
        val res = browserProcessHost.evaluateJavaScript(script, "<swing-webview>")
        callback?.invoke(res)
    }

    override fun postWebMessage(message: String) {
        browserProcessHost.postWebMessage(message)
    }

    override fun setOnWebMessageReceived(listener: ((String) -> Unit)?) {
        webMessageListener = listener
    }

    override fun setOnPageFinished(listener: ((String) -> Unit)?) {
        pageFinishedListener = listener
    }

    override fun registerCommunicationFunction(name: String, handler: (String) -> String) {
        browserProcessHost.registerCommunicationFunction(name, handler)
    }

    override fun unregisterCommunicationFunction(name: String) {
        browserProcessHost.unregisterCommunicationFunction(name)
    }

    override fun callCommunicationFunction(name: String, payloadJson: String, callback: ((String?) -> Unit)?) {
        if (!config.enableJavaScript) {
            callback?.invoke(null)
            return
        }
        val res = browserProcessHost.callCommunicationFunction(name, payloadJson)
        callback?.invoke(res)
    }

    override fun setOnConsoleMessage(listener: ((WebViewConsoleMessage) -> Unit)?) {
        consoleMessageListener = listener
    }

    override fun setOnJavaScriptError(listener: ((String) -> Unit)?) {
        jsErrorListener = listener
    }

    internal fun loadLocalTextResource(pathOrUrl: String): String? {
        val clean = stripUrlSchemeAndQuery(pathOrUrl)
        if (clean.isEmpty()) return null
        val base = currentBaseDir
        if (base != null && !File(clean).isAbsolute) {
            val relFile = File(base, clean.removePrefix("./").removePrefix("/"))
            if (relFile.isFile) {
                return runCatching { relFile.readText() }.getOrNull()
            }
        }
        val direct = File(clean)
        if (direct.isFile) {
            return runCatching { direct.readText() }.getOrNull()
        }
        return null
    }

    private fun resolveHtmlFromUrlOrPath(url: String): Pair<File, String>? {
        val trimmed = url.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("about:", ignoreCase = true) ||
            trimmed.startsWith("data:", ignoreCase = true)
        ) {
            return null
        }
        val clean = stripUrlSchemeAndQuery(trimmed)
        if (clean.isEmpty()) return null
        val f = File(clean)
        if (f.isDirectory) {
            val idx = File(f, "index.html")
            if (idx.isFile) {
                return runCatching { idx to idx.readText() }.getOrNull()
            }
        }
        if (f.isFile) {
            return runCatching { f to f.readText() }.getOrNull()
        }
        return null
    }

    private fun stripUrlSchemeAndQuery(rawUrl: String): String {
        var s = rawUrl.trim()
        val qIdx = s.indexOf('?')
        if (qIdx >= 0) s = s.substring(0, qIdx)
        val hIdx = s.indexOf('#')
        if (hIdx >= 0) s = s.substring(0, hIdx)
        if (s.startsWith("file://", ignoreCase = true)) {
            s = s.substring("file://".length)
            if (s.length >= 3 && s[0] == '/' && s[2] == ':') {
                s = s.substring(1)
            }
        }
        return s
    }

    override fun dispose() {
        browserProcessHost.shutdown()
        nativeWidget.removeAll()
        super.dispose()
    }
}

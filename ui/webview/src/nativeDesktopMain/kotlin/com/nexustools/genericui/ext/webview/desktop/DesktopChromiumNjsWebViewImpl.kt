@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.webview.desktop

import com.nexustools.genericui.desktop.core.DesktopBaseNativeImpl
import com.nexustools.genericui.desktop.core.DesktopNativeWidget
import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.ext.webview.WebViewConsoleMessage
import com.nexustools.genericui.ext.webview.desktop.process.ChromiumBrowserProcessHost
import com.nexustools.genericui.nativeimpl.media.WebViewNativeImpl
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread

/**
 * Desktop `WebViewNativeImpl` implementation backed by the multi-process **Chromium** architecture
 * ([ChromiumBrowserProcessHost]: Browser + Renderer + GPU + Network processes), the Chromium Blink
 * layout/rendering engine ([ChromiumBlinkEngine]), and the **`NJS`** Native JIT runtime ([NjsDesktopWebRuntime])
 * across `:windows`, `:macos`, `:gtk`, and `:qt`.
 */
class DesktopChromiumNjsWebViewImpl(
    val config: WebViewConfig = WebViewConfig(),
    nativeHandle: DesktopNativeWidget = DesktopNativeWidget("Desktop", "ChromiumNjsWebView")
) : DesktopBaseNativeImpl<DesktopNativeWidget>(nativeHandle, "webview"), WebViewNativeImpl<DesktopNativeWidget> {

    private val history = ArrayList<String>()
    private var historyIndex: Int = -1
    private var currentBaseDir: String? = null
    private var lastLoadedHtml: String? = null
    private var messageListener: ((String) -> Unit)? = null
    private var pageFinishedListener: ((String) -> Unit)? = null
    private var consoleMessageListener: ((WebViewConsoleMessage) -> Unit)? = null
    private var jsErrorListener: ((String) -> Unit)? = null

    val browserProcessHost: ChromiumBrowserProcessHost = ChromiumBrowserProcessHost(
        resourceLoader = { targetPath -> loadLocalTextResource(targetPath) },
        onPostMessage = { msg -> messageListener?.invoke(msg) },
        onConsoleMessage = { msg -> consoleMessageListener?.invoke(msg) },
        onJavaScriptError = { err -> jsErrorListener?.invoke(err) },
        onPageFinished = { url -> pageFinishedListener?.invoke(url) }
    )

    val blinkEngine: ChromiumBlinkEngine
        get() = browserProcessHost.blinkEngine

    val njsRuntime: NjsDesktopWebRuntime
        get() = browserProcessHost.njsRuntime

    override fun loadUrl(url: String) {
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history.add(url)
        historyIndex = history.size - 1

        val resolvedHtmlPair = resolveHtmlFromUrlOrPath(url)
        val htmlText = if (resolvedHtmlPair != null) {
            currentBaseDir = parentDirOf(resolvedHtmlPair.first)
            lastLoadedHtml = resolvedHtmlPair.second
            resolvedHtmlPair.second
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
            val cleanBase = stripUrlSchemeAndQuery(baseUrl)
            if (cleanBase.isNotEmpty()) {
                val indexCheck = readPosixTextFile("$cleanBase/index.html")
                currentBaseDir = if (indexCheck != null || (!cleanBase.endsWith(".html", ignoreCase = true) && !cleanBase.endsWith(".htm", ignoreCase = true))) {
                    cleanBase.trimEnd('/', '\\')
                } else {
                    parentDirOf(cleanBase)
                }
            }
        }
        browserProcessHost.loadHtml(
            html = html,
            baseUrl = effectiveUrl,
            enableJavaScript = config.enableJavaScript
        )
    }

    override fun reload() {
        val url = getUrl() ?: "about:blank"
        val cachedHtml = lastLoadedHtml
        if (cachedHtml != null) {
            browserProcessHost.loadHtml(
                html = cachedHtml,
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
            val resolvedLocalHtml = resolveHtmlFromUrlOrPath(targetUrl)
            val htmlText = if (resolvedLocalHtml != null) {
                currentBaseDir = parentDirOf(resolvedLocalHtml.first)
                lastLoadedHtml = resolvedLocalHtml.second
                resolvedLocalHtml.second
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
            val resolvedLocalHtml = resolveHtmlFromUrlOrPath(targetUrl)
            val htmlText = if (resolvedLocalHtml != null) {
                currentBaseDir = parentDirOf(resolvedLocalHtml.first)
                lastLoadedHtml = resolvedLocalHtml.second
                resolvedLocalHtml.second
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
        val result = browserProcessHost.evaluateJavaScript(script, "<evaluateJavascript>")
        callback?.invoke(result)
    }

    override fun postWebMessage(message: String) {
        browserProcessHost.postWebMessage(message)
    }

    override fun setOnWebMessageReceived(listener: ((String) -> Unit)?) {
        messageListener = listener
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

    override fun dispose() {
        browserProcessHost.shutdown()
        super.dispose()
    }

    internal fun loadLocalTextResource(pathOrUrl: String): String? {
        if (!config.allowFileAccess && (pathOrUrl.startsWith("file://", ignoreCase = true) || !pathOrUrl.contains("://"))) {
            // Even when default config is used, local relative bundle scripts inside the loaded page directory are resolved
        }
        val clean = stripUrlSchemeAndQuery(pathOrUrl)
        if (clean.isEmpty()) return null

        // 1. Try relative to currentBaseDir first if clean is relative
        val base = currentBaseDir
        if (base != null && !isAbsolutePath(clean)) {
            val normalizedRel = clean.removePrefix("./").removePrefix("/")
            readPosixTextFile("$base/$normalizedRel")?.let { return it }
        }
        // 2. Try direct path
        return readPosixTextFile(clean)
    }

    private fun resolveHtmlFromUrlOrPath(url: String): Pair<String, String>? {
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

        // Check if clean points to a directory containing index.html
        val dirIndexCandidate = "${clean.trimEnd('/', '\\')}/index.html"
        readPosixTextFile(dirIndexCandidate)?.let { return dirIndexCandidate to it }

        // Otherwise read clean directly as a file
        readPosixTextFile(clean)?.let { return clean to it }
        return null
    }

    private fun stripUrlSchemeAndQuery(raw: String): String {
        var s = raw.trim()
        val qIdx = s.indexOf('?')
        if (qIdx >= 0) s = s.substring(0, qIdx)
        val hIdx = s.indexOf('#')
        if (hIdx >= 0) s = s.substring(0, hIdx)
        if (s.startsWith("file:///", ignoreCase = true)) {
            val after = s.substring(8)
            s = if (after.length >= 2 && after[1] == ':') after else "/$after"
        } else if (s.startsWith("file://", ignoreCase = true)) {
            s = s.substring(7)
        }
        return s.replace('\\', '/')
    }

    private fun isAbsolutePath(path: String): Boolean =
        path.startsWith("/") || (path.length >= 2 && path[1] == ':')

    private fun parentDirOf(filePath: String): String {
        val norm = filePath.replace('\\', '/').trimEnd('/')
        val lastSlash = norm.lastIndexOf('/')
        return if (lastSlash > 0) norm.substring(0, lastSlash) else "."
    }

    private fun readPosixTextFile(path: String): String? {
        val file = fopen(path, "rb") ?: return null
        return try {
            val chunks = StringBuilder()
            memScoped {
                val bufSize = 8192
                val buffer = allocArray<ByteVar>(bufSize + 1)
                while (true) {
                    val readCount = fread(buffer, 1u, bufSize.toULong(), file).toInt()
                    if (readCount <= 0) break
                    buffer[readCount] = 0
                    chunks.append(buffer.toKString())
                }
            }
            chunks.toString()
        } finally {
            fclose(file)
        }
    }
}

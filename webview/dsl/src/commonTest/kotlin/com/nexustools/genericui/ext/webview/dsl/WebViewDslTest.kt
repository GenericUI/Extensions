package com.nexustools.genericui.ext.webview.dsl

import com.nexustools.genericui.ext.dsl.buildDiv
import com.nexustools.genericui.ext.webview.WebView
import kotlin.test.Test
import kotlin.test.assertEquals

class WebViewDslTest {

    @Test
    fun testWebViewDsl() {
        val root = buildDiv {
            webViewExt(initialUrl = "https://example.com", id = "web-1")
            webView(htmlContent = "<h1>Hello</h1>", id = "web-2")
        }
        assertEquals(2, root.children.size)
        val w1 = root.children[0] as WebView
        val w2 = root.children[1] as WebView
        assertEquals("https://example.com", w1.url)
        assertEquals("<h1>Hello</h1>", w2.htmlContent)
    }
}

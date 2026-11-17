package com.nexustools.genericui.android

import com.nexustools.genericui.ext.webview.WebView
import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.ext.webview.WebViewEngineType
import com.nexustools.genericui.ext.webview.WebViewExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidNativeWebViewTest {

    @Test
    fun testAndroidNativeWebViewEngineAndViewHandle() {
        AndroidWebViewExtensionProvider.install()
        assertEquals(WebViewEngineType.ANDROID_NATIVE_WEBKIT, WebViewExtension.activeEngineType)
        assertFalse(WebViewExtension.activeEngineType.usesNjsJitRuntime)
        assertTrue(WebViewExtension.activeEngineType.usesChromium)

        val config = WebViewConfig(
            enableJavaScript = true,
            domStorageEnabled = true,
            userAgentOverride = "GenericUI-AndroidTest/1.0",
            allowFileAccess = true
        )
        val impl = AndroidNativeWebViewImpl(config)
        assertEquals("android.webkit.WebView", impl.nativeWidget.viewClassName)
        assertEquals("true", impl.nativeWidget.attributes["javaScriptEnabled"])
        assertEquals("true", impl.nativeWidget.attributes["domStorageEnabled"])
        assertEquals("true", impl.nativeWidget.attributes["allowFileAccess"])
        assertEquals("GenericUI-AndroidTest/1.0", impl.nativeWidget.attributes["userAgent"])
    }

    @Test
    fun testAndroidWebViewNavigationHistoryAndHtmlLoading() {
        AndroidWebViewExtensionProvider.install()
        val impl = AndroidNativeWebViewImpl()
        val finishedPages = ArrayList<String>()
        impl.setOnPageFinished { finishedPages.add(it) }

        impl.loadUrl("https://android.example.com/home")
        assertEquals("https://android.example.com/home", impl.getUrl())
        assertEquals("https://android.example.com/home", impl.nativeWidget.attributes["url"])
        assertFalse(impl.canGoBack())

        impl.loadUrl("https://android.example.com/details")
        assertEquals("https://android.example.com/details", impl.getUrl())
        assertTrue(impl.canGoBack())
        assertFalse(impl.canGoForward())

        impl.goBack()
        assertEquals("https://android.example.com/home", impl.getUrl())
        assertTrue(impl.canGoForward())

        impl.goForward()
        assertEquals("https://android.example.com/details", impl.getUrl())

        impl.loadHtml("<html><body><h1>Android WebView</h1></body></html>", "https://android.example.com/local")
        assertEquals("https://android.example.com/local", impl.getUrl())
        assertTrue(impl.nativeWidget.attributes["html"]?.contains("Android WebView") == true)
        assertEquals(5, finishedPages.size)
    }

    @Test
    fun testAndroidJavascriptInterfaceAndCommunicationFunctions() {
        AndroidWebViewExtensionProvider.install()
        val impl = AndroidNativeWebViewImpl()
        val receivedMessages = ArrayList<String>()
        impl.setOnWebMessageReceived { receivedMessages.add(it) }

        impl.registerCommunicationFunction("androidEcho") { payload ->
            "android:$payload"
        }

        // Direct @JavascriptInterface invocation
        assertEquals("android:test-payload", impl.call("androidEcho", "test-payload"))
        impl.postMessage("from-js-interface")
        assertTrue(receivedMessages.contains("from-js-interface"))

        // evaluateJavascript bridge invocation
        var evalOut: String? = null
        impl.evaluateJavascript("window.genericUi.call('androidEcho', 'via-eval')") { evalOut = it }
        assertEquals("android:via-eval", evalOut)

        val webView = WebView()
        assertEquals(WebViewEngineType.ANDROID_NATIVE_WEBKIT, webView.engineType)

        // Verify disabled JS blocks evaluation
        val noJs = AndroidNativeWebViewImpl(WebViewConfig(enableJavaScript = false))
        var disabledOut: String? = "non-null"
        noJs.evaluateJavascript("1 + 1") { disabledOut = it }
        assertNull(disabledOut)
    }
}

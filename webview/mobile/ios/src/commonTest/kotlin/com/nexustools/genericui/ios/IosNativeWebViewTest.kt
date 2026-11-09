package com.nexustools.genericui.ios

import com.nexustools.genericui.ext.webview.WebView
import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.ext.webview.WebViewEngineType
import com.nexustools.genericui.ext.webview.WebViewExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosNativeWebViewTest {

    @Test
    fun testIosWkWebViewEngineAndNativeView() {
        IosWebViewExtensionProvider.install()
        assertEquals(WebViewEngineType.IOS_NATIVE_WKWEBVIEW, WebViewExtension.activeEngineType)
        assertFalse(WebViewExtension.activeEngineType.usesNjsJitRuntime)
        assertFalse(WebViewExtension.activeEngineType.usesChromium)

        val impl = IosNativeWebViewImpl(
            WebViewConfig(
                enableJavaScript = true,
                domStorageEnabled = true,
                userAgentOverride = "GenericUI-iOS/1.0"
            )
        )
        assertEquals("WKWebView", impl.nativeWidget.uiViewClassName)
        assertEquals("true", impl.nativeWidget.metadata["javaScriptEnabled"])
        assertEquals("true", impl.nativeWidget.metadata["domStorageEnabled"])
        assertEquals("GenericUI-iOS/1.0", impl.nativeWidget.metadata["customUserAgent"])
    }

    @Test
    fun testIosWkWebViewNavigationAndBridge() {
        IosWebViewExtensionProvider.install()
        val impl = IosNativeWebViewImpl()
        impl.loadUrl("https://apple.example.com/first")
        impl.loadUrl("https://apple.example.com/second")
        assertTrue(impl.canGoBack())
        impl.goBack()
        assertEquals("https://apple.example.com/first", impl.getUrl())
        assertEquals("https://apple.example.com/first", impl.nativeWidget.metadata["wkWebViewUrl"])

        impl.registerCommunicationFunction("iosPing") { "ios:$it" }
        var out: String? = null
        impl.evaluateJavascript("window.genericUi.call('iosPing', 'wk')") { out = it }
        assertEquals("ios:wk", out)

        val webView = WebView()
        assertEquals(WebViewEngineType.IOS_NATIVE_WKWEBVIEW, webView.engineType)

        val disabled = IosNativeWebViewImpl(WebViewConfig(enableJavaScript = false))
        var blocked: String? = "non-null"
        disabled.evaluateJavascript("1 + 1") { blocked = it }
        assertNull(blocked)
    }
}

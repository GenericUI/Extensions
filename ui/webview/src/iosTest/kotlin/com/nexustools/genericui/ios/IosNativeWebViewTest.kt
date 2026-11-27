@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ext.webview.WebView
import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.ext.webview.WebViewEngineType
import com.nexustools.genericui.ext.webview.WebViewExtension
import com.nexustools.genericui.ext.webview.mobile.MobileWebViewExtensionProvider
import platform.WebKit.WKWebView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IosNativeWebViewTest {

    @Test
    fun testIosAndIpadosWkWebViewEngineAndNativeView() {
        assertTrue(IosWebViewExtensionProvider.supportsTarget(BackendTarget.IOS))
        assertTrue(IosWebViewExtensionProvider.supportsTarget(BackendTarget.IPadOS))

        MobileWebViewExtensionProvider.install(BackendTarget.IPadOS)
        assertEquals(WebViewEngineType.APPLE_WKWEBVIEW, WebViewExtension.activeEngineType)

        IosWebViewExtensionProvider.install()
        assertEquals(WebViewEngineType.APPLE_WKWEBVIEW, WebViewExtension.activeEngineType)
        assertFalse(WebViewExtension.activeEngineType.usesNjsJitRuntime)

        val impl = IosNativeWebViewImpl(
            WebViewConfig(
                enableJavaScript = true,
                domStorageEnabled = true,
                userAgentOverride = "GenericUI-iOS/1.0"
            )
        )
        assertSame(impl.wkWebView, impl.nativeWidget.uiView)
        assertTrue(impl.nativeWidget.uiView is WKWebView)
        assertTrue(impl.wkWebView.configuration.defaultWebpagePreferences.allowsContentJavaScript)
        assertEquals("GenericUI-iOS/1.0", impl.wkWebView.customUserAgent)
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

        impl.registerCommunicationFunction("iosPing") { "ios:$it" }
        var out: String? = null
        impl.evaluateJavascript("window.genericUi.call('iosPing', 'wk')") { out = it }
        assertEquals("ios:wk", out)

        val webView = WebView()
        assertEquals(WebViewEngineType.APPLE_WKWEBVIEW, webView.engineType)

        val disabled = IosNativeWebViewImpl(WebViewConfig(enableJavaScript = false))
        assertFalse(disabled.wkWebView.configuration.defaultWebpagePreferences.allowsContentJavaScript)
        var blocked: String? = "non-null"
        disabled.evaluateJavascript("1 + 1") { blocked = it }
        assertNull(blocked)
    }
}

package com.nexustools.genericui.ext.webview.desktop.process

import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.core.GenericUiExtensionRegistry
import com.nexustools.genericui.ext.webview.WebViewCapabilities
import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomSelectorMatcher
import com.nexustools.genericui.ext.webview.desktop.dom.WebHtml5Parser
import com.nexustools.genericui.ext.webview.desktop.engine.WebCssCascadeResolver
import com.nexustools.genericui.ext.webview.desktop.engine.WebResourceLoader
import com.nexustools.genericui.ext.webview.desktop.engine.WorkerPlatformServices
import com.nexustools.genericui.ext.webview.desktop.ipc.WebViewHostFramebuffer
import com.nexustools.genericui.ext.webview.desktop.ipc.WebViewRuntimeConfigResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AlienNjsWebViewProtocolTest {

    @Test
    fun testHtml5ParserAndSelectorsAndCssCascade() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
              <style>
                :root { --accent: rgb(16, 185, 129); }
                .card[data-kind^="pri"] > span.label:first-child {
                  color: var(--accent) !important;
                  width: calc(100px + 20px);
                }
                span.label { color: rgb(255, 0, 0); }
              </style>
            </head>
            <body>
              <div class="card" data-kind="primary">
                <span class="label">A &amp; B &lt;ok&gt;</span>
                <script>var rawCheck = 1 < 2 && 3 > 1;</script>
              </div>
              <svg width="20" height="20"><circle cx="10" cy="10" r="8" fill="#38bdf8"/></svg>
            </body>
            </html>
        """.trimIndent()
        val doc = WebHtml5Parser.parseDocument(html, "https://test.local/")
        assertNotNull(doc.doctype)
        val span = WebDomSelectorMatcher.querySelector(doc, ".card[data-kind^=\"pri\"] > span.label:first-child")
        assertNotNull(span)
        assertEquals("A & B <ok>", span.textContent)
        val script = WebDomSelectorMatcher.querySelector(doc, "script")
        assertNotNull(script)
        assertTrue(script.textContent.contains("1 < 2 && 3 > 1"))
        val circle = WebDomSelectorMatcher.querySelector(doc, "circle")
        assertNotNull(circle)
        assertEquals("http://www.w3.org/2000/svg", circle.namespaceURI)

        val resolver = WebCssCascadeResolver(
            WebResourceLoader(
                fileSystem = WorkerPlatformServices.createFileSystem(),
                httpClient = WorkerPlatformServices.createHttpClient(),
                allowFileAccess = false
            )
        )
        resolver.rebuildFromDocument(doc, 1024, 768)
        val computed = resolver.computeStyle(span, doc, 1024, 768)
        assertEquals("rgb(16, 185, 129)", computed["color"])
        assertEquals("120px", computed["width"])
    }

    @Test
    fun testRuntimeConfigResolverAndCapabilitiesPrecedence() {
        GenericUiExtensionRegistry.setExtensionProperty(
            WebViewRuntimeConfigResolver.KEY_DESKTOP_WORKER_BINARY_BASE_NAME,
            "custom-wv-worker"
        )
        GenericUiExtensionRegistry.setExtensionProperty(
            WebViewRuntimeConfigResolver.KEY_CAP_WEBGL,
            false
        )
        GenericUiExtensionRegistry.setExtensionProperty(
            WebViewRuntimeConfigResolver.KEY_CAP_SVG,
            true
        )
        try {
            val resolvedDefault = WebViewRuntimeConfigResolver.resolve(WebViewConfig())
            assertEquals("custom-wv-worker", resolvedDefault.workerBinaryBaseName)
            assertFalse(resolvedDefault.capabilities.webgl)
            assertTrue(resolvedDefault.capabilities.svg)

            val resolvedOverride = WebViewRuntimeConfigResolver.resolve(
                WebViewConfig(capabilities = WebViewCapabilities(webgl = true, canvas2d = false))
            )
            assertTrue(resolvedOverride.capabilities.webgl)
            assertFalse(resolvedOverride.capabilities.canvas2d)
        } finally {
            GenericUiExtensionRegistry.setExtensionProperty(
                WebViewRuntimeConfigResolver.KEY_DESKTOP_WORKER_BINARY_BASE_NAME,
                WebViewRuntimeConfigResolver.DEFAULT_DESKTOP_WORKER_BINARY_BASE_NAME
            )
            GenericUiExtensionRegistry.setExtensionProperty(WebViewRuntimeConfigResolver.KEY_CAP_WEBGL, true)
        }
    }

    @Test
    fun testFramebufferBinaryJsonTileCompositingAndResize() {
        val fb = WebViewHostFramebuffer(initialWidth = 64, initialHeight = 64)
        assertEquals(0L, fb.frameSequence)
        val initialBgra = fb.copyBgraSnapshot()
        assertEquals(0xFF.toByte(), initialBgra[3])

        // Deliberately write non-opaque source alpha (0x44) to verify persistent BGRA forces opaque 0xFF
        val redTileBytes = ByteArray(16 * 16 * 4) { idx ->
            when (idx % 4) {
                0 -> 0xE0.toByte()
                1 -> 0x20.toByte()
                2 -> 0x40.toByte()
                else -> 0x44.toByte()
            }
        }
        val tile = PreferenceElement.ofObject(
            linkedMapOf(
                "x" to PreferenceElement.ofInt(8),
                "y" to PreferenceElement.ofInt(8),
                "w" to PreferenceElement.ofInt(16),
                "h" to PreferenceElement.ofInt(16),
                "rgba" to PreferenceElement.ofBytes(redTileBytes)
            )
        )
        fb.applyDirtyTiles(64, 64, 1L, listOf(tile))
        val seqAfterTile1 = fb.frameSequence
        assertTrue(seqAfterTile1 > 0L)

        val rgba = fb.copyRgbaSnapshot()
        val off = (10 * 64 + 10) * 4
        assertEquals(0xE0.toByte(), rgba[off])
        assertEquals(0x20.toByte(), rgba[off + 1])
        assertEquals(0x40.toByte(), rgba[off + 2])
        assertEquals(0x44.toByte(), rgba[off + 3])

        val bgra = fb.copyBgraSnapshot()
        assertEquals(0x40.toByte(), bgra[off])
        assertEquals(0x20.toByte(), bgra[off + 1])
        assertEquals(0xE0.toByte(), bgra[off + 2])
        assertEquals(0xFF.toByte(), bgra[off + 3], "BGRA presentation buffer must force opaque alpha 0xFF")

        val intoDst = ByteArray(64 * 64 * 4)
        assertEquals(64 * 64 * 4, fb.copyBgraInto(intoDst))
        assertEquals(bgra[off], intoDst[off])
        assertEquals(0xFF.toByte(), intoDst[off + 3])

        // Same or smaller frameSeq with non-empty tiles must still advance sequenceCounter
        fb.applyDirtyTiles(64, 64, 1L, listOf(tile))
        val seqAfterTile2 = fb.frameSequence
        assertTrue(seqAfterTile2 > seqAfterTile1, "Non-empty applyDirtyTiles must increment frameSequence")

        fb.resize(128, 96)
        assertEquals(128 to 96, fb.copySize())
        assertTrue(fb.frameSequence > seqAfterTile2, "resize must increment frameSequence")
        val resized = fb.copyRgbaSnapshot()
        val offAfterResize = (10 * 128 + 10) * 4
        assertEquals(0xE0.toByte(), resized[offAfterResize])
        val resizedBgra = fb.copyBgraSnapshot()
        assertEquals(0x40.toByte(), resizedBgra[offAfterResize])
        assertEquals(0xFF.toByte(), resizedBgra[offAfterResize + 3])
        // Newly expanded blank pixel outside old bounds must be white opaque
        val expandedOff = (90 * 128 + 120) * 4
        assertEquals(0xFF.toByte(), resizedBgra[expandedOff])
        assertEquals(0xFF.toByte(), resizedBgra[expandedOff + 3])
    }
}

package com.nexustools.genericui.ext.webview.desktop

import com.nexustools.genericui.ext.webview.WebViewConsoleMessage
import com.nexustools.njs.compiler.RuntimeCompiler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NjsDesktopWebRuntimeTest {

    @Test
    fun testActiveNjsPlatformCompilerEngineInstalled() {
        val blink = ChromiumBlinkEngine()
        val runtime = NjsDesktopWebRuntime(
            blinkEngine = blink,
            onPostMessage = {}
        )
        assertTrue(
            runtime.activeEngineName == "NativeJitCompiler" || runtime.activeEngineName == "JavaTranspiler",
            "Expected active NJS engine to be NativeJitCompiler or JavaTranspiler, found ${runtime.activeEngineName}"
        )
        assertNotNull(RuntimeCompiler.activeBlockCompiler, "RuntimeCompiler.activeBlockCompiler must be installed")
    }

    @Test
    fun testComplexJavaScriptExecutionViaNjs() {
        val blink = ChromiumBlinkEngine()
        val jsErrors = ArrayList<String>()
        val runtime = NjsDesktopWebRuntime(
            blinkEngine = blink,
            onPostMessage = {},
            onJavaScriptError = { jsErrors.add(it) }
        )

        // Arithmetic, closures, recursion, loops, conditionals, objects, arrays, method calls with `this`
        val fibResult = runtime.evaluateScript(
            """
            function makeMultiplier(factor) {
                return function(x) {
                    return x * factor;
                };
            }
            var triple = makeMultiplier(3);

            function fib(n) {
                if (n <= 1) return n;
                return fib(n - 1) + fib(n - 2);
            }

            var acc = 0;
            for (var i = 1; i <= 5; i++) {
                acc += triple(i);
            }

            var calc = {
                base: 100,
                combine: function(a, b) {
                    return this.base + a + b;
                }
            };

            calc.combine(acc, fib(7));
            """.trimIndent()
        )
        // triple(1..5) sum = 3*(1+2+3+4+5) = 45; fib(7) = 13; 100 + 45 + 13 = 158
        assertEquals("158", fibResult)
        assertTrue(jsErrors.isEmpty(), "Expected no JS errors, got: $jsErrors")
    }

    @Test
    fun testBlinkHtmlParsingPreservesLoaderTagsAndExecutesScripts() {
        val blink = ChromiumBlinkEngine()
        val scripts = blink.parseHtmlDocumentWithScripts(
            """
            <!DOCTYPE html>
            <html>
            <head>
                <title>GenericUI Web Build</title>
                <script type="loader-js" src="app.js"></script>
                <link rel="resources" href="resources.json">
            </head>
            <body>
                <genericui:app></genericui:app>
                <script src="loader.js"></script>
                <script>window.__inlineRan = true;</script>
            </body>
            </html>
            """.trimIndent(),
            baseUrl = "file:///dist/index.html"
        )

        assertEquals("GenericUI Web Build", blink.documentTitle)
        assertEquals("complete", blink.readyState)
        assertNotNull(blink.rootNode.findBySelector("script[type=\"loader-js\"]"))
        assertNotNull(blink.rootNode.findBySelector("link[rel=\"resources\"]"))
        assertNotNull(blink.rootNode.findBySelector("genericui:app"))
        assertEquals(2, scripts.size)
        assertEquals("loader.js", scripts[0].src)
        assertTrue(scripts[1].inlineCode.contains("__inlineRan"))
    }

    @Test
    fun testFullChromiumBlinkDomManipulationSelectorsAndEvents() {
        val blink = ChromiumBlinkEngine()
        val jsErrors = ArrayList<String>()
        val runtime = NjsDesktopWebRuntime(
            blinkEngine = blink,
            onPostMessage = {},
            onJavaScriptError = { jsErrors.add(it) }
        )

        val scripts = blink.parseHtmlDocumentWithScripts(
            """
            <!DOCTYPE html>
            <html>
            <body>
                <div id="container" class="panel primary">
                    <span id="label">Initial</span>
                    <button id="action-btn">Run</button>
                </div>
            </body>
            </html>
            """.trimIndent()
        )
        for (s in scripts) {
            if (s.inlineCode.isNotBlank()) runtime.evaluateScript(s.inlineCode)
        }

        val domOut = runtime.evaluateScript(
            """
            var container = document.getElementById('container');
            container.classList.add('active');
            container.classList.remove('primary');
            container.style.setProperty('background-color', '#112233');
            container.setAttribute('data-mode', 'dark');

            var firstSpan = document.querySelector('#container > span');
            var badge = document.createElement('em');
            badge.id = 'badge';
            badge.textContent = 'NEW';
            container.insertBefore(badge, firstSpan);

            var clickedCount = 0;
            var btn = document.getElementById('action-btn');
            btn.addEventListener('click', function() {
                clickedCount++;
                firstSpan.textContent = 'Clicked:' + clickedCount;
            });
            btn.click();
            btn.click();

            container.className + '|' +
                container.style.getPropertyValue('background-color') + '|' +
                container.getAttribute('data-mode') + '|' +
                container.children[0].id + '|' +
                firstSpan.textContent;
            """.trimIndent()
        )

        assertEquals("panel active|#112233|dark|badge|Clicked:2", domOut)
        assertTrue(jsErrors.isEmpty(), "Expected no JS errors, got: $jsErrors")
    }

    @Test
    fun testCommunicationFunctionsTimersFetchAndGuestTestHarness() {
        val blink = ChromiumBlinkEngine()
        val consoleLogs = ArrayList<WebViewConsoleMessage>()
        val jsErrors = ArrayList<String>()
        val postMessages = ArrayList<String>()

        val runtime = NjsDesktopWebRuntime(
            blinkEngine = blink,
            onPostMessage = { postMessages.add(it) },
            onConsoleMessage = { consoleLogs.add(it) },
            onJavaScriptError = { jsErrors.add(it) },
            resourceTextLoader = { url ->
                when (url) {
                    "app.js" -> """
                        var hostEl = document.querySelector('genericui:app');
                        var root = document.createElement('div');
                        root.id = 'app-root';
                        root.textContent = 'App Loaded';
                        hostEl.appendChild(root);
                    """.trimIndent()
                    "resources.json" -> "{\"version\":1,\"items\":[\"icon.svg\"]}"
                    else -> null
                }
            }
        )

        val finishedTests = ArrayList<String>()
        runtime.registerCommunicationFunction("__jestElectricTestFinish") { payload ->
            finishedTests.add(payload)
            "{\"ok\":true}"
        }
        runtime.registerCommunicationFunction("echoHost") { payload ->
            "host:$payload"
        }

        val scripts = blink.parseHtmlDocumentWithScripts(
            """
            <!DOCTYPE html>
            <html>
            <head>
                <script type="loader-js" src="app.js"></script>
            </head>
            <body>
                <genericui:app></genericui:app>
                <script>
                    console.info("Booting guest runtime");
                    var dyn = document.createElement('script');
                    dyn.src = 'app.js';
                    document.head.appendChild(dyn);

                    var fetchedData = '';
                    fetch('resources.json').then(function(resp) {
                        return resp.text();
                    }).then(function(txt) {
                        fetchedData = txt;
                    });

                    var timerFired = false;
                    setTimeout(function() {
                        timerFired = true;
                    }, 10);

                    window.addEventListener('message', function(ev) {
                        window.genericUi.postMessage('ack:' + ev.data);
                    });

                    window.genericUi.registerCommunicationFunction('guestDouble', function(val) {
                        return String((parseInt(val, 10) || 0) * 2);
                    });

                    describe('WebRuntimeSuite', function() {
                        it('mounts app-root, fetches resources, and calls host function', function() {
                            expect(document.getElementById('app-root').textContent).toBe('App Loaded');
                            expect(window.genericUi.callCommunicationFunction('echoHost', 'abc')).toBe('host:abc');
                            expect(fetchedData.indexOf('icon.svg') >= 0).toBeTruthy();
                        });
                    });
                </script>
            </body>
            </html>
            """.trimIndent()
        )
        for (s in scripts) {
            if (s.inlineCode.isNotBlank()) {
                runtime.evaluateScript(s.inlineCode)
            }
        }

        runtime.flushImmediateTimers()
        assertEquals(
            "true",
            runtime.evaluateScript("timerFired ? 'true' : 'false'"),
            "timerFired check failed, jsErrors=$jsErrors, typeofTimerFired=${runtime.evaluateScript("typeof timerFired")}"
        )

        val delivered = runtime.deliverHostMessageToGuest("hello-guest")
        assertTrue(delivered, "delivered check failed")
        assertTrue(postMessages.contains("ack:hello-guest"), "postMessages=$postMessages")
        assertTrue(consoleLogs.any { it.level == "info" && it.message.contains("Booting guest runtime") }, "consoleLogs=$consoleLogs")

        assertEquals("24", runtime.callCommunicationFunction("guestDouble", "12"), "guestDouble check failed")
        val summaryJson = runtime.callCommunicationFunction("__runRegisteredWebTests", "")
        assertNotNull(summaryJson, "summaryJson was null")
        assertTrue(summaryJson.contains("\"passedCount\":1"), "summaryJson=$summaryJson, finishedTests=$finishedTests, jsErrors=$jsErrors")
        assertEquals(1, finishedTests.size, "finishedTests=$finishedTests")
        assertTrue(jsErrors.isEmpty(), "Expected no JS errors, got: $jsErrors")

        runtime.unregisterCommunicationFunction("echoHost")
        assertFalse(runtime.evaluateScript("typeof window.genericUi") == "undefined")
    }
}

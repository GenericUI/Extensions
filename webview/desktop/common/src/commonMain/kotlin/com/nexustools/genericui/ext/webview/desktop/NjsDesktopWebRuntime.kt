package com.nexustools.genericui.ext.webview.desktop

import com.nexustools.njs.compiler.RuntimeCompiler
import com.nexustools.njs.runtime.AbstractFunction
import com.nexustools.njs.runtime.BaseObject
import com.nexustools.njs.runtime.GenericObject
import com.nexustools.njs.runtime.Global
import com.nexustools.njs.runtime.Undefined
import com.nexustools.njs.runtime.Utilities

/**
 * Desktop-only JavaScript execution engine for `:extensions:webview:desktop` powered by
 * `NJS` (`lib/NJS`).
 *
 * - On **Desktop Native builds** (`mingwX64`, `linuxX64`, `linuxArm64`, `macosArm64`, `macosX64`),
 *   [RuntimeCompiler] compiles and executes scripts via the NJS AsmJit/SLJIT Native JIT Engine
 *   (`com.nexustools.njs.jit.*` in `nativeMain`).
 * - On **Desktop JVM builds**, [RuntimeCompiler] delegates to NJS's own Java compiler system
 *   (`JavaSourceTranspiler` + `JavaTranspiler`).
 *
 * **Strictly Desktop Only**: Never used on iOS, Android, or Web.
 */
class NjsDesktopWebRuntime(
    private val blinkEngine: ChromiumBlinkEngine,
    private val onPostMessage: (String) -> Unit
) {
    val compiler: RuntimeCompiler = RuntimeCompiler()
    val global: Global = Utilities.createExtendedGlobal(compiler) ?: Global(compiler)

    init {
        installDomBindings()
    }

    private fun installDomBindings() {
        // Bind `window`
        global.setHidden("window", global)

        // Bind `console.log`, `console.warn`, `console.error`
        val consoleObj = GenericObject(global)
        val logFn = object : AbstractFunction(global) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                println("[Chromium+NJS WebView] " + params.joinToString(" ") { it.toString() })
                return Undefined
            }
            override fun name(): String = "log"
        }
        consoleObj.setHidden("log", logFn)
        consoleObj.setHidden("warn", logFn)
        consoleObj.setHidden("error", logFn)
        global.setHidden("console", consoleObj)

        // Bind `document` (`title`, `getElementById`)
        val hostGlobal = this.global
        val documentObj = GenericObject(hostGlobal)
        documentObj.set("title", hostGlobal.wrap(blinkEngine.documentTitle))
        documentObj.setHidden("getElementById", object : AbstractFunction(hostGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val id = params.getOrNull(0)?.toString() ?: return Undefined
                val domNode = blinkEngine.rootNode.findById(id) ?: return Undefined
                val elemObj = GenericObject(hostGlobal)
                elemObj.set("id", hostGlobal.wrap(domNode.id))
                elemObj.set("tagName", hostGlobal.wrap(domNode.tagName.uppercase()))
                elemObj.set("textContent", hostGlobal.wrap(domNode.textContent))
                return elemObj
            }
            override fun name(): String = "getElementById"
        })
        global.setHidden("document", documentObj)

        // Bind `genericUi.postMessage(msg)` and `window.postMessage(msg)`
        val postMessageFn = object : AbstractFunction(global) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val msg = params.getOrNull(0)?.toString() ?: ""
                onPostMessage(msg)
                return Undefined
            }
            override fun name(): String = "postMessage"
        }
        val genericUiBridge = GenericObject(global)
        genericUiBridge.setHidden("postMessage", postMessageFn)
        global.setHidden("genericUi", genericUiBridge)
        global.setHidden("postMessage", postMessageFn)
    }

    /**
     * Compiles and executes JavaScript/TypeScript code using NJS.
     */
    fun evaluateScript(script: String, sourceName: String = "<webview-script>"): String? {
        val docObj = global.get("document") as? GenericObject
        docObj?.set("title", global.wrap(blinkEngine.documentTitle))

        val compiled = compiler.compile(script, sourceName, false)
        val result = compiled.exec(global, null)
        if (docObj != null) {
            blinkEngine.documentTitle = docObj.get("title").toString()
        }
        return if (result == null || result === Undefined) null else result.toString()
    }
}

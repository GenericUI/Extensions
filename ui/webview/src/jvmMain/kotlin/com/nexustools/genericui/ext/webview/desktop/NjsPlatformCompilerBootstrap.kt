package com.nexustools.genericui.ext.webview.desktop

import com.nexustools.njs.compiler.JavaTranspiler

actual object NjsPlatformCompilerBootstrap {
    actual fun ensureInitialized() {
        JavaTranspiler.installIntoRuntimeCompiler()
    }

    actual fun activeEngineName(): String = "JavaTranspiler"
}

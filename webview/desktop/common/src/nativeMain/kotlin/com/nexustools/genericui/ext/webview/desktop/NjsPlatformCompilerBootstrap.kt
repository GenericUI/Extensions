package com.nexustools.genericui.ext.webview.desktop

import com.nexustools.njs.compiler.NativeJitCompiler

actual object NjsPlatformCompilerBootstrap {
    actual fun ensureInitialized() {
        NativeJitCompiler.installIntoRuntimeCompiler()
    }

    actual fun activeEngineName(): String = "NativeJitCompiler"
}

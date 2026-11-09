package com.nexustools.genericui.ext.webview.desktop

/**
 * Installs the platform-appropriate `NJS` compiler into `RuntimeCompiler` for `:extensions:webview:desktop`:
 * - **Desktop Native** (`mingwX64`, `linuxX64`, `linuxArm64`, `macosArm64`): `NativeJitCompiler`
 *   (AsmJit/SLJIT native machine code + `NjsJitEngine`).
 * - **Desktop JVM / Swing**: `JavaTranspiler` (`JavaSourceTranspiler` + `javax.tools.JavaCompiler`).
 */
expect object NjsPlatformCompilerBootstrap {
    fun ensureInitialized()
    fun activeEngineName(): String
}

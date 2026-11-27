package com.nexustools.genericui.ext.svg

import com.nexustools.genericui.core.NativeFactory

/**
 * Registry and platform provider bridge for `:extensions:graphics:svg`.
 *
 * - System targets (`Win32`, `macOS`, `GTK`, `KDE`, `Swing`, `Android`, `iOS`) register
 *   `SystemSvgProvider` delegating software-rasterized vector buffers to `factory.createImage()`.
 * - Web targets (`js`, `wasmJs`) register `WebSvgProvider` rendering live namespace-qualified
 *   `<svg>` DOM trees.
 * - `:extensions:graphics:svg:alien` can plug in via `NativeFactory.createExtensionNativeImpl`.
 */
object SvgExtension {
    private var svgNativeFactory: ((Svg, NativeFactory) -> SvgNativeImpl<*>)? = null
    private var isInstallingDefaults: Boolean = false

    fun registerProvider(factory: (Svg, NativeFactory) -> SvgNativeImpl<*>) {
        svgNativeFactory = factory
    }

    fun hasRegisteredProvider(): Boolean = svgNativeFactory != null

    fun resetProviderForTests() {
        svgNativeFactory = null
    }

    private fun ensureDefaultProvidersInstalled() {
        if (isInstallingDefaults || svgNativeFactory != null) return
        isInstallingDefaults = true
        try {
            installPlatformDefaultSvgProviders()
        } finally {
            isInstallingDefaults = false
        }
    }

    fun createSvgNative(svg: Svg, factory: NativeFactory): SvgNativeImpl<*> {
        ensureDefaultProvidersInstalled()
        return svgNativeFactory?.invoke(svg, factory)
            ?: throw IllegalStateException("No SvgNativeImpl provider registered in SvgExtension")
    }
}

package com.nexustools.genericui.ext.pip

import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.core.ApplicationLifecycleExtensionHook
import com.nexustools.genericui.core.GenericUiAppDescriptor
import com.nexustools.genericui.core.GenericUiExtensionRegistry
import com.nexustools.genericui.ext.overlay.OverlayExtension
import com.nexustools.genericui.ext.pip.res.GeneratedResourceRegistry
import com.nexustools.genericui.ext.pip.res.R
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.sass.SassCompiler
import com.nexustools.genericui.style.theme.ThemeStyleSheetRegistry

/**
 * Central entry point for `:extensions:ui:pip` stylesheet compilation and registration.
 */
object PipExtension {

    const val DEFAULT_STYLE_MODULE_ID: String = "ext:pip:style"

    private var cachedDefaultStyleSheet: CssStyleSheet? = null
    private var lifecycleHookInstalled: Boolean = false

    /**
     * Lazily compiles and caches `:extensions:ui:pip`'s generic, theme-independent `style.scss`
     * stylesheet (`R.style.style`). Never compiles `theme.scss`.
     */
    fun defaultStyleSheet(): CssStyleSheet {
        cachedDefaultStyleSheet?.let { return it }
        GeneratedResourceRegistry.ensureInitialized()
        val rawScss = Resources.getRawText(R.style.style).orEmpty()
        return SassCompiler.compileToStyleSheet(rawScss).also { sheet ->
            cachedDefaultStyleSheet = sheet
        }
    }

    /**
     * Composes Overlay + PiP base stylesheets with any active registered consumer theme sheets
     * so standalone floating PiP windows receive complete generic + consumer styling.
     */
    fun composeEffectiveStyleSheet(): CssStyleSheet {
        OverlayExtension.ensureStyleSheetRegistered()
        ensureStyleSheetRegistered()
        val sheets = buildList {
            add(OverlayExtension.defaultStyleSheet())
            add(defaultStyleSheet())
            for ((moduleId, sheet) in ThemeStyleSheetRegistry.getCompiledThemeEntries()) {
                if (moduleId != OverlayExtension.DEFAULT_STYLE_MODULE_ID &&
                    moduleId != DEFAULT_STYLE_MODULE_ID
                ) {
                    add(sheet)
                }
            }
        }
        return CssStyleSheet.combineCascade(sheets)
    }

    /**
     * Registers [defaultStyleSheet] with [ThemeStyleSheetRegistry] (`"ext:pip:style"`) and installs
     * an application lifecycle hook so consumer apps receive PiP chrome styling automatically.
     */
    fun ensureStyleSheetRegistered(): CssStyleSheet {
        OverlayExtension.ensureStyleSheetRegistered()
        val sheet = defaultStyleSheet()
        if (ThemeStyleSheetRegistry.getCompiledThemeEntries().none { it.first == DEFAULT_STYLE_MODULE_ID }) {
            ThemeStyleSheetRegistry.registerCompiledThemeStyleSheet(DEFAULT_STYLE_MODULE_ID, sheet)
        }
        if (!lifecycleHookInstalled) {
            lifecycleHookInstalled = true
            GenericUiExtensionRegistry.registerLifecycleHook(
                object : ApplicationLifecycleExtensionHook {
                    override fun onAppPrepared(app: IApplication, descriptor: GenericUiAppDescriptor) {
                        if (ThemeStyleSheetRegistry.getCompiledThemeEntries().none { it.first == DEFAULT_STYLE_MODULE_ID }) {
                            ThemeStyleSheetRegistry.registerCompiledThemeStyleSheet(DEFAULT_STYLE_MODULE_ID, defaultStyleSheet())
                        }
                    }
                }
            )
        }
        return sheet
    }
}

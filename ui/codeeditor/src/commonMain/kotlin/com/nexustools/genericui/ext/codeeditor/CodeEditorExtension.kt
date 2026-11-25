package com.nexustools.genericui.ext.codeeditor

import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.core.ApplicationLifecycleExtensionHook
import com.nexustools.genericui.core.GenericUiAppDescriptor
import com.nexustools.genericui.core.GenericUiExtensionRegistry
import com.nexustools.genericui.ext.codeeditor.res.GeneratedResourceRegistry
import com.nexustools.genericui.ext.codeeditor.res.R
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.sass.SassCompiler
import com.nexustools.genericui.style.theme.ThemeStyleSheetRegistry

/**
 * Central entry point for `:extensions:ui:codeeditor` stylesheet compilation and registration.
 */
object CodeEditorExtension {

    const val DEFAULT_STYLE_MODULE_ID: String = "ext:codeeditor:style"

    private var cachedDefaultStyleSheet: CssStyleSheet? = null
    private var lifecycleHookInstalled: Boolean = false

    fun defaultStyleSheet(): CssStyleSheet {
        cachedDefaultStyleSheet?.let { return it }
        GeneratedResourceRegistry.ensureInitialized()
        val rawScss = Resources.getRawText(R.style.style).orEmpty()
        return SassCompiler.compileToStyleSheet(rawScss).also { sheet ->
            cachedDefaultStyleSheet = sheet
        }
    }

    fun ensureStyleSheetRegistered(): CssStyleSheet {
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

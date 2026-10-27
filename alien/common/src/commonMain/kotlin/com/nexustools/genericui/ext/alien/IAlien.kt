package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.res.ResourceType
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet

/**
 * Common contract implemented by all Alien root containers and fragments ([AlienContainer],
 * [AlienFragment], [AlienScrollFragment]).
 *
 * - Starts with the precompiled Bootstrap 5.3 stylesheet ([AlienPrecompiledBootstrapStyleSheet],
 *   identified by [BOOTSTRAP_STYLE_SHEET_ID]) active by default at index `0`.
 * - Allows callers to remove the default Bootstrap stylesheet via `removeStyleSheet(IAlien.BOOTSTRAP_STYLE_SHEET_ID)`
 *   (or `removeStyleSheet(AlienPrecompiledBootstrapStyleSheet)`) and add/remove custom stylesheets
 *   by path, [ResourceID], [PrecompiledStyleSheet], or [CssStyleSheet].
 * - Provides [insertApplicationStyleSheets] (`1` by default) to control where application/window
 *   stylesheets (`$ApplicationStyleSheets`) are inserted into the Alien stylesheet cascade
 *   (`[Bootstrap, $ApplicationStyleSheets, AllAdditionalStyleSheets...]` when `1`, disabled when `-1`).
 */
interface IAlien {

    /**
     * Controls the index at which application/window stylesheets (`$ApplicationStyleSheets`) are
     * inserted into this [IAlien]'s stylesheet cascade (`1` by default):
     * - `1` (default): `[Bootstrap, $ApplicationStyleSheets, AllAdditionalStyleSheets...]`
     * - `0`: `[$ApplicationStyleSheets, Bootstrap, AllAdditionalStyleSheets...]`
     * - `-1` (or `< 0`): Application stylesheets are disabled for this [IAlien].
     */
    var insertApplicationStyleSheets: Int

    fun addStyleSheet(styleSheetPath: String)

    fun addStyleSheet(styleSheetResourceId: ResourceID)

    fun addStyleSheet(styleSheet: PrecompiledStyleSheet)

    fun addStyleSheet(styleSheet: CssStyleSheet)

    fun removeStyleSheet(styleSheetPath: String): Boolean

    fun removeStyleSheet(styleSheetResourceId: ResourceID): Boolean

    fun removeStyleSheet(styleSheet: PrecompiledStyleSheet): Boolean

    fun removeStyleSheet(styleSheet: CssStyleSheet): Boolean

    fun hasStyleSheet(styleSheetPath: String): Boolean

    fun hasStyleSheet(styleSheetResourceId: ResourceID): Boolean

    fun hasStyleSheet(styleSheet: PrecompiledStyleSheet): Boolean

    fun hasStyleSheet(styleSheet: CssStyleSheet): Boolean =
        getStyleSheets().any { it.styleSheet === styleSheet }

    fun getStyleSheets(): List<PrecompiledStyleSheet>

    fun getStyleSheetPaths(): List<String>

    val isBootstrapStyleSheetEnabled: Boolean
        get() = hasStyleSheet(BOOTSTRAP_STYLE_SHEET_ID)

    fun repaintAlien(deltaMs: Float = 16.67f)

    companion object {
        /**
         * Canonical [ResourceID] for the default precompiled Bootstrap 5.3 stylesheet
         * ([AlienPrecompiledBootstrapStyleSheet]) attached to every [IAlien] by default.
         *
         * Pass this ID to [removeStyleSheet] to disable the default Bootstrap stylesheet on an
         * [AlienContainer] or [AlienFragment], or to [addStyleSheet] to re-enable it.
         */
        val BOOTSTRAP_STYLE_SHEET_ID: ResourceID = ResourceID(
            id = 0x7F06_0001,
            type = ResourceType.STYLE,
            name = "bootstrap",
            relativePath = "styles/bootstrap.scss"
        )

        val BOOTSTRAP_RESOURCE_ID: ResourceID = BOOTSTRAP_STYLE_SHEET_ID
    }
}

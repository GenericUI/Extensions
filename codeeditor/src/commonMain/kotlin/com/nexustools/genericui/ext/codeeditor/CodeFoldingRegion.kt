package com.nexustools.genericui.ext.codeeditor

/**
 * Collapsible source-code region (`{ ... }`, imports, comments) managed by [IdeCodeEditor].
 */
data class CodeFoldingRegion(
    val startLine: Int,
    val endLine: Int,
    val placeholder: String = "...",
    var isCollapsed: Boolean = false
) {
    fun containsHiddenLine(line: Int): Boolean =
        isCollapsed && line > startLine && line <= endLine
}

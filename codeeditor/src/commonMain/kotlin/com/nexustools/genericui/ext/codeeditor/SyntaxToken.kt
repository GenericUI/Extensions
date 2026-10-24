package com.nexustools.genericui.ext.codeeditor

/**
 * Single syntax-highlighted span within a line of source code.
 */
data class SyntaxToken(
    val type: SyntaxTokenType,
    val startColumn: Int,
    val endColumn: Int,
    val text: String
) {
    val length: Int
        get() = endColumn - startColumn
}

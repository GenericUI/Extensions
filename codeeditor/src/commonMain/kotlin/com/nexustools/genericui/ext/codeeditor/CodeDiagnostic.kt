package com.nexustools.genericui.ext.codeeditor

/**
 * Inline compiler/linter diagnostic rendered with squiggle underlines and hover popovers in [IdeCodeEditor].
 */
data class CodeDiagnostic(
    val range: TextRange,
    val message: String,
    val severity: CodeDiagnosticSeverity = CodeDiagnosticSeverity.ERROR,
    val code: String? = null,
    val quickFixes: List<String> = emptyList()
)

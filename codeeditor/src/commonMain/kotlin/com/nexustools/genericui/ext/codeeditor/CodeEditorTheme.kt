package com.nexustools.genericui.ext.codeeditor

import com.nexustools.genericui.style.color.RgbaColor

/**
 * Complete IDE syntax and chrome color theme for [IdeCodeEditor] and [CodeMinimap],
 * including built-in presets ([DARK_IDE], [LIGHT_IDE], [DRACULA], [MONOKAI]).
 */
data class CodeEditorTheme(
    val name: String,
    val background: RgbaColor,
    val gutterBackground: RgbaColor,
    val gutterText: RgbaColor,
    val activeLineBackground: RgbaColor,
    val selectionBackground: RgbaColor,
    val caretColor: RgbaColor,
    val bracketMatchBorder: RgbaColor,
    val errorSquiggle: RgbaColor,
    val warningSquiggle: RgbaColor,
    val infoSquiggle: RgbaColor,
    val hintSquiggle: RgbaColor,
    val minimapViewportOverlay: RgbaColor,
    val tokenColors: Map<SyntaxTokenType, RgbaColor>
) {
    fun colorForToken(type: SyntaxTokenType): RgbaColor =
        tokenColors[type] ?: tokenColors[SyntaxTokenType.PLAIN] ?: RgbaColor.WHITE

    fun colorForDiagnostic(severity: CodeDiagnosticSeverity): RgbaColor = when (severity) {
        CodeDiagnosticSeverity.ERROR -> errorSquiggle
        CodeDiagnosticSeverity.WARNING -> warningSquiggle
        CodeDiagnosticSeverity.INFO -> infoSquiggle
        CodeDiagnosticSeverity.HINT -> hintSquiggle
    }

    companion object {
        val DARK_IDE = CodeEditorTheme(
            name = "Dark IDE (Darcula / VS Code Dark+)",
            background = RgbaColor.fromHex("#1E1E2E"),
            gutterBackground = RgbaColor.fromHex("#181825"),
            gutterText = RgbaColor.fromHex("#6C7086"),
            activeLineBackground = RgbaColor.fromHex("#2A2B3C"),
            selectionBackground = RgbaColor(59, 130, 246, 85),
            caretColor = RgbaColor.fromHex("#F5E0DC"),
            bracketMatchBorder = RgbaColor.fromHex("#F9E2AF"),
            errorSquiggle = RgbaColor.fromHex("#F38BA8"),
            warningSquiggle = RgbaColor.fromHex("#FAB387"),
            infoSquiggle = RgbaColor.fromHex("#89B4FA"),
            hintSquiggle = RgbaColor.fromHex("#94E2D5"),
            minimapViewportOverlay = RgbaColor(255, 255, 255, 36),
            tokenColors = mapOf(
                SyntaxTokenType.KEYWORD to RgbaColor.fromHex("#CBA6F7"),
                SyntaxTokenType.TYPE_IDENTIFIER to RgbaColor.fromHex("#F9E2AF"),
                SyntaxTokenType.FUNCTION_NAME to RgbaColor.fromHex("#89B4FA"),
                SyntaxTokenType.VARIABLE to RgbaColor.fromHex("#CDD6F4"),
                SyntaxTokenType.PROPERTY to RgbaColor.fromHex("#89DCEB"),
                SyntaxTokenType.STRING_LITERAL to RgbaColor.fromHex("#A6E3A1"),
                SyntaxTokenType.NUMBER_LITERAL to RgbaColor.fromHex("#FAB387"),
                SyntaxTokenType.BOOLEAN_LITERAL to RgbaColor.fromHex("#FAB387"),
                SyntaxTokenType.COMMENT_LINE to RgbaColor.fromHex("#6C7086"),
                SyntaxTokenType.COMMENT_BLOCK to RgbaColor.fromHex("#6C7086"),
                SyntaxTokenType.DOC_COMMENT to RgbaColor.fromHex("#7F849C"),
                SyntaxTokenType.ANNOTATION to RgbaColor.fromHex("#F5C2E7"),
                SyntaxTokenType.OPERATOR to RgbaColor.fromHex("#94E2D5"),
                SyntaxTokenType.PUNCTUATION to RgbaColor.fromHex("#BAC2DE"),
                SyntaxTokenType.TAG_NAME to RgbaColor.fromHex("#F38BA8"),
                SyntaxTokenType.ATTRIBUTE_NAME to RgbaColor.fromHex("#FAB387"),
                SyntaxTokenType.WHITESPACE to RgbaColor.TRANSPARENT,
                SyntaxTokenType.PLAIN to RgbaColor.fromHex("#CDD6F4")
            )
        )

        val LIGHT_IDE = CodeEditorTheme(
            name = "Light IDE (IntelliJ Light)",
            background = RgbaColor.fromHex("#FFFFFF"),
            gutterBackground = RgbaColor.fromHex("#F3F4F6"),
            gutterText = RgbaColor.fromHex("#9CA3AF"),
            activeLineBackground = RgbaColor.fromHex("#EFF6FF"),
            selectionBackground = RgbaColor(59, 130, 246, 60),
            caretColor = RgbaColor.fromHex("#111827"),
            bracketMatchBorder = RgbaColor.fromHex("#2563EB"),
            errorSquiggle = RgbaColor.fromHex("#DC2626"),
            warningSquiggle = RgbaColor.fromHex("#D97706"),
            infoSquiggle = RgbaColor.fromHex("#2563EB"),
            hintSquiggle = RgbaColor.fromHex("#059669"),
            minimapViewportOverlay = RgbaColor(0, 0, 0, 28),
            tokenColors = mapOf(
                SyntaxTokenType.KEYWORD to RgbaColor.fromHex("#7C3AED"),
                SyntaxTokenType.TYPE_IDENTIFIER to RgbaColor.fromHex("#0F766E"),
                SyntaxTokenType.FUNCTION_NAME to RgbaColor.fromHex("#1D4ED8"),
                SyntaxTokenType.VARIABLE to RgbaColor.fromHex("#1F2937"),
                SyntaxTokenType.PROPERTY to RgbaColor.fromHex("#0369A1"),
                SyntaxTokenType.STRING_LITERAL to RgbaColor.fromHex("#15803D"),
                SyntaxTokenType.NUMBER_LITERAL to RgbaColor.fromHex("#B45309"),
                SyntaxTokenType.BOOLEAN_LITERAL to RgbaColor.fromHex("#B45309"),
                SyntaxTokenType.COMMENT_LINE to RgbaColor.fromHex("#6B7280"),
                SyntaxTokenType.COMMENT_BLOCK to RgbaColor.fromHex("#6B7280"),
                SyntaxTokenType.DOC_COMMENT to RgbaColor.fromHex("#4B5563"),
                SyntaxTokenType.ANNOTATION to RgbaColor.fromHex("#9333EA"),
                SyntaxTokenType.OPERATOR to RgbaColor.fromHex("#374151"),
                SyntaxTokenType.PUNCTUATION to RgbaColor.fromHex("#4B5563"),
                SyntaxTokenType.TAG_NAME to RgbaColor.fromHex("#BE123C"),
                SyntaxTokenType.ATTRIBUTE_NAME to RgbaColor.fromHex("#B45309"),
                SyntaxTokenType.WHITESPACE to RgbaColor.TRANSPARENT,
                SyntaxTokenType.PLAIN to RgbaColor.fromHex("#1F2937")
            )
        )

        val DRACULA = DARK_IDE.copy(
            name = "Dracula",
            background = RgbaColor.fromHex("#282A36"),
            gutterBackground = RgbaColor.fromHex("#21222C"),
            activeLineBackground = RgbaColor.fromHex("#44475A")
        )

        val MONOKAI = DARK_IDE.copy(
            name = "Monokai Pro",
            background = RgbaColor.fromHex("#272822"),
            gutterBackground = RgbaColor.fromHex("#1E1F1C"),
            activeLineBackground = RgbaColor.fromHex("#3E3D32")
        )
    }
}

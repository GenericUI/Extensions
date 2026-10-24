package com.nexustools.genericui.ext.codeeditor

/**
 * Lexical syntax token categories used by [SyntaxTokenizer] and colored by [CodeEditorTheme].
 */
enum class SyntaxTokenType {
    KEYWORD,
    TYPE_IDENTIFIER,
    FUNCTION_NAME,
    VARIABLE,
    PROPERTY,
    STRING_LITERAL,
    NUMBER_LITERAL,
    BOOLEAN_LITERAL,
    COMMENT_LINE,
    COMMENT_BLOCK,
    DOC_COMMENT,
    ANNOTATION,
    OPERATOR,
    PUNCTUATION,
    TAG_NAME,
    ATTRIBUTE_NAME,
    WHITESPACE,
    PLAIN
}

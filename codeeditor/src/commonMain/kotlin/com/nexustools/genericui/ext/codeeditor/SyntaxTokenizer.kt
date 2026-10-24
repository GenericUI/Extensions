package com.nexustools.genericui.ext.codeeditor

/**
 * Multi-language lexical tokenizer producing per-line [SyntaxToken] lists with stateful
 * multi-line block comment and multi-line triple-quoted string tracking.
 */
class SyntaxTokenizer(
    var language: CodeLanguage = CodeLanguage.KOTLIN
) {

    fun tokenizeDocument(lines: List<String>): List<List<SyntaxToken>> {
        var inBlockComment = false
        var inMultiLineString = false
        val result = ArrayList<List<SyntaxToken>>(lines.size)

        for (line in lines) {
            val tokens = ArrayList<SyntaxToken>()
            val state = tokenizeLineStateful(line, inBlockComment, inMultiLineString, tokens)
            inBlockComment = state.first
            inMultiLineString = state.second
            result.add(tokens)
        }
        return result
    }

    fun tokenizeLine(line: String, inBlockCommentStart: Boolean = false): List<SyntaxToken> {
        val tokens = ArrayList<SyntaxToken>()
        tokenizeLineStateful(line, inBlockCommentStart, false, tokens)
        return tokens
    }

    private fun tokenizeLineStateful(
        line: String,
        initialBlockComment: Boolean,
        initialMultiLineString: Boolean,
        outTokens: MutableList<SyntaxToken>
    ): Pair<Boolean, Boolean> {
        var inBlockComment = initialBlockComment
        var inMultiString = initialMultiLineString
        val len = line.length
        var i = 0

        val blockStart = language.blockCommentStart
        val blockEnd = language.blockCommentEnd
        val lineComment = language.lineCommentPrefix
        val keywords = keywordsFor(language)

        while (i < len) {
            if (inBlockComment && blockEnd != null) {
                val endIdx = line.indexOf(blockEnd, i)
                if (endIdx < 0) {
                    outTokens.add(SyntaxToken(SyntaxTokenType.COMMENT_BLOCK, i, len, line.substring(i)))
                    return true to inMultiString
                } else {
                    val stop = endIdx + blockEnd.length
                    outTokens.add(SyntaxToken(SyntaxTokenType.COMMENT_BLOCK, i, stop, line.substring(i, stop)))
                    i = stop
                    inBlockComment = false
                    continue
                }
            }

            if (inMultiString) {
                val endIdx = line.indexOf("\"\"\"", i)
                if (endIdx < 0) {
                    outTokens.add(SyntaxToken(SyntaxTokenType.STRING_LITERAL, i, len, line.substring(i)))
                    return inBlockComment to true
                } else {
                    val stop = endIdx + 3
                    outTokens.add(SyntaxToken(SyntaxTokenType.STRING_LITERAL, i, stop, line.substring(i, stop)))
                    i = stop
                    inMultiString = false
                    continue
                }
            }

            val ch = line[i]

            // Whitespace
            if (ch.isWhitespace()) {
                val start = i
                while (i < len && line[i].isWhitespace()) i++
                outTokens.add(SyntaxToken(SyntaxTokenType.WHITESPACE, start, i, line.substring(start, i)))
                continue
            }

            // Line comment
            if (lineComment != null && line.startsWith(lineComment, i)) {
                outTokens.add(SyntaxToken(SyntaxTokenType.COMMENT_LINE, i, len, line.substring(i)))
                break
            }

            // Block comment
            if (blockStart != null && blockEnd != null && line.startsWith(blockStart, i)) {
                val isDoc = line.startsWith("/**", i) || line.startsWith("///", i)
                val tokenType = if (isDoc) SyntaxTokenType.DOC_COMMENT else SyntaxTokenType.COMMENT_BLOCK
                val searchFrom = i + blockStart.length
                val endIdx = line.indexOf(blockEnd, searchFrom)
                if (endIdx < 0) {
                    outTokens.add(SyntaxToken(tokenType, i, len, line.substring(i)))
                    inBlockComment = true
                    break
                } else {
                    val stop = endIdx + blockEnd.length
                    outTokens.add(SyntaxToken(tokenType, i, stop, line.substring(i, stop)))
                    i = stop
                    continue
                }
            }

            // Triple-quoted string
            if (line.startsWith("\"\"\"", i)) {
                val searchFrom = i + 3
                val endIdx = line.indexOf("\"\"\"", searchFrom)
                if (endIdx < 0) {
                    outTokens.add(SyntaxToken(SyntaxTokenType.STRING_LITERAL, i, len, line.substring(i)))
                    inMultiString = true
                    break
                } else {
                    val stop = endIdx + 3
                    outTokens.add(SyntaxToken(SyntaxTokenType.STRING_LITERAL, i, stop, line.substring(i, stop)))
                    i = stop
                    continue
                }
            }

            // Single/double/backtick string literals
            if (ch == '"' || ch == '\'' || ch == '`') {
                val quote = ch
                val start = i
                i++
                var escaped = false
                while (i < len) {
                    val c = line[i++]
                    if (escaped) {
                        escaped = false
                    } else if (c == '\\') {
                        escaped = true
                    } else if (c == quote) {
                        break
                    }
                }
                outTokens.add(SyntaxToken(SyntaxTokenType.STRING_LITERAL, start, i, line.substring(start, i)))
                continue
            }

            // Annotations / decorators (@Override, @Composable, #pragma)
            if (ch == '@' && i + 1 < len && (line[i + 1].isLetter() || line[i + 1] == '_')) {
                val start = i
                i++
                while (i < len && (line[i].isLetterOrDigit() || line[i] == '_' || line[i] == '.')) i++
                outTokens.add(SyntaxToken(SyntaxTokenType.ANNOTATION, start, i, line.substring(start, i)))
                continue
            }

            // XML / HTML / SVG tags
            if ((language == CodeLanguage.XML || language == CodeLanguage.SVG_XML || language == CodeLanguage.HTML) &&
                ch == '<'
            ) {
                val start = i
                i++
                if (i < len && line[i] == '/') i++
                while (i < len && (line[i].isLetterOrDigit() || line[i] == '-' || line[i] == ':' || line[i] == '_')) i++
                if (i > start + 1) {
                    outTokens.add(SyntaxToken(SyntaxTokenType.TAG_NAME, start, i, line.substring(start, i)))
                    continue
                } else {
                    i = start
                }
            }

            // Number literals (hex, binary, float, int)
            if (ch.isDigit() || (ch == '.' && i + 1 < len && line[i + 1].isDigit())) {
                val start = i
                if (ch == '0' && i + 1 < len && (line[i + 1] == 'x' || line[i + 1] == 'X' || line[i + 1] == 'b' || line[i + 1] == 'B')) {
                    i += 2
                    while (i < len && (line[i].isLetterOrDigit() || line[i] == '_')) i++
                } else {
                    while (i < len && (line[i].isDigit() || line[i] == '.' || line[i] == '_' || line[i] == 'e' || line[i] == 'E' || line[i] == 'f' || line[i] == 'F' || line[i] == 'L')) i++
                }
                outTokens.add(SyntaxToken(SyntaxTokenType.NUMBER_LITERAL, start, i, line.substring(start, i)))
                continue
            }

            // Identifiers, Keywords, Booleans, Types, Functions
            if (ch.isLetter() || ch == '_' || ch == '$') {
                val start = i
                while (i < len && (line[i].isLetterOrDigit() || line[i] == '_' || line[i] == '$' || line[i] == '-')) i++
                val word = line.substring(start, i)
                val nextNonSpace = line.substring(i).firstOrNull { !it.isWhitespace() }

                val type = when {
                    word == "true" || word == "false" || word == "null" || word == "None" || word == "nil" ->
                        SyntaxTokenType.BOOLEAN_LITERAL
                    word in keywords ->
                        SyntaxTokenType.KEYWORD
                    nextNonSpace == '(' ->
                        SyntaxTokenType.FUNCTION_NAME
                    word.first().isUpperCase() ->
                        SyntaxTokenType.TYPE_IDENTIFIER
                    nextNonSpace == '=' && (language == CodeLanguage.XML || language == CodeLanguage.SVG_XML || language == CodeLanguage.HTML) ->
                        SyntaxTokenType.ATTRIBUTE_NAME
                    start > 0 && line[start - 1] == '.' ->
                        SyntaxTokenType.PROPERTY
                    else ->
                        SyntaxTokenType.VARIABLE
                }
                outTokens.add(SyntaxToken(type, start, i, word))
                continue
            }

            // Operators & Punctuation
            val start = i
            i++
            val tokenType = if (ch in "{}()[].,;:") SyntaxTokenType.PUNCTUATION else SyntaxTokenType.OPERATOR
            outTokens.add(SyntaxToken(tokenType, start, i, line.substring(start, i)))
        }

        return inBlockComment to inMultiString
    }

    companion object {
        private val COMMON_KEYWORDS = setOf(
            "fun", "val", "var", "class", "interface", "object", "enum", "data", "sealed", "open",
            "override", "private", "protected", "public", "internal", "abstract", "final", "static",
            "const", "suspend", "inline", "reified", "companion", "init", "constructor", "package",
            "import", "return", "if", "else", "when", "switch", "case", "default", "for", "while",
            "do", "break", "continue", "try", "catch", "finally", "throw", "new", "this", "super",
            "is", "in", "as", "by", "where", "typealias", "expect", "actual", "function", "let",
            "async", "await", "yield", "export", "from", "extends", "implements", "typeof", "void",
            "def", "lambda", "pass", "with", "elif", "except", "raise", "fn", "mut", "impl", "trait",
            "struct", "pub", "mod", "use", "match", "loop", "move", "ref", "unsafe", "extern",
            "func", "chan", "defer", "go", "select", "map", "uniform", "varying", "attribute",
            "layout", "vec2", "vec3", "vec4", "mat3", "mat4", "sampler2D", "precision", "highp"
        )

        private val SQL_KEYWORDS = setOf(
            "select", "from", "where", "insert", "into", "values", "update", "set", "delete",
            "create", "table", "drop", "alter", "index", "join", "inner", "left", "right", "outer",
            "on", "group", "by", "order", "asc", "desc", "limit", "offset", "having", "distinct",
            "and", "or", "not", "null", "primary", "key", "foreign", "references", "as"
        )

        fun keywordsFor(language: CodeLanguage): Set<String> = when (language) {
            CodeLanguage.SQL -> SQL_KEYWORDS
            CodeLanguage.JSON, CodeLanguage.PLAIN_TEXT -> emptySet()
            else -> COMMON_KEYWORDS
        }
    }
}

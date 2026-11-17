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
        val isStylesheet = (language == CodeLanguage.SCSS || language == CodeLanguage.CSS)

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

            // Annotations / decorators / SCSS @directives (@Override, @Composable, @mixin, @include, @media, @keyframes)
            if (ch == '@' && i + 1 < len && (line[i + 1].isLetter() || line[i + 1] == '_' || (isStylesheet && line[i + 1] == '-'))) {
                val start = i
                i++
                while (i < len && (line[i].isLetterOrDigit() || line[i] == '_' || line[i] == '.' || (isStylesheet && line[i] == '-'))) i++
                outTokens.add(SyntaxToken(SyntaxTokenType.ANNOTATION, start, i, line.substring(start, i)))
                continue
            }

            // SCSS/CSS Specific Lexical Rules
            if (isStylesheet) {
                // 1. !important / !default / !global / !optional flags
                if (ch == '!' && i + 1 < len && line[i + 1].isLetter()) {
                    val start = i
                    i++
                    while (i < len && line[i].isLetter()) i++
                    val bangWord = line.substring(start, i).lowercase()
                    val tokType = if (bangWord in SCSS_BANG_FLAGS) SyntaxTokenType.KEYWORD else SyntaxTokenType.OPERATOR
                    outTokens.add(SyntaxToken(tokType, start, i, line.substring(start, i)))
                    continue
                }

                // 2. Pseudo-classes (&:hover, &:focus, &:active, :hover, :focus, :active, :disabled, :checked)
                if (line.startsWith("&:", i) && i + 2 < len && line[i + 2].isLetter()) {
                    val start = i
                    i += 2
                    while (i < len && (line[i].isLetterOrDigit() || line[i] == '-')) i++
                    outTokens.add(SyntaxToken(SyntaxTokenType.KEYWORD, start, i, line.substring(start, i)))
                    continue
                }
                if (ch == ':' && i + 1 < len && line[i + 1].isLetter()) {
                    var j = i + 1
                    while (j < len && (line[j].isLetterOrDigit() || line[j] == '-')) j++
                    val candidatePseudo = line.substring(i + 1, j).lowercase()
                    if (candidatePseudo in SCSS_PSEUDO_CLASSES) {
                        outTokens.add(SyntaxToken(SyntaxTokenType.KEYWORD, i, j, line.substring(i, j)))
                        i = j
                        continue
                    }
                }

                // 3. Hex color literals (#0d6efd, #1e293b, #fff, #1e293bcc) vs ID selectors (#showcase-mdi-container)
                if (ch == '#' && i + 1 < len && (line[i + 1].isLetterOrDigit() || line[i + 1] == '_' || line[i + 1] == '-')) {
                    val start = i
                    i++
                    while (i < len && (line[i].isLetterOrDigit() || line[i] == '_' || line[i] == '-')) i++
                    val afterHash = line.substring(start + 1, i)
                    val isHexColor = afterHash.length in setOf(3, 4, 6, 8) &&
                        afterHash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
                    val tokType = if (isHexColor) SyntaxTokenType.NUMBER_LITERAL else SyntaxTokenType.TAG_NAME
                    outTokens.add(SyntaxToken(tokType, start, i, line.substring(start, i)))
                    continue
                }

                // 4. Custom genericui:* tag selectors (e.g. genericui:mdicontainer, genericui:mdiwindow, genericui:card)
                if (line.regionMatches(i, "genericui:", 0, 10, ignoreCase = true)) {
                    val start = i
                    i += 10
                    while (i < len && (line[i].isLetterOrDigit() || line[i] == '-' || line[i] == '_')) i++
                    outTokens.add(SyntaxToken(SyntaxTokenType.TAG_NAME, start, i, line.substring(start, i)))
                    continue
                }
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

            // Number literals (hex, binary, float, int, and CSS units like px, rem, %, s, ms, deg, vh, vw)
            if (ch.isDigit() || (ch == '.' && i + 1 < len && line[i + 1].isDigit())) {
                val start = i
                if (ch == '0' && i + 1 < len && (line[i + 1] == 'x' || line[i + 1] == 'X' || line[i + 1] == 'b' || line[i + 1] == 'B')) {
                    i += 2
                    while (i < len && (line[i].isLetterOrDigit() || line[i] == '_')) i++
                } else {
                    while (i < len && (line[i].isDigit() || line[i] == '.' || (!isStylesheet && (line[i] == '_' || line[i] == 'e' || line[i] == 'E' || line[i] == 'f' || line[i] == 'F' || line[i] == 'L')))) i++
                    if (isStylesheet && i < len) {
                        if (line[i] == '%') {
                            i++
                        } else if (line[i].isLetter()) {
                            val unitStart = i
                            while (i < len && line[i].isLetter()) i++
                            val unit = line.substring(unitStart, i).lowercase()
                            if (unit !in SCSS_UNITS) {
                                i = unitStart
                            }
                        }
                    }
                }
                outTokens.add(SyntaxToken(SyntaxTokenType.NUMBER_LITERAL, start, i, line.substring(start, i)))
                continue
            }

            // Identifiers, Keywords, Booleans, Types, Functions, SCSS Variables/Properties/Tags
            if (ch.isLetter() || ch == '_' || ch == '$') {
                val start = i
                while (i < len && (line[i].isLetterOrDigit() || line[i] == '_' || line[i] == '$' || line[i] == '-')) i++
                val word = line.substring(start, i)
                val nextNonSpace = line.substring(i).firstOrNull { !it.isWhitespace() }

                val type = if (isStylesheet) {
                    val lower = word.lowercase()
                    when {
                        word.startsWith("$") -> SyntaxTokenType.VARIABLE
                        lower == "true" || lower == "false" || lower == "null" -> SyntaxTokenType.BOOLEAN_LITERAL
                        nextNonSpace == '(' -> SyntaxTokenType.FUNCTION_NAME
                        lower in SCSS_PROPERTY_SET && nextNonSpace == ':' -> SyntaxTokenType.PROPERTY
                        start > 0 && line[start - 1] == '.' -> SyntaxTokenType.PROPERTY
                        lower in SCSS_TAG_SET -> SyntaxTokenType.TAG_NAME
                        lower in keywords -> SyntaxTokenType.KEYWORD
                        nextNonSpace == ':' && !line.substring(i).contains('{') -> SyntaxTokenType.PROPERTY
                        else -> SyntaxTokenType.VARIABLE
                    }
                } else {
                    when {
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

        private val SCSS_KEYWORDS = setOf(
            "solid", "dashed", "dotted", "double", "none", "auto", "inherit", "initial", "unset",
            "flex", "grid", "block", "inline", "inline-block", "inline-flex", "hidden", "visible",
            "scroll", "pointer", "default", "bold", "normal", "lighter", "bolder", "center", "left",
            "right", "top", "bottom", "relative", "absolute", "fixed", "sticky", "ease", "ease-in",
            "ease-out", "ease-in-out", "linear", "infinite", "and", "or", "not", "only", "screen",
            "from", "to", "through", "in"
        )

        private val SCSS_BANG_FLAGS = setOf(
            "!important", "!default", "!global", "!optional"
        )

        private val SCSS_PSEUDO_CLASSES = setOf(
            "hover", "focus", "focus-visible", "active", "disabled", "checked",
            "first-child", "last-child", "nth-child", "root", "before", "after"
        )

        private val SCSS_UNITS = setOf(
            "px", "rem", "em", "vh", "vw", "vmin", "vmax", "deg", "rad", "turn", "s", "ms", "fr", "ch", "pt"
        )

        private val SCSS_PROPERTY_SET: Set<String> = ScssLanguageSupport.SCSS_PROPERTIES.toSet()

        private val SCSS_TAG_SET: Set<String> = ScssLanguageSupport.HTML_COMPAT_TAGS.toSet()

        fun keywordsFor(language: CodeLanguage): Set<String> = when (language) {
            CodeLanguage.SQL -> SQL_KEYWORDS
            CodeLanguage.SCSS, CodeLanguage.CSS -> SCSS_KEYWORDS
            CodeLanguage.JSON, CodeLanguage.PLAIN_TEXT -> emptySet()
            else -> COMMON_KEYWORDS
        }
    }
}

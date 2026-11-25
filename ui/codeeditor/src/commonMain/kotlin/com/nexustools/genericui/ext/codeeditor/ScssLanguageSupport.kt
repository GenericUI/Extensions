package com.nexustools.genericui.ext.codeeditor

import com.nexustools.genericui.style.css.BackendStyleMask

/**
 * SCSS/CSS language intelligence helper for [IdeCodeEditor] and [SyntaxTokenizer], providing
 * property catalogs, `@` directive definitions, `genericui:*` custom element selectors,
 * HTML compatibility tag selectors, context-aware autocomplete, and live syntax diagnostics.
 */
object ScssLanguageSupport {

    val SCSS_PROPERTIES: List<String> = listOf(
        "background-color",
        "background-image",
        "background-position",
        "background-repeat",
        "background-size",
        "background",
        "color",
        "border",
        "border-color",
        "border-radius",
        "border-style",
        "border-width",
        "box-shadow",
        "text-shadow",
        "font-size",
        "font-weight",
        "font-family",
        "padding",
        "margin",
        "width",
        "height",
        "min-width",
        "min-height",
        "max-width",
        "max-height",
        "opacity",
        "accent-color",
        "transform",
        "transition",
        "z-index",
        "display",
        "cursor",
        "overflow",
        "position",
        "left",
        "top",
        "right",
        "bottom",
        "text-align",
        "vertical-align",
        "white-space",
        "pointer-events",
        "aspect-ratio"
    )

    val SCSS_DIRECTIVES: List<String> = listOf(
        "@mixin",
        "@include",
        "@media",
        "@keyframes",
        "@import",
        "@use",
        "@forward",
        "@extend",
        "@function",
        "@return",
        "@if",
        "@else",
        "@for",
        "@each"
    )

    val GENERICUI_BACKEND_DIRECTIVES: List<Pair<String, String>> = BackendStyleMask.ALL_DIRECTIVE_DESCRIPTIONS

    val GENERICUI_CUSTOM_TAGS: List<String> = listOf(
        "genericui:mdicontainer",
        "genericui:mdiwindow",
        "genericui:menubar",
        "genericui:dockworkspace",
        "genericui:dockpanel",
        "genericui:card",
        "genericui:tabcontainer",
        "genericui:tab",
        "genericui:row",
        "genericui:col",
        "genericui:flexbox",
        "genericui:splitpane",
        "genericui:lazylist",
        "genericui:lazygrid",
        "genericui:treeview",
        "genericui:categorizedtreeview",
        "genericui:toolpalette",
        "genericui:colorswatchbox",
        "genericui:progressbar",
        "genericui:spinner",
        "genericui:alert",
        "genericui:banner",
        "genericui:toast",
        "genericui:navbar",
        "genericui:nav",
        "genericui:breadcrumb",
        "genericui:pagination",
        "genericui:stepper",
        "genericui:bottomnavigationbar",
        "genericui:dropdown",
        "genericui:buttongroup",
        "genericui:collapsiblepanel",
        "genericui:accordion",
        "genericui:accordionitem",
        "genericui:jumbotron",
        "genericui:well",
        "genericui:listgroup",
        "genericui:listgroupitem",
        "genericui:formgroup",
        "genericui:inputgroup",
        "genericui:radiogroup",
        "genericui:compoundunitinput",
        "genericui:filedropzone",
        "genericui:popover",
        "genericui:tooltip",
        "genericui:markdownview",
        "genericui:skeletonplaceholder"
    )

    val HTML_COMPAT_TAGS: List<String> = listOf(
        "header",
        "footer",
        "main",
        "section",
        "article",
        "aside",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "div",
        "span",
        "button",
        "p",
        "a",
        "strong",
        "b",
        "em",
        "i",
        "small",
        "sup",
        "sub",
        "pre",
        "code",
        "blockquote",
        "ul",
        "ol",
        "li",
        "table",
        "thead",
        "tbody",
        "tfoot",
        "tr",
        "th",
        "td",
        "figure",
        "figcaption",
        "details",
        "summary",
        "form",
        "fieldset",
        "hr",
        "br",
        "textarea"
    )

    val SCSS_PSEUDO_SELECTORS: List<String> = listOf(
        ":hover",
        ":focus",
        ":focus-visible",
        ":active",
        ":disabled",
        ":checked",
        "&:hover",
        "&:focus",
        "&:active",
        "&:disabled",
        "&:checked"
    )

    private fun addAtDirectiveSuggestion(
        suggestions: MutableList<CompletionItem>,
        directive: String,
        detail: String,
        extendedPrefix: String,
        rawPrefix: String
    ) {
        if (extendedPrefix.isEmpty() ||
            directive.startsWith(extendedPrefix, ignoreCase = true) ||
            directive.removePrefix("@").startsWith(rawPrefix, ignoreCase = true)
        ) {
            val insertVal = if (extendedPrefix.startsWith("@") && rawPrefix.isNotEmpty()) {
                directive.removePrefix("@")
            } else {
                directive
            }
            suggestions.add(
                CompletionItem(
                    label = directive,
                    kind = CompletionItemKind.KEYWORD,
                    detail = detail,
                    insertText = insertVal
                )
            )
        }
    }

    /**
     * Creates a context-aware [CodeCompletionProvider] for SCSS documents that suggests:
     * - Document-declared `$variables`
     * - Document-declared `@mixin` names
     * - Built-in SCSS `@` directives
     * - GenericUI backend-targeted `@` directives (e.g. `@macos`, `@desktop`, `@backend(...)`)
     * - CSS properties
     * - `genericui:*` custom component tags
     * - Standard HTML compatibility tags
     */
    fun createCompletionProvider(): CodeCompletionProvider = CodeCompletionProvider { document, position, rawPrefix ->
        val fullText = document.getText()
        val lineText = document.getLine(position.line)
        val col = position.column.coerceIn(0, lineText.length)
        var tokenStart = col
        while (tokenStart > 0 && (lineText[tokenStart - 1].isLetterOrDigit() || lineText[tokenStart - 1] in "_-\$@:")) {
            tokenStart--
        }
        val extendedPrefix = lineText.substring(tokenStart, col).ifEmpty { rawPrefix }

        val suggestions = mutableListOf<CompletionItem>()

        // 1. Document $variables
        val varRegex = Regex("\\$([a-zA-Z0-9_-]+)\\s*:")
        val docVars = varRegex.findAll(fullText).map { "\$${it.groupValues[1]}" }.distinct()
        for (v in docVars) {
            if (extendedPrefix.isEmpty() || v.startsWith(extendedPrefix, ignoreCase = true) || v.removePrefix("$").startsWith(rawPrefix, ignoreCase = true)) {
                val insertVal = if (extendedPrefix.startsWith("$") && rawPrefix.isNotEmpty()) v.removePrefix("$") else v
                suggestions.add(
                    CompletionItem(
                        label = v,
                        kind = CompletionItemKind.VARIABLE,
                        detail = "SCSS Variable",
                        insertText = insertVal
                    )
                )
            }
        }

        // 2. Document @mixins
        val mixinRegex = Regex("@mixin\\s+([a-zA-Z0-9_-]+)")
        val docMixins = mixinRegex.findAll(fullText).map { it.groupValues[1] }.distinct()
        for (m in docMixins) {
            val includeLabel = "@include $m"
            if (extendedPrefix.isEmpty() ||
                m.startsWith(extendedPrefix, ignoreCase = true) ||
                includeLabel.startsWith(extendedPrefix, ignoreCase = true)
            ) {
                suggestions.add(
                    CompletionItem(
                        label = m,
                        kind = CompletionItemKind.FUNCTION,
                        detail = "SCSS @mixin",
                        insertText = m
                    )
                )
            }
        }

        // 3. SCSS @directives & GenericUI backend-targeted @directives
        for (dir in SCSS_DIRECTIVES) {
            addAtDirectiveSuggestion(suggestions, dir, "SCSS Directive", extendedPrefix, rawPrefix)
        }
        for ((dir, desc) in GENERICUI_BACKEND_DIRECTIVES) {
            addAtDirectiveSuggestion(suggestions, dir, "GenericUI Backend Directive — $desc", extendedPrefix, rawPrefix)
        }

        // 4. CSS Properties
        for (prop in SCSS_PROPERTIES) {
            if (extendedPrefix.isEmpty() || prop.startsWith(extendedPrefix, ignoreCase = true) || prop.startsWith(rawPrefix, ignoreCase = true)) {
                suggestions.add(
                    CompletionItem(
                        label = prop,
                        kind = CompletionItemKind.PROPERTY,
                        detail = "CSS Property",
                        insertText = "$prop: "
                    )
                )
            }
        }

        // 5. GenericUI custom tags
        for (tag in GENERICUI_CUSTOM_TAGS) {
            if (extendedPrefix.isEmpty() || tag.startsWith(extendedPrefix, ignoreCase = true) || tag.substringAfter(':').startsWith(rawPrefix, ignoreCase = true)) {
                val insertVal = if (extendedPrefix.startsWith("genericui:", ignoreCase = true) && rawPrefix.isNotEmpty()) {
                    tag.substringAfter(':')
                } else {
                    tag
                }
                suggestions.add(
                    CompletionItem(
                        label = tag,
                        kind = CompletionItemKind.CLASS,
                        detail = "GenericUI Component Tag",
                        insertText = insertVal
                    )
                )
            }
        }

        // 6. HTML compat tags
        for (tag in HTML_COMPAT_TAGS) {
            if (extendedPrefix.isEmpty() || tag.startsWith(extendedPrefix, ignoreCase = true)) {
                suggestions.add(
                    CompletionItem(
                        label = tag,
                        kind = CompletionItemKind.INTERFACE,
                        detail = "HTML Element Tag",
                        insertText = tag
                    )
                )
            }
        }

        suggestions.distinctBy { it.label }
    }

    /**
     * Configures [editor] for SCSS editing by setting [CodeLanguage.SCSS], attaching
     * [createCompletionProvider], and running initial brace/syntax diagnostics.
     */
    fun configureAsScssEditor(editor: IdeCodeEditor) {
        editor.language = CodeLanguage.SCSS
        editor.completionProvider = createCompletionProvider()
        editor.setDiagnostics(validateScssDiagnostics(editor.getText()))
    }

    /**
     * Performs lexical/structural validation on [scssSource] and returns any [CodeDiagnostic]s
     * for unmatched `{` / `}` braces or unclosed block comments.
     */
    fun validateScssDiagnostics(scssSource: String): List<CodeDiagnostic> {
        val diagnostics = mutableListOf<CodeDiagnostic>()
        val lines = scssSource.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val braceStack = ArrayDeque<CursorPosition>()
        var inBlockComment = false
        var blockCommentStart = CursorPosition.ORIGIN

        for ((lineIdx, line) in lines.withIndex()) {
            var i = 0
            val len = line.length
            var inStringChar: Char? = null
            while (i < len) {
                if (inBlockComment) {
                    val closeIdx = line.indexOf("*/", i)
                    if (closeIdx < 0) {
                        break
                    } else {
                        inBlockComment = false
                        i = closeIdx + 2
                        continue
                    }
                }
                val ch = line[i]
                if (inStringChar != null) {
                    if (ch == '\\' && i + 1 < len) {
                        i += 2
                        continue
                    }
                    if (ch == inStringChar) {
                        inStringChar = null
                    }
                    i++
                    continue
                }
                if (line.startsWith("//", i)) {
                    break
                }
                if (line.startsWith("/*", i)) {
                    inBlockComment = true
                    blockCommentStart = CursorPosition(lineIdx, i)
                    i += 2
                    continue
                }
                when (ch) {
                    '"', '\'' -> inStringChar = ch
                    '{' -> braceStack.addLast(CursorPosition(lineIdx, i))
                    '}' -> {
                        if (braceStack.isEmpty()) {
                            diagnostics.add(
                                CodeDiagnostic(
                                    range = TextRange(CursorPosition(lineIdx, i), CursorPosition(lineIdx, i + 1)),
                                    message = "Unexpected closing brace '}'",
                                    severity = CodeDiagnosticSeverity.ERROR,
                                    code = "SCSS_UNEXPECTED_CLOSE_BRACE"
                                )
                            )
                        } else {
                            braceStack.removeLast()
                        }
                    }
                }
                i++
            }
        }

        if (inBlockComment) {
            diagnostics.add(
                CodeDiagnostic(
                    range = TextRange(blockCommentStart, CursorPosition(blockCommentStart.line, blockCommentStart.column + 2)),
                    message = "Unclosed block comment '/*'",
                    severity = CodeDiagnosticSeverity.ERROR,
                    code = "SCSS_UNCLOSED_COMMENT"
                )
            )
        }

        while (braceStack.isNotEmpty()) {
            val openPos = braceStack.removeLast()
            diagnostics.add(
                CodeDiagnostic(
                    range = TextRange(openPos, CursorPosition(openPos.line, openPos.column + 1)),
                    message = "Unclosed opening brace '{'",
                    severity = CodeDiagnosticSeverity.ERROR,
                    code = "SCSS_UNCLOSED_OPEN_BRACE"
                )
            )
        }

        return diagnostics
    }
}

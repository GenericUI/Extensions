package com.nexustools.genericui.ext.codeeditor.dsl

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.codeeditor.CodeDocumentBuffer
import com.nexustools.genericui.ext.codeeditor.CodeEditorTheme
import com.nexustools.genericui.ext.codeeditor.CodeLanguage
import com.nexustools.genericui.ext.codeeditor.CodeMinimap
import com.nexustools.genericui.ext.codeeditor.CursorPosition
import com.nexustools.genericui.ext.codeeditor.IdeCodeEditor
import com.nexustools.genericui.ext.codeeditor.SyntaxTokenizer
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker

/**
 * In-place [IdeCodeEditor] builder with syntax highlighting, gutter, minimap, and `style { ... }` support.
 */
fun AbstractContainer.codeEditor(
    initialText: String = "",
    language: CodeLanguage = CodeLanguage.KOTLIN,
    theme: CodeEditorTheme = CodeEditorTheme.DARK_IDE,
    width: Int = 960,
    height: Int = 640,
    readOnly: Boolean = false,
    id: String? = null,
    classes: String? = null,
    onTextChanged: ((String) -> Unit)? = null,
    onCursorChanged: ((CursorPosition) -> Unit)? = null,
    block: (@GenericUiDslMarker IdeCodeEditor.() -> Unit)? = null
): IdeCodeEditor {
    val editor = IdeCodeEditor(
        initialText = initialText,
        language = language,
        theme = theme,
        width = width,
        height = height
    )
    editor.readOnly = readOnly
    if (onTextChanged != null) {
        editor.setOnTextChanged(onTextChanged)
    }
    if (onCursorChanged != null) {
        editor.setOnCursorChanged(onCursorChanged)
    }
    if (!classes.isNullOrBlank()) {
        editor.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        editor.id = id
    }
    if (block != null) {
        editor.block()
    }
    addChild(editor)
    return editor
}

/**
 * Alias for [codeEditor] that mounts and configures an [IdeCodeEditor].
 */
fun AbstractContainer.ideCodeEditor(
    initialText: String = "",
    language: CodeLanguage = CodeLanguage.KOTLIN,
    theme: CodeEditorTheme = CodeEditorTheme.DARK_IDE,
    width: Int = 960,
    height: Int = 640,
    readOnly: Boolean = false,
    id: String? = null,
    classes: String? = null,
    onTextChanged: ((String) -> Unit)? = null,
    onCursorChanged: ((CursorPosition) -> Unit)? = null,
    block: (@GenericUiDslMarker IdeCodeEditor.() -> Unit)? = null
): IdeCodeEditor = codeEditor(
    initialText = initialText,
    language = language,
    theme = theme,
    width = width,
    height = height,
    readOnly = readOnly,
    id = id,
    classes = classes,
    onTextChanged = onTextChanged,
    onCursorChanged = onCursorChanged,
    block = block
)

/**
 * In-place [CodeMinimap] overview builder.
 */
fun AbstractContainer.codeMinimap(
    document: CodeDocumentBuffer = CodeDocumentBuffer(),
    tokenizer: SyntaxTokenizer = SyntaxTokenizer(),
    theme: CodeEditorTheme = CodeEditorTheme.DARK_IDE,
    width: Int = 96,
    height: Int = 600,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker CodeMinimap.() -> Unit)? = null
): CodeMinimap {
    val minimap = CodeMinimap(
        document = document,
        tokenizer = tokenizer,
        theme = theme,
        width = width,
        height = height
    )
    if (!classes.isNullOrBlank()) {
        minimap.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        minimap.id = id
    }
    if (block != null) {
        minimap.block()
    }
    addChild(minimap)
    return minimap
}

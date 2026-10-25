package com.nexustools.genericui.ext.codeeditor.dsl

import com.nexustools.genericui.ext.codeeditor.CodeEditorTheme
import com.nexustools.genericui.ext.codeeditor.CodeLanguage
import com.nexustools.genericui.ext.codeeditor.IdeCodeEditor
import com.nexustools.genericui.ext.dsl.ColorRgba
import com.nexustools.genericui.ext.dsl.buildDiv
import kotlin.test.Test
import kotlin.test.assertEquals

class CodeEditorDslTest {

    @Test
    fun testCodeEditorDslBuilder() {
        val root = buildDiv {
            codeEditor(
                initialText = "fun main() = println(\"Hello\")",
                language = CodeLanguage.KOTLIN,
                theme = CodeEditorTheme.DRACULA,
                id = "main-editor"
            ) {
                style {
                    tint = ColorRgba(30, 30, 46, 1.0)
                }
            }
        }
        assertEquals(1, root.children.size)
        val editor = root.children[0] as IdeCodeEditor
        assertEquals("main-editor", editor.id)
        assertEquals(CodeLanguage.KOTLIN, editor.language)
        assertEquals("fun main() = println(\"Hello\")", editor.getText())
    }
}

package com.nexustools.genericui.ext.codeeditor.dsl

import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.ext.codeeditor.CodeEditorTheme
import com.nexustools.genericui.ext.codeeditor.CodeLanguage
import com.nexustools.genericui.ext.codeeditor.IdeCodeEditor
import com.nexustools.genericui.ext.dsl.ColorRgba
import com.nexustools.genericui.ext.dsl.bootstrap
import com.nexustools.genericui.ext.dsl.buildDiv
import com.nexustools.genericui.ext.dsl.style
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
                    backgroundColor = ColorRgba(30, 30, 46, 1.0)
                }
                bootstrap {
                    borderRadius = BorderRadiusStyle.LARGE
                    padding = SpacingScale.TWO
                }
            }
        }
        assertEquals(1, root.children.size)
        val editor = root.children[0] as IdeCodeEditor
        assertEquals("main-editor", editor.id)
        assertEquals(CodeLanguage.KOTLIN, editor.language)
        assertEquals("fun main() = println(\"Hello\")", editor.getText())
        assertEquals(ColorRgba(30, 30, 46, 1.0), editor.getStyle().getBackgroundColor())
        assertEquals(BorderRadiusStyle.LARGE, editor.getBootstrap().getBorderRadius())
        assertEquals(SpacingScale.TWO, editor.getBootstrap().getPadding())
        assertFalse(editor.hasClass("rounded-3"))
    }
}

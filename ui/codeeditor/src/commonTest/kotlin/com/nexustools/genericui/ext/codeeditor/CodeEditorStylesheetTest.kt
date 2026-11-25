package com.nexustools.genericui.ext.codeeditor

import com.nexustools.genericui.ext.codeeditor.res.R
import com.nexustools.genericui.res.Resources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CodeEditorStylesheetTest {

    @Test
    fun defaultStyleSheetCompilesCleanlyAndAppliesEditorAndMinimapClasses() {
        val sheet = CodeEditorExtension.defaultStyleSheet()
        assertTrue(sheet.rules.isNotEmpty())

        val rawScss = Resources.getRawText(R.style.style)
        assertNotNull(rawScss)
        assertFalse(rawScss.contains("\$primary"))
        assertFalse(rawScss.contains("\$accent"))

        val editor = IdeCodeEditor(initialText = "fun main() {}").apply { applyStyleSheet(sheet) }
        assertTrue(editor.hasClass("ide-code-editor"))
        assertEquals("hidden", editor.computedStyle?.overflow)
        assertEquals(4f, editor.computedStyle?.borderRadiusPx)
        assertTrue(editor.minimap.hasClass("code-minimap"))
    }
}

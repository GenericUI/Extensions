package com.nexustools.genericui.ext.dsl

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.form.Select
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.Row
import com.nexustools.genericui.component.typography.Heading
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenericUiDslTest {

    private class SampleCharModel(
        var characterName: String = "Alden",
        var alignment: String = "LN",
        var level: Int = 1
    ) : MvcModel()

    private class SampleCharView(model: SampleCharModel) : MvcView<SampleCharModel>(model, "chargen-mvc-view") {
        var onLevelUpClicked: (() -> Unit)? = null

        override fun Container.render() {
            div(classes = "header-section", id = "char-header") {
                h1("${model.characterName} (Lv. ${model.level})", classes = "title")
                p("Alignment: ${model.alignment}", classes = "subtitle")
            }
            row(classes = "controls-row") {
                col(span = 6) {
                    button("Level Up", type = BootstrapType.SUCCESS, onClick = { onLevelUpClicked?.invoke() })
                }
            }
        }
    }

    private class SampleCharController(
        model: SampleCharModel,
        view: SampleCharView
    ) : MvcController<SampleCharModel, SampleCharView>(model, view) {
        override fun onAttach() {
            view.onLevelUpClicked = {
                updateModel {
                    level += 1
                }
            }
        }
    }

    @Test
    fun testKotlinxHtmlStyleInPlaceBuilderAndMvcFlow() {
        var clickedCount = 0
        var selectedAlignment = "LN"

        val root = buildDiv(classes = "chargen-root container", id = "main-page") {
            h1(classes = "page-title") {
                +"Create Your Character"
            }
            p("Select your character's alignment and attributes.", classes = "lead")
            row(classes = "g-3") {
                col(span = 6, classes = "left-col") {
                    label("Alignment:")
                    select(id = "alignment-select", onSelectionChanged = { selectedAlignment = it }) {
                        option("LG", "Lawful Good")
                        option("LN", "Lawful Neutral", selected = true)
                        option("CG", "Chaotic Good")
                    }
                }
                col(span = 6, classes = "right-col") {
                    button(
                        type = BootstrapType.PRIMARY,
                        size = BootstrapSize.LG,
                        classes = "w-100",
                        onClick = { clickedCount++ }
                    ) {
                        +"Continue"
                    }
                }
            }
            table(classes = "stats-table") {
                thead {
                    tr {
                        th("Stat")
                        th("Score")
                    }
                }
                tbody {
                    tr {
                        td("Strength")
                        td { +"18" }
                    }
                }
            }
        }

        assertEquals("main-page", root.id)
        assertEquals(4, root.children.size)

        val heading = root.children[0] as Heading
        assertEquals("Create Your Character", heading.text)
        assertEquals(1, heading.level)

        val paragraph = root.children[1] as Text
        assertEquals("Select your character's alignment and attributes.", paragraph.text)

        val row = root.children[2] as Row
        assertEquals(2, row.children.size)
        val leftCol = row.children[0] as Container
        val select = leftCol.children[1] as Select
        assertEquals(3, select.options.size)
        assertEquals("LN", select.selectedValue)

        val rightCol = row.children[1] as Container
        val btn = rightCol.children[0] as Button
        assertEquals("Continue", btn.text)
        btn.onClick?.invoke()
        assertEquals(1, clickedCount)
        assertTrue(selectedAlignment == "LN")

        // Verify MVC (Model-View-Controller) lifecycle and automatic view re-render
        val model = SampleCharModel()
        val view = SampleCharView(model)
        val controller = SampleCharController(model, view)
        controller.attach()

        val initialHeader = view.children[0] as Container
        val initialTitle = initialHeader.children[0] as Heading
        assertEquals("Alden (Lv. 1)", initialTitle.text)

        view.onLevelUpClicked?.invoke()
        assertEquals(2, model.level)
        val updatedHeader = view.children[0] as Container
        val updatedTitle = updatedHeader.children[0] as Heading
        assertEquals("Alden (Lv. 2)", updatedTitle.text)
    }
}

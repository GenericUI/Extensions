package com.nexustools.genericui.ext.dsl

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.form.Select
import com.nexustools.genericui.component.htmlcompat.Div
import com.nexustools.genericui.component.htmlcompat.H1
import com.nexustools.genericui.component.htmlcompat.H2
import com.nexustools.genericui.component.htmlcompat.Header
import com.nexustools.genericui.component.htmlcompat.Li
import com.nexustools.genericui.component.htmlcompat.Ol
import com.nexustools.genericui.component.htmlcompat.P
import com.nexustools.genericui.component.htmlcompat.Span
import com.nexustools.genericui.component.htmlcompat.Td
import com.nexustools.genericui.component.htmlcompat.Th
import com.nexustools.genericui.component.htmlcompat.Tr
import com.nexustools.genericui.component.htmlcompat.Ul
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.Row
import com.nexustools.genericui.component.typography.Heading
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.BootstrapWidth
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.enums.css.CssCursor
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssLength
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GenericUiDslTest {

    private class SampleCharModel(
        var characterName: String = "Alden",
        var alignment: String = "LN",
        var level: Int = 1
    ) : MvcModel()

    private class SampleCharView(model: SampleCharModel) : MvcView<SampleCharModel>(model, "sample-mvc-view") {
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

        val root = buildDiv(classes = "sample-root container", id = "main-page") {
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

        assertIs<Div>(root)
        assertEquals("div", root.htmlTag)
        assertEquals("main-page", root.id)
        assertEquals(4, root.children.size)

        val heading = root.children[0] as Heading
        assertIs<H1>(heading)
        assertEquals("h1", heading.htmlTag)
        assertEquals("Create Your Character", heading.text)
        assertEquals(1, heading.level)

        val paragraph = root.children[1] as Text
        assertIs<P>(paragraph)
        assertEquals("p", paragraph.htmlTag)
        assertEquals("Select your character's alignment and attributes.", paragraph.text)

        val row = root.children[2] as Row
        assertEquals(2, row.children.size)
        val leftCol = row.children[0] as Container
        val select = leftCol.children[1] as Select
        assertEquals(3, select.options.size)
        assertEquals("LN", select.selectedValue)

        val rightCol = row.children[1] as Container
        val btn = rightCol.children[0] as Button
        assertEquals("button", btn.htmlTag)
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

    @Test
    fun testHtmlCompatAndMdiDslBuilders() {
        val root = buildDiv {
            header {
                h2("Workspace")
                span("v1.0")
            }
            ul {
                li("First")
            }
            ol {
                li("Step 1")
            }
            mdiContainer(showTaskbar = true, id = "mdi-root") {
                mdiWindow(windowId = "win-1", title = "Document 1", width = 500, height = 360) {
                    contentContainer.p("Inside MDI Window")
                }
            }
            lazyGridTree<String>(id = "inspector-tree") {
                showHeaderRow = true
            }
        }

        val header = assertIs<Header>(root.children[0])
        assertIs<H2>(header.children[0])
        assertIs<Span>(header.children[1])

        val ul = assertIs<Ul>(root.children[1])
        assertIs<Li>(ul.children[0])

        val ol = assertIs<Ol>(root.children[2])
        assertIs<Li>(ol.children[0])

        val mdi = root.children[3] as com.nexustools.genericui.component.shell.MdiContainer
        assertEquals("genericui:mdicontainer", mdi.htmlTag)
        assertEquals(1, mdi.windows.size)
        assertEquals("genericui:mdiwindow", mdi.windows[0].htmlTag)
        assertEquals("Document 1", mdi.windows[0].title)

        val gridTree = root.children[4] as com.nexustools.genericui.component.lazy.LazyGridTree<*>
        assertEquals("genericui:lazygridtree", gridTree.htmlTag)
        assertEquals("inspector-tree", gridTree.id)
    }

    @Test
    fun testDslComponentStyleAndBootstrapIntegration() {
        val root = buildDiv(classes = "user-root") {
            bootstrap {
                display = DisplayMode.FLEX
                padding = SpacingScale.S3
                width = BootstrapWidth.W_100
            }
            style {
                backgroundColor = RgbaColor.fromHex("#0f172a")
                borderRadius = CssCornerRadii.px(8f)
                important {
                    width = CssLength.px(640f)
                }
            }
            button("Apply", classes = "apply-action") {
                bootstrap {
                    type = BootstrapType.DANGER
                    size = BootstrapSize.LG
                    outline = true
                    width = BootstrapWidth.W_50
                }
                style {
                    cursor = CssCursor.POINTER
                    accentColor = RgbaColor.fromHex("#38bdf8")
                }
            }
        }

        assertEquals(setOf("user-root"), root.getClasses())
        assertFalse(root.hasClass("d-flex"))
        assertFalse(root.hasClass("p-3"))
        assertFalse(root.hasClass("w-100"))
        assertEquals(DisplayMode.FLEX, root.getBootstrap().display)
        assertEquals(SpacingScale.S3, root.getBootstrap().padding)
        assertEquals(BootstrapWidth.W_100, root.getBootstrap().width)
        assertEquals(RgbaColor.fromHex("#0f172a"), root.getStyle().backgroundColor)
        assertEquals(640f, root.getStyle().width?.value)

        val btn = assertIs<Button>(root.children[0])
        assertEquals(setOf("apply-action"), btn.getClasses())
        assertFalse(btn.hasClass("btn-outline-danger"))
        assertFalse(btn.hasClass("btn-lg"))
        assertFalse(btn.hasClass("w-50"))
        assertEquals(BootstrapType.DANGER, btn.getBootstrap().type)
        assertEquals(BootstrapSize.LG, btn.getBootstrap().size)
        assertTrue(btn.getBootstrap().outline)
        assertEquals(BootstrapWidth.W_50, btn.getBootstrap().width)
        assertEquals(CssCursor.POINTER, btn.getStyle().cursor)
        assertEquals(RgbaColor.fromHex("#38bdf8"), btn.getStyle().accentColor)
    }
}

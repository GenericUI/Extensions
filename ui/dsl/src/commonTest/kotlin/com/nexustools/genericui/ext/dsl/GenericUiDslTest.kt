package com.nexustools.genericui.ext.dsl

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.action.DropdownButton
import com.nexustools.genericui.component.action.styled.StyledButton
import com.nexustools.genericui.component.feedback.Alert
import com.nexustools.genericui.component.feedback.Banner
import com.nexustools.genericui.component.feedback.ProgressBar
import com.nexustools.genericui.component.feedback.Spinner
import com.nexustools.genericui.component.feedback.Toast
import com.nexustools.genericui.component.form.Select
import com.nexustools.genericui.component.form.Slider
import com.nexustools.genericui.component.form.TextField
import com.nexustools.genericui.component.form.styled.StyledSlider
import com.nexustools.genericui.component.form.styled.StyledTextArea
import com.nexustools.genericui.component.form.styled.StyledTextField
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
import com.nexustools.genericui.component.layout.Column
import com.nexustools.genericui.component.layout.ColumnContainer
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.surface.Card
import com.nexustools.genericui.component.surface.Jumbotron
import com.nexustools.genericui.component.surface.ListGroup
import com.nexustools.genericui.component.surface.ListGroupItem
import com.nexustools.genericui.component.surface.Well
import com.nexustools.genericui.component.typography.Badge
import com.nexustools.genericui.component.typography.Heading
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.BootstrapWidth
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.enums.css.CssCursor
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl
import com.nexustools.genericui.nativeimpl.action.DropdownNativeImpl
import com.nexustools.genericui.nativeimpl.action.StyledButtonNativeImpl
import com.nexustools.genericui.nativeimpl.content.ImageNativeImpl
import com.nexustools.genericui.nativeimpl.content.TableNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.DialogWindowNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.FileBrowserDialogNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.StandardDialogsNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockAreaNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockGroupNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockPanelNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockWindowNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockWorkspaceNativeImpl
import com.nexustools.genericui.nativeimpl.dock.ToolPaletteNativeImpl
import com.nexustools.genericui.nativeimpl.document.BookContainerNativeImpl
import com.nexustools.genericui.nativeimpl.feedback.ProgressBarNativeImpl
import com.nexustools.genericui.nativeimpl.feedback.SpinnerNativeImpl
import com.nexustools.genericui.nativeimpl.form.CheckboxNativeImpl
import com.nexustools.genericui.nativeimpl.form.RangeSliderNativeImpl
import com.nexustools.genericui.nativeimpl.form.SelectNativeImpl
import com.nexustools.genericui.nativeimpl.form.StyledTextInputNativeImpl
import com.nexustools.genericui.nativeimpl.form.TextInputNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ScrollViewNativeImpl
import com.nexustools.genericui.nativeimpl.layout.StyledContainerNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyGridNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyListNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.TreeViewNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.BottomNavigationBarNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl
import com.nexustools.genericui.nativeimpl.overlay.PopoverNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl
import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl
import com.nexustools.genericui.nativeimpl.system.NotificationManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.PermissionManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssLength
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
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
            columnLayout(classes = "controls-row") {
                column(span = 6) {
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
            columnLayout(gutter = SpacingScale.S3) {
                column(span = 6, classes = "left-col") {
                    label("Alignment:")
                    select(id = "alignment-select", onSelectionChanged = { selectedAlignment = it }) {
                        option("LG", "Lawful Good")
                        option("LN", "Lawful Neutral", selected = true)
                        option("CG", "Chaotic Good")
                    }
                }
                column(span = 6, classes = "right-col") {
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

        val row = root.children[2] as ColumnContainer
        assertEquals(2, row.children.size)
        val leftCol = row.children[0] as Column
        val select = leftCol.children[1] as Select
        assertEquals(3, select.options.size)
        assertEquals("LN", select.selectedValue)

        val rightCol = row.children[1] as Column
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

    @Test
    fun testBootstrapTypeParityAcrossNonControlAndControlDslBuilders() {
        val root = buildDiv {
            dropdownButton("Actions", id = "dd-btn")
            progressBar(progress = 0.65f, label = "Loading 65%", id = "pb")
            spinner(growStyle = true, label = "Syncing...", id = "spin")

            alert("Saved", type = BootstrapType.SUCCESS, dismissible = true)
            badge("New", type = BootstrapType.INFO, pill = true)
            card(title = "Summary", type = BootstrapType.PRIMARY) {
                p("Body")
            }
            banner("Heads up", type = BootstrapType.WARNING)
            toast(title = "Done", message = "Uploaded", type = BootstrapType.SUCCESS, autoHideDurationMs = 2500L)
            listGroup(type = BootstrapType.SECONDARY) {
                listGroupItem("Item A", active = true, type = BootstrapType.INFO)
            }
            jumbotron(type = BootstrapType.DARK)
            well(type = BootstrapType.LIGHT)
        }

        val dd = assertIs<DropdownButton>(root.children[0])
        assertEquals("Actions", dd.label)

        val pb = assertIs<ProgressBar>(root.children[1])
        assertEquals(0.65f, pb.progress)
        assertEquals("Loading 65%", pb.label)

        val spin = assertIs<Spinner>(root.children[2])
        assertTrue(spin.growStyle)
        assertEquals("Syncing...", spin.label)

        val alertComp = assertIs<Alert>(root.children[3])
        assertEquals(BootstrapType.SUCCESS, alertComp.type)

        val badgeComp = assertIs<Badge>(root.children[4])
        assertEquals(BootstrapType.INFO, badgeComp.type)
        assertEquals("New", badgeComp.text)

        val cardComp = assertIs<Card>(root.children[5])
        assertEquals(BootstrapType.PRIMARY, cardComp.type)

        val bannerComp = assertIs<Banner>(root.children[6])
        assertEquals(BootstrapType.WARNING, bannerComp.type)

        val toastComp = assertIs<Toast>(root.children[7])
        assertEquals(BootstrapType.SUCCESS, toastComp.type)
        assertEquals(2500L, toastComp.autoHideDurationMs)

        val listGroupComp = assertIs<ListGroup>(root.children[8])
        assertEquals(BootstrapType.SECONDARY, listGroupComp.type)
        val itemComp = assertIs<ListGroupItem>(listGroupComp.children[0])
        assertEquals(BootstrapType.INFO, itemComp.type)
        assertTrue(itemComp.isActive)

        val jumboComp = assertIs<Jumbotron>(root.children[9])
        assertEquals(BootstrapType.DARK, jumboComp.type)

        val wellComp = assertIs<Well>(root.children[10])
        assertEquals(BootstrapType.LIGHT, wellComp.type)
    }

    private class DslLabelModeFactory(
        override val backendTarget: BackendTarget
    ) : NativeFactory {
        override val backendName: String = "dsl-label-mode-${backendTarget.name.lowercase()}"
        override fun supportsTcp(): Boolean = true
        override fun supportsUdp(): Boolean = true
        private fun unsupported(): Nothing = throw UnsupportedOperationException("Headless DSL label test factory")
        override fun createApplication(): ApplicationNativeImpl<*> = unsupported()
        override fun createWindow(): WindowNativeImpl<*> = unsupported()
        override fun createMenuBar(): MenuBarNativeImpl<*> = unsupported()
        override fun createMenu(): MenuNativeImpl<*> = unsupported()
        override fun createMenuItem(checkable: Boolean, radio: Boolean): MenuItemNativeImpl<*> = unsupported()
        override fun createContextMenu(): ContextMenuNativeImpl<*> = unsupported()
        override fun createDockWorkspace(): DockWorkspaceNativeImpl<*> = unsupported()
        override fun createDockArea(): DockAreaNativeImpl<*> = unsupported()
        override fun createDockGroup(): DockGroupNativeImpl<*> = unsupported()
        override fun createDockPanel(): DockPanelNativeImpl<*> = unsupported()
        override fun createDockWindow(): DockWindowNativeImpl<*> = unsupported()
        override fun createToolPalette(): ToolPaletteNativeImpl<*> = unsupported()
        override fun createContainer(classPrefix: String): ContainerNativeImpl<*> = unsupported()
        override fun createStyledContainer(classPrefix: String, tagName: String): StyledContainerNativeImpl<*> = unsupported()
        override fun createStyledComponent(classPrefix: String, tagName: String): StyledComponentNativeImpl<*> = unsupported()
        override fun createBookContainer(): BookContainerNativeImpl<*> = unsupported()
        override fun createScrollView(): ScrollViewNativeImpl<*> = unsupported()
        override fun createDivider(): DividerNativeImpl<*> = unsupported()
        override fun createText(tagName: String, classPrefix: String): TextNativeImpl<*> = unsupported()
        override fun createMarkdownView(): MarkdownViewNativeImpl<*> = unsupported()
        override fun createImage(): ImageNativeImpl<*> = unsupported()
        override fun createTable(): TableNativeImpl<*> = unsupported()
        override fun createButton(classPrefix: String): ButtonNativeImpl<*> = unsupported()
        override fun createStyledButton(classPrefix: String): StyledButtonNativeImpl<*> = unsupported()
        override fun createDropdown(): DropdownNativeImpl<*> = unsupported()
        override fun createTextInput(multiline: Boolean): TextInputNativeImpl<*> = unsupported()
        override fun createStyledTextInput(multiline: Boolean, classPrefix: String): StyledTextInputNativeImpl<*> = unsupported()
        override fun createSelect(): SelectNativeImpl<*> = unsupported()
        override fun createCheckbox(switchStyle: Boolean, radioStyle: Boolean): CheckboxNativeImpl<*> = unsupported()
        override fun createRangeSlider(): RangeSliderNativeImpl<*> = unsupported()
        override fun createTab(closable: Boolean): TabNativeImpl<*> = unsupported()
        override fun createTabContainer(): TabContainerNativeImpl<*> = unsupported()
        override fun createNavbar(): NavbarNativeImpl<*> = unsupported()
        override fun createBottomNavigationBar(): BottomNavigationBarNativeImpl<*> = unsupported()
        override fun createProgressBar(): ProgressBarNativeImpl<*> = unsupported()
        override fun createSpinner(): SpinnerNativeImpl<*> = unsupported()
        override fun createPopover(): PopoverNativeImpl<*> = unsupported()
        override fun createDialogWindow(): DialogWindowNativeImpl<*> = unsupported()
        override fun createFileBrowserDialog(): FileBrowserDialogNativeImpl = unsupported()
        override fun createStandardDialogs(): StandardDialogsNativeImpl = unsupported()
        override fun createLazyList(): LazyListNativeImpl<*> = unsupported()
        override fun createLazyGrid(): LazyGridNativeImpl<*> = unsupported()
        override fun createTreeView(): TreeViewNativeImpl<*> = unsupported()
        override fun createPermissionManager(): PermissionManagerNativeImpl = unsupported()
        override fun createSystemTray(): SystemTrayNativeImpl = unsupported()
        override fun createNotificationManager(): NotificationManagerNativeImpl = unsupported()
        override fun createNetworkMonitor(): NetworkMonitorNativeImpl = object : NetworkMonitorNativeImpl {
            override var isDisposed: Boolean = false
            override fun dispose() { isDisposed = true }
            override fun isOnline(): Boolean = true
            override fun setOnConnectivityChanged(listener: ((Boolean) -> Unit)?) {}
        }
    }

    @Test
    fun testStyledFormBuildersAndLabelTargetDslParameter() {
        val prevFactory = GenericUI.activeFactory
        try {
            GenericUI.activeFactory = DslLabelModeFactory(BackendTarget.Windows)
            var styledBtnClicks = 0
            var fieldChangedTo: String? = null
            var areaChangedTo: String? = null
            var sliderChangedTo = 0.0
            var styledSliderChangedTo = 0.0

            lateinit var fieldRef: StyledTextField
            lateinit var areaRef: StyledTextArea
            lateinit var sliderRef: Slider
            lateinit var styledSliderRef: StyledSlider
            lateinit var styledBtnRef: StyledButton
            lateinit var fieldLabel: Label
            lateinit var areaLabel: Label
            lateinit var styledSliderLabel: Label

            val root = buildDiv {
                styledBtnRef = styledButton("Run Action", onClick = { styledBtnClicks++ }) {
                    backgroundColor = RgbaColor(12, 24, 36)
                }
                fieldRef = styledTextField(
                    value = "alpha",
                    placeholder = "Token",
                    onValueChanged = { fieldChangedTo = it }
                ) {
                    focusBorderColor = RgbaColor(80, 140, 255)
                }
                areaRef = styledTextArea(
                    value = "line1",
                    placeholder = "Body",
                    rows = 5,
                    onValueChanged = { areaChangedTo = it }
                ) {
                    borderRadiusPx = 6f
                }
                sliderRef = slider(value = 25.0, min = 0.0, max = 100.0, step = 5.0, onValueChanged = { sliderChangedTo = it })
                styledSliderRef = styledSlider(min = 0.0, max = 1.0, value = 0.4, step = 0.1, onValueChanged = { styledSliderChangedTo = it })

                fieldLabel = label("Token", target = fieldRef)
                areaLabel = label("Body", target = areaRef)
                styledSliderLabel = label("Intensity", target = styledSliderRef)
            }

            assertEquals(8, root.children.size)
            assertEquals("Run Action", styledBtnRef.text)
            assertEquals(RgbaColor(12, 24, 36), styledBtnRef.backgroundColor)
            styledBtnRef.triggerClick()
            assertEquals(1, styledBtnClicks)

            assertEquals("alpha", fieldRef.value)
            assertEquals("Token", fieldRef.placeholder)
            fieldRef.value = "beta"
            fieldRef.onValueChanged?.invoke("beta")
            assertEquals("beta", fieldChangedTo)

            assertEquals(5, areaRef.rows)
            areaRef.value = "line2"
            areaRef.onValueChanged?.invoke("line2")
            assertEquals("line2", areaChangedTo)

            assertEquals(25.0, sliderRef.value)
            sliderRef.dispatchUserValueChange(40.0)
            assertEquals(40.0, sliderChangedTo)

            assertEquals(0.4, styledSliderRef.value, 0.001)
            styledSliderRef.value = 0.7
            styledSliderRef.onValueChanged?.invoke(0.7)
            assertEquals(0.7, styledSliderChangedTo, 0.001)

            // Non-Web label(target = ...) focuses targets on click without setting HTML "for"
            assertSame(fieldRef, fieldLabel.target)
            assertNull(fieldLabel.getAttribute("for"))
            fieldRef.isFocused = false
            fieldLabel.triggerClick()
            assertTrue(fieldRef.isFocused)

            assertSame(areaRef, areaLabel.target)
            assertNull(areaLabel.getAttribute("for"))
            areaRef.isFocused = false
            areaLabel.triggerClick()
            assertTrue(areaRef.isFocused)

            assertSame(styledSliderRef, styledSliderLabel.target)
            assertNull(styledSliderLabel.getAttribute("for"))
            styledSliderRef.isFocused = false
            styledSliderLabel.triggerClick()
            assertTrue(styledSliderRef.isFocused)

            // Web backend: label(target = ...) assigns DOM id & HTML "for"
            GenericUI.activeFactory = DslLabelModeFactory(BackendTarget.Web)
            val webRoot = buildDiv {
                val plainField = textField(value = "hi")
                val styledInput = styledTextField(placeholder = "Styled")
                label("Plain", target = plainField)
                label("Styled", target = styledInput)
            }
            val plainCtrl = assertIs<TextField>(webRoot.children[0])
            val styledCtrl = assertIs<StyledTextField>(webRoot.children[1])
            val webPlainLbl = assertIs<Label>(webRoot.children[2])
            val webStyledLbl = assertIs<Label>(webRoot.children[3])
            assertFalse(plainCtrl.id.isNullOrBlank())
            assertFalse(styledCtrl.id.isNullOrBlank())
            assertEquals(plainCtrl.id, webPlainLbl.getAttribute("for"))
            assertEquals(styledCtrl.id, webStyledLbl.getAttribute("for"))
        } finally {
            GenericUI.activeFactory = prevFactory
        }
    }
}

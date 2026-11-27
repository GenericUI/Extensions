package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.feedback.Alert
import com.nexustools.genericui.component.form.CheckBox
import com.nexustools.genericui.component.form.NumberField
import com.nexustools.genericui.component.form.PasswordField
import com.nexustools.genericui.component.form.Select
import com.nexustools.genericui.component.form.Slider
import com.nexustools.genericui.component.form.Switch
import com.nexustools.genericui.component.form.TextArea
import com.nexustools.genericui.component.form.TextField
import com.nexustools.genericui.component.layout.Column
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceStore
import com.nexustools.genericui.component.typography.Badge
import com.nexustools.genericui.component.typography.ILabelTarget
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.AlignSelf
import com.nexustools.genericui.enums.bootstrap.BootstrapType
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
import com.nexustools.genericui.style.css.CssLength
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SchemaExtensionTest {

    private data class SamplePlayerModel(
        val heroName: String = "Aria",
        val level: Int = 5,
        val immortal: Boolean = false
    )

    private object SamplePlayerDescriptor : SchemaDescriptor<SamplePlayerModel> {
        override val schema: ObjectSchema = ObjectSchema(
            id = "sample_player",
            title = "Sample Player",
            nodes = listOf(
                StringFieldSpec(key = "heroName", label = "Hero Name", defaultValue = "Aria", required = true),
                IntFieldSpec(key = "level", label = "Level", defaultValue = 5, min = 1, max = 20),
                BooleanFieldSpec(
                    key = "immortal",
                    label = "Immortal",
                    defaultValue = false,
                    widgetHint = SchemaWidgetHint.SELECT_ZERO_ONE,
                    falseLabel = "Mortal",
                    trueLabel = "Immortal"
                )
            )
        )

        override fun defaultInstance(): SamplePlayerModel = SamplePlayerModel()

        override fun toValueMap(instance: SamplePlayerModel): Map<String, Any?> = linkedMapOf(
            "heroName" to instance.heroName,
            "level" to instance.level,
            "immortal" to instance.immortal
        )

        override fun fromValueMap(values: Map<String, Any?>): SamplePlayerModel = SamplePlayerModel(
            heroName = values["heroName"]?.toString() ?: "Aria",
            level = (values["level"] as? Number)?.toInt() ?: 5,
            immortal = SchemaValueCodec.coerceBoolean(values["immortal"], false)
        )

        override fun toPreferenceElement(instance: SamplePlayerModel): PreferenceElement =
            SchemaPreferenceCodec.toPreferenceElement(schema, toValueMap(instance))

        override fun fromPreferenceElement(element: PreferenceElement): SamplePlayerModel =
            fromValueMap(SchemaPreferenceCodec.fromPreferenceElement(schema, element))

        override fun toJson(instance: SamplePlayerModel): String =
            SchemaJsonCodec.encodeValues(schema, toValueMap(instance))

        override fun fromJson(json: String): SamplePlayerModel =
            fromValueMap(SchemaJsonCodec.decodeValues(schema, json))

        override fun saveTo(store: PreferenceStore, keyOrPrefix: String, instance: SamplePlayerModel) {
            SchemaPreferenceCodec.saveTo(store, keyOrPrefix, schema, toValueMap(instance))
        }

        override fun loadFrom(store: PreferenceStore, keyOrPrefix: String): SamplePlayerModel =
            fromValueMap(SchemaPreferenceCodec.loadFrom(store, keyOrPrefix, schema))
    }

    private fun collectComponents(root: AbstractComponent): List<AbstractComponent> {
        val result = mutableListOf(root)
        if (root is AbstractContainer) {
            for (child in root.children) {
                result.addAll(collectComponents(child))
            }
        }
        return result
    }

    @Test
    fun testAll12FieldTypesRenderAndRoundtrip() {
        var previewUpdates = 0
        var customConstraintCalls = 0
        val signatureRenderer = object : SchemaCustomFieldRenderer {
            override val rendererId: String = "sig-widget"
            override fun createFieldController(
                spec: CustomFieldSpec,
                initialContext: SchemaEvalContext,
                labelText: String,
                hostForm: SchemaForm
            ): SchemaFieldController = object : SchemaFieldController() {
                private val input = TextField(value = spec.defaultValue?.toString().orEmpty(), placeholder = "")
                private val listeners = mutableListOf<(Any?) -> Unit>()
                override val key: String = spec.key
                override val spec: SchemaNode = spec
                override val labelComponent: Label = Label(labelText)
                override val labelHeaderContainer: HStack = HStack().apply { addChild(labelComponent) }
                override val errorLabel: Label = Label("")
                override val rootComponent: AbstractComponent = input
                private var current: String = spec.defaultValue?.toString().orEmpty()
                override var savedValue: Any? = current
                override var value: Any?
                    get() = current
                    set(newVal) {
                        val s = newVal?.toString().orEmpty()
                        if (current != s) {
                            current = s
                            input.value = s
                            listeners.toList().forEach { it(s) }
                            onAnyChangedCallback?.invoke(key, s)
                        }
                    }

                init {
                    input.onValueChanged = { s ->
                        if (current != s) {
                            current = s
                            listeners.toList().forEach { it(s) }
                            onAnyChangedCallback?.invoke(key, s)
                        }
                    }
                }

                override fun setValueAndSaved(value: Any?) {
                    val s = value?.toString().orEmpty()
                    savedValue = s
                    this.value = s
                }

                override fun save() {
                    savedValue = current
                }

                override fun reset() {
                    value = savedValue
                }

                override fun resetToDefault() {
                    value = spec.defaultValue
                }

                override fun validate(context: SchemaEvalContext): Boolean =
                    !spec.required || current.isNotBlank()

                override fun applyConstraint(target: SchemaConstraintTarget, constraintValue: Any?) {
                    if (target == SchemaConstraintTarget.CUSTOM) {
                        customConstraintCalls++
                    }
                }

                override fun onChange(listener: (Any?) -> Unit) =
                    object : com.nexustools.genericui.core.Disposable {
                        override var isDisposed: Boolean = false
                        init { listeners.add(listener) }
                        override fun dispose() {
                            isDisposed = true
                            listeners.remove(listener)
                        }
                    }
            }
        }

        val itemSubSchema = ObjectSchema(
            id = "sub_item",
            nodes = listOf(
                StringFieldSpec(key = "title", defaultValue = "Default Item", required = true),
                IntFieldSpec(
                    key = "weight",
                    defaultValue = 1,
                    visibleWhen = SchemaCondition.IsTrue(".showWeights")
                )
            )
        )

        val schema = ObjectSchema(
            id = "all_12_fields",
            title = "Comprehensive Schema",
            nodes = listOf(
                BooleanFieldSpec(
                    key = "zeroOneFlag",
                    widgetHint = SchemaWidgetHint.SELECT_ZERO_ONE,
                    falseLabel = "Mortal",
                    trueLabel = "Immortal",
                    defaultValue = false
                ),
                IntFieldSpec(key = "count", defaultValue = 10, min = 0, max = 100, step = 5, unit = "px"),
                DoubleFieldSpec(key = "ratio", defaultValue = 10.0, min = 0.0, max = 20.0, precision = null),
                CompoundNumberFieldSpec(
                    key = "wealth",
                    unitLabels = listOf("G", "S", "C"),
                    radixStep = 100.0,
                    defaultValue = 1.0203
                ),
                StringFieldSpec(key = "notes", defaultValue = "hello", maxLength = 20, multiline = true),
                ColorFieldSpec(key = "tint", defaultValue = "#FF8800", allowAlpha = false),
                SelectFieldSpec(
                    key = "mode",
                    defaultValue = "fast",
                    staticOptions = listOf(
                        SchemaOption("fast", "Fast"),
                        SchemaOption("quality", "Quality")
                    ),
                    searchable = true,
                    allowUnlistedSavedValue = true
                ),
                OrderedMultiSelectFieldSpec(
                    key = "tags",
                    staticOptions = listOf(
                        SchemaOption("alpha", "Alpha"),
                        SchemaOption("beta", "Beta"),
                        SchemaOption("gamma", "Gamma"),
                        SchemaOption("delta", "Delta")
                    ),
                    maxItems = 3,
                    allowDuplicates = false,
                    serializeAsJsonString = true,
                    itemSeparatorProvider = { oneBased -> if (oneBased == 3) "Bonus Tier" else null }
                ),
                PolymorphicRowListFieldSpec(
                    key = "rules",
                    variants = listOf(
                        SchemaRowVariantSpec(
                            id = "eq",
                            label = "Equals",
                            slotFields = listOf(
                                StringFieldSpec(key = "attr"),
                                StringFieldSpec(key = "expected")
                            )
                        )
                    ),
                    includeVariantIdInRow = true,
                    serializeAsJsonString = true
                ),
                CubicBezierFieldSpec(
                    key = "easing",
                    defaultValue = listOf(0.2f, -0.5f, 0.8f, 1.5f),
                    previewFactory = { _, _ ->
                        previewUpdates++
                        Label("CurvePreview")
                    }
                ),
                BooleanFieldSpec(key = "showWeights", defaultValue = true),
                ObjectListFieldSpec(
                    key = "subItems",
                    itemSchema = itemSubSchema,
                    defaultValue = listOf(mapOf("title" to "Sword", "weight" to 3))
                ),
                CustomFieldSpec(
                    key = "signature",
                    rendererId = "sig-widget",
                    defaultValue = "initial-sig"
                )
            ),
            reactiveRules = listOf(
                SchemaReactiveRule(
                    targetPath = "signature",
                    targetConstraint = SchemaConstraintTarget.CUSTOM,
                    sourcePaths = listOf("count"),
                    customKey = "countSynced",
                    syncEvaluator = { ctx -> ctx.getValue("count") }
                )
            )
        )

        val form = SchemaExtension.createForm(
            schema = schema,
            customRenderers = mapOf("sig-widget" to signatureRenderer)
        )

        // Check zero default tooltips across entire generated tree
        for (comp in collectComponents(form)) {
            assertNull(comp.tooltipText, "Unexpected fallback tooltipText on ${comp::class.simpleName}")
        }

        // 1. Boolean SELECT_ZERO_ONE emits "0"/"1" wire strings while keeping Boolean in getData()
        assertEquals(false, form.getData()["zeroOneFlag"])
        assertEquals("0", form.getStringData()["zeroOneFlag"])
        form.findField("zeroOneFlag")!!.value = "1"
        assertEquals(true, form.getData()["zeroOneFlag"])
        assertEquals("1", form.getStringData()["zeroOneFlag"])

        // 3. Double omits trailing .0 when precision == null
        assertEquals("10", form.getStringData()["ratio"])
        assertEquals("10", DoubleFieldSpec.formatDouble(10.0, null))
        assertEquals("10.00", DoubleFieldSpec.formatDouble(10.0, 2))

        // 4. CompoundNumber mixed-radix G/S/C
        val wealthCtrl = form.findField("wealth")!!
        wealthCtrl.value = 12.3456
        val wealthTotal = (wealthCtrl.value as Number).toDouble()
        assertEquals(12.3456, wealthTotal, 1e-4)

        // 6. Color allowAlpha = false forces alpha = 255
        form.findField("tint")!!.value = "#11223380"
        assertEquals("#112233", form.getData()["tint"].toString())

        // 7. Select allowUnlistedSavedValue retains unlisted saved value
        form.findField("mode")!!.value = "legacy-custom"
        assertEquals("legacy-custom", form.getData()["mode"])

        // 8. OrderedMultiSelect add/reorder/delete/maxItems/itemSeparatorProvider
        val multiCtrl = form.findField("tags") as OrderedMultiSelectSchemaFieldController
        assertTrue(multiCtrl.addItem("alpha"))
        assertFalse(multiCtrl.addItem("alpha")) // allowDuplicates = false
        assertTrue(multiCtrl.addItem("beta"))
        assertTrue(multiCtrl.addItem("gamma"))
        assertFalse(multiCtrl.addItem("delta")) // at maxItems = 3
        val multiBadges = collectComponents(multiCtrl.labelHeaderContainer).filterIsInstance<Badge>()
        assertTrue(multiBadges.any { it.text == "0 Left" })
        val sepLabels = collectComponents(multiCtrl.itemsContainer).filterIsInstance<Label>().map { it.text }
        assertTrue("Bonus Tier" in sepLabels)
        assertTrue(multiCtrl.moveItemUp(1))
        assertEquals("[\"beta\",\"alpha\",\"gamma\"]", form.getStringData()["tags"])

        // 9. PolymorphicRowList add + delete + JSON matrix roundtrip
        val polyCtrl = form.findField("rules") as PolymorphicRowListSchemaFieldController
        assertTrue(polyCtrl.addRow("eq"))
        assertTrue((form.getStringData()["rules"] ?: "").contains("\"eq\""))
        assertTrue(polyCtrl.removeRowAt(0))
        assertEquals("[]", form.getStringData()["rules"])

        // 10. CubicBezier clamping X to 0..1 while keeping Y free + custom preview rendered
        assertEquals(1, previewUpdates)
        form.findField("easing")!!.value = listOf(1.5f, -0.75f, -0.2f, 2.25f)
        @Suppress("UNCHECKED_CAST")
        val bezier = form.getData()["easing"] as List<Float>
        assertEquals(listOf(1.0f, -0.75f, 0.0f, 2.25f), bezier)

        // 11. ObjectList parent scope `.showWeights` visibility
        val listCtrl = form.findField("subItems")!!
        val nestedWeightCtrl = listCtrl.findNestedController("[0].weight")!!
        assertTrue(nestedWeightCtrl.isVisible)
        form.findField("showWeights")!!.value = false
        assertFalse(nestedWeightCtrl.isVisible)

        // 12. CustomField participated in reactive rule & modified diff
        assertTrue(customConstraintCalls >= 1)
        form.findField("signature")!!.value = "signed-v2"
        assertTrue("signature" in form.getModifiedData())
    }

    @Test
    fun testSelectAsyncOptionsQueryFilterSuspensionAndStaleGuard() {
        var loaderCallCount = 0
        val pendingCalls = mutableListOf<Pair<Map<String, String>?, (List<SchemaOption>) -> Unit>>()

        val schema = ObjectSchema(
            id = "filtered_select",
            nodes = listOf(
                StringFieldSpec(key = "country", defaultValue = ""),
                SelectFieldSpec(
                    key = "city",
                    defaultValue = null,
                    allowUnlistedSavedValue = true,
                    queryFilterRefs = mapOf(
                        "country" to SchemaFilterValue.FieldRef("country", required = true)
                    ),
                    asyncOptionsLoader = { _, filters ->
                        loaderCallCount++
                        SchemaAsyncResult.async { accept, _ ->
                            pendingCalls.add(filters to accept)
                        }
                    }
                )
            )
        )

        val form = SchemaForm(
            schema = schema,
            initialData = mapOf("country" to "", "city" to "SavedTown")
        )
        // Suspended while required parent `country` is blank, and saved value is preserved
        assertEquals(0, loaderCallCount)
        assertEquals("SavedTown", form.getData()["city"])

        // First non-blank value triggers async load 1
        form.findField("country")!!.value = "US"
        assertEquals(1, loaderCallCount)
        assertEquals("US", pendingCalls[0].first?.get("country"))

        // Rapid second change triggers async load 2 BEFORE call 1 resolves
        form.findField("country")!!.value = "CA"
        assertEquals(2, loaderCallCount)
        assertEquals("CA", pendingCalls[1].first?.get("country"))

        // Resolve second call first, then resolve stale first call out of order
        pendingCalls[1].second(listOf(SchemaOption("tor", "Toronto")))
        pendingCalls[0].second(listOf(SchemaOption("nyc", "New York")))

        val cityCtrl = form.findField("city") as SelectSchemaFieldController
        val optionValues = cityCtrl.selectWidget.options.map { it.value }
        assertTrue("tor" in optionValues)
        assertFalse("nyc" in optionValues, "Stale earlier async options must be ignored")
    }

    @Test
    fun testStructuralGroupsAsyncBufferingAndIndexedGroup() {
        lateinit var acceptAsync: (List<SchemaNode>) -> Unit
        lateinit var rejectAsync: (Throwable) -> Unit
        var mountedCalled = 0

        val schema = ObjectSchema(
            id = "groups_schema",
            nodes = listOf(
                SchemaGroupSpec(
                    key = "display",
                    label = "Display",
                    bordered = true,
                    collapsible = true,
                    onMounted = { mountedCalled++ },
                    children = listOf(
                        IntFieldSpec(key = "width", defaultValue = 1280),
                        IntFieldSpec(key = "height", defaultValue = 720)
                    )
                ),
                SchemaAsyncGroupSpec(
                    key = "remote",
                    label = "Remote",
                    loader = {
                        SchemaAsyncResult.async { accept, reject ->
                            acceptAsync = accept
                            rejectAsync = reject
                        }
                    },
                    onMounted = { mountedCalled++ }
                ),
                SchemaAsyncGroupSpec(
                    key = "failing",
                    label = "Failing",
                    loader = {
                        SchemaAsyncResult.async { _, reject ->
                            rejectAsync = reject
                        }
                    }
                ),
                SchemaIndexedGroupSpec(
                    key = "levels",
                    label = "Levels",
                    count = 5,
                    indexLabelPrefix = "Tier",
                    children = listOf(
                        IntFieldSpec(key = "dc", defaultValue = 10)
                    )
                )
            )
        )

        val form = SchemaForm(schema)
        assertEquals(1, mountedCalled) // sync group mounted

        // Buffer pending data & disabled state BEFORE async group resolves
        form.updateValues(mapOf("remote" to mapOf("endpoint" to "https://cast.local")))
        form.disableAll()
        assertFalse(form.findField("display.width")!!.isEnabled)

        acceptAsync(
            listOf(
                StringFieldSpec(key = "endpoint", defaultValue = "http://localhost")
            )
        )
        assertEquals(2, mountedCalled)
        assertEquals("https://cast.local", form.findField("remote.endpoint")!!.value)
        assertFalse(form.findField("remote.endpoint")!!.isEnabled, "Buffered disableAll must apply on async mount")

        // Failure renders inline danger Alert and marks validate false
        rejectAsync(IllegalStateException("Network offline"))
        val failingCtrl = form.findField("failing")!!
        val alerts = collectComponents(failingCtrl.rootComponent).filterIsInstance<Alert>()
        assertTrue(alerts.any { it.type == BootstrapType.DANGER && it.messageText.text.contains("Network offline") })

        // Re-enable for interactive testing
        form.enableAll()

        // Nested group dirty tracking
        form.findField("display.width")!!.value = 1920
        @Suppress("UNCHECKED_CAST")
        val modDisplay = form.getModifiedData()["display"] as Map<String, Any?>
        assertEquals(mapOf("width" to 1920), modDisplay)

        // Indexed group preserves separate slots across switches
        val levelsCtrl = form.findField("levels") as SchemaIndexedGroupController
        levelsCtrl.selectSlot(0)
        form.findField("levels[0].dc")!!.value = 15
        levelsCtrl.selectSlot(3)
        form.findField("levels[3].dc")!!.value = 22
        levelsCtrl.selectSlot(0)
        @Suppress("UNCHECKED_CAST")
        val allLevels = form.getData()["levels"] as List<Map<String, Any?>>
        assertEquals(5, allLevels.size)
        assertEquals(15, allLevels[0]["dc"])
        assertEquals(10, allLevels[1]["dc"])
        assertEquals(22, allLevels[3]["dc"])
    }

    @Test
    fun testConditionsRelativeScopesAndDeduplicatedReactiveRules() {
        var ruleEvaluations = 0
        var asyncRuleAccepted: ((Any?) -> Unit)? = null
        var asyncRuleEvalCount = 0

        val schema = ObjectSchema(
            id = "reactive_schema",
            nodes = listOf(
                StringFieldSpec(key = "mode", defaultValue = "basic"),
                BooleanFieldSpec(key = "advancedToggle", defaultValue = false),
                IntFieldSpec(
                    key = "limit",
                    defaultValue = 5,
                    min = 0,
                    max = 10,
                    visibleWhen = SchemaCondition.Or(
                        listOf(
                            SchemaCondition.Equals("mode", "pro"),
                            SchemaCondition.IsTrue("advancedToggle")
                        )
                    )
                ),
                IntFieldSpec(
                    key = "unrelated",
                    defaultValue = 1
                ),
                IntFieldSpec(key = "asyncTarget", defaultValue = 10, max = 50)
            ),
            reactiveRules = listOf(
                SchemaReactiveRule(
                    targetPath = "limit",
                    targetConstraint = SchemaConstraintTarget.MAX,
                    sourcePaths = listOf("mode", "advancedToggle", "mode"),
                    syncEvaluator = { ctx ->
                        ruleEvaluations++
                        if (ctx.getString("mode") == "pro") 50 else 10
                    }
                ),
                SchemaReactiveRule(
                    targetPath = "asyncTarget",
                    targetConstraint = SchemaConstraintTarget.MAX,
                    sourcePaths = listOf("mode"),
                    asyncEvaluator = { _ ->
                        asyncRuleEvalCount++
                        SchemaAsyncResult.async { accept, _ ->
                            asyncRuleAccepted = accept
                        }
                    }
                )
            )
        )

        val form = SchemaForm(schema)
        val initialRuleRuns = ruleEvaluations
        val limitCtrl = form.findField("limit")!!
        val originalWidgetRef = limitCtrl.rootComponent
        assertFalse(limitCtrl.isVisible)

        // Changing unrelated field must NOT trigger the rule scoped to mode/advancedToggle
        form.findField("unrelated")!!.value = 9
        assertEquals(initialRuleRuns, ruleEvaluations)

        // Changing mode triggers the deduplicated rule exactly ONCE even though sourcePaths repeated/shared
        form.findField("mode")!!.value = "pro"
        assertEquals(initialRuleRuns + 1, ruleEvaluations)
        assertTrue(limitCtrl.isVisible)
        assertSame(originalWidgetRef, form.findField("limit")!!.rootComponent)

        limitCtrl.value = 40
        assertEquals(40, limitCtrl.value)

        // Test relative parent/grandparent scopes directly on SchemaEvalContext
        val rootCtx = SchemaEvalContext(values = mapOf("Bloodline" to "Draconic"))
        val midCtx = rootCtx.child(mapOf("Class" to "Sorcerer"))
        val leafCtx = midCtx.child(mapOf("spell" to "Fireball"))
        assertTrue(SchemaCondition.Equals(".Class", "Sorcerer").evaluate(leafCtx))
        assertTrue(SchemaCondition.Equals("..Bloodline", "Draconic").evaluate(leafCtx))

        // Verify stale async reactive rule result is discarded
        val firstAsyncCb = asyncRuleAccepted
        form.findField("mode")!!.value = "basic"
        assertEquals(asyncRuleEvalCount, 3)
        val latestAsyncCb = asyncRuleAccepted
        latestAsyncCb?.invoke(30)
        firstAsyncCb?.invoke(99)
        form.findField("asyncTarget")!!.value = 45
        assertEquals(30, form.findField("asyncTarget")!!.value)
    }

    @Test
    fun testLayoutModesLegacyKeysPresetsModalAndPersistenceCodecs() {
        val schema = ObjectSchema(
            id = "layout_modal_test",
            title = "Video Quality",
            sections = listOf(
                SchemaSectionSpec("general", "General Settings"),
                SchemaSectionSpec("expert", "Expert Tuning")
            ),
            presets = listOf(
                SchemaPreset("high", "High", values = mapOf("bitrateMbps" to 25))
            ),
            nodes = listOf(
                IntFieldSpec(
                    key = "bitrateMbps",
                    defaultValue = 10,
                    min = 1,
                    max = 100,
                    legacyKeys = listOf("old_bitrate"),
                    sectionId = "general"
                ),
                StringFieldSpec(
                    key = "encoderName",
                    defaultValue = "x264",
                    required = true,
                    maxLength = 12,
                    sectionId = "expert"
                )
            )
        )

        // TABBED mounts TabContainer
        val tabbedForm = SchemaForm(schema, layoutMode = SchemaLayoutMode.TABBED)
        assertNotNull(tabbedForm.tabContainer)
        assertEquals(2, tabbedForm.tabContainer!!.entries.size)

        // RESPONSIVE_GRID wraps fields in Column inside 12-col grid
        val gridForm = SchemaForm(schema, layoutMode = SchemaLayoutMode.RESPONSIVE_GRID)
        assertEquals("Bitrate Mbps", gridForm.findField("bitrateMbps")!!.labelComponent!!.text)
        assertTrue(gridForm.findField("bitrateMbps")!!.wrapperComponent is Column)

        // Legacy key migration & banner API
        gridForm.setData(mapOf("old_bitrate" to 42, "encoderName" to "nvenc"), markAsSaved = true)
        assertEquals(42, gridForm.getData()["bitrateMbps"])
        assertFalse(gridForm.isChanged)

        // Validation marks invalid control and fails validate()
        gridForm.findField("encoderName")!!.value = "way-too-long-encoder-name"
        assertFalse(gridForm.validate())
        assertTrue("is-invalid" in gridForm.findField("encoderName")!!.rootComponent.classes)

        // Modal preset + apply + cancel
        var appliedMap: Map<String, Any?>? = null
        val modal = SchemaModal(
            schema = schema,
            initialData = mapOf("bitrateMbps" to 10, "encoderName" to "nvenc"),
            onApply = { _, map -> appliedMap = map }
        )
        val presetSelect = modal.presetSelect
        assertNotNull(presetSelect)
        presetSelect.selectedValue = "high"
        presetSelect.onSelectionChanged?.invoke("high")
        assertEquals(25, modal.form.getData()["bitrateMbps"])
        modal.applyButton.triggerClick()
        assertEquals(25, appliedMap?.get("bitrateMbps"))
        assertFalse(modal.form.isChanged)

        // Draft-07 JSON Schema export/import roundtrip
        val draft07Json = SchemaJsonCodec.toJsonSchema(schema)
        assertTrue(draft07Json.contains("http://json-schema.org/draft-07/schema#"))
        val importedSchema = SchemaJsonCodec.fromJsonSchema(draft07Json, id = "reimported")
        assertTrue("bitrateMbps" in importedSchema.fieldsByKey)
        assertTrue("encoderName" in importedSchema.fieldsByKey)

        // PreferenceStore + SchemaRegistry + SamplePlayerDescriptor roundtrip
        SchemaRegistry.register(SamplePlayerDescriptor)
        val store = PreferenceStore("schema_ext_unit_test")
        try {
            val model = SamplePlayerModel(heroName = "Kael", level = 12, immortal = true)
            SamplePlayerDescriptor.saveTo(store, "hero1", model)
            val loaded = SamplePlayerDescriptor.loadFrom(store, "hero1")
            assertEquals(model, loaded)

            val boundForm = SchemaExtension.createForm<SamplePlayerModel>(model)
            assertEquals("1", boundForm.getStringData()["immortal"])
            assertEquals(12, boundForm.toDataClass<SamplePlayerModel>().level)
        } finally {
            SchemaRegistry.unregister(SamplePlayerDescriptor.schema.id)
        }
    }

    @Test
    fun testSliderNumberFieldCompactSizingAndStackedSwitchAlignment() {
        val schema = ObjectSchema(
            id = "form_layout_sizing_test",
            nodes = listOf(
                IntFieldSpec(key = "timeoutMs", defaultValue = 1000, min = 0, max = 60000),
                DoubleFieldSpec(key = "opacity", defaultValue = 0.5, min = 0.0, max = 1.0),
                IntFieldSpec(key = "port", defaultValue = 8080),
                BooleanFieldSpec(key = "enabled", defaultValue = true, widgetHint = SchemaWidgetHint.SWITCH)
            )
        )

        val form = SchemaForm(schema, layoutMode = SchemaLayoutMode.STACKED)

        val intComponents = collectComponents(form.findField("timeoutMs")!!.rootComponent)
        val intSlider = intComponents.filterIsInstance<Slider>().single()
        val intField = intComponents.filterIsInstance<NumberField>().single()
        assertEquals(CssLength.px(120f), intSlider.style.getMinWidth())
        assertEquals(CssLength.px(110f), intField.style.getWidth())
        assertEquals(0f, intField.style.getFlexShrink())

        val doubleComponents = collectComponents(form.findField("opacity")!!.rootComponent)
        val doubleSlider = doubleComponents.filterIsInstance<Slider>().single()
        val doubleField = doubleComponents.filterIsInstance<NumberField>().single()
        assertEquals(CssLength.px(120f), doubleSlider.style.getMinWidth())
        assertEquals(CssLength.px(110f), doubleField.style.getWidth())
        assertEquals(0f, doubleField.style.getFlexShrink())

        val standaloneComponents = collectComponents(form.findField("port")!!.rootComponent)
        val standaloneField = standaloneComponents.filterIsInstance<NumberField>().single()
        assertEquals(1f, standaloneField.style.getFlexGrow())

        val switchComponents = collectComponents(form.findField("enabled")!!.rootComponent)
        val switchCtrl = switchComponents.filterIsInstance<Switch>().single()
        assertEquals(AlignSelf.START, switchCtrl.style.getAlignSelf())
    }

    private class SchemaLabelModeFactory(
        override val backendTarget: BackendTarget
    ) : NativeFactory {
        override val backendName: String = "schema-label-mode-${backendTarget.name.lowercase()}"
        override fun supportsTcp(): Boolean = true
        override fun supportsUdp(): Boolean = true
        private fun unsupported(): Nothing = throw UnsupportedOperationException("Headless schema label test factory")
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

    private fun buildLabelTargetTestSchema(): ObjectSchema = ObjectSchema(
        id = "label_target_binding_schema",
        presets = listOf(SchemaPreset("default", "Default Preset", values = emptyMap())),
        nodes = listOf(
            StringFieldSpec(key = "username", label = "Username", defaultValue = "alice", required = true),
            StringFieldSpec(key = "bio", label = "Bio", multiline = true, defaultValue = "notes"),
            StringFieldSpec(key = "apiKey", label = "API Key", secret = true, defaultValue = "secret"),
            BooleanFieldSpec(key = "useSwitch", label = "Use Switch", defaultValue = true, widgetHint = SchemaWidgetHint.SWITCH),
            BooleanFieldSpec(key = "acceptTerms", label = "Accept Terms", defaultValue = false, widgetHint = SchemaWidgetHint.CHECKBOX),
            BooleanFieldSpec(key = "zeroOneFlag", label = "Zero One", defaultValue = true, widgetHint = SchemaWidgetHint.SELECT_ZERO_ONE),
            IntFieldSpec(key = "bitrate", label = "Bitrate", defaultValue = 10, min = 1, max = 50),
            IntFieldSpec(key = "port", label = "Port", defaultValue = 8080),
            DoubleFieldSpec(key = "gamma", label = "Gamma", defaultValue = 1.5, min = 0.5, max = 3.0),
            SelectFieldSpec(
                key = "codec",
                label = "Codec",
                defaultValue = "h264",
                staticOptions = listOf(SchemaOption("h264", "H.264"), SchemaOption("vp9", "VP9"))
            ),
            ColorFieldSpec(key = "accent", label = "Accent Color", defaultValue = "#336699"),
            CompoundNumberFieldSpec(
                key = "dimensions",
                label = "Dimensions",
                unitLabels = listOf("ft.", "in."),
                radices = listOf(12.0, 1.0),
                defaultValue = 5.5
            ),
            CubicBezierFieldSpec(key = "easing", label = "Easing"),
            OrderedMultiSelectFieldSpec(
                key = "pipeline",
                label = "Pipeline",
                staticOptions = listOf(SchemaOption("a", "Step A"), SchemaOption("b", "Step B"))
            ),
            PolymorphicRowListFieldSpec(
                key = "rules",
                label = "Rules",
                variants = listOf(
                    SchemaRowVariantSpec(
                        id = "eq",
                        label = "Equals",
                        slotFields = listOf(StringFieldSpec("rhs", label = "Right Hand"))
                    ),
                    SchemaRowVariantSpec(
                        id = "ne",
                        label = "Not Equals",
                        slotFields = listOf(StringFieldSpec("rhs", label = "Right Hand"))
                    )
                )
            ),
            ObjectListFieldSpec(
                key = "items",
                label = "Items",
                itemSchema = ObjectSchema(id = "item_sub", nodes = listOf(StringFieldSpec("itemName", label = "Item Name")))
            ),
            SchemaIndexedGroupSpec(
                key = "slots",
                label = "Slots",
                count = 2,
                children = listOf(IntFieldSpec("gain", label = "Gain", defaultValue = 0))
            )
        )
    )

    @Test
    fun testLabelTargetBindingsAcrossWebAndNonWebBackends() {
        val prevFactory = GenericUI.activeFactory
        val keysExpectingTarget = listOf(
            "username", "bio", "apiKey",
            "useSwitch", "acceptTerms", "zeroOneFlag",
            "bitrate", "port", "gamma",
            "codec", "accent", "dimensions",
            "easing", "pipeline", "rules", "items", "slots"
        )
        try {
            // 1. Web backend: DOM id + HTML "for" attribute
            GenericUI.activeFactory = SchemaLabelModeFactory(BackendTarget.Web)
            val webSchema = buildLabelTargetTestSchema()
            val webForm = SchemaForm(webSchema)
            val webModal = SchemaModal(schema = webSchema, showPresetSelector = true)

            assertTrue(webForm.findField("username")!!.primaryLabelTarget is TextField)
            assertTrue(webForm.findField("bio")!!.primaryLabelTarget is TextArea)
            assertTrue(webForm.findField("apiKey")!!.primaryLabelTarget is PasswordField)
            assertTrue(webForm.findField("useSwitch")!!.primaryLabelTarget is Switch)
            assertTrue(webForm.findField("acceptTerms")!!.primaryLabelTarget is CheckBox)
            assertTrue(webForm.findField("zeroOneFlag")!!.primaryLabelTarget is Select)
            assertTrue(webForm.findField("bitrate")!!.primaryLabelTarget is NumberField)
            assertTrue(webForm.findField("port")!!.primaryLabelTarget is NumberField)
            assertTrue(webForm.findField("gamma")!!.primaryLabelTarget is NumberField)
            assertTrue(webForm.findField("codec")!!.primaryLabelTarget is Select)
            assertTrue(webForm.findField("accent")!!.primaryLabelTarget is TextField)
            assertTrue(webForm.findField("dimensions")!!.primaryLabelTarget is NumberField)
            assertTrue(webForm.findField("easing")!!.primaryLabelTarget is NumberField)
            assertTrue(webForm.findField("pipeline")!!.primaryLabelTarget is Select)

            for (key in keysExpectingTarget) {
                val ctrl = webForm.findField(key)!!
                val label = assertNotNull(ctrl.labelComponent, "Missing label for '$key'")
                val target = assertNotNull(ctrl.primaryLabelTarget, "Missing primaryLabelTarget for '$key'")
                assertSame(target, label.target, "Expected label.target to match primaryLabelTarget for '$key'")
                val targetId = target.labelTargetComponent.id
                assertFalse(targetId.isNullOrBlank(), "Expected generated DOM id on target of '$key'")
                assertEquals(targetId, label.getAttribute("for"), "Expected matching HTML 'for' for '$key'")
            }

            // Label constraint preserves target association
            webForm.findField("username")!!.applyConstraint(SchemaConstraintTarget.LABEL, "Account Handle")
            assertEquals("Account Handle *", webForm.findField("username")!!.labelComponent!!.text)
            assertEquals(
                webForm.findField("username")!!.primaryLabelTarget!!.labelTargetComponent.id,
                webForm.findField("username")!!.labelComponent!!.getAttribute("for")
            )

            // CubicBezier all 4 sub-labels X1, Y1, X2, Y2
            val webBezier = webForm.findField("easing")!!
            val webBezierComponents = collectComponents(webBezier.rootComponent)
            val webBezierFields = webBezierComponents.filterIsInstance<NumberField>()
            assertEquals(4, webBezierFields.size)
            val bezierSubNames = listOf("X1", "Y1", "X2", "Y2")
            val subForIds = mutableSetOf<String>()
            for ((idx, subName) in bezierSubNames.withIndex()) {
                val subLabel = webBezierComponents.filterIsInstance<Label>().first { it.text == subName }
                val nf = webBezierFields[idx]
                assertSame(nf, subLabel.target, "Web CubicBezier sub-label $subName should target NumberField[$idx]")
                val forAttr = subLabel.getAttribute("for")
                assertFalse(forAttr.isNullOrBlank())
                assertEquals(nf.id, forAttr)
                subForIds.add(forAttr)
            }
            assertEquals(4, subForIds.size)

            // SchemaModal Preset selector label
            val webPresetLabel = collectComponents(webModal).filterIsInstance<Label>().first { it.text == "Preset" }
            assertSame(webModal.presetSelect, webPresetLabel.target)
            assertEquals(webModal.presetSelect!!.id, webPresetLabel.getAttribute("for"))

            // 2. Non-Web backend: clicking label invokes focusFromLabel() and focuses target
            GenericUI.activeFactory = SchemaLabelModeFactory(BackendTarget.Windows)
            val desktopSchema = buildLabelTargetTestSchema()
            val desktopForm = SchemaForm(desktopSchema)
            val desktopModal = SchemaModal(schema = desktopSchema, showPresetSelector = true)

            for (key in keysExpectingTarget) {
                val ctrl = desktopForm.findField(key)!!
                val label = assertNotNull(ctrl.labelComponent, "Missing label for '$key'")
                val target = assertNotNull(ctrl.primaryLabelTarget, "Missing primaryLabelTarget for '$key'")
                assertNull(label.getAttribute("for"), "Non-Web label for '$key' must not set HTML 'for'")
                val targetComp = target.labelTargetComponent
                targetComp.isFocused = false
                label.triggerClick()
                assertTrue(targetComp.isFocused, "Clicking label '$key' should focus its target component")
            }

            // CubicBezier sub-labels X1, Y1, X2, Y2 focus their respective inputs on click
            val desktopBezier = desktopForm.findField("easing")!!
            val desktopBezierComponents = collectComponents(desktopBezier.rootComponent)
            val desktopBezierFields = desktopBezierComponents.filterIsInstance<NumberField>()
            assertEquals(4, desktopBezierFields.size)
            for ((idx, subName) in bezierSubNames.withIndex()) {
                val subLabel = desktopBezierComponents.filterIsInstance<Label>().first { it.text == subName }
                assertNull(subLabel.getAttribute("for"))
                desktopBezierFields.forEach { it.isFocused = false }
                subLabel.triggerClick()
                for (j in 0 until 4) {
                    assertEquals(j == idx, desktopBezierFields[j].isFocused, "Sub-label $subName clicked: expected field $j focused=${j == idx}")
                }
            }

            // Dynamic nested row slots also get Label.target wired on both orders
            val listCtrl = desktopForm.findField("items") as ObjectListSchemaFieldController
            assertTrue(listCtrl.addItem())
            val nestedNameLabel = collectComponents(listCtrl.listContainer)
                .filterIsInstance<Label>()
                .first { it.text == "Item Name" }
            val nestedNameField = collectComponents(listCtrl.listContainer)
                .filterIsInstance<TextField>()
                .first()
            assertSame(nestedNameField, nestedNameLabel.target)
            nestedNameField.isFocused = false
            nestedNameLabel.triggerClick()
            assertTrue(nestedNameField.isFocused)

            // Both creation orders: bindLabelTarget before vs after mountStandardBody
            val deferredSpec = StringFieldSpec(key = "deferred", label = "Deferred")
            val ctrlBindBeforeMount = object : SchemaBaseFieldController(deferredSpec, "Deferred", false) {
                override var savedValue: Any? = ""
                val input = TextField()
                init {
                    bindLabelTarget(input)
                    assertSame(input, labelComponent.target)
                    mountStandardBody(input, false)
                }
                override var value: Any? = ""
            }
            val ctrlFallbackFromMount = object : SchemaBaseFieldController(deferredSpec, "Deferred Fallback", false) {
                override var savedValue: Any? = ""
                val input = TextField()
                init {
                    assertNull(labelComponent.target)
                    mountStandardBody(input, false, input)
                    assertSame(input, labelComponent.target)
                }
                override var value: Any? = ""
            }
            assertSame(ctrlBindBeforeMount.input, ctrlBindBeforeMount.primaryLabelTarget)
            assertSame(ctrlFallbackFromMount.input, ctrlFallbackFromMount.primaryLabelTarget)

            // SchemaModal Preset selector click-to-focus on non-Web
            val desktopPresetLabel = collectComponents(desktopModal).filterIsInstance<Label>().first { it.text == "Preset" }
            assertNull(desktopPresetLabel.getAttribute("for"))
            desktopModal.presetSelect!!.isFocused = false
            desktopPresetLabel.triggerClick()
            assertTrue(desktopModal.presetSelect!!.isFocused)
        } finally {
            GenericUI.activeFactory = prevFactory
        }
    }
}

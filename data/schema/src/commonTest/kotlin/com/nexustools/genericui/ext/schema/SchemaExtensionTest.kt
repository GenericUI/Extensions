package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.feedback.Alert
import com.nexustools.genericui.component.form.TextField
import com.nexustools.genericui.component.layout.Column
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceStore
import com.nexustools.genericui.component.typography.Badge
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.style.color.RgbaColor
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
}

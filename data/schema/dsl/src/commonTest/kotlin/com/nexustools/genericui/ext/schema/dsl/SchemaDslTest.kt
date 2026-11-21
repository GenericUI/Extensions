package com.nexustools.genericui.ext.schema.dsl

import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.ext.schema.CompoundNumberFieldSpec
import com.nexustools.genericui.ext.schema.ObjectListFieldSpec
import com.nexustools.genericui.ext.schema.PolymorphicRowListFieldSpec
import com.nexustools.genericui.ext.schema.SchemaAsyncResult
import com.nexustools.genericui.ext.schema.SchemaCondition
import com.nexustools.genericui.ext.schema.SchemaConstraintTarget
import com.nexustools.genericui.ext.schema.SchemaGroupSpec
import com.nexustools.genericui.ext.schema.SchemaIndexedGroupSpec
import com.nexustools.genericui.ext.schema.SchemaLayoutMode
import com.nexustools.genericui.ext.schema.SchemaOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SchemaDslTest {

    @Test
    fun testObjectSchemaAndUiDslBuilders() {
        val builtSchema = objectSchema(
            id = "render_dsl_config",
            title = "Render DSL Config",
            description = "Declarative DSL validation schema"
        ) {
            section("general", "General")
            section("curves", "Animation Curves")

            preset("fast", "Fast Preset", values = mapOf("threads" to 2, "opacity" to 0.5f))

            booleanField("enabled", "Enabled", defaultValue = true, sectionId = "general")
            intField("threads", "Threads", defaultValue = 4, min = 1, max = 32, sectionId = "general")
            longField("maxCacheBytes", "Max Cache", defaultValue = 65536L, sectionId = "general")
            doubleField("gamma", "Gamma", defaultValue = 2.2, min = 1.0, max = 3.0, sectionId = "general")
            floatField("opacity", "Opacity", defaultValue = 1.0f, min = 0f, max = 1f, sectionId = "general")
            compoundNumberField(
                key = "size",
                label = "Dimensions",
                unitLabels = listOf("ft.", "in."),
                radices = listOf(12.0, 1.0),
                defaultValue = 6.5,
                sectionId = "general"
            )
            stringField("outputPath", "Output Path", required = true, sectionId = "general")
            colorField("bgColor", "Background", defaultValue = "#223344", sectionId = "general")
            selectField(
                key = "codec",
                label = "Codec",
                defaultValue = "h264",
                staticOptions = listOf(
                    SchemaOption("h264", "H.264"),
                    SchemaOption("vp9", "VP9")
                ),
                sectionId = "general"
            )
            orderedMultiSelectField(
                key = "filters",
                label = "Filters",
                staticOptions = listOf(
                    SchemaOption("denoise", "Denoise"),
                    SchemaOption("sharpen", "Sharpen")
                ),
                maxItems = 2,
                sectionId = "general"
            )
            polymorphicRowListField(
                key = "filtersMatrix",
                label = "Filter Matrix",
                sectionId = "general"
            ) {
                variant("eq", "Equals") {
                    stringField("lhs")
                    stringField("rhs")
                }
            }
            cubicBezierField("easing", "Easing", sectionId = "curves")
            objectListField("keyframes", "Keyframes", sectionId = "curves") {
                doubleField("time", defaultValue = 0.0)
                doubleField("value", defaultValue = 1.0)
            }
            customField("customTag", "Custom Tag", rendererId = "missing-until-runtime")
            separator("sep1", "Groups", sectionId = "general")
            group("sub", "Sub Settings") {
                booleanField("verbose", defaultValue = false)
            }
            asyncGroup("remote", "Remote Group") {
                SchemaAsyncResult.resolved(emptyList())
            }
            indexedGroup("slots", "Slots", count = 3, indexLabelPrefix = "Slot") {
                intField("power", defaultValue = 1)
            }

            reactiveRule(
                targetPath = "threads",
                targetConstraint = SchemaConstraintTarget.MAX,
                sourcePaths = listOf("enabled"),
                syncEvaluator = { ctx ->
                    if (SchemaCondition.IsTrue("enabled").evaluate(ctx)) 32 else 4
                }
            )
        }

        assertEquals("render_dsl_config", builtSchema.id)
        assertEquals(2, builtSchema.sections.size)
        assertEquals(1, builtSchema.presets.size)
        assertTrue(builtSchema.nodesByKey["size"] is CompoundNumberFieldSpec)
        assertTrue(builtSchema.nodesByKey["filtersMatrix"] is PolymorphicRowListFieldSpec)
        assertTrue(builtSchema.nodesByKey["keyframes"] is ObjectListFieldSpec)
        assertTrue(builtSchema.nodesByKey["sub"] is SchemaGroupSpec)
        assertTrue(builtSchema.nodesByKey["slots"] is SchemaIndexedGroupSpec)
        assertEquals(1, builtSchema.reactiveRules.size)

        val host = VStack()
        val mountedForm = host.schemaForm(
            id = "inline_host_form",
            title = "Inline Host Form",
            layoutMode = SchemaLayoutMode.STACKED
        ) {
            stringField("title", "Title", defaultValue = "Clip")
            intField("fps", "FPS", defaultValue = 60)
        }
        assertEquals(1, host.children.size)
        assertEquals(60, mountedForm.getData()["fps"])

        val modal = schemaModal(
            id = "inline_modal",
            title = "Quick Settings"
        ) {
            booleanField("loop", "Loop", defaultValue = true)
        }
        assertNotNull(modal.form.findField("loop"))
        assertEquals(true, modal.form.getData()["loop"])
    }
}

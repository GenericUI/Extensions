package com.nexustools.genericui.ext.schema

/**
 * RGBA color field specification rendered via [com.nexustools.genericui.component.form.ColorPicker]
 * paired with a synchronized hex [com.nexustools.genericui.component.form.TextField] and optional swatches.
 */
data class ColorFieldSpec(
    override val key: String,
    override val label: String = "",
    override val description: String? = null,
    override val defaultValue: String = "#000000",
    val allowAlpha: Boolean = true,
    val quickChips: List<String> = emptyList(),
    val returnRgbaColorValue: Boolean = false,
    override val placeholder: String? = null,
    override val sectionId: String? = null,
    override val fullWidth: Boolean = false,
    override val required: Boolean = false,
    override val validators: List<SchemaValidator> = emptyList(),
    override val legacyKeys: List<String> = emptyList(),
    override val visibleWhen: SchemaCondition? = null,
    override val enabledWhen: SchemaCondition? = null,
    override val widgetHint: SchemaWidgetHint = SchemaWidgetHint.COLOR,
    override val colSpan: Int? = null
) : SchemaFieldSpec

package com.nexustools.genericui.ext.schema

/**
 * Boolean field specification rendered as a [com.nexustools.genericui.component.form.Switch],
 * [com.nexustools.genericui.component.form.CheckBox], or zero/one [com.nexustools.genericui.component.form.Select].
 */
data class BooleanFieldSpec(
    override val key: String,
    override val label: String = "",
    override val description: String? = null,
    override val defaultValue: Boolean = false,
    override val widgetHint: SchemaWidgetHint = SchemaWidgetHint.SWITCH,
    val falseLabel: String = "No",
    val trueLabel: String = "Yes",
    val wireZeroOneStrings: Boolean = (widgetHint == SchemaWidgetHint.SELECT_ZERO_ONE),
    override val placeholder: String? = null,
    override val sectionId: String? = null,
    override val fullWidth: Boolean = false,
    override val required: Boolean = false,
    override val validators: List<SchemaValidator> = emptyList(),
    override val legacyKeys: List<String> = emptyList(),
    override val visibleWhen: SchemaCondition? = null,
    override val enabledWhen: SchemaCondition? = null,
    override val colSpan: Int? = null
) : SchemaFieldSpec

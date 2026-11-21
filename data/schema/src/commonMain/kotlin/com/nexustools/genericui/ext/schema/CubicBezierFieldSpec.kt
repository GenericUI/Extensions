package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.core.AbstractComponent

/**
 * 4-float cubic-Bezier easing curve `[x1, y1, x2, y2]` field specification where X coordinates
 * clamp to `0..1`, Y coordinates remain unclamped, and an optional [previewFactory] renders an
 * interactive curve preview component.
 */
data class CubicBezierFieldSpec(
    override val key: String,
    override val label: String = "",
    override val description: String? = null,
    override val defaultValue: List<Float> = listOf(0.25f, 0.1f, 0.25f, 1.0f),
    val previewFactory: ((values: List<Float>, onUpdate: (List<Float>) -> Unit) -> AbstractComponent)? = null,
    override val placeholder: String? = null,
    override val sectionId: String? = null,
    override val fullWidth: Boolean = true,
    override val required: Boolean = false,
    override val validators: List<SchemaValidator> = emptyList(),
    override val legacyKeys: List<String> = emptyList(),
    override val visibleWhen: SchemaCondition? = null,
    override val enabledWhen: SchemaCondition? = null,
    override val widgetHint: SchemaWidgetHint = SchemaWidgetHint.AUTO,
    override val colSpan: Int? = null
) : SchemaFieldSpec

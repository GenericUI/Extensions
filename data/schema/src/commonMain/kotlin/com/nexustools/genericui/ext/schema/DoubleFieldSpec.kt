package com.nexustools.genericui.ext.schema

import kotlin.math.abs
import kotlin.math.round

/**
 * Floating-point (`Double` / `Float`) numeric field specification supporting slider+field,
 * number field, stepper, angle (`°`), and percent (`%`) presentations.
 *
 * When [precision] is `null`, whole numbers format cleanly without trailing `.0`.
 */
data class DoubleFieldSpec(
    override val key: String,
    override val label: String = "",
    override val description: String? = null,
    override val defaultValue: Double = 0.0,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double = 0.1,
    val precision: Int? = null,
    val unit: String? = null,
    val quickChips: List<Double> = emptyList(),
    val isFloat: Boolean = false,
    override val widgetHint: SchemaWidgetHint = SchemaWidgetHint.AUTO,
    override val placeholder: String? = null,
    override val sectionId: String? = null,
    override val fullWidth: Boolean = false,
    override val required: Boolean = false,
    override val validators: List<SchemaValidator> = emptyList(),
    override val legacyKeys: List<String> = emptyList(),
    override val visibleWhen: SchemaCondition? = null,
    override val enabledWhen: SchemaCondition? = null,
    override val colSpan: Int? = null
) : SchemaFieldSpec {

    fun formatNumber(value: Double): String = formatDouble(value, precision)

    fun effectiveUnit(): String? = unit?.takeIf { it.isNotBlank() } ?: when (widgetHint) {
        SchemaWidgetHint.ANGLE -> "°"
        SchemaWidgetHint.PERCENT -> "%"
        else -> null
    }

    companion object {
        fun formatDouble(value: Double, precision: Int? = null): String {
            if (!value.isFinite()) return value.toString()
            val clean = if (value == -0.0) 0.0 else value
            if (precision != null && precision >= 0) {
                if (precision == 0) {
                    return round(clean).toLong().toString()
                }
                var factor = 1.0
                repeat(precision) { factor *= 10.0 }
                val scaled = round(clean * factor).toLong()
                val negative = scaled < 0L
                val absScaled = abs(scaled)
                var intPow = 1L
                repeat(precision) { intPow *= 10L }
                val whole = absScaled / intPow
                val frac = (absScaled % intPow).toString().padStart(precision, '0')
                return buildString {
                    if (negative) append('-')
                    append(whole)
                    append('.')
                    append(frac)
                }
            }
            val roundedLong = round(clean).toLong()
            if (abs(clean - roundedLong.toDouble()) < 1e-9 && abs(clean) < 1e15) {
                return roundedLong.toString()
            }
            val cleanScale = 1_000_000_000.0
            if (abs(clean) < 1e6) {
                val scaled9 = round(clean * cleanScale)
                if (abs(clean - scaled9 / cleanScale) < 1e-12) {
                    val s = (scaled9 / cleanScale).toString()
                    if ('.' in s && 'e' !in s && 'E' !in s) {
                        val trimmed = s.trimEnd('0').trimEnd('.')
                        return if (trimmed == "-0") "0" else trimmed
                    }
                }
            }
            val raw = clean.toString()
            if ('.' in raw && 'e' !in raw && 'E' !in raw) {
                val trimmed = raw.trimEnd('0').trimEnd('.')
                return if (trimmed == "-0") "0" else trimmed
            }
            return raw
        }
    }
}

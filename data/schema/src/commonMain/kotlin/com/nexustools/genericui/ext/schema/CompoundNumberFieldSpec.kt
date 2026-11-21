package com.nexustools.genericui.ext.schema

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

/**
 * Multi-unit / mixed-radix numeric field specification (e.g., `G / S / C`, `ft. / in.`,
 * `lbs. / oz.`, `Days / Hours`, or multi-axis vector tuples).
 */
data class CompoundNumberFieldSpec(
    override val key: String,
    override val label: String = "",
    override val description: String? = null,
    val unitLabels: List<String>,
    val radices: List<Double> = emptyList(),
    val radixStep: Double = 10.0,
    val tupleMode: Boolean = false,
    override val defaultValue: Any? = if (tupleMode) List(unitLabels.size) { 0.0 } else 0.0,
    val min: Double? = null,
    val max: Double? = null,
    val precision: Int? = null,
    val slotSteps: List<Double> = emptyList(),
    override val placeholder: String? = null,
    override val sectionId: String? = null,
    override val fullWidth: Boolean = false,
    override val required: Boolean = false,
    override val validators: List<SchemaValidator> = emptyList(),
    override val legacyKeys: List<String> = emptyList(),
    override val visibleWhen: SchemaCondition? = null,
    override val enabledWhen: SchemaCondition? = null,
    override val widgetHint: SchemaWidgetHint = SchemaWidgetHint.AUTO,
    override val colSpan: Int? = null
) : SchemaFieldSpec {

    init {
        require(unitLabels.isNotEmpty()) { "CompoundNumberFieldSpec '$key' requires at least one unit label." }
    }

    fun effectiveDivisors(): List<Double> {
        val count = unitLabels.size
        if (radices.isEmpty()) {
            val out = ArrayList<Double>(count)
            var div = 1.0
            repeat(count) {
                out.add(div)
                div *= radixStep
            }
            return out
        }
        if (radices.size == count && abs(radices[0] - 1.0) < 1e-9) {
            return radices
        }
        val out = ArrayList<Double>(count)
        var div = 1.0
        out.add(1.0)
        for (i in 1 until count) {
            val stepRadix = radices.getOrNull(i - 1) ?: radixStep
            div *= stepRadix
            out.add(div)
        }
        return out
    }

    fun decompose(total: Double): List<Double> {
        val count = unitLabels.size
        if (tupleMode) {
            return List(count) { if (it == 0) total else 0.0 }
        }
        val divisors = effectiveDivisors()
        val sign = if (total < 0.0) -1.0 else 1.0
        var rem = abs(total)
        val slots = ArrayList<Double>(count)
        for (i in 0 until count) {
            if (i == count - 1) {
                val rounded = round(rem)
                val clean = if (abs(rem - rounded) < 1e-6) rounded else rem
                slots.add(if (clean == 0.0) 0.0 else sign * clean)
            } else {
                val stepFactor = divisors[i + 1] / divisors[i]
                val whole = floor(rem + 1e-9)
                rem = ((rem - whole) * stepFactor).coerceAtLeast(0.0)
                slots.add(if (whole == 0.0) 0.0 else sign * whole)
            }
        }
        return slots
    }

    fun compose(slots: List<Double>): Double {
        val count = unitLabels.size
        if (tupleMode) {
            return slots.firstOrNull() ?: 0.0
        }
        val divisors = effectiveDivisors()
        var sum = 0.0
        for (i in 0 until count) {
            val v = slots.getOrNull(i) ?: 0.0
            sum += v / divisors[i]
        }
        val minBound = min
        val maxBound = max
        return when {
            minBound != null && maxBound != null -> sum.coerceIn(minBound, maxBound)
            minBound != null -> sum.coerceAtLeast(minBound)
            maxBound != null -> sum.coerceAtMost(maxBound)
            else -> sum
        }
    }
}

package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.form.NumberField
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.SpacingScale

internal class CompoundNumberSchemaFieldController(
    private val compoundSpec: CompoundNumberFieldSpec,
    initialLabel: String,
    compactInline: Boolean
) : SchemaBaseFieldController(compoundSpec, initialLabel, compactInline) {

    private var currentMin: Double? = compoundSpec.min
    private var currentMax: Double? = compoundSpec.max
    private var currentVal: Any = SchemaValueCodec.coerceCompoundValue(compoundSpec, compoundSpec.defaultValue)
    override var savedValue: Any? = deepCopyValue(currentVal)

    private val slotFields = mutableListOf<NumberField>()
    private val rowContainer = HStack(SpacingScale.S2).apply {
        alignItems = AlignItems.CENTER
        addClass("input-group")
    }

    init {
        val initialSlots = extractSlotsFromValue(currentVal)
        for (i in compoundSpec.unitLabels.indices) {
            val step = compoundSpec.slotSteps.getOrNull(i) ?: 1.0
            val slotVal = initialSlots.getOrElse(i) { 0.0 }
            val nf = NumberField(
                numericValue = slotVal,
                step = step
            ).apply {
                value = DoubleFieldSpec.formatDouble(slotVal, compoundSpec.precision)
                onValueChanged = {
                    if (!suppressUiSync) {
                        recomputeFromSlotFields()
                    }
                }
            }
            slotFields.add(nf)
            registerInvalidTarget(nf)
            rowContainer.addChild(nf)
            val unitLbl = compoundSpec.unitLabels[i]
            if (unitLbl.isNotBlank()) {
                rowContainer.addChild(Label(unitLbl, "span", "input-group-text"))
            }
        }
        mountStandardBody(rowContainer, compactInline, *slotFields.toTypedArray())
    }

    private fun extractSlotsFromValue(v: Any?): List<Double> {
        return if (compoundSpec.tupleMode) {
            val list = v as? List<*> ?: emptyList<Any?>()
            List(compoundSpec.unitLabels.size) { idx ->
                (list.getOrNull(idx) as? Number)?.toDouble() ?: 0.0
            }
        } else {
            compoundSpec.decompose((v as? Number)?.toDouble() ?: 0.0)
        }
    }

    private fun recomputeFromSlotFields() {
        val slots = slotFields.map { it.value.trim().toDoubleOrNull() ?: 0.0 }
        val nextVal: Any = if (compoundSpec.tupleMode) {
            slots
        } else {
            val raw = compoundSpec.compose(slots)
            val lo = currentMin
            val hi = currentMax
            when {
                lo != null && hi != null -> raw.coerceIn(lo, hi)
                lo != null -> raw.coerceAtLeast(lo)
                hi != null -> raw.coerceAtMost(hi)
                else -> raw
            }
        }
        val changed = !SchemaValueCodec.valuesEqual(compoundSpec, currentVal, nextVal)
        currentVal = nextVal
        if (changed) notifyValueChanged(nextVal)
    }

    override var value: Any?
        get() = currentVal
        set(newVal) {
            val coerced = SchemaValueCodec.coerceCompoundValue(compoundSpec, newVal)
            val nextClamped: Any = if (!compoundSpec.tupleMode && coerced is Number) {
                val lo = currentMin
                val hi = currentMax
                val d = coerced.toDouble()
                when {
                    lo != null && hi != null -> d.coerceIn(lo, hi)
                    lo != null -> d.coerceAtLeast(lo)
                    hi != null -> d.coerceAtMost(hi)
                    else -> d
                }
            } else {
                coerced
            }
            val changed = !SchemaValueCodec.valuesEqual(compoundSpec, currentVal, nextClamped)
            currentVal = nextClamped
            val slots = extractSlotsFromValue(nextClamped)
            suppressUiSync = true
            try {
                slotFields.forEachIndexed { idx, nf ->
                    nf.value = DoubleFieldSpec.formatDouble(slots.getOrElse(idx) { 0.0 }, compoundSpec.precision)
                }
            } finally {
                suppressUiSync = false
            }
            if (changed) notifyValueChanged(nextClamped)
        }

    override fun applyEnabledState(enabled: Boolean) {
        super.applyEnabledState(enabled)
        for (nf in slotFields) nf.isEnabled = enabled
    }

    override fun applySpecificConstraint(target: SchemaConstraintTarget, constraintValue: Any?) {
        when (target) {
            SchemaConstraintTarget.MIN -> {
                currentMin = (constraintValue as? Number)?.toDouble() ?: constraintValue?.toString()?.trim()?.toDoubleOrNull()
                value = currentVal
            }
            SchemaConstraintTarget.MAX -> {
                currentMax = (constraintValue as? Number)?.toDouble() ?: constraintValue?.toString()?.trim()?.toDoubleOrNull()
                value = currentVal
            }
            else -> Unit
        }
    }
}

package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.form.NumberField
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import kotlin.math.abs

internal class CubicBezierSchemaFieldController(
    private val bezierSpec: CubicBezierFieldSpec,
    initialLabel: String,
    compactInline: Boolean
) : SchemaBaseFieldController(bezierSpec, initialLabel, compactInline) {

    private val coords = FloatArray(4).apply {
        val initList = SchemaValueCodec.coerceCubicBezierValue(bezierSpec, bezierSpec.defaultValue)
        for (i in 0 until 4) {
            this[i] = initList.getOrElse(i) { 0f }
        }
    }

    override var savedValue: Any? = coords.toList()

    private val numberFields = ArrayList<NumberField>(4)
    private var previewComponent: AbstractComponent? = null

    init {
        val labels = listOf("X1", "Y1", "X2", "Y2")
        val row = HStack(SpacingScale.S2).apply {
            alignItems = AlignItems.CENTER
        }
        for (i in 0 until 4) {
            val isX = (i == 0 || i == 2)
            val nf = NumberField(
                numericValue = coords[i].toDouble(),
                minValue = if (isX) 0.0 else -Double.MAX_VALUE,
                maxValue = if (isX) 1.0 else Double.MAX_VALUE,
                step = 0.05
            ).apply {
                value = DoubleFieldSpec.formatDouble(coords[i].toDouble(), 3)
                onValueChanged = { text ->
                    if (!suppressUiSync) {
                        val parsed = text.trim().toFloatOrNull()
                        if (parsed != null && parsed.isFinite()) {
                            updateCoordinate(i, parsed, syncField = false)
                        }
                    }
                }
            }
            numberFields.add(nf)
            registerInvalidTarget(nf)

            val slotCol = VStack(SpacingScale.S1).apply {
                addChild(Label(labels[i]))
                addChild(nf)
            }
            row.addChild(slotCol, weight = 1f)
        }

        val body = VStack(SpacingScale.S2).apply {
            addChild(row)
        }
        val factory = bezierSpec.previewFactory
        if (factory != null) {
            val preview = factory.invoke(coords.toList()) { updated ->
                value = updated
            }
            previewComponent = preview
            body.addChild(preview)
        }
        mountStandardBody(body, compactInline, *numberFields.toTypedArray())
    }

    override var value: Any?
        get() = coords.toList()
        set(newVal) {
            val coerced = SchemaValueCodec.coerceCubicBezierValue(bezierSpec, newVal)
            val changed = coerced.indices.any { idx -> abs(coords[idx] - coerced[idx]) > 1e-6f }
            for (i in 0 until 4) {
                coords[i] = coerced[i]
            }
            syncFieldsToCoords()
            if (changed) notifyValueChanged(coords.toList())
        }

    override fun applyEnabledState(enabled: Boolean) {
        super.applyEnabledState(enabled)
        for (nf in numberFields) {
            nf.isEnabled = enabled
        }
        previewComponent?.isEnabled = enabled
    }

    override fun collectBuiltInValidationErrors(
        current: Any?,
        context: SchemaEvalContext,
        label: String,
        errorsOut: MutableList<String>
    ) {
        if (coords[0] < 0f || coords[0] > 1f || coords[2] < 0f || coords[2] > 1f) {
            errorsOut.add("$label X control points must be between 0 and 1.")
        }
    }

    private fun updateCoordinate(index: Int, rawFloat: Float, syncField: Boolean) {
        val nextVal = if (index == 0 || index == 2) rawFloat.coerceIn(0f, 1f) else rawFloat
        val changed = abs(coords[index] - nextVal) > 1e-6f
        coords[index] = nextVal
        if (syncField || nextVal != rawFloat) {
            suppressUiSync = true
            try {
                numberFields[index].value = DoubleFieldSpec.formatDouble(nextVal.toDouble(), 3)
            } finally {
                suppressUiSync = false
            }
        }
        if (changed) notifyValueChanged(coords.toList())
    }

    private fun syncFieldsToCoords() {
        suppressUiSync = true
        try {
            for (i in 0 until 4) {
                numberFields[i].value = DoubleFieldSpec.formatDouble(coords[i].toDouble(), 3)
            }
        } finally {
            suppressUiSync = false
        }
    }
}

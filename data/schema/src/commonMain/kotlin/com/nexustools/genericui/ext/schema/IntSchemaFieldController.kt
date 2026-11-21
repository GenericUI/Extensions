package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.form.NumberField
import com.nexustools.genericui.component.form.Slider
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.enums.bootstrap.SpacingScale

internal class IntSchemaFieldController(
    private val intSpec: IntFieldSpec,
    initialLabel: String,
    compactInline: Boolean
) : SchemaBaseFieldController(intSpec, initialLabel, compactInline) {

    private var currentMin: Int? = intSpec.min
    private var currentMax: Int? = intSpec.max
    private var currentStep: Int = intSpec.step.coerceAtLeast(1)

    private var currentVal: Long = if (intSpec.isLong) intSpec.defaultLongValue else intSpec.defaultValue.toLong()
    override var savedValue: Any? = if (intSpec.isLong) currentVal else currentVal.toInt()

    private val numberField: NumberField
    private var slider: Slider? = null
    private var minusButton: Button? = null
    private var plusButton: Button? = null
    private val chipButtons = mutableListOf<Button>()

    init {
        val minBound = currentMin?.toDouble() ?: -Double.MAX_VALUE
        val maxBound = currentMax?.toDouble() ?: Double.MAX_VALUE
        numberField = NumberField(
            numericValue = currentVal.toDouble(),
            minValue = minBound,
            maxValue = maxBound,
            step = currentStep.toDouble()
        ).apply {
            placeholder = intSpec.placeholder ?: ""
            value = currentVal.toString()
            onValueChanged = { text ->
                if (!suppressUiSync) {
                    val parsed = text.trim().toLongOrNull() ?: text.trim().toDoubleOrNull()?.toLong()
                    if (parsed != null) {
                        applyNumericValue(parsed, syncTextField = false)
                    }
                }
            }
        }
        registerInvalidTarget(numberField)

        val useSlider = intSpec.widgetHint == SchemaWidgetHint.SLIDER_WITH_FIELD ||
            (intSpec.widgetHint == SchemaWidgetHint.AUTO && currentMin != null && currentMax != null)
        val useStepper = intSpec.widgetHint == SchemaWidgetHint.STEPPER

        val row = HStack(SpacingScale.S2).apply {
            alignItems = AlignItems.CENTER
        }
        if (useSlider) {
            val sMin = (currentMin ?: 0).toDouble()
            val sMax = (currentMax ?: 100).toDouble().coerceAtLeast(sMin)
            slider = Slider(
                value = currentVal.toDouble().coerceIn(sMin, sMax),
                min = sMin,
                max = sMax,
                step = currentStep.toDouble()
            ).apply {
                onValueChanged = { d ->
                    if (!suppressUiSync) {
                        applyNumericValue(kotlin.math.round(d).toLong(), syncTextField = true)
                    }
                }
            }
            row.addChild(slider!!, weight = 1f)
            registerInvalidTarget(slider!!)
        }
        if (useStepper) {
            minusButton = Button("-", type = BootstrapType.SECONDARY, size = BootstrapSize.SM).apply {
                onClick = { applyNumericValue(currentVal - currentStep, syncTextField = true) }
            }
            row.addChild(minusButton!!)
        }
        row.addChild(numberField)
        if (useStepper) {
            plusButton = Button("+", type = BootstrapType.SECONDARY, size = BootstrapSize.SM).apply {
                onClick = { applyNumericValue(currentVal + currentStep, syncTextField = true) }
            }
            row.addChild(plusButton!!)
        }
        if (!intSpec.unit.isNullOrBlank()) {
            row.addChild(Label(intSpec.unit, "span", "input-group-text"))
        }

        val body: AbstractComponent = if (intSpec.quickChips.isNotEmpty()) {
            val container = VStack(SpacingScale.S1)
            container.addChild(row)
            val chipsRow = HStack(SpacingScale.S1).apply {
                flexWrap = FlexWrap.WRAP
            }
            for (chipVal in intSpec.quickChips) {
                val btn = Button(chipVal.toString(), type = BootstrapType.SECONDARY, size = BootstrapSize.SM, outline = true).apply {
                    onClick = { applyNumericValue(chipVal.toLong(), syncTextField = true) }
                }
                chipButtons.add(btn)
                chipsRow.addChild(btn)
            }
            container.addChild(chipsRow)
            container
        } else {
            row
        }
        mountStandardBody(body, compactInline, numberField)
    }

    private fun clampToCurrentBounds(raw: Long): Long {
        val lo = currentMin?.toLong()
        val hi = currentMax?.toLong()
        return when {
            lo != null && hi != null -> raw.coerceIn(lo, hi)
            lo != null -> raw.coerceAtLeast(lo)
            hi != null -> raw.coerceAtMost(hi)
            else -> raw
        }
    }

    private fun applyNumericValue(raw: Long, syncTextField: Boolean) {
        val clamped = clampToCurrentBounds(raw)
        val changed = currentVal != clamped
        currentVal = clamped
        suppressUiSync = true
        try {
            slider?.value = clamped.toDouble()
            if (syncTextField) {
                numberField.value = clamped.toString()
            }
        } finally {
            suppressUiSync = false
        }
        if (changed) notifyValueChanged(if (intSpec.isLong) clamped else clamped.toInt())
    }

    override var value: Any?
        get() = if (intSpec.isLong) currentVal else currentVal.toInt()
        set(newVal) {
            val coerced = clampToCurrentBounds(SchemaValueCodec.parseLongUnclamped(intSpec, newVal))
            applyNumericValue(coerced, syncTextField = true)
        }

    override fun setValueAndSaved(value: Any?) {
        val coerced = clampToCurrentBounds(SchemaValueCodec.parseLongUnclamped(intSpec, value))
        savedValue = if (intSpec.isLong) coerced else coerced.toInt()
        applyNumericValue(coerced, syncTextField = true)
    }

    override fun collectBuiltInValidationErrors(
        current: Any?,
        context: SchemaEvalContext,
        label: String,
        errorsOut: MutableList<String>
    ) {
        if (currentMin != null || currentMax != null) {
            SchemaValidator.NumericRange(currentMin?.toDouble(), currentMax?.toDouble())
                .validate(current, context, label)?.let { errorsOut.add(it) }
        }
    }

    override fun applyEnabledState(enabled: Boolean) {
        super.applyEnabledState(enabled)
        numberField.isEnabled = enabled
        slider?.isEnabled = enabled
        minusButton?.isEnabled = enabled
        plusButton?.isEnabled = enabled
        for (btn in chipButtons) btn.isEnabled = enabled
    }

    override fun applySpecificConstraint(target: SchemaConstraintTarget, constraintValue: Any?) {
        when (target) {
            SchemaConstraintTarget.MIN -> {
                currentMin = (constraintValue as? Number)?.toInt() ?: constraintValue?.toString()?.trim()?.toDoubleOrNull()?.toInt()
                numberField.minValue = currentMin?.toDouble() ?: -Double.MAX_VALUE
                slider?.min = (currentMin ?: 0).toDouble()
                applyNumericValue(currentVal, syncTextField = true)
            }
            SchemaConstraintTarget.MAX -> {
                currentMax = (constraintValue as? Number)?.toInt() ?: constraintValue?.toString()?.trim()?.toDoubleOrNull()?.toInt()
                numberField.maxValue = currentMax?.toDouble() ?: Double.MAX_VALUE
                slider?.max = (currentMax ?: 100).toDouble()
                applyNumericValue(currentVal, syncTextField = true)
            }
            SchemaConstraintTarget.STEP -> {
                val nextStep = ((constraintValue as? Number)?.toInt() ?: constraintValue?.toString()?.trim()?.toIntOrNull())?.coerceAtLeast(1) ?: return
                currentStep = nextStep
                numberField.step = nextStep.toDouble()
                slider?.step = nextStep.toDouble()
            }
            else -> Unit
        }
    }
}

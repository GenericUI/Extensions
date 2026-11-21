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
import kotlin.math.abs

internal class DoubleSchemaFieldController(
    private val doubleSpec: DoubleFieldSpec,
    initialLabel: String,
    compactInline: Boolean
) : SchemaBaseFieldController(doubleSpec, initialLabel, compactInline) {

    private var currentMin: Double? = doubleSpec.min
    private var currentMax: Double? = doubleSpec.max
    private var currentStep: Double = doubleSpec.step.takeIf { it > 0.0 } ?: 0.1

    private var currentVal: Double = doubleSpec.defaultValue
    override var savedValue: Any? = if (doubleSpec.isFloat) currentVal.toFloat() else currentVal

    private val numberField: NumberField
    private var slider: Slider? = null
    private var minusButton: Button? = null
    private var plusButton: Button? = null
    private val chipButtons = mutableListOf<Button>()

    init {
        numberField = NumberField(
            numericValue = currentVal,
            minValue = currentMin ?: -Double.MAX_VALUE,
            maxValue = currentMax ?: Double.MAX_VALUE,
            step = currentStep
        ).apply {
            placeholder = doubleSpec.placeholder ?: ""
            value = DoubleFieldSpec.formatDouble(currentVal, doubleSpec.precision)
            onValueChanged = { text ->
                if (!suppressUiSync) {
                    val parsed = text.trim().toDoubleOrNull()
                    if (parsed != null && parsed.isFinite()) {
                        applyNumericValue(parsed, syncTextField = false)
                    }
                }
            }
        }
        registerInvalidTarget(numberField)

        val useSlider = doubleSpec.widgetHint == SchemaWidgetHint.SLIDER_WITH_FIELD ||
            (doubleSpec.widgetHint == SchemaWidgetHint.AUTO && currentMin != null && currentMax != null)
        val useStepper = doubleSpec.widgetHint == SchemaWidgetHint.STEPPER

        val row = HStack(SpacingScale.S2).apply {
            alignItems = AlignItems.CENTER
        }
        if (useSlider) {
            val sMin = currentMin ?: 0.0
            val sMax = (currentMax ?: 100.0).coerceAtLeast(sMin)
            slider = Slider(
                value = currentVal.coerceIn(sMin, sMax),
                min = sMin,
                max = sMax,
                step = currentStep
            ).apply {
                onValueChanged = { d ->
                    if (!suppressUiSync) {
                        applyNumericValue(d, syncTextField = true)
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
        doubleSpec.effectiveUnit()?.let { unitText ->
            row.addChild(Label(unitText, "span", "input-group-text"))
        }

        val body: AbstractComponent = if (doubleSpec.quickChips.isNotEmpty()) {
            val container = VStack(SpacingScale.S1)
            container.addChild(row)
            val chipsRow = HStack(SpacingScale.S1).apply {
                flexWrap = FlexWrap.WRAP
            }
            for (chipVal in doubleSpec.quickChips) {
                val btn = Button(
                    DoubleFieldSpec.formatDouble(chipVal, doubleSpec.precision),
                    type = BootstrapType.SECONDARY,
                    size = BootstrapSize.SM,
                    outline = true
                ).apply {
                    onClick = { applyNumericValue(chipVal, syncTextField = true) }
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

    private fun clampToCurrentBounds(raw: Double): Double {
        val lo = currentMin
        val hi = currentMax
        return when {
            lo != null && hi != null -> raw.coerceIn(lo, hi)
            lo != null -> raw.coerceAtLeast(lo)
            hi != null -> raw.coerceAtMost(hi)
            else -> raw
        }
    }

    private fun applyNumericValue(raw: Double, syncTextField: Boolean) {
        val clamped = clampToCurrentBounds(raw)
        val tol = if (doubleSpec.isFloat) 1e-6 else 1e-9
        val changed = abs(currentVal - clamped) > tol
        currentVal = clamped
        suppressUiSync = true
        try {
            slider?.value = clamped
            if (syncTextField) {
                numberField.value = DoubleFieldSpec.formatDouble(clamped, doubleSpec.precision)
            }
        } finally {
            suppressUiSync = false
        }
        if (changed) notifyValueChanged(if (doubleSpec.isFloat) clamped.toFloat() else clamped)
    }

    override var value: Any?
        get() = if (doubleSpec.isFloat) currentVal.toFloat() else currentVal
        set(newVal) {
            val coerced = clampToCurrentBounds(SchemaValueCodec.parseDoubleUnclamped(doubleSpec, newVal))
            applyNumericValue(coerced, syncTextField = true)
        }

    override fun setValueAndSaved(value: Any?) {
        val coerced = clampToCurrentBounds(SchemaValueCodec.parseDoubleUnclamped(doubleSpec, value))
        savedValue = if (doubleSpec.isFloat) coerced.toFloat() else coerced
        applyNumericValue(coerced, syncTextField = true)
    }

    override val stringValue: String
        get() = DoubleFieldSpec.formatDouble(currentVal, doubleSpec.precision)

    override fun collectBuiltInValidationErrors(
        current: Any?,
        context: SchemaEvalContext,
        label: String,
        errorsOut: MutableList<String>
    ) {
        if (currentMin != null || currentMax != null) {
            SchemaValidator.NumericRange(currentMin, currentMax)
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
                currentMin = (constraintValue as? Number)?.toDouble() ?: constraintValue?.toString()?.trim()?.toDoubleOrNull()
                numberField.minValue = currentMin ?: -Double.MAX_VALUE
                slider?.min = currentMin ?: 0.0
                applyNumericValue(currentVal, syncTextField = true)
            }
            SchemaConstraintTarget.MAX -> {
                currentMax = (constraintValue as? Number)?.toDouble() ?: constraintValue?.toString()?.trim()?.toDoubleOrNull()
                numberField.maxValue = currentMax ?: Double.MAX_VALUE
                slider?.max = currentMax ?: 100.0
                applyNumericValue(currentVal, syncTextField = true)
            }
            SchemaConstraintTarget.STEP -> {
                val nextStep = ((constraintValue as? Number)?.toDouble() ?: constraintValue?.toString()?.trim()?.toDoubleOrNull())?.takeIf { it > 0.0 } ?: return
                currentStep = nextStep
                numberField.step = nextStep
                slider?.step = nextStep
            }
            else -> Unit
        }
    }
}

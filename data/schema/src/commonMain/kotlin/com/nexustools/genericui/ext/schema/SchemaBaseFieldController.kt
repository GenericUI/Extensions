package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.SpacingScale

/**
 * Shared internal base controller managing label chrome, error feedback, validation,
 * baseline/dirty tracking, and common constraint targets.
 */
internal abstract class SchemaBaseFieldController(
    override val spec: SchemaFieldSpec,
    initialLabel: String,
    compactInline: Boolean
) : SchemaFieldController() {

    override val key: String = spec.key

    private val changeListeners = mutableListOf<(Any?) -> Unit>()
    private val invalidTargetControls = mutableListOf<AbstractComponent>()

    override val labelComponent: Label = Label(
        if (spec.required && initialLabel.isNotBlank()) "$initialLabel *" else initialLabel
    )

    override val labelHeaderContainer: HStack = HStack(SpacingScale.S2).apply {
        alignItems = AlignItems.CENTER
        addChild(labelComponent)
        isVisible = initialLabel.isNotBlank()
    }

    override val descriptionLabel: Text = Text(spec.description.orEmpty()).apply {
        addClass("form-text")
        textColorType = BootstrapType.SECONDARY
        isVisible = !spec.description.isNullOrBlank()
    }

    override val errorLabel: Label = Label("", tagName = "div", classPrefix = "invalid-feedback").apply {
        textColorType = BootstrapType.DANGER
        isVisible = false
    }

    override val rootComponent: AbstractComponent = if (compactInline) {
        HStack(SpacingScale.S2).apply {
            alignItems = AlignItems.CENTER
            addClass("gui-schema-field")
            addClass("gui-schema-field-inline")
        }
    } else {
        VStack(SpacingScale.S1).apply {
            addClass("gui-schema-field")
            addClass("mb-2")
        }
    }

    protected var suppressUiSync: Boolean = false
    protected var dynamicDefaultOverride: Any? = null
    protected var hasDynamicDefaultOverride: Boolean = false
    private var rawLabelText: String = initialLabel

    protected fun mountStandardBody(
        controlComponent: AbstractComponent,
        compactInline: Boolean,
        vararg invalidTargets: AbstractComponent
    ) {
        invalidTargetControls.clear()
        invalidTargetControls.addAll(invalidTargets.ifEmpty { arrayOf(controlComponent) })
        if (compactInline) {
            val row = rootComponent as HStack
            row.addChild(labelHeaderContainer)
            row.addChild(controlComponent)
            row.addChild(errorLabel)
        } else {
            val col = rootComponent as VStack
            col.addChild(labelHeaderContainer)
            col.addChild(controlComponent)
            col.addChild(descriptionLabel)
            col.addChild(errorLabel)
        }
    }

    protected fun registerInvalidTarget(component: AbstractComponent) {
        if (component !in invalidTargetControls) {
            invalidTargetControls.add(component)
        }
    }

    protected fun notifyValueChanged(newValue: Any?) {
        for (listener in changeListeners.toList()) {
            listener(newValue)
        }
        onAnyChangedCallback?.invoke(key, newValue)
    }

    override fun onChange(listener: (Any?) -> Unit): Disposable {
        changeListeners.add(listener)
        return object : Disposable {
            override var isDisposed: Boolean = false
                private set

            override fun dispose() {
                if (!isDisposed) {
                    isDisposed = true
                    changeListeners.remove(listener)
                }
            }
        }
    }

    override fun setValueAndSaved(value: Any?) {
        val coerced = SchemaValueCodec.coerceValue(spec, value)
        savedValue = deepCopyValue(coerced)
        this.value = coerced
    }

    override fun save() {
        savedValue = deepCopyValue(value)
    }

    override fun reset() {
        value = deepCopyValue(savedValue)
        setValidationErrors(emptyList())
    }

    override fun resetToDefault() {
        val targetDefault = if (hasDynamicDefaultOverride) {
            dynamicDefaultOverride
        } else {
            SchemaValueCodec.defaultFieldValue(spec)
        }
        value = SchemaValueCodec.coerceValue(spec, targetDefault)
        setValidationErrors(emptyList())
    }

    override fun validate(context: SchemaEvalContext): Boolean {
        if (!isVisible) {
            setValidationErrors(emptyList())
            return true
        }
        val current = value
        val label = rawLabelText.ifBlank { spec.key }
        val errors = mutableListOf<String>()
        if (spec.required) {
            SchemaValidator.Required().validate(current, context, label)?.let { errors.add(it) }
        }
        collectBuiltInValidationErrors(current, context, label, errors)
        for (validator in spec.validators) {
            validator.validate(current, context, label)?.let { errors.add(it) }
        }
        val distinct = errors.distinct()
        setValidationErrors(distinct)
        return distinct.isEmpty()
    }

    protected open fun collectBuiltInValidationErrors(
        current: Any?,
        context: SchemaEvalContext,
        label: String,
        errorsOut: MutableList<String>
    ) {}

    override fun setValidationErrors(errors: List<String>) {
        if (errors.isEmpty()) {
            errorLabel.text = ""
            errorLabel.isVisible = false
            rootComponent.removeClass("is-invalid")
            for (ctrl in invalidTargetControls) {
                ctrl.removeClass("is-invalid")
            }
        } else {
            errorLabel.text = errors.joinToString(" ")
            errorLabel.isVisible = true
            rootComponent.addClass("is-invalid")
            for (ctrl in invalidTargetControls) {
                ctrl.addClass("is-invalid")
            }
        }
    }

    override fun applyConstraint(target: SchemaConstraintTarget, constraintValue: Any?) {
        when (target) {
            SchemaConstraintTarget.VISIBLE -> {
                ruleVisibleOverride = constraintValue?.let { SchemaCondition.isTruthy(it) }
                syncEffectiveStates()
            }
            SchemaConstraintTarget.ENABLED -> {
                ruleEnabledOverride = constraintValue?.let { SchemaCondition.isTruthy(it) }
                syncEffectiveStates()
            }
            SchemaConstraintTarget.LABEL -> {
                val text = constraintValue?.toString().orEmpty()
                rawLabelText = text
                labelComponent.text = if (spec.required && text.isNotBlank()) "$text *" else text
                labelHeaderContainer.isVisible = text.isNotBlank() || labelHeaderContainer.children.size > 1
            }
            SchemaConstraintTarget.DESCRIPTION -> {
                val desc = constraintValue?.toString().orEmpty()
                descriptionLabel.text = desc
                descriptionLabel.isVisible = desc.isNotBlank()
            }
            SchemaConstraintTarget.DEFAULT_VALUE -> {
                val prevDefault = if (hasDynamicDefaultOverride) {
                    dynamicDefaultOverride
                } else {
                    SchemaValueCodec.defaultFieldValue(spec)
                }
                val coercedNewDefault = SchemaValueCodec.coerceValue(spec, constraintValue)
                dynamicDefaultOverride = coercedNewDefault
                hasDynamicDefaultOverride = true
                if (!isChanged || SchemaValueCodec.valuesEqual(spec, value, prevDefault)) {
                    value = coercedNewDefault
                }
            }
            else -> applySpecificConstraint(target, constraintValue)
        }
    }

    protected open fun applySpecificConstraint(target: SchemaConstraintTarget, constraintValue: Any?) {}

    protected fun deepCopyValue(v: Any?): Any? = when (v) {
        is Map<*, *> -> LinkedHashMap<String, Any?>(v.size).apply {
            for ((k, entryVal) in v) {
                if (k != null) put(k.toString(), deepCopyValue(entryVal))
            }
        }
        is List<*> -> v.map { deepCopyValue(it) }
        else -> v
    }
}

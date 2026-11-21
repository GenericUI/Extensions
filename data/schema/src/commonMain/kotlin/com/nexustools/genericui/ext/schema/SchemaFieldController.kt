package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.Disposable

/**
 * Runtime controller handle for a single mounted [SchemaNode] inside a [SchemaForm].
 */
abstract class SchemaFieldController {
    abstract val key: String
    abstract val spec: SchemaNode
    abstract val rootComponent: AbstractComponent
    abstract val labelHeaderContainer: HStack
    abstract val labelComponent: Label?
    abstract val errorLabel: Label?
    open val descriptionLabel: Text? = null

    abstract var value: Any?
    abstract var savedValue: Any?

    open val stringValue: String
        get() = SchemaValueCodec.toWireString(spec, value)

    open val isChanged: Boolean
        get() = !SchemaValueCodec.valuesEqual(spec, value, savedValue)

    internal var formEnabled: Boolean = true
    internal var conditionEnabled: Boolean = true
    internal var ruleEnabledOverride: Boolean? = null

    internal var conditionVisible: Boolean = true
    internal var ruleVisibleOverride: Boolean? = null

    internal var wrapperComponent: AbstractComponent? = null
    internal var onAnyChangedCallback: ((String, Any?) -> Unit)? = null

    open var isEnabled: Boolean = true
        set(newVal) {
            field = newVal
            applyEnabledState(newVal)
        }

    open var isVisible: Boolean = true
        set(newVal) {
            field = newVal
            val target = wrapperComponent ?: rootComponent
            target.isVisible = newVal
            if (newVal) {
                target.removeClass("d-none")
            } else {
                target.addClass("d-none")
            }
        }

    protected open fun applyEnabledState(enabled: Boolean) {
        rootComponent.isEnabled = enabled
    }

    internal fun syncEffectiveStates() {
        val nextVisible = conditionVisible && (ruleVisibleOverride ?: true)
        val nextEnabled = formEnabled && conditionEnabled && (ruleEnabledOverride ?: true)
        if (isVisible != nextVisible) {
            isVisible = nextVisible
        }
        if (isEnabled != nextEnabled) {
            isEnabled = nextEnabled
        }
    }

    abstract fun setValueAndSaved(value: Any?)

    internal open fun setDataValue(value: Any?, markAsSaved: Boolean) {
        if (markAsSaved) {
            setValueAndSaved(value)
        } else {
            this.value = value
        }
    }

    abstract fun save()

    abstract fun reset()

    abstract fun resetToDefault()

    abstract fun validate(context: SchemaEvalContext = SchemaEvalContext()): Boolean

    abstract fun applyConstraint(target: SchemaConstraintTarget, constraintValue: Any?)

    open fun refreshOptions(context: SchemaEvalContext = SchemaEvalContext()) {}

    internal open fun reconcileNestedContext(parentContext: SchemaEvalContext) {}

    abstract fun onChange(listener: (Any?) -> Unit): Disposable

    internal open fun findNestedController(path: String): SchemaFieldController? = null

    internal open fun getModifiedDataValue(): Pair<Boolean, Any?> =
        if (isChanged) true to value else false to null

    internal open fun getModifiedStringDataValue(): Pair<Boolean, String> =
        if (isChanged) true to stringValue else false to ""

    internal open fun setValidationErrors(errors: List<String>) {
        val label = errorLabel ?: return
        if (errors.isEmpty()) {
            label.text = ""
            label.isVisible = false
            rootComponent.removeClass("is-invalid")
        } else {
            label.text = errors.joinToString(" ")
            label.isVisible = true
            rootComponent.addClass("is-invalid")
        }
    }
}

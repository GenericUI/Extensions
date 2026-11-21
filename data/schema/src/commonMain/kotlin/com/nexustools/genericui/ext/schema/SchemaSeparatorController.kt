package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.layout.Divider
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.SpacingScale

internal class SchemaSeparatorController(
    override val spec: SchemaSeparatorSpec,
    initialLabel: String
) : SchemaFieldController() {

    override val key: String = spec.key

    override val labelComponent: Label? = if (initialLabel.isNotBlank()) {
        Label(initialLabel).apply { textColorType = BootstrapType.SECONDARY }
    } else {
        null
    }

    override val labelHeaderContainer: HStack = HStack(SpacingScale.S2).apply {
        alignItems = AlignItems.CENTER
        labelComponent?.let { addChild(it) }
        if (spec.showLine) {
            addChild(Divider(), weight = 1f)
        }
        isVisible = initialLabel.isNotBlank() || spec.showLine
    }

    override val errorLabel: Label? = null
    override val descriptionLabel: Text? = spec.description?.let { desc ->
        Text(desc).apply {
            addClass("form-text")
            textColorType = BootstrapType.SECONDARY
        }
    }

    override val rootComponent: AbstractComponent = VStack(SpacingScale.S1).apply {
        addClass("gui-schema-separator")
        addChild(labelHeaderContainer)
        descriptionLabel?.let { addChild(it) }
    }

    override var value: Any?
        get() = null
        set(_) {}

    override var savedValue: Any?
        get() = null
        set(_) {}

    override val isChanged: Boolean = false

    override fun setValueAndSaved(value: Any?) {}
    override fun save() {}
    override fun reset() {}
    override fun resetToDefault() {}
    override fun validate(context: SchemaEvalContext): Boolean = true

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
                labelComponent?.text = text
            }
            SchemaConstraintTarget.DESCRIPTION -> {
                descriptionLabel?.text = constraintValue?.toString().orEmpty()
            }
            else -> Unit
        }
    }

    override fun onChange(listener: (Any?) -> Unit): Disposable = object : Disposable {
        override var isDisposed: Boolean = true
        override fun dispose() {}
    }

    override fun getModifiedDataValue(): Pair<Boolean, Any?> = false to null
    override fun getModifiedStringDataValue(): Pair<Boolean, String> = false to ""
}

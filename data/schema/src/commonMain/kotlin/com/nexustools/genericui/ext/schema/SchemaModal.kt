package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.form.Select
import com.nexustools.genericui.component.form.SelectOption
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.overlay.Modal
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.SpacingScale

/**
 * Dialog modal wrapping a [SchemaForm] with optional preset selector, header/preview slots,
 * and `Reset Defaults` / `Cancel` / `Apply` actions.
 */
open class SchemaModal(
    val schema: ObjectSchema,
    initialData: Map<String, Any?>? = null,
    val layoutMode: SchemaLayoutMode = SchemaLayoutMode.STACKED,
    val labelTranslator: ((String) -> String)? = null,
    customRenderers: Map<String, SchemaCustomFieldRenderer> = emptyMap(),
    modalTitle: String = schema.title,
    applyButtonText: String = "Apply",
    cancelButtonText: String = "Cancel",
    resetDefaultsButtonText: String = "Reset Defaults",
    showPresetSelector: Boolean = schema.presets.isNotEmpty(),
    showResetDefaultsButton: Boolean = true,
    headerSlot: AbstractComponent? = null,
    previewSlot: AbstractComponent? = null,
    val validateOnApply: Boolean = true,
    val closeOnApply: Boolean = true,
    var onApply: ((SchemaForm, Map<String, Any?>) -> Unit)? = null,
    var onCancel: ((SchemaForm) -> Unit)? = null
) : Modal(title = modalTitle.ifBlank { schema.id }) {

    val form: SchemaForm = SchemaForm(
        schema = schema,
        layoutMode = layoutMode,
        initialData = initialData,
        labelTranslator = labelTranslator,
        customRenderers = customRenderers,
        showHeader = false
    )

    val presetSelect: Select?
    val resetDefaultsButton: Button?
    val cancelButton: Button
    val applyButton: Button

    init {
        addClass("gui-schema-modal")

        headerSlot?.let { addChild(it) }

        if (showPresetSelector && schema.presets.isNotEmpty()) {
            val presetOptions = listOf(SelectOption("", "(Custom)")) +
                schema.presets.map { SelectOption(it.id, it.label) }
            val select = Select(options = presetOptions, selectedValue = "").apply {
                onSelectionChanged = { selectedId ->
                    if (selectedId.isNotBlank()) {
                        form.applyPreset(selectedId)
                    }
                }
            }
            presetSelect = select
            val presetBar = HStack(SpacingScale.S2).apply {
                alignItems = AlignItems.CENTER
                addChild(Label("Preset", target = select))
                addChild(select, weight = 1f)
            }
            addChild(presetBar)
        } else {
            presetSelect = null
        }

        previewSlot?.let { addChild(it) }

        addChild(form)

        resetDefaultsButton = if (showResetDefaultsButton) {
            addLeftButton(
                text = resetDefaultsButtonText,
                type = BootstrapType.SECONDARY,
                size = BootstrapSize.SM,
                outline = true
            ) {
                form.resetToDefaults()
                presetSelect?.selectedValue = ""
            }
        } else {
            null
        }

        cancelButton = addRightButton(
            text = cancelButtonText,
            type = BootstrapType.SECONDARY,
            size = BootstrapSize.SM,
            outline = true
        ) {
            form.reset()
            onCancel?.invoke(form)
            close()
        }

        applyButton = addRightButton(
            text = applyButtonText,
            type = BootstrapType.PRIMARY,
            size = BootstrapSize.SM,
            outline = false
        ) {
            if (!validateOnApply || form.validate()) {
                form.hideBannerMessage()
                val snapshot = form.getData()
                form.save()
                onApply?.invoke(form, snapshot)
                if (closeOnApply) {
                    close()
                }
            } else {
                form.setError("Please resolve the highlighted validation errors.")
            }
        }
    }

    fun selectPreset(presetId: String): Boolean {
        val applied = form.applyPreset(presetId)
        if (applied) {
            presetSelect?.selectedValue = presetId
        }
        return applied
    }

    companion object {
        fun <T : Any> forData(
            descriptor: SchemaDescriptor<T>,
            initialInstance: T = descriptor.defaultInstance(),
            layoutMode: SchemaLayoutMode = SchemaLayoutMode.STACKED,
            labelTranslator: ((String) -> String)? = null,
            customRenderers: Map<String, SchemaCustomFieldRenderer> = emptyMap(),
            modalTitle: String = descriptor.schema.title,
            applyButtonText: String = "Apply",
            cancelButtonText: String = "Cancel",
            resetDefaultsButtonText: String = "Reset Defaults",
            headerSlot: AbstractComponent? = null,
            previewSlot: AbstractComponent? = null,
            validateOnApply: Boolean = true,
            closeOnApply: Boolean = true,
            onApply: ((T) -> Unit)? = null,
            onCancel: (() -> Unit)? = null
        ): SchemaModal = SchemaModal(
            schema = descriptor.schema,
            initialData = descriptor.toValueMap(initialInstance),
            layoutMode = layoutMode,
            labelTranslator = labelTranslator,
            customRenderers = customRenderers,
            modalTitle = modalTitle,
            applyButtonText = applyButtonText,
            cancelButtonText = cancelButtonText,
            resetDefaultsButtonText = resetDefaultsButtonText,
            headerSlot = headerSlot,
            previewSlot = previewSlot,
            validateOnApply = validateOnApply,
            closeOnApply = closeOnApply,
            onApply = { _, map -> onApply?.invoke(descriptor.fromValueMap(map)) },
            onCancel = { onCancel?.invoke() }
        )

        inline fun <reified T : Any> forData(
            initialInstance: T? = null,
            layoutMode: SchemaLayoutMode = SchemaLayoutMode.STACKED,
            noinline labelTranslator: ((String) -> String)? = null,
            customRenderers: Map<String, SchemaCustomFieldRenderer> = emptyMap(),
            modalTitle: String? = null,
            applyButtonText: String = "Apply",
            cancelButtonText: String = "Cancel",
            resetDefaultsButtonText: String = "Reset Defaults",
            headerSlot: AbstractComponent? = null,
            previewSlot: AbstractComponent? = null,
            validateOnApply: Boolean = true,
            closeOnApply: Boolean = true,
            noinline onApply: ((T) -> Unit)? = null,
            noinline onCancel: (() -> Unit)? = null
        ): SchemaModal {
            val descriptor = SchemaRegistry.getForClass<T>()
                ?: throw IllegalStateException("No SchemaDescriptor registered for ${T::class.simpleName}.")
            return forData(
                descriptor = descriptor,
                initialInstance = initialInstance ?: descriptor.defaultInstance(),
                layoutMode = layoutMode,
                labelTranslator = labelTranslator,
                customRenderers = customRenderers,
                modalTitle = modalTitle ?: descriptor.schema.title,
                applyButtonText = applyButtonText,
                cancelButtonText = cancelButtonText,
                resetDefaultsButtonText = resetDefaultsButtonText,
                headerSlot = headerSlot,
                previewSlot = previewSlot,
                validateOnApply = validateOnApply,
                closeOnApply = closeOnApply,
                onApply = onApply,
                onCancel = onCancel
            )
        }
    }
}

package com.nexustools.genericui.ext.schema

/**
 * Composite meta-schema describing an object's fields, structural groups, separators, sections,
 * presets, and reactive cross-field rules.
 */
data class ObjectSchema(
    val id: String,
    val title: String = "",
    val description: String? = null,
    val nodes: List<SchemaNode> = emptyList(),
    val sections: List<SchemaSectionSpec> = emptyList(),
    val presets: List<SchemaPreset> = emptyList(),
    val reactiveRules: List<SchemaReactiveRule> = emptyList(),
    val version: Int = 1
) {
    val nodesByKey: Map<String, SchemaNode> by lazy {
        val map = LinkedHashMap<String, SchemaNode>()
        collectNodes(nodes, map)
        map
    }

    val fieldsByKey: Map<String, SchemaFieldSpec> by lazy {
        val map = LinkedHashMap<String, SchemaFieldSpec>()
        collectFields(nodes, map)
        map
    }

    fun resolveDefaults(): LinkedHashMap<String, Any?> = resolveDefaultsForNodes(nodes)

    fun validateValues(
        values: Map<String, Any?>,
        parentContext: SchemaEvalContext? = null
    ): SchemaValidationResult {
        val fieldErrors = LinkedHashMap<String, MutableList<String>>()
        val rootContext = SchemaEvalContext(values = values, parent = parentContext)
        validateNodes(nodes, values, rootContext, "", fieldErrors)
        return SchemaValidationResult(
            isValid = fieldErrors.isEmpty(),
            fieldErrors = fieldErrors
        )
    }

    private fun validateNodes(
        nodeList: List<SchemaNode>,
        scopeValues: Map<String, Any?>,
        context: SchemaEvalContext,
        pathPrefix: String,
        errorsOut: MutableMap<String, MutableList<String>>
    ) {
        for (node in nodeList) {
            if (node.visibleWhen?.evaluate(context) == false) continue
            val fullPath = if (pathPrefix.isEmpty()) node.key else "$pathPrefix.${node.key}"
            when (node) {
                is SchemaSeparatorSpec -> Unit
                is SchemaAsyncGroupSpec -> Unit
                is SchemaGroupSpec -> {
                    val childMap = (scopeValues[node.key] as? Map<*, *>)?.entries
                        ?.associate { (k, v) -> k.toString() to v }
                        ?: emptyMap()
                    validateNodes(node.children, childMap, context.child(childMap), fullPath, errorsOut)
                }
                is SchemaIndexedGroupSpec -> {
                    val slotList = SchemaValueCodec.coerceIndexedGroupValue(node, scopeValues[node.key])
                    val childSchema = ObjectSchema(id = "${node.key}_item", nodes = node.children)
                    slotList.forEachIndexed { idx, slotMap ->
                        val slotRes = childSchema.validateValues(slotMap, parentContext = context)
                        for ((k, msgs) in slotRes.fieldErrors) {
                            errorsOut.getOrPut("$fullPath[$idx].$k") { mutableListOf() }.addAll(msgs)
                        }
                    }
                }
                is SchemaFieldSpec -> {
                    val value = scopeValues[node.key]
                    val label = node.label.ifBlank { node.key }
                    val errs = mutableListOf<String>()
                    if (node.required) {
                        SchemaValidator.Required().validate(value, context, label)?.let { errs.add(it) }
                    }
                    when (node) {
                        is IntFieldSpec -> {
                            val minD = node.min?.toDouble()
                            val maxD = node.max?.toDouble()
                            if (minD != null || maxD != null) {
                                SchemaValidator.NumericRange(minD, maxD).validate(value, context, label)?.let { errs.add(it) }
                            }
                        }
                        is DoubleFieldSpec -> {
                            if (node.min != null || node.max != null) {
                                SchemaValidator.NumericRange(node.min, node.max).validate(value, context, label)?.let { errs.add(it) }
                            }
                        }
                        is CompoundNumberFieldSpec -> {
                            if (!node.tupleMode && (node.min != null || node.max != null)) {
                                SchemaValidator.NumericRange(node.min, node.max).validate(value, context, label)?.let { errs.add(it) }
                            }
                        }
                        is StringFieldSpec -> {
                            node.maxLength?.let { maxLen ->
                                SchemaValidator.MaxLength(maxLen).validate(value, context, label)?.let { errs.add(it) }
                            }
                        }
                        is ObjectListFieldSpec -> {
                            val items = SchemaValueCodec.coerceObjectListValue(node, value)
                            items.forEachIndexed { idx, itemMap ->
                                val itemRes = node.itemSchema.validateValues(itemMap, parentContext = context)
                                for ((k, msgs) in itemRes.fieldErrors) {
                                    errorsOut.getOrPut("$fullPath[$idx].$k") { mutableListOf() }.addAll(msgs)
                                }
                            }
                        }
                        else -> Unit
                    }
                    for (validator in node.validators) {
                        validator.validate(value, context, label)?.let { errs.add(it) }
                    }
                    if (errs.isNotEmpty()) {
                        errorsOut.getOrPut(fullPath) { mutableListOf() }.addAll(errs.distinct())
                    }
                }
            }
        }
    }

    companion object {
        internal fun resolveDefaultsForNodes(nodeList: List<SchemaNode>): LinkedHashMap<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            for (node in nodeList) {
                when (node) {
                    is SchemaSeparatorSpec -> Unit
                    is SchemaAsyncGroupSpec -> map[node.key] = linkedMapOf<String, Any?>()
                    is SchemaGroupSpec -> map[node.key] = resolveDefaultsForNodes(node.children)
                    is SchemaIndexedGroupSpec -> {
                        val list = List(node.count) { resolveDefaultsForNodes(node.children) }
                        map[node.key] = if (node.serializeAsJsonString) {
                            SchemaJsonCodec.toJson(list)
                        } else {
                            list
                        }
                    }
                    is SchemaFieldSpec -> {
                        map[node.key] = when (node) {
                            is IntFieldSpec -> if (node.isLong) node.defaultLongValue else node.defaultValue
                            is DoubleFieldSpec -> if (node.isFloat) node.defaultValue.toFloat() else node.defaultValue
                            is BooleanFieldSpec -> node.defaultValue
                            is OrderedMultiSelectFieldSpec -> if (node.serializeAsJsonString) {
                                SchemaJsonCodec.toJson(node.defaultValue)
                            } else {
                                node.defaultValue.toList()
                            }
                            is PolymorphicRowListFieldSpec -> if (node.serializeAsJsonString) {
                                SchemaJsonCodec.toJson(node.defaultValue)
                            } else {
                                node.defaultValue.map { it.toList() }
                            }
                            is ObjectListFieldSpec -> if (node.serializeAsJsonString) {
                                SchemaJsonCodec.toJson(node.defaultValue)
                            } else {
                                node.defaultValue.map { LinkedHashMap(it) }
                            }
                            is CubicBezierFieldSpec -> node.defaultValue.toList()
                            else -> node.defaultValue
                        }
                    }
                }
            }
            return map
        }

        private fun collectNodes(nodeList: List<SchemaNode>, out: MutableMap<String, SchemaNode>) {
            for (node in nodeList) {
                if (node !is SchemaSeparatorSpec && node.key !in out) {
                    out[node.key] = node
                }
                if (node is SchemaGroupSpec) {
                    collectNodes(node.children, out)
                }
            }
        }

        private fun collectFields(nodeList: List<SchemaNode>, out: MutableMap<String, SchemaFieldSpec>) {
            for (node in nodeList) {
                when (node) {
                    is SchemaFieldSpec -> if (node.key !in out) {
                        out[node.key] = node
                    }
                    is SchemaGroupSpec -> collectFields(node.children, out)
                    else -> Unit
                }
            }
        }
    }
}

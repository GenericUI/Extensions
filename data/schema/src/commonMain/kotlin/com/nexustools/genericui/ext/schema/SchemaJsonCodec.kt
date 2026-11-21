package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.style.color.RgbaColor

/**
 * Pure Kotlin Multiplatform JSON parser/writer plus JSON Schema (`draft-07`) generator
 * ([toJsonSchema]) and parser ([fromJsonSchema]) for [ObjectSchema].
 */
object SchemaJsonCodec {

    fun encodeValues(schema: ObjectSchema, values: Map<String, Any?>, pretty: Boolean = false): String {
        val coerced = SchemaValueCodec.coerceObjectValues(schema, values)
        return toJson(coerced, pretty = pretty)
    }

    fun decodeValues(schema: ObjectSchema, json: String): LinkedHashMap<String, Any?> {
        val parsed = parseJsonObject(json)
        return SchemaValueCodec.coerceObjectValues(schema, parsed)
    }

    fun toJson(value: Any?, pretty: Boolean = false): String {
        val sb = StringBuilder()
        writeJsonValue(sb, value, pretty = pretty, indent = 0)
        return sb.toString()
    }

    fun parseJsonObject(json: String): Map<String, Any?> {
        val parsed = parseJson(json)
        @Suppress("UNCHECKED_CAST")
        return (parsed as? Map<String, Any?>) ?: emptyMap()
    }

    fun parseJsonArray(json: String): List<Any?> {
        val parsed = parseJson(json)
        return (parsed as? List<*>) ?: emptyList()
    }

    fun parseJson(json: String): Any? {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return null
        return JsonReader(trimmed).readValue()
    }

    fun toJsonSchema(
        schema: ObjectSchema,
        includeUiMetadata: Boolean = true,
        pretty: Boolean = true
    ): String = toJson(toJsonSchemaMap(schema, includeUiMetadata), pretty = pretty)

    fun toJsonSchemaMap(
        schema: ObjectSchema,
        includeUiMetadata: Boolean = true
    ): LinkedHashMap<String, Any?> {
        val root = linkedMapOf<String, Any?>(
            "\$schema" to "http://json-schema.org/draft-07/schema#",
            "type" to "object",
            "title" to schema.title.ifBlank { schema.id }
        )
        if (!schema.description.isNullOrBlank()) {
            root["description"] = schema.description
        }
        if (includeUiMetadata) {
            root["x-genericui-id"] = schema.id
            root["x-genericui-version"] = schema.version
            if (schema.sections.isNotEmpty()) {
                root["x-genericui-sections"] = schema.sections.map { sec ->
                    linkedMapOf<String, Any?>(
                        "id" to sec.id,
                        "title" to sec.title
                    ).apply {
                        sec.description?.let { put("description", it) }
                        sec.icon?.let { put("icon", it) }
                    }
                }
            }
            if (schema.presets.isNotEmpty()) {
                root["x-genericui-presets"] = schema.presets.map { p ->
                    linkedMapOf<String, Any?>(
                        "id" to p.id,
                        "label" to p.label,
                        "values" to p.values
                    ).apply {
                        p.description?.let { put("description", it) }
                    }
                }
            }
        }
        val (props, required) = nodesToJsonSchemaProperties(schema.nodes, includeUiMetadata)
        root["properties"] = props
        if (required.isNotEmpty()) {
            root["required"] = required
        }
        root["additionalProperties"] = false
        return root
    }

    fun fromJsonSchema(json: String, id: String = ""): ObjectSchema {
        val root = parseJsonObject(json)
        return fromJsonSchemaMap(root, id = id)
    }

    fun fromJsonSchemaMap(root: Map<String, Any?>, id: String = ""): ObjectSchema {
        val resolvedId = id.ifBlank {
            root["x-genericui-id"]?.toString()?.takeIf { it.isNotBlank() }
                ?: root["title"]?.toString()?.takeIf { it.isNotBlank() }
                ?: "schema"
        }
        val title = root["title"]?.toString() ?: resolvedId
        val description = root["description"]?.toString()
        val version = (root["x-genericui-version"] as? Number)?.toInt() ?: 1

        val sections = ((root["x-genericui-sections"] as? List<*>) ?: emptyList<Any?>()).mapNotNull { entry ->
            val m = entry as? Map<*, *> ?: return@mapNotNull null
            val secId = m["id"]?.toString() ?: return@mapNotNull null
            SchemaSectionSpec(
                id = secId,
                title = m["title"]?.toString() ?: secId,
                description = m["description"]?.toString(),
                icon = m["icon"]?.toString()
            )
        }
        val presets = ((root["x-genericui-presets"] as? List<*>) ?: emptyList<Any?>()).mapNotNull { entry ->
            val m = entry as? Map<*, *> ?: return@mapNotNull null
            val pId = m["id"]?.toString() ?: return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            val vals = (m["values"] as? Map<String, Any?>) ?: emptyMap()
            SchemaPreset(
                id = pId,
                label = m["label"]?.toString() ?: pId,
                description = m["description"]?.toString(),
                values = vals
            )
        }

        val requiredSet = ((root["required"] as? List<*>) ?: emptyList<Any?>())
            .mapNotNull { it?.toString() }
            .toSet()

        val propsMap = (root["properties"] as? Map<*, *>) ?: emptyMap<Any?, Any?>()
        val nodes = propsMap.entries.mapNotNull { (rawKey, rawSpec) ->
            val key = rawKey?.toString() ?: return@mapNotNull null
            val specMap = rawSpec as? Map<*, *> ?: return@mapNotNull null
            jsonSchemaPropertyToNode(key, specMap, isRequired = key in requiredSet)
        }

        return ObjectSchema(
            id = resolvedId,
            title = title,
            description = description,
            nodes = nodes,
            sections = sections,
            presets = presets,
            version = version
        )
    }

    private fun nodesToJsonSchemaProperties(
        nodes: List<SchemaNode>,
        includeUiMetadata: Boolean
    ): Pair<LinkedHashMap<String, Any?>, List<String>> {
        val props = linkedMapOf<String, Any?>()
        val required = mutableListOf<String>()

        for (node in nodes) {
            when (node) {
                is SchemaSeparatorSpec -> Unit
                is SchemaAsyncGroupSpec -> {
                    props[node.key] = linkedMapOf<String, Any?>(
                        "type" to "object",
                        "title" to node.label.ifBlank { node.key }
                    )
                }
                is SchemaGroupSpec -> {
                    val (childProps, childReq) = nodesToJsonSchemaProperties(node.children, includeUiMetadata)
                    val groupObj = linkedMapOf<String, Any?>(
                        "type" to "object",
                        "title" to node.label.ifBlank { node.key },
                        "properties" to childProps,
                        "additionalProperties" to false
                    )
                    if (!node.description.isNullOrBlank()) groupObj["description"] = node.description
                    if (childReq.isNotEmpty()) groupObj["required"] = childReq
                    if (includeUiMetadata && node.sectionId != null) {
                        groupObj["x-genericui-section"] = node.sectionId
                    }
                    props[node.key] = groupObj
                }
                is SchemaIndexedGroupSpec -> {
                    val (childProps, childReq) = nodesToJsonSchemaProperties(node.children, includeUiMetadata)
                    val itemObj = linkedMapOf<String, Any?>(
                        "type" to "object",
                        "properties" to childProps,
                        "additionalProperties" to false
                    )
                    if (childReq.isNotEmpty()) itemObj["required"] = childReq
                    val arrObj = linkedMapOf<String, Any?>(
                        "type" to "array",
                        "title" to node.label.ifBlank { node.key },
                        "items" to itemObj,
                        "minItems" to node.count,
                        "maxItems" to node.count
                    )
                    if (!node.description.isNullOrBlank()) arrObj["description"] = node.description
                    if (includeUiMetadata) {
                        arrObj["x-genericui-kind"] = "indexedGroup"
                        arrObj["x-genericui-indexPrefix"] = node.indexLabelPrefix
                        if (node.serializeAsJsonString) arrObj["x-genericui-serializeAsJsonString"] = true
                        node.sectionId?.let { arrObj["x-genericui-section"] = it }
                    }
                    props[node.key] = arrObj
                }
                is SchemaFieldSpec -> {
                    if (node.required) required.add(node.key)
                    props[node.key] = fieldToJsonSchemaMap(node, includeUiMetadata)
                }
            }
        }
        return props to required
    }

    private fun fieldToJsonSchemaMap(
        field: SchemaFieldSpec,
        includeUiMetadata: Boolean
    ): LinkedHashMap<String, Any?> {
        val m = linkedMapOf<String, Any?>()
        if (field.label.isNotBlank()) m["title"] = field.label
        if (!field.description.isNullOrBlank()) m["description"] = field.description

        when (field) {
            is BooleanFieldSpec -> {
                m["type"] = "boolean"
                m["default"] = field.defaultValue
                if (includeUiMetadata && field.wireZeroOneStrings) {
                    m["x-genericui-wireZeroOne"] = true
                    m["x-genericui-falseLabel"] = field.falseLabel
                    m["x-genericui-trueLabel"] = field.trueLabel
                }
            }
            is IntFieldSpec -> {
                m["type"] = "integer"
                m["default"] = if (field.isLong) field.defaultLongValue else field.defaultValue
                field.min?.let { m["minimum"] = it }
                field.max?.let { m["maximum"] = it }
                if (field.step != 1) m["multipleOf"] = field.step
                if (includeUiMetadata) {
                    if (field.isLong) m["x-genericui-long"] = true
                    field.unit?.let { m["x-genericui-unit"] = it }
                    if (field.quickChips.isNotEmpty()) m["x-genericui-quickChips"] = field.quickChips
                }
            }
            is DoubleFieldSpec -> {
                m["type"] = "number"
                m["default"] = field.defaultValue
                field.min?.let { m["minimum"] = it }
                field.max?.let { m["maximum"] = it }
                if (field.step > 0.0) m["multipleOf"] = field.step
                if (includeUiMetadata) {
                    if (field.isFloat) m["x-genericui-float"] = true
                    field.precision?.let { m["x-genericui-precision"] = it }
                    field.unit?.let { m["x-genericui-unit"] = it }
                    if (field.quickChips.isNotEmpty()) m["x-genericui-quickChips"] = field.quickChips
                }
            }
            is CompoundNumberFieldSpec -> {
                if (field.tupleMode) {
                    m["type"] = "array"
                    m["items"] = linkedMapOf("type" to "number")
                    m["minItems"] = field.unitLabels.size
                    m["maxItems"] = field.unitLabels.size
                    m["default"] = field.defaultValue
                } else {
                    m["type"] = "number"
                    m["default"] = field.defaultValue
                    field.min?.let { m["minimum"] = it }
                    field.max?.let { m["maximum"] = it }
                }
                if (includeUiMetadata) {
                    m["x-genericui-kind"] = "compoundNumber"
                    m["x-genericui-unitLabels"] = field.unitLabels
                    if (field.radices.isNotEmpty()) m["x-genericui-radices"] = field.radices
                    m["x-genericui-radixStep"] = field.radixStep
                    m["x-genericui-tupleMode"] = field.tupleMode
                }
            }
            is StringFieldSpec -> {
                m["type"] = "string"
                m["default"] = field.defaultValue
                field.maxLength?.let { m["maxLength"] = it }
                if (field.secret || field.widgetHint == SchemaWidgetHint.SECRET) {
                    m["format"] = "password"
                }
                if (includeUiMetadata) {
                    if (field.multiline) m["x-genericui-multiline"] = true
                    if (field.suggestions.isNotEmpty()) m["x-genericui-suggestions"] = field.suggestions
                }
            }
            is ColorFieldSpec -> {
                m["type"] = "string"
                m["format"] = "color"
                m["pattern"] = "^#([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$"
                m["default"] = field.defaultValue
                if (includeUiMetadata) {
                    m["x-genericui-allowAlpha"] = field.allowAlpha
                    if (field.quickChips.isNotEmpty()) m["x-genericui-quickChips"] = field.quickChips
                }
            }
            is SelectFieldSpec -> {
                m["type"] = "string"
                if (field.defaultValue != null) m["default"] = field.defaultValue
                if (field.staticOptions.isNotEmpty()) {
                    m["enum"] = field.staticOptions.map { it.value }
                    if (includeUiMetadata) {
                        m["x-genericui-optionLabels"] = field.staticOptions.associate { it.value to it.label }
                    }
                }
                if (includeUiMetadata) {
                    if (field.searchable) m["x-genericui-searchable"] = true
                    if (field.allowUnlistedSavedValue) m["x-genericui-allowUnlistedSavedValue"] = true
                }
            }
            is OrderedMultiSelectFieldSpec -> {
                m["type"] = "array"
                val itemSchema = linkedMapOf<String, Any?>("type" to "string")
                if (field.staticOptions.isNotEmpty()) {
                    itemSchema["enum"] = field.staticOptions.map { it.value }
                }
                m["items"] = itemSchema
                m["uniqueItems"] = !field.allowDuplicates
                field.maxItems?.let { m["maxItems"] = it }
                m["default"] = field.defaultValue
                if (includeUiMetadata) {
                    m["x-genericui-kind"] = "orderedMultiSelect"
                    if (field.staticOptions.isNotEmpty()) {
                        m["x-genericui-optionLabels"] = field.staticOptions.associate { it.value to it.label }
                    }
                    m["x-genericui-allowDuplicates"] = field.allowDuplicates
                    m["x-genericui-allowDeletion"] = field.allowDeletion
                    if (field.serializeAsJsonString) m["x-genericui-serializeAsJsonString"] = true
                }
            }
            is PolymorphicRowListFieldSpec -> {
                m["type"] = "array"
                m["items"] = linkedMapOf(
                    "type" to "array",
                    "items" to linkedMapOf("type" to listOf("string", "null"))
                )
                field.maxRows?.let { m["maxItems"] = it }
                m["default"] = field.defaultValue
                if (includeUiMetadata) {
                    m["x-genericui-kind"] = "polymorphicRowList"
                    if (field.serializeAsJsonString) m["x-genericui-serializeAsJsonString"] = true
                }
            }
            is CubicBezierFieldSpec -> {
                m["type"] = "array"
                m["items"] = linkedMapOf("type" to "number")
                m["minItems"] = 4
                m["maxItems"] = 4
                m["default"] = field.defaultValue
                if (includeUiMetadata) {
                    m["x-genericui-kind"] = "cubicBezier"
                }
            }
            is ObjectListFieldSpec -> {
                m["type"] = "array"
                val (childProps, childReq) = nodesToJsonSchemaProperties(field.itemSchema.nodes, includeUiMetadata)
                val itemObj = linkedMapOf<String, Any?>(
                    "type" to "object",
                    "properties" to childProps,
                    "additionalProperties" to false
                )
                if (childReq.isNotEmpty()) itemObj["required"] = childReq
                m["items"] = itemObj
                field.maxItems?.let { m["maxItems"] = it }
                m["default"] = field.defaultValue
                if (includeUiMetadata) {
                    m["x-genericui-kind"] = "objectList"
                }
            }
            is CustomFieldSpec -> {
                m["default"] = field.defaultValue
                if (includeUiMetadata) {
                    m["x-genericui-kind"] = "custom"
                    m["x-genericui-renderer"] = field.rendererId
                }
            }
        }

        if (includeUiMetadata) {
            if (field.widgetHint != SchemaWidgetHint.AUTO) {
                m["x-genericui-widget"] = field.widgetHint.name
            }
            field.sectionId?.let { m["x-genericui-section"] = it }
            field.placeholder?.let { m["x-genericui-placeholder"] = it }
            if (field.fullWidth) m["x-genericui-fullWidth"] = true
            if (field.legacyKeys.isNotEmpty()) m["x-genericui-legacyKeys"] = field.legacyKeys
        }
        return m
    }

    private fun jsonSchemaPropertyToNode(
        key: String,
        m: Map<*, *>,
        isRequired: Boolean
    ): SchemaNode {
        val label = m["title"]?.toString().orEmpty()
        val description = m["description"]?.toString()
        val sectionId = m["x-genericui-section"]?.toString()
        val placeholder = m["x-genericui-placeholder"]?.toString()
        val fullWidth = m["x-genericui-fullWidth"] as? Boolean ?: false
        val legacyKeys = ((m["x-genericui-legacyKeys"] as? List<*>) ?: emptyList<Any?>()).mapNotNull { it?.toString() }
        val widgetHint = (m["x-genericui-widget"]?.toString())?.let { name ->
            SchemaWidgetHint.entries.firstOrNull { it.name == name }
        } ?: SchemaWidgetHint.AUTO

        val customKind = m["x-genericui-kind"]?.toString()
        val rawType = when (val t = m["type"]) {
            is String -> t
            is List<*> -> t.firstOrNull { it != "null" }?.toString() ?: "string"
            else -> "string"
        }

        if (customKind == "compoundNumber") {
            val unitLabels = ((m["x-genericui-unitLabels"] as? List<*>) ?: emptyList<Any?>()).mapNotNull { it?.toString() }
            val radices = ((m["x-genericui-radices"] as? List<*>) ?: emptyList<Any?>()).mapNotNull { (it as? Number)?.toDouble() }
            val radixStep = (m["x-genericui-radixStep"] as? Number)?.toDouble() ?: 10.0
            val tupleMode = m["x-genericui-tupleMode"] as? Boolean ?: (rawType == "array")
            val defVal = m["default"] ?: if (tupleMode) List(unitLabels.size) { 0.0 } else 0.0
            return CompoundNumberFieldSpec(
                key = key,
                label = label,
                description = description,
                unitLabels = unitLabels.ifEmpty { listOf("value") },
                radices = radices,
                radixStep = radixStep,
                tupleMode = tupleMode,
                defaultValue = defVal,
                min = (m["minimum"] as? Number)?.toDouble(),
                max = (m["maximum"] as? Number)?.toDouble(),
                sectionId = sectionId,
                fullWidth = fullWidth,
                required = isRequired,
                legacyKeys = legacyKeys
            )
        }
        if (customKind == "indexedGroup") {
            val itemsMap = m["items"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            val childProps = (itemsMap["properties"] as? Map<*, *>) ?: emptyMap<Any?, Any?>()
            val childReq = ((itemsMap["required"] as? List<*>) ?: emptyList<Any?>()).mapNotNull { it?.toString() }.toSet()
            val children = childProps.entries.mapNotNull { (ck, cv) ->
                val ckStr = ck?.toString() ?: return@mapNotNull null
                val cvMap = cv as? Map<*, *> ?: return@mapNotNull null
                jsonSchemaPropertyToNode(ckStr, cvMap, isRequired = ckStr in childReq)
            }
            val count = (m["maxItems"] as? Number)?.toInt() ?: (m["minItems"] as? Number)?.toInt() ?: 1
            return SchemaIndexedGroupSpec(
                key = key,
                label = label,
                description = description,
                count = count.coerceAtLeast(1),
                indexLabelPrefix = m["x-genericui-indexPrefix"]?.toString() ?: "Level",
                children = children,
                serializeAsJsonString = m["x-genericui-serializeAsJsonString"] as? Boolean ?: false,
                sectionId = sectionId
            )
        }
        if (customKind == "cubicBezier") {
            val defFloats = ((m["default"] as? List<*>) ?: listOf(0.25f, 0.1f, 0.25f, 1.0f))
                .mapNotNull { (it as? Number)?.toFloat() }
                .let { if (it.size == 4) it else listOf(0.25f, 0.1f, 0.25f, 1.0f) }
            return CubicBezierFieldSpec(
                key = key,
                label = label,
                description = description,
                defaultValue = defFloats,
                sectionId = sectionId,
                fullWidth = true,
                required = isRequired,
                legacyKeys = legacyKeys
            )
        }
        if (customKind == "custom") {
            return CustomFieldSpec(
                key = key,
                label = label,
                description = description,
                rendererId = m["x-genericui-renderer"]?.toString().orEmpty(),
                defaultValue = m["default"],
                sectionId = sectionId,
                fullWidth = fullWidth,
                required = isRequired,
                legacyKeys = legacyKeys
            )
        }

        return when (rawType) {
            "boolean" -> BooleanFieldSpec(
                key = key,
                label = label,
                description = description,
                defaultValue = (m["default"] as? Boolean) ?: false,
                widgetHint = if (widgetHint != SchemaWidgetHint.AUTO) widgetHint else SchemaWidgetHint.SWITCH,
                falseLabel = m["x-genericui-falseLabel"]?.toString() ?: "No",
                trueLabel = m["x-genericui-trueLabel"]?.toString() ?: "Yes",
                wireZeroOneStrings = m["x-genericui-wireZeroOne"] as? Boolean ?: false,
                sectionId = sectionId,
                fullWidth = fullWidth,
                required = isRequired,
                legacyKeys = legacyKeys
            )
            "integer" -> IntFieldSpec(
                key = key,
                label = label,
                description = description,
                defaultValue = (m["default"] as? Number)?.toInt() ?: 0,
                min = (m["minimum"] as? Number)?.toInt(),
                max = (m["maximum"] as? Number)?.toInt(),
                step = (m["multipleOf"] as? Number)?.toInt() ?: 1,
                unit = m["x-genericui-unit"]?.toString(),
                quickChips = ((m["x-genericui-quickChips"] as? List<*>) ?: emptyList<Any?>()).mapNotNull { (it as? Number)?.toInt() },
                isLong = m["x-genericui-long"] as? Boolean ?: false,
                defaultLongValue = (m["default"] as? Number)?.toLong() ?: 0L,
                widgetHint = widgetHint,
                placeholder = placeholder,
                sectionId = sectionId,
                fullWidth = fullWidth,
                required = isRequired,
                legacyKeys = legacyKeys
            )
            "number" -> DoubleFieldSpec(
                key = key,
                label = label,
                description = description,
                defaultValue = (m["default"] as? Number)?.toDouble() ?: 0.0,
                min = (m["minimum"] as? Number)?.toDouble(),
                max = (m["maximum"] as? Number)?.toDouble(),
                step = (m["multipleOf"] as? Number)?.toDouble() ?: 0.1,
                precision = (m["x-genericui-precision"] as? Number)?.toInt(),
                unit = m["x-genericui-unit"]?.toString(),
                quickChips = ((m["x-genericui-quickChips"] as? List<*>) ?: emptyList<Any?>()).mapNotNull { (it as? Number)?.toDouble() },
                isFloat = m["x-genericui-float"] as? Boolean ?: false,
                widgetHint = widgetHint,
                placeholder = placeholder,
                sectionId = sectionId,
                fullWidth = fullWidth,
                required = isRequired,
                legacyKeys = legacyKeys
            )
            "object" -> {
                val childProps = (m["properties"] as? Map<*, *>) ?: emptyMap<Any?, Any?>()
                val childReq = ((m["required"] as? List<*>) ?: emptyList<Any?>()).mapNotNull { it?.toString() }.toSet()
                val children = childProps.entries.mapNotNull { (ck, cv) ->
                    val ckStr = ck?.toString() ?: return@mapNotNull null
                    val cvMap = cv as? Map<*, *> ?: return@mapNotNull null
                    jsonSchemaPropertyToNode(ckStr, cvMap, isRequired = ckStr in childReq)
                }
                SchemaGroupSpec(
                    key = key,
                    label = label,
                    description = description,
                    children = children,
                    sectionId = sectionId
                )
            }
            "array" -> {
                val itemsMap = m["items"] as? Map<*, *>
                val itemsType = itemsMap?.get("type")?.toString()
                val minItems = (m["minItems"] as? Number)?.toInt()
                val maxItems = (m["maxItems"] as? Number)?.toInt()
                when {
                    itemsType == "number" && minItems == 4 && maxItems == 4 -> {
                        val defFloats = ((m["default"] as? List<*>) ?: listOf(0.25f, 0.1f, 0.25f, 1.0f))
                            .mapNotNull { (it as? Number)?.toFloat() }
                            .let { if (it.size == 4) it else listOf(0.25f, 0.1f, 0.25f, 1.0f) }
                        CubicBezierFieldSpec(
                            key = key,
                            label = label,
                            description = description,
                            defaultValue = defFloats,
                            sectionId = sectionId,
                            fullWidth = true,
                            required = isRequired,
                            legacyKeys = legacyKeys
                        )
                    }
                    itemsType == "object" -> {
                        val itemSchema = fromJsonSchemaMap(SchemaValueCodec.coerceToMap(itemsMap), id = "${key}_item")
                        ObjectListFieldSpec(
                            key = key,
                            label = label,
                            description = description,
                            itemSchema = itemSchema,
                            maxItems = maxItems,
                            sectionId = sectionId,
                            fullWidth = true,
                            required = isRequired,
                            legacyKeys = legacyKeys
                        )
                    }
                    customKind == "polymorphicRowList" || itemsType == "array" -> {
                        PolymorphicRowListFieldSpec(
                            key = key,
                            label = label,
                            description = description,
                            variants = listOf(SchemaRowVariantSpec("default", "Row", emptyList())),
                            maxRows = maxItems,
                            serializeAsJsonString = m["x-genericui-serializeAsJsonString"] as? Boolean ?: false,
                            sectionId = sectionId,
                            fullWidth = true,
                            required = isRequired,
                            legacyKeys = legacyKeys
                        )
                    }
                    else -> {
                        val optionLabels = (m["x-genericui-optionLabels"] as? Map<*, *>).orEmpty()
                        val enumVals = ((itemsMap?.get("enum") as? List<*>) ?: emptyList<Any?>()).mapNotNull { it?.toString() }
                        val opts = enumVals.map { v -> SchemaOption(v, optionLabels[v]?.toString() ?: v) }
                        val defList = ((m["default"] as? List<*>) ?: emptyList<Any?>()).mapNotNull { it?.toString() }
                        OrderedMultiSelectFieldSpec(
                            key = key,
                            label = label,
                            description = description,
                            defaultValue = defList,
                            staticOptions = opts,
                            allowDuplicates = m["x-genericui-allowDuplicates"] as? Boolean ?: !(m["uniqueItems"] as? Boolean ?: false),
                            allowDeletion = m["x-genericui-allowDeletion"] as? Boolean ?: true,
                            maxItems = maxItems,
                            serializeAsJsonString = m["x-genericui-serializeAsJsonString"] as? Boolean ?: false,
                            sectionId = sectionId,
                            fullWidth = true,
                            required = isRequired,
                            legacyKeys = legacyKeys
                        )
                    }
                }
            }
            else -> {
                val enumList = (m["enum"] as? List<*>)?.mapNotNull { it?.toString() }
                val format = m["format"]?.toString()
                when {
                    enumList != null -> {
                        val optionLabels = (m["x-genericui-optionLabels"] as? Map<*, *>).orEmpty()
                        val opts = enumList.map { v -> SchemaOption(v, optionLabels[v]?.toString() ?: v) }
                        SelectFieldSpec(
                            key = key,
                            label = label,
                            description = description,
                            defaultValue = m["default"]?.toString() ?: opts.firstOrNull()?.value,
                            staticOptions = opts,
                            searchable = m["x-genericui-searchable"] as? Boolean ?: false,
                            allowUnlistedSavedValue = m["x-genericui-allowUnlistedSavedValue"] as? Boolean ?: false,
                            placeholder = placeholder,
                            sectionId = sectionId,
                            fullWidth = fullWidth,
                            required = isRequired,
                            legacyKeys = legacyKeys
                        )
                    }
                    format == "color" || widgetHint == SchemaWidgetHint.COLOR -> {
                        ColorFieldSpec(
                            key = key,
                            label = label,
                            description = description,
                            defaultValue = m["default"]?.toString() ?: "#000000",
                            allowAlpha = m["x-genericui-allowAlpha"] as? Boolean ?: true,
                            quickChips = ((m["x-genericui-quickChips"] as? List<*>) ?: emptyList<Any?>()).mapNotNull { it?.toString() },
                            sectionId = sectionId,
                            fullWidth = fullWidth,
                            required = isRequired,
                            legacyKeys = legacyKeys
                        )
                    }
                    else -> {
                        val isSecret = format == "password" || widgetHint == SchemaWidgetHint.SECRET
                        val isMultiline = (m["x-genericui-multiline"] as? Boolean ?: false) || widgetHint == SchemaWidgetHint.MULTILINE
                        StringFieldSpec(
                            key = key,
                            label = label,
                            description = description,
                            defaultValue = m["default"]?.toString() ?: "",
                            placeholder = placeholder,
                            maxLength = (m["maxLength"] as? Number)?.toInt(),
                            multiline = isMultiline,
                            secret = isSecret,
                            suggestions = ((m["x-genericui-suggestions"] as? List<*>) ?: emptyList<Any?>()).mapNotNull { it?.toString() },
                            widgetHint = widgetHint,
                            sectionId = sectionId,
                            fullWidth = fullWidth,
                            required = isRequired,
                            legacyKeys = legacyKeys
                        )
                    }
                }
            }
        }
    }

    private fun writeJsonValue(sb: StringBuilder, value: Any?, pretty: Boolean, indent: Int) {
        when (value) {
            null -> sb.append("null")
            is Boolean -> sb.append(value)
            is Int, is Long, is Short, is Byte -> sb.append(value.toString())
            is Float -> {
                if (value.isFinite()) sb.append(DoubleFieldSpec.formatDouble(value.toDouble())) else sb.append("null")
            }
            is Double -> {
                if (value.isFinite()) sb.append(DoubleFieldSpec.formatDouble(value)) else sb.append("null")
            }
            is Number -> sb.append(value.toString())
            is RgbaColor -> appendEscapedString(sb, value.toHex())
            is Enum<*> -> appendEscapedString(sb, value.name)
            is CharSequence -> appendEscapedString(sb, value.toString())
            is Map<*, *> -> {
                if (value.isEmpty()) {
                    sb.append("{}")
                    return
                }
                sb.append('{')
                val entries = value.entries.toList()
                entries.forEachIndexed { idx, (k, v) ->
                    if (pretty) {
                        sb.append('\n')
                        appendIndent(sb, indent + 2)
                    }
                    appendEscapedString(sb, k?.toString() ?: "")
                    sb.append(if (pretty) ": " else ":")
                    writeJsonValue(sb, v, pretty, indent + 2)
                    if (idx < entries.lastIndex) sb.append(',')
                }
                if (pretty) {
                    sb.append('\n')
                    appendIndent(sb, indent)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                val list = value.toList()
                if (list.isEmpty()) {
                    sb.append("[]")
                    return
                }
                sb.append('[')
                list.forEachIndexed { idx, item ->
                    if (pretty) {
                        sb.append('\n')
                        appendIndent(sb, indent + 2)
                    }
                    writeJsonValue(sb, item, pretty, indent + 2)
                    if (idx < list.lastIndex) sb.append(',')
                }
                if (pretty) {
                    sb.append('\n')
                    appendIndent(sb, indent)
                }
                sb.append(']')
            }
            is Array<*> -> writeJsonValue(sb, value.toList(), pretty, indent)
            else -> appendEscapedString(sb, value.toString())
        }
    }

    private fun appendIndent(sb: StringBuilder, spaces: Int) {
        repeat(spaces) { sb.append(' ') }
    }

    private fun appendEscapedString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch.code < 0x20) {
                    sb.append("\\u")
                    sb.append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    sb.append(ch)
                }
            }
        }
        sb.append('"')
    }

    private class JsonReader(private val input: String) {
        private var pos = 0

        fun readValue(): Any? {
            skipWhitespace()
            if (pos >= input.length) error("Unexpected end of JSON input")
            return when (val ch = input[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't', 'f' -> readBoolean()
                'n' -> readNull()
                '-', in '0'..'9' -> readNumber()
                else -> error("Unexpected character '$ch' at JSON index $pos")
            }
        }

        private fun readObject(): Map<String, Any?> {
            pos++ // '{'
            skipWhitespace()
            val map = linkedMapOf<String, Any?>()
            if (pos < input.length && input[pos] == '}') {
                pos++
                return map
            }
            while (pos < input.length) {
                skipWhitespace()
                if (pos >= input.length || input[pos] != '"') error("Expected string key at JSON index $pos")
                val key = readString()
                skipWhitespace()
                if (pos >= input.length || input[pos] != ':') error("Expected ':' after key '$key' at JSON index $pos")
                pos++
                map[key] = readValue()
                skipWhitespace()
                if (pos < input.length && input[pos] == ',') {
                    pos++
                    continue
                }
                if (pos < input.length && input[pos] == '}') {
                    pos++
                    return map
                }
                error("Expected ',' or '}' in JSON object at index $pos")
            }
            error("Unterminated JSON object")
        }

        private fun readArray(): List<Any?> {
            pos++ // '['
            skipWhitespace()
            val list = mutableListOf<Any?>()
            if (pos < input.length && input[pos] == ']') {
                pos++
                return list
            }
            while (pos < input.length) {
                list.add(readValue())
                skipWhitespace()
                if (pos < input.length && input[pos] == ',') {
                    pos++
                    continue
                }
                if (pos < input.length && input[pos] == ']') {
                    pos++
                    return list
                }
                error("Expected ',' or ']' in JSON array at index $pos")
            }
            error("Unterminated JSON array")
        }

        private fun readString(): String {
            pos++ // '"'
            val sb = StringBuilder()
            while (pos < input.length) {
                val ch = input[pos++]
                if (ch == '"') return sb.toString()
                if (ch == '\\') {
                    if (pos >= input.length) error("Unterminated escape sequence in JSON string")
                    when (val esc = input[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > input.length) error("Incomplete unicode escape at JSON index $pos")
                            val code = input.substring(pos, pos + 4).toInt(16)
                            sb.append(code.toChar())
                            pos += 4
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(ch)
                }
            }
            error("Unterminated JSON string")
        }

        private fun readBoolean(): Boolean {
            return if (input.startsWith("true", pos)) {
                pos += 4
                true
            } else if (input.startsWith("false", pos)) {
                pos += 5
                false
            } else {
                error("Invalid boolean token at JSON index $pos")
            }
        }

        private fun readNull(): Any? {
            if (input.startsWith("null", pos)) {
                pos += 4
                return null
            }
            error("Invalid null token at JSON index $pos")
        }

        private fun readNumber(): Number {
            val start = pos
            if (input[pos] == '-') pos++
            var isFloat = false
            while (pos < input.length && input[pos] in '0'..'9') pos++
            if (pos < input.length && input[pos] == '.') {
                isFloat = true
                pos++
                while (pos < input.length && input[pos] in '0'..'9') pos++
            }
            if (pos < input.length && (input[pos] == 'e' || input[pos] == 'E')) {
                isFloat = true
                pos++
                if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) pos++
                while (pos < input.length && input[pos] in '0'..'9') pos++
            }
            val token = input.substring(start, pos)
            if (!isFloat) {
                token.toIntOrNull()?.let { return it }
                token.toLongOrNull()?.let { return it }
            }
            return token.toDouble()
        }

        private fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) {
                pos++
            }
        }
    }
}

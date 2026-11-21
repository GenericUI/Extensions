package com.nexustools.genericui.ext.schema.dsl

import com.nexustools.genericui.ext.schema.ObjectSchema

fun objectSchema(
    id: String,
    title: String = "",
    description: String? = null,
    version: Int = 1,
    block: ObjectSchemaBuilder.() -> Unit
): ObjectSchema = ObjectSchemaBuilder(
    id = id,
    title = title,
    description = description,
    version = version
).apply(block).build()

fun buildObjectSchema(
    id: String,
    title: String = "",
    description: String? = null,
    version: Int = 1,
    block: ObjectSchemaBuilder.() -> Unit
): ObjectSchema = objectSchema(
    id = id,
    title = title,
    description = description,
    version = version,
    block = block
)

package com.nexustools.genericui.ext.aichat

/**
 * Declarative schema definition for a tool/function exposed to an AI model.
 */
data class AiToolDefinition(
    val name: String,
    val description: String,
    val parametersJsonSchema: String = """{"type":"object","properties":{}}""",
    val strict: Boolean = false,
    val requiresConfirmation: Boolean = false
)

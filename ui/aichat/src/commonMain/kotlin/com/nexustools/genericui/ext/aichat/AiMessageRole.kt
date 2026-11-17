package com.nexustools.genericui.ext.aichat

/**
 * Role of a participant message in an [AiChatSession].
 */
enum class AiMessageRole(val wireName: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool");

    companion object {
        fun fromWireName(wireName: String): AiMessageRole {
            val clean = wireName.trim().lowercase()
            return entries.firstOrNull { it.wireName == clean } ?: USER
        }
    }
}

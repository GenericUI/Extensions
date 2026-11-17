package com.nexustools.genericui.ext.aichat

/**
 * Multimodal attachment (image, document, audio, or code file) associated with an [AiChatMessage].
 */
data class AiAttachment(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val dataBytes: ByteArray? = null,
    val base64Data: String? = null,
    val uriOrPath: String? = null
) {
    val isImage: Boolean
        get() = mimeType.startsWith("image/", ignoreCase = true)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AiAttachment) return false
        if (id != other.id) return false
        if (fileName != other.fileName) return false
        if (mimeType != other.mimeType) return false
        if (base64Data != other.base64Data) return false
        if (uriOrPath != other.uriOrPath) return false
        if (dataBytes != null) {
            if (other.dataBytes == null) return false
            if (!dataBytes.contentEquals(other.dataBytes)) return false
        } else if (other.dataBytes != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + (dataBytes?.contentHashCode() ?: 0)
        result = 31 * result + (base64Data?.hashCode() ?: 0)
        result = 31 * result + (uriOrPath?.hashCode() ?: 0)
        return result
    }
}

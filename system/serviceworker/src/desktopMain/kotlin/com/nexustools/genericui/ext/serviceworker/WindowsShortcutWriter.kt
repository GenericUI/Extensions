package com.nexustools.genericui.ext.serviceworker

/**
 * Deterministic MS-SHLLINK (`.lnk`) binary writer creating valid Windows Start Menu Startup
 * shortcuts pointing at desktop targets with zero external scripting subprocesses.
 */
internal object WindowsShortcutWriter {

    fun buildLnkBytes(
        targetPath: String,
        arguments: String = "",
        workingDirectory: String = "",
        description: String = ""
    ): ByteArray {
        val out = ArrayList<Byte>(256)
        // HeaderSize = 76 (0x4C)
        appendIntLE(out, 0x0000004C)
        // LinkCLSID = {00021401-0000-0000-C000-000000000046}
        val clsid = byteArrayOf(
            0x01, 0x14, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00,
            0xC0.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x46
        )
        for (b in clsid) out.add(b)

        val hasName = description.isNotEmpty()
        val hasWorkingDir = workingDirectory.isNotEmpty()
        val hasArguments = arguments.isNotEmpty()
        var flags = 0x08 or 0x80 // HasRelativePath | IsUnicode
        if (hasName) flags = flags or 0x04
        if (hasWorkingDir) flags = flags or 0x10
        if (hasArguments) flags = flags or 0x20
        appendIntLE(out, flags)

        // FileAttributes (0), CreationTime (8B), AccessTime (8B), WriteTime (8B), FileSize (4B), IconIndex (4B)
        appendIntLE(out, 0)
        appendLongLE(out, 0L)
        appendLongLE(out, 0L)
        appendLongLE(out, 0L)
        appendIntLE(out, 0)
        appendIntLE(out, 0)
        // ShowCommand = SW_SHOWNORMAL (1)
        appendIntLE(out, 1)
        // HotKey (2B) + Reserved1 (2B) + Reserved2 (4B) + Reserved3 (4B)
        appendShortLE(out, 0)
        appendShortLE(out, 0)
        appendIntLE(out, 0)
        appendIntLE(out, 0)

        if (hasName) appendCountedUtf16LE(out, description)
        appendCountedUtf16LE(out, targetPath)
        if (hasWorkingDir) appendCountedUtf16LE(out, workingDirectory)
        if (hasArguments) appendCountedUtf16LE(out, arguments)

        return out.toByteArray()
    }

    private fun appendShortLE(out: MutableList<Byte>, value: Int) {
        out.add((value and 0xFF).toByte())
        out.add(((value ushr 8) and 0xFF).toByte())
    }

    private fun appendIntLE(out: MutableList<Byte>, value: Int) {
        out.add((value and 0xFF).toByte())
        out.add(((value ushr 8) and 0xFF).toByte())
        out.add(((value ushr 16) and 0xFF).toByte())
        out.add(((value ushr 24) and 0xFF).toByte())
    }

    private fun appendLongLE(out: MutableList<Byte>, value: Long) {
        for (i in 0..7) {
            out.add(((value ushr (i * 8)) and 0xFFL).toByte())
        }
    }

    private fun appendCountedUtf16LE(out: MutableList<Byte>, text: String) {
        appendShortLE(out, text.length)
        for (ch in text) {
            val code = ch.code
            out.add((code and 0xFF).toByte())
            out.add(((code ushr 8) and 0xFF).toByte())
        }
    }
}

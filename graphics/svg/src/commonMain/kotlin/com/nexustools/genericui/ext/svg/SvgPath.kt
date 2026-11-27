package com.nexustools.genericui.ext.svg

/**
 * SVG `<path>` vector element keeping `d` path string and parsed [SvgPathCommand] list synchronized.
 */
class SvgPath(d: String = "") : SvgElement("path") {

    private var cachedTolerance: Float = -1f
    private var cachedSubpaths: List<SvgPathParser.Subpath>? = null

    private var suppressDSetter = false

    var d: String = d
        set(value) {
            if (field == value) return
            field = value
            if (!suppressDSetter) {
                backingCommands = SvgPathParser.parseCommands(value)
                cachedSubpaths = null
                notifyMutated()
            }
        }

    private var backingCommands: List<SvgPathCommand> = SvgPathParser.parseCommands(d)

    var commands: List<SvgPathCommand>
        get() = backingCommands
        set(value) {
            val copy = value.toList()
            backingCommands = copy
            suppressDSetter = true
            try {
                d = SvgPathParser.formatCommands(copy)
            } finally {
                suppressDSetter = false
            }
            cachedSubpaths = null
            notifyMutated()
        }

    internal fun resolveSubpaths(tolerance: Float = 0.25f): List<SvgPathParser.Subpath> {
        val existing = cachedSubpaths
        if (existing != null && kotlin.math.abs(cachedTolerance - tolerance) <= tolerance * 0.25f) {
            return existing
        }
        return SvgPathParser.flattenSubpaths(backingCommands, tolerance).also {
            cachedSubpaths = it
            cachedTolerance = tolerance
        }
    }

    override fun cloneElement(): SvgPath = SvgPath(d).also {
        copyCommonPropertiesTo(it)
    }
}

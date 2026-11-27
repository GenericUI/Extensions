package com.nexustools.genericui.ext.svg

/**
 * Common mutable container contract shared by [Svg], [SvgGroup], [SvgDefs], and [SvgClipPath].
 */
interface SvgContainer {
    val children: List<SvgElement>
    fun addChild(element: SvgElement)
    fun insertChild(index: Int, element: SvgElement)
    fun removeChild(element: SvgElement): Boolean
    fun clearChildren()
}

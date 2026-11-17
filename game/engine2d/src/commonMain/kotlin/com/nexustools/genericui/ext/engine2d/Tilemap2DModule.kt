package com.nexustools.genericui.ext.engine2d

import com.nexustools.genericui.ext.engine.scene.EntityModule
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Grid-based 2D tilemap module supporting per-tile palette colors and optional tileset sprite atlas.
 */
class Tilemap2DModule(
    val columns: Int,
    val rows: Int,
    val tileWidth: Float = 32f,
    val tileHeight: Float = 32f,
    val tilesetImage: ImageBuffer? = null,
    val tilesetColumns: Int = 8,
    val zOrder: Int = -10
) : EntityModule() {

    private val tileIds = IntArray(columns.coerceAtLeast(1) * rows.coerceAtLeast(1)) { -1 }
    private val tileColors = mutableMapOf<Int, RgbaColor>()

    fun setTile(col: Int, row: Int, tileId: Int) {
        if (col !in 0 until columns || row !in 0 until rows) return
        tileIds[row * columns + col] = tileId
    }

    fun getTile(col: Int, row: Int): Int {
        if (col !in 0 until columns || row !in 0 until rows) return -1
        return tileIds[row * columns + col]
    }

    fun setTileColor(tileId: Int, color: RgbaColor) {
        tileColors[tileId] = color
    }

    fun render(ctx: ImageBuffer2DContext) {
        if (!isEnabled) return
        val m = entity?.worldAffine2D() ?: return

        ctx.save()
        ctx.transform(m.a, m.b, m.c, m.d, m.tx, m.ty)

        val atlas = tilesetImage
        val safeAtlasCols = tilesetColumns.coerceAtLeast(1)
        for (r in 0 until rows) {
            for (c in 0 until columns) {
                val id = tileIds[r * columns + c]
                if (id < 0) continue
                val dx = c * tileWidth
                val dy = r * tileHeight
                if (atlas != null) {
                    val srcCol = id % safeAtlasCols
                    val srcRow = id / safeAtlasCols
                    ctx.drawImage(
                        atlas,
                        srcCol * tileWidth,
                        srcRow * tileHeight,
                        tileWidth,
                        tileHeight,
                        dx,
                        dy,
                        tileWidth,
                        tileHeight
                    )
                } else {
                    val color = tileColors[id] ?: RgbaColor(80, 120, 180, 255)
                    ctx.setFillColor(color)
                    ctx.fillRect(dx, dy, tileWidth, tileHeight)
                }
            }
        }

        ctx.restore()
    }
}

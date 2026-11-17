package com.nexustools.genericui.ext.threejs.core

import com.nexustools.genericui.ext.threejs.math.Vector2
import com.nexustools.genericui.ext.threejs.math.Vector3
import com.nexustools.genericui.ext.threejs.math.Vector4

/**
 * Typed vertex or index attribute buffer (`THREE.BufferAttribute`).
 */
class BufferAttribute(
    var array: FloatArray,
    val itemSize: Int,
    var normalized: Boolean = false
) {
    var version: Int = 0
        private set

    var needsUpdate: Boolean = true
        set(value) {
            field = value
            if (value) version++
        }

    val count: Int
        get() = if (itemSize > 0) array.size / itemSize else 0

    fun getX(index: Int): Float = array[index * itemSize]
    fun getY(index: Int): Float = array[index * itemSize + 1]
    fun getZ(index: Int): Float = array[index * itemSize + 2]
    fun getW(index: Int): Float = array[index * itemSize + 3]

    fun setXY(index: Int, x: Float, y: Float): BufferAttribute {
        val base = index * itemSize
        array[base] = x
        array[base + 1] = y
        return this
    }

    fun setXYZ(index: Int, x: Float, y: Float, z: Float): BufferAttribute {
        val base = index * itemSize
        array[base] = x
        array[base + 1] = y
        array[base + 2] = z
        return this
    }

    fun setXYZW(index: Int, x: Float, y: Float, z: Float, w: Float): BufferAttribute {
        val base = index * itemSize
        array[base] = x
        array[base + 1] = y
        array[base + 2] = z
        array[base + 3] = w
        return this
    }

    fun getVector2(index: Int, target: Vector2 = Vector2()): Vector2 =
        target.set(getX(index), getY(index))

    fun getVector3(index: Int, target: Vector3 = Vector3()): Vector3 =
        target.set(getX(index), getY(index), getZ(index))

    fun getVector4(index: Int, target: Vector4 = Vector4()): Vector4 =
        target.set(getX(index), getY(index), getZ(index), getW(index))

    fun clone(): BufferAttribute =
        BufferAttribute(array.copyOf(), itemSize, normalized)
}

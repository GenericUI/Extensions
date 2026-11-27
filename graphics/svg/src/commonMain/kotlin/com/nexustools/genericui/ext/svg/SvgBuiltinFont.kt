package com.nexustools.genericui.ext.svg

import kotlin.math.max

/**
 * Pure-Kotlin built-in vector stroke font covering printable ASCII (`32..126`) so `<text>`
 * elements inside [Svg] always render sharply at any scale, rotation, or `viewBox` with zero
 * external font dependencies.
 */
object SvgBuiltinFont {

    const val DEFAULT_ADVANCE: Float = 0.62f

    private val GLYPHS: Map<Char, List<FloatArray>> = buildMap {
        fun poly(vararg coords: Float): FloatArray = coords
        fun putG(ch: Char, vararg strokes: FloatArray) {
            put(ch, strokes.toList())
        }

        putG(' ')
        // Digits
        putG('0', poly(0.10f, -0.60f, 0.20f, -0.72f, 0.40f, -0.72f, 0.50f, -0.60f, 0.50f, -0.12f, 0.40f, 0.0f, 0.20f, 0.0f, 0.10f, -0.12f, 0.10f, -0.60f), poly(0.14f, -0.14f, 0.46f, -0.58f))
        putG('1', poly(0.18f, -0.58f, 0.32f, -0.72f, 0.32f, 0.0f), poly(0.14f, 0.0f, 0.48f, 0.0f))
        putG('2', poly(0.10f, -0.56f, 0.18f, -0.72f, 0.42f, -0.72f, 0.50f, -0.58f, 0.48f, -0.42f, 0.10f, 0.0f, 0.50f, 0.0f))
        putG('3', poly(0.10f, -0.62f, 0.20f, -0.72f, 0.42f, -0.72f, 0.50f, -0.58f, 0.42f, -0.38f, 0.24f, -0.38f), poly(0.42f, -0.38f, 0.50f, -0.18f, 0.42f, 0.0f, 0.20f, 0.0f, 0.10f, -0.10f))
        putG('4', poly(0.42f, 0.0f, 0.42f, -0.72f, 0.10f, -0.22f, 0.52f, -0.22f))
        putG('5', poly(0.48f, -0.72f, 0.12f, -0.72f, 0.12f, -0.40f, 0.40f, -0.40f, 0.50f, -0.28f, 0.50f, -0.12f, 0.40f, 0.0f, 0.16f, 0.0f, 0.10f, -0.10f))
        putG('6', poly(0.46f, -0.64f, 0.36f, -0.72f, 0.20f, -0.72f, 0.10f, -0.56f, 0.10f, -0.12f, 0.20f, 0.0f, 0.40f, 0.0f, 0.50f, -0.12f, 0.50f, -0.30f, 0.40f, -0.40f, 0.20f, -0.40f, 0.10f, -0.30f))
        putG('7', poly(0.10f, -0.72f, 0.50f, -0.72f, 0.22f, 0.0f))
        putG('8', poly(0.20f, -0.38f, 0.12f, -0.48f, 0.12f, -0.62f, 0.22f, -0.72f, 0.38f, -0.72f, 0.48f, -0.62f, 0.48f, -0.48f, 0.40f, -0.38f, 0.20f, -0.38f, 0.10f, -0.26f, 0.10f, -0.12f, 0.20f, 0.0f, 0.40f, 0.0f, 0.50f, -0.12f, 0.50f, -0.26f, 0.40f, -0.38f))
        putG('9', poly(0.50f, -0.44f, 0.40f, -0.34f, 0.20f, -0.34f, 0.10f, -0.44f, 0.10f, -0.62f, 0.20f, -0.72f, 0.40f, -0.72f, 0.50f, -0.60f, 0.50f, -0.14f, 0.40f, 0.0f, 0.20f, 0.0f, 0.12f, -0.08f))

        // Uppercase
        putG('A', poly(0.08f, 0.0f, 0.30f, -0.72f, 0.52f, 0.0f), poly(0.15f, -0.24f, 0.45f, -0.24f))
        putG('B', poly(0.10f, 0.0f, 0.10f, -0.72f, 0.38f, -0.72f, 0.48f, -0.62f, 0.48f, -0.46f, 0.38f, -0.36f, 0.10f, -0.36f), poly(0.38f, -0.36f, 0.50f, -0.26f, 0.50f, -0.10f, 0.40f, 0.0f, 0.10f, 0.0f))
        putG('C', poly(0.50f, -0.58f, 0.40f, -0.72f, 0.20f, -0.72f, 0.10f, -0.58f, 0.10f, -0.14f, 0.20f, 0.0f, 0.40f, 0.0f, 0.50f, -0.14f))
        putG('D', poly(0.10f, 0.0f, 0.10f, -0.72f, 0.36f, -0.72f, 0.50f, -0.56f, 0.50f, -0.16f, 0.36f, 0.0f, 0.10f, 0.0f))
        putG('E', poly(0.48f, -0.72f, 0.10f, -0.72f, 0.10f, 0.0f, 0.48f, 0.0f), poly(0.10f, -0.36f, 0.40f, -0.36f))
        putG('F', poly(0.48f, -0.72f, 0.10f, -0.72f, 0.10f, 0.0f), poly(0.10f, -0.36f, 0.40f, -0.36f))
        putG('G', poly(0.50f, -0.58f, 0.40f, -0.72f, 0.20f, -0.72f, 0.10f, -0.58f, 0.10f, -0.14f, 0.20f, 0.0f, 0.42f, 0.0f, 0.50f, -0.12f, 0.50f, -0.34f, 0.32f, -0.34f))
        putG('H', poly(0.10f, -0.72f, 0.10f, 0.0f), poly(0.50f, -0.72f, 0.50f, 0.0f), poly(0.10f, -0.36f, 0.50f, -0.36f))
        putG('I', poly(0.18f, -0.72f, 0.42f, -0.72f), poly(0.30f, -0.72f, 0.30f, 0.0f), poly(0.18f, 0.0f, 0.42f, 0.0f))
        putG('J', poly(0.46f, -0.72f, 0.46f, -0.14f, 0.36f, 0.0f, 0.20f, 0.0f, 0.10f, -0.12f))
        putG('K', poly(0.10f, -0.72f, 0.10f, 0.0f), poly(0.50f, -0.72f, 0.10f, -0.32f), poly(0.24f, -0.46f, 0.50f, 0.0f))
        putG('L', poly(0.12f, -0.72f, 0.12f, 0.0f, 0.50f, 0.0f))
        putG('M', poly(0.08f, 0.0f, 0.08f, -0.72f, 0.30f, -0.26f, 0.52f, -0.72f, 0.52f, 0.0f))
        putG('N', poly(0.10f, 0.0f, 0.10f, -0.72f, 0.50f, 0.0f, 0.50f, -0.72f))
        putG('O', poly(0.10f, -0.58f, 0.20f, -0.72f, 0.40f, -0.72f, 0.50f, -0.58f, 0.50f, -0.14f, 0.40f, 0.0f, 0.20f, 0.0f, 0.10f, -0.14f, 0.10f, -0.58f))
        putG('P', poly(0.10f, 0.0f, 0.10f, -0.72f, 0.40f, -0.72f, 0.50f, -0.62f, 0.50f, -0.44f, 0.40f, -0.34f, 0.10f, -0.34f))
        putG('Q', poly(0.10f, -0.58f, 0.20f, -0.72f, 0.40f, -0.72f, 0.50f, -0.58f, 0.50f, -0.14f, 0.40f, 0.0f, 0.20f, 0.0f, 0.10f, -0.14f, 0.10f, -0.58f), poly(0.34f, -0.18f, 0.52f, 0.08f))
        putG('R', poly(0.10f, 0.0f, 0.10f, -0.72f, 0.40f, -0.72f, 0.50f, -0.62f, 0.50f, -0.44f, 0.40f, -0.34f, 0.10f, -0.34f), poly(0.32f, -0.34f, 0.50f, 0.0f))
        putG('S', poly(0.48f, -0.60f, 0.38f, -0.72f, 0.20f, -0.72f, 0.10f, -0.60f, 0.14f, -0.44f, 0.46f, -0.30f, 0.50f, -0.14f, 0.40f, 0.0f, 0.20f, 0.0f, 0.10f, -0.12f))
        putG('T', poly(0.06f, -0.72f, 0.54f, -0.72f), poly(0.30f, -0.72f, 0.30f, 0.0f))
        putG('U', poly(0.10f, -0.72f, 0.10f, -0.14f, 0.20f, 0.0f, 0.40f, 0.0f, 0.50f, -0.14f, 0.50f, -0.72f))
        putG('V', poly(0.08f, -0.72f, 0.30f, 0.0f, 0.52f, -0.72f))
        putG('W', poly(0.06f, -0.72f, 0.18f, 0.0f, 0.30f, -0.44f, 0.42f, 0.0f, 0.54f, -0.72f))
        putG('X', poly(0.08f, -0.72f, 0.52f, 0.0f), poly(0.52f, -0.72f, 0.08f, 0.0f))
        putG('Y', poly(0.08f, -0.72f, 0.30f, -0.34f, 0.52f, -0.72f), poly(0.30f, -0.34f, 0.30f, 0.0f))
        putG('Z', poly(0.10f, -0.72f, 0.50f, -0.72f, 0.10f, 0.0f, 0.50f, 0.0f))

        // Lowercase
        putG('a', poly(0.14f, -0.44f, 0.24f, -0.52f, 0.40f, -0.52f, 0.48f, -0.42f, 0.48f, 0.0f), poly(0.48f, -0.28f, 0.22f, -0.28f, 0.12f, -0.18f, 0.12f, -0.08f, 0.22f, 0.0f, 0.40f, 0.0f, 0.48f, -0.08f))
        putG('b', poly(0.12f, -0.72f, 0.12f, 0.0f), poly(0.12f, -0.42f, 0.22f, -0.52f, 0.40f, -0.52f, 0.48f, -0.40f, 0.48f, -0.12f, 0.40f, 0.0f, 0.22f, 0.0f, 0.12f, -0.10f))
        putG('c', poly(0.48f, -0.42f, 0.38f, -0.52f, 0.20f, -0.52f, 0.12f, -0.40f, 0.12f, -0.12f, 0.20f, 0.0f, 0.38f, 0.0f, 0.48f, -0.10f))
        putG('d', poly(0.48f, -0.72f, 0.48f, 0.0f), poly(0.48f, -0.42f, 0.38f, -0.52f, 0.20f, -0.52f, 0.12f, -0.40f, 0.12f, -0.12f, 0.20f, 0.0f, 0.38f, 0.0f, 0.48f, -0.10f))
        putG('e', poly(0.12f, -0.26f, 0.48f, -0.26f, 0.48f, -0.42f, 0.38f, -0.52f, 0.20f, -0.52f, 0.12f, -0.40f, 0.12f, -0.12f, 0.20f, 0.0f, 0.40f, 0.0f, 0.48f, -0.08f))
        putG('f', poly(0.44f, -0.66f, 0.36f, -0.72f, 0.26f, -0.72f, 0.20f, -0.62f, 0.20f, 0.0f), poly(0.10f, -0.48f, 0.38f, -0.48f))
        putG('g', poly(0.48f, -0.52f, 0.48f, 0.10f, 0.38f, 0.18f, 0.20f, 0.18f, 0.12f, 0.12f), poly(0.48f, -0.42f, 0.38f, -0.52f, 0.20f, -0.52f, 0.12f, -0.40f, 0.12f, -0.12f, 0.20f, 0.0f, 0.38f, 0.0f, 0.48f, -0.10f))
        putG('h', poly(0.12f, -0.72f, 0.12f, 0.0f), poly(0.12f, -0.40f, 0.22f, -0.52f, 0.40f, -0.52f, 0.48f, -0.40f, 0.48f, 0.0f))
        putG('i', poly(0.30f, -0.70f, 0.30f, -0.66f), poly(0.30f, -0.50f, 0.30f, 0.0f))
        putG('j', poly(0.38f, -0.70f, 0.38f, -0.66f), poly(0.38f, -0.50f, 0.38f, 0.10f, 0.28f, 0.18f, 0.16f, 0.16f))
        putG('k', poly(0.14f, -0.72f, 0.14f, 0.0f), poly(0.46f, -0.50f, 0.14f, -0.20f), poly(0.26f, -0.32f, 0.48f, 0.0f))
        putG('l', poly(0.24f, -0.72f, 0.30f, -0.72f, 0.30f, 0.0f, 0.38f, 0.0f))
        putG('m', poly(0.06f, 0.0f, 0.06f, -0.52f), poly(0.06f, -0.40f, 0.14f, -0.52f, 0.24f, -0.52f, 0.30f, -0.40f, 0.30f, 0.0f), poly(0.30f, -0.40f, 0.38f, -0.52f, 0.48f, -0.52f, 0.54f, -0.40f, 0.54f, 0.0f))
        putG('n', poly(0.12f, 0.0f, 0.12f, -0.52f), poly(0.12f, -0.40f, 0.22f, -0.52f, 0.40f, -0.52f, 0.48f, -0.40f, 0.48f, 0.0f))
        putG('o', poly(0.12f, -0.40f, 0.20f, -0.52f, 0.40f, -0.52f, 0.48f, -0.40f, 0.48f, -0.12f, 0.40f, 0.0f, 0.20f, 0.0f, 0.12f, -0.12f, 0.12f, -0.40f))
        putG('p', poly(0.12f, 0.18f, 0.12f, -0.52f), poly(0.12f, -0.42f, 0.22f, -0.52f, 0.40f, -0.52f, 0.48f, -0.40f, 0.48f, -0.12f, 0.40f, 0.0f, 0.22f, 0.0f, 0.12f, -0.10f))
        putG('q', poly(0.48f, 0.18f, 0.48f, -0.52f), poly(0.48f, -0.42f, 0.38f, -0.52f, 0.20f, -0.52f, 0.12f, -0.40f, 0.12f, -0.12f, 0.20f, 0.0f, 0.38f, 0.0f, 0.48f, -0.10f))
        putG('r', poly(0.14f, 0.0f, 0.14f, -0.52f), poly(0.14f, -0.38f, 0.24f, -0.52f, 0.40f, -0.52f, 0.48f, -0.44f))
        putG('s', poly(0.46f, -0.44f, 0.36f, -0.52f, 0.20f, -0.52f, 0.12f, -0.44f, 0.16f, -0.32f, 0.44f, -0.22f, 0.48f, -0.10f, 0.38f, 0.0f, 0.20f, 0.0f, 0.12f, -0.08f))
        putG('t', poly(0.28f, -0.68f, 0.28f, -0.08f, 0.36f, 0.0f, 0.46f, 0.0f), poly(0.14f, -0.48f, 0.44f, -0.48f))
        putG('u', poly(0.12f, -0.52f, 0.12f, -0.12f, 0.20f, 0.0f, 0.38f, 0.0f, 0.48f, -0.12f), poly(0.48f, -0.52f, 0.48f, 0.0f))
        putG('v', poly(0.10f, -0.52f, 0.30f, 0.0f, 0.50f, -0.52f))
        putG('w', poly(0.08f, -0.52f, 0.18f, 0.0f, 0.30f, -0.32f, 0.42f, 0.0f, 0.52f, -0.52f))
        putG('x', poly(0.10f, -0.52f, 0.50f, 0.0f), poly(0.50f, -0.52f, 0.10f, 0.0f))
        putG('y', poly(0.12f, -0.52f, 0.30f, -0.02f), poly(0.48f, -0.52f, 0.22f, 0.18f, 0.12f, 0.18f))
        putG('z', poly(0.12f, -0.52f, 0.48f, -0.52f, 0.12f, 0.0f, 0.48f, 0.0f))

        // Common punctuation
        putG('!', poly(0.30f, -0.72f, 0.30f, -0.22f), poly(0.30f, -0.06f, 0.30f, 0.0f))
        putG('.', poly(0.28f, -0.05f, 0.32f, -0.05f, 0.32f, 0.0f, 0.28f, 0.0f, 0.28f, -0.05f))
        putG(',', poly(0.30f, -0.06f, 0.30f, 0.02f, 0.22f, 0.10f))
        putG(':', poly(0.28f, -0.44f, 0.32f, -0.44f, 0.32f, -0.38f, 0.28f, -0.38f), poly(0.28f, -0.06f, 0.32f, -0.06f, 0.32f, 0.0f, 0.28f, 0.0f))
        putG(';', poly(0.28f, -0.44f, 0.32f, -0.44f), poly(0.30f, -0.06f, 0.30f, 0.02f, 0.22f, 0.10f))
        putG('-', poly(0.14f, -0.34f, 0.46f, -0.34f))
        putG('_', poly(0.08f, 0.08f, 0.52f, 0.08f))
        putG('+', poly(0.12f, -0.34f, 0.48f, -0.34f), poly(0.30f, -0.52f, 0.30f, -0.16f))
        putG('=', poly(0.12f, -0.42f, 0.48f, -0.42f), poly(0.12f, -0.24f, 0.48f, -0.24f))
        putG('/', poly(0.14f, 0.04f, 0.46f, -0.74f))
        putG('\\', poly(0.14f, -0.74f, 0.46f, 0.04f))
        putG('(', poly(0.38f, -0.74f, 0.24f, -0.54f, 0.24f, -0.14f, 0.38f, 0.08f))
        putG(')', poly(0.22f, -0.74f, 0.36f, -0.54f, 0.36f, -0.14f, 0.22f, 0.08f))
        putG('[', poly(0.38f, -0.74f, 0.22f, -0.74f, 0.22f, 0.08f, 0.38f, 0.08f))
        putG(']', poly(0.22f, -0.74f, 0.38f, -0.74f, 0.38f, 0.08f, 0.22f, 0.08f))
        putG('{', poly(0.40f, -0.74f, 0.28f, -0.66f, 0.28f, -0.42f, 0.20f, -0.34f, 0.28f, -0.26f, 0.28f, 0.0f, 0.40f, 0.08f))
        putG('}', poly(0.20f, -0.74f, 0.32f, -0.66f, 0.32f, -0.42f, 0.40f, -0.34f, 0.32f, -0.26f, 0.32f, 0.0f, 0.20f, 0.08f))
        putG('?', poly(0.14f, -0.58f, 0.22f, -0.72f, 0.40f, -0.72f, 0.48f, -0.58f, 0.44f, -0.44f, 0.30f, -0.34f, 0.30f, -0.22f), poly(0.30f, -0.06f, 0.30f, 0.0f))
        putG('%', poly(0.12f, 0.02f, 0.48f, -0.72f), poly(0.12f, -0.66f, 0.22f, -0.66f, 0.22f, -0.54f, 0.12f, -0.54f, 0.12f, -0.66f), poly(0.38f, -0.14f, 0.48f, -0.14f, 0.48f, -0.02f, 0.38f, -0.02f, 0.38f, -0.14f))
        putG('#', poly(0.20f, -0.72f, 0.14f, 0.0f), poly(0.44f, -0.72f, 0.38f, 0.0f), poly(0.08f, -0.46f, 0.52f, -0.46f), poly(0.06f, -0.24f, 0.50f, -0.24f))
        putG('*', poly(0.30f, -0.62f, 0.30f, -0.22f), poly(0.12f, -0.52f, 0.48f, -0.32f), poly(0.48f, -0.52f, 0.12f, -0.32f))
        putG('\'', poly(0.30f, -0.72f, 0.28f, -0.54f))
        putG('"', poly(0.22f, -0.72f, 0.22f, -0.54f), poly(0.38f, -0.72f, 0.38f, -0.54f))
        putG('<', poly(0.46f, -0.56f, 0.14f, -0.34f, 0.46f, -0.12f))
        putG('>', poly(0.14f, -0.56f, 0.46f, -0.34f, 0.14f, -0.12f))
        putG('^', poly(0.14f, -0.50f, 0.30f, -0.72f, 0.46f, -0.50f))
        putG('~', poly(0.10f, -0.34f, 0.20f, -0.44f, 0.40f, -0.26f, 0.50f, -0.36f))
        putG('|', poly(0.30f, -0.74f, 0.30f, 0.10f))
        putG('@', poly(0.44f, -0.26f, 0.36f, -0.44f, 0.22f, -0.44f, 0.18f, -0.24f, 0.26f, -0.14f, 0.42f, -0.18f, 0.48f, -0.52f, 0.36f, -0.68f, 0.16f, -0.64f, 0.08f, -0.36f, 0.14f, -0.06f, 0.36f, 0.02f, 0.50f, -0.04f))
        putG('&', poly(0.48f, 0.0f, 0.16f, -0.44f, 0.18f, -0.64f, 0.30f, -0.72f, 0.40f, -0.62f, 0.36f, -0.46f, 0.12f, -0.22f, 0.14f, -0.04f, 0.30f, 0.02f, 0.48f, -0.20f))
        putG('$', poly(0.46f, -0.58f, 0.34f, -0.66f, 0.18f, -0.66f, 0.12f, -0.54f, 0.18f, -0.40f, 0.44f, -0.28f, 0.48f, -0.12f, 0.36f, -0.02f, 0.18f, -0.02f, 0.10f, -0.12f), poly(0.30f, -0.74f, 0.30f, 0.08f))
    }

    private val FALLBACK_GLYPH: List<FloatArray> = listOf(
        floatArrayOf(0.12f, -0.68f, 0.48f, -0.68f, 0.48f, -0.04f, 0.12f, -0.04f, 0.12f, -0.68f)
    )

    fun measureTextWidth(text: String, fontSize: Float): Float {
        if (text.isEmpty()) return 0f
        return text.length * fontSize * DEFAULT_ADVANCE
    }

    /**
     * Generates local user-space stroke polylines for [text] positioned at `(x, y)` with
     * [fontSize] and [textAnchor] (`start`, `middle`, `end`).
     */
    fun buildTextStrokeSubpaths(
        text: String,
        x: Float,
        y: Float,
        fontSize: Float,
        textAnchor: String
    ): List<List<SvgPoint>> {
        if (text.isEmpty() || fontSize <= 0f) return emptyList()
        val totalWidth = measureTextWidth(text, fontSize)
        val startX = when (textAnchor.trim().lowercase()) {
            "middle" -> x - totalWidth * 0.5f
            "end" -> x - totalWidth
            else -> x
        }
        val advance = fontSize * DEFAULT_ADVANCE
        val subpaths = ArrayList<List<SvgPoint>>(text.length * 2)
        var cursorX = startX
        for (ch in text) {
            val strokes = GLYPHS[ch] ?: if (ch.isWhitespace()) emptyList() else FALLBACK_GLYPH
            for (arr in strokes) {
                if (arr.size < 2) continue
                val pts = ArrayList<SvgPoint>(max(2, arr.size / 2))
                var i = 0
                while (i + 1 < arr.size) {
                    pts.add(SvgPoint(cursorX + arr[i] * fontSize, y + arr[i + 1] * fontSize))
                    i += 2
                }
                if (pts.size == 1) {
                    pts.add(SvgPoint(pts[0].x + 0.01f * fontSize, pts[0].y))
                }
                subpaths.add(pts)
            }
            cursorX += advance
        }
        return subpaths
    }
}

package com.nexustools.genericui.ext.engine.svg

import com.nexustools.genericui.ext.engine.animation.AnimationWrapMode
import com.nexustools.genericui.ext.engine.animation.EasingFunction
import kotlin.math.PI
import kotlin.math.sin

/**
 * Builds an articulated 11-bone 2D humanoid character skeleton (`pelvis`, `torso`, `head`,
 * `upperArmL`, `forearmL`, `upperArmR`, `forearmR`, `thighL`, `shinL`, `thighR`, `shinR`)
 * with default SVG vector parts and built-in walk/wave animation clips.
 */
object HumanoidSkeleton2DFactory {
    fun create(
        scale: Float = 1f,
        rootOffsetX: Float = 0f,
        rootOffsetY: Float = 0f,
        attachDefaultParts: Boolean = true
    ): Skeleton2D {
        val s = scale.coerceAtLeast(0.1f)
        val halfPi = (PI * 0.5).toFloat()
        val pi = PI.toFloat()

        val bones = listOf(
            Bone2D("pelvis", 12f * s, 0f, 0f, -halfPi, parentName = null),
            Bone2D("torso", 42f * s, 12f * s, 0f, 0f, parentName = "pelvis"),
            Bone2D("head", 22f * s, 44f * s, 0f, 0f, parentName = "torso"),
            Bone2D("upperArmL", 28f * s, 38f * s, -15f * s, pi, parentName = "torso"),
            Bone2D("forearmL", 26f * s, 28f * s, 0f, 0f, parentName = "upperArmL"),
            Bone2D("upperArmR", 28f * s, 38f * s, 15f * s, pi, parentName = "torso"),
            Bone2D("forearmR", 26f * s, 28f * s, 0f, 0f, parentName = "upperArmR"),
            Bone2D("thighL", 32f * s, 0f, -9f * s, pi, parentName = "pelvis"),
            Bone2D("shinL", 30f * s, 32f * s, 0f, 0f, parentName = "thighL"),
            Bone2D("thighR", 32f * s, 0f, 9f * s, pi, parentName = "pelvis"),
            Bone2D("shinR", 30f * s, 32f * s, 0f, 0f, parentName = "thighR")
        )

        val parts = if (attachDefaultParts) {
            listOf(
                SvgBonePart("upperArmL", SvgRectPrimitive(0f, -4f * s, 28f * s, 8f * s, 4f * s), "#3B82F6", "#1E3A8A", 1.5f, zOrder = 1),
                SvgBonePart("forearmL", SvgRectPrimitive(0f, -3.5f * s, 26f * s, 7f * s, 3.5f * s), "#FDBA74", "#9A3412", 1.5f, zOrder = 2),
                SvgBonePart("thighL", SvgRectPrimitive(0f, -5.5f * s, 32f * s, 11f * s, 5f * s), "#1E293B", "#0F172A", 1.5f, zOrder = 3),
                SvgBonePart("shinL", SvgRectPrimitive(0f, -4.5f * s, 30f * s, 9f * s, 4f * s), "#334155", "#0F172A", 1.5f, zOrder = 4),
                SvgBonePart("pelvis", SvgRectPrimitive(-4f * s, -12f * s, 16f * s, 24f * s, 6f * s), "#1D4ED8", "#1E3A8A", 1.5f, zOrder = 5),
                SvgBonePart("torso", SvgRectPrimitive(0f, -15f * s, 42f * s, 30f * s, 8f * s), "#2563EB", "#1E3A8A", 1.5f, zOrder = 6),
                SvgBonePart("head", SvgCirclePrimitive(12f * s, 0f, 13f * s), "#FDBA74", "#9A3412", 1.5f, zOrder = 7),
                SvgBonePart("thighR", SvgRectPrimitive(0f, -5.5f * s, 32f * s, 11f * s, 5f * s), "#1E293B", "#0F172A", 1.5f, zOrder = 8),
                SvgBonePart("shinR", SvgRectPrimitive(0f, -4.5f * s, 30f * s, 9f * s, 4f * s), "#334155", "#0F172A", 1.5f, zOrder = 9),
                SvgBonePart("upperArmR", SvgRectPrimitive(0f, -4f * s, 28f * s, 8f * s, 4f * s), "#60A5FA", "#1E3A8A", 1.5f, zOrder = 10),
                SvgBonePart("forearmR", SvgRectPrimitive(0f, -3.5f * s, 26f * s, 7f * s, 3.5f * s), "#FDBA74", "#9A3412", 1.5f, zOrder = 11)
            )
        } else {
            emptyList()
        }

        return Skeleton2D(
            bones = bones,
            rootOffsetX = rootOffsetX,
            rootOffsetY = rootOffsetY,
            initialParts = parts
        )
    }

    fun createWalkClip(durationSeconds: Float = 0.8f): SkeletalAnimationClip2D {
        val pi = PI.toFloat()
        val twoPi = (PI * 2.0).toFloat()
        val tracks = listOf(
            BonePoseTrack2D.fromLambda("thighL") { p -> pi + sin(p * twoPi) * 0.55f },
            BonePoseTrack2D.fromLambda("shinL") { p -> (0.25f - sin(p * twoPi) * 0.35f).coerceAtLeast(0f) },
            BonePoseTrack2D.fromLambda("thighR") { p -> pi - sin(p * twoPi) * 0.55f },
            BonePoseTrack2D.fromLambda("shinR") { p -> (0.25f + sin(p * twoPi) * 0.35f).coerceAtLeast(0f) },
            BonePoseTrack2D.fromLambda("upperArmL") { p -> pi - sin(p * twoPi) * 0.45f },
            BonePoseTrack2D.fromLambda("forearmL") { p -> -0.35f - sin(p * twoPi) * 0.2f },
            BonePoseTrack2D.fromLambda("upperArmR") { p -> pi + sin(p * twoPi) * 0.45f },
            BonePoseTrack2D.fromLambda("forearmR") { p -> -0.35f + sin(p * twoPi) * 0.2f }
        )
        return SkeletalAnimationClip2D("Walk", durationSeconds, AnimationWrapMode.LOOP, tracks)
    }

    fun createWaveClip(durationSeconds: Float = 1.0f): SkeletalAnimationClip2D {
        val pi = PI.toFloat()
        val tracks = listOf(
            BonePoseTrack2D.fromLambda("upperArmR", EasingFunction.SINE_IN_OUT) { p ->
                pi - 2.1f * sin((p * PI).toFloat())
            },
            BonePoseTrack2D.fromLambda("forearmR", EasingFunction.LINEAR) { p ->
                -0.6f + sin((p * PI * 4.0).toFloat()) * 0.45f
            }
        )
        return SkeletalAnimationClip2D("Wave", durationSeconds, AnimationWrapMode.LOOP, tracks)
    }
}

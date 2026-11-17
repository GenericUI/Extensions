package com.nexustools.genericui.ext.engine.svg

import com.nexustools.genericui.ext.engine.animation.AnimationScheduler
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Skeleton2DAndTwoBoneIkTest {

    @Test
    fun skeletonUpdatesTopologicalForwardKinematicsOutOfOrder() {
        // Deliberately pass child before parent to verify topological ordering
        val forearm = Bone2D("forearm", lengthPx = 30f, localX = 40f, localY = 0f, localAngleRad = 0f, parentName = "upperArm")
        val upperArm = Bone2D("upperArm", lengthPx = 40f, localX = 10f, localY = 20f, localAngleRad = 0f, parentName = null)
        val skeleton = Skeleton2D(listOf(forearm, upperArm), rootOffsetX = 100f, rootOffsetY = 50f)

        assertEquals(110f, upperArm.worldX, 1e-3f)
        assertEquals(70f, upperArm.worldY, 1e-3f)
        assertEquals(150f, forearm.worldX, 1e-3f)
        assertEquals(70f, forearm.worldY, 1e-3f)
        assertEquals(180f, forearm.tipX, 1e-3f)
        assertEquals(70f, forearm.tipY, 1e-3f)

        upperArm.localAngleRad = (PI * 0.5).toFloat()
        skeleton.updateWorldTransforms()
        assertEquals(110f, forearm.worldX, 1e-3f)
        assertEquals(110f, forearm.worldY, 1e-3f)
        assertEquals(110f, forearm.tipX, 1e-3f)
        assertEquals(140f, forearm.tipY, 1e-3f)
    }

    @Test
    fun twoBoneIkSolverConvergesWithinHalfPixelAcrossReachableTargets() {
        val skeleton = HumanoidSkeleton2DFactory.create(rootOffsetX = 128f, rootOffsetY = 140f)
        val upper = skeleton.getBone("upperArmR")!!
        val lower = skeleton.getBone("forearmR")!!

        val targetX = upper.worldX + 26f
        val targetY = upper.worldY + 22f

        val result = TwoBoneIkSolver2D.applyToBones(
            skeleton = skeleton,
            upperBoneName = "upperArmR",
            lowerBoneName = "forearmR",
            targetX = targetX,
            targetY = targetY,
            bendDirection = 1f
        )

        assertNotNull(result)
        assertTrue(result.reachedTarget)
        val errorPx = hypot(lower.tipX - targetX, lower.tipY - targetY)
        assertTrue(errorPx < 0.5f, "Expected IK convergence < 0.5px, got $errorPx px")
    }

    @Test
    fun humanoidAnimatorDrivesArmAndLegPosesAndExportsValidSvgMarkup() {
        val skeleton = HumanoidSkeleton2DFactory.create(rootOffsetX = 128f, rootOffsetY = 128f)
        assertNotNull(skeleton.getBone("pelvis"))
        assertNotNull(skeleton.getBone("torso"))
        assertNotNull(skeleton.getBone("head"))
        assertNotNull(skeleton.getBone("upperArmL"))
        assertNotNull(skeleton.getBone("forearmL"))
        assertNotNull(skeleton.getBone("upperArmR"))
        assertNotNull(skeleton.getBone("forearmR"))
        assertNotNull(skeleton.getBone("thighL"))
        assertNotNull(skeleton.getBone("shinL"))
        assertNotNull(skeleton.getBone("thighR"))
        assertNotNull(skeleton.getBone("shinR"))

        val animator = SkeletalAnimator2D(skeleton)
        val walk = HumanoidSkeleton2DFactory.createWalkClip(durationSeconds = 0.8f)
        val scheduler = AnimationScheduler()
        animator.play(walk, scheduler = scheduler)

        val legStartX = skeleton.getBone("shinL")!!.tipX
        scheduler.evaluateAtVBlank(1L, 0.0)
        val pose0Angle = skeleton.getBone("thighL")!!.localAngleRad
        scheduler.evaluateAtVBlank(2L, 200.0)
        val poseQuarterAngle = skeleton.getBone("thighL")!!.localAngleRad
        val legQuarterX = skeleton.getBone("shinL")!!.tipX

        assertTrue(pose0Angle != poseQuarterAngle, "Expected thighL angle to animate")
        assertTrue(legStartX != legQuarterX, "Expected foot tip X to change during walk cycle")

        val svg = skeleton.exportSvgMarkup(256, 256)
        assertTrue(svg.startsWith("<svg "))
        assertTrue(svg.contains("viewBox=\"0 0 256 256\""))
        assertTrue(svg.contains("data-bone=\"head\""))
        assertTrue(svg.contains("data-bone=\"thighL\""))
        assertTrue(svg.endsWith("</svg>"))
    }
}

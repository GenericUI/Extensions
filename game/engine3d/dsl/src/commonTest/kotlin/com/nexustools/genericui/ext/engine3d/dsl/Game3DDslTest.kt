package com.nexustools.genericui.ext.engine3d.dsl

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.ext.box3d.BodyType3D
import com.nexustools.genericui.ext.canvas3d.Canvas3DRegistry
import com.nexustools.genericui.ext.engine.scene.FixedStepAccumulator
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Game3DDslTest {
    @BeforeTest
    fun setUp() {
        Canvas3DRegistry.overrideAllSupportForTesting()
    }

    @AfterTest
    fun tearDown() {
        Canvas3DRegistry.resetForTesting()
    }
    @Test
    fun game3DCanvasDslComposesPhysicsMeshesLightsCamerasAndHumanoidRig() {
        val root = Container()
        var vblankAnimProgress = 0f

        val host = root.game3DCanvas(width = 320, height = 240, autoStart = false) {
            physics3D(debugDraw = true)
            animateLambda(durationSeconds = 1f) { p ->
                vblankAnimProgress = p
            }

            val hero = entity("Hero") {
                position(0f, 2f, 0f)
                rigidBody3D(BodyType3D.KINEMATIC)
                sphereCollider3D(radius = 0.5f)
                characterController3D()
                humanoidRig3D(colorHex = 0x38bdf8, scale = 1.2f) {
                    playClip("run", loop = true)
                }
            }

            entity("Crate") {
                position(2f, 4f, 0f)
                meshRenderer3D()
                rigidBody3D(BodyType3D.DYNAMIC)
                boxCollider3D()
            }

            entity("Sun") {
                directionalLight3D(colorHex = 0xffffff, intensity = 1.2f)
            }

            entity("Point") {
                pointLight3D(colorHex = 0xffcc88, intensity = 2f)
            }

            entity("FollowCam") {
                followCamera3D(target = hero)
            }
        }

        host.scene.stepLogicTick(1f / 60f, FixedStepAccumulator(1f / 60f))
        host.stepVBlank(0.0)
        host.stepVBlank(250.0)

        val hero = host.scene.findEntityByName("Hero")
        assertNotNull(hero)
        val rigMod = hero.getModule<com.nexustools.genericui.ext.engine3d.SkeletalModel3DModule>()
        assertNotNull(rigMod)
        assertEquals(15, rigMod.skeleton?.bones?.size)
        assertTrue(vblankAnimProgress > 0.2f)
        host.dispose()
    }
}

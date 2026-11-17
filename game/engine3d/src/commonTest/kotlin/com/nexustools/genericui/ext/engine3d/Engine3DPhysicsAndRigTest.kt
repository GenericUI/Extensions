package com.nexustools.genericui.ext.engine3d

import com.nexustools.genericui.ext.box3d.BodyType3D
import com.nexustools.genericui.ext.box3d.Vec3
import com.nexustools.genericui.ext.engine.animation.EasingFunction
import com.nexustools.genericui.ext.engine.math.Vec3f
import com.nexustools.genericui.ext.engine.scene.FixedStepAccumulator
import com.nexustools.genericui.ext.threejs.geometries.BoxGeometry
import com.nexustools.genericui.ext.threejs.materials.MeshStandardMaterial
import com.nexustools.genericui.ext.threejs.math.Color
import com.nexustools.genericui.ext.canvas3d.Canvas3DRegistry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Engine3DPhysicsAndRigTest {
    @BeforeTest
    fun setUp() {
        Canvas3DRegistry.overrideAllSupportForTesting()
    }

    @AfterTest
    fun tearDown() {
        Canvas3DRegistry.resetForTesting()
    }
    @Test
    fun box3dPhysicsCharacterControllerAnd15BoneHumanoidRigAnimateAcrossVBlank() {
        val host = Game3DCanvasHost()
        val physics = host.scene.registerSubsystem(PhysicsWorld3DSubsystem(gravity = Vec3(0f, -9.81f, 0f)))

        // Ground collider + falling dynamic box + character controller
        host.scene.createEntity("Ground").apply {
            transform.setPosition(0f, -1f, 0f)
            addModule(RigidBody3DModule(BodyType3D.STATIC))
            addModule(Collider3DModule.box(20f, 1f, 20f))
        }
        val boxEntity = host.scene.createEntity("Box3D").apply {
            transform.setPosition(0f, 6f, 0f)
            addModule(RigidBody3DModule(BodyType3D.DYNAMIC))
            addModule(Collider3DModule.box(0.5f, 0.5f, 0.5f))
            addModule(MeshRenderer3DModule(BoxGeometry(1f, 1f, 1f), MeshStandardMaterial(color = Color(0xff8800))))
        }
        val charEntity = host.scene.createEntity("HeroChar").apply {
            transform.setPosition(3f, 2f, 0f)
            addModule(RigidBody3DModule(BodyType3D.KINEMATIC))
            addModule(Collider3DModule.capsule(0.4f, 0.8f))
            addModule(CharacterController3DModule())
        }

        // Procedural 15-bone rigged 3D humanoid + clip + VBlank bone lambda animation
        val rig = ProceduralHumanoidRig3D.create()
        assertEquals(15, rig.skeleton.bones.size)
        assertNotNull(rig.getBone("pelvis"))
        assertNotNull(rig.getBone("forearmR"))
        assertNotNull(rig.getBone("shinL"))

        var waveCallbackCount = 0
        val rigEntity = host.scene.createEntity("Humanoid").apply {
            val mod = SkeletalModel3DModule(rig)
            addModule(mod)
            mod.playClip("walk", loop = true)
            mod.animateBoneLambda(
                boneName = "upperArmR",
                durationSeconds = 0.5f,
                curve = EasingFunction.SINE_IN_OUT
            ) { bone, p ->
                bone.rotation.z = p * 1.1f
                waveCallbackCount++
            }
        }

        // Lights & Orbit Camera
        host.scene.createEntity("Sun").apply { addModule(DirectionalLight3DModule()) }
        host.scene.createEntity("Lamp").apply { addModule(PointLight3DModule()) }
        host.scene.createEntity("OrbCam").apply {
            transform.setPosition(0f, 2f, 6f)
            addModule(OrbitCamera3DModule(canvas = host.canvas))
        }

        val acc = FixedStepAccumulator(1f / 60f)
        for (i in 0 until 20) {
            host.scene.stepLogicTick(1f / 60f, acc)
        }
        assertTrue(boxEntity.transform.position.y < 5.75f, "Gravity must pull 3D box downward")
        assertTrue(physics.world.bodies.size >= 3)

        val moved = charEntity.getModule<CharacterController3DModule>()?.move(Vec3f(0.25f, 0f, 0f), 1f / 60f)
        assertNotNull(moved)
        assertTrue(moved.x > 0.1f)

        // Two VBlank frames evaluate skeletal tracks & lambda bone animation before Three.js render
        host.stepVBlank(0.0)
        host.stepVBlank(250.0)
        val modelMod = rigEntity.getModule<SkeletalModel3DModule>()!!
        assertTrue(waveCallbackCount >= 1)
        assertTrue(modelMod.getBone("upperArmR")!!.rotation.z > 0.05f)
        assertTrue(modelMod.getBone("thighL") != null)
        host.dispose()
    }
}

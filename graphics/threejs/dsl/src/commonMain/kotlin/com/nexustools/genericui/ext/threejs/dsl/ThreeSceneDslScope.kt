package com.nexustools.genericui.ext.threejs.dsl

import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.threejs.cameras.Camera
import com.nexustools.genericui.ext.threejs.cameras.OrthographicCamera
import com.nexustools.genericui.ext.threejs.cameras.PerspectiveCamera
import com.nexustools.genericui.ext.threejs.core.BufferGeometry
import com.nexustools.genericui.ext.threejs.geometries.BoxGeometry
import com.nexustools.genericui.ext.threejs.geometries.SphereGeometry
import com.nexustools.genericui.ext.threejs.lights.AmbientLight
import com.nexustools.genericui.ext.threejs.lights.DirectionalLight
import com.nexustools.genericui.ext.threejs.lights.PointLight
import com.nexustools.genericui.ext.threejs.materials.Material
import com.nexustools.genericui.ext.threejs.materials.MeshStandardMaterial
import com.nexustools.genericui.ext.threejs.math.Color
import com.nexustools.genericui.ext.threejs.objects.Group
import com.nexustools.genericui.ext.threejs.objects.Mesh
import com.nexustools.genericui.ext.threejs.renderers.WebGLRenderer
import com.nexustools.genericui.ext.threejs.scenes.Scene

/**
 * Fluent DSL scope for constructing a Three.js [Scene], cameras, lights, and meshes.
 */
@GenericUiDslMarker
class ThreeSceneDslScope(
    val scene: Scene = Scene(),
    var activeCamera: Camera = PerspectiveCamera(50f, 800f / 600f, 0.1f, 2000f),
    val renderer: WebGLRenderer? = null
) {
    var onBeforeRender: ((deltaMs: Double, elapsedTimeMs: Double) -> Unit)? = null

    /**
     * Sets the scene background color from a 24-bit RGB integer.
     */
    fun background(colorHex: Int) {
        scene.background = Color(colorHex)
    }

    /**
     * Sets the scene background [Color].
     */
    fun background(color: Color) {
        scene.background = color
    }

    /**
     * Configures and sets the active [PerspectiveCamera] for this scene.
     */
    fun perspectiveCamera(
        fov: Float = 50f,
        aspect: Float = 800f / 600f,
        near: Float = 0.1f,
        far: Float = 2000f,
        block: (PerspectiveCamera.() -> Unit)? = null
    ): PerspectiveCamera {
        val cam = PerspectiveCamera(fov = fov, aspect = aspect, near = near, far = far)
        if (block != null) {
            cam.block()
        }
        cam.updateProjectionMatrix()
        scene.add(cam)
        activeCamera = cam
        return cam
    }

    /**
     * Configures and sets the active [OrthographicCamera] for this scene.
     */
    fun orthographicCamera(
        left: Float = -1f,
        right: Float = 1f,
        top: Float = 1f,
        bottom: Float = -1f,
        near: Float = 0.1f,
        far: Float = 2000f,
        block: (OrthographicCamera.() -> Unit)? = null
    ): OrthographicCamera {
        val cam = OrthographicCamera(
            left = left,
            right = right,
            top = top,
            bottom = bottom,
            near = near,
            far = far
        )
        if (block != null) {
            cam.block()
        }
        cam.updateProjectionMatrix()
        scene.add(cam)
        activeCamera = cam
        return cam
    }

    /**
     * Adds a [Mesh] with the given [geometry] and [material] to the [scene].
     */
    fun mesh(
        geometry: BufferGeometry = BoxGeometry(),
        material: Material = MeshStandardMaterial(),
        name: String = "",
        block: (Mesh.() -> Unit)? = null
    ): Mesh {
        val m = Mesh(geometry = geometry, material = material)
        if (name.isNotEmpty()) {
            m.name = name
        }
        if (block != null) {
            m.block()
        }
        scene.add(m)
        return m
    }

    /**
     * Convenience helper that creates and adds a [BoxGeometry] [Mesh] to the [scene].
     */
    fun boxMesh(
        width: Float = 1f,
        height: Float = 1f,
        depth: Float = 1f,
        colorHex: Int = 0x3b82f6,
        block: (Mesh.() -> Unit)? = null
    ): Mesh = mesh(
        geometry = BoxGeometry(width = width, height = height, depth = depth),
        material = MeshStandardMaterial(color = Color(colorHex)),
        block = block
    )

    /**
     * Convenience helper that creates and adds a [SphereGeometry] [Mesh] to the [scene].
     */
    fun sphereMesh(
        radius: Float = 1f,
        widthSegments: Int = 32,
        heightSegments: Int = 16,
        colorHex: Int = 0x10b981,
        block: (Mesh.() -> Unit)? = null
    ): Mesh = mesh(
        geometry = SphereGeometry(
            radius = radius,
            widthSegments = widthSegments,
            heightSegments = heightSegments
        ),
        material = MeshStandardMaterial(color = Color(colorHex)),
        block = block
    )

    /**
     * Adds a [Group] node to the [scene].
     */
    fun group(
        name: String = "",
        block: (Group.() -> Unit)? = null
    ): Group {
        val g = Group()
        if (name.isNotEmpty()) {
            g.name = name
        }
        if (block != null) {
            g.block()
        }
        scene.add(g)
        return g
    }

    /**
     * Adds an [AmbientLight] to the [scene].
     */
    fun ambientLight(
        colorHex: Int = 0xffffff,
        intensity: Float = 1f,
        block: (AmbientLight.() -> Unit)? = null
    ): AmbientLight {
        val light = AmbientLight(color = Color(colorHex), intensity = intensity)
        if (block != null) {
            light.block()
        }
        scene.add(light)
        return light
    }

    /**
     * Adds a [DirectionalLight] to the [scene].
     */
    fun directionalLight(
        colorHex: Int = 0xffffff,
        intensity: Float = 1f,
        x: Float = 0f,
        y: Float = 1f,
        z: Float = 0f,
        block: (DirectionalLight.() -> Unit)? = null
    ): DirectionalLight {
        val light = DirectionalLight(color = Color(colorHex), intensity = intensity)
        light.position.set(x, y, z)
        if (block != null) {
            light.block()
        }
        scene.add(light)
        return light
    }

    /**
     * Adds a [PointLight] to the [scene].
     */
    fun pointLight(
        colorHex: Int = 0xffffff,
        intensity: Float = 1f,
        distance: Float = 0f,
        decay: Float = 2f,
        block: (PointLight.() -> Unit)? = null
    ): PointLight {
        val light = PointLight(
            color = Color(colorHex),
            intensity = intensity,
            distance = distance,
            decay = decay
        )
        if (block != null) {
            light.block()
        }
        scene.add(light)
        return light
    }

    /**
     * Registers a per-frame animation callback invoked before rendering each VBlank frame.
     */
    fun onAnimate(handler: (deltaMs: Double, elapsedTimeMs: Double) -> Unit) {
        onBeforeRender = handler
    }
}

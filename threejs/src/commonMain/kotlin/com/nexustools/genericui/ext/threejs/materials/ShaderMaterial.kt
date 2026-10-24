package com.nexustools.genericui.ext.threejs.materials

/**
 * Custom GLSL vertex & fragment shader material (`THREE.ShaderMaterial`).
 */
class ShaderMaterial(
    var vertexShader: String = DEFAULT_VERTEX_SHADER,
    var fragmentShader: String = DEFAULT_FRAGMENT_SHADER,
    val uniforms: MutableMap<String, Any?> = LinkedHashMap()
) : Material() {
    override val type: String = "ShaderMaterial"

    companion object {
        const val DEFAULT_VERTEX_SHADER = """#version 300 es
precision highp float;
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;
uniform mat4 uModelViewProjection;
uniform mat3 uNormalMatrix;
out vec3 vNormal;
out vec2 vUv;
void main() {
    vNormal = normalize(uNormalMatrix * aNormal);
    vUv = aUv;
    gl_Position = uModelViewProjection * vec4(aPosition, 1.0);
}
"""

        const val DEFAULT_FRAGMENT_SHADER = """#version 300 es
precision highp float;
in vec3 vNormal;
in vec2 vUv;
uniform vec4 uBaseColor;
uniform vec3 uLightDirection;
uniform vec3 uAmbientColor;
out vec4 fragColor;
void main() {
    vec3 n = normalize(vNormal);
    float ndl = max(dot(n, normalize(uLightDirection)), 0.15);
    vec3 lit = uBaseColor.rgb * (uAmbientColor + vec3(ndl));
    fragColor = vec4(lit, uBaseColor.a);
}
"""
    }
}

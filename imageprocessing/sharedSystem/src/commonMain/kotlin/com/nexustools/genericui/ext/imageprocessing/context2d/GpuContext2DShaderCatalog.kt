package com.nexustools.genericui.ext.imageprocessing.context2d

/**
 * Shared system-target (`:extensions:imageprocessing:sharedSystem`) catalog of hardware
 * **Vulkan (`#version 450`)** and **OpenGL ES (`#version 300 es`)** shaders that accelerate
 * all 2D context operations in [GpuAcceleratedContext2DBackend].
 */
object GpuContext2DShaderCatalog {

    const val KEY_CLEAR_FILL_RECT: String = "ctx2d_clear_fill_rect"
    const val KEY_ANTIALIASED_SDF_LINE: String = "ctx2d_aa_sdf_line"
    const val KEY_ANTIALIASED_SDF_SHAPE: String = "ctx2d_aa_sdf_shape"
    const val KEY_ANTIALIASED_BRUSH_STAMP: String = "ctx2d_aa_brush_stamp"
    const val KEY_LINEAR_RADIAL_GRADIENT: String = "ctx2d_linear_radial_gradient"
    const val KEY_IMAGE_BLIT_SWIZZLE: String = "ctx2d_image_blit_swizzle"

    const val VULKAN_CLEAR_FILL_RECT_450: String = """
        #version 450
        layout(set = 0, binding = 0) uniform sampler2D uBaseTex;
        layout(push_constant) uniform PushConstants {
            vec4 uRectBounds;
            vec4 uColor;
            vec4 uResolutionAndMode;
        } pc;
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;
        void main() {
            vec2 px = vUv * pc.uResolutionAndMode.xy;
            vec4 base = texture(uBaseTex, vUv);
            if (px.x >= pc.uRectBounds.x && px.x <= pc.uRectBounds.z &&
                px.y >= pc.uRectBounds.y && px.y <= pc.uRectBounds.w) {
                if (pc.uResolutionAndMode.z > 0.5) {
                    outColor = pc.uColor;
                } else {
                    float a = pc.uColor.a;
                    outColor = vec4(mix(base.rgb, pc.uColor.rgb, a), a + base.a * (1.0 - a));
                }
            } else {
                outColor = base;
            }
        }
    """

    const val GLES_CLEAR_FILL_RECT_300: String = """
        #version 300 es
        precision highp float;
        uniform sampler2D uBaseTex;
        uniform vec4 uRectBounds;
        uniform vec4 uColor;
        uniform vec4 uResolutionAndMode;
        in vec2 vUv;
        out vec4 outColor;
        void main() {
            vec2 px = vUv * uResolutionAndMode.xy;
            vec4 base = texture(uBaseTex, vUv);
            if (px.x >= uRectBounds.x && px.x <= uRectBounds.z &&
                px.y >= uRectBounds.y && px.y <= uRectBounds.w) {
                if (uResolutionAndMode.z > 0.5) {
                    outColor = uColor;
                } else {
                    float a = uColor.a;
                    outColor = vec4(mix(base.rgb, uColor.rgb, a), a + base.a * (1.0 - a));
                }
            } else {
                outColor = base;
            }
        }
    """

    const val VULKAN_ANTIALIASED_SDF_LINE_450: String = """
        #version 450
        layout(set = 0, binding = 0) uniform sampler2D uBaseTex;
        layout(push_constant) uniform PushConstants {
            vec4 uEndpoints;
            vec4 uColor;
            vec4 uWidthAndAa;
        } pc;
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;
        float sdSegment(vec2 p, vec2 a, vec2 b) {
            vec2 pa = p - a, ba = b - a;
            float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-5), 0.0, 1.0);
            return length(pa - ba * h);
        }
        void main() {
            vec2 p = vUv * pc.uWidthAndAa.xy;
            vec4 base = texture(uBaseTex, vUv);
            float d = sdSegment(p, pc.uEndpoints.xy, pc.uEndpoints.zw);
            float halfW = pc.uWidthAndAa.z * 0.5;
            float feather = max(pc.uWidthAndAa.w, 0.001);
            float cov = 1.0 - smoothstep(halfW - feather, halfW + feather, d);
            float a = pc.uColor.a * cov;
            outColor = vec4(mix(base.rgb, pc.uColor.rgb, a), a + base.a * (1.0 - a));
        }
    """

    const val GLES_ANTIALIASED_SDF_LINE_300: String = """
        #version 300 es
        precision highp float;
        uniform sampler2D uBaseTex;
        uniform vec4 uEndpoints;
        uniform vec4 uColor;
        uniform vec4 uWidthAndAa;
        in vec2 vUv;
        out vec4 outColor;
        float sdSegment(vec2 p, vec2 a, vec2 b) {
            vec2 pa = p - a, ba = b - a;
            float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-5), 0.0, 1.0);
            return length(pa - ba * h);
        }
        void main() {
            vec2 p = vUv * uWidthAndAa.xy;
            vec4 base = texture(uBaseTex, vUv);
            float d = sdSegment(p, uEndpoints.xy, uEndpoints.zw);
            float halfW = uWidthAndAa.z * 0.5;
            float feather = max(uWidthAndAa.w, 0.001);
            float cov = 1.0 - smoothstep(halfW - feather, halfW + feather, d);
            float a = uColor.a * cov;
            outColor = vec4(mix(base.rgb, uColor.rgb, a), a + base.a * (1.0 - a));
        }
    """

    const val VULKAN_ANTIALIASED_SDF_SHAPE_450: String = """
        #version 450
        layout(set = 0, binding = 0) uniform sampler2D uBaseTex;
        layout(push_constant) uniform PushConstants {
            vec4 uCenterAndRadii;
            vec4 uColor;
            vec4 uCornerAndAa;
        } pc;
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;
        float sdRoundedBox(vec2 p, vec2 b, float r) {
            vec2 q = abs(p) - b + vec2(r);
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }
        void main() {
            vec2 p = vUv * pc.uCornerAndAa.xy - pc.uCenterAndRadii.xy;
            vec4 base = texture(uBaseTex, vUv);
            float d = sdRoundedBox(p, pc.uCenterAndRadii.zw, pc.uCornerAndAa.z);
            float cov = 1.0 - smoothstep(-pc.uCornerAndAa.w, pc.uCornerAndAa.w, d);
            float a = pc.uColor.a * cov;
            outColor = vec4(mix(base.rgb, pc.uColor.rgb, a), a + base.a * (1.0 - a));
        }
    """

    const val GLES_ANTIALIASED_SDF_SHAPE_300: String = """
        #version 300 es
        precision highp float;
        uniform sampler2D uBaseTex;
        uniform vec4 uCenterAndRadii;
        uniform vec4 uColor;
        uniform vec4 uCornerAndAa;
        in vec2 vUv;
        out vec4 outColor;
        float sdRoundedBox(vec2 p, vec2 b, float r) {
            vec2 q = abs(p) - b + vec2(r);
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }
        void main() {
            vec2 p = vUv * uCornerAndAa.xy - uCenterAndRadii.xy;
            vec4 base = texture(uBaseTex, vUv);
            float d = sdRoundedBox(p, uCenterAndRadii.zw, uCornerAndAa.z);
            float cov = 1.0 - smoothstep(-uCornerAndAa.w, uCornerAndAa.w, d);
            float a = uColor.a * cov;
            outColor = vec4(mix(base.rgb, uColor.rgb, a), a + base.a * (1.0 - a));
        }
    """

    const val VULKAN_ANTIALIASED_BRUSH_STAMP_450: String = """
        #version 450
        layout(set = 0, binding = 0) uniform sampler2D uBaseTex;
        layout(push_constant) uniform PushConstants {
            vec4 uSegment;
            vec4 uColor;
            vec4 uRadiusHardnessErase;
        } pc;
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;
        float sdSegment(vec2 p, vec2 a, vec2 b) {
            vec2 pa = p - a, ba = b - a;
            float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-5), 0.0, 1.0);
            return length(pa - ba * h);
        }
        void main() {
            vec2 p = vUv * pc.uRadiusHardnessErase.xy;
            vec4 base = texture(uBaseTex, vUv);
            float d = sdSegment(p, pc.uSegment.xy, pc.uSegment.zw);
            float r = max(pc.uRadiusHardnessErase.z, 0.5);
            float inner = r * clamp(pc.uRadiusHardnessErase.w, 0.0, 0.98);
            float cov = 1.0 - smoothstep(inner, r + 0.75, d);
            float a = pc.uColor.a * cov;
            outColor = vec4(mix(base.rgb, pc.uColor.rgb, a), a + base.a * (1.0 - a));
        }
    """

    const val GLES_ANTIALIASED_BRUSH_STAMP_300: String = """
        #version 300 es
        precision highp float;
        uniform sampler2D uBaseTex;
        uniform vec4 uSegment;
        uniform vec4 uColor;
        uniform vec4 uRadiusHardnessErase;
        in vec2 vUv;
        out vec4 outColor;
        float sdSegment(vec2 p, vec2 a, vec2 b) {
            vec2 pa = p - a, ba = b - a;
            float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-5), 0.0, 1.0);
            return length(pa - ba * h);
        }
        void main() {
            vec2 p = vUv * uRadiusHardnessErase.xy;
            vec4 base = texture(uBaseTex, vUv);
            float d = sdSegment(p, uSegment.xy, uSegment.zw);
            float r = max(uRadiusHardnessErase.z, 0.5);
            float inner = r * clamp(uRadiusHardnessErase.w, 0.0, 0.98);
            float cov = 1.0 - smoothstep(inner, r + 0.75, d);
            float a = uColor.a * cov;
            outColor = vec4(mix(base.rgb, uColor.rgb, a), a + base.a * (1.0 - a));
        }
    """

    const val VULKAN_LINEAR_RADIAL_GRADIENT_450: String = """
        #version 450
        layout(set = 0, binding = 0) uniform sampler2D uBaseTex;
        layout(push_constant) uniform PushConstants {
            vec4 uGradEndpoints;
            vec4 uStartColor;
            vec4 uEndColor;
        } pc;
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;
        void main() {
            vec2 a = pc.uGradEndpoints.xy;
            vec2 b = pc.uGradEndpoints.zw;
            vec2 ab = b - a;
            float t = clamp(dot(vUv - a, ab) / max(dot(ab, ab), 1e-5), 0.0, 1.0);
            outColor = mix(pc.uStartColor, pc.uEndColor, t);
        }
    """

    const val GLES_LINEAR_RADIAL_GRADIENT_300: String = """
        #version 300 es
        precision highp float;
        uniform sampler2D uBaseTex;
        uniform vec4 uGradEndpoints;
        uniform vec4 uStartColor;
        uniform vec4 uEndColor;
        in vec2 vUv;
        out vec4 outColor;
        void main() {
            vec2 a = uGradEndpoints.xy;
            vec2 b = uGradEndpoints.zw;
            vec2 ab = b - a;
            float t = clamp(dot(vUv - a, ab) / max(dot(ab, ab), 1e-5), 0.0, 1.0);
            outColor = mix(uStartColor, uEndColor, t);
        }
    """

    const val VULKAN_IMAGE_BLIT_SWIZZLE_450: String = """
        #version 450
        layout(set = 0, binding = 0) uniform sampler2D uSrcTex;
        layout(push_constant) uniform PushConstants {
            vec4 uSrcDestRect;
            vec4 uSwizzleAndAlpha;
        } pc;
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;
        void main() {
            vec4 c = texture(uSrcTex, vUv);
            if (pc.uSwizzleAndAlpha.x > 0.5) {
                outColor = vec4(c.bgr, c.a * pc.uSwizzleAndAlpha.y);
            } else {
                outColor = vec4(c.rgb, c.a * pc.uSwizzleAndAlpha.y);
            }
        }
    """

    const val GLES_IMAGE_BLIT_SWIZZLE_300: String = """
        #version 300 es
        precision highp float;
        uniform sampler2D uSrcTex;
        uniform vec4 uSwizzleAndAlpha;
        in vec2 vUv;
        out vec4 outColor;
        void main() {
            vec4 c = texture(uSrcTex, vUv);
            if (uSwizzleAndAlpha.x > 0.5) {
                outColor = vec4(c.bgr, c.a * uSwizzleAndAlpha.y);
            } else {
                outColor = vec4(c.rgb, c.a * uSwizzleAndAlpha.y);
            }
        }
    """
}

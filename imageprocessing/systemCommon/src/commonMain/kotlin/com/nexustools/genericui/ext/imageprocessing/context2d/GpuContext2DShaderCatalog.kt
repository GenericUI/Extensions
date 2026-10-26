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
    const val KEY_SDF_TEXT_GLYPH: String = "ctx2d_sdf_text_glyph"
    const val KEY_CONIC_MULTI_GRADIENT: String = "ctx2d_conic_multi_gradient"
    const val KEY_PATTERN_SAMPLER: String = "ctx2d_pattern_sampler"
    const val KEY_SHADOW_GAUSSIAN_BLUR: String = "ctx2d_shadow_gaussian_blur"
    const val KEY_COMPOSITE_BLEND_26: String = "ctx2d_composite_blend_26"
    const val KEY_PER_CORNER_ELLIPSE_SDF: String = "ctx2d_per_corner_ellipse_sdf"

    fun allProgramKeys(): List<String> = listOf(
        KEY_CLEAR_FILL_RECT,
        KEY_ANTIALIASED_SDF_LINE,
        KEY_ANTIALIASED_SDF_SHAPE,
        KEY_ANTIALIASED_BRUSH_STAMP,
        KEY_LINEAR_RADIAL_GRADIENT,
        KEY_IMAGE_BLIT_SWIZZLE,
        KEY_SDF_TEXT_GLYPH,
        KEY_CONIC_MULTI_GRADIENT,
        KEY_PATTERN_SAMPLER,
        KEY_SHADOW_GAUSSIAN_BLUR,
        KEY_COMPOSITE_BLEND_26,
        KEY_PER_CORNER_ELLIPSE_SDF
    )

    fun vulkanSourceForKey(key: String): String = when (key) {
        KEY_CLEAR_FILL_RECT -> VULKAN_CLEAR_FILL_RECT_450
        KEY_ANTIALIASED_SDF_LINE -> VULKAN_ANTIALIASED_SDF_LINE_450
        KEY_ANTIALIASED_SDF_SHAPE -> VULKAN_ANTIALIASED_SDF_SHAPE_450
        KEY_ANTIALIASED_BRUSH_STAMP -> VULKAN_ANTIALIASED_BRUSH_STAMP_450
        KEY_LINEAR_RADIAL_GRADIENT -> VULKAN_LINEAR_RADIAL_GRADIENT_450
        KEY_IMAGE_BLIT_SWIZZLE -> VULKAN_IMAGE_BLIT_SWIZZLE_450
        KEY_SDF_TEXT_GLYPH -> VULKAN_SDF_TEXT_GLYPH_450
        KEY_CONIC_MULTI_GRADIENT -> VULKAN_CONIC_MULTI_GRADIENT_450
        KEY_PATTERN_SAMPLER -> VULKAN_PATTERN_SAMPLER_450
        KEY_SHADOW_GAUSSIAN_BLUR -> VULKAN_SHADOW_GAUSSIAN_BLUR_450
        KEY_COMPOSITE_BLEND_26 -> VULKAN_COMPOSITE_BLEND_26_450
        KEY_PER_CORNER_ELLIPSE_SDF -> VULKAN_PER_CORNER_ELLIPSE_SDF_450
        else -> VULKAN_CLEAR_FILL_RECT_450
    }

    fun glesSourceForKey(key: String): String = when (key) {
        KEY_CLEAR_FILL_RECT -> GLES_CLEAR_FILL_RECT_300
        KEY_ANTIALIASED_SDF_LINE -> GLES_ANTIALIASED_SDF_LINE_300
        KEY_ANTIALIASED_SDF_SHAPE -> GLES_ANTIALIASED_SDF_SHAPE_300
        KEY_ANTIALIASED_BRUSH_STAMP -> GLES_ANTIALIASED_BRUSH_STAMP_300
        KEY_LINEAR_RADIAL_GRADIENT -> GLES_LINEAR_RADIAL_GRADIENT_300
        KEY_IMAGE_BLIT_SWIZZLE -> GLES_IMAGE_BLIT_SWIZZLE_300
        KEY_SDF_TEXT_GLYPH -> GLES_SDF_TEXT_GLYPH_300
        KEY_CONIC_MULTI_GRADIENT -> GLES_CONIC_MULTI_GRADIENT_300
        KEY_PATTERN_SAMPLER -> GLES_PATTERN_SAMPLER_300
        KEY_SHADOW_GAUSSIAN_BLUR -> GLES_SHADOW_GAUSSIAN_BLUR_300
        KEY_COMPOSITE_BLEND_26 -> GLES_COMPOSITE_BLEND_26_300
        KEY_PER_CORNER_ELLIPSE_SDF -> GLES_PER_CORNER_ELLIPSE_SDF_300
        else -> GLES_CLEAR_FILL_RECT_300
    }

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

    const val VULKAN_SDF_TEXT_GLYPH_450: String = """
        #version 450
        layout(set = 0, binding = 0) uniform sampler2D uGlyphSdfTex;
        layout(push_constant) uniform PushConstants {
            vec4 uColor;
            vec4 uSdfParams;
        } pc;
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;
        void main() {
            float dist = texture(uGlyphSdfTex, vUv).r;
            float edge = pc.uSdfParams.x;
            float feather = max(pc.uSdfParams.y, 0.001);
            float cov = smoothstep(edge - feather, edge + feather, dist);
            outColor = vec4(pc.uColor.rgb, pc.uColor.a * cov);
        }
    """

    const val GLES_SDF_TEXT_GLYPH_300: String = """
        #version 300 es
        precision highp float;
        uniform sampler2D uGlyphSdfTex;
        uniform vec4 uColor;
        uniform vec4 uSdfParams;
        in vec2 vUv;
        out vec4 outColor;
        void main() {
            float dist = texture(uGlyphSdfTex, vUv).r;
            float edge = uSdfParams.x;
            float feather = max(uSdfParams.y, 0.001);
            float cov = smoothstep(edge - feather, edge + feather, dist);
            outColor = vec4(uColor.rgb, uColor.a * cov);
        }
    """

    const val VULKAN_CONIC_MULTI_GRADIENT_450: String = """
        #version 450
        layout(set = 0, binding = 0) uniform sampler2D uGradientRampTex;
        layout(push_constant) uniform PushConstants {
            vec4 uCenterAndStartAngle;
            vec4 uRadiiAndKind;
        } pc;
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;
        void main() {
            vec2 d = vUv - pc.uCenterAndStartAngle.xy;
            float angle = atan(d.y, d.x) - pc.uCenterAndStartAngle.z;
            float t = fract(angle / 6.28318530718);
            outColor = texture(uGradientRampTex, vec2(t, 0.5));
        }
    """

    const val GLES_CONIC_MULTI_GRADIENT_300: String = """
        #version 300 es
        precision highp float;
        uniform sampler2D uGradientRampTex;
        uniform vec4 uCenterAndStartAngle;
        uniform vec4 uRadiiAndKind;
        in vec2 vUv;
        out vec4 outColor;
        void main() {
            vec2 d = vUv - uCenterAndStartAngle.xy;
            float angle = atan(d.y, d.x) - uCenterAndStartAngle.z;
            float t = fract(angle / 6.28318530718);
            outColor = texture(uGradientRampTex, vec2(t, 0.5));
        }
    """

    const val VULKAN_PATTERN_SAMPLER_450: String = """
        #version 450
        layout(set = 0, binding = 0) uniform sampler2D uPatternTex;
        layout(push_constant) uniform PushConstants {
            vec4 uPatternScaleAndRepeat;
            vec4 uAlphaAndTransform;
        } pc;
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;
        void main() {
            vec2 uv = vUv * pc.uPatternScaleAndRepeat.xy;
            float repX = pc.uPatternScaleAndRepeat.z;
            float repY = pc.uPatternScaleAndRepeat.w;
            if ((repX < 0.5 && (uv.x < 0.0 || uv.x > 1.0)) ||
                (repY < 0.5 && (uv.y < 0.0 || uv.y > 1.0))) {
                outColor = vec4(0.0);
            } else {
                vec4 c = texture(uPatternTex, fract(uv));
                outColor = vec4(c.rgb, c.a * pc.uAlphaAndTransform.x);
            }
        }
    """

    const val GLES_PATTERN_SAMPLER_300: String = """
        #version 300 es
        precision highp float;
        uniform sampler2D uPatternTex;
        uniform vec4 uPatternScaleAndRepeat;
        uniform vec4 uAlphaAndTransform;
        in vec2 vUv;
        out vec4 outColor;
        void main() {
            vec2 uv = vUv * uPatternScaleAndRepeat.xy;
            float repX = uPatternScaleAndRepeat.z;
            float repY = uPatternScaleAndRepeat.w;
            if ((repX < 0.5 && (uv.x < 0.0 || uv.x > 1.0)) ||
                (repY < 0.5 && (uv.y < 0.0 || uv.y > 1.0))) {
                outColor = vec4(0.0);
            } else {
                vec4 c = texture(uPatternTex, fract(uv));
                outColor = vec4(c.rgb, c.a * uAlphaAndTransform.x);
            }
        }
    """

    const val VULKAN_SHADOW_GAUSSIAN_BLUR_450: String = """
        #version 450
        layout(set = 0, binding = 0) uniform sampler2D uMaskTex;
        layout(push_constant) uniform PushConstants {
            vec4 uDirectionAndSigma;
            vec4 uShadowColor;
        } pc;
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;
        void main() {
            vec2 stepUv = pc.uDirectionAndSigma.xy;
            float a = texture(uMaskTex, vUv).a * 0.227027;
            a += texture(uMaskTex, vUv + stepUv * 1.384615).a * 0.316216;
            a += texture(uMaskTex, vUv - stepUv * 1.384615).a * 0.316216;
            a += texture(uMaskTex, vUv + stepUv * 3.230769).a * 0.070270;
            a += texture(uMaskTex, vUv - stepUv * 3.230769).a * 0.070270;
            outColor = vec4(pc.uShadowColor.rgb, pc.uShadowColor.a * a);
        }
    """

    const val GLES_SHADOW_GAUSSIAN_BLUR_300: String = """
        #version 300 es
        precision highp float;
        uniform sampler2D uMaskTex;
        uniform vec4 uDirectionAndSigma;
        uniform vec4 uShadowColor;
        in vec2 vUv;
        out vec4 outColor;
        void main() {
            vec2 stepUv = uDirectionAndSigma.xy;
            float a = texture(uMaskTex, vUv).a * 0.227027;
            a += texture(uMaskTex, vUv + stepUv * 1.384615).a * 0.316216;
            a += texture(uMaskTex, vUv - stepUv * 1.384615).a * 0.316216;
            a += texture(uMaskTex, vUv + stepUv * 3.230769).a * 0.070270;
            a += texture(uMaskTex, vUv - stepUv * 3.230769).a * 0.070270;
            outColor = vec4(uShadowColor.rgb, uShadowColor.a * a);
        }
    """

    const val VULKAN_COMPOSITE_BLEND_26_450: String = """
        #version 450
        layout(set = 0, binding = 0) uniform sampler2D uSrcTex;
        layout(set = 0, binding = 1) uniform sampler2D uDstTex;
        layout(push_constant) uniform PushConstants {
            vec4 uModeAndAlpha;
        } pc;
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;
        void main() {
            vec4 s = texture(uSrcTex, vUv);
            vec4 d = texture(uDstTex, vUv);
            s.a *= pc.uModeAndAlpha.y;
            float sa = s.a;
            float da = d.a;
            int mode = int(pc.uModeAndAlpha.x + 0.5);
            if (mode == 7) {
                outColor = s;
            } else if (mode == 10) {
                outColor = clamp(s * sa + d * da, 0.0, 1.0);
            } else if (mode == 11) {
                outColor = vec4(s.rgb * d.rgb, sa + da - sa * da);
            } else if (mode == 12) {
                outColor = vec4(1.0 - (1.0 - s.rgb) * (1.0 - d.rgb), sa + da - sa * da);
            } else {
                float outA = sa + da * (1.0 - sa);
                vec3 outRgb = outA > 1e-5 ? (s.rgb * sa + d.rgb * da * (1.0 - sa)) / outA : vec3(0.0);
                outColor = vec4(outRgb, outA);
            }
        }
    """

    const val GLES_COMPOSITE_BLEND_26_300: String = """
        #version 300 es
        precision highp float;
        uniform sampler2D uSrcTex;
        uniform sampler2D uDstTex;
        uniform vec4 uModeAndAlpha;
        in vec2 vUv;
        out vec4 outColor;
        void main() {
            vec4 s = texture(uSrcTex, vUv);
            vec4 d = texture(uDstTex, vUv);
            s.a *= uModeAndAlpha.y;
            float sa = s.a;
            float da = d.a;
            int mode = int(uModeAndAlpha.x + 0.5);
            if (mode == 7) {
                outColor = s;
            } else if (mode == 10) {
                outColor = clamp(s * sa + d * da, 0.0, 1.0);
            } else if (mode == 11) {
                outColor = vec4(s.rgb * d.rgb, sa + da - sa * da);
            } else if (mode == 12) {
                outColor = vec4(1.0 - (1.0 - s.rgb) * (1.0 - d.rgb), sa + da - sa * da);
            } else {
                float outA = sa + da * (1.0 - sa);
                vec3 outRgb = outA > 1e-5 ? (s.rgb * sa + d.rgb * da * (1.0 - sa)) / outA : vec3(0.0);
                outColor = vec4(outRgb, outA);
            }
        }
    """

    const val VULKAN_PER_CORNER_ELLIPSE_SDF_450: String = """
        #version 450
        layout(set = 0, binding = 0) uniform sampler2D uBaseTex;
        layout(push_constant) uniform PushConstants {
            vec4 uCenterAndHalfSize;
            vec4 uCornerRadii;
            vec4 uColor;
        } pc;
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;
        float sdPerCornerBox(vec2 p, vec2 b, vec4 r) {
            r.xy = (p.x > 0.0) ? r.yz : r.xw;
            r.x  = (p.y > 0.0) ? r.y  : r.x;
            vec2 q = abs(p) - b + r.x;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
        }
        void main() {
            vec2 p = vUv - pc.uCenterAndHalfSize.xy;
            float d = sdPerCornerBox(p, pc.uCenterAndHalfSize.zw, pc.uCornerRadii);
            float cov = 1.0 - smoothstep(-0.75, 0.75, d);
            outColor = vec4(pc.uColor.rgb, pc.uColor.a * cov);
        }
    """

    const val GLES_PER_CORNER_ELLIPSE_SDF_300: String = """
        #version 300 es
        precision highp float;
        uniform sampler2D uBaseTex;
        uniform vec4 uCenterAndHalfSize;
        uniform vec4 uCornerRadii;
        uniform vec4 uColor;
        in vec2 vUv;
        out vec4 outColor;
        float sdPerCornerBox(vec2 p, vec2 b, vec4 r) {
            r.xy = (p.x > 0.0) ? r.yz : r.xw;
            r.x  = (p.y > 0.0) ? r.y  : r.x;
            vec2 q = abs(p) - b + r.x;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
        }
        void main() {
            vec2 p = vUv - uCenterAndHalfSize.xy;
            float d = sdPerCornerBox(p, uCenterAndHalfSize.zw, uCornerRadii);
            float cov = 1.0 - smoothstep(-0.75, 0.75, d);
            outColor = vec4(uColor.rgb, uColor.a * cov);
        }
    """
}

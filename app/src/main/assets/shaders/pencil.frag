#version 300 es
precision highp float;

in highp vec2 v_normal;
in highp vec4 v_color;
in highp float v_tone;
in highp vec2 v_worldPos;
in highp vec2 v_toothCoord;
in highp vec2 v_toothCoordRot;

uniform sampler2D u_ToothTexture;

uniform float u_BleedRatio;
uniform float u_BleedPower;
uniform float u_CoreShiftRatio;
uniform float u_BleedBaseOpacity;
uniform float u_AaWidth;
uniform float u_PaperGrainMix;
uniform int u_InvertColors;

// Physical Wet Ink Simulation Parameters
uniform float u_InkDilution;
uniform float u_FiberFeathering;
uniform float u_MeniscusRim;
uniform float u_WetSheen;
uniform float u_ChromaHalo;

// Finalized Paper Tooth Shader Constants (Velvet Soft)
const highp float TOOTH_SHARPNESS = 0.050;
const highp float TOOTH_BITE = 0.72;
const highp float DUAL_SAMPLE_MIX = 0.26;

out vec4 fragColor;

vec3 getUndertone(vec3 base) {
    float maxC = max(base.r, max(base.g, base.b));
    if (maxC < 0.2) return vec3(0.08, 0.14, 0.28);
    if (base.b > base.r && base.b > base.g) return vec3(0.0, 0.45, 0.70);
    if (base.r > base.b) return vec3(0.75, 0.30, 0.05);
    return base * 1.2;
}

void main() {
    // 1. Screen-space pixel derivatives of dab normal (evaluated uniformly across fragment quad)
    vec2 dX = dFdx(v_normal);
    vec2 dY = dFdy(v_normal);
    float delta = sqrt(0.5 * (dot(dX, dX) + dot(dY, dY)));

    float distSq = dot(v_normal, v_normal);
    if (distSq > 1.0) discard;
    float dist = sqrt(distSq);

    float distFromEdge = 1.0 - dist;
    float finalAlpha;
    vec3 outRgb = v_color.rgb;

    if (v_tone < 2.5) {
        // --- GRAPHITE PENCIL LEAD ---
        // Crisp 1.25 physical screen pixel analytical anti-aliasing
        float aaWidth = max(delta * 1.25, 1e-4);
        float t = clamp(distFromEdge / aaWidth, 0.0, 1.0);
        float aaEdge = t * t * (3.0 - 2.0 * t);

        float edgeFalloff = smoothstep(1.0, 0.35, dist);

        // Continuous organic paper tooth noise with pre-interpolated dual-sampled UVs (zero per-pixel matrix math)
        float grain1 = texture(u_ToothTexture, v_toothCoord).r;
        float grain2 = texture(u_ToothTexture, v_toothCoordRot).r;
        float grain = mix(grain1, grain2, DUAL_SAMPLE_MIX);

        float density = clamp(v_tone, 0.0, 1.0);

        float minThreshold = mix(0.18, 0.13, density);
        float maxThreshold = mix(0.55, 0.48, density);
        float toothThreshold = mix(maxThreshold, minThreshold, v_color.a);
        float toothWidth = mix(TOOTH_SHARPNESS * 1.1, TOOTH_SHARPNESS * 0.95, density);

        float toothAbrasion = smoothstep(toothThreshold - toothWidth, toothThreshold + toothWidth, grain);
        float effectiveAbrasion = mix(1.0, toothAbrasion, TOOTH_BITE);
        finalAlpha = v_color.a * edgeFalloff * effectiveAbrasion * aaEdge;
    } else if (v_tone < 5.0) {
        // --- FOUNTAIN PEN LIQUID INK ---
        // Outward capillary ink bleed into paper fibers:
        // v_tone encodes (3.0 + nominalRatio), where nominalRatio = R_nominal / R_quad in [0.05, 0.95].
        float nominalDist = clamp(v_tone - 3.0, 0.05, 0.95);
        float safeDelta = max(delta, 1e-4);

        // Core shift: relative fraction of nominal stroke radius
        float coreShiftDist = nominalDist * u_CoreShiftRatio;
        float effectiveNominalDist = clamp(nominalDist + coreShiftDist, 0.01, 0.98);

        // 1. Fiber Feathering along paper grain (using pre-interpolated UVs)
        float paperGrain = texture(u_ToothTexture, v_toothCoord).r;
        float fiberPerturb = (paperGrain - 0.5) * u_FiberFeathering * effectiveNominalDist;
        float perturbedDist = dist + fiberPerturb;

        float distFromNominal = perturbedDist - effectiveNominalDist;

        // Tunable wet ink dilution
        float coreAlpha = v_color.a * u_InkDilution;

        if (distFromNominal <= 0.0) {
            // Inside nominal core
            float coreRatio = clamp(perturbedDist / effectiveNominalDist, 0.0, 1.0);

            // Meniscus surface-tension rim darkening (Coffee-ring effect)
            float rimDarkening = smoothstep(0.65, 0.98, coreRatio) * u_MeniscusRim;
            finalAlpha = coreAlpha * (1.0 + rimDarkening * 0.55);
            outRgb = mix(outRgb, outRgb * 0.70, rimDarkening * 0.40);

            // Wet liquid specular sheen along centerline disabled for subtractive ink to prevent core washout
        } else {
            // Relative bleed width: fraction of nominal stroke radius
            float maxBleed = max(1.0 - effectiveNominalDist - safeDelta * 0.5, 1e-4);
            float bleedWidth = min(effectiveNominalDist * max(u_BleedRatio, 0.0), maxBleed);

            // Sub-pixel AA band width in dab coordinates
            float aaBand = max(safeDelta * u_AaWidth, 1e-4);

            if (bleedWidth <= aaBand * 0.5) {
                // Virtually zero bleed: crisp analytical sub-pixel edge anti-aliasing
                float t = clamp((effectiveNominalDist - perturbedDist) / aaBand + 1.0, 0.0, 1.0);
                float smoothAa = t * t * (3.0 - 2.0 * t);
                finalAlpha = coreAlpha * smoothAa;
            } else {
                if (distFromNominal >= bleedWidth) {
                    discard;
                } else {
                    float u = distFromNominal / bleedWidth;
                    float t = 1.0 - u;
                    float bleedFalloff = pow(max(t, 0.0), max(u_BleedPower, 0.1)) * u_BleedBaseOpacity;

                    // Smooth outer edge anti-aliasing in screen pixels
                    float distFromBleedEdge = bleedWidth - distFromNominal;
                    float aaEdge = clamp(distFromBleedEdge / aaBand, 0.0, 1.0);
                    float smoothAa = aaEdge * aaEdge * (3.0 - 2.0 * aaEdge);

                    // Paper tooth grain mix
                    if (u_PaperGrainMix > 0.001) {
                        float toothFactor = mix(1.0, smoothstep(0.2, 0.8, paperGrain), u_PaperGrainMix);
                        bleedFalloff *= toothFactor;
                    }

                    // Chromatographic dye separation halo at fringe
                    if (u_ChromaHalo > 0.001) {
                        vec3 undertone = getUndertone(v_color.rgb);
                        outRgb = mix(outRgb, undertone, u_ChromaHalo * pow(u, 1.5));
                    }

                    finalAlpha = coreAlpha * bleedFalloff * smoothAa;
                }
            }
        }
    } else {
        // --- SOLID CONTINUOUS HIGHLIGHTER ---
        // Clean, uniform stroke with cubic Hermite analytical sub-pixel anti-aliased edge
        float aaWidth = max(delta * 1.25, 1e-4);
        float t = clamp((1.0 - dist) / aaWidth, 0.0, 1.0);
        float aaEdge = t * t * (3.0 - 2.0 * t);
        finalAlpha = v_color.a * aaEdge;
        outRgb = v_color.rgb;
    }

    if (finalAlpha <= 0.0001) discard;

    if (u_InvertColors != 0) {
        outRgb = vec3(1.0) - outRgb;
    }

    // Premultiplied alpha output
    fragColor = vec4(outRgb * finalAlpha, finalAlpha);
}

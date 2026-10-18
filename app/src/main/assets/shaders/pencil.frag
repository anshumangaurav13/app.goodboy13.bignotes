#version 300 es
precision highp float;

in highp vec2 v_normal;
in highp vec4 v_color;
in highp float v_tone;
in highp vec2 v_worldPos;

uniform sampler2D u_ToothTexture;

uniform float u_BleedRatio;
uniform float u_BleedPower;
uniform float u_CoreShiftRatio;
uniform float u_BleedBaseOpacity;
uniform float u_AaWidth;
uniform float u_PaperGrainMix;
uniform int u_InvertColors;

// Hardcoded Wet Ink Simulation Constant (Dilution = 10%)
const highp float INK_DILUTION = 0.10;

// Finalized Paper Tooth Shader Constants (Velvet Soft)
const highp float TEXTURE_SCALE = 0.183;
const highp float TOOTH_SHARPNESS = 0.050;
const highp float TOOTH_BITE = 0.72;
const highp float DUAL_SAMPLE_MIX = 0.26;

out vec4 fragColor;

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

        // Continuous organic paper tooth noise with golden-ratio rotated dual-sampling
        vec2 tc1 = v_worldPos * TEXTURE_SCALE;
        // Rotation by 23.5 degrees: cos=0.917, sin=0.399
        mat2 rot = mat2(0.917, 0.399, -0.399, 0.917);
        vec2 tc2 = (rot * v_worldPos) * (TEXTURE_SCALE * 1.61803) + vec2(0.371, 0.618);
        float grain1 = texture(u_ToothTexture, tc1).r;
        float grain2 = texture(u_ToothTexture, tc2).r;
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

        float distFromNominal = dist - effectiveNominalDist;

        // Hardcoded wet ink dilution (10% single-pass pigment)
        float coreAlpha = v_color.a * INK_DILUTION;

        if (distFromNominal <= 0.0) {
            // Inside nominal core
            finalAlpha = coreAlpha;
        } else {
            // Relative bleed width: fraction of nominal stroke radius
            float maxBleed = max(1.0 - effectiveNominalDist - safeDelta * 0.5, 1e-4);
            float bleedWidth = min(effectiveNominalDist * max(u_BleedRatio, 0.0), maxBleed);

            // Sub-pixel AA band width in dab coordinates
            float aaBand = max(safeDelta * u_AaWidth, 1e-4);

            if (bleedWidth <= aaBand * 0.5) {
                // Virtually zero bleed: crisp analytical sub-pixel edge anti-aliasing
                float t = clamp((effectiveNominalDist - dist) / aaBand + 1.0, 0.0, 1.0);
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

                    // Optional paper tooth grain mix
                    if (u_PaperGrainMix > 0.001) {
                        vec2 tc1 = v_worldPos * TEXTURE_SCALE;
                        float grain = texture(u_ToothTexture, tc1).r;
                        float toothFactor = mix(1.0, smoothstep(0.2, 0.8, grain), u_PaperGrainMix);
                        bleedFalloff *= toothFactor;
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

    if (finalAlpha <= 0.001) discard;

    if (u_InvertColors != 0) {
        outRgb = vec3(1.0) - outRgb;
    }

    // Premultiplied alpha output
    fragColor = vec4(outRgb * finalAlpha, finalAlpha);
}

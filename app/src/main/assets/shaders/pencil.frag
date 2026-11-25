#version 300 es
precision highp float;

in highp vec2 v_worldPos;
in highp vec2 v_segStart;
in highp vec2 v_segEnd;
in highp vec4 v_radii;
in highp vec4 v_color;
in highp float v_tone;
in highp vec4 v_trims;
in highp vec3 v_nib;
in highp vec2 v_toothCoord;
in highp vec2 v_toothCoordRot;

uniform sampler2D u_ToothTexture; // R = coarse paper tooth, G = fine cellulose fibers

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
    // 1. Evaluate world pixel derivatives before branching or discard (uniform across 2x2 helper quad)
    vec2 dxWorld = dFdx(v_worldPos);
    vec2 dyWorld = dFdy(v_worldPos);
    float pxToWorld = sqrt(0.5 * (dot(dxWorld, dxWorld) + dot(dyWorld, dyWorld)));

    // 2. Half-space joint partitioning to guarantee zero intra-stroke overlap across adjacent segments
    vec2 p0 = v_worldPos - v_segStart;
    vec2 p1 = v_worldPos - v_segEnd;
    if (dot(v_trims.xy, v_trims.xy) > 0.25 && dot(p0, v_trims.xy) < 0.0) discard;
    if (dot(v_trims.zw, v_trims.zw) > 0.25 && dot(p1, v_trims.zw) >= 0.0) discard;

    // 3. Tapered Capsule SDF along [v_segStart, v_segEnd]
    vec2 ba = v_segEnd - v_segStart;
    float baLenSq = dot(ba, ba);
    float h = (baLenSq > 1e-6) ? clamp(dot(p0, ba) / baLenSq, 0.0, 1.0) : 0.0;
    vec2 dVec = p0 - ba * h;

    float R = max(mix(v_radii.x, v_radii.y, h), 1e-4);
    float rNom = max(mix(v_radii.z, v_radii.w, h), 1e-4);

    // 4. Broadside tilt shading for graphite pencil (fountain pen & highlighter keep a round tip)
    float tilt = clamp(v_nib.z, 0.0, 1.0);
    float rawDistWorld;
    if (tilt > 0.01 && v_tone < 2.5) {
        vec2 nibDir = (dot(v_nib.xy, v_nib.xy) > 0.01) ? normalize(v_nib.xy) : vec2(1.0, 0.0);
        vec2 nibPerp = vec2(-nibDir.y, nibDir.x);
        // Broadside graphite contact ellipse expands wider with tilt
        float alongTilt = dot(dVec, nibDir);
        float acrossTilt = dot(dVec, nibPerp);
        rawDistWorld = length(vec2(
            alongTilt / (1.0 + 1.15 * tilt),
            acrossTilt / (1.0 + 0.35 * tilt)
        ));
    } else {
        rawDistWorld = length(dVec);
    }

    float dist = rawDistWorld / R;
    if (dist > 1.0) discard;

    float delta = pxToWorld / R;
    float distFromEdge = 1.0 - dist;
    float finalAlpha;

    // Resolve base display color in output color space BEFORE rim shading so dark mode
    // concentrates meniscus contrast accurately instead of inverting into a bright fringe.
    vec3 displayBase = (u_InvertColors != 0) ? (vec3(1.0) - v_color.rgb) : v_color.rgb;
    vec3 outRgb = displayBase;

    if (v_tone < 2.5) {
        // --- GRAPHITE PENCIL LEAD ---
        float aaWidth = max(delta * 1.25, 1e-4);
        float t = clamp(distFromEdge / aaWidth, 0.0, 1.0);
        float aaEdge = t * t * (3.0 - 2.0 * t);

        float edgeFalloff = smoothstep(1.0, 0.32, dist);

        // Multi-scale RG paper tooth + fine cellulose fibers
        vec2 texA = texture(u_ToothTexture, v_toothCoord).rg;
        vec2 texB = texture(u_ToothTexture, v_toothCoordRot).rg;
        float coarseGrain = mix(texA.r, texB.r, DUAL_SAMPLE_MIX);
        float fineGrain = mix(texA.g, texB.g, 0.40);
        float grain = mix(coarseGrain, fineGrain, 0.22);

        float density = clamp(v_tone, 0.0, 1.0);
        float tiltFade = 1.0 - 0.38 * tilt;
        float effBaseAlpha = v_color.a * tiltFade;

        float minThreshold = mix(0.20, 0.14, density);
        float maxThreshold = mix(0.58, 0.50, density);
        float toothThreshold = mix(maxThreshold, minThreshold, effBaseAlpha);
        float toothWidth = mix(TOOTH_SHARPNESS * 1.1, TOOTH_SHARPNESS * 0.95, density);

        float toothAbrasion = smoothstep(toothThreshold - toothWidth, toothThreshold + toothWidth, grain);
        float effectiveAbrasion = mix(1.0, toothAbrasion, TOOTH_BITE);
        finalAlpha = effBaseAlpha * edgeFalloff * effectiveAbrasion * aaEdge;
    } else if (v_tone < 5.0) {
        // --- FOUNTAIN PEN LIQUID INK ---
        float nominalDist = clamp(rNom / R, 0.05, 0.96);
        float safeDelta = max(delta, 1e-4);

        // Dwell factor carried in v_color.a (slow corners > 1.0 pool slightly darker & bleed wider; fast < 1.0 crisp)
        float dwellFactor = clamp(v_color.a, 0.80, 1.25);
        float dwellNorm = clamp((dwellFactor - 0.88) / 0.28, 0.0, 1.0);

        float coreShiftDist = nominalDist * u_CoreShiftRatio;
        float effectiveNominalDist = clamp(nominalDist + coreShiftDist, 0.01, 0.98);

        // 1. Multi-scale Fiber Feathering (coarse paper tooth + fine directional cellulose fibers)
        vec2 inkTexA = texture(u_ToothTexture, v_toothCoord).rg;
        float fineFiber = texture(u_ToothTexture, v_toothCoordRot * 1.45).g;
        float paperGrain = mix(inkTexA.r, mix(inkTexA.g, fineFiber, 0.55), 0.55);

        float fiberPerturb = (paperGrain - 0.5) * u_FiberFeathering * effectiveNominalDist;
        float perturbedDist = dist + fiberPerturb;

        float distFromNominal = perturbedDist - effectiveNominalDist;

        // Tunable wet ink dilution modulated by stylus dwell
        float coreAlpha = clamp(u_InkDilution * dwellFactor, 0.02, 1.0);

        if (distFromNominal <= 0.0) {
            float coreRatio = clamp(perturbedDist / effectiveNominalDist, 0.0, 1.0);

            // Meniscus surface-tension rim darkening (Coffee-ring effect)
            float rimDarkening = smoothstep(0.62, 0.98, coreRatio) * u_MeniscusRim;
            finalAlpha = clamp(coreAlpha * (1.0 + rimDarkening * 0.45), 0.0, 1.0);
            if (u_InvertColors == 0) {
                outRgb = mix(displayBase, displayBase * 0.70, rimDarkening * 0.40);
            } else {
                // Dark paper: enrich/saturate edge gently without brightening into a white glow
                outRgb = mix(displayBase, displayBase * 0.84, rimDarkening * 0.35);
            }
        } else {
            // Capillary bleed modulated gently by stylus dwell at corners/terminations
            float dwellBleedRatio = max(u_BleedRatio, 0.0) * mix(0.86, 1.18, dwellNorm);
            float maxBleed = max(1.0 - effectiveNominalDist - safeDelta * 0.5, 1e-4);
            float bleedWidth = min(effectiveNominalDist * dwellBleedRatio, maxBleed);

            float aaBand = max(safeDelta * u_AaWidth, 1e-4);

            if (bleedWidth <= aaBand * 0.5) {
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

                    float distFromBleedEdge = bleedWidth - distFromNominal;
                    float aaEdge = clamp(distFromBleedEdge / aaBand, 0.0, 1.0);
                    float smoothAa = aaEdge * aaEdge * (3.0 - 2.0 * aaEdge);

                    if (u_PaperGrainMix > 0.001) {
                        float macroTooth = smoothstep(0.18, 0.82, mix(inkTexA.r, fineFiber, 0.35));
                        float toothFactor = mix(1.0, macroTooth, u_PaperGrainMix);
                        bleedFalloff *= toothFactor;
                    }

                    if (u_ChromaHalo > 0.001) {
                        vec3 undertone = getUndertone(v_color.rgb);
                        if (u_InvertColors != 0) {
                            undertone = vec3(1.0) - undertone;
                        }
                        outRgb = mix(displayBase, undertone, u_ChromaHalo * pow(u, 1.5));
                    }

                    finalAlpha = coreAlpha * bleedFalloff * smoothAa;
                }
            }
        }
    } else {
        // --- SOLID CONTINUOUS HIGHLIGHTER ---
        float aaWidth = max(delta * 1.25, 1e-4);
        float t = clamp((1.0 - dist) / aaWidth, 0.0, 1.0);
        float aaEdge = t * t * (3.0 - 2.0 * t);
        finalAlpha = v_color.a * aaEdge;
    }

    if (finalAlpha <= 0.0001) discard;

    // Premultiplied alpha output
    fragColor = vec4(outRgb * finalAlpha, finalAlpha);
}

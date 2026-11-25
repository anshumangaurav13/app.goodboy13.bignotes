#version 300 es
precision highp float;

layout(location = 0) in vec2 a_position;
layout(location = 1) in vec2 a_segStart;
layout(location = 2) in vec2 a_segEnd;
layout(location = 3) in vec4 a_radii;   // (R0, R1, nominalR0, nominalR1)
layout(location = 4) in vec4 a_color;   // (r, g, b, alpha/dwell)
layout(location = 5) in float a_tone;   // <2.5 Graphite, [2.5..5.0) Ink, >=5.0 Highlighter
layout(location = 6) in vec4 a_trims;   // (m0x, m0y, m1x, m1y)
layout(location = 7) in vec3 a_nib;     // (nibX, nibY, tilt)

uniform mat4 u_MVP;

out highp vec2 v_worldPos;
out highp vec2 v_segStart;
out highp vec2 v_segEnd;
out highp vec4 v_radii;
out highp vec4 v_color;
out highp float v_tone;
out highp vec4 v_trims;
out highp vec3 v_nib;
out highp vec2 v_toothCoord;
out highp vec2 v_toothCoordRot;

const highp float TEXTURE_SCALE = 0.183;
const mat2 ROT_23_5 = mat2(0.917, 0.399, -0.399, 0.917);

void main() {
    v_worldPos = a_position;
    v_segStart = a_segStart;
    v_segEnd = a_segEnd;
    v_radii = a_radii;
    v_color = a_color;
    v_tone = a_tone;
    v_trims = a_trims;
    v_nib = a_nib;
    v_toothCoord = a_position * TEXTURE_SCALE;
    v_toothCoordRot = (ROT_23_5 * a_position) * (TEXTURE_SCALE * 1.61803) + vec2(0.371, 0.618);
    gl_Position = u_MVP * vec4(a_position, 0.0, 1.0);
}

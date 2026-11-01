#version 300 es
precision highp float;

layout(location = 0) in vec2 a_position;
layout(location = 1) in vec2 a_normal;
layout(location = 2) in float a_radius;
layout(location = 3) in vec4 a_color;
layout(location = 4) in float a_edgeDist; // tone mode: 0=Hard(2H), 1=Soft(4B), 2=Light

uniform mat4 u_MVP;

out highp vec2 v_normal;
out highp vec4 v_color;
out highp float v_tone;
out highp vec2 v_worldPos;
out highp vec2 v_toothCoord;
out highp vec2 v_toothCoordRot;

const highp float TEXTURE_SCALE = 0.183;
const mat2 ROT_23_5 = mat2(0.917, 0.399, -0.399, 0.917);

void main() {
    vec2 offsetPos = a_position + a_normal * a_radius;
    v_worldPos = offsetPos;
    v_normal = a_normal;
    v_color = a_color;
    v_tone = a_edgeDist;
    v_toothCoord = offsetPos * TEXTURE_SCALE;
    v_toothCoordRot = (ROT_23_5 * offsetPos) * (TEXTURE_SCALE * 1.61803) + vec2(0.371, 0.618);
    gl_Position = u_MVP * vec4(offsetPos, 0.0, 1.0);
}

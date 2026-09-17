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

void main() {
    vec2 offsetPos = a_position + a_normal * a_radius;
    v_worldPos = offsetPos;
    v_normal = a_normal;
    v_color = a_color;
    v_tone = a_edgeDist;
    gl_Position = u_MVP * vec4(offsetPos, 0.0, 1.0);
}

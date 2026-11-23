#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 0) out vec2 sceneUV;
layout(location = 1) out vec2 localUV;
layout(location = 2) flat out vec2 glassParams;

const vec2 corners[4] = vec2[](vec2(0,0), vec2(0,1), vec2(1,1), vec2(1,0));
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    sceneUV = UV0;
    localUV = corners[gl_VertexID % 4];
    glassParams = Color.rg * 255.0;
}

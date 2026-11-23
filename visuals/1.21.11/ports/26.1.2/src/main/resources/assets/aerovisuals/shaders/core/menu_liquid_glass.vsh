#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
out vec2 sceneUV;
out vec2 localUV;
flat out vec2 glassParams;

const vec2 corners[4] = vec2[](vec2(0,0), vec2(0,1), vec2(1,1), vec2(1,0));
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    sceneUV = UV0;
    localUV = corners[gl_VertexID % 4];
    glassParams = Color.rg * 255.0;
}

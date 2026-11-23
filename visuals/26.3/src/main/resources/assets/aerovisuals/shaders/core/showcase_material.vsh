#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;
layout(location = 0) out vec2 texCoord;
layout(location = 1) out vec3 modelPosition;
layout(location = 2) out vec4 materialColor;
layout(location = 3) out float materialTime;
layout(location = 4) out float materialRim;
layout(location = 5) flat out int materialStyle;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    modelPosition = Position;
    uint packedTint = (uint(UV1.x) & 65535u) | ((uint(UV1.y) & 65535u) << 16u);
    vec4 tint = vec4(float((packedTint >> 16u) & 255u), float((packedTint >> 8u) & 255u), float(packedTint & 255u), float((packedTint >> 24u) & 255u)) / 255.0;
    materialColor = Color * tint * ColorModulator;
    materialTime = float(uint(UV2.x) & 65535u) / 1000.0;
    materialStyle = UV2.y;
    vec3 viewPosition = (ModelViewMat * vec4(Position, 1.0)).xyz;
    vec3 normal = normalize(mat3(ModelViewMat) * Normal);
    materialRim = pow(1.0 - abs(dot(normal, normalize(-viewPosition))), 2.5);
}

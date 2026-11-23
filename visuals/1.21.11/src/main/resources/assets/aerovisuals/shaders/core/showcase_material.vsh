#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
out vec2 texCoord;
out vec3 modelPosition;
out vec4 materialColor;
out float materialTime;
out float materialRim;
flat out int materialStyle;
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

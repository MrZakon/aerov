#version 330
uniform sampler2D Sampler0;
in vec2 texCoord;
in vec3 modelPosition;
in vec4 materialColor;
in float materialTime;
in float materialRim;
flat in int materialStyle;
out vec4 fragColor;
float noise(vec3 p) { return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453); }
void main() {
    vec4 skin = texture(Sampler0, texCoord);
    if (skin.a < 0.1) discard;
    float t = materialTime;
    float band = 0.5 + 0.5 * sin(modelPosition.y * 65.0 - t * 7.0);
    float power = 0.7;
    float alpha = materialColor.a;
    int style = materialStyle & 255;
    if (style == 0) power = 0.9 + 0.1 * sin(t * 3.0);
    else if (style == 1) { power = mix(0.25, 1.2, pow(band, 6.0)); alpha *= 0.3 + 0.7 * band; }
    else if (style == 2) { float energy = sin(modelPosition.x * 18.0 + sin(modelPosition.y * 20.0 - t * 4.0) * 2.0 + t * 3.0); power = 0.35 + pow(1.0 - abs(energy), 5.0) * 1.3; }
    else if (style == 3) { float pulse = 0.5 + 0.5 * sin(t * 3.5 - modelPosition.y * 5.0); power = 0.35 + pulse; alpha *= 0.35 + pulse * 0.65; }
    else { vec3 p = modelPosition * 7.0 + vec3(t * 0.3, -t * 0.8, 0.0); float smoke = noise(floor(p)) * 0.35 + 0.65 * (0.5 + 0.5 * sin(p.x + sin(p.y + p.z))); power = smoke; alpha *= smoothstep(0.1, 0.8, smoke); }
    vec3 color = mix(skin.rgb, materialColor.rgb, 0.85) * power;
    if ((materialStyle & 256) != 0) { color += materialColor.rgb * materialRim * 1.1; alpha = max(alpha, materialColor.a * materialRim); }
    fragColor = vec4(color, skin.a * alpha);
}

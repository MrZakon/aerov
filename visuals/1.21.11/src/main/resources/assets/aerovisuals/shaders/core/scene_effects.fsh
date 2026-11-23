#version 330
uniform sampler2D Scene;
uniform sampler2D SceneDepth;
uniform sampler2D BloomTexture;
layout(std140) uniform SceneParameters {
    mat4 InverseViewProjection;
    mat4 PreviousViewProjection;
    vec4 CameraDelta;
    vec4 Frame;
    vec4 Bloom;
    vec4 Blur;
    vec4 Sky;
    vec4 SkyColor;
    vec4 Neon1;
    vec4 Neon2;
    vec4 Neon3;
    vec4 HandColor;
    vec4 HandSecond;
    vec4 HandParameters;
    vec4 Wave;
};
in vec2 texCoord;
out vec4 fragColor;
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1,0)), f.x), mix(hash(i + vec2(0,1)), hash(i + vec2(1,1)), f.x), f.y);
}
vec3 skyAt(vec2 uv) {
    vec4 p = InverseViewProjection * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    vec3 direction = normalize(p.xyz / max(abs(p.w), 0.00001));
    vec2 q = vec2(atan(direction.z, direction.x), asin(clamp(direction.y, -1.0, 1.0))) * Sky.z;
    float time = Frame.z * Sky.y;
    float clouds = noise(q * 0.8 + vec2(time * 0.04, 0)) * 0.6
            + noise(q * 2.0 - vec2(time * 0.02, 0)) * 0.3 + noise(q * 4.0) * 0.1;
    vec3 color = vec3(0.008, 0.012, 0.035) + SkyColor.rgb * (0.08 + clouds * clouds * 1.3);
    if (Sky.x > 1.5 && Sky.x < 2.5) {
        float curtain = pow(max(0.0, sin(q.x * 1.8 + sin(q.y + time * 0.4) * 2.0)), 6.0);
        float band = exp(-pow(q.y * 0.35 - 0.5 - sin(q.x * 0.4 + time * 0.2) * 0.3, 2.0) * 4.0);
        color = vec3(0.008, 0.015, 0.04) + SkyColor.rgb * curtain * band * 1.4;
    } else if (Sky.x > 2.5 && Sky.x < 3.5) {
        vec2 cells = q * 40.0;
        float star = step(0.987, hash(floor(cells))) * exp(-length(fract(cells) - 0.5) * 20.0);
        color = vec3(0.004, 0.008, 0.022) + SkyColor.rgb * star * (2.0 + sin(time + hash(floor(cells)) * 30.0));
    } else if (Sky.x > 3.5 && Sky.x < 4.5) {
        float plasma = sin(q.x + time) + sin(q.y * 1.3 - time * 0.7) + sin(length(q) * 2.0 + time);
        color = SkyColor.rgb * (0.3 + 0.25 * plasma) + vec3(0.1, 0.01, 0.16) * clouds;
    } else if (Sky.x > 4.5) {
        float wave = 0.5 + 0.5 * sin(q.x + q.y + clouds * 6.0 + time);
        color = mix(Neon1.rgb, Neon2.rgb, smoothstep(0.0, 0.5, wave));
        color = mix(color, Neon3.rgb, smoothstep(0.5, 1.0, wave));
        color *= 0.25 + clouds * 0.8;
    }
    return max(color, vec3(0)) * max(0.05, SkyColor.a * 1.5);
}
vec3 sceneAt(vec2 uv) {
    if (Wave.w > 0.0) {
        float aspect = Frame.x / Frame.y;
        vec2 displacement = (uv - Wave.xy) * vec2(aspect, 1.0);
        float distance = length(displacement);
        float band = exp(-pow((distance - Wave.z) * 50.0, 2.0));
        uv += displacement / max(distance, 0.0001) / vec2(aspect, 1.0) * band * Wave.w * sin((distance - Wave.z) * 120.0);
    }
    uv = clamp(uv, vec2(0.001), vec2(0.999));
    vec3 color = texture(Scene, uv).rgb;
    if (Sky.x > 0.5 && texture(SceneDepth, uv).r > 0.99999) color = mix(color, skyAt(uv), Sky.w);
    return color;
}
void main() {
    int stage = int(Frame.w + 0.5);
    if (stage == 1) {
        vec3 color = sceneAt(texCoord);
        float light = max(color.r, max(color.g, color.b));
        float soft = max(light - Bloom.x, 0.0) / max(light, 0.0001);
        vec3 extracted = HandParameters.y > 0.5 || HandParameters.x < 0.5 ? color * soft : vec3(0);
        if (HandParameters.x > 0.5) {
            vec2 shifted = texCoord + vec2(sin(texCoord.y * 90.0 + Frame.z * 5.0), cos(texCoord.x * 85.0 - Frame.z * 4.0)) * HandParameters.z * 2.0 / Frame.xy;
            float handMask = texture(SceneDepth, clamp(shifted, vec2(0), vec2(1))).r < 0.9999 ? 1.0 : 0.0;
            vec3 handTint = HandSecond.a > 0.0 ? mix(HandColor.rgb, HandSecond.rgb, 0.5 + 0.5 * sin(texCoord.y * 5.0 + Frame.z * HandSecond.a)) : HandColor.rgb;
            extracted += handTint * HandColor.a * handMask;
        }
        fragColor = vec4(extracted, 1.0);
        return;
    }
    if (stage == 2 || stage == 3) {
        vec2 direction = stage == 2 ? vec2(1,0) : vec2(0,1);
        vec2 offset = direction / vec2(textureSize(Scene, 0)) * max(0.5, Bloom.z * 0.5) * (HandParameters.x > 0.5 ? HandParameters.w : 1.0);
        vec3 color = texture(Scene, texCoord).rgb * 0.227027;
        color += texture(Scene, texCoord + offset * 1.384615).rgb * 0.316216;
        color += texture(Scene, texCoord - offset * 1.384615).rgb * 0.316216;
        color += texture(Scene, texCoord + offset * 3.230769).rgb * 0.070270;
        color += texture(Scene, texCoord - offset * 3.230769).rgb * 0.070270;
        fragColor = vec4(color, 1.0);
        return;
    }
    vec3 color = sceneAt(texCoord);
    if (Blur.x > 0.001) {
        float depth = texture(SceneDepth, texCoord).r;
        vec4 world = InverseViewProjection * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
        world /= abs(world.w) > 0.00001 ? world.w : 0.00001;
        world.xyz += Blur.z > 0.5 ? CameraDelta.xyz : vec3(0);
        vec4 old = PreviousViewProjection * world;
        vec2 previousUV = old.xy / max(abs(old.w), 0.00001) * 0.5 + 0.5;
        vec2 velocity = (texCoord - previousUV) * Blur.x * CameraDelta.w;
        velocity *= min(1.0, 0.12 / max(length(velocity), 0.0001));
        if (length(velocity * Frame.xy) > 0.1) {
            int samples = int(clamp(Blur.y, 4.0, 128.0));
            color = vec3(0);
            for (int i = 0; i < 128; ++i) {
                if (i >= samples) break;
                float step = float(i) / float(samples - 1) - (Blur.w > 0.5 ? 0.5 : 0.0);
                color += sceneAt(texCoord - velocity * step);
            }
            color /= float(samples);
        }
    }
    if (Bloom.w > 0.5) color += texture(BloomTexture, texCoord).rgb * Bloom.y * Bloom.z * 0.15;
    fragColor = vec4(color, 1.0);
}

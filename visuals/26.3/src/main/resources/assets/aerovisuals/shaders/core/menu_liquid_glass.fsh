#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;
layout(location = 0) in vec2 sceneUV;
layout(location = 1) in vec2 localUV;
layout(location = 2) flat in vec2 glassParams;
layout(location = 0) out vec4 fragColor;

float roundedDistance(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

vec3 scene(vec2 uv) {
    vec2 inset = 2.0 / vec2(textureSize(Sampler0, 0));
    return texture(Sampler0, clamp(uv, inset, 1.0 - inset)).rgb;
}

void main() {
    // Derivatives recover this quad's physical size at every GUI scale.
    vec2 size = 1.0 / max(fwidth(localUV), vec2(0.000001));
    float scale = glassParams.y / 16.0;
    float radius = min(glassParams.x, min(size.x, size.y) * 0.5);
    vec2 p = (localUV - 0.5) * size;
    float d = roundedDistance(p, size * 0.5 - 1.0, radius);
    float coverage = 1.0 - smoothstep(-0.75, 0.75, d);
    if (coverage <= 0.0) discard;

    // Rounded-box normal bends the background most strongly in the curved bevel.
    vec2 q = abs(p) - size * 0.5 + radius + 1.0;
    vec2 normal = sign(p) * (length(max(q, 0.0)) > 0.001
            ? normalize(max(q, 0.0))
            : (q.x > q.y ? vec2(1,0) : vec2(0,1)));
    float bevel = 1.0 - smoothstep(0.0, max(8.0, 16.0 * scale), -d);
    vec2 uvPerPixel = vec2(length(vec2(dFdx(sceneUV.x), dFdy(sceneUV.x))),
                          length(vec2(dFdx(sceneUV.y), dFdy(sceneUV.y))));
    vec2 bend = normal * bevel * bevel * 11.0 * scale * uvPerPixel;
    vec2 uv = sceneUV - bend;

    // Weighted disk blur samples the real menu artwork, never the text above it.
    vec3 blurred = scene(uv) * 4.0;
    float weight = 4.0;
    for (int ring = 1; ring <= 3; ++ring) {
        float w = 1.0 - float(ring) * 0.18;
        for (int i = 0; i < 8; ++i) {
            float angle = float(i) * 0.78539816339 + float(ring) * 0.23;
            vec2 offset = vec2(cos(angle), sin(angle)) * float(ring) * 2.3 * scale * uvPerPixel;
            blurred += scene(uv + offset) * w;
            weight += w;
        }
    }
    blurred /= weight;
    // Restrained dispersion along the refracting rim.
    blurred.r = mix(blurred.r, scene(uv - bend * 0.13).r, bevel * 0.25);
    blurred.b = mix(blurred.b, scene(uv + bend * 0.13).b, bevel * 0.25);
    vec3 color = mix(blurred, vec3(0.035, 0.045, 0.065), 0.36);
    float rim = exp(-abs(d + 1.0) / max(0.8, scale * 0.65));
    float light = 0.5 + 0.5 * dot(normal, normalize(vec2(-0.5, -0.85)));
    color += vec3(0.74, 0.85, 1.0) * rim * (0.12 + light * 0.30);
    color += vec3(0.04, 0.05, 0.07) * bevel * light;
    fragColor = vec4(color, coverage * 0.96);
}

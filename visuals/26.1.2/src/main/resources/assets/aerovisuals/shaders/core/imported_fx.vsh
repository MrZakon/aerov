#version 330

out vec2 uv;
#if defined(AERO_BLEND) || defined(AERO_FOG_TAPS)
out vec4 fogRay;
layout(std140) uniform Effects {
    mat4 InverseProjection;
    vec4 Frame;
    vec4 Fog;
    vec4 FogColor;
    vec4 Hand;
    vec4 HandColor;
};
#endif

void main() {
    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    uv = p;
    vec2 clip = p * 2.0 - 1.0;
#ifdef AERO_HAND_DEPTH
    // The depth comparison uses the same limit as glass, with the default OpenGL depth range.
    gl_Position = vec4(clip, 0.99999 * 2.0 - 1.0, 1.0);
#else
#if defined(AERO_BLEND) || defined(AERO_FOG_TAPS)
    fogRay = InverseProjection * vec4(clip, 0.0, 1.0);
#endif
    gl_Position = vec4(clip, 0.0, 1.0);
#endif
}

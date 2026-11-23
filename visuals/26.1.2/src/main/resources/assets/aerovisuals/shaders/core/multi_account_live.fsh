#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;
out vec4 fragColor;

void main() {
    vec3 color = texture(Sampler0, vec2(texCoord0.x, 1.0 - texCoord0.y)).rgb;
    fragColor = vec4(color, 1.0);
}

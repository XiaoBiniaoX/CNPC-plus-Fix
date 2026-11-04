#version 150
uniform sampler2D DiffuseSampler;
uniform float Intensity;
uniform float Opacity;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec4 color = texture(DiffuseSampler, texCoord);
    fragColor = vec4(mix(color.rgb, vec3(1.0) - color.rgb, Intensity * Opacity), color.a);
}

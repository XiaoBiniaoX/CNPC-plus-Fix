#version 120
uniform sampler2D DiffuseSampler;
uniform float Intensity;
uniform float Opacity;
varying vec2 texCoord;

void main() {
    vec4 color = texture2D(DiffuseSampler, texCoord);
    gl_FragColor = vec4(mix(color.rgb, vec3(1.0) - color.rgb, Intensity * Opacity), color.a);
}

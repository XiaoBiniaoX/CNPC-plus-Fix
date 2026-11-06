#version 150
#moj_import <fog.glsl>
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec4 Tint;
uniform float Opacity;
uniform float Pulse;
uniform float Time;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
in vec2 uv;
in vec4 litColor;
in vec4 overlay;
in float distanceToCamera;
out vec4 fragColor;
void main() {
    vec4 color = texture(Sampler0, uv) * litColor * ColorModulator;
    if (color.a < 0.1) discard;
    color.rgb = mix(overlay.rgb, color.rgb, overlay.a);
    color *= Tint;
    color.rgb *= 1.0 + Pulse * (0.5 + 0.5 * sin(Time * 6.283185));
    color.a *= Opacity;
    fragColor = linear_fog(color, distanceToCamera, FogStart, FogEnd, FogColor);
}

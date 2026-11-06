#version 120
uniform sampler2D Sampler0;
uniform vec4 Tint;
uniform float Opacity;
uniform float Pulse;
uniform float Time;
uniform int Textured;
uniform int FogMode;
varying vec2 uv;
varying vec4 litColor;
varying float distanceToCamera;
void main() {
    vec4 color = litColor;
    if (Textured != 0) color *= texture2D(Sampler0,uv);
    if (color.a < 0.1) discard;
    color *= Tint;
    color.rgb *= 1.0 + Pulse * (0.5 + 0.5 * sin(Time * 6.283185));
    color.a *= Opacity;
    float fog = 1.0;
    if (FogMode == 9729) fog = clamp((gl_Fog.end-distanceToCamera)*gl_Fog.scale,0.0,1.0);
    else if (FogMode == 2048) fog = clamp(exp(-gl_Fog.density*distanceToCamera),0.0,1.0);
    else if (FogMode == 2049) fog = clamp(exp(-gl_Fog.density*gl_Fog.density*distanceToCamera*distanceToCamera),0.0,1.0);
    gl_FragColor = vec4(mix(gl_Fog.color.rgb,color.rgb,fog),color.a);
}

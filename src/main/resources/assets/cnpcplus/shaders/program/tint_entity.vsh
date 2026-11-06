#version 120
uniform sampler2D Sampler2;
uniform int Lighting;
uniform int Lightmap;
varying vec2 uv;
varying vec4 litColor;
varying float distanceToCamera;
void main() {
    vec4 p = gl_ModelViewMatrix * gl_Vertex;
    gl_Position = gl_ProjectionMatrix * p;
    uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    litColor = gl_Color;
    if (Lighting != 0) {
        vec3 n = normalize(gl_NormalMatrix * gl_Normal);
        vec4 light = gl_LightModel.ambient;
        light += gl_LightSource[0].diffuse * max(dot(n,normalize(gl_LightSource[0].position.xyz)),0.0);
        light += gl_LightSource[1].diffuse * max(dot(n,normalize(gl_LightSource[1].position.xyz)),0.0);
        litColor.rgb *= clamp(light.rgb,0.0,1.0);
    }
    if (Lightmap != 0) litColor *= texture2D(Sampler2,(gl_TextureMatrix[1]*gl_MultiTexCoord1).xy);
    distanceToCamera = length(p.xyz);
}

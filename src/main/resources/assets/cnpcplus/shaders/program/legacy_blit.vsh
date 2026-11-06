#version 120
uniform mat4 ProjMat;
uniform vec2 OutSize;
varying vec2 texCoord;
void main() {
    vec4 p = ProjMat * vec4(gl_Vertex.xy, 0.0, 1.0);
    gl_Position = vec4(p.xy, 0.2, 1.0);
    texCoord = gl_Vertex.xy / OutSize;
    texCoord.y = 1.0 - texCoord.y;
}

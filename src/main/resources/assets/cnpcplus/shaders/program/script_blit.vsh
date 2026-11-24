#version 120
uniform vec2 OutSize;
varying vec2 texCoord;
void main() {
    texCoord = gl_Vertex.xy / OutSize;
    // Clip and UV share the same bottom-left origin, independent of ProjMat.
    gl_Position = vec4(texCoord * 2.0 - 1.0, 0.2, 1.0);
}

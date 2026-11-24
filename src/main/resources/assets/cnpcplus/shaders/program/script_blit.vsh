#version 150

in vec4 Position;
uniform vec2 OutSize;
out vec2 texCoord;

void main() {
    texCoord = Position.xy / OutSize;
    gl_Position = vec4(texCoord * 2.0 - 1.0, 0.2, 1.0);
}

#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 uv;
out vec4 litColor;
out vec4 overlay;
out float distanceToCamera;
void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    distanceToCamera = length(view.xyz);
    uv = UV0;
    litColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
    overlay = vec4(0.0, 0.0, 0.0, 1.0);
}

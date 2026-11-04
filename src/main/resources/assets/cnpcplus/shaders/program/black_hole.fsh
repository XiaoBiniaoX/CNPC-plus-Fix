#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform sampler2D MaskSampler;
uniform mat4 WorldViewProjection;
uniform mat4 InverseWorldViewProjection;
uniform vec3 CameraPosition;
uniform vec3 WorldPosition;
uniform vec2 OutSize;
uniform float Radius;
uniform float InfluenceRadius;
uniform float Intensity;
uniform float Distortion;
uniform float Falloff;
uniform float Rotation;
uniform float DiskRadius;
uniform float DiskWidth;
uniform vec3 DiskNormal;
uniform float DiskIntensity;
uniform vec3 DiskColor;
uniform vec3 CoreColor;
uniform float Opacity;
uniform float Time;
uniform float LensStrength;
in vec2 texCoord;
out vec4 fragColor;

// Positions are camera-relative: the matrix contains the actual world view rotation and FOV.
vec3 unproject(vec2 uv, float depth) {
    vec4 p = InverseWorldViewProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

void main() {
    vec4 original = texture(DiffuseSampler, texCoord);
    fragColor = original;
    vec3 ray = normalize(unproject(texCoord, 1.0));
    float sceneDistance = length(unproject(texCoord, texture(DepthSampler, texCoord).r));
    vec3 hole = WorldPosition - CameraPosition;
    float along = dot(hole, ray);
    vec3 impact = ray * along - hole;
    float b = length(impact);
    float extent = max(InfluenceRadius, DiskRadius + 3.0 * DiskWidth);
    float discriminant = extent * extent - b * b;
    if (discriminant <= 0.0 || along + sqrt(discriminant) <= 0.0) return;
    float entry = max(0.0, along - sqrt(discriminant));
    if (sceneDistance <= entry) return; // A wall in front of the lens stays untouched.

    float influence = pow(clamp(Radius / max(b, Radius), 0.0, 1.0), Falloff);
    influence *= 1.0 - smoothstep(Radius, extent, b);
    influence *= smoothstep(entry, max(entry + 0.01, along), sceneDistance);
    vec3 bentRay = normalize(ray - impact / max(b, Radius) * influence * Distortion * LensStrength * Intensity * 0.32);
    vec4 projected = WorldViewProjection * vec4(bentRay * max(along, extent), 1.0);
    vec2 pixel = 0.5 / OutSize;
    vec2 warped = clamp(projected.xy / projected.w * 0.5 + 0.5, pixel, 1.0 - pixel);
    vec3 color = original.rgb;
    // Do not drag nearer opaque geometry into the warped background.
    if (projected.w > 0.0 && length(unproject(warped, texture(DepthSampler, warped).r)) > entry)
        color = texture(DiffuseSampler, warped).rgb;

    float aa = max(fwidth(b), 0.001);
    float coreEntry = along - sqrt(max(Radius * Radius - b * b, 0.0));
    float core = (1.0 - smoothstep(Radius - aa, Radius + aa, b))
               * step(0.0, along) * step(max(0.0, coreEntry), sceneDistance);
    color = mix(color, CoreColor, core);

    // An actual world-space disk plane, not a screen-facing annulus.
    vec3 normal = normalize(DiskNormal);
    float denominator = dot(ray, normal);
    if (abs(denominator) > 0.0001) {
        float t = dot(hole, normal) / denominator;
        vec3 radial = ray * t - hole;
        float r = length(radial);
        if (t > 0.0 && t < sceneDistance && (core < 0.5 || t < coreEntry)) {
            vec3 axis = normalize(cross(normal, abs(normal.y) < 0.9 ? vec3(0, 1, 0) : vec3(1, 0, 0)));
            vec2 diskUv = vec2(dot(radial, axis), dot(radial, cross(normal, axis)));
            float angle = atan(diskUv.y, diskUv.x) - Rotation - Time * 0.4;
            float disk = exp(-pow((r - DiskRadius) / DiskWidth, 2.0)) * smoothstep(Radius, Radius + aa, r);
            float flow = 0.65 + 0.35 * sin(angle * 12.0 + r * 9.0 - Time * 2.0);
            disk *= texture(MaskSampler, clamp(diskUv / (2.0 * extent) + 0.5, 0.0, 1.0)).r;
            color += DiskColor * disk * flow * DiskIntensity * Intensity;
        }
    }
    float ring = exp(-pow((b - Radius * 1.08) / max(aa, Radius * 0.035), 2.0));
    color += DiskColor * ring * 0.6 * Intensity * (1.0 - core) * step(0.0, along) * step(along, sceneDistance);
    fragColor = vec4(mix(original.rgb, color, Opacity), original.a);
}

#version 120

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform mat4 InverseWorldViewProjection;
uniform vec3 CameraPosition;
uniform vec3 WorldPosition;
uniform vec3 PlaneNormal;
uniform vec3 Color;
uniform float Radius;
uniform float LineWidth;
uniform float Glow;
uniform float Rotation;
uniform float RotationSpeed;
uniform float Intensity;
uniform float Opacity;
uniform float Time;
uniform int Shape;
varying vec2 texCoord;


const float PI = 3.14159265359;
const float TAU = 6.28318530718;

vec3 unproject(vec2 uv, float depth) {
    vec4 p = InverseWorldViewProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

vec2 turn(vec2 p, float angle) {
    float c = cos(angle), s = sin(angle);
    return mat2(c, -s, s, c) * p;
}

float segment(vec2 p, vec2 a, vec2 b) {
    vec2 d = b - a;
    return length(p - a - d * clamp(dot(p - a, d) / dot(d, d), 0.0, 1.0));
}

float triangle(vec2 p) {
    vec2 a = vec2(0.0, 0.78), b = vec2(-0.6755, -0.39), c = vec2(0.6755, -0.39);
    return min(segment(p, a, b), min(segment(p, b, c), segment(p, c, a)));
}

float hexagram(vec2 p) { return min(triangle(p), triangle(-p)); }
float apparatus(vec3 p) {
    vec2 q=turn(p.xz,Rotation+Time*RotationSpeed);
    float r=length(q),angle=atan(q.y,q.x);
    float ring=max(abs(r-0.88)-0.025,abs(p.y)-0.028);
    float slots=0.35-sin(angle*12.0+float(Shape)*0.8);
    ring=max(ring,slots*0.04);
    float inner=max(abs(r-0.52)-0.018,abs(p.y+0.04)-0.025);
    float hub=max(max(abs(q.x),abs(q.y))-0.095,abs(p.y)-0.09);
    float spokeAngle=mod(angle+PI/6.0,TAU/6.0)-PI/6.0;
    vec3 spoke=vec3(cos(spokeAngle)*r,p.y,sin(spokeAngle)*r);
    float girder=max(max(abs(spoke.x-0.42)-0.24,abs(spoke.z)-0.012),abs(spoke.y)-0.02);
    float tower=max(max(abs(spoke.x-0.78)-0.045,abs(spoke.z)-0.055),abs(spoke.y-0.09)-0.12);
    if(Shape==2) {
        vec3 tilted=p;tilted.yz=turn(tilted.yz,0.22*sin(Time*0.6)+0.35);
        inner=max(abs(length(tilted.xz)-0.48)-0.016,abs(tilted.y)-0.028);
    }
    if(Shape==3)hub=length(p)-0.14;
    return min(min(ring,inner),min(hub,min(girder,tower)));
}

void main() {
    vec4 original = texture2D(DiffuseSampler, texCoord);
    gl_FragColor = original;
    vec3 ray = normalize(unproject(texCoord, 1.0));
    vec3 normal = length(PlaneNormal) > 0.001 ? normalize(PlaneNormal) : vec3(0, 1, 0);
    vec3 axis = normalize(cross(normal, abs(normal.y) < 0.9 ? vec3(0, 1, 0) : vec3(1, 0, 0)));
    float sceneDistance = length(unproject(texCoord, texture2D(DepthSampler, texCoord).r));
    // Thick segmented hardware around each seal, visible edge-on and from below.
    if(Shape>0) {
        vec3 origin=CameraPosition-WorldPosition;
        vec3 ro=vec3(dot(origin,axis),dot(origin,normal),dot(origin,cross(normal,axis)))/Radius;
        vec3 rd=vec3(dot(ray,axis),dot(ray,normal),dot(ray,cross(normal,axis)));
        float b=dot(ro,rd),h=b*b-dot(ro,ro)+1.18*1.18;
        if(h>=0.0) {
            float nearT=max(0.0,-b-sqrt(h)),end=min(-b+sqrt(h),sceneDistance/Radius);
            float distance=nearT;
            for(int i=0;i<64;i++) {
                if(distance>=end)break;
                vec3 pos=ro+rd*distance;float d=apparatus(pos);
                if(d<0.001) {
                    vec2 e=vec2(0.002,0);
                    vec3 n=normalize(vec3(apparatus(pos+e.xyy)-apparatus(pos-e.xyy),apparatus(pos+e.yxy)-apparatus(pos-e.yxy),apparatus(pos+e.yyx)-apparatus(pos-e.yyx)));
                    float lit=max(dot(n,normalize(vec3(-0.4,0.8,0.5))),0.0);
                    float rim=pow(1.0-abs(dot(n,rd)),3.0);
                    float circuit=pow(max(0.0,sin(atan(pos.z,pos.x)*24.0-Time*2.0)),12.0);
                    vec3 metal=vec3(0.025,0.038,0.06)*(0.4+lit)+Color*(rim*0.2+circuit*0.22)*Intensity;
                    gl_FragColor=vec4(mix(original.rgb,metal,Opacity),original.a);
                    break;
                }
                distance+=max(d*0.65,0.0005);
            }
        }
    }
    float denominator = dot(ray, normal);
    if (abs(denominator) < 0.0001) return;
    float t = dot(WorldPosition - CameraPosition, normal) / denominator;
    if (t <= 0.0 || t > sceneDistance) return;
    vec3 hit = ray * t - (WorldPosition - CameraPosition);
    vec2 p = vec2(dot(hit, axis), dot(hit, cross(normal, axis))) / Radius;
    p = turn(p, Rotation + Time * RotationSpeed);
    float r = length(p);
    if (r > 1.08) return;
    float angle = atan(p.y, p.x);
    float d = 10.0;
    float pulse = 0.82 + 0.18 * sin(Time * 2.0);

    if (Shape == 0) {
        // Six winding stems with alternating tapered barbs, not a recolored magic circle.
        float sector = mod(angle + PI / 6.0, TAU / 6.0) - PI / 6.0;
        vec2 q = vec2(cos(sector), sin(sector)) * r;
        float stem = 0.045 * sin(q.x * 16.0 - Time * 1.5) * q.x;
        d = abs(q.y - stem) - 0.012 * (1.0 - q.x);
        d = max(d, max(0.08 - q.x, q.x - 0.99));
        for (int i = 0; i < 7; i++) {
            float x = 0.16 + float(i) * 0.115;
            float side = mod(float(i), 2.0) * 2.0 - 1.0;
            float y = 0.045 * sin(x * 16.0 - Time * 1.5) * x;
            vec2 root = vec2(x, y), tip = root + vec2(-0.065, side * (0.16 - x * 0.07));
            float taper = clamp(dot(q - root, tip - root) / dot(tip - root, tip - root), 0.0, 1.0);
            d = min(d, segment(q, root, tip) - 0.017 * (1.0 - taper));
        }
        d = min(d, abs(r - (0.09 + 0.012 * sin(angle * 6.0 + Time))));
    } else if (Shape == 1) {
        d = min(hexagram(p), abs(r - 0.88));
        float runes = max(abs(r - 0.98), abs(sin(angle * 24.0)) * 0.045);
        d = min(d, runes);
        d = min(d, abs(r - 0.22));
    } else if (Shape == 2) {
        // Asymmetric moving rosette surrounding an eye, with broken orbital arcs.
        float wave = 0.59 + 0.13 * sin(angle * 7.0 + Time * 0.7) + 0.06 * sin(angle * 3.0 - Time);
        d = abs(r - wave);
        d = min(d, abs(r - (0.34 + 0.07 * sin(angle * 5.0 - Time * 0.5))));
        vec2 eye = turn(p, -Time * 0.25);
        float eyelid = abs(abs(eye.y) - 0.19 * (1.0 - pow(eye.x / 0.36, 2.0)));
        d = min(d, max(eyelid, abs(eye.x) - 0.36));
        d = min(d, abs(length(eye - vec2(0.04 * sin(Time), 0)) - 0.075));
        float arcs = max(abs(r - 0.91), 0.2 - sin(angle * 9.0 + r * 8.0));
        d = min(d, arcs);
    } else {
        // Counter-rotating nested seals, toothed outer ring and an eight-fold strike crown.
        vec2 inner = turn(p, -Time * RotationSpeed * 2.0);
        d = min(hexagram(inner / 0.72) * 0.72, abs(r - 0.58));
        d = min(d, abs(r - 0.84));
        float teeth = max(abs(r - 0.95), abs(sin(angle * 32.0 - Time)) * 0.04);
        d = min(d, teeth);
        float crown = 0.69 + 0.12 * cos(angle * 8.0 + Time * 0.6);
        d = min(d, abs(r - crown));
        d = min(d, abs(r - 0.18));
        pulse = 0.75 + 0.25 * sin(Time * 3.0 - r * 12.0);
    }

    float aa = max(length(fwidth(p)) * 0.7, 0.0005);
    float stroke = 1.0 - smoothstep(LineWidth - aa, LineWidth + aa, d);
    float halo = exp(-max(d - LineWidth, 0.0) / max(LineWidth * 4.0, aa)) * Glow;
    float edge = 1.0 - smoothstep(1.0, 1.08, r);
    vec3 color = Color * (stroke + halo) * pulse * Intensity * edge;
    gl_FragColor = vec4(gl_FragColor.rgb + color * Opacity, original.a);
}

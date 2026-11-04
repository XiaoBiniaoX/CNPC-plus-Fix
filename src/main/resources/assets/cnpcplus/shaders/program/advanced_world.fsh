#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform vec2 OutSize;
uniform mat4 InverseWorldViewProjection;
uniform mat4 WorldViewProjection;
uniform vec3 CameraPosition;
uniform vec3 WorldPosition;
uniform vec3 PlaneNormal;
uniform vec3 Color;
uniform vec4 Hit0;
uniform vec4 Hit1;
uniform vec4 Hit2;
uniform vec4 Hit3;
uniform float Radius;
uniform float Height;
uniform float Downward;
uniform float Growth;
uniform float Distortion;
uniform float Thermal;
uniform float Glow;
uniform float Rotation;
uniform float RotationSpeed;
uniform float Intensity;
uniform float Opacity;
uniform float Time;
uniform int Mode;
in vec2 texCoord;
out vec4 fragColor;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;

float hash(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}
float noise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i+vec3(1,0,0)), f.x), mix(hash(i+vec3(0,1,0)), hash(i+vec3(1,1,0)), f.x), f.y),
               mix(mix(hash(i+vec3(0,0,1)), hash(i+vec3(1,0,1)), f.x), mix(hash(i+vec3(0,1,1)), hash(i+vec3(1,1,1)), f.x), f.y), f.z);
}
float fbm(vec3 p) {
    float n = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) { n += a * noise(p); p = p * 2.03 + vec3(9,3,7); a *= 0.5; }
    return n;
}
vec3 unproject(vec2 uv, float depth) {
    vec4 p = InverseWorldViewProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}
vec2 turn(vec2 p, float a) { float c=cos(a), s=sin(a); return mat2(c,-s,s,c)*p; }
vec2 sphere(vec3 ro, vec3 rd, float radius) {
    float b=dot(ro,rd), h=b*b-dot(ro,ro)+radius*radius;
    if (h < 0.0) return vec2(-1.0);
    h=sqrt(h); return vec2(-b-h,-b+h);
}
vec3 readScene(vec2 uv) { return texture(DiffuseSampler, clamp(uv, vec2(0.001), vec2(0.999))).rgb; }

// Alternate procedural world: rotating nebula, stars, a ringed planet and a jagged horizon.
// This is not a second Minecraft world camera or a recoloring of the current scene.
vec3 otherWorld(vec2 p, vec3 ray) {
    p = turn(p, Time * RotationSpeed + Rotation);
    vec3 direction = normalize(vec3(p * 1.5, 1.0) + ray * 0.12);
    float nebula = fbm(direction * 5.0 + vec3(0,0,Time*0.03));
    vec3 color = mix(vec3(0.015,0.006,0.045), vec3(0.25,0.05,0.55), nebula) * (0.4+nebula);
    vec2 cells = p * 95.0, id = floor(cells), f = fract(cells)-0.5;
    float star = pow(max(0.0, 1.0-length(f)*4.0), 12.0) * step(0.966, hash(vec3(id,4)));
    color += vec3(0.6,0.8,1.0) * star * (1.2+0.6*sin(Time+hash(vec3(id,2))*20.0));
    vec2 planet = p - vec2(0.25,0.22);
    float r = length(planet);
    if (r < 0.27) {
        vec3 n = vec3(planet/0.27, sqrt(max(0.0, 1.0-r*r/(0.27*0.27))));
        float bands = 0.5+0.5*sin(n.y*35.0+noise(n*9.0)*6.0);
        color = mix(vec3(0.03,0.13,0.2), vec3(0.35,0.75,0.65), bands) * (0.15+max(dot(n,normalize(vec3(-1,1,1))),0.0));
    }
    float ring=abs(length(planet*vec2(1,3))-0.42);
    color += vec3(0.3,0.6,0.8)*exp(-ring*160.0);
    float horizon=-0.5+0.10*sin(p.x*12.0)+0.05*sin(p.x*31.0);
    if (p.y < horizon) color=mix(vec3(0.015,0.03,0.06),vec3(0.08,0.18,0.22),exp((p.y-horizon)*8.0));
    return color;
}

float contact(vec3 direction, vec4 hit) {
    if (dot(hit.xyz,hit.xyz) < 0.01) return 0.0;
    float age = Time-hit.w;
    if (age < 0.0 || age > 1.0) return 0.0;
    vec3 normal=normalize(hit.xyz);
    float distance=length(direction-normal);
    float ring=exp(-abs(distance-age*1.5)*55.0);
    vec3 axis=normalize(cross(normal, abs(normal.y)<0.9 ? vec3(0,1,0) : vec3(1,0,0)));
    float a=atan(dot(direction,cross(normal,axis)),dot(direction,axis));
    float jagged=sin(a*7.0 + noise(direction*35.0+Time*8.0)*5.0 + distance*32.0);
    float arcs=exp(-abs(jagged)*18.0)*exp(-distance*5.5);
    return (arcs*3.0+ring)*(1.0-age);
}

// Six rising, curled stems with solid tapered conical barbs. Angular folding is 3D,
// not a plane decal; the Y axis remains real height throughout sphere tracing.
float thornField(vec3 p) {
    float h=max(0.15, Height*Growth);
    float y=clamp(p.y,0.0,h);
    p.xz=turn(p.xz, Rotation + Time*RotationSpeed + y*0.25);
    float angle=atan(p.z,p.x);
    float a=mod(angle+PI/6.0,TAU/6.0)-PI/6.0;
    vec3 q=vec3(cos(a)*length(p.xz), p.y, sin(a)*length(p.xz));
    float center=Radius*(0.32+0.23*sin(y/h*1.8));
    q.x-=center + 0.12*sin(y*2.0+Time);
    q.z-=0.09*sin(y*2.7-Time*0.7);
    float stem=max(length(q.xz)-(0.14-0.095*y/h), max(-p.y,p.y-h));
    float row=clamp(floor(y/0.62),0.0,floor(h/0.62)), barb=10.0;
    for (int i=-1;i<=1;i++) {
        float level=clamp(row+float(i),0.0,floor(h/0.62));
        float by=level*0.62+0.24;
        vec3 base=vec3(0,by,0), tip=base+vec3((mod(level,2.0)*2.0-1.0)*0.46,0.18,0.12);
        vec3 v=tip-base, relative=q-base;
        float along=clamp(dot(relative,v)/dot(v,v),0.0,1.0);
        barb=min(barb,length(relative-v*along)-0.095*(1.0-along));
    }
    barb=max(barb,max(-p.y,p.y-h));
    float root=length(vec3(p.x,p.y*1.8,p.z))-Radius*0.25;
    return min(stem, min(barb,root));
}

void main() {
    vec4 original=texture(DiffuseSampler,texCoord);
    fragColor=original;
    vec3 ray=normalize(unproject(texCoord,1.0));
    float sceneDistance=length(unproject(texCoord,texture(DepthSampler,texCoord).r));
    vec3 center=WorldPosition-CameraPosition;
    vec3 ro=-center;
    vec3 result=original.rgb;

    if (Mode == 0) {
        vec3 normal=normalize(PlaneNormal);
        float denom=dot(ray,normal);
        if (abs(denom)<0.0001) return;
        float t=dot(center,normal)/denom;
        if (t<=0.0 || t>sceneDistance) return;
        vec3 axis=normalize(cross(normal,abs(normal.y)<0.9?vec3(0,1,0):vec3(1,0,0)));
        vec3 point=ray*t-center;
        vec2 p=vec2(dot(point,axis),dot(point,cross(normal,axis)))/Radius;
        float r=length(p);
        if (r>1.75) return;
        float wave=sin((r-1.0)*35.0-Time*5.0)*exp(-abs(r-1.0)*4.5);
        // Project the portal's radial tangent, not the screen center: it stays anchored when looking away.
        vec3 tangent=axis*p.x+cross(normal,axis)*p.y;
        vec4 edgeClip=WorldViewProjection*vec4(ray*t+tangent*0.1,1.0);
        vec2 radial=normalize(edgeClip.xy/edgeClip.w*0.5+0.5-texCoord+vec2(0.000001));
        float refraction=Distortion*(wave*0.008+exp(-abs(r-1.0)*25.0)*0.035);
        result=readScene(texCoord+radial*refraction);
        float rim=exp(-abs(r-1.0)*70.0)+0.5*exp(-abs(r-1.05)*110.0);
        float interior=1.0-smoothstep(0.965,0.99,r);
        result=mix(result,otherWorld(p,ray),interior);
        result += Color*(rim*(1.5+Glow)+max(wave,0.0)*0.08)*Intensity;
    } else if (Mode == 1) {
        vec2 interval=sphere(ro,ray,Radius);
        float t=interval.x>0.0?interval.x:interval.y;
        if (t<=0.0 || t>sceneDistance) return;
        vec3 n=normalize(ro+ray*t);
        float fresnel=pow(1.0-abs(dot(n,ray)),2.0);
        float flow=fbm(n*8.0+vec3(Time*0.6,-Time*1.2,Time*0.3));
        float filaments=pow(1.0-abs(sin(flow*23.0+n.y*12.0-Time*2.0)),8.0);
        float hits=contact(n,Hit0)+contact(n,Hit1)+contact(n,Hit2)+contact(n,Hit3);
        result=readScene(texCoord+n.xy*Distortion*(0.002+filaments*0.004));
        result += Color*(0.035+fresnel*0.45+filaments*0.22+hits*(1.0+Glow))*Intensity;
    } else if (Mode == 2) {
        // Analytic capped cylinder interval followed by 24 volume samples; opaque depth clips it.
        float b=dot(ro.xz,ray.xz), a=dot(ray.xz,ray.xz), c=dot(ro.xz,ro.xz)-Radius*Radius;
        float discriminant=b*b-a*c;
        if (discriminant<0.0 || a<0.000001) {
            if (c>0.0) return;
        }
        float nearT=0.0, farT=sceneDistance;
        if (a>=0.000001) { nearT=max(0.0,(-b-sqrt(max(discriminant,0.0)))/a); farT=min(farT,(-b+sqrt(max(discriminant,0.0)))/a); }
        if (abs(ray.y)>0.000001) {
            float y0=-ro.y/ray.y, y1=(Height-ro.y)/ray.y;
            nearT=max(nearT,min(y0,y1)); farT=min(farT,max(y0,y1));
        } else if (ro.y<0.0 || ro.y>Height) return;
        if (farT<=nearT) return;
        float density=0.0;
        for (int i=0;i<24;i++) {
            float t=mix(nearT,farT,(float(i)+0.5)/24.0);
            vec3 p=ro+ray*t;
            float front=mix(Height,clamp(Time*Height*4.0,0.0,Height),Downward);
            if (p.y<Height-front) continue;
            float r=length(p.xz)/Radius;
            float core=exp(-r*r*9.0);
            float spiral=0.5+0.5*sin(atan(p.z,p.x)*6.0+p.y*2.0+mix(-1.0,1.0,Downward)*Time*18.0);
            density+=(core+0.28*spiral*exp(-abs(r-0.75)*14.0))*(farT-nearT)/24.0;
        }
        result+=Color*density*Intensity*(1.0+Glow);
        result+=vec3(0.6,0.8,1.0)*density*density*0.12*Intensity;
    } else if (Mode == 3) {
        vec2 interval=sphere(ro,ray,Radius);
        if (interval.y<=0.0 || max(interval.x,0.0)>sceneDistance) return;
        float t=clamp(dot(center,ray),max(0.0,interval.x),min(sceneDistance,interval.y));
        vec3 p=ro+ray*t;
        float envelope=pow(max(0.0,1.0-length(p)/Radius),1.3);
        float n=fbm(p*2.0-vec3(0,Time*1.8,0));
        vec2 warp=vec2(sin(p.y*9.0+Time*4.0+n*6.0),cos(p.x*8.0-Time*3.0+n*8.0));
        result=readScene(texCoord+warp*Distortion*0.008*envelope);
        float l=dot(result,vec3(0.2126,0.7152,0.0722));
        vec3 thermal=clamp(vec3(l*3.0,l*3.0-1.0,l*4.0-3.0),0.0,1.0);
        result=mix(result,thermal,Thermal*envelope);
        result+=Color*max(0.0,sin(length(p.xz)*12.0-Time*5.0))*envelope*Glow*0.04*Intensity;
    } else {
        vec3 bound=vec3(0,Height*Growth*0.5,0);
        vec2 interval=sphere(ro-bound,ray,max(Radius,Height*Growth*0.6)+0.6);
        float t=max(0.0,interval.x), end=min(sceneDistance,interval.y);
        if (end<=t) return;
        bool hit=false;
        for (int i=0;i<96;i++) {
            float d=thornField(ro+ray*t);
            if (d<0.0025) { hit=true; break; }
            t+=max(d*0.45,0.001);
            if (t>=end) break;
        }
        if (!hit || t>=sceneDistance) return;
        vec3 p=ro+ray*t;
        vec2 e=vec2(0.003,0);
        vec3 n=normalize(vec3(thornField(p+e.xyy)-thornField(p-e.xyy),thornField(p+e.yxy)-thornField(p-e.yxy),thornField(p+e.yyx)-thornField(p-e.yyx)));
        vec3 light=normalize(vec3(-0.4,0.8,0.5));
        float diffuse=max(dot(n,light),0.0), specular=pow(max(dot(n,normalize(light-ray)),0.0),40.0);
        result=Color*(0.16+diffuse*0.9)*Intensity+vec3(0.8,0.16,0.2)*specular;
        result+=Color*Glow*0.1*pow(1.0-abs(dot(n,ray)),3.0);
    }
    fragColor=vec4(mix(original.rgb,result,Opacity),original.a);
}

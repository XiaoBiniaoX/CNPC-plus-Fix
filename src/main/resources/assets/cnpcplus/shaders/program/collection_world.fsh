#version 150
uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform vec2 OutSize;
uniform mat4 InverseWorldViewProjection;
uniform mat4 WorldViewProjection;
uniform vec3 CameraPosition;
uniform vec3 WorldPosition;
uniform vec3 Color;
uniform float Radius;
uniform float Intensity;
uniform float Opacity;
uniform float Time;
uniform int Mode;
in vec2 texCoord;
out vec4 fragColor;
const float TAU=6.28318530718;
float hash(vec3 p) { p=fract(p*0.1031); p+=dot(p,p.yzx+33.33); return fract((p.x+p.y)*p.z); }
float noise(vec3 p) {
    vec3 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
    return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);
}
float fbm(vec3 p) { float v=0.0,a=0.5; for(int i=0;i<4;i++) { v+=noise(p)*a; p=p*2.03+vec3(5,11,7);a*=0.5; } return v; }
vec2 rotate(vec2 p,float a) { return mat2(cos(a),-sin(a),sin(a),cos(a))*p; }
vec3 unproject(float depth) {
    vec4 p=InverseWorldViewProjection*vec4(texCoord*2.0-1.0,depth*2.0-1.0,1);
    return p.xyz/p.w;
}
vec2 project(vec3 p) { vec4 q=WorldViewProjection*vec4(p,1);return q.xy/q.w*0.5+0.5; }
vec3 scene(vec2 uv) { return texture(DiffuseSampler,clamp(uv,vec2(0.001),vec2(0.999))).rgb; }
vec2 sphere(vec3 ro,vec3 rd,float r) {
    float b=dot(ro,rd),h=b*b-dot(ro,ro)+r*r;
    if(h<0.0)return vec2(-1); h=sqrt(h);return vec2(-b-h,-b+h);
}
float capsule(vec3 p,vec3 a,vec3 b,float r) { vec3 v=b-a;return length(p-a-v*clamp(dot(p-a,v)/dot(v,v),0.0,1.0))-r; }
float shape(vec3 p) {
    if(Mode==5) {
        p.xz=rotate(p.xz,Time*0.28);p.xy=rotate(p.xy,0.2*sin(Time));
        return (abs(p.x)+abs(p.y)*0.8+abs(p.z)-0.72)*0.57735;
    }
    if(Mode==7) {
        float a=atan(p.z,p.x),r=length(p.xz);
        float petals=0.53+0.18*cos(a*8.0+Time*0.15);
        float bowl=abs(p.y-0.18*cos(r*4.0))-0.075;
        float flower=max(bowl,r-petals);
        return min(flower,length(p-vec3(0,0.28,0))-0.17);
    }
    if(Mode==8) {
        vec3 q=p;q.xy=rotate(q.xy,Time*0.4);
        float d=length(vec2(length(q.xz)-0.68,q.y))-0.035;
        q=p;q.yz=rotate(q.yz,Time*0.55+0.9);
        d=min(d,length(vec2(length(q.xy)-0.52,q.z))-0.03);
        q=p;q.xz=rotate(q.xz,-Time*0.3+0.4);
        return min(d,min(length(vec2(length(q.yz)-0.84,q.x))-0.026,length(p)-0.12));
    }
    if(Mode==9) {
        float d=length(p)-0.19;
        // Rotational symmetry: only the two neighboring spokes can be closest to each segment.
        // 8 segments * 2 spokes, rather than testing all 6 arms at every ray step.
        for(int j=1;j<=8;j++) {
            float t=float(j)/8.0,previousT=t-0.125;
            float sector=TAU/6.0;
            float spoke=floor((atan(p.z,p.x)-(t-0.0625)*2.0-Time*0.18)/sector);
            for(int k=0;k<2;k++) {
                float a=(spoke+float(k))*sector+Time*0.18;
                vec3 previous=vec3(cos(a+previousT*2.0),sin(previousT*5.0+Time)*0.25+0.35*previousT,sin(a+previousT*2.0))*previousT*0.9;
                vec3 next=vec3(cos(a+t*2.0),sin(t*5.0+Time)*0.25+0.35*t,sin(a+t*2.0))*t*0.9;
                d=min(d,capsule(p,previous,next,0.08*(1.0-t)+0.012));
            }
        }
        return d;
    }
    vec3 q=p; q.xz=rotate(q.xz,Time*0.12);
    float body=max(max(abs(q.x)-0.20,abs(q.z)-0.20),abs(q.y)-0.75);
    float crown=(abs(q.x)+abs(q.z)+abs(q.y-0.72)*0.7-0.28)*0.57735;
    return min(body,crown);
}
void main() {
    vec4 original=texture(DiffuseSampler,texCoord);fragColor=original;
    vec3 rd=normalize(unproject(1.0)), center=WorldPosition-CameraPosition;
    float depth=texture(DepthSampler,texCoord).r;
    vec3 surface=unproject(depth);
    float sceneDistance=length(surface);
    vec3 result=original.rgb;
    if(Mode==0) {
        // A real world trajectory, a brief black-core contraction, then outward white-hole ejection.
        center+=Radius*vec3(sin(Time*0.6)*0.75,sin(Time*0.37)*0.22,(cos(Time*0.6)-1.0)*0.35);
        vec3 ro=-center;vec2 span=sphere(ro,rd,Radius*1.5);
        if(span.y<=0.0||max(span.x,0.0)>sceneDistance)return;
        float phase=smoothstep(6.0,7.0,Time);
        float coreRadius=Radius*mix(0.35,0.20,phase)*(1.0-0.75*sin(phase*3.14159265));
        float closest=length(ro+rd*max(0.0,dot(center,rd)))/Radius;
        vec2 direction=normalize(texCoord-project(center)+vec2(0.0001));
        float pull=exp(-closest*2.0)*(1.0-2.0*phase);
        result=scene(texCoord+direction*pull*0.045);
        vec2 core=sphere(ro,rd,coreRadius);
        if(core.y>0.0&&max(core.x,0.0)<sceneDistance)result=mix(vec3(0),vec3(3),phase);
        float plane=abs(rd.y)>0.0001?center.y/rd.y:-1.0;
        if(plane>0.0&&plane<sceneDistance) {
            vec3 p=rd*plane-center;float r=length(p.xz)/Radius,a=atan(p.z,p.x);
            float disk=exp(-abs(r-0.72)*20.0)*(0.5+0.5*sin(a*32.0-r*18.0-Time*8.0));
            result+=mix(vec3(1,0.35,0.04),Color,phase)*disk*Intensity;
        }
        float wave=exp(-abs(closest-mod((Time-7.0)*1.8,1.5))*30.0)*step(7.0,Time);
        result+=Color*(exp(-abs(closest-coreRadius/Radius)*45.0)*1.4+wave)*Intensity;
    } else if(Mode==1) {
        // Spherical propagation, not a screen-centered ring. Its distortion travels through visible world surfaces.
        vec3 ro=-center;vec2 span=sphere(ro,rd,Radius);
        if(span.y<=0.0||max(span.x,0.0)>sceneDistance)return;
        float d=length(surface-center), wave=sin(d*6.0-Time*5.0)*exp(-d/Radius);
        vec2 direction=normalize(texCoord-project(center)+vec2(0.0001));
        float near=clamp(1.0-d/Radius,0.0,1.0);
        result=scene(texCoord+direction*wave*0.02*near);
        float t=clamp(dot(center,rd),max(span.x,0.0),min(span.y,sceneDistance));
        float radial=length(rd*t-center);
        float shell=exp(-abs(radial-mod(Time*1.8,Radius))*20.0);
        result+=Color*(shell*0.65+abs(wave)*near*0.12)*Intensity;
    } else if(Mode==2) {
        // Depth reconstructed mask clips tearing/blocks to NPC and surrounding visible block surfaces.
        if(depth>=0.999999||length(surface-center)>Radius)return;
        float frame=floor(Time*11.0),gate=step(0.45,hash(vec3(frame,8,2)));
        vec3 worldSurface=surface+CameraPosition;
        float band=floor(worldSurface.y*8.0);
        float shift=(hash(vec3(band,frame,3))-0.5)*0.12*gate;
        result=scene(texCoord+vec2(shift,0));
        vec3 tile=floor(worldSurface*vec3(4,9,4));
        float flash=step(0.87,hash(tile+frame))*gate;
        float tint=hash(tile+vec3(frame,19,7));
        vec3 glitch=tint<0.33?vec3(1):tint<0.66?vec3(1,0,1):vec3(0,1,0);
        result=mix(result,glitch,flash);
    } else if(Mode==5||Mode==7||Mode==8||Mode==9||Mode==12) {
        vec3 ro=-center;vec2 span=sphere(ro,rd,Radius*1.25);
        float t=max(span.x,0.0),end=min(span.y,sceneDistance);
        if(end<=t)return;
        bool found=false;
        for(int i=0;i<80;i++) {
            if(Mode==9&&i>=48)break;
            float d=shape((ro+rd*t)/Radius)*Radius;
            if(d<0.003){found=true;break;} t+=max(d*0.65,0.001);if(t>=end)break;
        }
        if(!found||t>=end)return;
        vec3 p=(ro+rd*t)/Radius;vec2 e=vec2(0.002,0);
        vec3 n=normalize(vec3(shape(p+e.xyy)-shape(p-e.xyy),shape(p+e.yxy)-shape(p-e.yxy),shape(p+e.yyx)-shape(p-e.yyx)));
        float diffuse=max(dot(n,normalize(vec3(-0.4,0.8,0.5))),0.0);
        float rim=pow(1.0-abs(dot(n,rd)),3.0);
        if(Mode==5) {
            float facets=step(0.5,fract((p.x+p.z)*9.0+p.y*4.0));
            result=mix(scene(texCoord+n.xy*0.05),Color,0.45)*(0.4+diffuse)+Color*rim*1.6+vec3(facets*0.15);
        } else if(Mode==7) {
            float veins=pow(1.0-abs(sin(atan(p.z,p.x)*16.0+length(p.xz)*19.0)),12.0);
            result=Color*(0.2+diffuse)+vec3(0.6,0.85,1)*(rim+veins*0.4);
        } else if(Mode==8)result=Color*(0.7+diffuse*0.4+rim)*Intensity;
        else if(Mode==9) {
            float suckers=pow(1.0-abs(sin(p.y*55.0+atan(p.z,p.x)*8.0)),12.0);
            result=Color*(0.12+diffuse*0.75)+vec3(0.7,0.1,0.25)*suckers*0.25+Color*rim*0.5;
        } else {
            float runes=step(0.72,sin(p.y*35.0)*sin(atan(p.z,p.x)*18.0+floor(p.y*8.0)));
            result=vec3(0.04,0.05,0.065)*(0.2+diffuse)+Color*runes*(1.5+sin(Time+p.y*8.0)*0.4);
        }
        if(Mode!=8)result*=Intensity;
    } else if(Mode==13) {
        vec3 ro=-center;vec2 span=sphere(ro,rd,Radius*1.3);
        if(span.y<=0.0||max(span.x,0.0)>sceneDistance)return;
        float glow=0.0;
        for(int i=0;i<16;i++) {
            float seed=float(i),phase=fract(Time*0.22+seed*0.173);
            vec3 head=vec3((hash(vec3(seed,1,3))-0.5)*1.5,1.1-phase*2.2,(hash(vec3(seed,2,7))-0.5)*1.5)*Radius;
            vec3 tail=head+vec3(-0.15,0.45,0.05)*Radius;
            float along=clamp(dot(head-ro,rd),0.0,sceneDistance);
            float d=capsule(ro+rd*along,head,tail,Radius*0.025)/Radius;
            glow+=exp(-max(d,0.0)*100.0)*(1.0-phase);
        }
        result+=Color*glow*Intensity*2.0;
    } else if(Mode==14) {
        vec3 ro=-center;vec2 span=sphere(ro,rd,Radius);
        float t=span.x>0.0?span.x:span.y;
        if(t<=0.0||t>=sceneDistance)return;
        vec3 n=normalize(ro+rd*t);
        float ripple=sin(n.y*28.0-Time*3.0+sin(n.x*17.0+Time));
        result=scene(texCoord+n.xy*(0.02+ripple*0.007));
        float caustic=pow(1.0-abs(sin(n.x*24.0+n.z*17.0+ripple*3.0)),12.0);
        result=mix(result,Color,0.1)+Color*(caustic*0.35+pow(1.0-abs(dot(n,rd)),3.0)*0.5)*Intensity;
    } else {
        // Bounded 24-sample volumes. Each mode has its own density function and spatial topology.
        vec3 ro=-center;vec2 span=sphere(ro,rd,Radius*1.25);
        float start=max(span.x,0.0),end=min(span.y,sceneDistance);if(end<=start)return;
        vec3 light=vec3(0);float alpha=0.0;
        for(int i=0;i<24;i++) {
            vec3 p=(ro+rd*mix(start,end,(float(i)+0.5)/24.0))/Radius;
            float d=0.0;vec3 tint=Color;
            if(Mode==3) {
                float a=atan(p.z,p.x),r=length(p.xz);
                float curtain=0.7+0.08*sin(a*9.0+Time+p.y*3.0);
                d=exp(-abs(r-curtain)*55.0)*smoothstep(-0.8,0.0,p.y)*(1.0-smoothstep(0.3,1.0,p.y));
                d*=0.4+0.6*noise(vec3(a*8.0,p.y*3.0-Time,Time*0.1));
                tint=mix(Color,vec3(0.65,0.1,1),smoothstep(-0.2,0.7,p.y));
            } else if(Mode==4) {
                float r=length(p),storm=noise(p*12.0+Time*2.0);
                float bolt=exp(-abs(sin(atan(p.z,p.x)*9.0+p.y*14.0+storm*6.0))*30.0);
                d=exp(-abs(r-0.72)*25.0)*(0.08+bolt*1.7);
                tint=mix(Color,vec3(1),bolt*0.6);
            } else if(Mode==6) {
                float r=length(p.xz),a=atan(p.z,p.x);
                float spiral=0.38+0.15*sin(a*3.0-p.y*9.0+Time*4.0);
                d=exp(-abs(r-spiral)*18.0)*max(0.0,1.0-abs(p.y));
                d*=fbm(p*6.0-vec3(0,Time*2.0,0))*2.0;
                tint=mix(vec3(1,0.05,0),Color,clamp(r*2.0,0.0,1.0));
            } else if(Mode==10) {
                vec3 q=p;q.xz=rotate(q.xz,Time*0.25);float box=max(max(abs(q.x),abs(q.y)),abs(q.z));
                d=step(box,0.65)*pow(fbm(q*5.0+Time*0.12),3.0)*2.0;
                d+=exp(-abs(box-0.65)*80.0)*0.18;
                tint=mix(Color,vec3(0.9,0.1,0.6),noise(q*3.0));
            } else {
                float r=length(p),n=fbm(p*7.0+vec3(Time*0.2,-Time,0));
                float skin=0.58+n*0.2;
                d=exp(-abs(r-skin)*25.0)*(0.4+pow(n,3.0)*4.0);
                d+=exp(-r*r*14.0)*0.6;
                tint=mix(vec3(1,0.08,0.005),Color,n);
            }
            float sampleAlpha=1.0-exp(-d*(end-start)/Radius/24.0*2.0);
            light+=(1.0-alpha)*tint*sampleAlpha*2.0;alpha+=(1.0-alpha)*sampleAlpha;
        }
        result=original.rgb*(1.0-alpha*0.5)+light*Intensity;
    }
    fragColor=vec4(mix(original.rgb,result,Opacity*(Mode==2?clamp(Intensity,0.0,1.0):1.0)),original.a);
}

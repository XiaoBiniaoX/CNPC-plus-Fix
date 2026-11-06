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
vec2 raySegment(vec3 ro,vec3 rd,vec3 a,vec3 b,float limit) {
    vec3 v=b-a,w=ro-a;float vv=dot(v,v),rv=dot(rd,v);
    float u=clamp((dot(v,w)-rv*dot(rd,w))/max(vv-rv*rv,0.000001),0.0,1.0);
    float t=clamp(dot(a+v*u-ro,rd),0.0,limit);
    u=clamp(dot(ro+rd*t-a,v)/vv,0.0,1.0);
    return vec2(length(ro+rd*t-a-v*u),u);
}
float shape(vec3 p) {
    if(Mode==5) {
        p.xz=rotate(p.xz,Time*0.28);p.xy=rotate(p.xy,0.2*sin(Time));
        float hex=max(abs(p.x)*0.8660254+abs(p.z)*0.5,abs(p.z));
        float crystal=max(hex+abs(p.y)*0.42-0.42,abs(p.y)-0.86)*0.8;
        float sector=TAU/3.0,a=floor(atan(p.z,p.x)/sector+0.5)*sector;
        vec3 q=p-vec3(cos(a)*0.63,0.12*sin(Time*1.1+a),sin(a)*0.63);
        q.xz=rotate(q.xz,-a);q.xy=rotate(q.xy,0.3*sin(Time+a));
        float shard=(abs(q.x)+abs(q.z)+abs(q.y)*0.48-0.13)*0.57735;
        return min(crystal,shard);
    }
    if(Mode==7) {
        float a=atan(p.z,p.x),r=length(p.xz),sector=TAU/8.0;
        float angle=mod(a+sector*0.5+Time*0.1,sector)-sector*0.5;
        vec3 q=vec3(cos(angle)*r-0.38,p.y,sin(angle)*r);
        float outer=(abs(q.x)/0.40+abs(q.z)/0.17-1.0)*0.11;
        outer=max(outer,abs(q.y-0.07-q.x*q.x*1.7)-0.023);
        sector=TAU/6.0;angle=mod(a+sector*0.5-Time*0.14+0.4,sector)-sector*0.5;
        q=vec3(cos(angle)*r-0.24,p.y-0.13,sin(angle)*r);
        float inner=(abs(q.x)/0.25+abs(q.z)/0.11-1.0)*0.075;
        inner=max(inner,abs(q.y-q.x*0.65)-0.026);
        float bud=(abs(p.x)+abs(p.z)+abs(p.y-0.30)*0.38-0.105)*0.57735;
        return min(min(outer,inner),bud);
    }
    if(Mode==8) {
        vec3 q=p;q.xy=rotate(q.xy,Time*0.4);
        float a=atan(q.z,q.x),teeth=step(0.82,fract(a/TAU*48.0));
        float d=length(vec2(length(q.xz)-0.68,q.y))-0.024-teeth*0.015;
        d=min(d,length(q-vec3(0.68,0,0))-0.062);
        q=p;q.yz=rotate(q.yz,Time*0.55+0.9);
        a=atan(q.y,q.x);teeth=step(0.85,fract(a/TAU*32.0));
        d=min(d,length(vec2(length(q.xy)-0.52,q.z))-0.020-teeth*0.012);
        d=min(d,length(q-vec3(0,0.52,0))-0.047);
        q=p;q.xz=rotate(q.xz,-Time*0.3+0.4);
        d=min(d,length(vec2(length(q.yz)-0.84,q.x))-0.022);
        d=min(d,length(q-vec3(0,0,0.84))-0.045);
        q=p;q.xy=rotate(q.xy,-Time*0.6);q.yz=rotate(q.yz,Time*0.35);
        return min(d,(abs(q.x)+abs(q.y)+abs(q.z)-0.20)*0.57735);
    }
    if(Mode==9) {
        float d=length(p*vec3(1.0,1.25,1.0))-0.23;
        // Rotational symmetry: only the two neighboring spokes can be closest to each segment.
        // 8 segments * 2 spokes, rather than testing all 6 arms at every ray step.
        for(int j=1;j<=8;j++) {
            float t=float(j)/8.0,previousT=t-0.125;
            float sector=TAU/6.0;
            float spoke=floor((atan(p.z,p.x)-(t-0.0625)*2.0-Time*0.18)/sector);
            for(int k=0;k<2;k++) {
                float a=(spoke+float(k))*sector+Time*0.18;
                vec3 previous=vec3(cos(a+previousT*2.0),sin(previousT*5.0+Time)*0.32+0.36*previousT,sin(a+previousT*2.0))*previousT*0.9;
                vec3 next=vec3(cos(a+t*2.0),sin(t*5.0+Time)*0.32+0.36*t,sin(a+t*2.0))*t*0.9;
                d=min(d,capsule(p,previous,next,0.105*(1.0-t)+0.019));
            }
        }
        return d;
    }
    vec3 q=p; q.xz=rotate(q.xz,Time*0.12);
    float bevel=max(max(abs(q.x),abs(q.z))-0.22,(abs(q.x)+abs(q.z)-0.35)*0.7071);
    float body=max(bevel,abs(q.y+0.05)-0.65);
    float crown=max((abs(q.x)+abs(q.z)+(q.y-0.60)*0.65-0.35)*0.57735,0.60-q.y);
    float collars=max(abs(max(abs(q.x),abs(q.z))-0.27),abs(abs(q.y+0.08)-0.58)*0.7)-0.028;
    return min(min(body,crown),collars);
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
            float disk=exp(-abs(r-0.72)*16.0)*(0.2+0.8*fbm(vec3(p.xz*5.0,Time*0.6)));
            float shear=pow(0.5+0.5*sin(a*5.0-r*35.0-Time*9.0),6.0);
            result+=mix(vec3(1,0.32,0.06),Color,phase)*disk*(0.4+shear)*Intensity;
        }
        // Far-side Einstein image lifted above the disk, and a thin photon sphere.
        float photon=exp(-abs(closest-coreRadius/Radius*1.06)*110.0);
        float lensArc=exp(-abs(closest-0.61)*38.0)*smoothstep(-0.05,0.2,(rd*dot(center,rd)-center).y/Radius);
        result+=mix(vec3(1,0.7,0.3),Color,phase)*(photon*1.4+lensArc*0.3)*Intensity;
        float wave=exp(-abs(closest-mod((Time-7.0)*1.8,1.5))*30.0)*step(7.0,Time);
        result+=Color*(exp(-abs(closest-coreRadius/Radius)*45.0)*1.4+wave)*Intensity;
        vec3 nearest=(ro+rd*max(dot(center,rd),0.0))/Radius;
        float jet=exp(-length(nearest.xz)*22.0)*smoothstep(0.18,0.35,abs(nearest.y));
        jet*=1.0-smoothstep(0.8,1.45,abs(nearest.y));
        result+=mix(vec3(0.8,0.46,0.22),Color,phase)*jet*(0.2+phase)*Intensity;
    } else if(Mode==1) {
        // Spherical propagation, not a screen-centered ring. Its distortion travels through visible world surfaces.
        vec3 ro=-center;vec2 span=sphere(ro,rd,Radius);
        if(span.y<=0.0||max(span.x,0.0)>sceneDistance)return;
        float d=length(surface-center), front=mod(Time*4.0,Radius);
        float wave=sin((d-front)*14.0)*exp(-abs(d-front)*2.5)*exp(-d/Radius);
        vec2 direction=normalize(texCoord-project(center)+vec2(0.0001));
        float near=clamp(1.0-d/Radius,0.0,1.0);
        result=scene(texCoord+direction*wave*0.02*near);
        float t=clamp(dot(center,rd),max(span.x,0.0),min(span.y,sceneDistance));
        float radial=length(rd*t-center),frontRadius=mod(Time*1.8,Radius);
        vec2 shock=sphere(ro,rd,max(frontRadius,0.001));
        float shell=exp(-abs(radial-frontRadius)*20.0);
        if(shock.y>0.0&&max(shock.x,0.0)<sceneDistance) {
            vec3 n=normalize(ro+rd*max(shock.x,0.0));
            float grazing=pow(1.0-abs(dot(n,rd)),3.0);
            float ribs=0.65+0.35*sin(n.y*31.0+n.x*17.0-Time*9.0);
            shell+=grazing*ribs*(1.0-frontRadius/Radius);
        }
        result+=Color*(shell*0.7+abs(wave)*near*0.18)*Intensity;
    } else if(Mode==2) {
        // Depth reconstructed mask clips tearing/blocks to NPC and surrounding visible block surfaces.
        if(depth>=0.999999||length(surface-center)>Radius)return;
        float frame=floor(Time*11.0),gate=step(0.45,hash(vec3(frame,8,2)));
        vec3 worldSurface=surface+CameraPosition;
        float band=floor(worldSurface.y*8.0);
        float rupture=step(0.67,hash(vec3(band,frame,11)));
        float shift=(hash(vec3(band,frame,3))-0.5)*0.19*gate*rupture;
        float subBand=floor(worldSurface.y*32.0);
        shift+=(hash(vec3(subBand,frame,29))-0.5)*0.025*gate*rupture;
        result=scene(texCoord+vec2(shift,0));
        result.r=scene(texCoord+vec2(shift*1.15,0)).r;
        result.b=scene(texCoord+vec2(shift*0.85,0)).b;
        vec3 tile=floor(worldSurface*vec3(4,9,4));
        float flash=step(0.87,hash(tile+frame))*gate;
        float tint=hash(tile+vec3(frame,19,7));
        vec3 glitch=tint<0.33?vec3(1):tint<0.66?vec3(1,0,1):vec3(0,1,0);
        result=mix(result,glitch,flash);
    } else if(Mode==4) {
        vec3 ro=-center;vec2 span=sphere(ro,rd,Radius*1.25);
        if(span.y<=0.0||max(span.x,0.0)>sceneDistance)return;
        vec3 glow=vec3(0);float frame=floor(Time*7.0);
        for(int arc=0;arc<7;arc++) {
            float seed=float(arc),a=seed*TAU/7.0+Time*0.16;
            float flicker=0.55+0.45*hash(vec3(seed,frame,8));
            for(int j=0;j<8;j++) {
                float lat=-1.35+float(j)*0.3375,nextLat=lat+0.3375;
                float angle=a+(hash(vec3(seed,float(j),frame))-0.5)*0.21;
                float nextAngle=a+(hash(vec3(seed,float(j+1),frame))-0.5)*0.21;
                vec3 from=vec3(cos(angle)*cos(lat),sin(lat),sin(angle)*cos(lat))*Radius*0.72;
                vec3 to=vec3(cos(nextAngle)*cos(nextLat),sin(nextLat),sin(nextAngle)*cos(nextLat))*Radius*0.72;
                from.xy=rotate(from.xy,Time*0.23);to.xy=rotate(to.xy,Time*0.23);
                float d=raySegment(ro,rd,from,to,sceneDistance).x/Radius;
                glow+=(Color*exp(-d*85.0)*0.34+vec3(0.8,0.9,1)*exp(-d*340.0)*1.3)*flicker;
            }
        }
        float closest=length(ro+rd*clamp(dot(center,rd),0.0,sceneDistance))/Radius;
        float haze=exp(-closest*closest*7.0)*0.05;
        result+=glow*Intensity+Color*haze*Intensity;
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
        float specular=pow(max(dot(n,normalize(normalize(vec3(-0.4,0.8,0.5))-rd)),0.0),48.0);
        if(Mode==5) {
            vec3 bent=refract(rd,n,0.66);
            vec2 uv=project(center+(p+bent*(0.38+0.28*(1.0-abs(p.y))))*Radius);
            vec2 dispersion=(uv-texCoord)*0.11;
            vec3 transmitted=vec3(scene(uv+dispersion).r,scene(uv).g,scene(uv-dispersion).b);
            float inner=pow(0.5+0.5*sin(p.y*31.0+atan(p.z,p.x)*6.0-Time*2.0),12.0);
            result=transmitted*mix(vec3(0.93),Color,0.17)*(0.90+diffuse*0.12);
            result+=Color*(rim*0.55+inner*0.14+0.07)+vec3(specular*1.2);
        } else if(Mode==7) {
            float veins=pow(1.0-abs(sin(atan(p.z,p.x)*24.0+length(p.xz)*35.0)),16.0);
            float frost=pow(noise(p*65.0),5.0);
            result=mix(scene(texCoord+n.xy*0.025),Color,0.48)*(0.55+diffuse*0.45);
            result+=vec3(0.64,0.84,1)*(rim*0.6+veins*0.32+frost*0.5+specular*0.85);
        } else if(Mode==8) {
            float engraving=0.5+0.5*sin(atan(p.z,p.x)*90.0+p.y*38.0);
            float signal=pow(0.5+0.5*sin(atan(p.z,p.x)*3.0-Time*3.0+p.y*5.0),10.0);
            result=Color*(0.16+diffuse*0.55)*(0.8+engraving*0.2);
            result+=vec3(1,0.82,0.48)*specular+Color*(rim*0.3+signal*0.65);
            result*=Intensity;
        }
        else if(Mode==9) {
            float ribs=sin(length(p.xz)*95.0+atan(p.z,p.x)*7.0+noise(p*22.0)*3.0);
            float pores=pow(1.0-abs(ribs),9.0);
            float blood=pow(1.0-abs(sin(p.y*18.0+noise(p*11.0)*9.0)),8.0);
            float wrapped=0.5+0.5*dot(n,normalize(vec3(-0.4,0.8,0.5)));
            result=Color*(0.14+diffuse*0.65)*(0.7+ribs*0.15);
            result=mix(result,vec3(0.025,0.008,0.026),pores*0.65);
            result+=vec3(0.55,0.045,0.13)*blood*wrapped*0.24+vec3(0.8,0.44,0.54)*specular*0.55+Color*rim*0.35;
        } else {
            vec3 q=p;q.xz=rotate(q.xz,Time*0.12);
            float face=abs(q.z)>abs(q.x)?q.x:q.z;
            vec2 rune=vec2(face/0.22,fract((q.y+0.65)*5.0)-0.5);
            float seed=floor((q.y+0.65)*5.0),variant=hash(vec3(seed,4,1));
            float stroke=min(abs(rune.x)*0.15,abs(rune.y-rune.x*(variant-0.5)*0.7));
            stroke=min(stroke,length(vec2(abs(rune.x)-0.48,rune.y+0.19))*0.4);
            float runes=(1.0-smoothstep(0.015,0.03,stroke))*step(abs(rune.x),0.72)*step(abs(rune.y),0.36);
            runes*=1.0-smoothstep(0.48,0.60,q.y);
            float grain=noise(q*45.0),signal=pow(0.5+0.5*sin(Time*2.0-seed*0.65),4.0);
            result=vec3(0.028,0.032,0.047)*(0.28+diffuse)*(0.7+grain*0.5);
            result+=vec3(0.3,0.38,0.5)*specular*0.35+Color*runes*(0.65+signal*1.0);
        }
        if(Mode!=8)result*=Intensity;
    } else if(Mode==13) {
        vec3 ro=-center;vec2 span=sphere(ro,rd,Radius*1.3);
        if(span.y<=0.0||max(span.x,0.0)>sceneDistance)return;
        vec3 glow=vec3(0);
        for(int i=0;i<16;i++) {
            float seed=float(i),phase=fract(Time*0.22+seed*0.173);
            vec3 head=vec3((hash(vec3(seed,1,3))-0.5)*1.5+phase*0.16,0.95-phase*1.9,(hash(vec3(seed,2,7))-0.5)*1.5+phase*phase*0.13)*Radius;
            vec3 trail=vec3(-0.10,0.25+hash(vec3(seed,8,2))*0.25,-phase*0.10)*Radius;
            vec2 distance=raySegment(ro,rd,head,head+trail,sceneDistance);
            float u=distance.y,d=distance.x/Radius;
            float headT=clamp(dot(head-ro,rd),0.0,sceneDistance);
            vec3 point=ro+rd*headT-head;
            float fade=smoothstep(0.0,0.08,phase)*(1.0-smoothstep(0.75,1.0,phase));
            float streak=exp(-d*95.0)*pow(1.0-u,1.6)*fade;
            float hot=exp(-length(point)/Radius*130.0)*fade;
            glow+=Color*streak*1.5+vec3(1,0.87,0.65)*hot*1.3;
            vec3 spark=head+Radius*vec3(sin(seed*4.0+Time)*0.08,0.07,cos(seed*3.0+Time)*0.07);
            float sparkT=clamp(dot(spark-ro,rd),0.0,sceneDistance);
            glow+=Color*exp(-length(ro+rd*sparkT-spark)/Radius*180.0)*fade*0.5;
        }
        result+=glow*Intensity;
    } else if(Mode==14) {
        vec3 ro=-center;vec2 span=sphere(ro,rd,Radius);
        float t=span.x>0.0?span.x:span.y;
        if(t<=0.0||t>=sceneDistance)return;
        vec3 position=ro+rd*t,n=normalize(position);
        float ripple=sin(n.y*28.0-Time*3.0+sin(n.x*17.0+Time));
        n=normalize(n+vec3(cos(n.x*17.0+Time),cos(n.y*28.0-Time*3.0),sin(n.z*19.0-Time))*0.075);
        float thickness=max(span.y-max(span.x,0.0),0.0)/Radius;
        vec2 uv=texCoord+n.xy*(0.025+ripple*0.006)*thickness;
        vec2 dispersion=n.xy*0.0025*thickness;
        vec3 transmitted=vec3(scene(uv+dispersion).r,scene(uv).g,scene(uv-dispersion).b);
        transmitted*=exp(-(vec3(1)-Color)*thickness*0.18);
        float fresnel=0.025+0.975*pow(1.0-abs(dot(n,rd)),5.0);
        vec3 reflection=scene(texCoord+n.xy*0.09+vec2(0,0.025));
        float caustic=pow(1.0-abs(sin(n.x*24.0+n.z*17.0+ripple*1.8)*sin(n.y*21.0-n.z*13.0)),10.0);
        float specular=pow(max(dot(n,normalize(normalize(vec3(-0.4,0.8,0.5))-rd)),0.0),100.0);
        result=mix(transmitted,reflection*0.6+Color*0.28,fresnel);
        result+=(Color*caustic*0.14*(1.0-fresnel)+vec3(specular*1.4)+Color*fresnel*0.35)*Intensity;
    } else {
        // Bounded 32-sample volumes; density, emission and extinction are distinct for each material.
        vec3 ro=-center;vec2 span=sphere(ro,rd,Radius*1.25);
        float start=max(span.x,0.0),end=min(span.y,sceneDistance);if(end<=start)return;
        vec3 light=vec3(0);float alpha=0.0;
        for(int i=0;i<32;i++) {
            vec3 p=(ro+rd*mix(start,end,(float(i)+0.5)/32.0))/Radius;
            float d=0.0;vec3 tint=Color;
            if(Mode==3) {
                float a=atan(p.z,p.x),r=length(p.xz);
                float curtain=0.65+0.13*sin(a*5.0+Time*0.55)+0.055*sin(a*13.0-Time+p.y*2.0);
                float folds=exp(-abs(r-curtain)*70.0)+exp(-abs(r-curtain+0.12)*90.0)*0.45;
                float height=smoothstep(-0.65,-0.38,p.y)*(1.0-smoothstep(0.30,1.0,p.y));
                float filaments=pow(0.5+0.5*sin(a*75.0+noise(vec3(a*5.0,Time*0.2,0))*9.0),3.0);
                d=folds*height*(0.12+filaments*0.85);
                d*=0.7+0.3*sin(p.y*10.0-Time*1.4+a*4.0);
                tint=mix(Color,vec3(0.55,0.12,0.82),smoothstep(-0.05,0.72,p.y));
                tint=mix(tint,vec3(0.64,1,0.67),exp(-abs(p.y+0.35)*14.0)*0.3);
            } else if(Mode==6) {
                float r=length(p.xz),a=atan(p.z,p.x);
                float spiral=0.25+0.18*(p.y+0.65)+0.095*sin(a*3.0-p.y*7.0+Time*4.0);
                float fuel=fbm(vec3(p.x*7.0,p.y*4.0-Time*2.0,p.z*7.0));
                float tongue=pow(0.5+0.5*sin(a*5.0-p.y*13.0+Time*6.0+fuel*5.0),2.0);
                d=exp(-abs(r-spiral)*22.0)*smoothstep(-1.0,-0.6,p.y)*(1.0-smoothstep(0.45,1.0,p.y));
                d*=0.25+fuel*1.5+tongue*0.8;
                float heat=clamp(fuel*0.7+tongue*0.45-p.y*0.18,0.0,1.0);
                tint=mix(vec3(0.8,0.025,0.002),Color,heat);
                tint=mix(tint,vec3(1,0.8,0.38),pow(heat,5.0));
            } else if(Mode==10) {
                vec3 q=p;q.xz=rotate(q.xz,Time*0.25);q.xy=rotate(q.xy,0.22*sin(Time*0.3));
                vec3 b=abs(q);float box=max(max(b.x,b.y),b.z);
                float frame=min(min(length(b.xy-0.65),length(b.yz-0.65)),length(b.xz-0.65));
                vec3 swirl=q+vec3(sin(q.z*5.0+Time*0.3),sin(q.x*5.0-Time*0.4),sin(q.y*5.0))*0.12;
                float gas=fbm(swirl*5.0+Time*0.12);
                float filaments=pow(1.0-abs(sin(swirl.x*9.0+swirl.y*6.0+gas*9.0)),5.0);
                vec3 cells=floor(q*18.0),stars=q*18.0-cells-0.5;
                float star=exp(-length(stars)*25.0)*step(0.94,hash(cells));
                d=step(box,0.65)*(pow(gas,3.0)*1.3+filaments*0.45+star*8.0);
                d+=exp(-frame*110.0)*0.9;
                tint=mix(Color,vec3(0.74,0.08,0.5),gas);
                tint=mix(tint,vec3(0.58,0.86,1),clamp(star*3.0+exp(-frame*100.0),0.0,1.0));
            } else {
                float r=length(p),n=fbm(p*8.0+vec3(Time*0.18,-Time*0.38,0));
                float skin=0.60+(n-0.5)*0.08;
                float granules=pow(1.0-abs(sin(p.x*22.0+p.y*13.0+n*10.0)*sin(p.z*24.0-p.y*19.0)),3.0);
                float spot=smoothstep(0.62,0.76,noise(p*5.0+Time*0.1));
                float atmosphere=exp(-abs(r-skin)*32.0);
                float prominence=exp(-abs(r-(0.77+0.10*sin(atan(p.z,p.x)*6.0+Time)))*40.0);
                prominence*=pow(0.5+0.5*sin(p.y*21.0+atan(p.z,p.x)*7.0-Time*1.7),8.0);
                d=atmosphere*(2.5+granules*2.0)+prominence*0.65;
                tint=mix(Color*0.45,vec3(1,0.66,0.16),granules*0.7+n*0.3);
                tint*=1.0-spot*0.72;
            }
            float sampleAlpha=1.0-exp(-d*(end-start)/Radius/32.0*3.0);
            float illumination=0.62+0.38*clamp(p.y*0.5+0.5,0.0,1.0);
            light+=(1.0-alpha)*tint*sampleAlpha*illumination*1.65;alpha+=(1.0-alpha)*sampleAlpha;
        }
        result=original.rgb*(1.0-alpha*(Mode==11?1.0:0.55))+light*Intensity;
    }
    fragColor=vec4(mix(original.rgb,result,Opacity*(Mode==2?clamp(Intensity,0.0,1.0):1.0)),original.a);
}

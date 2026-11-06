#version 150
uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform sampler2D HistorySampler;
uniform sampler2D HistorySampler2;
uniform sampler2D HistorySampler3;
uniform vec2 OutSize;
uniform vec3 Color;
uniform float Intensity;
uniform float Opacity;
uniform float Time;
uniform int Mode;
in vec2 texCoord;
out vec4 fragColor;
const float TAU=6.28318530718;
float hash(vec2 p) { vec3 q=fract(vec3(p.xyx)*0.1031);q+=dot(q,q.yzx+33.33);return fract((q.x+q.y)*q.z); }
float noise(vec2 p) { vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+1.0),f.x),f.y); }
float fbm(vec2 p) { float v=0.0,a=0.5;for(int i=0;i<5;i++){v+=noise(p)*a;p=p*2.03+11.7;a*=0.5;}return v; }
vec2 rotate(vec2 p,float a){return mat2(cos(a),-sin(a),sin(a),cos(a))*p;}
vec3 scene(vec2 p){return texture(DiffuseSampler,clamp(p,vec2(0.001),vec2(0.999))).rgb;}
vec3 chroma(vec2 p,vec2 d){return vec3(scene(p+d).r,scene(p).g,scene(p-d).b);}
void main(){
    vec4 original=texture(DiffuseSampler,texCoord);vec3 result=original.rgb;
    vec2 aspect=vec2(OutSize.x/OutSize.y,1),p=(texCoord-0.5)*aspect;
    float r=length(p),a=atan(p.y,p.x),depth=texture(DepthSampler,texCoord).r;
    if(Mode==0){
        // Irregular breathing tissue across the entire field, independently warped folds, veins and moving wet highlights.
        vec2 q=p*5.0;float n=fbm(q+vec2(Time*0.12,-Time*0.08));
        float pulse=sin(Time*2.6+n*9.0)+0.35*sin(Time*4.3+q.y*2.0);
        vec2 muscle=vec2(fbm(q*1.7+Time*0.3),fbm(q*1.9-Time*0.2))-0.5;
        q+=muscle*1.3+vec2(pulse*0.10,-pulse*0.07);
        vec2 warp=muscle*(0.045+0.03*pulse)*(0.6+depth*0.4);
        float lobes=sin(q.x*3.8+sin(q.y*4.2)+n*7.0+pulse*0.7);
        float folds=abs(lobes),cavity=pow(1.0-folds,6.0);
        float veins=pow(1.0-abs(sin(q.x*10.0+q.y*4.0+muscle.y*16.0)),22.0);
        veins*=0.4+0.6*noise(q*4.0);
        float fiber=sin(q.y*53.0+muscle.x*23.0+pulse*2.0)*sin(q.y*97.0+q.x*4.0);
        float height=folds*(0.65+0.35*n)+fiber*0.016;
        vec3 normal=normalize(vec3(-dFdx(height)*OutSize.x*0.05,-dFdy(height)*OutSize.y*0.05,1));
        vec3 light=normalize(vec3(-0.45,0.55,1));
        float diffuse=max(dot(normal,light),0.0),wet=pow(max(dot(normal,normalize(light+vec3(0,0,1))),0.0),64.0);
        vec3 tissue=Color*(0.13+0.35*n+0.08*pulse)*(0.55+diffuse*0.65);
        tissue=mix(tissue,vec3(0.035,0.003,0.010),cavity*0.8);
        tissue*=1.0-veins*0.58;
        tissue+=Color*pow(folds,4.0)*(0.07+0.03*pulse)+vec3(0.75,0.42,0.38)*wet*0.22;
        result=mix(scene(texCoord+warp/aspect),tissue,0.91);
    }else if(Mode==1){
        float flow=fbm(vec2(p.x*5.0,p.y*4.0-Time*0.22));
        float sheet=sin(p.y*21.0+flow*8.0+Time)*0.4+sin(p.x*13.0-Time*0.7)*0.18;
        vec2 slope=vec2(cos(p.x*13.0-Time*0.7)*2.34,cos(p.y*21.0+flow*8.0+Time)*8.4)*0.09;
        vec3 normal=normalize(vec3(-slope,1));
        vec2 uv=vec2(1.0-texCoord.x,texCoord.y)+slope/aspect*0.07+vec2(flow-0.5,0)*0.025;
        float fresnel=pow(1.0-normal.z,2.0),light=pow(max(dot(normal,normalize(vec3(-0.2,0.6,1))),0.0),30.0);
        float seams=pow(1.0-abs(sin(p.y*12.0+flow*6.0-Time*0.5)),12.0);
        result=chroma(uv,slope/aspect*0.004)*(0.68+normal.z*0.25);
        result+=Color*(fresnel*0.28+seams*0.05)+vec3(0.6,0.7,0.8)*light*0.2;
    }else if(Mode==2){
        float sector=TAU/6.0,phase=a+Time*0.16+sin(r*6.0-Time)*0.07;
        float angle=abs(mod(phase+sector*0.5,sector)-sector*0.5);
        float radius=abs(mod(r+Time*0.025,0.48)-0.24)*2.0;
        vec2 uv=vec2(cos(angle),sin(angle))*radius;
        uv=rotate(uv,Time*0.08)/aspect+0.5;
        vec2 dispersion=normalize(p+0.0001)/aspect*0.004*(0.6+sin(r*15.0-Time)*0.4);
        result=chroma(uv,dispersion);
        float bevel=exp(-min(angle,sector*0.5-angle)*130.0)*smoothstep(0.03,0.15,r);
        result=result*(0.82+0.18*cos(radius*11.0))+Color*bevel*0.12;
    }else if(Mode==3){
        vec2 q=p*5.0;float t=0.5+0.45*sin(Time*0.55);
        vec2 curl=vec2(fbm(q+vec2(Time*0.08,0)),fbm(q.yx+vec2(7,-Time*0.06)))-0.5;
        float ink=fbm(q+curl*2.2),filament=abs(sin(q.x*8.0+q.y*3.0+curl.x*12.0));
        float field=ink+r*0.18-filament*0.055;
        float spread=smoothstep(t-0.022,t+0.022,field);
        float edge=exp(-abs(field-t)*100.0),wet=exp(-abs(field-t-0.027)*75.0);
        vec3 pigment=Color*0.025+vec3(0.006,0.004,0.01)*(0.5+ink);
        result=mix(pigment,scene(texCoord+curl/aspect*edge*0.018),spread);
        result+=Color*edge*0.26+vec3(0.45,0.39,0.5)*wet*0.08;
    }else if(Mode==4){
        vec2 d=vec2(sin(Time*0.7),cos(Time*0.5))*0.018;
        d+=vec2(sin(p.y*19.0-Time)*0.003,cos(p.x*15.0+Time)*0.002);
        vec3 h1=texture(HistorySampler,clamp(texCoord-d,0.001,0.999)).rgb;
        vec3 h2=texture(HistorySampler2,clamp(texCoord+d*2.0,0.001,0.999)).rgb;
        vec3 h3=texture(HistorySampler3,clamp(texCoord-d*3.0,0.001,0.999)).rgb;
        vec3 motion=abs(h1-h3),ghost=vec3(h1.r,h2.g,h3.b);
        float residue=clamp(dot(motion,vec3(0.33))*2.0,0.0,0.8);
        float scan=0.8+0.2*sin(p.y*160.0-Time*4.0);
        result=mix(original.rgb,ghost,0.30+residue*0.45)+motion*Color*0.6*scan;
        result+=max(h2-original.rgb,vec3(0))*Color*0.22;
    }else if(Mode==5){
        float front=mod(Time*0.25,0.9),scan=exp(-abs(r-front)*100.0);
        float edge=length(vec2(dFdx(depth),dFdy(depth)))*75.0;
        float sweep=exp(-mod(a-Time*0.8+TAU,TAU)*3.0);
        float rings=pow(0.5+0.5*cos(r*TAU*7.0),28.0);
        float bearing=pow(0.5+0.5*cos(a*24.0),36.0)*smoothstep(0.3,0.32,r)*(1.0-smoothstep(0.42,0.44,r));
        float luminance=dot(original.rgb,vec3(0.3,0.59,0.11));
        result=mix(original.rgb,Color*luminance*0.55,0.65);
        result+=Color*(scan*(0.45+min(edge,2.0))+rings*0.045+bearing*0.22+sweep*min(edge,1.5)*0.3);
    }else if(Mode==6){
        vec2 grid=vec2(54.0*aspect.x,36),cells=texCoord*grid,id=floor(cells),cell=fract(cells)-0.5;
        float speed=0.16+hash(vec2(id.x,3))*0.24,head=fract(Time*speed+hash(vec2(id.x,7)));
        float distance=mod(head-texCoord.y+1.0,1.0),tail=exp(-distance*9.0)*step(0.38,hash(vec2(id.x,9)));
        float glyphSeed=hash(id+floor(Time*4.0));
        float vertical=min(abs(cell.x-0.19),abs(cell.x+0.19));
        float horizontal=min(abs(cell.y),abs(abs(cell.y)-0.25));
        float glyph=min(max(vertical,abs(cell.y)-0.32),max(horizontal,abs(cell.x)-0.22));
        glyph=1.0-smoothstep(0.025,0.065,glyph);
        glyph*=step(abs(cell.x),0.25)*step(abs(cell.y),0.35);
        glyph*=step(0.28,hash(floor((cell+0.5)*vec2(3,5))+glyphSeed));
        float glow=exp(-length(cell*vec2(1,0.6))*5.0)*tail*0.08;
        result=original.rgb*(0.30+0.30*(1.0-tail))+Color*(glyph*tail*0.9+glow);
        result+=vec3(0.65,1,0.8)*glyph*exp(-distance*120.0)*0.4;
    }else if(Mode==7){
        float twist=Time*0.3+log(r+0.045)*0.75;
        vec2 q=rotate(p,twist),direction=normalize(q+0.0001);
        float helix=sin(a*3.0+log(r+0.045)*12.0-Time*2.0);
        vec2 d=direction*(0.006+0.010*r+helix*0.003);
        q*=1.0+helix*0.025;
        result=chroma(q/aspect+0.5,d/aspect);
        result+=Color*exp(-abs(helix)*25.0)*smoothstep(0.05,0.18,r)*0.05+Color*exp(-r*30.0)*0.24;
    }else if(Mode==8){
        float far=fbm(p*3.0+vec2(Time*0.045,-Time*0.06));
        float near=fbm(p*5.5+vec2(-Time*0.055,Time*0.09)+vec2(far*0.5));
        float fog=clamp((far*0.55+near*0.6-0.27)*(0.3+depth*0.7),0.0,0.72);
        vec2 drift=vec2(near-far,far-0.5)*0.018/aspect;
        vec3 shade=mix(Color*0.28,Color,clamp(far*0.8+near*0.3,0.0,1.0));
        result=mix(scene(texCoord+drift),shade,fog);
        vec2 cells=p*65.0+vec2(Time*0.4,Time*0.7),id=floor(cells),local=fract(cells)-0.5;
        float motes=exp(-length(local)*32.0)*step(0.985,hash(id));
        result+=vec3(0.75,0.63,1)*motes*(0.2+0.15*sin(Time+hash(id)*TAU));
    }else if(Mode==9){
        vec2 q=p*10.0,id=floor(q);float f1=10.0,f2=10.0;vec2 seed=vec2(0),first=vec2(0),second=vec2(0);
        for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++){
            vec2 cell=id+vec2(x,y),site=cell+vec2(hash(cell),hash(cell+7.0));float d=length(q-site);
            if(d<f1){f2=f1;second=first;f1=d;first=site;seed=cell;}
            else if(d<f2){f2=d;second=site;}
        }
        vec2 branch=second-first;float length2=max(dot(branch,branch),0.001);
        float along=clamp(dot(q-first,branch)/length2,0.0,1.0);
        float distance=length(q-first-branch*along);
        float link=exp(-distance*45.0),pulse=pow(0.5+0.5*sin(Time*4.5-along*9.0+hash(seed)*TAU),16.0);
        float soma=exp(-f1*13.0),membrane=exp(-abs(f1-0.085)*70.0);
        result=original.rgb*0.50+Color*(link*(0.12+pulse*0.9)+soma*0.4+membrane*0.18);
        result+=vec3(0.7,0.85,1)*soma*pulse*0.7;
    }else if(Mode==10){
        float threshold=0.5+0.4*sin(Time*0.4),n=fbm(p*6.0);
        float alive=smoothstep(threshold-0.015,threshold+0.015,n);
        float ember=exp(-abs(n-threshold)*95.0);
        float charred=exp(-abs(n-threshold+0.045)*25.0)*(1.0-alive);
        float cracks=pow(1.0-abs(sin(p.x*55.0+n*9.0)*sin(p.y*48.0-n*7.0)),12.0);
        float flicker=0.65+0.35*noise(p*35.0+vec2(Time*3.0,-Time*4.0));
        result=original.rgb*alive+vec3(0.035,0.013,0.006)*charred;
        result+=mix(vec3(0.9,0.045,0.001),Color,ember)*ember*flicker*1.8;
        result+=vec3(1,0.52,0.07)*cracks*charred*flicker*0.28;
    }else if(Mode==11){
        vec2 grid=vec2(15.0*aspect.x,10),q=texCoord*grid;
        float column=floor(q.x),speed=0.45+hash(vec2(column,2))*0.6;
        q.y+=Time*speed;vec2 id=floor(q),f=fract(q)-0.5;
        f.x+=sin(hash(id)*TAU)*0.20;
        float elongation=0.68+0.2*hash(id+3.0),d=length(f*vec2(1.0,elongation));
        float drop=1.0-smoothstep(0.14,0.22,d);
        vec2 slope=f*drop;
        float trail=exp(-abs(f.x)*55.0)*step(0.1,f.y)*(1.0-smoothstep(0.25,0.48,f.y));
        result=scene(texCoord+slope/grid*1.8+vec2(trail*0.002,0));
        float rim=exp(-abs(d-0.18)*100.0),highlight=exp(-length((f-vec2(-0.055,0.07))*vec2(1,elongation))*70.0);
        result+=Color*rim*0.06+vec3(highlight*0.6);
        result*=1.0-drop*0.04;
    }else if(Mode==12){
        vec2 left=p-vec2(-0.22,0),right=p-vec2(0.22,0);
        float dl=length(left),dr=length(right),pl=dl*65.0-Time*3.0,pr=dr*68.0+Time*2.1;
        float wave=(sin(pl)/sqrt(1.0+dl*7.0)+sin(pr)/sqrt(1.0+dr*7.0))*0.5;
        vec2 slope=normalize(left+0.0001)*cos(pl)+normalize(right+0.0001)*cos(pr);
        float constructive=pow(abs(wave),5.0),beats=0.5+0.5*cos(pl-pr);
        result=chroma(texCoord+slope/aspect*0.003,slope/aspect*0.0025);
        result=result*(0.92+beats*0.08)+Color*constructive*0.28;
        result+=vec3(0.8,0.9,1)*exp(-min(dl,dr)*90.0)*0.35;
    }else if(Mode==13){
        vec2 dir=p/max(r,0.001);vec3 trail=vec3(0);
        for(int i=0;i<12;i++){float t=float(i)/11.0;trail+=scene(texCoord-dir/aspect*t*0.22*(0.3+r))/12.0;}
        float sector=floor(a*170.0),seed=hash(vec2(sector,4));
        float ray=pow(0.5+0.5*cos(a*170.0),18.0);
        float head=fract(Time*(0.8+seed*0.7)+seed)*1.15;
        float streak=ray*exp(-abs(r-head)*14.0)*step(0.78,seed)*smoothstep(0.08,0.35,r);
        result=mix(original.rgb,trail,0.75)+Color*streak*1.2;
        result+=vec3(0.65,0.82,1)*ray*exp(-abs(r-head)*90.0)*step(0.78,seed)*0.7;
    }else{
        float opening=0.73+0.12*sin(Time*0.7)+0.06*sin(Time*1.9);
        vec2 eye=p*vec2(1.0,2.8/opening);float socket=length(eye),lid=1.0-smoothstep(0.34,0.365,socket);
        vec2 gaze=p-vec2(sin(Time*0.6)*0.035,cos(Time*0.4)*0.015);
        float iris=length(gaze),angle=atan(gaze.y,gaze.x),irisMask=1.0-smoothstep(0.125,0.14,iris);
        float n=noise(gaze*85.0),filaments=pow(0.5+0.5*sin(angle*105.0+n*8.0+sin(iris*80.0)*3.0),3.0);
        float vein=pow(1.0-abs(sin(p.x*40.0+p.y*15.0+fbm(p*14.0)*12.0)),18.0);
        vec3 sclera=mix(vec3(0.23,0.14,0.15),Color*0.30,vein*0.7)*(0.6+0.4*(1.0-socket));
        float pupilRadius=0.033+0.007*sin(Time*1.5),pupilMask=1.0-smoothstep(pupilRadius,pupilRadius+0.012,iris);
        vec3 irisColor=mix(Color*0.12,Color*0.72,filaments)*(0.65+0.35*sin(iris*70.0));
        irisColor+=vec3(0.82,0.51,0.21)*pow(1.0-abs(sin(angle*42.0+n*4.0)),12.0)*0.12;
        vec3 pupil=scene(rotate(gaze,Time*0.16)/aspect*2.4+0.5)*0.20;
        vec3 inside=mix(sclera,mix(irisColor,pupil,pupilMask),irisMask);
        float reflection=exp(-length((gaze-vec2(-0.024,0.032))*vec2(1,1.4))*150.0);
        inside+=vec3(reflection*0.72)*irisMask;
        float rim=exp(-abs(socket-0.35)*110.0);
        result=mix(original.rgb*0.30,inside,lid)+Color*rim*0.22;
    }
    fragColor=vec4(mix(original.rgb,result,Opacity*Intensity),original.a);
}

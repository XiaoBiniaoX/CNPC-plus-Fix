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
        vec2 q=p*6.0;float n=fbm(q+vec2(Time*0.12,-Time*0.08));
        float pulse=sin(Time*2.6+n*9.0)+0.35*sin(Time*4.3+q.y*2.0);
        vec2 muscle=vec2(fbm(q*1.7+Time*0.3),fbm(q*1.9-Time*0.2))-0.5;
        vec2 warp=muscle*(0.055+0.025*pulse)*(0.6+depth*0.4);
        float folds=abs(sin((q.x+muscle.x*3.0)*7.0+fbm(q*3.0)*12.0+pulse));
        float veins=pow(1.0-folds,18.0);
        float wet=pow(max(0.0,sin(q.y*17.0+muscle.y*12.0+Time)),26.0);
        result=mix(scene(texCoord+warp/aspect),Color*(0.25+0.65*n+0.15*pulse),0.62);
        result*=1.0-veins*0.75;result+=vec3(0.5,0.18,0.15)*wet*0.3;
    }else if(Mode==1){
        float flow=fbm(vec2(p.x*7.0,p.y*4.0-Time*0.3));
        vec2 uv=vec2(1.0-texCoord.x,texCoord.y)+vec2(sin(p.y*25.0+Time+flow*7.0),cos(p.x*18.0+Time))*flow*0.035;
        result=scene(uv)*(0.85+0.15*sin(flow*9.0+Time))+Color*pow(flow,5.0)*0.3;
    }else if(Mode==2){
        float sector=TAU/6.0;float angle=abs(mod(a+sector*0.5+Time*0.12,sector)-sector*0.5);
        vec2 uv=vec2(cos(angle),sin(angle))*r;uv=rotate(uv,Time*0.08)/aspect+0.5;
        result=chroma(uv,normalize(p+0.0001)*0.005*sin(Time));
    }else if(Mode==3){
        float t=0.5+0.5*sin(Time*0.65);float ink=fbm(p*8.0+vec2(Time*0.07,0));
        float spread=smoothstep(t-0.06,t+0.06,ink+r*0.25);
        float edge=exp(-abs(ink+r*0.25-t)*80.0);
        result=mix(Color*0.08,original.rgb,spread)+Color*edge*0.8;
    }else if(Mode==4){
        vec2 d=vec2(sin(Time*0.7),cos(Time*0.5))*0.012;
        vec3 h1=texture(HistorySampler,clamp(texCoord-d,0.001,0.999)).rgb;
        vec3 h2=texture(HistorySampler2,clamp(texCoord+d*2.0,0.001,0.999)).rgb;
        vec3 h3=texture(HistorySampler3,clamp(texCoord-d*3.0,0.001,0.999)).rgb;
        result=original.rgb*0.45+vec3(h1.r,h2.g,h3.b)*0.55+abs(h1-h3)*Color*0.5;
    }else if(Mode==5){
        float scan=exp(-abs(r-mod(Time*0.25,0.9))*85.0);
        float edge=length(vec2(dFdx(depth),dFdy(depth)))*75.0;
        float grid=step(0.96,fract(texCoord.x*45.0))+step(0.96,fract(texCoord.y*30.0));
        result=mix(original.rgb,vec3(dot(original.rgb,vec3(0.3,0.59,0.11)))*Color,0.45);
        result+=Color*(scan*(0.3+min(edge,2.0))+grid*0.035);
    }else if(Mode==6){
        vec2 cells=texCoord*vec2(60,40),id=floor(cells);float fall=fract(Time*0.3+hash(vec2(id.x,7)));
        float strip=step(0.75,hash(vec2(id.x,3)))*exp(-abs(texCoord.y-fall)*8.0);
        vec2 uv=(id+0.5)/vec2(60,40);uv.y+=strip*0.12;
        result=mix(original.rgb,scene(uv),0.4+strip*0.6)+Color*strip*0.25;
    }else if(Mode==7){
        vec2 q=rotate(p,Time*0.3+log(r+0.08)*0.8);vec2 d=normalize(q+0.0001)*0.013*(0.2+r);
        result=chroma(q/aspect+0.5,d/aspect)+Color*exp(-r*16.0)*0.4;
    }else if(Mode==8){
        float clouds=fbm(p*4.0+vec2(Time*0.05,-Time*0.08));
        float fog=clamp((clouds-0.25)*(0.25+depth*0.75),0.0,0.7);
        result=mix(scene(texCoord+vec2(clouds-0.5,0)*0.012),Color,fog);
        result+=Color*pow(noise(p*55.0-Time*0.02),15.0)*0.08;
    }else if(Mode==9){
        vec2 q=p*14.0,id=floor(q);float f1=10.0,f2=10.0;vec2 seed=vec2(0);
        for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++){
            vec2 cell=id+vec2(x,y),site=cell+vec2(hash(cell),hash(cell+7.0));float d=length(q-site);
            if(d<f1){f2=f1;f1=d;seed=cell;}else f2=min(f2,d);
        }
        float link=exp(-(f2-f1)*55.0),pulse=pow(0.5+0.5*sin(Time*5.0+hash(seed)*TAU+f1*8.0),8.0);
        result=original.rgb*0.7+Color*(link*(0.25+pulse)+exp(-f1*12.0)*pulse)*1.4;
    }else if(Mode==10){
        float threshold=0.5+0.4*sin(Time*0.4),n=fbm(p*6.0);
        float alive=smoothstep(threshold-0.01,threshold+0.01,n);
        float edge=exp(-abs(n-threshold)*70.0);
        result=original.rgb*alive+vec3(1,0.18,0.01)*edge*2.0+Color*pow(edge,3.0);
    }else if(Mode==11){
        vec2 q=texCoord*vec2(18,12);q.y+=Time*0.9;vec2 id=floor(q),f=fract(q)-0.5;
        f.x+=sin(hash(id)*TAU)*0.16;float d=length(f*vec2(1.0,0.75));
        float drop=1.0-smoothstep(0.12,0.24,d);
        result=scene(texCoord+f*drop*0.05)+Color*exp(-abs(d-0.18)*100.0)*0.17;
    }else if(Mode==12){
        float wave=sin(length(p-vec2(-0.22,0))*75.0-Time*3.0)+sin(length(p-vec2(0.22,0))*79.0+Time*2.0);
        float band=0.5+0.5*sin(wave*4.0+Time);
        result=chroma(texCoord,vec2(wave*0.007,band*0.004));result=mix(result,Color*band,0.2);
    }else if(Mode==13){
        vec2 dir=p/max(r,0.001);vec3 trail=vec3(0);
        for(int i=0;i<12;i++){float t=float(i)/11.0;trail+=scene(texCoord-dir/aspect*t*0.22*(0.3+r))/12.0;}
        float streak=pow(hash(vec2(floor(a*140.0),floor(r*15.0-Time*8.0))),28.0)*smoothstep(0.05,0.4,r);
        result=mix(original.rgb,trail,0.75)+Color*streak*2.0;
    }else{
        vec2 eye=p*vec2(1.0,2.8);float lid=1.0-smoothstep(0.34,0.37,length(eye));
        float iris=length(p),irisMask=1.0-smoothstep(0.12,0.14,iris);
        float filaments=pow(0.5+0.5*sin(a*95.0+noise(p*90.0)*8.0+Time),4.0);
        vec3 tissue=Color*(0.25+0.4*fbm(p*25.0+Time*0.08));
        vec3 pupil=scene(rotate(p,Time*0.2)/aspect*2.4+0.5)*(1.0-smoothstep(0.035,0.06,iris));
        vec3 inside=mix(tissue,Color*filaments*1.4+pupil,irisMask);
        result=mix(original.rgb*0.35,inside,lid)+Color*exp(-abs(length(eye)-0.35)*90.0)*0.45;
    }
    fragColor=vec4(mix(original.rgb,result,Opacity*Intensity),original.a);
}

#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform sampler2D HistorySampler;
uniform sampler2D HistorySampler2;
uniform sampler2D HistorySampler3;
uniform mat4 WorldViewProjection;
uniform vec3 CameraPosition;
uniform vec3 WorldPosition;
uniform vec2 OutSize;
uniform vec2 ScreenCenter;
uniform vec3 Color;
uniform float Radius;
uniform float Distortion;
uniform float Dispersion;
uniform float Trail;
uniform float Progress;
uniform float Intensity;
uniform float Opacity;
uniform float Time;
uniform int Mode;
in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) { vec3 q=fract(vec3(p.xyx)*0.1031); q+=dot(q,q.yzx+33.33); return fract((q.x+q.y)*q.z); }
vec3 scene(vec2 p) { return texture(DiffuseSampler,clamp(p,vec2(0.001),vec2(0.999))).rgb; }
vec3 rgb(vec2 p, vec2 offset) { return vec3(scene(p+offset).r,scene(p).g,scene(p-offset).b); }
vec2 turn(vec2 p,float a) { float c=cos(a),s=sin(a); return mat2(c,-s,s,c)*p; }

void main() {
    vec4 original=texture(DiffuseSampler,texCoord);
    vec3 result=original.rgb;
    vec2 aspect=vec2(OutSize.x/OutSize.y,1);
    vec2 p=(texCoord-ScreenCenter)*aspect;
    float r=length(p);
    if (Mode == 0) {
        // Exactly normal central disc; edge-only distortion and genuine previous-frame textures.
        float edge=smoothstep(Radius*0.32,Radius,r)*(1.0-smoothstep(Radius,Radius*1.15,r));
        vec2 direction=normalize(p+vec2(0.0001));
        vec2 warp=direction*sin(r*46.0-Time*2.0)*Distortion*0.022*edge/aspect;
        result=rgb(texCoord+warp,direction*Dispersion*0.012*edge/aspect);
        vec3 h1=texture(HistorySampler,clamp(texCoord-warp*0.7,0.001,0.999)).rgb;
        vec3 h2=texture(HistorySampler2,clamp(texCoord-warp*1.4,0.001,0.999)).rgb;
        vec3 h3=texture(HistorySampler3,clamp(texCoord-warp*2.1,0.001,0.999)).rgb;
        result=mix(result,h1*0.5+h2*0.3+h3*0.2,Trail*edge);
        result+=Color*exp(-abs(r-Radius)*180.0)*0.15*Intensity;
    } else if (Mode == 1) {
        // Voronoi fracture: opposite offsets on independently oriented shards.
        vec2 cell=texCoord*vec2(10,6), base=floor(cell);
        float nearest=100.0, second=100.0;
        vec2 shard=vec2(0), closest=vec2(0);
        for (int y=-1;y<=1;y++) for (int x=-1;x<=1;x++) {
            vec2 id=base+vec2(x,y);
            vec2 site=id+vec2(hash(id),hash(id+19.7))*0.8+0.1;
            float distance=length(site-cell);
            if (distance<nearest) { second=nearest; nearest=distance; shard=id; closest=site; }
            else second=min(second,distance);
        }
        float crack=second-nearest;
        float width=0.022+0.009*sin(Time+hash(shard)*5.0);
        vec2 offset=(vec2(hash(shard+3.0),hash(shard+11.0))-0.5)*Distortion*0.065;
        result=rgb(texCoord+offset,offset*Dispersion*0.5);
        float fissure=1.0-smoothstep(width,width+0.025,crack);
        vec2 alien=turn((cell-closest)*2.0,Time*0.2);
        vec3 other=Color*(0.3+0.7*sin(alien.x*5.0+alien.y*7.0-Time)*sin(alien.y*11.0+Time));
        other+=vec3(0.04,0.01,0.15)+vec3(0.7,0.9,1)*pow(hash(floor(alien*80.0)),30.0);
        result=mix(result,other,fissure);
        result+=Color*exp(-abs(crack-width)*90.0)*0.35*Intensity;
    } else if (Mode == 2) {
        // Two prism layers, each with independently displaced R/G/B and different axes.
        float depth=texture(DepthSampler,texCoord).r;
        vec2 axis=vec2(cos(Time*0.17),sin(Time*0.17));
        vec2 offset=axis*Dispersion*0.014*(0.35+r)*(0.7+depth*0.3);
        vec3 first=rgb(texCoord,offset);
        vec2 facet=floor(texCoord*vec2(8,5));
        vec2 second=vec2(-axis.y,axis.x)*Distortion*0.012*(hash(facet)-0.5);
        result=mix(first,rgb(texCoord+second,offset*1.8+second),0.5);
        result+=Color*pow(max(0.0,1.0-abs(sin((texCoord.x+texCoord.y)*35.0))),30.0)*Intensity*0.035;
    } else {
        vec4 clip=WorldViewProjection*vec4(WorldPosition-CameraPosition,1.0);
        vec2 center=clip.w>0.0 ? clip.xy/clip.w*0.5+0.5 : ScreenCenter;
        vec2 delta=(texCoord-center)*aspect;
        float radius=length(delta);
        // Progress 0..0.68: local pull; 0.55..0.78: whole frame contracts; 0.78..0.84: hold the point.
        // 0.84..1: release wave and reconstruction. Sampling is inverse mapping, not an enlarged image.
        float collapse=smoothstep(0.0,0.68,Progress);
        float scale=1.0-smoothstep(0.55,0.78,Progress)*0.998;
        float release=smoothstep(0.84,1.0,Progress);
        scale=mix(scale,1.0,release);
        vec2 source=center+(texCoord-center)/max(scale,0.002);
        float local=exp(-radius*radius/max(Radius*Radius,0.001))*collapse*(1.0-release);
        vec2 warped=turn(source-center,local*Distortion*1.2)+center;
        warped=center+(warped-center)*(1.0+local*Distortion*0.9);
        float band=floor(texCoord.y*OutSize.y/7.0);
        warped.x+=(hash(vec2(band,floor(Time*18.0)))-0.5)*0.05*local;
        vec2 separation=normalize(delta+vec2(0.0001))*Dispersion*0.015*collapse*(1.0-release)/aspect;
        result=rgb(warped,separation);
        if (source.x<0.0 || source.x>1.0 || source.y<0.0 || source.y>1.0) result=vec3(0);
        float dust=hash(floor(texCoord*OutSize/3.0)+floor(Time*12.0));
        result*=mix(1.0,smoothstep(0.15,1.0,dust),collapse*(1.0-release));
        float point=exp(-radius*radius/0.000035)*smoothstep(0.5,0.8,Progress)*(1.0-release);
        float wave=exp(-abs(radius-release*1.5)*65.0)*smoothstep(0.0,0.02,release)*(1.0-release);
        result+=Color*(point*3.0+wave*2.0)*Intensity;
    }
    fragColor=vec4(mix(original.rgb,result,Opacity),original.a);
}

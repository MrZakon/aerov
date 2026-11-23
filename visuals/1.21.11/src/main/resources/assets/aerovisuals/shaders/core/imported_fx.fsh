#version 330
#ifndef AERO_BLEND
uniform sampler2D Scene;
#endif
uniform sampler2D Depth;
layout(std140) uniform Effects {
    mat4 InverseProjection;vec4 Frame;
    vec4 Fog;vec4 FogColor;vec4 Hand;vec4 HandColor;vec4 BlurOptions;
};
in vec2 uv;out vec4 fragColor;
#ifndef AERO_BLEND
vec3 sampleScene(vec2 p){return texture(Scene,clamp(p,vec2(.001),vec2(.999))).rgb;}
const vec2 blurOffsets[6]=vec2[6](
    vec2(1,0),vec2(.5,.8660254),vec2(-.5,.8660254),
    vec2(-1,0),vec2(-.5,-.8660254),vec2(.5,-.8660254)
);
#endif
#ifndef AERO_BLEND
const vec2 fastBlurOffsets[4] = vec2[4](vec2(1,0), vec2(0,1), vec2(-1,0), vec2(0,-1));
#endif
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
void main(){
    float depth=texture(Depth,uv).r;

    if(Frame.w<.5){
        if(depth>=.99999)discard;
        vec4 view=InverseProjection*vec4(uv*2.0-1.0,depth*2.0-1.0,1.0);
        float w=max(abs(view.w),.00001);
        float start=Fog.x*w;
        float distanceSquared=dot(view.xyz,view.xyz);
        if(distanceSquared<=start*start)discard;
        float fog=smoothstep(Fog.x,Fog.x+Fog.y,sqrt(distanceSquared)/w)*Fog.z;
        if(fog<=0.0)discard;
#ifdef AERO_BLEND
        fragColor=vec4(FogColor.rgb,fog);
#else
        vec3 base=sampleScene(uv);
        float radius=Fog.w*fog;
        if(radius>.5){
            vec3 sum=base;
            float total=1.0;
            int taps = int(BlurOptions.x);
            for (int i = 0; i < taps; i++) {
                vec2 offset = taps == 6 ? blurOffsets[i] : fastBlurOffsets[taps == 2 ? i * 2 : i];
                vec2 at = clamp(uv + offset * radius / Frame.xy, vec2(0), vec2(1));
                float other=texture(Depth,at).r;
                float weight=exp(-abs(depth-other)*1000.0);
                sum+=sampleScene(at)*weight;
                total+=weight;
            }
            base=mix(base,sum/total,fog);
        }
        fragColor=vec4(mix(base,FogColor.rgb,fog),1);
#endif
        return;
    }

#ifndef AERO_BLEND
    // Only glass needs neighbor depth derivatives. Evaluate before discarding background.
    vec2 d=vec2(dFdx(depth),dFdy(depth));
#endif
    if(depth>=.99999)discard;
    float time=Frame.z*Hand.z;
    vec2 p=uv*Hand.w*10.0;
#ifdef AERO_BLEND
    float waves=.5+.5*sin(p.x*1.8+sin(p.y*2.0+time)*2.0+time);
    vec3 material;
    if(Hand.x<1.5){
        material=mix(HandColor.rgb*.15,HandColor.rgb*1.5+vec3(.12,.04,.15),waves);
    }else{
        vec2 cell=floor(p*16.0);
        float stars=step(.985,hash(cell))*pow(max(0.0,1.0-length(fract(p*16.0)-.5)*2.0),4.0);
        material=HandColor.rgb*(.15+.8*waves*waves)+vec3(stars);
    }
    fragColor=vec4(material,Hand.y);
#else
    vec2 normal=normalize(d+vec2(.000001));
    vec2 bend=normal*.008+vec2(sin(p.y+time),cos(p.x-time))*.002;
    vec3 material=sampleScene(uv+bend)*.85+HandColor.rgb*.12;
    float rim=0.0;
    for(int i=0;i<4;i++){
        vec2 offset=vec2(i==0?1:i==1?-1:0,i==2?1:i==3?-1:0)*2.0/Frame.xy;
        rim=max(rim,step(.99999,texture(Depth,clamp(uv+offset,vec2(0),vec2(1))).r));
    }
    material+=vec3(.5,.65,.8)*rim;
    fragColor=vec4(mix(sampleScene(uv),material,Hand.y),1);
#endif
}

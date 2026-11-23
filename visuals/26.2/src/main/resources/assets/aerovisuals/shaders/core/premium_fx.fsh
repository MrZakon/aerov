#version 330
in vec2 uv;
out vec4 fragColor;
#ifndef AERO_WARMTH
uniform sampler2D Depth;
#endif
#if !defined(AERO_REPLACE_SKY) && !defined(AERO_WARMTH)
uniform sampler2D Scene;
uniform sampler2D Glint;
#endif
layout(std140) uniform Premium {
    mat4 inverseViewProjection;
    vec4 viewportTime;
    vec4 skySettings;
    vec4 skyColor;
    vec4 grading;
    vec4 glintSettings;
    vec4 glintColor;
    vec4 camera;
    vec4 reserved;
};
vec3 warmthMultiplier(){return vec3(1.+grading.y*.08,1.+grading.y*.015,1.-grading.y*.08);}
#ifdef AERO_WARMTH
void main(){
    // Both RGB blend terms contribute scene * halfFactor, including on normalized targets.
    fragColor=vec4(warmthMultiplier()*.5,1.);
}
#else
float hash(vec3 p){p=fract(p*.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z);}
float noise(vec3 p){vec3 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);}
float fbm(vec3 p){float n=0.,a=.5;for(int i=0;i<4;i++){n+=noise(p)*a;p=p*2.07+vec3(4.1,7.3,2.7);a*=.5;}return n;}
float luminance(vec3 color){return dot(color,vec3(.2126,.7152,.0722));}
vec3 proceduralSky(vec3 ray){
    float t=viewportTime.z*skySettings.y;vec3 p=ray*3.;
    float n=fbm(p+vec3(t*.04,0,t*.03)),nebula=pow(max(0.,1.-abs(n-.52)*2.),3.);
    if(skySettings.x>1.5&&skySettings.x<2.5)nebula*=exp(-abs(ray.y+ray.x*.3)*5.)*2.;
    if(skySettings.x>2.5){float a=atan(ray.z,ray.x),r=length(ray.xz);nebula=pow(.5+.5*sin(a*5.+r*16.-t*.6+fbm(p)*5.),4.)*exp(-abs(ray.y)*2.);}
    vec3 cell=floor(ray*650.);float stars=step(.9985,hash(cell))*pow(max(0.,1.-length(fract(ray*650.)-.5)*1.8),6.);
    return (mix(vec3(.012,.018,.045),skyColor.rgb*.65,nebula)+nebula*nebula*skyColor.bgr*.25+stars*1.5)*skySettings.z;
}
vec3 softClouds(vec3 background,vec3 ray){
    if(abs(ray.y)<.015)return background;
    // Integrate a soft volume instead of discarding an entire plane at camera height.
    float base=reserved.z-10.,top=reserved.z+28.;
    float a=(base-camera.y)/ray.y,b=(top-camera.y)/ray.y;
    float start=max(0.,min(a,b)),end=min(12000.,max(a,b));
    if(end<=start)return background;
    float stepLength=(end-start)/2.,density=0.;
    for(int i=0;i<2;i++){
        float distance=start+(float(i)+.5)*stepLength;
        float height=clamp((camera.y+ray.y*distance-base)/(top-base),0.,1.);
        float edge=smoothstep(0.,.25,height)*(1.-smoothstep(.65,1.,height));
        vec2 p=(camera.xz+ray.xz*distance)*.0028+vec2(viewportTime.z*.003,viewportTime.z*.0009);
        float cloud=fbm(vec3(p,3.1+height*.73));
        density+=smoothstep(.47,.7,cloud)*edge*min(stepLength/38.,1.);
    }
    float opacity=(1.-exp(-density*skySettings.w*2.2))*smoothstep(.015,.16,abs(ray.y));
    vec3 light=mix(vec3(.14,.18,.27),vec3(.9,.93,1.),clamp(luminance(background)*2.,0.,1.));
    return mix(background,light,opacity);
}
vec3 viewRay(){
    vec4 direction=inverseViewProjection*vec4(uv*2.-1.,1.,1.);
    return normalize(direction.xyz*(direction.w<0.?-1.:1.));
}
#ifdef AERO_REPLACE_SKY
void main(){
    if(texture(Depth,uv).r>0.0)discard;
    vec3 ray=viewRay();
    vec3 sky=proceduralSky(ray);
    if(skySettings.w>0.)sky=softClouds(sky,ray);
    fragColor=vec4(sky,1.);
}
#else
vec3 sceneAt(vec2 at){return texture(Scene,clamp(at,vec2(0),vec2(1))).rgb;}
float maskAt(vec2 at){return clamp(luminance(texture(Glint,clamp(at,vec2(0),vec2(1))).rgb),0.,1.);}
vec3 brightAt(vec2 at){
    if(reserved.w>.5&&texture(Depth,clamp(at,vec2(0),vec2(1))).r<=0.0)return vec3(0.);
    vec3 value=sceneAt(at);float brightness=luminance(value),threshold=reserved.y;
    return value*smoothstep(max(0.,threshold-.12),min(1.,threshold+.12),brightness)*.45;
}
void main(){
    vec2 pixel=1./viewportTime.xy;
    if(viewportTime.w>.5){
        if(skySettings.w<=0.||!(texture(Depth,uv).r<=0.0))discard;
        fragColor=vec4(softClouds(sceneAt(uv),viewRay()),1.);
        return;
    }
    vec3 scene=sceneAt(uv);
    scene*=warmthMultiplier();
    if(grading.x>0.){
        vec3 bloom=vec3(0.);float radius=reserved.x*viewportTime.y/1080.;
        const vec2 axes[4]=vec2[4](vec2(1,0),vec2(0,1),vec2(-1,0),vec2(0,-1));
        for(int i=0;i<4;i++){
            vec2 axis=axes[i];
            if(grading.z>.5){
                bloom+=brightAt(uv+axis*pixel*radius*.58);
            }else{
                vec2 diagonal=vec2(axis.x-axis.y,axis.x+axis.y)*.70710678;
                bloom+=brightAt(uv+axis*pixel*radius*.35)*.65+brightAt(uv+diagonal*pixel*radius)*.35;
            }
        }
        // Screen blend preserves detail and prevents a white wash over the whole scene.
        bloom=clamp(bloom*(grading.x/4.),0.,.45);
        scene=1.-(1.-clamp(scene,0.,1.))*(1.-bloom);
    }
    if(glintSettings.x>0.){
        float core=maskAt(uv),halo=0.;
        for(int i=0;i<8;i++){
            float angle=float(i)*.785398;
            halo+=maskAt(uv+vec2(cos(angle),sin(angle))*pixel*glintSettings.y);
        }
        float energy=1.-exp(-(core+halo*.01875)*glintSettings.x*.8);
        vec3 tint=glintColor.rgb;
        scene=mix(scene,scene*mix(vec3(1.),tint,.3),core*.3);
        scene=1.-(1.-clamp(scene,0.,1.))*(1.-tint*energy*.62);
    }
    fragColor=vec4(scene,1.);
}
#endif
#endif

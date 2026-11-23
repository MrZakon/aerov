#version 330
#extension GL_ARB_separate_shader_objects : require
layout(location = 0) in vec2 starUV;
layout(location = 1) in vec4 starColor;
layout(location = 0) out vec4 fragColor;
void main(){
    if(starUV.x<0.){
        float edge=smoothstep(0.,.28,starUV.y)*(1.-smoothstep(.72,1.,starUV.y));
        fragColor=vec4(starColor.rgb,starColor.a*edge);
        return;
    }
    vec2 p=starUV*2.-1.;
    vec2 a=abs(p);
    float halo=exp(-dot(p,p)*7.5)*.18;
    float shape=pow(a.x,.55)+pow(a.y,.55);
    float core=1.-smoothstep(.72,.88,shape);
    float alpha=(halo+core*.85)*(1.-smoothstep(.78,1.,max(a.x,a.y)))*starColor.a;
    if(alpha<.003)discard;
    fragColor=vec4(mix(starColor.rgb,vec3(1.),core*.6),alpha);
}

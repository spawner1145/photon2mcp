#version 330 core

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <photon:particle.glsl>

#ifdef PARTICLE_INSTANCE
layout(location = 8) in float GateProgress;
#endif

layout(std140) uniform PhotonCustomMaterial {
    vec4 GateColor;
    float Lifetime;
    float WarpStrength;
};

out vec2 gateUV;
out float gateTick;

void main() {
    ParticleData data = getParticleData();
    vec3 position = data.Position;
#ifdef PHOTON_INSTANCED
    position += ModelOffset;
#endif
    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);
    gateUV = data.UV;
#ifdef PARTICLE_INSTANCE
    gateTick = GateProgress * Lifetime;
#else
    gateTick = 0.0;
#endif
}

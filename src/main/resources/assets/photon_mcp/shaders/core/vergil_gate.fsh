#version 330 core

#moj_import <photon:engine.glsl>

uniform sampler2D SamplerSceneColor;
layout(std140) uniform PhotonCustomMaterial {
    vec4 GateColor;
    float Lifetime;
    float WarpStrength;
};

in vec2 gateUV;
in float gateTick;
out vec4 fragColor;

float hash21(vec2 point) {
    return fract(sin(dot(point, vec2(127.1, 311.7))) * 43758.5453);
}

float noise21(vec2 point) {
    vec2 cell = floor(point);
    vec2 fraction = fract(point);
    fraction = fraction * fraction * (3.0 - 2.0 * fraction);
    return mix(mix(hash21(cell), hash21(cell + vec2(1.0, 0.0)), fraction.x),
               mix(hash21(cell + vec2(0.0, 1.0)), hash21(cell + vec2(1.0)), fraction.x), fraction.y);
}

void main() {
    vec2 point = gateUV * 2.0 - 1.0;
    float seconds = gateTick * 0.05;
    float horizontal = smoothstep(0.0, 20.0, gateTick);
    float vertical = smoothstep(22.0, 40.0, gateTick);
    float opening = smoothstep(43.0, 78.0, gateTick);
    float closing = 1.0 - smoothstep(170.0, 195.0, gateTick);
    float amplitude = closing * smoothstep(0.0, 3.0, gateTick);
    float fineNoise = noise21(vec2(point.y * 35.0, seconds * 2.0)) - 0.5;
    float height = 0.80;
    float width = 0.32 * opening;
    float boundary = pow(max(0.0, 1.0 - abs(point.y) / height), 0.65) * width;
    boundary += (fineNoise * 0.016 + sin(point.y * 54.0 + seconds * 8.0) * 0.007) * opening;
    float slit = abs(point.x) - max(0.001, boundary);
    float inside = (1.0 - smoothstep(-0.004, 0.004, slit)) * (1.0 - smoothstep(height - 0.012, height, abs(point.y))) * opening;
    float horizontalDistance = abs(point.y + sin(point.x * 38.0) * 0.0015);
    float horizontalCap = 1.0 - smoothstep(0.62 * horizontal - 0.018, 0.62 * horizontal, abs(point.x));
    float horizontalGlow = exp(-horizontalDistance * 180.0) * horizontalCap * horizontal;
    float verticalGlow = exp(-abs(point.x) * 210.0) * (1.0 - smoothstep(0.80 * vertical - 0.02, 0.80 * vertical, abs(point.y))) * vertical;
    float edgeBand = exp(-abs(slit) * 210.0) * (1.0 - smoothstep(height - 0.014, height + 0.012, abs(point.y))) * opening;
    float haze = exp(-abs(slit) * 28.0) * (1.0 - smoothstep(0.78, 0.95, abs(point.y))) * opening;
    float flash = exp(-pow((gateTick - 44.0) / 4.0, 2.0)) * exp(-length(point) * 7.0);
    float tears = noise21(vec2(point.y * 65.0, seconds * 6.0));
    float edgeLight = edgeBand * (0.65 + tears * 1.1);
    float electric = exp(-abs(abs(point.x) - boundary - 0.03 - fineNoise * 0.05) * 180.0) * opening;
    electric *= step(0.7, noise21(vec2(point.y * 20.0, seconds * 5.0))) * (1.0 - smoothstep(0.5, 0.82, abs(point.y)));
    float cross = horizontalGlow * (1.0 - inside) + verticalGlow * (1.0 - opening);
    float warpEnvelope = exp(-abs(slit) * 13.0) * (1.0 - smoothstep(0.78, 0.96, abs(point.y))) * opening;
    vec2 screenUV = gl_FragCoord.xy / U_ViewPort.zw;
    vec2 direction = normalize(vec2(point.x + 0.0001, point.y * 0.3));
    vec2 displacement = direction * WarpStrength * warpEnvelope;
    displacement += vec2(sin(point.y * 45.0 + seconds * 12.0), cos(point.x * 25.0 - seconds * 7.0)) * WarpStrength * 0.18 * warpEnvelope;
    vec2 warpedUV = clamp(screenUV + displacement, vec2(0.002), vec2(0.998));
    vec3 warped = texture(SamplerSceneColor, warpedUV).rgb;
    warped.r = texture(SamplerSceneColor, clamp(warpedUV + displacement * 0.16, vec2(0.002), vec2(0.998))).r;
    warped.b = texture(SamplerSceneColor, clamp(warpedUV - displacement * 0.16, vec2(0.002), vec2(0.998))).b;
    float radius = length(vec2(point.x * 1.8, point.y));
    float angle = atan(point.y, point.x * 1.8);
    float spiral = pow(max(0.0, sin(radius * 34.0 + angle * 3.0 - seconds * 2.5)), 9.0);
    float dust = pow(noise21(point * 95.0 + seconds * vec2(0.6, -1.2)), 18.0);
    vec3 voidColor = vec3(0.0015, 0.003, 0.013) + vec3(0.012, 0.025, 0.10) * spiral * radius;
    voidColor += vec3(0.12, 0.26, 0.65) * dust;
    vec3 color = mix(warped, voidColor, inside * 0.985);
    color += GateColor.rgb * (cross * 3.8 + edgeLight * 2.9 + haze * 0.12 + electric * 2.0 + flash * 2.0);
    color += vec3(1.1, 1.5, 2.1) * (pow(cross, 3.0) + pow(edgeLight, 3.0) * 0.7);
    float alpha = max(inside, max(warpEnvelope * 0.98, max(cross * 2.0, max(haze * 0.8, flash))));
    alpha = clamp(alpha * amplitude, 0.0, 1.0);
    if (alpha < 0.002) discard;
    fragColor = vec4(color, alpha);
}

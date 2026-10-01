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

float pulse(float center, float width) {
    return exp(-pow((gateTick - center) / width, 2.0));
}

void main() {
    vec2 point = gateUV * 2.0 - 1.0;
    point.y = -point.y;
    float seconds = gateTick * 0.05;
    float width = 0.80;
    float height = 0.72;
    float verticalProgress = clamp((gateTick - 2.0) / 5.0, 0.0, 1.0);
    float horizontalProgress = clamp((gateTick - 12.0) / 5.0, 0.0, 1.0);
    float opening = smoothstep(23.0, 39.0, gateTick);
    float closing = 1.0 - smoothstep(174.0, 197.0, gateTick);
    float alive = smoothstep(1.5, 2.2, gateTick) * closing;
    float verticalFront = mix(height, -height, verticalProgress);
    float horizontalFront = mix(-width, width, horizontalProgress);
    float verticalMask = smoothstep(verticalFront - 0.008, verticalFront + 0.008, point.y);
    verticalMask *= 1.0 - smoothstep(height - 0.008, height, abs(point.y));
    float horizontalMask = 1.0 - smoothstep(horizontalFront - 0.008, horizontalFront + 0.008, point.x);
    horizontalMask *= 1.0 - smoothstep(width - 0.008, width, abs(point.x));
    horizontalMask *= smoothstep(11.5, 12.1, gateTick);
    float verticalDistance = abs(point.x - sin(point.y * 75.0) * 0.0009);
    float horizontalDistance = abs(point.y - sin(point.x * 65.0) * 0.0010);
    float verticalLine = exp(-verticalDistance * 470.0) * verticalMask;
    float horizontalLine = exp(-horizontalDistance * 430.0) * horizontalMask;
    float verticalHalo = exp(-verticalDistance * 115.0) * verticalMask;
    float horizontalHalo = exp(-horizontalDistance * 110.0) * horizontalMask;
    float cutHead = exp(-length((point - vec2(0.0, verticalFront)) * vec2(1.7, 1.0)) * 95.0);
    cutHead *= step(gateTick, 7.0);
    cutHead += exp(-length((point - vec2(horizontalFront, 0.0)) * vec2(1.0, 1.6)) * 95.0)
               * step(12.0, gateTick) * step(gateTick, 17.0);
    float cutImpact = pulse(7.0, 1.0) * verticalHalo + pulse(17.0, 1.0) * horizontalHalo;
    float lineFade = 1.0 - smoothstep(23.0, 29.0, gateTick);
    float crossCore = (verticalLine + horizontalLine) * lineFade;
    float crossHalo = (verticalHalo + horizontalHalo) * lineFade;

    float openHeight = max(0.0005, height * opening * closing);
    vec2 diamondGradient = vec2(1.0 / width, 1.0 / openHeight);
    float metric = abs(point.x) / width + abs(point.y) / openHeight - 1.0;
    float distanceToEdge = metric / length(diamondGradient);
    float flow = seconds * 1.8;
    float broadNoise = noise21(point * vec2(7.0, 11.0) + vec2(flow, -flow * 0.7)) - 0.5;
    float fineNoise = noise21(point * vec2(38.0, 62.0) + vec2(-flow * 2.1, flow * 1.9)) - 0.5;
    float filamentNoise = noise21(point * vec2(85.0, 105.0) + flow * 3.0) - 0.5;
    float ripple = (broadNoise * 0.100 + fineNoise * 0.032 + filamentNoise * 0.009) * opening;
    float signedEdge = distanceToEdge - ripple;
    float portalMask = smoothstep(23.0, 24.5, gateTick);
    float inside = (1.0 - smoothstep(-0.003, 0.003, signedEdge)) * portalMask;
    float tipMask = (1.0 - smoothstep(width + 0.03, width + 0.13, abs(point.x)))
                  * (1.0 - smoothstep(openHeight + 0.035, openHeight + 0.13, abs(point.y)));
    float edgeCore = exp(-abs(signedEdge) * 340.0) * tipMask * portalMask;
    float edgeHalo = exp(-abs(signedEdge) * 65.0) * tipMask * portalMask;
    float branchDistance = signedEdge - 0.040 - broadNoise * 0.100 - fineNoise * 0.055;
    float branchMask = smoothstep(0.42, 0.67, noise21(point * vec2(16.0, 28.0) + vec2(-flow, flow)));
    float branches = exp(-abs(branchDistance) * 330.0) * branchMask * tipMask * portalMask;
    float innerThreads = exp(-abs(signedEdge + 0.026 + fineNoise * 0.030) * 400.0);
    innerThreads *= smoothstep(0.48, 0.72, noise21(point * 44.0 - flow * 2.0)) * tipMask * portalMask;
    vec2 curlPoint = point + vec2(sin(point.y * 32.0 + flow), cos(point.x * 24.0 - flow)) * 0.018;
    float curlingField = noise21(curlPoint * vec2(28.0, 45.0) + vec2(-flow, flow * 1.3));
    float curls = exp(-abs(curlingField - 0.54) * 160.0);
    curls *= exp(-pow((signedEdge - 0.035) / 0.055, 2.0)) * branchMask * tipMask * portalMask;
    float shards = pow(noise21(point * vec2(100.0, 145.0) + vec2(-seconds * 3.0, seconds)), 12.0);
    shards *= exp(-abs(signedEdge - 0.045) * 28.0) * tipMask * portalMask;
    float flicker = 0.85 + 0.5 * noise21(point * vec2(45.0, 65.0) + seconds * 5.0);

    vec2 edgeNormal = normalize(sign(point + vec2(0.0001)) * diamondGradient);
    vec2 edgeTangent = vec2(-edgeNormal.y, edgeNormal.x);
    float warpEnvelope = exp(-pow(signedEdge / 0.10, 2.0)) * tipMask * portalMask;
    float breathing = 0.65 + 0.25 * sin(seconds * 4.0 + point.x * 9.0) + broadNoise;
    vec2 displacement = edgeNormal * (0.45 + breathing * 0.55);
    displacement += edgeTangent * (sin(point.x * 42.0 + point.y * 36.0 - seconds * 8.0) * 0.38 + fineNoise);
    displacement *= WarpStrength * warpEnvelope;
    vec2 screenUV = gl_FragCoord.xy / U_ViewPort.zw;
    vec2 warpedUV = clamp(screenUV + displacement, vec2(0.002), vec2(0.998));
    vec3 warped = texture(SamplerSceneColor, warpedUV).rgb;
    warped.r = texture(SamplerSceneColor, clamp(warpedUV + displacement * 0.09, vec2(0.002), vec2(0.998))).r;
    warped.b = texture(SamplerSceneColor, clamp(warpedUV - displacement * 0.09, vec2(0.002), vec2(0.998))).b;
    float nebula = noise21(point * 8.0 + vec2(seconds * 0.11, -seconds * 0.07));
    float stars = pow(noise21(point * 160.0 + vec2(seconds * 0.07, -seconds * 0.12)), 22.0);
    vec3 voidColor = vec3(0.004, 0.0015, 0.010) + vec3(0.022, 0.003, 0.030) * pow(nebula, 3.0);
    voidColor += vec3(0.65, 0.52, 0.95) * stars * 0.35;
    vec3 color = mix(warped, voidColor, inside * 0.995);
    float electric = (edgeCore * 2.3 + branches * 1.9 + innerThreads * 1.5 + curls * 1.1) * flicker;
    color += GateColor.rgb * (crossHalo * 0.6 + edgeHalo * 0.32 + electric + shards * 4.0);
    color += vec3(1.65, 1.7, 2.1) * (crossCore * 2.0 + edgeCore * 1.15 + branches * 0.85 + innerThreads * 0.75 + curls * 0.45);
    color += vec3(1.0, 1.05, 1.25) * (cutHead * 4.0 + cutImpact * 2.2);
    float openingFlash = pulse(25.5, 1.8) * exp(-abs(point.y) * 35.0) * (1.0 - smoothstep(width - 0.02, width, abs(point.x)));
    color += GateColor.rgb * openingFlash * 1.8;
    float alpha = max(inside, max(warpEnvelope * 0.96, max(edgeHalo * 1.7, max(branches * 2.0, shards * 8.0))));
    alpha = max(alpha, curls * 1.5);
    alpha = max(alpha, max(crossHalo * 2.0, max(cutHead, max(cutImpact, openingFlash))));
    alpha = clamp(alpha * alive, 0.0, 1.0);
    if (alpha < 0.002) discard;
    fragColor = vec4(color, alpha);
}

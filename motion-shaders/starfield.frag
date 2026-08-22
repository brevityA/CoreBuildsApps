#version 330 core
// Core Motion — Starfield
// Twinkling stars with a slow drift. Seamless: star positions and twinkle
// phase are periodic in T seconds (positions wrap via fract).
uniform vec2 u_resolution;
uniform float u_time;
out vec4 fragColor;

const float TAU = 6.28318530718;
const float T = 20.0;

const vec3 NIGHT = vec3(0.051, 0.067, 0.090); // #0D1117
const vec3 CYAN  = vec3(0.000, 0.898, 1.000); // #00E5FF
const vec3 BLUE  = vec3(0.310, 0.675, 0.996); // #4FACFE

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

void main() {
    vec2 uv = (2.0 * gl_FragCoord.xy - u_resolution) / min(u_resolution.x, u_resolution.y);
    float lt = TAU * u_time / T; // periodic loop time

    vec3 col = NIGHT;

    // Layered starfields; each cell drifts and wraps over one period.
    for (int layer = 0; layer < 3; layer++) {
        float scale = 8.0 + 6.0 * float(layer);
        vec2 cell = uv * scale + vec2(float(layer) * 13.7, 0.0);
        vec2 id = floor(cell);
        vec2 f = fract(cell);

        // periodic drift (wraps via fract so the loop closes)
        vec2 drift = vec2(sin(lt + float(layer)), cos(lt + float(layer) * 0.7)) * 0.35;
        vec2 star = f + drift;
        star = fract(star) - 0.5;

        float d = length(star);
        float twinkle = 0.5 + 0.5 * sin(lt * 3.0 + hash(id) * TAU);
        float brightness = smoothstep(0.12, 0.0, d) * twinkle;
        brightness *= smoothstep(0.0, 1.0, hash(id + 0.5)); // subset of stars

        vec3 tint = mix(CYAN, BLUE, hash(id + 1.0));
        col += tint * brightness * (0.5 + 0.5 / (1.0 + float(layer)));
    }

    fragColor = vec4(col, 1.0);
}

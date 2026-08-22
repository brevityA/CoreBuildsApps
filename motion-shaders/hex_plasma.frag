#version 330 core
// Core Motion — Hex Plasma
// Self-authored GLSL fragment shader. Seamless loop: every motion term is
// periodic in T seconds (2*pi*t/T), so frame 0 == frame N.
uniform vec2 u_resolution;
uniform float u_time;
out vec4 fragColor;

const float TAU = 6.28318530718;
const float T = 20.0;

// Core Builds §03 palette
const vec3 NIGHT  = vec3(0.051, 0.067, 0.090); // #0D1117
const vec3 CYAN   = vec3(0.000, 0.898, 1.000); // #00E5FF
const vec3 BLUE   = vec3(0.310, 0.675, 0.996); // #4FACFE
const vec3 VIOLET = vec3(0.541, 0.282, 0.565); // #8A4890

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p *= 2.03;
        a *= 0.5;
    }
    return v;
}

// Signed distance to a point-up hexagon (Inigo Quilez's form).
float hexSDF(vec2 p, float r) {
    const vec3 k = vec3(-0.8660254, 0.5, 0.57735027);
    p = abs(p);
    p -= 2.0 * min(dot(k.xy, p), 0.0) * k.xy;
    p -= vec2(clamp(p.x, -k.z * r, k.z * r), r);
    return length(p) * sign(p.y);
}

void main() {
    vec2 uv = (2.0 * gl_FragCoord.xy - u_resolution) / min(u_resolution.x, u_resolution.y);
    float lt = TAU * u_time / T; // periodic loop time

    // Plasma field, drifting on the loop period.
    vec2 q = uv;
    float p = fbm(q * 1.5 + vec2(sin(lt), cos(lt)) * 0.8) * 0.6
            + 0.3 * sin(q.x * 3.0 + lt)
            + 0.3 * cos(q.y * 3.0 + lt * 0.9);

    // Breathing hexagon mark.
    float r = 0.5 + 0.06 * sin(lt);
    float hex = hexSDF(uv, r);

    vec3 col = mix(NIGHT, CYAN, smoothstep(0.55, 0.0, p));
    col = mix(col, VIOLET, smoothstep(0.9, 0.4, p));

    float glow = 1.0 - smoothstep(0.0, 0.06, abs(hex));
    col = mix(col, BLUE, glow * 0.8);
    col = mix(col, CYAN, (1.0 - smoothstep(0.0, 0.5, hex)) * 0.25 * (0.5 + 0.5 * sin(lt)));

    fragColor = vec4(col, 1.0);
}

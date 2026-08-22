#version 330 core
// Core Motion — Flow (aurora / flowing noise)
// Horizontal flowing noise bands, cyan→build-blue→violet. Seamless: the noise
// sample offset and the colour cycle are both periodic in T.
uniform vec2 u_resolution;
uniform float u_time;
out vec4 fragColor;

const float TAU = 6.28318530718;
const float T = 20.0;

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
        p *= 2.1;
        a *= 0.5;
    }
    return v;
}

void main() {
    vec2 uv = (2.0 * gl_FragCoord.xy - u_resolution) / min(u_resolution.x, u_resolution.y);
    float lt = TAU * u_time / T; // periodic loop time

    // flowing sample offset (wraps: sin/cos periodic)
    vec2 flow = vec2(sin(lt) * 1.5, cos(lt * 0.8) * 0.4);
    float n = fbm(vec2(uv.x * 1.2 + flow.x, uv.y * 3.0 + flow.y));

    // layered bands
    float bands = 0.0;
    for (int i = 1; i <= 4; i++) {
        float fi = float(i);
        bands += sin(uv.y * fi * 6.0 + lt * fi + n * 4.0) / fi;
    }
    float v = 0.5 + 0.5 * bands * 0.5;

    vec3 col = mix(NIGHT, CYAN, smoothstep(0.25, 0.8, v));
    col = mix(col, BLUE, smoothstep(0.55, 0.95, n + v * 0.3));
    col = mix(col, VIOLET, smoothstep(0.7, 1.0, v));

    fragColor = vec4(col, 1.0);
}

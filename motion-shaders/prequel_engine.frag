#version 330 core
// Core Prequel Engine v1.0
//
// One self-authored shader drives the Series 2 and 3 prequel generations.
// Every trajectory is periodic over the requested loop duration. There is no still-image pan:
// the field, emitters, fog, orbiters, tracers and horizon atmosphere all move.
// Keep this file free of external shader snippets so the generated work is
// owned and reproducible.
uniform vec2 u_resolution;
uniform float u_time;
uniform float u_loop;
uniform float u_speed;
uniform float u_seed;
uniform float u_intensity;
uniform vec3 u_accent;
uniform int u_scene;
out vec4 fragColor;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;
const float LOOP = 20.0;
const vec3 VOID = vec3(0.0157, 0.0275, 0.0588);
const vec3 NIGHT = vec3(0.0510, 0.0670, 0.0902);
const vec3 CYAN = vec3(0.0000, 0.8980, 1.0000);
const vec3 BLUE = vec3(0.3100, 0.6750, 0.9960);
const vec3 VIOLET = vec3(0.5410, 0.2820, 0.5650);
const vec3 EMBER = vec3(0.7530, 0.2270, 0.1250);

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float hash11(float p) {
    return fract(sin(p * 127.1 + u_seed * 17.3) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec2(17.1, 9.2);
        a *= 0.5;
    }
    return v;
}

float glowLine(float d, float width) {
    return exp(-max(d, 0.0) / max(width, 0.0001));
}

float segmentDistance(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

vec3 ramp(float x) {
    x = clamp(x, 0.0, 1.0);
    vec3 a = mix(CYAN, BLUE, smoothstep(0.0, 0.48, x));
    vec3 b = mix(VIOLET, EMBER, smoothstep(0.52, 1.0, x));
    return mix(a, b, smoothstep(0.38, 0.82, x));
}

vec3 baseField(vec2 p, float phase) {
    float n = fbm(p * 1.25 + vec2(cos(phase), sin(phase)) * 0.45);
    float v = 0.5 + 0.5 * sin(p.x * 2.2 + n * 4.0 + phase);
    vec3 col = mix(VOID, NIGHT, smoothstep(0.08, 0.86, n));
    col += ramp(v) * n * 0.035 * u_intensity;
    return col;
}

vec3 sceneOrbitals(vec2 p, float phase) {
    vec3 col = baseField(p, phase);
    float t = phase / TAU;
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        float radius = 0.22 + fi * 0.105;
        float tilt = 0.35 + 0.08 * sin(fi * 1.7 + u_seed);
        vec2 q = vec2(p.x, p.y / tilt);
        float ring = abs(length(q) - radius);
        float wobble = 0.004 * sin(phase * 2.0 + fi * 1.9);
        col += mix(CYAN, VIOLET, fi / 7.0) * glowLine(abs(ring - wobble), 0.006 + fi * 0.001) * 0.12;
        float a = phase * (0.34 + fi * 0.065) + fi * 1.91;
        vec2 orb = vec2(cos(a) * radius, sin(a) * radius * tilt);
        float d = length(p - orb);
        float flare = exp(-d * (90.0 - fi * 4.0));
        col += mix(u_accent, BLUE, fi / 8.0) * flare * (0.55 + 0.3 * sin(phase + fi));
    }
    float core = exp(-length(p) * 9.0);
    col += mix(CYAN, u_accent, 0.45) * core * 0.14;
    return col;
}

vec3 sceneWarp(vec2 p, float phase) {
    vec3 col = baseField(p, phase);
    float r = length(p);
    float a = atan(p.y, p.x);
    float twist = a + 0.9 * r + phase * 0.23;
    float lanes = abs(sin(twist * 17.0 + sin(r * 10.0 - phase) * 1.7));
    float wave = smoothstep(0.72, 1.0, lanes) * smoothstep(1.45, 0.08, r);
    float streaks = pow(max(wave, 0.0), 3.0) * (0.45 + 0.55 * fbm(vec2(a * 3.0, r * 7.0) + phase));
    col += mix(BLUE, CYAN, 0.5 + 0.5 * sin(a * 4.0)) * streaks * 0.3 * u_intensity;
    float pulse = 0.5 + 0.5 * cos(r * 15.0 - phase * 1.7);
    col += u_accent * pow(max(pulse, 0.0), 12.0) * smoothstep(1.15, 0.1, r) * 0.08;
    return col;
}

vec3 sceneFogbanks(vec2 p, float phase) {
    vec3 col = mix(VOID, NIGHT, smoothstep(-0.9, 0.8, p.y));
    float drift = sin(phase) * 0.45;
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float center = -0.42 + fi * 0.17 + 0.04 * sin(phase * (1.0 + fi * 0.11) + fi);
        float shape = p.y - center;
        float n = fbm(vec2(p.x * (1.25 + fi * 0.18) + drift + fi * 3.0, p.y * 2.4 + fi));
        float band = exp(-shape * shape / (0.022 + fi * 0.003)) * smoothstep(0.0, 0.8, n);
        col += mix(CYAN, VIOLET, fi / 6.0) * band * (0.045 + fi * 0.012) * u_intensity;
    }
    float haze = fbm(p * 1.2 + vec2(drift, 0.0));
    col += mix(BLUE, CYAN, haze) * smoothstep(0.35, 0.9, haze) * 0.035;
    return col;
}

vec3 sceneSpiral(vec2 p, float phase) {
    vec3 col = baseField(p, phase);
    float r = length(p);
    float a = atan(p.y, p.x);
    float spiral = sin(a * 3.0 - r * 11.0 + phase * 0.42 + sin(r * 4.0 + phase) * 0.7);
    float arms = smoothstep(0.84, 0.99, spiral) * smoothstep(1.6, 0.04, r);
    float inner = smoothstep(0.36, 0.0, r);
    col += mix(VIOLET, u_accent, 0.5 + 0.5 * sin(a + phase)) * arms * 0.25 * u_intensity;
    col += CYAN * inner * (0.08 + 0.05 * sin(phase));
    float dust = fbm(vec2(a * 5.0 + phase, r * 8.0));
    col += BLUE * arms * dust * 0.11;
    return col;
}

vec3 sceneSlipstream(vec2 p, float phase) {
    vec3 col = baseField(p, phase);
    float r = length(p);
    float a = atan(p.y, p.x);
    float lanes = abs(sin(a * 26.0 + r * 10.0 - phase * 0.5));
    float tunnel = smoothstep(0.93, 0.995, lanes) * smoothstep(1.3, 0.04, r);
    float travel = 0.5 + 0.5 * sin(r * 22.0 - phase * 1.8 + a * 4.0);
    col += mix(CYAN, BLUE, 0.5 + 0.5 * sin(a * 2.0)) * tunnel * (0.08 + 0.12 * travel);
    float center = exp(-r * 12.0);
    col += u_accent * center * 0.15;
    return col;
}

vec3 sceneEmbers(vec2 p, float phase) {
    vec3 col = mix(VOID, NIGHT, smoothstep(-0.7, 0.7, p.y));
    for (int i = 0; i < 36; i++) {
        float fi = float(i);
        float x = hash11(fi * 2.13) * 2.4 - 1.2;
        float y = hash11(fi * 5.17 + 8.0) * 2.0 - 1.0;
        float speed = 0.45 + hash11(fi + 2.0) * 0.6;
        float loopY = fract(y + phase / TAU * speed);
        float sway = 0.10 * sin(phase * (1.0 + hash11(fi) * 0.7) + fi);
        vec2 pos = vec2(x + sway, loopY - 1.0);
        float d = length(p - pos);
        float glow = exp(-d * 125.0) * (0.5 + 0.5 * sin(phase * 2.0 + fi));
        col += mix(EMBER, u_accent, 0.25 + 0.5 * hash11(fi + 4.0)) * glow * 0.18;
        vec2 tail = pos - vec2(0.0, 0.07 + 0.04 * speed);
        col += EMBER * exp(-segmentDistance(p, tail, pos) * 90.0) * 0.015;
    }
    float floorGlow = exp(-abs(p.y + 0.68) * 18.0) * (0.5 + 0.5 * sin(phase));
    col += EMBER * floorGlow * 0.04;
    return col;
}

vec3 sceneTracer(vec2 p, float phase) {
    vec3 col = baseField(p, phase);
    float grid = 0.0;
    vec2 g = p * 4.2;
    vec2 cell = abs(fract(g) - 0.5);
    grid += smoothstep(0.08, 0.0, min(cell.x, cell.y)) * 0.025;
    col += CYAN * grid;
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float y = -0.55 + fi * 0.27 + 0.03 * sin(phase + fi);
        float x = -1.05 + fract(phase / TAU * (0.18 + fi * 0.04) + fi * 0.19) * 2.1;
        vec2 a = vec2(x - 0.22, y);
        vec2 b = vec2(x, y);
        vec2 c = vec2(x, y + 0.11);
        float d = min(segmentDistance(p, a, b), segmentDistance(p, b, c));
        col += mix(CYAN, BLUE, fi / 5.0) * exp(-d * 110.0) * 0.25;
    }
    return col;
}

vec3 scenePendulum(vec2 p, float phase) {
    vec3 col = baseField(p, phase);
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float pivotX = -0.78 + fi * 0.39;
        float armLength = 0.42 + 0.07 * sin(fi * 2.0 + u_seed);
        float angle = 0.44 * sin(phase * (0.55 + fi * 0.05) + fi * 1.2);
        vec2 pivot = vec2(pivotX, 0.68);
        vec2 bob = pivot + vec2(sin(angle) * armLength, -cos(angle) * armLength);
        float arm = segmentDistance(p, pivot, bob);
        col += mix(CYAN, VIOLET, fi / 5.0) * exp(-arm * 85.0) * 0.14;
        col += u_accent * exp(-length(p - bob) * 115.0) * 0.25;
        for (int j = 1; j < 5; j++) {
            float fj = float(j) / 5.0;
            float oldAngle = 0.44 * sin((phase - fj * 0.45) * (0.55 + fi * 0.05) + fi * 1.2);
            vec2 trail = pivot + vec2(sin(oldAngle) * armLength, -cos(oldAngle) * armLength);
            col += u_accent * exp(-length(p - trail) * 100.0) * 0.018 * (1.0 - fj);
        }
    }
    return col;
}

vec3 horizonBase(vec2 p, float phase, vec3 skyA, vec3 skyB, float horizonY) {
    float sky = smoothstep(-0.72, 0.72, p.y);
    vec3 col = mix(skyA, skyB, sky);
    float haze = exp(-abs(p.y - horizonY) * 18.0);
    col += u_accent * haze * 0.10;
    float stars = step(0.992, hash21(floor(p * vec2(42.0, 18.0)) + u_seed));
    stars *= smoothstep(-0.05, 0.35, p.y) * (0.6 + 0.4 * sin(phase * 2.0 + hash21(floor(p * 42.0)) * TAU));
    col += vec3(0.5, 0.85, 1.0) * stars * 0.14;
    return col;
}

vec3 sceneHorizon(vec2 p, float phase, int variant) {
    float horizonY = -0.40 + 0.018 * sin(phase);
    vec3 skyA = VOID;
    vec3 skyB = NIGHT;
    if (variant == 1) { skyA = vec3(0.12, 0.035, 0.07); skyB = vec3(0.14, 0.055, 0.065); }
    if (variant == 2) { skyA = vec3(0.06, 0.035, 0.09); skyB = vec3(0.11, 0.06, 0.11); }
    if (variant == 3) { skyA = vec3(0.02, 0.08, 0.14); skyB = vec3(0.035, 0.09, 0.11); }
    if (variant == 4) { skyA = vec3(0.015, 0.06, 0.08); skyB = vec3(0.03, 0.08, 0.09); }
    if (variant == 5) { skyA = vec3(0.12, 0.07, 0.025); skyB = vec3(0.08, 0.05, 0.03); }
    if (variant == 6) { skyA = vec3(0.055, 0.07, 0.095); skyB = vec3(0.06, 0.075, 0.09); }
    vec3 col = horizonBase(p, phase, skyA, skyB, horizonY);

    // Dark, calm foreground: launcher cards keep a readable lower third.
    float ground = smoothstep(horizonY + 0.01, horizonY - 0.22, p.y);
    col = mix(col, VOID * (0.75 + 0.25 * ground), ground);

    // A living horizon: soft bands move in a closed loop instead of scrolling.
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float yy = horizonY + 0.07 + fi * 0.12 + 0.025 * sin(phase * (0.7 + fi * 0.13) + fi);
        float wave = sin(p.x * (2.5 + fi) + phase * (0.45 + fi * 0.07) + fbm(vec2(p.x * 2.0, fi)) * 2.0);
        float band = exp(-abs(p.y - yy - wave * 0.035) * (28.0 - fi * 3.0));
        col += mix(u_accent, BLUE, fi / 4.0) * band * 0.035 * u_intensity;
    }

    if (variant == 0) {
        // Cyan aurora ribbons.
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            float y = 0.05 + fi * 0.18 + 0.06 * sin(p.x * 2.0 + phase + fi);
            float d = abs(p.y - y - 0.08 * sin(p.x * 4.0 - phase * 0.7 + fi));
            col += CYAN * exp(-d * 28.0) * 0.04;
        }
    } else if (variant == 1 || variant == 5) {
        // Low solar disc, breathing by a few pixels over the loop.
        vec2 sun = vec2(0.45, horizonY + 0.10 + 0.025 * sin(phase));
        float sd = length(p - sun);
        col += mix(EMBER, u_accent, 0.25) * exp(-sd * 13.0) * 0.10;
        col += u_accent * smoothstep(0.08, 0.0, sd) * 0.35;
    } else if (variant == 2) {
        // Rose cloud shelf.
        float cloud = fbm(p * 2.3 + vec2(sin(phase), cos(phase)) * 0.3);
        col += vec3(0.65, 0.20, 0.30) * smoothstep(0.52, 0.78, cloud) * 0.08;
    } else if (variant == 3) {
        // Laboratory scan plane.
        float scan = smoothstep(0.94, 1.0, sin((p.y + phase * 0.02) * 55.0) * 0.5 + 0.5);
        float vertical = smoothstep(0.96, 1.0, sin(p.x * 20.0) * 0.5 + 0.5);
        col += BLUE * (scan + vertical) * 0.018;
        col += CYAN * exp(-abs(p.y - (0.22 + 0.12 * sin(phase))) * 55.0) * 0.08;
    } else if (variant == 4) {
        // Verified signal arcs.
        float arc = abs(length(p - vec2(-0.45, 0.12)) - 0.35);
        col += CYAN * exp(-arc * 45.0) * 0.06 * (0.5 + 0.5 * sin(phase));
    } else if (variant == 6) {
        // Slate monolith / quiet fog.
        vec2 q = p - vec2(-0.22, -0.20);
        float block = max(abs(q.x) - 0.10, abs(q.y) - 0.35);
        col += vec3(0.18, 0.23, 0.30) * smoothstep(0.015, 0.0, block) * 0.5;
        col += vec3(0.22, 0.28, 0.35) * exp(-abs(p.y - horizonY) * 12.0) * 0.035;
    } else if (variant == 7) {
        // Void scene: one razor-thin cyan signal line and slow breathing haze.
        col += CYAN * exp(-abs(p.y - horizonY) * 80.0) * 0.16;
        col += BLUE * exp(-length(p - vec2(0.15, 0.15)) * 7.0) * 0.025;
    }
    return col;
}

void main() {
    vec2 p = (2.0 * gl_FragCoord.xy - u_resolution.xy) / min(u_resolution.x, u_resolution.y);
    float phase = TAU * fract(u_time * u_speed / max(u_loop, 0.001));
    vec3 col;
    if (u_scene == 0) col = sceneOrbitals(p, phase);
    else if (u_scene == 1) col = sceneWarp(p, phase);
    else if (u_scene == 2) col = sceneFogbanks(p, phase);
    else if (u_scene == 3) col = sceneSpiral(p, phase);
    else if (u_scene == 4) col = sceneSlipstream(p, phase);
    else if (u_scene == 5) col = sceneEmbers(p, phase);
    else if (u_scene == 6) col = sceneTracer(p, phase);
    else if (u_scene == 7) col = scenePendulum(p, phase);
    else col = sceneHorizon(p, phase, u_scene - 8);

    // Filmic shoulder keeps highlights from blooming into launcher chrome.
    col = 1.0 - exp(-max(col, vec3(0.0)) * 1.35);
    col = pow(col, vec3(0.92));
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}

// Shared uniforms and helpers. Pure math only, no texture reads.

uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform mat4 shadowModelView;
uniform mat4 shadowProjection;
uniform vec3 cameraPosition;
uniform vec3 sunPosition;
uniform vec3 shadowLightPosition;
uniform vec3 fogColor;
uniform float frameTimeCounter;
uniform float rainStrength;
uniform float viewWidth;
uniform float viewHeight;
uniform float far;
uniform float near;
uniform float nightVision;
uniform int isEyeInWater;
uniform ivec2 eyeBrightnessSmooth;

// Block IDs from block.properties
#define ID_PLANT      10001
#define ID_LEAVES     10002
#define ID_PLANT_FULL 10004
#define ID_WATER      10010
#define ID_EMISSIVE   10020
#define ID_LAVA       10021

const vec3 TORCH_COLOR = vec3(1.0, 0.55, 0.22);

// True if the depth at screen point uv is sky or beyond render distance (e.g. clouds).
// Clouds write depth but shouldn't block lens flare or sun rays. Uses the real projection, so it's exact.
bool isSkyAt(vec2 uv, float d) {
	if (d >= 0.99999) return true;
	vec4 v = gbufferProjectionInverse * vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
	return -v.z / v.w > far * 0.98;
}

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
vec3 toLinear(vec3 c) { return c * c * (c * 0.2 + 0.8); }   // cheap ~gamma 2.2

// Sun direction in world space (y = up). Uses the real sun even at night.
vec3 worldSunDir() { return normalize(mat3(gbufferModelViewInverse) * sunPosition); }

// ---------------- Time-of-day colors ----------------
// h = sun height (-1..1). These run per vertex, not per pixel.

float dayFactor(float h)    { return smoothstep(-0.06, 0.18, h); }
float sunsetFactor(float h) { return clamp(1.0 - abs(h - 0.10) * 2.1, 0.0, 1.0); }
float nightFactor(float h)  { return 1.0 - smoothstep(-0.14, 0.02, h); }
// Dawn: sun low and in the east (+X). Used for morning mist.
float morningFactor(vec3 sunDir) {
	return sunsetFactor(sunDir.y) * smoothstep(-0.1, 0.25, sunDir.x);
}

vec3 sunLightColor(float h) {
#if defined NETHER || defined END
	return vec3(0.0);
#else
	vec3 day    = vec3(1.00, 0.96, 0.91) * 1.42;
	vec3 sunset = vec3(1.00, 0.50, 0.18) * 1.75;
	vec3 night  = vec3(0.42, 0.56, 1.00) * 0.28;          // silvery-blue moonlight
	vec3 c = mix(sunset, day, smoothstep(0.08, 0.45, h));
	c = mix(night, c, smoothstep(-0.08, 0.03, h));
	return c * (1.0 - rainStrength * 0.8) * SUN_BRIGHTNESS;
#endif
}

vec3 ambientColor(float h) {
#ifdef NETHER
	return toLinear(fogColor) * 0.9 + vec3(0.12, 0.05, 0.03);
#elif defined END
	return vec3(0.10, 0.08, 0.16);
#else
	vec3 day    = vec3(0.36, 0.48, 0.72) * 0.88;
	vec3 sunset = vec3(0.50, 0.36, 0.34) * 0.55;
	vec3 night  = vec3(0.05, 0.075, 0.15);
	vec3 c = mix(sunset, day, smoothstep(0.05, 0.35, h));
	c = mix(night, c, smoothstep(-0.12, 0.05, h));
	c = mix(c, vec3(luma(c)) * 0.75, rainStrength * 0.7);
	return c * AMBIENT_BRIGHTNESS;
#endif
}

// ---------------- Sky ----------------
vec3 skyColor(vec3 dir, vec3 sunDir) {
#ifdef NETHER
	return toLinear(fogColor);
#elif defined END
	return vec3(0.02, 0.012, 0.035);
#else
	float h = sunDir.y;
	float day = dayFactor(h);
	float sunset = sunsetFactor(h);
	float night = nightFactor(h);
	float up = max(dir.y, 0.0);
	float horizon = pow(1.0 - up, 3.0);

	vec3 zenith = mix(vec3(0.004, 0.009, 0.030), vec3(0.07, 0.22, 0.75), day * (1.0 - sunset * 0.6));
	vec3 hor    = mix(vec3(0.020, 0.032, 0.075), vec3(0.40, 0.58, 0.92), day);
	zenith = mix(zenith, vec3(0.30, 0.20, 0.20), sunset * 0.75);  // warm, dusky sky overhead at sunset
	vec3 col = mix(zenith, hor, horizon);

	// Warm sunset band, strongest on the sun's side of the sky
	float sunSide = dot(dir, sunDir) * 0.5 + 0.5;
	vec3 sunsetCol = mix(vec3(0.48, 0.24, 0.16), vec3(1.00, 0.42, 0.07), sunSide * sunSide);
	col = mix(col, sunsetCol, sunset * pow(1.0 - up, 0.6) * (0.65 + 0.35 * sunSide));

	// Deep orange right at the horizon under the sun
	col = mix(col, vec3(1.0, 0.28, 0.04), sunset * pow(1.0 - up, 7.0) * pow(sunSide, 4.0) * 0.85);

	// Dawn is a little paler and pinker than dusk
	col = mix(col, vec3(0.95, 0.62, 0.55) * 0.8, morningFactor(sunDir) * horizon * 0.35);

	// Glow around the sun
	float sd = max(dot(dir, sunDir), 0.0);
	col += sunLightColor(h) * (pow(sd, 6.0) * 0.10 + pow(sd, 40.0) * 0.20) * smoothstep(-0.1, 0.05, h) * (1.0 - sunset * 0.5);

	// Night: soft halo around the moon and a faint glow along the horizon
	float md = max(dot(dir, -sunDir), 0.0);
	col += vec3(0.50, 0.62, 1.00) * (pow(md, 10.0) * 0.06 + pow(md, 120.0) * 0.18) * night;
	col += vec3(0.06, 0.07, 0.13) * pow(1.0 - up, 6.0) * night;

	// Below the horizon, fade a little darker
	col *= 1.0 - clamp(-dir.y, 0.0, 1.0) * 0.4;

	col = mix(col, vec3(luma(col)) * vec3(0.85, 0.9, 1.0) * 0.7, rainStrength * 0.85);
	return col;
#endif
}

// ---------------- Atmosphere / fog ----------------
// How thick the air is right now: misty dawns, clear noons, hazy golden dusk, light night mist.
float airDensity(vec3 sunDir) {
	float h = sunDir.y;
	return (1.0 + morningFactor(sunDir) * 3.0 + sunsetFactor(h) * 0.5 + nightFactor(h) * 0.8 + rainStrength * 5.0) * FOG_DENSITY;
}

// How much fog covers something at view-space distance `dist`.
float fogAmount(float dist, vec3 sunDir) {
	if (isEyeInWater == 1) return 1.0 - exp(-dist * 0.07);
	if (isEyeInWater == 2) return 1.0 - exp(-dist * 0.6);
#ifdef NETHER
	float haze = 1.0 - exp(-dist * 0.012 * FOG_DENSITY);
#elif defined END
	float haze = 1.0 - exp(-dist * 0.006 * FOG_DENSITY);
#else
	float haze = 1.0 - exp(-dist * 0.0032 * airDensity(sunDir));
#endif
	// Border fog hides the edge of the render distance
	float border = smoothstep(far * 0.72, far, dist);
	return clamp(max(haze, border), 0.0, 1.0);
}

vec3 fogColorFor(vec3 dir, vec3 sunDir) {
	if (isEyeInWater == 1) return vec3(0.02, 0.09, 0.14) * (0.2 + 0.8 * dayFactor(sunDir.y));
	if (isEyeInWater == 2) return vec3(0.9, 0.25, 0.02);
	vec3 c = skyColor(dir, sunDir);
#if !defined NETHER && !defined END
	// Sunlight scattering in the haze: fog glows warm toward the sun
	float sd = max(dot(dir, sunDir), 0.0);
	c += sunLightColor(sunDir.y) * pow(sd, 5.0) * 0.30 * dayFactor(sunDir.y + 0.1);
#endif
	return c;
}

// Final: lens flare, exposure, filmic tone curve, saturation, vignette, dither.
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/consts.glsl"

/*
const int colortex0Format = R11F_G11F_B10F;   // 32-bit HDR: half the memory traffic of RGBA16F
const int colortex1Format = RGBA8;            // water: normal.xz, sky light, mask
const int colortex2Format = RGBA8;            // water: biome tint, sun shadow
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex2ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
*/

uniform sampler2D colortex0;
uniform sampler2D depthtex0;

// ======================================================================
#ifdef VERTEX
out vec2 texcoord;
flat out float exposure;
flat out vec3 grade;
flat out float satBoost;
flat out vec2 sunScreen;
flat out vec3 flareCol;

void main() {
	texcoord = gl_MultiTexCoord0.xy;
	gl_Position = ftransform();
	vec3 sunDir = worldSunDir();

#if defined NETHER || defined END
	exposure = EXPOSURE * 1.6;
#else
	// Brighter in caves (eyes adjusting), and a little brighter at night so nights look moody, not black
	float sky = float(eyeBrightnessSmooth.y) / 240.0;
	exposure = EXPOSURE * mix(2.4, 1.0, sky) * mix(1.0, 1.45, nightFactor(sunDir.y) * sky);
#endif

	// Golden color grade around sunrise/sunset, cool silver at night
#if defined NETHER || defined END
	grade = vec3(1.0);
	satBoost = 0.0;
#else
	float golden = sunsetFactor(sunDir.y) * smoothstep(-0.08, 0.04, sunDir.y) * (1.0 - rainStrength);
	grade = mix(vec3(1.0), vec3(1.16, 0.96, 0.72), golden);
	satBoost = golden * 0.25;
	grade = mix(grade, vec3(0.92, 0.98, 1.10), nightFactor(sunDir.y));
#endif
	flareCol = vec3(0.0);
	sunScreen = vec2(0.5);
#if defined LENS_FLARE && !defined NETHER && !defined END
	vec4 sp = gbufferProjection * vec4(sunPosition, 1.0);
	if (sp.w > 0.0 && isEyeInWater == 0) {
		sunScreen = sp.xy / sp.w * 0.5 + 0.5;
		float onScreen = smoothstep(-0.15, 0.1, min(sunScreen.x, sunScreen.y)) * smoothstep(-0.15, 0.1, 1.0 - max(sunScreen.x, sunScreen.y));
		flareCol = sunLightColor(sunDir.y) * onScreen * smoothstep(-0.02, 0.06, sunDir.y) * (1.0 - rainStrength) * LENS_FLARE_STRENGTH;
	}
#endif
}
#endif

// ======================================================================
#ifdef FRAGMENT
in vec2 texcoord;
flat in float exposure;
flat in vec3 grade;
flat in float satBoost;
flat in vec2 sunScreen;
flat in vec3 flareCol;

layout(location = 0) out vec4 outColor;

vec3 filmic(vec3 x) {   // ACES-style curve: rich contrast, soft highlight rolloff
	return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

#ifdef LENS_FLARE
// Soft disc of radius r centered at c (aspect-corrected)
float disc(vec2 uv, vec2 c, float r, vec2 aspect) {
	float d = length((uv - c) * aspect);
	return smoothstep(r, r * 0.55, d);
}

vec3 lensFlare(vec2 uv) {
	vec2 aspect = vec2(viewWidth / viewHeight, 1.0);
	vec2 toCenter = vec2(0.5) - sunScreen;
	vec3 f = vec3(0.0);
	// Glow and a thin horizontal streak at the sun
	vec2 d = (uv - sunScreen) * aspect;
	f += vec3(1.0, 0.85, 0.6) * exp(-length(d) * 7.0) * 0.25;
	f += vec3(0.75, 0.85, 1.0) * exp(-abs(d.y) * 120.0) * exp(-abs(d.x) * 1.6) * 0.45;
	// Ghosts mirrored through the screen center, each with its own tint
	f += vec3(0.9, 0.5, 0.2) * disc(uv, sunScreen + toCenter * 0.55, 0.035, aspect) * 0.30;
	f += vec3(0.3, 0.9, 0.6) * disc(uv, sunScreen + toCenter * 0.95, 0.022, aspect) * 0.35;
	f += vec3(0.4, 0.5, 1.0) * disc(uv, sunScreen + toCenter * 1.30, 0.075, aspect) * 0.16;
	f += vec3(1.0, 0.4, 0.6) * disc(uv, sunScreen + toCenter * 1.65, 0.045, aspect) * 0.24;
	f += vec3(0.6, 0.8, 1.0) * disc(uv, sunScreen + toCenter * 2.10, 0.115, aspect) * 0.10;
	// Faint ring halo around the sun
	float ring = length(d);
	f += vec3(0.6, 0.75, 1.0) * smoothstep(0.02, 0.0, abs(ring - 0.32)) * 0.08;
	return f;
}
#endif

// Is the sun visible? Every pixel reads the same 5 depth texels, so these come straight from cache.
float sunVisibility() {
	const vec2 offs[5] = vec2[5](vec2(0.0), vec2(0.008, 0.0), vec2(-0.008, 0.0), vec2(0.0, 0.012), vec2(0.0, -0.012));
	float vis = 0.0;
	for (int i = 0; i < 5; i++) {
		vec2 p = sunScreen + offs[i];
		if (all(greaterThan(p, vec2(0.0))) && all(lessThan(p, vec2(1.0))))
			vis += isSkyAt(p, texture(depthtex0, p).r) ? 1.0 : 0.0;
	}
	return vis / 5.0;
}

void main() {
	vec3 c = texture(colortex0, texcoord).rgb;
#ifdef LENS_FLARE
	if (dot(flareCol, flareCol) > 1e-6) {
		float vis = sunVisibility();
		if (vis > 0.0) c += lensFlare(texcoord) * flareCol * vis;
	}
#endif
	c = filmic(c * exposure * grade);
	float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
	c = max(mix(vec3(l), c, SATURATION + satBoost), 0.0);
	c = pow(c, vec3(1.0 / 2.2));
#ifdef VIGNETTE
	vec2 v = texcoord - 0.5;
	c *= 1.0 - dot(v, v) * 0.45;
#endif
	// Tiny dither so dark skies don't show color banding
	c += (fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715)))) - 0.5) / 255.0;
	outColor = vec4(c, 1.0);
}
#endif

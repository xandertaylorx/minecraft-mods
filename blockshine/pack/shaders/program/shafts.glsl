// Light rays (composite). Two versions:
//  - VOLUMETRIC_LIGHT (High): march from the camera toward each pixel through the shadow map.
//    Wherever the air is lit by the sun you see a glowing ray, so light streams through gaps in leaves
//    from any viewing angle. The shadow-space path is computed once per pixel and stepped linearly.
//  - Otherwise (Medium): screen-space rays, only when looking toward the sun. Very cheap.
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/consts.glsl"

#if defined VOLUMETRIC_LIGHT && defined SHADOWS && !defined NETHER && !defined END
	#define USE_VOLUMETRIC
	#define SHADOW_LOOKUP
#endif
#include "/lib/shadows.glsl"

#ifdef VERTEX
out vec2 texcoord;
flat out vec2 sunScreen;
flat out vec3 shaftCol;
flat out vec3 lightDirW;
flat out vec3 vlCol;
flat out float vlDensity;
void main() {
	texcoord = gl_MultiTexCoord0.xy;
	gl_Position = ftransform();
	vec3 sunDir = worldSunDir();
	float h = sunDir.y;
	vec4 sp = gbufferProjection * vec4(sunPosition, 1.0);
	sunScreen = sp.xy / sp.w * 0.5 + 0.5;
	float visible = (sp.w > 0.0) ? 1.0 : 0.0;
	shaftCol = sunLightColor(h) * visible * smoothstep(-0.02, 0.08, h) * (1.0 + sunsetFactor(h)) * (1.0 - rainStrength);

	lightDirW = normalize(mat3(gbufferModelViewInverse) * shadowLightPosition);
	// Rays are strongest in morning mist and at sunset; moonbeams at night are faint and blue.
	vlCol = sunLightColor(h) * (1.0 - rainStrength * 0.7);
	vlDensity = 0.0022 * airDensity(sunDir);
}
#endif

#ifdef FRAGMENT
uniform sampler2D colortex0;
uniform sampler2D depthtex0;
in vec2 texcoord;
flat in vec2 sunScreen;
flat in vec3 shaftCol;
flat in vec3 lightDirW;
flat in vec3 vlCol;
flat in float vlDensity;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

float ign2(vec2 p) { return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715)))); }

void main() {
	vec3 col = texture(colortex0, texcoord).rgb;
	if (isEyeInWater != 0) { outColor = vec4(col, 1.0); return; }

#ifdef USE_VOLUMETRIC
	float depth = texture(depthtex0, texcoord).r;
	vec4 ndc = vec4(texcoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
	vec4 vp = gbufferProjectionInverse * ndc;
	vec3 viewPos = vp.xyz / vp.w;
	vec3 playerPos = mat3(gbufferModelViewInverse) * viewPos;
	float dist = min(length(playerPos), shadowDistance * 0.9);
	vec3 dir = normalize(playerPos);

	// Shadow-space start (camera) and end; shadow transforms are affine so we can step linearly.
	vec3 s0 = (shadowProjection * (shadowModelView * vec4(0.0, 0.0, 0.0, 1.0))).xyz;
	vec3 s1 = (shadowProjection * (shadowModelView * vec4(dir * dist, 1.0))).xyz;
	float jitter = ign2(gl_FragCoord.xy);
	float lit = 0.0;
	for (int i = 0; i < VL_SAMPLES; i++) {
		float t = (float(i) + jitter) / float(VL_SAMPLES);
		t *= t;                                   // more samples close to the camera, where detail matters
		lit += shadowTap(mix(s0, s1, t));
	}
	lit /= float(VL_SAMPLES);

	// Forward scattering: rays glow most when looking toward the light
	float cosA = dot(dir, lightDirW);
	float phase = 0.04 + 0.75 * pow(max(cosA, 0.0), 8.0) + 0.18 * pow(max(cosA, 0.0), 2.0);
	float amount = 1.0 - exp(-dist * vlDensity);
	col += vlCol * lit * phase * amount * 0.55 * VL_STRENGTH;
#else
	if (dot(shaftCol, shaftCol) > 1e-5) {
		vec2 aspect = vec2(viewWidth / viewHeight, 1.0);
		vec2 d = (texcoord - sunScreen) * aspect;
		float falloff = exp(-dot(d, d) * 4.0);
		if (falloff > 0.01) {
			vec2 stepUV = (sunScreen - texcoord) / float(LIGHT_SHAFT_SAMPLES) * 0.8;
			vec2 uv = texcoord + stepUV * ign2(gl_FragCoord.xy);
			float sky = 0.0;
			for (int i = 0; i < LIGHT_SHAFT_SAMPLES; i++) {
				vec2 cuv = clamp(uv, 0.001, 0.999);
				sky += isSkyAt(cuv, texture(depthtex0, cuv).r) ? 1.0 : 0.0;
				uv += stepUV;
			}
			sky /= float(LIGHT_SHAFT_SAMPLES);
			col += shaftCol * sky * falloff * 0.09 * LIGHT_SHAFT_STRENGTH;
		}
	}
#endif
	outColor = vec4(col, 1.0);
}
#endif

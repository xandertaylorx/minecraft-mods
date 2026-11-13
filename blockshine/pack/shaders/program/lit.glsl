// Lit geometry: terrain, block entities, entities, hand, particles, water.
// Forward lighting: everything is lit right here in one pass (no deferred G-buffer = less memory bandwidth).
// Per-frame colors (sun, ambient, fog) are computed per vertex and interpolated, which is far cheaper than per pixel.

#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/consts.glsl"

#if defined SHADOWS && !defined NETHER && !defined END
	#define USE_SHADOWS
	#define SHADOW_LOOKUP
#endif
#include "/lib/shadows.glsl"

// ======================================================================
#ifdef VERTEX

#ifdef TERRAIN
in vec4 mc_Entity;
in vec4 mc_midTexCoord;
#include "/lib/waving.glsl"
#endif

out vec2 texcoord;
out vec2 lmcoord;
out vec4 tint;
out vec3 shadowClip;
out float NdotL;
out vec3 fogCol;
out float fogF;
flat out vec3 sunCol;
flat out vec3 ambCol;
flat out float nightF;
flat out int blockId;
out float isFoliage;
out float sssAmount;
#ifdef WATER_PASS
out vec3 worldPos;
out vec3 worldNormal;
out vec3 viewDirW;
flat out vec3 sunDirW;
flat out vec3 lightDirW;
#endif

void main() {
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	lmcoord  = clamp(((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy - 0.03125) * 1.0666667, 0.0, 1.0);
	tint = gl_Color;

	vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
	vec3 playerPos = (gbufferModelViewInverse * viewPos).xyz;

	int id = 0;
#ifdef TERRAIN
	id = int(mc_Entity.x + 0.5);
	#if defined WAVING_PLANTS || defined WAVING_LEAVES
	if (id == ID_PLANT || id == ID_LEAVES || id == ID_PLANT_FULL) {
		bool top = gl_MultiTexCoord0.t < mc_midTexCoord.t;
		playerPos += waveOffset(playerPos + cameraPosition, id, top);
		viewPos = gbufferModelView * vec4(playerPos, 1.0);
	}
	#endif
#endif
	blockId = id;
	gl_Position = gl_ProjectionMatrix * viewPos;

	// Particles and some entities have no normal; treat them as facing the light.
	vec3 vn = gl_NormalMatrix * gl_Normal;
	bool noNormal = dot(vn, vn) < 1e-4;
	vec3 wn = noNormal ? vec3(0.0, 1.0, 0.0) : mat3(gbufferModelViewInverse) * normalize(vn);

	vec3 sunDir = worldSunDir();
	vec3 lightDir = normalize(mat3(gbufferModelViewInverse) * shadowLightPosition);
	bool foliage = id == ID_PLANT || id == ID_LEAVES || id == ID_PLANT_FULL;
	// Foliage is thin: light it softly from both sides instead of by face direction
	NdotL = noNormal ? 0.6 : (foliage ? 0.28 + 0.30 * abs(dot(wn, lightDir)) : max(dot(wn, lightDir), 0.0));
	isFoliage = foliage ? 1.0 : 0.0;
	// How much the light shines through toward the camera (strongest looking into the sun through leaves)
	sssAmount = pow(max(dot(normalize(playerPos), lightDir), 0.0), 4.0);

	sunCol = sunLightColor(sunDir.y);
	ambCol = ambientColor(sunDir.y);
	nightF = nightFactor(sunDir.y);

#ifdef USE_SHADOWS
	// Push the lookup point off the surface; more push farther away where shadow texels are bigger.
	float dist = length(playerPos);
	vec3 offsetDir = foliage ? lightDir : wn;
	vec3 biased = playerPos + offsetDir * (0.03 + dist * 0.0035);
	shadowClip = (shadowProjection * (shadowModelView * vec4(biased, 1.0))).xyz;
#else
	shadowClip = vec3(0.0);
#endif

	vec3 dirW = normalize(playerPos);
	fogF = fogAmount(length(viewPos.xyz), sunDir);
	fogCol = fogColorFor(dirW, sunDir);

#ifdef WATER_PASS
	worldPos = playerPos + cameraPosition;
	worldNormal = wn;
	viewDirW = dirW;
	sunDirW = sunDir;
	lightDirW = lightDir;
#endif
}

#endif
// ======================================================================
#ifdef FRAGMENT

uniform sampler2D gtexture;
uniform vec4 entityColor;

in vec2 texcoord;
in vec2 lmcoord;
in vec4 tint;
in vec3 shadowClip;
in float NdotL;
in vec3 fogCol;
in float fogF;
flat in vec3 sunCol;
flat in vec3 ambCol;
flat in float nightF;
flat in int blockId;
in float isFoliage;
in float sssAmount;
#ifdef WATER_PASS
in vec3 worldPos;
in vec3 worldNormal;
in vec3 viewDirW;
flat in vec3 sunDirW;
flat in vec3 lightDirW;

// Water itself is shaded later in the water pass (program/water.glsl), which can see what's underneath.
// Here water only records: face normal, sky light, biome tint and sun shadow.
// (The RENDERTARGETS line lives in the generated gbuffers_*.fsh entry files so each program has exactly one.)
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outWaterData;
layout(location = 2) out vec4 outWaterTint;
#else
layout(location = 0) out vec4 outColor;
#endif

void main() {
	vec4 albedo = texture(gtexture, texcoord) * tint;
#ifndef WATER_PASS
	if (albedo.a < 0.1) discard;
#endif
	albedo.rgb = mix(albedo.rgb, entityColor.rgb, entityColor.a);
	vec3 col = toLinear(albedo.rgb);

	float sh = 1.0;
#ifdef USE_SHADOWS
	if (NdotL > 0.001) sh = mix(1.0, getShadow(shadowClip), SHADOW_STRENGTH * (1.0 - nightF * 0.55));   // moon shadows are soft
#endif

	float skyL = lmcoord.y;
	float blockL = lmcoord.x;

#ifdef WATER_PASS
	if (blockId == ID_WATER) {
		vec3 n = normalize(worldNormal);
		outColor = vec4(0.0);                                  // leave the scene underneath untouched
		outWaterData = vec4(n.xz * 0.5 + 0.5, skyL, 1.0);       // a = 1 marks water
		outWaterTint = vec4(tint.rgb, sh);
		return;
	}
	outWaterData = vec4(0.0);
	outWaterTint = vec4(0.0);
#endif

#if defined NETHER || defined END
	vec3 light = ambCol                       // no sky light in these dimensions: constant ambient instead
#else
	vec3 light = ambCol * (skyL * skyL)
#endif
	           + vec3(MIN_LIGHT + nightVision * 0.35)
	           + TORCH_COLOR * (pow(blockL, 6.0) * 2.0 + blockL * blockL * 0.04) * TORCH_BRIGHTNESS
	           + sunCol * (NdotL * sh * smoothstep(0.05, 0.6, skyL));

#ifdef FOLIAGE_GLOW
	// Leaves and grass glow when the sun shines through them toward you (subsurface scattering)
	if (isFoliage > 0.5) light += sunCol * sssAmount * sh * smoothstep(0.05, 0.6, skyL) * 0.6;
#endif

	vec3 lit = col * light;
	float alpha = albedo.a;

#ifdef EMISSIVE_BLOCKS
	if (blockId == ID_EMISSIVE) {
		float glow = smoothstep(0.45, 0.85, max(albedo.r, max(albedo.g, albedo.b)));
		lit += col * glow * 3.5;
	}
	if (blockId == ID_LAVA) lit += col * 1.1;
#endif

	lit = mix(lit, fogCol, fogF);
	outColor = vec4(max(lit, 0.0), alpha);
}

#endif

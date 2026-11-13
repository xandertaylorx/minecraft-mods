// Unlit geometry: lines, lightning, weather, beacon beams, glint, clouds, sun & moon.
#include "/lib/settings.glsl"
#include "/lib/common.glsl"

#ifdef VERTEX
out vec2 texcoord;
out vec4 tint;
out vec3 fogCol;
out float fogF;
flat out vec3 sunCol;
flat out vec3 ambCol;

void main() {
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	tint = gl_Color;
	vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
	gl_Position = gl_ProjectionMatrix * viewPos;

	vec3 sunDir = worldSunDir();
	sunCol = sunLightColor(sunDir.y);
	ambCol = ambientColor(sunDir.y);
	vec3 playerPos = mat3(gbufferModelViewInverse) * viewPos.xyz;
	float dist = length(viewPos.xyz);
#ifdef CLOUDS
	fogF = clamp(dist / (far * 4.0), 0.0, 1.0);
	fogF *= fogF;
#elif defined SKYTEXTURED
	fogF = 0.0;
#else
	fogF = fogAmount(dist, sunDir);
#endif
	fogCol = fogColorFor(normalize(playerPos), sunDir);
}
#endif

#ifdef FRAGMENT
#ifdef SKYTEXTURED
#include "/lib/clouds.glsl"
#endif
#ifndef NO_TEXTURE
uniform sampler2D gtexture;
#endif
in vec2 texcoord;
in vec4 tint;
in vec3 fogCol;
in float fogF;
flat in vec3 sunCol;
flat in vec3 ambCol;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

void main() {
#ifdef NO_TEXTURE
	vec4 albedo = tint;
#else
	vec4 albedo = texture(gtexture, texcoord) * tint;
#endif
	if (albedo.a < 0.01) discard;
	vec3 col = toLinear(albedo.rgb);

#if defined CLOUDS
	col *= ambCol * 1.4 + sunCol * 0.55;
#elif defined SKYTEXTURED
	// Bright sun/moon so bloom picks them up; the moon gets a cool silver tint
	float isMoon = step(sunCol.r, sunCol.b);   // moonlight is bluer than sunlight
	col *= mix(vec3(3.0), vec3(2.2, 2.5, 3.2), isMoon) * (1.0 - rainStrength);
	vec4 ndc = vec4(gl_FragCoord.xy / vec2(viewWidth, viewHeight) * 2.0 - 1.0, 1.0, 1.0);
	vec4 vp = gbufferProjectionInverse * ndc;
	vec3 dirW = normalize(mat3(gbufferModelViewInverse) * (vp.xyz / vp.w));
	col *= 1.0 - cloudLayer(dirW, worldSunDir(), sunCol, ambCol).a * 0.85;   // sun hides behind clouds
#elif defined LIGHTNING
	col *= 6.0;
#elif defined WEATHER
	col *= ambCol * 1.6 + sunCol * 0.3;
	albedo.a *= 0.6;
#endif

	col = mix(col, fogCol, fogF);
	outColor = vec4(col, albedo.a);
}
#endif

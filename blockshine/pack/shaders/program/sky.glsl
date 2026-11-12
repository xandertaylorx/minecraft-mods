// Sky: our own gradient + sunset + sun glow. Stars come from vanilla.
#include "/lib/settings.glsl"
#include "/lib/common.glsl"

#ifdef VERTEX
out vec4 tint;
flat out vec3 sunDirW;
void main() {
	tint = gl_Color;
	sunDirW = worldSunDir();
	gl_Position = ftransform();
}
#endif

#ifdef FRAGMENT
uniform int renderStage;
in vec4 tint;
flat in vec3 sunDirW;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

void main() {
	vec4 ndc = vec4(gl_FragCoord.xy / vec2(viewWidth, viewHeight) * 2.0 - 1.0, 1.0, 1.0);
	vec4 vp = gbufferProjectionInverse * ndc;
	vec3 dirW = normalize(mat3(gbufferModelViewInverse) * (vp.xyz / vp.w));
#ifdef MC_RENDER_STAGE_STARS
	if (renderStage == MC_RENDER_STAGE_STARS) {
		// Brighter, slightly blue-white stars that gently twinkle
		vec3 cell = floor(dirW * 180.0);
		float seed = fract(sin(dot(cell, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
		float twinkle = 0.65 + 0.35 * sin(frameTimeCounter * (1.5 + seed * 3.0) + seed * 40.0);
		vec3 starCol = toLinear(tint.rgb) * vec3(0.85, 0.92, 1.0) * 3.0 * twinkle;
		outColor = vec4(starCol * (1.0 - rainStrength), tint.a);
		return;
	}
#endif
#ifdef MC_RENDER_STAGE_SUNSET
	if (renderStage == MC_RENDER_STAGE_SUNSET) discard;   // we draw our own sunset
#endif
	vec3 col = (isEyeInWater == 1) ? fogColorFor(dirW, sunDirW) : skyColor(dirW, sunDirW);
	outColor = vec4(col, 1.0);
}
#endif

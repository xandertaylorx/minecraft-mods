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
#ifdef MC_RENDER_STAGE_STARS
	if (renderStage == MC_RENDER_STAGE_STARS) {
		outColor = vec4(toLinear(tint.rgb) * 1.5 * (1.0 - rainStrength), tint.a);
		return;
	}
#endif
#ifdef MC_RENDER_STAGE_SUNSET
	if (renderStage == MC_RENDER_STAGE_SUNSET) discard;   // we draw our own sunset
#endif
	vec4 ndc = vec4(gl_FragCoord.xy / vec2(viewWidth, viewHeight) * 2.0 - 1.0, 1.0, 1.0);
	vec4 vp = gbufferProjectionInverse * ndc;
	vec3 dirW = normalize(mat3(gbufferModelViewInverse) * (vp.xyz / vp.w));
	vec3 col = (isEyeInWater == 1) ? fogColorFor(dirW, sunDirW) : skyColor(dirW, sunDirW);
	outColor = vec4(col, 1.0);
}
#endif

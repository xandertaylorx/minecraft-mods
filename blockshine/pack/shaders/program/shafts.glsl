// Light shafts (composite). Screen-space: march from each pixel toward the sun and count how much sky is visible.
// Only runs when LIGHT_SHAFTS is on (see shaders.properties).
#include "/lib/settings.glsl"
#include "/lib/common.glsl"

#ifdef VERTEX
out vec2 texcoord;
flat out vec2 sunScreen;
flat out vec3 shaftCol;
void main() {
	texcoord = gl_MultiTexCoord0.xy;
	gl_Position = ftransform();
	vec4 sp = gbufferProjection * vec4(sunPosition, 1.0);
	sunScreen = sp.xy / sp.w * 0.5 + 0.5;
	float h = worldSunDir().y;
	float visible = (sp.w > 0.0) ? 1.0 : 0.0;
	shaftCol = sunLightColor(h) * visible * smoothstep(-0.02, 0.08, h) * (1.0 + sunsetFactor(h)) * (1.0 - rainStrength);
}
#endif

#ifdef FRAGMENT
uniform sampler2D colortex0;
uniform sampler2D depthtex0;
in vec2 texcoord;
flat in vec2 sunScreen;
flat in vec3 shaftCol;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

float ign(vec2 p) { return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715)))); }

void main() {
	vec3 col = texture(colortex0, texcoord).rgb;
	if (isEyeInWater == 0 && dot(shaftCol, shaftCol) > 1e-5) {
		vec2 aspect = vec2(viewWidth / viewHeight, 1.0);
		vec2 d = (texcoord - sunScreen) * aspect;
		float falloff = exp(-dot(d, d) * 4.0);
		if (falloff > 0.01) {
			vec2 stepUV = (sunScreen - texcoord) / float(LIGHT_SHAFT_SAMPLES) * 0.8;
			vec2 uv = texcoord + stepUV * ign(gl_FragCoord.xy);
			float sky = 0.0;
			for (int i = 0; i < LIGHT_SHAFT_SAMPLES; i++) {
				vec2 c = clamp(uv, 0.001, 0.999);
				sky += step(0.99999, texture(depthtex0, c).r);
				uv += stepUV;
			}
			sky /= float(LIGHT_SHAFT_SAMPLES);
			col += shaftCol * sky * falloff * 0.09 * LIGHT_SHAFT_STRENGTH;
		}
	}
	outColor = vec4(col, 1.0);
}
#endif

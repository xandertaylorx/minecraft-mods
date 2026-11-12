// Final: exposure, filmic tone curve, saturation, vignette, dither.
#include "/lib/settings.glsl"
#include "/lib/consts.glsl"

/*
const int colortex0Format = R11F_G11F_B10F;   // 32-bit HDR: half the memory traffic of RGBA16F
*/

uniform ivec2 eyeBrightnessSmooth;
uniform float viewWidth;
uniform float viewHeight;

#ifdef VERTEX
out vec2 texcoord;
flat out float exposure;
void main() {
	texcoord = gl_MultiTexCoord0.xy;
	gl_Position = ftransform();
#if defined NETHER || defined END
	exposure = EXPOSURE * 1.6;
#else
	// Brighter in caves so you can still see, like your eyes adjusting
	float sky = float(eyeBrightnessSmooth.y) / 240.0;
	exposure = EXPOSURE * mix(2.4, 1.0, sky);
#endif
}
#endif

#ifdef FRAGMENT
uniform sampler2D colortex0;
in vec2 texcoord;
flat in float exposure;

layout(location = 0) out vec4 outColor;

vec3 filmic(vec3 x) {   // ACES-style curve: rich contrast, soft highlight rolloff
	return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
	vec3 c = texture(colortex0, texcoord).rgb * exposure;
	c = filmic(c);
	float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
	c = max(mix(vec3(l), c, SATURATION), 0.0);
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

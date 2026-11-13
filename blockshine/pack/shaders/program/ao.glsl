// Screen-space ambient occlusion (deferred: after solid blocks, before water and glass).
// Soft darkening where surfaces meet: block corners, under overhangs, around plants and props.
// For each pixel, a few points in a small disc around it (in the world, ~half a block) are checked
// against the depth buffer; points that are hidden behind nearby geometry count as occluders.
// A per-pixel, per-frame rotation makes the noise random, and TAA averages it into a smooth result.
#include "/lib/settings.glsl"
#include "/lib/common.glsl"

#ifdef VERTEX
out vec2 texcoord;
void main() {
	texcoord = gl_MultiTexCoord0.xy;
	gl_Position = ftransform();
}
#endif

#ifdef FRAGMENT
uniform sampler2D colortex0;
uniform sampler2D depthtex0;
in vec2 texcoord;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

vec3 viewAt(vec2 uv, float d) {
	vec4 v = gbufferProjectionInverse * vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
	return v.xyz / v.w;
}

void main() {
	vec3 col = texture(colortex0, texcoord).rgb;
	float d = texture(depthtex0, texcoord).r;
	if (d >= 1.0 || d < 0.56) { outColor = vec4(col, 1.0); return; }   // sky or held item

	vec3 p = viewAt(texcoord, d);
	float dist = -p.z;
	float fade = 1.0 - smoothstep(32.0, 64.0, dist);                   // far away it's lost in fog anyway
	if (fade <= 0.0) { outColor = vec4(col, 1.0); return; }

	// Surface direction from how depth changes across the pixel
	vec3 n = normalize(cross(dFdx(p), dFdy(p)));

	float noise = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))) + frameNoiseShift());
	float ang = noise * 6.2831853;
	float radius = AO_RADIUS;
	// Disc radius in screen space for a world-space radius at this distance
	float screenR = radius * gbufferProjection[1][1] / dist * 0.5;

	float occ = 0.0;
	for (int i = 0; i < AO_SAMPLES; i++) {
		float t = (float(i) + 0.5) / float(AO_SAMPLES);
		float a = ang + float(i) * 2.3999632;                         // golden-angle spiral
		vec2 off = vec2(cos(a), sin(a)) * sqrt(t) * screenR;
		off.x *= viewHeight / viewWidth;
		vec2 suv = texcoord + off;
		if (any(lessThan(suv, vec2(0.0))) || any(greaterThan(suv, vec2(1.0)))) continue;
		vec3 s = viewAt(suv, texture(depthtex0, suv).r);
		vec3 v = s - p;
		float len = length(v);
		// Counts if it rises above the surface, weighted down with distance so far-off walls don't darken
		float rise = max(dot(v / max(len, 1e-4), n) - 0.08, 0.0);
		occ += rise * clamp(1.0 - len / (radius * 2.0), 0.0, 1.0);
	}
	occ /= float(AO_SAMPLES);
	float ao = clamp(1.0 - occ * 1.6 * AO_STRENGTH, 0.0, 1.0);
	ao = mix(1.0, ao * ao * (3.0 - 2.0 * ao), fade);                     // soft-edged curve

	outColor = vec4(col * ao, 1.0);
}
#endif

// Water (composite). Runs after everything is drawn, so it can see what's under and around the water.
//  - Color that deepens with depth: clear in the shallows, dark teal in the middle (light absorption)
//  - Refraction: the bottom wobbles through the waves
//  - Reflections: real reflections of trees and terrain (screen-space), falling back to sky + clouds
//  - Sun glint, hidden where the water is in shade
// Only water pixels do any work; everything else passes straight through.
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/consts.glsl"

#ifdef VERTEX
out vec2 texcoord;
flat out vec3 sunDirW;
flat out vec3 lightDirW;
flat out vec3 sunCol;
flat out vec3 ambCol;
void main() {
	texcoord = gl_MultiTexCoord0.xy;
	gl_Position = ftransform();
	sunDirW = worldSunDir();
	lightDirW = normalize(mat3(gbufferModelViewInverse) * shadowLightPosition);
	sunCol = sunLightColor(sunDirW.y);
	ambCol = ambientColor(sunDirW.y);
}
#endif

#ifdef FRAGMENT
#include "/lib/clouds.glsl"
uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform sampler2D depthtex0;
uniform sampler2D depthtex1;
in vec2 texcoord;
flat in vec3 sunDirW;
flat in vec3 lightDirW;
flat in vec3 sunCol;
flat in vec3 ambCol;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

vec3 viewFromDepth(vec2 uv, float d) {
	vec4 v = gbufferProjectionInverse * vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
	return v.xyz / v.w;
}

// Gentle swell plus small ripples, as the slope of the surface.
vec3 waveNormal(vec2 p, float t) {
	vec2 d = vec2(0.0);
	const vec2 d1 = vec2(0.80, 0.60), d2 = vec2(-0.45, 0.89), d3 = vec2(0.97, -0.24), d4 = vec2(-0.70, -0.71);
	d += d1 * 0.030 * cos(dot(d1, p) * 1.1 + t * 1.3);
	d += d2 * 0.024 * cos(dot(d2, p) * 2.1 + t * 1.8);
	d += d3 * 0.016 * cos(dot(d3, p) * 4.3 + t * 2.6);
	d += d4 * 0.010 * cos(dot(d4, p) * 8.7 + t * 3.4);
	return normalize(vec3(-d.x, 1.0, -d.y));
}

#ifdef WATER_SSR
// March the reflected ray across the screen; returns the hit color, a = confidence (0 = no hit).
vec4 screenReflection(vec3 viewPos, vec3 rv) {
	float stepLen = 0.5 + length(viewPos) * 0.03;
	vec3 stepV = rv * stepLen;
	vec3 p = viewPos + stepV * (0.5 + 0.5 * fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715)))));
	for (int i = 0; i < SSR_STEPS; i++) {
		vec4 clip = gbufferProjection * vec4(p, 1.0);
		if (clip.w <= 0.0) break;
		vec3 sp = clip.xyz / clip.w * 0.5 + 0.5;
		if (any(lessThan(sp.xy, vec2(0.0))) || any(greaterThan(sp.xy, vec2(1.0)))) break;
		float sceneD = texture(depthtex1, sp.xy).r;
		if (sceneD < 1.0) {
			float sceneZ = viewFromDepth(sp.xy, sceneD).z;
			float diff = sceneZ - p.z;                    // > 0: the ray went behind something
			if (diff > 0.0 && diff < length(stepV) * 3.0 + 0.5) {
				// Refine the hit a few times for a sharper reflection
				vec3 a = p - stepV, b = p;
				for (int j = 0; j < 4; j++) {
					vec3 m = (a + b) * 0.5;
					vec4 mc = gbufferProjection * vec4(m, 1.0);
					vec2 ms = mc.xy / mc.w * 0.5 + 0.5;
					if (viewFromDepth(ms, texture(depthtex1, ms).r).z > m.z) b = m; else a = m;
				}
				vec4 bc = gbufferProjection * vec4(b, 1.0);
				vec2 uv = bc.xy / bc.w * 0.5 + 0.5;
				vec2 edge = smoothstep(0.0, 0.08, uv) * smoothstep(0.0, 0.08, 1.0 - uv);
				return vec4(texture(colortex0, uv).rgb, edge.x * edge.y);
			}
		}
		p += stepV;
		stepV *= 1.18;
	}
	return vec4(0.0);
}
#endif

void main() {
	vec3 col = texture(colortex0, texcoord).rgb;
	vec4 wd = texture(colortex1, texcoord);
	if (wd.a < 0.5 || isEyeInWater != 0) { outColor = vec4(col, 1.0); return; }

	float d0 = texture(depthtex0, texcoord).r;
	vec3 v0 = viewFromDepth(texcoord, d0);
	vec3 playerPos = (gbufferModelViewInverse * vec4(v0, 1.0)).xyz;
	vec3 worldPos = playerPos + cameraPosition;

	vec3 nW = vec3(wd.x * 2.0 - 1.0, 0.0, wd.y * 2.0 - 1.0);
	nW.y = sqrt(max(0.0, 1.0 - dot(nW.xz, nW.xz)));
#ifdef WATER_WAVES
	if (nW.y > 0.5) nW = waveNormal(worldPos.xz, frameTimeCounter);
#endif
	vec3 nV = mat3(gbufferModelView) * nW;
	vec3 vDir = normalize(v0);

	// ---- Refraction + depth color ----
	float dist0 = length(v0);
	vec2 refrUV = clamp(texcoord + nV.xy * 0.06 / (1.0 + dist0 * 0.08), 0.001, 0.999);
	float d1 = texture(depthtex1, refrUV).r;
	if (d1 < d0) { refrUV = texcoord; d1 = texture(depthtex1, texcoord).r; }   // something in front of the water
	vec3 under = texture(colortex0, refrUV).rgb;
	float waterDepth = (d1 >= 1.0) ? 32.0 : distance(v0, viewFromDepth(refrUV, d1));

	float skyL = wd.b;
	vec4 tintData = texture(colortex2, texcoord);
	vec3 tintCol = toLinear(tintData.rgb);
	float sh = tintData.a;

	vec3 absorb = vec3(0.50, 0.15, 0.10) * WATER_DEPTH_FOG;            // red fades first, then green
	vec3 trans = exp(-absorb * waterDepth);
	vec3 scatter = tintCol * vec3(0.22, 0.48, 0.58) * (ambCol * (0.15 + 0.85 * skyL * skyL) + sunCol * 0.03 * sh) * 0.32;
	vec3 body = under * trans + scatter * (1.0 - trans);

	// ---- Reflection ----
	float cosT = clamp(dot(-vDir, nV), 0.0, 1.0);
	float fres = 0.04 + 0.96 * pow(1.0 - cosT, 4.0);
	vec3 water = body;
#ifdef WATER_REFLECTIONS
	vec3 rV = reflect(vDir, nV);
	vec3 rW = mat3(gbufferModelViewInverse) * rV;
	vec3 rWs = vec3(rW.x, max(rW.y, 0.02), rW.z);
	vec3 sky = skyColor(rWs, sunDirW);
	vec4 cl = cloudLayer(rWs, sunDirW, sunCol, ambCol);
	sky = mix(sky, cl.rgb, cl.a);
	vec3 refl = sky * smoothstep(0.25, 0.9, skyL) + ambCol * 0.05;
	#ifdef WATER_SSR
	vec4 ssr = screenReflection(v0, rV);
	refl = mix(refl, ssr.rgb, ssr.a);
	#endif
	float spec = pow(max(dot(rW, lightDirW), 0.0), 350.0) * 9.0 + pow(max(dot(rW, lightDirW), 0.0), 40.0) * 0.25;
	water = mix(body, refl, fres) + sunCol * spec * sh * (1.0 - rainStrength);
#endif

	water = mix(water, fogColorFor(normalize(playerPos), sunDirW), fogAmount(dist0, sunDirW));
	outColor = vec4(max(water, 0.0), 1.0);
}
#endif

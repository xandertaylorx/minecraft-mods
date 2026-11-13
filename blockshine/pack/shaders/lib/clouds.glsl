// Soft 2D clouds painted into the sky. Value noise only (no textures), a few octaves.
// Thin edges catch the light, thick middles go grey; they turn orange at sunset and blue at night.

float hash12(vec2 p) {
	vec3 p3 = fract(vec3(p.xyx) * 0.1031);
	p3 += dot(p3, p3.yzx + 33.33);
	return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	f = f * f * (3.0 - 2.0 * f);
	float a = hash12(i);
	float b = hash12(i + vec2(1.0, 0.0));
	float c = hash12(i + vec2(0.0, 1.0));
	float d = hash12(i + vec2(1.0, 1.0));
	return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float cloudFbm(vec2 p) {
	float v = 0.0;
	float a = 0.5;
	for (int i = 0; i < CLOUD_DETAIL; i++) {
		v += a * vnoise(p);
		p = p * 2.07 + vec2(17.1, 9.2);
		a *= 0.5;
	}
	return v;
}

// rgb = cloud color, a = coverage. dir = world-space view direction.
vec4 cloudLayer(vec3 dir, vec3 sunDir, vec3 sunCol, vec3 ambCol) {
#if !defined SOFT_CLOUDS || defined NETHER || defined END
	return vec4(0.0);
#else
	if (dir.y < 0.01) return vec4(0.0);
	float t = frameTimeCounter * 0.8;
	vec2 p = (dir.xz / dir.y) * 0.9 + cameraPosition.xz * 0.0012 + vec2(t, t * 0.35) * 0.004;
	float n = cloudFbm(p * 1.6);
	float coverage = smoothstep(0.50 - rainStrength * 0.25 - CLOUD_AMOUNT * 0.12, 0.80, n);
	if (coverage <= 0.0) return vec4(0.0);

	float thick = smoothstep(0.55, 0.95, n);
	float sd = max(dot(dir, sunDir), 0.0);
	vec3 lit = sunCol * 0.75 + ambCol * 1.25;
	vec3 shade = ambCol * 1.15 + sunCol * 0.18;
	vec3 c = mix(lit, shade, thick);
	c += sunCol * pow(sd, 8.0) * (1.0 - thick) * 0.55;    // silver lining toward the sun
	c = mix(c, vec3(luma(c)) * 0.8, rainStrength * 0.7);

	float fade = smoothstep(0.01, 0.18, dir.y);
	return vec4(c, coverage * fade * 0.92);
#endif
}

// Shadow map distortion: more detail near the player, less far away.
// Must match exactly between the shadow pass and the lookups.

vec3 distortShadow(vec3 p) {
	float f = length(p.xy) * 0.88 + 0.12;
	return vec3(p.xy / f, p.z * 0.25);
}

#if defined SHADOW_LOOKUP && defined FRAGMENT
uniform sampler2DShadow shadowtex0;

// Cheap per-pixel noise to hide filter patterns
float ign(vec2 p) { return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715)))); }

// One hardware-filtered lookup, used by volumetric light.
float shadowTap(vec3 shadowClip) {
	vec3 p = distortShadow(shadowClip) * 0.5 + 0.5;
	if (p.x <= 0.0 || p.x >= 1.0 || p.y <= 0.0 || p.y >= 1.0 || p.z >= 1.0) return 1.0;
	return texture(shadowtex0, p);
}

// shadowClip: undistorted shadow clip-space position (interpolated per vertex).
float getShadow(vec3 shadowClip) {
	vec3 p = distortShadow(shadowClip) * 0.5 + 0.5;
	if (p.x <= 0.0 || p.x >= 1.0 || p.y <= 0.0 || p.y >= 1.0 || p.z >= 1.0) return 1.0;
	p.z -= 0.00012;

#if SHADOW_FILTER == 0
	// 1 lookup. Hardware filtering still smooths the edge a little.
	return texture(shadowtex0, p);
#elif SHADOW_FILTER == 1
	// 4 lookups in a small rotated square
	float texel = 1.0 / float(textureSize(shadowtex0, 0).x);
	float a = ign(gl_FragCoord.xy) * 6.2831853;
	vec2 r = vec2(cos(a), sin(a)) * texel * 1.25;
	vec2 r2 = vec2(-r.y, r.x);
	return 0.25 * (texture(shadowtex0, p + vec3(r, 0.0)) + texture(shadowtex0, p - vec3(r, 0.0))
	             + texture(shadowtex0, p + vec3(r2, 0.0)) + texture(shadowtex0, p - vec3(r2, 0.0)));
#else
	// 8 lookups, softer
	float texel = 1.0 / float(textureSize(shadowtex0, 0).x);
	float a = ign(gl_FragCoord.xy) * 6.2831853;
	float s = 0.0;
	for (int i = 0; i < 8; i++) {
		float ang = a + float(i) * 2.3999632;
		float rad = sqrt((float(i) + 0.5) / 8.0) * 2.2 * texel;
		s += texture(shadowtex0, p + vec3(cos(ang) * rad, sin(ang) * rad, 0.0));
	}
	return s * 0.125;
#endif
}
#endif

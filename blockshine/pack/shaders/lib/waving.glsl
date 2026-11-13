// Wind for plants and leaves. Runs in the vertex shader only.
// Gusts roll across the world in slow waves, so whole patches of grass lean together
// instead of each blade wobbling on its own.

vec3 waveOffset(vec3 worldPos, int id, bool topVertex) {
	vec3 o = vec3(0.0);
	float t = frameTimeCounter * WAVE_SPEED;
	vec2 windDir = normalize(vec2(1.0, 0.45));
	float along = dot(worldPos.xz, windDir);
	// Slow gust front moving with the wind, plus a little steady breeze
	float gust = 0.35 + 0.65 * pow(0.5 + 0.5 * sin(along * 0.11 - t * 0.9), 2.0);
	gust *= 1.0 + rainStrength * 1.3;
#ifdef WAVING_PLANTS
	if ((id == ID_PLANT && topVertex) || id == ID_PLANT_FULL) {
		float a = 0.10 * gust;
		float lean = 0.6 + 0.4 * sin(t * 1.9 + along * 0.6);          // bend with the wind...
		float flutter = sin(t * 4.3 + worldPos.x * 1.7 + worldPos.z * 1.3) * 0.25; // ...plus quick flutter
		o.xz = windDir * a * (lean + flutter);
		o.x += sin(t * 2.6 + worldPos.z * 2.1) * a * 0.15;
		o.y = -abs(lean) * a * 0.25;                                    // tips dip a little as they bend
	}
#endif
#ifdef WAVING_LEAVES
	if (id == ID_LEAVES) {
		float a = 0.035 * (0.5 + 0.5 * gust);
		o.x = sin(t * 1.4 + worldPos.x * 0.9 + worldPos.y * 0.5) * a + windDir.x * a * 0.6 * gust;
		o.y = sin(t * 2.1 + worldPos.y * 0.8 + worldPos.z * 0.6) * a * 0.45;
		o.z = sin(t * 1.2 + worldPos.z * 0.7 + worldPos.x * 0.4) * a + windDir.y * a * 0.6 * gust;
	}
#endif
	return o;
}

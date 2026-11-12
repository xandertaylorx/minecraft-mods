// Wind for plants and leaves. Runs in the vertex shader only (a handful of sin() calls per vertex).

vec3 waveOffset(vec3 worldPos, int id, bool topVertex) {
	vec3 o = vec3(0.0);
	float t = frameTimeCounter * WAVE_SPEED;
	float strength = 1.0 + rainStrength * 1.2;
#ifdef WAVING_PLANTS
	if ((id == ID_PLANT && topVertex) || id == ID_PLANT_FULL) {
		float a = 0.075 * strength;
		o.x = sin(t * 1.7 + worldPos.x * 0.7 + worldPos.z * 0.3) * a + sin(t * 3.1 + worldPos.z * 1.3) * a * 0.35;
		o.z = sin(t * 1.3 + worldPos.z * 0.6 + worldPos.x * 0.25 + 1.3) * a * 0.8;
	}
#endif
#ifdef WAVING_LEAVES
	if (id == ID_LEAVES) {
		float a = 0.03 * strength;
		o.x = sin(t * 1.1 + worldPos.x * 0.9 + worldPos.y * 0.5) * a;
		o.y = sin(t * 1.6 + worldPos.y * 0.8 + worldPos.z * 0.6) * a * 0.5;
		o.z = sin(t * 0.9 + worldPos.z * 0.7 + worldPos.x * 0.4) * a;
	}
#endif
	return o;
}

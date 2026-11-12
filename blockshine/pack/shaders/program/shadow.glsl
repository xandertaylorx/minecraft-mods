// Shadow map pass. Only depth matters; kept as light as possible.
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/consts.glsl"
#include "/lib/shadows.glsl"

#ifdef VERTEX
in vec4 mc_Entity;
in vec4 mc_midTexCoord;
uniform mat4 shadowModelViewInverse;
#include "/lib/waving.glsl"

out vec2 texcoord;
out float alphaTint;
flat out int skip;

void main() {
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	alphaTint = gl_Color.a;
	int id = int(mc_Entity.x + 0.5);
	skip = (id == ID_WATER) ? 1 : 0;   // water doesn't cast shadows

	vec4 sv = gl_ModelViewMatrix * gl_Vertex;
#if defined WAVING_PLANTS || defined WAVING_LEAVES
	if (id == ID_PLANT || id == ID_LEAVES || id == ID_PLANT_FULL) {
		vec3 playerPos = (shadowModelViewInverse * sv).xyz;
		playerPos += waveOffset(playerPos + cameraPosition, id, gl_MultiTexCoord0.t < mc_midTexCoord.t);
		sv = shadowModelView * vec4(playerPos, 1.0);
	}
#endif
	gl_Position = gl_ProjectionMatrix * sv;
	gl_Position.xyz = distortShadow(gl_Position.xyz);
}
#endif

#ifdef FRAGMENT
uniform sampler2D gtexture;
in vec2 texcoord;
in float alphaTint;
flat in int skip;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 shadowOut;

void main() {
	if (skip == 1) discard;
	float a = texture(gtexture, texcoord).a * alphaTint;
	if (a < 0.1) discard;
	shadowOut = vec4(1.0);
}
#endif

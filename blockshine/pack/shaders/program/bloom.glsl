// Bloom (composite1). The GPU builds blurred copies (mipmaps) of the frame almost for free;
// we just blend a few of them back in. 5 texture reads per pixel, no blur passes.
#include "/lib/settings.glsl"

#ifdef VERTEX
out vec2 texcoord;
void main() {
	texcoord = gl_MultiTexCoord0.xy;
	gl_Position = ftransform();
}
#endif

#ifdef FRAGMENT
const bool colortex0MipmapEnabled = true;
uniform sampler2D colortex0;
in vec2 texcoord;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

void main() {
	vec3 col = texture(colortex0, texcoord).rgb;
	vec3 b = textureLod(colortex0, texcoord, 2.0).rgb * 0.10
	       + textureLod(colortex0, texcoord, 3.0).rgb * 0.18
	       + textureLod(colortex0, texcoord, 4.0).rgb * 0.24
	       + textureLod(colortex0, texcoord, 5.0).rgb * 0.26
	       + textureLod(colortex0, texcoord, 6.0).rgb * 0.22;
	// Only the bright parts glow noticeably
	b *= smoothstep(0.0, 1.5, dot(b, vec3(0.333)));
	col += b * BLOOM_STRENGTH * 4.0;
	outColor = vec4(col, 1.0);
}
#endif

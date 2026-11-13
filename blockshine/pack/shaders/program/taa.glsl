// Temporal anti-aliasing (composite3). Every frame is drawn with a tiny sub-pixel nudge (lib/common.glsl),
// and here each pixel is blended with where that same spot was last frame. Over a few frames, jagged
// block edges, shimmering leaves and filter noise from shadows/AO average out into a smooth, soft image.
//  - Reprojection: depth + this/last frame's camera tells us where the point was last frame.
//  - Ghosting guard: last frame's color is clamped to the range of colors around this pixel now,
//    so moving things and newly revealed areas don't leave trails.
// Cost: 9 reads of the current frame + 1 history read per pixel.
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
/*
const bool colortex3Clear = false;
*/
uniform sampler2D colortex0;
uniform sampler2D colortex3;
uniform sampler2D depthtex0;
uniform mat4 gbufferPreviousModelView;
uniform mat4 gbufferPreviousProjection;
uniform vec3 previousCameraPosition;
in vec2 texcoord;

/* RENDERTARGETS: 0,3 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outHistory;

// Blend in a compressed space so one very bright pixel can't dominate (stops fireflies flickering)
vec3 squash(vec3 c)   { return c / (1.0 + luma(c)); }
vec3 unsquash(vec3 c) { return c / max(1.0 - luma(c), 1e-4); }

vec2 previousUV(vec2 uv, float depth) {
	if (depth < 0.56) return uv;                            // held item: it moves with the camera
	vec4 view = gbufferProjectionInverse * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
	view /= view.w;
	vec3 world = (gbufferModelViewInverse * view).xyz + cameraPosition - previousCameraPosition;
	vec4 prev = gbufferPreviousProjection * (gbufferPreviousModelView * vec4(world, 1.0));
	return prev.xy / prev.w * 0.5 + 0.5;
}

void main() {
	vec2 px = 1.0 / vec2(viewWidth, viewHeight);
	vec3 cur = squash(texture(colortex0, texcoord).rgb);

	// Range of colors in the 3x3 neighborhood right now
	vec3 lo = cur, hi = cur;
	for (int y = -1; y <= 1; y++)
	for (int x = -1; x <= 1; x++) {
		if (x == 0 && y == 0) continue;
		vec3 n = squash(texture(colortex0, texcoord + vec2(x, y) * px).rgb);
		lo = min(lo, n);
		hi = max(hi, n);
	}

	vec2 prevUV = previousUV(texcoord, texture(depthtex0, texcoord).r);
	vec3 result = cur;
	if (all(greaterThan(prevUV, vec2(0.0))) && all(lessThan(prevUV, vec2(1.0)))) {
		vec3 hist = clamp(texture(colortex3, prevUV).rgb, lo, hi);
		// Keep more history when the camera is still; react faster when the view moves a lot
		float moved = length((prevUV - texcoord) / px);
		float keep = mix(0.90, 0.75, clamp(moved / 12.0, 0.0, 1.0));
		result = mix(cur, hist, keep);
	}

	outHistory = vec4(result, 1.0);
	outColor = vec4(unsquash(result), 1.0);
}
#endif

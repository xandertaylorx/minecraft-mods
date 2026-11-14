package com.massdestruction.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Drawing helpers. Glow is drawn with plain coloured quads in the additive lightning render type (light adds up, so
 * layered beams get a white-hot core). Soft things (smoke, fire, cloud) are camera-facing textured cards.
 */
public final class Draw {
	private Draw() {
	}

	public static final int FULL_BRIGHT = 0xF000F0;

	// ------------------------------------------------------------------ noise

	private static float hash(int x, int y, int z) {
		int h = x * 374761393 + y * 668265263 + z * 1274126177;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0xFFFFFF;
	}

	public static float hash(float n) {
		return hash(Float.floatToIntBits(n), 7, 13);
	}

	/** Smooth value noise in 0..1. */
	public static float noise(float x, float y, float z) {
		int ix = Mth.floor(x), iy = Mth.floor(y), iz = Mth.floor(z);
		float fx = x - ix, fy = y - iy, fz = z - iz;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		fz = fz * fz * (3 - 2 * fz);
		float a = Mth.lerp(fx, hash(ix, iy, iz), hash(ix + 1, iy, iz));
		float b = Mth.lerp(fx, hash(ix, iy + 1, iz), hash(ix + 1, iy + 1, iz));
		float c = Mth.lerp(fx, hash(ix, iy, iz + 1), hash(ix + 1, iy, iz + 1));
		float d = Mth.lerp(fx, hash(ix, iy + 1, iz + 1), hash(ix + 1, iy + 1, iz + 1));
		return Mth.lerp(fz, Mth.lerp(fy, a, b), Mth.lerp(fy, c, d));
	}

	public static float smooth(float e0, float e1, float x) {
		float t = Mth.clamp((x - e0) / (e1 - e0), 0, 1);
		return t * t * (3 - 2 * t);
	}

	// ------------------------------------------------------------------ coloured (additive) geometry

	/** One quad, both windings, one colour. */
	public static void quad(Matrix4fc m, VertexConsumer b, float x0, float y0, float z0, float x1, float y1, float z1,
		float x2, float y2, float z2, float x3, float y3, float z3, float r, float g, float bl, float a) {
		if (a <= 0.002F) return;
		b.addVertex(m, x0, y0, z0).setColor(r, g, bl, a);
		b.addVertex(m, x1, y1, z1).setColor(r, g, bl, a);
		b.addVertex(m, x2, y2, z2).setColor(r, g, bl, a);
		b.addVertex(m, x3, y3, z3).setColor(r, g, bl, a);
		b.addVertex(m, x3, y3, z3).setColor(r, g, bl, a);
		b.addVertex(m, x2, y2, z2).setColor(r, g, bl, a);
		b.addVertex(m, x1, y1, z1).setColor(r, g, bl, a);
		b.addVertex(m, x0, y0, z0).setColor(r, g, bl, a);
	}

	/** A quad with a colour per corner (rgba arrays), both windings. */
	public static void quad4(Matrix4fc m, VertexConsumer b, float[] p, float[] c0, float[] c1, float[] c2, float[] c3) {
		b.addVertex(m, p[0], p[1], p[2]).setColor(c0[0], c0[1], c0[2], c0[3]);
		b.addVertex(m, p[3], p[4], p[5]).setColor(c1[0], c1[1], c1[2], c1[3]);
		b.addVertex(m, p[6], p[7], p[8]).setColor(c2[0], c2[1], c2[2], c2[3]);
		b.addVertex(m, p[9], p[10], p[11]).setColor(c3[0], c3[1], c3[2], c3[3]);
		b.addVertex(m, p[9], p[10], p[11]).setColor(c3[0], c3[1], c3[2], c3[3]);
		b.addVertex(m, p[6], p[7], p[8]).setColor(c2[0], c2[1], c2[2], c2[3]);
		b.addVertex(m, p[3], p[4], p[5]).setColor(c1[0], c1[1], c1[2], c1[3]);
		b.addVertex(m, p[0], p[1], p[2]).setColor(c0[0], c0[1], c0[2], c0[3]);
	}

	/** A flat ring (or disc, inner = 0) in the plane y, between two angles (radians). */
	public static void arc(Matrix4fc m, VertexConsumer b, float y, float inner, float outer, float a0, float a1, int seg,
		float r, float g, float bl, float a) {
		if (outer <= inner || a <= 0.002F) return;
		float step = (a1 - a0) / seg;
		for (int i = 0; i < seg; i++) {
			float s = a0 + i * step, e = s + step;
			float cs = Mth.cos(s), ss = Mth.sin(s), ce = Mth.cos(e), se = Mth.sin(e);
			quad(m, b, cs * inner, y, ss * inner, cs * outer, y, ss * outer, ce * outer, y, se * outer, ce * inner, y, se * inner, r, g, bl, a);
		}
	}

	public static void ring(Matrix4fc m, VertexConsumer b, float y, float inner, float outer, int seg, float r, float g, float bl, float a) {
		arc(m, b, y, inner, outer, 0, Mth.TWO_PI, seg, r, g, bl, a);
	}

	/** A straight line on the plane y, drawn as a thin flat quad. */
	public static void line(Matrix4fc m, VertexConsumer b, float y, float x0, float z0, float x1, float z1, float w,
		float r, float g, float bl, float a) {
		float dx = x1 - x0, dz = z1 - z0, len = Mth.sqrt(dx * dx + dz * dz);
		if (len < 1e-4F) return;
		float px = -dz / len * w * 0.5F, pz = dx / len * w * 0.5F;
		quad(m, b, x0 + px, y, z0 + pz, x1 + px, y, z1 + pz, x1 - px, y, z1 - pz, x0 - px, y, z0 - pz, r, g, bl, a);
	}

	/** A vertical round column (open tube), {@code sides} faces. */
	public static void tube(Matrix4fc m, VertexConsumer b, float radius, float bottom, float top, int sides, float r, float g, float bl, float a) {
		if (radius <= 0.001F || a <= 0.002F) return;
		float step = Mth.TWO_PI / sides;
		for (int i = 0; i < sides; i++) {
			float s = i * step, e = s + step;
			float x0 = Mth.cos(s) * radius, z0 = Mth.sin(s) * radius, x1 = Mth.cos(e) * radius, z1 = Mth.sin(e) * radius;
			quad(m, b, x0, bottom, z0, x1, bottom, z1, x1, top, z1, x0, top, z0, r, g, bl, a);
		}
	}

	/** A camera-facing flat ring (a halo) centred at (cx, cy, cz). {@code right}/{@code up} are the camera's axes. */
	public static void halo(Matrix4fc m, VertexConsumer b, Vector3f right, Vector3f up, float cx, float cy, float cz,
		float inner, float outer, int seg, float[] cIn, float[] cOut) {
		float step = Mth.TWO_PI / seg;
		float[] p = new float[12];
		for (int i = 0; i < seg; i++) {
			float s = i * step, e = s + step;
			float cs = Mth.cos(s), ss = Mth.sin(s), ce = Mth.cos(e), se = Mth.sin(e);
			put(p, 0, cx, cy, cz, right, up, cs * inner, ss * inner);
			put(p, 3, cx, cy, cz, right, up, cs * outer, ss * outer);
			put(p, 6, cx, cy, cz, right, up, ce * outer, se * outer);
			put(p, 9, cx, cy, cz, right, up, ce * inner, se * inner);
			quad4(m, b, p, cIn, cOut, cOut, cIn);
		}
	}

	private static void put(float[] p, int o, float cx, float cy, float cz, Vector3f right, Vector3f up, float u, float v) {
		p[o] = cx + right.x * u + up.x * v;
		p[o + 1] = cy + right.y * u + up.y * v;
		p[o + 2] = cz + right.z * u + up.z * v;
	}

	/** The camera's right and up directions, for cards that face it. */
	public static Vector3f right(Quaternionf camera) {
		return camera.transform(new Vector3f(1, 0, 0));
	}

	public static Vector3f up(Quaternionf camera) {
		return camera.transform(new Vector3f(0, 1, 0));
	}

	// ------------------------------------------------------------------ textured cards (smoke, fire)

	/** A camera-facing square card, rotated by {@code spin}, in an entity-format render type (lit full bright). */
	public static void card(PoseStack.Pose pose, VertexConsumer b, Vector3f right, Vector3f up, float x, float y, float z,
		float size, float spin, float r, float g, float bl, float a) {
		if (a <= 0.003F || size <= 0.01F) return;
		float c = Mth.cos(spin) * size, s = Mth.sin(spin) * size;
		// rotated corner offsets: (±1, ±1) turned by spin
		float[][] k = {{-c + s, -s - c}, {c + s, s - c}, {c - s, s + c}, {-c - s, -s + c}};
		float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
		for (int i = 0; i < 4; i++) {
			float u = k[i][0], v = k[i][1];
			b.addVertex(pose, x + right.x * u + up.x * v, y + right.y * u + up.y * v, z + right.z * u + up.z * v)
				.setColor(r, g, bl, a).setUv(uv[i][0], uv[i][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 1, 0);
		}
	}

	/** One textured quad with its own UVs and colour, both windings. */
	public static void texQuad(PoseStack.Pose pose, VertexConsumer b, float[] p, float[] uv, float r, float g, float bl, float a) {
		if (a <= 0.003F) return;
		for (int pass = 0; pass < 2; pass++) {
			for (int k = 0; k < 4; k++) {
				int i = pass == 0 ? k : 3 - k;
				b.addVertex(pose, p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).setColor(r, g, bl, a).setUv(uv[i * 2], uv[i * 2 + 1])
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 1, 0);
			}
		}
	}
}

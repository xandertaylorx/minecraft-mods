package com.massdestruction.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4fc;

/**
 * Circles of runes, drawn the way Small Magic writes them: two thin rims, a turning star polygon, an inner ring, and a
 * band of glyphs between them. Each glyph is two to four strokes picked from a 3x3 grid by a hash, so every circle
 * reads like writing but none of it is a real alphabet. {@code written} (0..1) writes it in, rim first, glyph by glyph.
 */
public final class Runes {
	private Runes() {
	}

	/** A level circle at height y, radius R, turned by {@code turn} radians. */
	public static void circle(Matrix4fc m, VertexConsumer b, float y, float R, float turn, int sides, float written, float seed,
		float r, float g, float bl, float a) {
		if (written <= 0 || a <= 0.003F) return;
		float w = Math.max(0.06F, R * 0.012F);
		float rim = Math.min(1, written * 2.5F);
		int seg = Math.max(24, (int) (R * 6));
		// the rims, drawn round as it is written
		Draw.arc(m, b, y, R * 0.985F, R, turn, turn + Mth.TWO_PI * rim, seg, r, g, bl, a);
		Draw.arc(m, b, y, R * 0.94F, R * 0.94F + w, -turn, -turn + Mth.TWO_PI * rim, seg, r, g, bl, a * 0.8F);
		Draw.arc(m, b, y, R * 0.62F, R * 0.62F + w, turn * 1.3F, turn * 1.3F + Mth.TWO_PI * rim, seg, r, g, bl, a * 0.8F);
		// a faint wash across it, brightest at the rim
		Draw.ring(m, b, y - 0.01F, R * 0.62F, R * 0.94F, seg, r, g, bl, a * 0.05F * rim);
		// the star
		float star = Mth.clamp(written * 2.5F - 0.6F, 0, 1);
		if (star > 0 && sides >= 3) {
			int step = sides >= 5 ? 2 : 1;
			float rs = R * 0.93F;
			for (int i = 0; i < sides; i++) {
				float a0 = turn * 0.6F + i * Mth.TWO_PI / sides, a1 = turn * 0.6F + (i + step) * Mth.TWO_PI / sides;
				float x0 = Mth.cos(a0) * rs, z0 = Mth.sin(a0) * rs;
				float x1 = Mth.lerp(star, x0, Mth.cos(a1) * rs), z1 = Mth.lerp(star, z0, Mth.sin(a1) * rs);
				Draw.line(m, b, y, x0, z0, x1, z1, w * 1.2F, r, g, bl, a);
			}
			// and a smaller one turning the other way inside
			float ri = R * 0.6F;
			for (int i = 0; i < sides; i++) {
				float a0 = -turn + i * Mth.TWO_PI / sides, a1 = -turn + (i + 1) * Mth.TWO_PI / sides;
				Draw.line(m, b, y, Mth.cos(a0) * ri, Mth.sin(a0) * ri, Mth.lerp(star, Mth.cos(a0), Mth.cos(a1)) * ri, Mth.lerp(star, Mth.sin(a0), Mth.sin(a1)) * ri, w, r, g, bl, a * 0.8F);
			}
		}
		// the glyphs, written one after another
		int count = Math.max(8, (int) (R * 2.4F));
		float glyphs = Mth.clamp(written * 1.6F - 0.4F, 0, 1) * count;
		float inner = R * 0.68F, outer = R * 0.88F, cell = Mth.TWO_PI / count;
		for (int i = 0; i < glyphs; i++) {
			float fade = Math.min(1, glyphs - i);
			float flicker = 0.7F + 0.3F * Mth.sin(i * 2.4F + seed + turn * 9);
			glyph(m, b, y, turn * 0.8F + i * cell, cell * 0.72F, inner, outer, (int) (seed * 97) + i * 31, w, r, g, bl, a * fade * flicker);
		}
	}

	/** One glyph in a cell of the band: angle a (cell centre), angular width aw, between radii ri and ro. */
	private static void glyph(Matrix4fc m, VertexConsumer b, float y, float a, float aw, float ri, float ro, int id, float w,
		float r, float g, float bl, float al) {
		int strokes = 2 + (int) (Draw.hash(id * 1.37F) * 3);
		for (int s = 0; s < strokes; s++) {
			int p = (int) (Draw.hash(id * 7.1F + s * 3.3F) * 9), q = (int) (Draw.hash(id * 3.7F + s * 5.9F + 1) * 9);
			if (p == q) q = (q + 4) % 9;
			float u0 = (p % 3) / 2.0F - 0.5F, v0 = (p / 3) / 2.0F, u1 = (q % 3) / 2.0F - 0.5F, v1 = (q / 3) / 2.0F;
			float a0 = a + u0 * aw, r0 = Mth.lerp(v0, ri, ro), a1 = a + u1 * aw, r1 = Mth.lerp(v1, ri, ro);
			Draw.line(m, b, y, Mth.cos(a0) * r0, Mth.sin(a0) * r0, Mth.cos(a1) * r1, Mth.sin(a1) * r1, w, r, g, bl, al);
		}
	}

	/** Bands of glyphs going round a standing cylinder of radius R from y0 to y1 (a wall of runes). */
	public static void wall(Matrix4fc m, VertexConsumer b, float R, float y0, float y1, float turn, float seed, float r, float g, float bl, float a) {
		if (a <= 0.003F) return;
		int bands = 3;
		for (int k = 0; k < bands; k++) {
			float h0 = Mth.lerp((k + 0.15F) / bands, y0, y1), h1 = Mth.lerp((k + 0.85F) / bands, y0, y1);
			float fade = a * (1 - 0.28F * k);
			int count = Math.max(12, (int) (R * 3.2F) - k * 6);
			float cell = Mth.TWO_PI / count, dir = (k % 2 == 0 ? 1 : -1) * (0.8F + 0.3F * k);
			for (int i = 0; i < count; i++) {
				float ca = turn * dir + i * cell;
				int id = (int) (seed * 53) + i * 17 + k * 101;
				int strokes = 2 + (int) (Draw.hash(id * 1.37F) * 3);
				float flick = 0.6F + 0.4F * Mth.sin(i * 2.4F + turn * 6);
				for (int s = 0; s < strokes; s++) {
					int p = (int) (Draw.hash(id * 7.1F + s * 3.3F) * 9), q = (int) (Draw.hash(id * 3.7F + s * 5.9F + 1) * 9);
					if (p == q) q = (q + 4) % 9;
					float a0 = ca + ((p % 3) / 2.0F - 0.5F) * cell * 0.7F, a1 = ca + ((q % 3) / 2.0F - 0.5F) * cell * 0.7F;
					float yy0 = Mth.lerp((p / 3) / 2.0F, h0, h1), yy1 = Mth.lerp((q / 3) / 2.0F, h0, h1);
					stroke(m, b, R, a0, yy0, a1, yy1, Math.max(0.07F, R * 0.008F), r, g, bl, fade * flick);
				}
			}
			// a thin line under each band
			for (int i = 0; i < 48; i++) {
				float s0 = i * Mth.TWO_PI / 48, s1 = s0 + Mth.TWO_PI / 48;
				Draw.quad(m, b, Mth.cos(s0) * R, h0 - 0.25F, Mth.sin(s0) * R, Mth.cos(s1) * R, h0 - 0.25F, Mth.sin(s1) * R,
					Mth.cos(s1) * R, h0 - 0.18F, Mth.sin(s1) * R, Mth.cos(s0) * R, h0 - 0.18F, Mth.sin(s0) * R, r, g, bl, fade * 0.6F);
			}
		}
	}

	/** A stroke on the surface of a vertical cylinder. */
	private static void stroke(Matrix4fc m, VertexConsumer b, float R, float a0, float y0, float a1, float y1, float w, float r, float g, float bl, float al) {
		float x0 = Mth.cos(a0) * R, z0 = Mth.sin(a0) * R, x1 = Mth.cos(a1) * R, z1 = Mth.sin(a1) * R;
		float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
		float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1e-4F) return;
		// across the stroke, in the cylinder's surface: (along) x (outward normal)
		float nx = Mth.cos((a0 + a1) * 0.5F), nz = Mth.sin((a0 + a1) * 0.5F);
		float cx = dy * nz, cy = dz * nx - dx * nz, cz = -dy * nx;
		float cl = Mth.sqrt(cx * cx + cy * cy + cz * cz);
		if (cl < 1e-5F) return;
		cx = cx / cl * w * 0.5F;
		cy = cy / cl * w * 0.5F;
		cz = cz / cl * w * 0.5F;
		Draw.quad(m, b, x0 + cx, y0 + cy, z0 + cz, x1 + cx, y1 + cy, z1 + cz, x1 - cx, y1 - cy, z1 - cz, x0 - cx, y0 - cy, z0 - cz, r, g, bl, al);
	}
}

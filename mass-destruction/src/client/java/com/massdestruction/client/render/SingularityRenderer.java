package com.massdestruction.client.render;

import com.massdestruction.client.ClientDebris;
import com.massdestruction.entity.SingularityEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.massdestruction.entity.SingularityEntity.*;

/**
 * The black hole. Its middle is a hole into a field of stars (the End portal's sky, seen through a ball). Round it a
 * disc of what it has taken turns, faster the nearer in so it is drawn out into streaks, white at its inner edge and
 * dying out through gold and rose to violet. The far side of the disc is seen bent up over the top and down under the
 * bottom of the hole, as a ring that always faces you, and a thin bright ring hugs the edge. A band of runes turns
 * round the disc. Torn-up blocks circle in it, and are thrown out in a level ring when it bursts.
 */
public class SingularityRenderer extends EntityRenderer<SingularityEntity, SingularityRenderer.State> {
	private static final float TILT = 14.0F;

	public SingularityRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected boolean affectedByCulling(SingularityEntity entity) {
		return false;
	}

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		float t = s.ageInTicks, R = s.radius, burst = s.sinceBurst;
		Vector3f right = Draw.right(camera.orientation), up = Draw.up(camera.orientation);
		if (burst < 0 && R > 0.02F) {
			// the hole: a ball you see a starfield through
			collector.submitCustomGeometry(pose, RenderTypes.endPortal(), (p, b) -> sphere(p.pose(), b, R));
			float on = Math.min(1, t / 14.0F) * (1 - s.closing * s.closing);
			float spinT = t / 20.0F * (1 + 7 * s.closing);
			// the bent image of the far side of the disc, and the bright edge, facing the eye
			collector.submitCustomGeometry(pose, RenderTypes.lightning(), (p, b) -> {
				Matrix4fc m = p.pose();
				lensed(m, b, right, up, R, spinT, on);
				Draw.halo(m, b, right, up, 0, 0, 0, R * 1.0F, R * 1.12F, 64, new float[] {1, 0.92F, 0.8F, 0.9F * on}, new float[] {1, 0.7F, 0.5F, 0.0F});
				Draw.halo(m, b, right, up, 0, 0, 0, R * 1.1F, R * 5.5F, 48, new float[] {0.5F, 0.2F, 0.9F, 0.18F * on}, new float[] {0.2F, 0.05F, 0.5F, 0});
			});
			// the disc and the band of runes, level but tipped a little so it is seen from above
			pose.pushPose();
			pose.rotateDegrees(Axis.YP, s.yaw);
			pose.rotateDegrees(Axis.XP, TILT);
			collector.submitCustomGeometry(pose, RenderTypes.lightning(), (p, b) -> {
				Matrix4fc m = p.pose();
				disc(m, b, R, spinT, on);
				Runes.circle(m, b, 0, R * DISC * 1.14F, t * 0.012F, 7, Math.min(1, t / 26.0F), 3.3F, 0.75F, 0.4F, 1.0F, 0.8F * on);
				Runes.circle(m, b, 0.02F, R * DISC * 1.42F, -t * 0.008F, 5, Math.min(1, t / 40.0F), 8.1F, 0.6F, 0.3F, 1.0F, 0.45F * on);
			});
			pose.popPose();
		} else if (burst >= 0) {
			// the ring it throws out, and a last flash
			collector.submitCustomGeometry(pose, RenderTypes.lightning(), (p, b) -> {
				Matrix4fc m = p.pose();
				float k = burst / BURST;
				float r = 2 + burst * 2.2F;
				Draw.ring(m, b, 0, Math.max(0, r - 1.6F), r, 96, 0.85F, 0.7F, 1.0F, 0.9F * (1 - k));
				Draw.ring(m, b, 0, Math.max(0, r * 0.8F - 0.8F), r * 0.8F, 96, 0.6F, 0.4F, 1.0F, 0.5F * (1 - k));
				Draw.halo(m, b, right, up, 0, 0, 0, 0, 6 + burst * 1.4F, 32, new float[] {1, 0.95F, 1, Math.max(0, 1 - burst / 8)}, new float[] {0.6F, 0.4F, 1, 0});
			});
		}
		// torn-up blocks
		for (Piece d : s.pieces) {
			pose.pushPose();
			pose.translate(d.x, d.y, d.z);
			pose.rotate(d.spin);
			pose.scale(d.scale, d.scale, d.scale);
			pose.translate(-0.5F, -0.5F, -0.5F);
			collector.submitMovingBlock(pose, d.block, EntityRenderState.NO_OUTLINE);
			pose.popPose();
		}
		super.submit(s, pose, collector, camera);
	}

	private static void sphere(Matrix4fc m, VertexConsumer b, float R) {
		int la = 14, lo = 24;
		for (int i = 0; i < la; i++) {
			float p0 = Mth.PI * i / la, p1 = Mth.PI * (i + 1) / la;
			for (int j = 0; j < lo; j++) {
				float t0 = Mth.TWO_PI * j / lo, t1 = Mth.TWO_PI * (j + 1) / lo;
				float[][] v = {pt(R, p0, t0), pt(R, p1, t0), pt(R, p1, t1), pt(R, p0, t1)};
				for (int k = 0; k < 4; k++) b.addVertex(m, v[k][0], v[k][1], v[k][2]);
				for (int k = 3; k >= 0; k--) b.addVertex(m, v[k][0], v[k][1], v[k][2]);
			}
		}
	}

	private static float[] pt(float R, float phi, float th) {
		return new float[] {R * Mth.sin(phi) * Mth.cos(th), R * Mth.cos(phi), R * Mth.sin(phi) * Mth.sin(th)};
	}

	/** How bright the disc is at a point: hottest at the inner edge, in streaks drawn out by the shear. */
	private static float heat(float rr, float ang, float spinT) {
		float u = (rr - INNER) / (DISC - INNER);
		float a = ang + spinT * 3.2F / (float) Math.pow(rr, 1.5);
		float cx = Mth.cos(a), cz = Mth.sin(a);
		float n = Draw.noise(cx * 1.3F + 10, cz * 1.3F, rr * 2.2F) * 0.5F + Draw.noise(cx * 3.1F + 5, cz * 3.1F, rr * 5) * 0.32F
			+ Draw.noise(cx * 7 + 9, cz * 7, rr * 11) * 0.18F;
		return (1 - u) * (0.28F + 1.45F * n);
	}

	/** Painted in a few tones: white at the inner edge, then gold, rose, and violet where it thins to nothing. */
	private static float[] tone(float t, float u, float on) {
		float[] c = {0.16F, 0.05F, 0.62F};
		c = lerp(c, new float[] {0.7F, 0.25F, 1.0F}, Draw.smooth(0.2F, 0.26F, t));
		c = lerp(c, new float[] {1.0F, 0.4F, 0.6F}, Draw.smooth(0.3F, 0.36F, t));
		c = lerp(c, new float[] {1.0F, 0.62F, 0.2F}, Draw.smooth(0.36F, 0.43F, t));
		c = lerp(c, new float[] {1.0F, 0.96F, 0.88F}, Draw.smooth(0.62F, 0.72F, t));
		float a = Draw.smooth(0.08F, 0.17F, t) * Draw.smooth(0, 0.06F, u) * Draw.smooth(1.0F, 0.85F, u) * on;
		return new float[] {c[0], c[1], c[2], Math.min(1, a * (0.6F + t))};
	}

	private static float[] lerp(float[] a, float[] b, float k) {
		return new float[] {Mth.lerp(k, a[0], b[0]), Mth.lerp(k, a[1], b[1]), Mth.lerp(k, a[2], b[2])};
	}

	private static void disc(Matrix4fc m, VertexConsumer b, float R, float spinT, float on) {
		int nr = 12, na = 144;
		float[][][] cols = new float[nr + 1][na + 1][];
		float[] radii = new float[nr + 1];
		for (int i = 0; i <= nr; i++) {
			float rr = INNER + (DISC - INNER) * i / nr;
			radii[i] = rr * R;
			for (int j = 0; j <= na; j++) cols[i][j] = tone(heat(rr, Mth.TWO_PI * j / na, spinT), (float) i / nr, on);
		}
		float[] p = new float[12];
		for (int i = 0; i < nr; i++) {
			for (int j = 0; j < na; j++) {
				float a0 = Mth.TWO_PI * j / na, a1 = Mth.TWO_PI * (j + 1) / na;
				p[0] = Mth.cos(a0) * radii[i];
				p[1] = 0;
				p[2] = Mth.sin(a0) * radii[i];
				p[3] = Mth.cos(a0) * radii[i + 1];
				p[4] = 0;
				p[5] = Mth.sin(a0) * radii[i + 1];
				p[6] = Mth.cos(a1) * radii[i + 1];
				p[7] = 0;
				p[8] = Mth.sin(a1) * radii[i + 1];
				p[9] = Mth.cos(a1) * radii[i];
				p[10] = 0;
				p[11] = Mth.sin(a1) * radii[i];
				Draw.quad4(m, b, p, cols[i][j], cols[i + 1][j], cols[i + 1][j + 1], cols[i][j + 1]);
			}
		}
	}

	/** The disc's far side, bent round the hole: a ring that faces the eye, hugging the hole. */
	private static void lensed(Matrix4fc m, VertexConsumer b, Vector3f right, Vector3f up, float R, float spinT, float on) {
		int nr = 6, na = 96;
		float[] p = new float[12];
		for (int i = 0; i < nr; i++) {
			float u0 = (float) i / nr, u1 = (float) (i + 1) / nr;
			float r0 = R * (1.1F + 0.9F * u0), r1 = R * (1.1F + 0.9F * u1);
			for (int j = 0; j < na; j++) {
				float a0 = Mth.TWO_PI * j / na, a1 = Mth.TWO_PI * (j + 1) / na;
				// brightest over the top and under the bottom, where the far side of the disc is bent into view
				float w0 = 0.35F + 0.65F * Math.abs(Mth.sin(a0)), w1 = 0.35F + 0.65F * Math.abs(Mth.sin(a1));
				float[] c00 = tone(heat(INNER + (DISC - INNER) * u0 * 0.6F, a0, spinT), u0 * 0.6F + 0.08F, on * 0.75F * w0);
				float[] c10 = tone(heat(INNER + (DISC - INNER) * u1 * 0.6F, a0, spinT), u1 * 0.6F + 0.08F, on * 0.75F * w0);
				float[] c11 = tone(heat(INNER + (DISC - INNER) * u1 * 0.6F, a1, spinT), u1 * 0.6F + 0.08F, on * 0.75F * w1);
				float[] c01 = tone(heat(INNER + (DISC - INNER) * u0 * 0.6F, a1, spinT), u0 * 0.6F + 0.08F, on * 0.75F * w1);
				set(p, 0, right, up, Mth.cos(a0) * r0, Mth.sin(a0) * r0);
				set(p, 3, right, up, Mth.cos(a0) * r1, Mth.sin(a0) * r1);
				set(p, 6, right, up, Mth.cos(a1) * r1, Mth.sin(a1) * r1);
				set(p, 9, right, up, Mth.cos(a1) * r0, Mth.sin(a1) * r0);
				Draw.quad4(m, b, p, c00, c10, c11, c01);
			}
		}
	}

	private static void set(float[] p, int o, Vector3f right, Vector3f up, float u, float v) {
		p[o] = right.x * u + up.x * v;
		p[o + 1] = right.y * u + up.y * v;
		p[o + 2] = right.z * u + up.z * v;
	}

	// ------------------------------------------------------------------ state

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SingularityEntity e, State s, float partial) {
		super.extractRenderState(e, s, partial);
		s.ageInTicks = e.age(partial);
		s.radius = e.radius(partial);
		s.closing = e.closing(partial);
		s.sinceBurst = e.sinceBurst(partial);
		s.yaw = (e.getId() * 47) % 360;
		s.pieces.clear();
		if (!(e.level() instanceof ClientLevel level)) return;
		float now = level.getGameTime() + partial;
		ClientDebris.prune(e.getId(), now, 360);
		Quaternionf tilt = new Quaternionf().rotateY(s.yaw * Mth.DEG_TO_RAD).rotateX(TILT * Mth.DEG_TO_RAD);
		double cx = e.getX(), cy = e.getY(), cz = e.getZ();
		for (ClientDebris.Piece d : ClientDebris.of(e.getId())) {
			float age = now - d.born;
			float rr = RH * (INNER + (DISC - INNER) * d.r1) * 0.85F;
			float f = 1;
			float swallow = 150 + 90 * d.r2;
			if (age > swallow) f = 1 - (age - swallow) / 30.0F;   // drawn in at last, and gone
			if (f <= 0) continue;
			rr *= f * (float) Math.pow(1 - s.closing, 2);
			float a0 = (float) Mth.atan2(d.z - cz, d.x - cx);
			float a = a0 + 0.3F / (float) Math.pow(rr / RH + 0.3F, 1.5) * Math.min(age, swallow + 30);
			Vector3f orbit = tilt.transform(new Vector3f(Mth.cos(a) * rr, Mth.sin(a * 3 + d.r3 * 6) * 0.5F, Mth.sin(a) * rr));
			float x, y, z;
			if (s.sinceBurst >= 0) {
				float sb = s.sinceBurst;
				if (sb > 45) continue;
				float sp = 0.9F + 1.2F * d.r2;
				x = Mth.cos(a) * (rr + sb * sp);
				z = Mth.sin(a) * (rr + sb * sp);
				y = orbit.y + (d.r3 - 0.5F) * sb * 0.3F - 0.015F * sb * sb;
			} else {
				float u = Mth.clamp(age / 26.0F, 0, 1), ease = u * u * (3 - 2 * u);
				x = Mth.lerp(ease, (float) (d.x - cx), orbit.x);
				y = Mth.lerp(ease, (float) (d.y - cy), orbit.y) + Mth.sin(u * Mth.PI) * 3;
				z = Mth.lerp(ease, (float) (d.z - cz), orbit.z);
			}
			Piece p = new Piece();
			p.x = x;
			p.y = y;
			p.z = z;
			p.scale = 0.9F * Math.max(0.1F, f);
			p.spin = new Quaternionf().rotateAxis(age * (0.05F + 0.15F * d.r4), new Vector3f(d.r1 - 0.5F, d.r2 - 0.5F + 0.01F, d.r3 - 0.5F).normalize());
			BlockPos at = BlockPos.containing(cx + x, cy + y, cz + z);
			MovingBlockRenderState mb = new MovingBlockRenderState();
			mb.blockState = d.state;
			mb.blockPos = at;
			mb.randomSeedPos = at;
			mb.biome = level.getBiome(at);
			mb.cardinalLighting = level.cardinalLighting();
			mb.lightEngine = level.getLightEngine();
			p.block = mb;
			s.pieces.add(p);
		}
	}

	public static class State extends EntityRenderState {
		public float radius;
		public float closing;
		public float sinceBurst;
		public float yaw;
		public final List<Piece> pieces = new ArrayList<>();
	}

	static class Piece {
		float x, y, z, scale;
		Quaternionf spin;
		MovingBlockRenderState block;
	}
}

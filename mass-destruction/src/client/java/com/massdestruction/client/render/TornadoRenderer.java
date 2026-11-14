package com.massdestruction.client.render;

import com.massdestruction.MassDestruction;
import com.massdestruction.client.ClientDebris;
import com.massdestruction.entity.TornadoEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.massdestruction.entity.TornadoEntity.*;

/**
 * The tornado. While it gathers: a circle of runes on the ground, rings of runes in the air one above another, and a
 * lid of dark cloud winding itself up overhead with lightning flickering in it. Then lightning strikes, the rings
 * burst, and the funnel is driven down: three nested sleeves of streaked wind turning at different speeds about an
 * axis that bends and leans back from the way it goes, wide at the top, thin at the foot, with a skirt of dust where
 * it touches. Over fire it burns; over water it is a pale spout. Torn-up blocks climb it in a helix and are thrown out.
 */
public class TornadoRenderer extends EntityRenderer<TornadoEntity, TornadoRenderer.State> {
	private static final Identifier STREAK = MassDestruction.id("textures/fx/streak.png");
	private static final Identifier PUFF = MassDestruction.id("textures/fx/puff.png");
	private static final float[] RING_Y = {12, 24, 36};
	private static final float[] RING_R = {6, 9, 12};

	public TornadoRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected boolean affectedByCulling(TornadoEntity entity) {
		return false;
	}

	/** Where the funnel's axis is at height h (it bends, leans back and wanders). */
	static float[] axis(State s, float h) {
		float k = h / HEIGHT, lean = (float) Math.pow(k, 1.5);
		float t = s.ageInTicks;
		return new float[] {s.leanX * lean + Mth.sin(t * 0.07F + h * 0.08F) * 1.6F * k, s.leanZ * lean + Mth.cos(t * 0.06F + h * 0.07F) * 1.6F * k};
	}

	static float radius(float h) {
		float k = h / HEIGHT;
		return R0 + (R1 - R0) * (float) Math.pow(k, 1.8) + 2.2F * (float) Math.exp(-h / 2.5);
	}

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		float t = s.ageInTicks;
		Vector3f right = Draw.right(camera.orientation), up = Draw.up(camera.orientation);
		// runes while it gathers, and the burst when it strikes
		if (t < GATHER + 14) {
			collector.submitCustomGeometry(pose, RenderTypes.lightning(), (p, b) -> runes(p.pose(), b, t));
		}
		if (s.down > 0.01F) {
			collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(STREAK), (p, b) -> funnel(p, b, s));
			if (s.kind == KIND_FIRE) {
				collector.submitCustomGeometry(pose, RenderTypes.lightning(), (p, b) -> {
					float bottom = HEIGHT * (1 - s.down);
					for (float h = bottom; h < HEIGHT - 4; h += 3) {
						float[] a = axis(s, h);
						Draw.halo(p.pose(), b, right, up, a[0], h, a[1], 0, radius(h) * 0.9F, 16, new float[] {1, 0.45F, 0.1F, 0.12F}, new float[] {1, 0.2F, 0.05F, 0});
					}
				});
			}
		}
		if (s.storm > 0.01F) {
			Vector3f eye = new Vector3f((float) (camera.pos.x - s.x), (float) (camera.pos.y - s.y), (float) (camera.pos.z - s.z));
			List<float[]> cards = lid(s, eye);
			collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(PUFF), (p, b) -> {
				for (float[] c : cards) Draw.card(p, b, right, up, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8]);
			});
		}
		for (Piece d : s.pieces) {
			pose.pushPose();
			pose.translate(d.x, d.y, d.z);
			pose.rotate(d.spin);
			pose.scale(0.85F, 0.85F, 0.85F);
			pose.translate(-0.5F, -0.5F, -0.5F);
			collector.submitMovingBlock(pose, d.block, EntityRenderState.NO_OUTLINE);
			pose.popPose();
		}
		super.submit(s, pose, collector, camera);
	}

	private static void runes(Matrix4fc m, VertexConsumer b, float t) {
		float k = Math.min(1, t / GATHER);
		float burst = t < GATHER ? 0 : (t - GATHER) / 14.0F;
		float a = 1 - burst;
		Runes.circle(m, b, 0.12F, 9.5F, t * 0.04F, 6, Math.min(1, k * 1.6F), 2.2F, 0.65F, 0.85F, 1.0F, 0.85F * a);
		for (int i = 0; i < RING_Y.length; i++) {
			float written = Mth.clamp((t - 10 - i * 10) / 14.0F, 0, 1);
			float R = RING_R[i] * (1 + 1.5F * burst * burst);
			Runes.circle(m, b, RING_Y[i], R, t * (i % 2 == 0 ? 0.05F : -0.05F), 4 + i, written, 5.5F + i, 0.6F, 0.8F, 1.0F, 0.75F * a);
		}
	}

	private static void funnel(PoseStack.Pose pose, VertexConsumer b, State s) {
		float t = s.ageInTicks;
		float bottom = HEIGHT * (1 - s.down);
		int NH = 34, NS = 30;
		float[][] layers = {{1.35F, 0.2F, 0.012F, -0.025F, 2}, {1.0F, 0.62F, 0.022F, -0.045F, 3}, {0.55F, 0.45F, -0.04F, -0.07F, 2}};
		float[] lo, hi;
		if (s.kind == KIND_FIRE) {
			lo = new float[] {1.0F, 0.5F, 0.12F};
			hi = new float[] {0.25F, 0.12F, 0.1F};
		} else if (s.kind == KIND_WATER) {
			lo = new float[] {0.8F, 0.9F, 1.0F};
			hi = new float[] {0.55F, 0.6F, 0.68F};
		} else {
			lo = new float[] {0.6F, 0.56F, 0.5F};
			hi = new float[] {0.38F, 0.38F, 0.42F};
		}
		float[] p = new float[12], uv = new float[8];
		for (float[] L : layers) {
			for (int i = 0; i < NH; i++) {
				float h0 = Mth.lerp((float) i / NH, bottom, HEIGHT), h1 = Mth.lerp((float) (i + 1) / NH, bottom, HEIGHT);
				float[] a0 = axis(s, h0), a1 = axis(s, h1);
				float r0 = radius(h0) * L[0], r1 = radius(h1) * L[0];
				float k = (h0 + h1) * 0.5F / HEIGHT;
				// fades in where it meets the ground and out into the cloud
				float alpha = L[1] * Draw.smooth(0, 0.06F, (h0 - bottom) / HEIGHT + 0.02F) * (1 - Draw.smooth(0.82F, 1.0F, k));
				float cr = Mth.lerp(k, lo[0], hi[0]), cg = Mth.lerp(k, lo[1], hi[1]), cb = Mth.lerp(k, lo[2], hi[2]);
				// the nearer the foot, the faster it turns
				float spin0 = t * L[2] * (1.6F - 0.8F * h0 / HEIGHT), spin1 = t * L[2] * (1.6F - 0.8F * h1 / HEIGHT);
				for (int j = 0; j < NS; j++) {
					float s0 = Mth.TWO_PI * j / NS, s1 = Mth.TWO_PI * (j + 1) / NS;
					p[0] = a0[0] + Mth.cos(s0) * r0;
					p[1] = h0;
					p[2] = a0[1] + Mth.sin(s0) * r0;
					p[3] = a0[0] + Mth.cos(s1) * r0;
					p[4] = h0;
					p[5] = a0[1] + Mth.sin(s1) * r0;
					p[6] = a1[0] + Mth.cos(s1) * r1;
					p[7] = h1;
					p[8] = a1[1] + Mth.sin(s1) * r1;
					p[9] = a1[0] + Mth.cos(s0) * r1;
					p[10] = h1;
					p[11] = a1[1] + Mth.sin(s0) * r1;
					float u0 = (float) j / NS * L[4], u1 = (float) (j + 1) / NS * L[4];
					float v0 = h0 / HEIGHT * 2 + t * L[3], v1 = h1 / HEIGHT * 2 + t * L[3];
					uv[0] = u0 + spin0;
					uv[1] = v0;
					uv[2] = u1 + spin0;
					uv[3] = v0;
					uv[4] = u1 + spin1;
					uv[5] = v1;
					uv[6] = u0 + spin1;
					uv[7] = v1;
					Draw.texQuad(pose, b, p, uv, cr, cg, cb, alpha * s.down);
				}
			}
		}
	}

	/** The lid of cloud: rings of soft cards turning slowly round the top of the funnel, lit by flickers of lightning. */
	private static List<float[]> lid(State s, Vector3f eye) {
		List<float[]> out = new ArrayList<>();
		float t = s.ageInTicks, storm = s.storm;
		float[] top = axis(s, HEIGHT);
		int bolt = (int) (t / 4);
		boolean flash = Draw.hash(bolt * 1.31F) > 0.86F;
		float fx = (Draw.hash(bolt * 2.7F) - 0.5F) * 50, fz = (Draw.hash(bolt * 5.3F) - 0.5F) * 50;
		int ring = 0;
		for (float r = 4; r < 54 * (0.4F + 0.6F * storm); r += 5.5F, ring++) {
			int n = Math.max(6, (int) (r * 0.9F));
			float spin = t * 0.25F / (r + 6);
			for (int i = 0; i < n; i++) {
				float a = spin + i * Mth.TWO_PI / n + ring * 0.7F;
				float wob = Draw.noise(i * 0.7F, ring, t * 0.01F);
				float x = top[0] + Mth.cos(a) * r, z = top[1] + Mth.sin(a) * r;
				float y = HEIGHT + 2 + wob * 4 - r * 0.06F + (1 - storm) * 12;
				float lit = flash ? Math.max(0, 1 - Mth.sqrt((x - fx) * (x - fx) + (z - fz) * (z - fz)) / 24) : 0;
				float g = 0.17F + 0.12F * wob + 0.6F * lit;
				float size = 5.5F + r * 0.12F + wob * 2;
				out.add(new float[] {x, y, z, size, a + wob * 3, g, g, g + 0.04F + 0.2F * lit, 0.9F * storm * Math.min(1, (54 - r) / 12)});
			}
		}
		out.sort((p, q) -> Float.compare(dist(q, eye), dist(p, eye)));
		return out;
	}

	private static float dist(float[] c, Vector3f eye) {
		float dx = c[0] - eye.x, dy = c[1] - eye.y, dz = c[2] - eye.z;
		return dx * dx + dy * dy + dz * dz;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(TornadoEntity e, State s, float partial) {
		super.extractRenderState(e, s, partial);
		s.ageInTicks = e.age(partial);
		s.down = e.down(partial);
		s.storm = e.storm(partial);
		s.kind = e.kind();
		// lean back from the way it is going
		float vx = (float) (e.getX() - e.xo), vz = (float) (e.getZ() - e.zo);
		s.leanX = Mth.clamp(-vx * 40, -10, 10);
		s.leanZ = Mth.clamp(-vz * 40, -10, 10);
		s.pieces.clear();
		if (!(e.level() instanceof ClientLevel level)) return;
		float now = level.getGameTime() + partial;
		ClientDebris.prune(e.getId(), now, 220);
		double bx = Mth.lerp(partial, e.xo, e.getX()), by = Mth.lerp(partial, e.yo, e.getY()), bz = Mth.lerp(partial, e.zo, e.getZ());
		for (ClientDebris.Piece d : ClientDebris.of(e.getId())) {
			float age = now - d.born;
			float climb = 0.3F + 0.22F * d.r1, topH = HEIGHT * (0.6F + 0.25F * d.r2), topT = topH / climb;
			float a = (float) Mth.atan2(d.z - bz, d.x - bx) + age * (0.3F + 0.2F * d.r3);
			float x, y, z;
			if (age < topT) {
				float h = age * climb;
				float r = radius(h) * (0.85F + 0.4F * d.r2) + 0.8F;
				float[] ax = axis(s, h);
				x = ax[0] + Mth.cos(a) * r;
				y = h;
				z = ax[1] + Mth.sin(a) * r;
				float u = Math.min(1, age / 8.0F);
				x = Mth.lerp(u, (float) (d.x - bx), x);
				y = Mth.lerp(u, (float) (d.y - by), y);
				z = Mth.lerp(u, (float) (d.z - bz), z);
			} else {
				float dt = age - topT;
				if (dt > 60) continue;
				float aT = (float) Mth.atan2(d.z - bz, d.x - bx) + topT * (0.3F + 0.2F * d.r3);
				float r = radius(topH) + dt * (0.6F + 0.8F * d.r4);
				float[] ax = axis(s, topH);
				x = ax[0] + Mth.cos(aT) * r;
				z = ax[1] + Mth.sin(aT) * r;
				y = topH + dt * 0.25F - 0.02F * dt * dt;
			}
			Piece p = new Piece();
			p.x = x;
			p.y = y;
			p.z = z;
			p.spin = new Quaternionf().rotateAxis(age * (0.1F + 0.2F * d.r4), new Vector3f(d.r1 - 0.5F, d.r2 - 0.5F + 0.01F, d.r3 - 0.5F).normalize());
			BlockPos at = BlockPos.containing(bx + x, by + y, bz + z);
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
		public float down;
		public float storm;
		public int kind;
		public float leanX;
		public float leanZ;
		public final List<Piece> pieces = new ArrayList<>();
	}

	static class Piece {
		float x, y, z;
		Quaternionf spin;
		MovingBlockRenderState block;
	}
}

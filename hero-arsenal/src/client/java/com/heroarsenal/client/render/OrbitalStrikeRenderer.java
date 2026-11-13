package com.heroarsenal.client.render;

import com.heroarsenal.entity.OrbitalStrikeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import org.joml.Matrix4fc;

/**
 * Draws the strike with plain colored quads in the additive lightning render type: target rings, a crosshair
 * and light pillars while charging, then a layered plasma column with energy rings sliding down it, a glowing
 * impact disk and shockwaves. A few hundred quads at most, no textures, no particles.
 */
public class OrbitalStrikeRenderer extends EntityRenderer<OrbitalStrikeEntity, OrbitalStrikeRenderer.State> {
	private static final int RING_SEGMENTS = 40;
	private static final float BEAM_TOP = 320.0F;
	private static final float BEAM_BOTTOM = -OrbitalStrikeEntity.CRATER_RADIUS;

	public OrbitalStrikeRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected boolean affectedByCulling(OrbitalStrikeEntity entity) {
		// The beam is far taller than the entity, so frustum culling by its tiny box would hide it.
		return false;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		float t = state.ticksSinceFire;
		float charge = Mth.clamp(1.0F + t / OrbitalStrikeEntity.CHARGE_TICKS, 0.0F, 1.0F);
		boolean firing = t >= 0.0F;
		float beamLife = firing ? t / OrbitalStrikeEntity.BEAM_TICKS : 0.0F;
		if (beamLife >= 1.0F) {
			return;
		}
		float age = state.ageInTicks;
		float spin = age * (2.0F + 12.0F * charge);
		float pulse = 0.7F + 0.3F * Mth.sin(age * (0.4F + charge * 1.2F));
		float maxRadius = (float) OrbitalStrikeEntity.BLAST_RADIUS;

		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			Matrix4fc m = pose.pose();
			if (!firing) {
				// Double outer boundary at the full blast radius.
				ring(m, buffer, 0.0F, 0.0F, maxRadius - 0.5F, maxRadius, 0.12F, 1.0F, 0.2F, 0.1F, 0.6F * pulse);
				ring(m, buffer, 0.0F, 0.0F, maxRadius + 0.6F, maxRadius + 0.85F, 0.12F, 1.0F, 0.35F, 0.1F, 0.35F * pulse);
				// Three ripples that keep collapsing inward, faster as it charges.
				for (int i = 0; i < 3; i++) {
					float phase = (age * (0.02F + 0.05F * charge) + i / 3.0F) % 1.0F;
					float r = maxRadius * (1.0F - phase);
					ring(m, buffer, 0.0F, 0.0F, r - 0.3F, r, 0.14F, 1.0F, 0.6F, 0.2F, 0.7F * phase);
				}
				// Rotating crosshair and counter-rotating tick marks.
				for (int i = 0; i < 4; i++) {
					float a = (spin + i * 90.0F) * Mth.DEG_TO_RAD;
					spoke(m, buffer, a, 1.5F, maxRadius - 1.0F, 0.18F, 0.16F, 1.0F, 0.3F, 0.15F, 0.65F * pulse);
				}
				for (int i = 0; i < 12; i++) {
					float a = (-spin * 0.6F + i * 30.0F) * Mth.DEG_TO_RAD;
					spoke(m, buffer, a, maxRadius - 2.4F, maxRadius - 1.2F, 0.14F, 0.16F, 1.0F, 0.75F, 0.3F, 0.7F);
				}
				// Light pillars rising from the rim, growing as it charges.
				for (int i = 0; i < 6; i++) {
					float a = (spin * 0.5F + i * 60.0F) * Mth.DEG_TO_RAD;
					column(m, buffer, Mth.cos(a) * maxRadius, Mth.sin(a) * maxRadius, 0.2F, 0.0F, 2.0F + 12.0F * charge,
						1.0F, 0.4F, 0.2F, 0.45F * charge);
				}
				// Aiming laser that thickens and flickers white just before the shot.
				boolean locked = charge > 0.8F;
				float flick = locked ? 0.6F + 0.4F * Mth.sin(age * 4.0F) : 1.0F;
				column(m, buffer, 0.0F, 0.0F, 0.06F + 0.4F * charge * charge, BEAM_BOTTOM, BEAM_TOP,
					1.0F, locked ? 0.9F : 0.35F, locked ? 0.8F : 0.25F, (0.3F + 0.5F * charge) * flick);
			} else {
				// Fast punch-in, hold, then thin out at the end.
				float grow = Mth.clamp(t / 3.0F, 0.0F, 1.0F);
				float fade = beamLife < 0.65F ? 1.0F : 1.0F - (beamLife - 0.65F) / 0.35F;
				float flicker = 0.88F + 0.12F * Mth.sin(age * 3.1F);
				float w = grow * fade * flicker;
				column(m, buffer, 0.0F, 0.0F, 7.0F * w, BEAM_BOTTOM, BEAM_TOP, 0.3F, 0.45F, 1.0F, 0.12F * fade);
				column(m, buffer, 0.0F, 0.0F, 4.6F * w, BEAM_BOTTOM, BEAM_TOP, 0.35F, 0.7F, 1.0F, 0.22F * fade);
				column(m, buffer, 0.0F, 0.0F, 2.8F * w, BEAM_BOTTOM, BEAM_TOP, 0.6F, 0.9F, 1.0F, 0.45F * fade);
				column(m, buffer, 0.0F, 0.0F, 1.4F * w, BEAM_BOTTOM, BEAM_TOP, 1.0F, 1.0F, 1.0F, 0.95F * fade);
				// Energy rings racing down the beam.
				for (int i = 0; i < 7; i++) {
					float y = 90.0F - ((age * 4.0F + i * 13.0F) % 91.0F);
					float r = (5.5F + 1.5F * Mth.sin(age * 0.3F + i)) * w;
					ring(m, buffer, 0.0F, 0.0F, r - 0.5F, r, y, 0.6F, 0.85F, 1.0F, 0.5F * fade);
				}
				// Molten glow pooled in the crater.
				ring(m, buffer, 0.0F, 0.0F, 0.0F, OrbitalStrikeEntity.CRATER_RADIUS * 0.8F * grow, 0.3F, 1.0F, 0.45F, 0.1F, 0.35F * fade);
				// Two shockwaves racing outward along the ground.
				shockwave(m, buffer, t, 0.0F, 10.0F, maxRadius + 10.0F, 1.6F, 0.9F);
				shockwave(m, buffer, t, 4.0F, 14.0F, maxRadius + 18.0F, 1.0F, 0.6F);
			}
		});
		super.submit(state, poseStack, collector, camera);
	}

	private static void shockwave(Matrix4fc m, VertexConsumer b, float t, float start, float duration, float maxRadius, float width, float alpha) {
		float p = (t - start) / duration;
		if (p <= 0.0F || p >= 1.0F) {
			return;
		}
		float r = Mth.sqrt(p) * maxRadius;
		ring(m, b, 0.0F, 0.0F, r - width, r, 0.35F, 0.7F, 0.9F, 1.0F, alpha * (1.0F - p));
	}

	/** A vertical square column, drawn two-sided. */
	private static void column(Matrix4fc m, VertexConsumer b, float cx, float cz, float r, float bottom, float top,
		float red, float green, float blue, float alpha) {
		if (r <= 0.001F || alpha <= 0.001F) {
			return;
		}
		float[][] corners = {{-r, -r}, {r, -r}, {r, r}, {-r, r}};
		for (int i = 0; i < 4; i++) {
			float[] p = corners[i];
			float[] q = corners[(i + 1) % 4];
			quad(m, b,
				cx + p[0], bottom, cz + p[1],
				cx + q[0], bottom, cz + q[1],
				cx + q[0], top, cz + q[1],
				cx + p[0], top, cz + p[1],
				red, green, blue, alpha);
		}
	}

	/** A flat horizontal ring (or disk, with inner = 0) made of {@link #RING_SEGMENTS} quads. */
	private static void ring(Matrix4fc m, VertexConsumer b, float cx, float cz, float inner, float outer, float y,
		float red, float green, float blue, float alpha) {
		inner = Math.max(0.0F, inner);
		if (outer <= inner || alpha <= 0.001F) {
			return;
		}
		float step = Mth.TWO_PI / RING_SEGMENTS;
		for (int i = 0; i < RING_SEGMENTS; i++) {
			float a0 = i * step;
			float a1 = a0 + step;
			float c0 = Mth.cos(a0);
			float s0 = Mth.sin(a0);
			float c1 = Mth.cos(a1);
			float s1 = Mth.sin(a1);
			quad(m, b,
				cx + c0 * inner, y, cz + s0 * inner,
				cx + c0 * outer, y, cz + s0 * outer,
				cx + c1 * outer, y, cz + s1 * outer,
				cx + c1 * inner, y, cz + s1 * inner,
				red, green, blue, alpha);
		}
	}

	/** A flat bar on the ground pointing outward at the given angle. */
	private static void spoke(Matrix4fc m, VertexConsumer b, float angle, float from, float to, float halfWidth, float y,
		float red, float green, float blue, float alpha) {
		float dx = Mth.cos(angle);
		float dz = Mth.sin(angle);
		float px = -dz * halfWidth;
		float pz = dx * halfWidth;
		quad(m, b,
			dx * from + px, y, dz * from + pz,
			dx * to + px, y, dz * to + pz,
			dx * to - px, y, dz * to - pz,
			dx * from - px, y, dz * from - pz,
			red, green, blue, alpha);
	}

	/** Emits the quad in both windings so it is visible from either side. */
	private static void quad(Matrix4fc m, VertexConsumer b,
		float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
		float red, float green, float blue, float alpha) {
		b.addVertex(m, x0, y0, z0).setColor(red, green, blue, alpha);
		b.addVertex(m, x1, y1, z1).setColor(red, green, blue, alpha);
		b.addVertex(m, x2, y2, z2).setColor(red, green, blue, alpha);
		b.addVertex(m, x3, y3, z3).setColor(red, green, blue, alpha);
		b.addVertex(m, x3, y3, z3).setColor(red, green, blue, alpha);
		b.addVertex(m, x2, y2, z2).setColor(red, green, blue, alpha);
		b.addVertex(m, x1, y1, z1).setColor(red, green, blue, alpha);
		b.addVertex(m, x0, y0, z0).setColor(red, green, blue, alpha);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(OrbitalStrikeEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.ticksSinceFire = entity.ticksSinceFire(partialTicks);
	}

	public static class State extends EntityRenderState {
		public float ticksSinceFire;
	}
}

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
 * Draws the strike with plain colored quads in the additive lightning render type: a target ring on the
 * ground while charging, then a layered plasma column. About 150 quads in total, no textures, no particles.
 */
public class OrbitalStrikeRenderer extends EntityRenderer<OrbitalStrikeEntity, OrbitalStrikeRenderer.State> {
	private static final int RING_SEGMENTS = 32;
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
		float spin = state.ageInTicks * (2.0F + 10.0F * charge);
		float pulse = 0.75F + 0.25F * Mth.sin(state.ageInTicks * (0.4F + charge * 0.8F));
		float maxRadius = (float) OrbitalStrikeEntity.BLAST_RADIUS;

		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			Matrix4fc m = pose.pose();
			if (!firing) {
				// Outer boundary ring (full blast radius) and an inner ring that closes in as it charges.
				ring(m, buffer, maxRadius - 0.35F, maxRadius, 0.12F, 1.0F, 0.25F, 0.15F, 0.55F * pulse);
				float inner = maxRadius * (1.0F - 0.8F * charge);
				ring(m, buffer, inner - 0.25F, inner, 0.14F, 1.0F, 0.55F, 0.2F, 0.7F);
				// Rotating crosshair.
				for (int i = 0; i < 4; i++) {
					float a = (spin + i * 90.0F) * Mth.DEG_TO_RAD;
					spoke(m, buffer, a, 1.2F, maxRadius - 0.6F, 0.13F, 0.18F, 1.0F, 0.3F, 0.2F, 0.6F * pulse);
				}
				// Thin aiming laser that thickens as the strike locks on.
				column(m, buffer, 0.05F + 0.25F * charge * charge, 1.0F, 0.35F, 0.25F, 0.25F + 0.35F * charge);
			} else {
				// Fast punch-in, hold, then thin out at the end.
				float grow = Mth.clamp(t / 3.0F, 0.0F, 1.0F);
				float fade = beamLife < 0.7F ? 1.0F : 1.0F - (beamLife - 0.7F) / 0.3F;
				float flicker = 0.9F + 0.1F * Mth.sin(state.ageInTicks * 3.1F);
				float w = grow * fade * flicker;
				column(m, buffer, 4.2F * w, 0.35F, 0.6F, 1.0F, 0.18F * fade);
				column(m, buffer, 2.6F * w, 0.5F, 0.85F, 1.0F, 0.35F * fade);
				column(m, buffer, 1.2F * w, 1.0F, 1.0F, 1.0F, 0.9F * fade);
				// Shockwave ring racing outward along the ground.
				float wave = Mth.clamp(t / 8.0F, 0.0F, 1.0F) * (maxRadius + 4.0F);
				float waveAlpha = 0.8F * (1.0F - Mth.clamp(t / 8.0F, 0.0F, 1.0F));
				if (waveAlpha > 0.01F) {
					ring(m, buffer, Math.max(0.0F, wave - 1.2F), wave, 0.3F, 0.7F, 0.9F, 1.0F, waveAlpha);
				}
			}
		});
		super.submit(state, poseStack, collector, camera);
	}

	/** A vertical square column centered on the strike, drawn two-sided. */
	private static void column(Matrix4fc m, VertexConsumer b, float r, float red, float green, float blue, float alpha) {
		if (r <= 0.001F || alpha <= 0.001F) {
			return;
		}
		float[][] corners = {{-r, -r}, {r, -r}, {r, r}, {-r, r}};
		for (int i = 0; i < 4; i++) {
			float[] p = corners[i];
			float[] q = corners[(i + 1) % 4];
			quad(m, b,
				p[0], BEAM_BOTTOM, p[1],
				q[0], BEAM_BOTTOM, q[1],
				q[0], BEAM_TOP, q[1],
				p[0], BEAM_TOP, p[1],
				red, green, blue, alpha);
		}
	}

	/** A flat ring on the ground made of {@link #RING_SEGMENTS} quads. */
	private static void ring(Matrix4fc m, VertexConsumer b, float inner, float outer, float y, float red, float green, float blue, float alpha) {
		inner = Math.max(0.0F, inner);
		float step = Mth.TWO_PI / RING_SEGMENTS;
		for (int i = 0; i < RING_SEGMENTS; i++) {
			float a0 = i * step;
			float a1 = a0 + step;
			float c0 = Mth.cos(a0);
			float s0 = Mth.sin(a0);
			float c1 = Mth.cos(a1);
			float s1 = Mth.sin(a1);
			quad(m, b,
				c0 * inner, y, s0 * inner,
				c0 * outer, y, s0 * outer,
				c1 * outer, y, s1 * outer,
				c1 * inner, y, s1 * inner,
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

package com.massdestruction.client.render;

import com.massdestruction.entity.AnnihilatorStrikeEntity;
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
import org.joml.Vector3f;

import static com.massdestruction.entity.AnnihilatorStrikeEntity.*;

/**
 * The Annihilator. Charging: circles of runes written one above another into the sky, a target on the ground whose
 * rings close in, and a thin aiming beam. Firing: a beam twenty blocks wide built of nested glowing tubes, its light
 * flowing downward in bands, wound round by two helices of energy, with rings racing down it; the sky circles flare
 * white and blow outward; the ground glows molten and shockwaves run out across it.
 */
public class AnnihilatorRenderer extends EntityRenderer<AnnihilatorStrikeEntity, AnnihilatorRenderer.State> {
	private static final float TOP = 420.0F;

	public AnnihilatorRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected boolean affectedByCulling(AnnihilatorStrikeEntity entity) {
		return false;
	}

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		float t = s.sinceFire;
		if (t >= BEAM + 20) return;
		float age = s.ageInTicks;
		Vector3f right = Draw.right(camera.orientation), up = Draw.up(camera.orientation);
		collector.submitCustomGeometry(pose, RenderTypes.lightning(), (p, b) -> {
			Matrix4fc m = p.pose();
			if (t < 0) charge(m, b, t, age);
			else fire(m, b, t, age, right, up);
		});
		super.submit(s, pose, collector, camera);
	}

	private static void charge(Matrix4fc m, VertexConsumer b, float t, float age) {
		float ct = t + CHARGE, k = ct / CHARGE;
		float pulse = 0.75F + 0.25F * Mth.sin(age * (0.3F + k));
		// the circles in the sky, written one after another; brighter as the charge completes
		for (int i = 0; i < RING_Y.length; i++) {
			float written = Mth.clamp((ct - ringWritten(i)) / 14.0F, 0, 1);
			float turn = age * (i % 2 == 0 ? 1 : -1) * (0.02F + 0.006F * i) * (1 + 2 * k);
			float hot = Draw.smooth(0.75F, 1.0F, k);
			Runes.circle(m, b, RING_Y[i], RING_R[i], turn, 5 + i % 3, written, i * 1.7F,
				0.35F + 0.65F * hot, 0.65F + 0.35F * hot, 1.0F, 0.75F * pulse);
		}
		// the target on the ground: a double rim, rings closing in faster and faster, a turning crosshair
		float R = (float) BLAST;
		Draw.ring(m, b, 0.12F, R - 0.6F, R, 64, 0.2F, 0.55F, 1.0F, 0.7F * pulse);
		Draw.ring(m, b, 0.12F, R + 0.8F, R + 1.1F, 64, 0.2F, 0.55F, 1.0F, 0.4F * pulse);
		for (int i = 0; i < 4; i++) {
			float ph = (age * (0.015F + 0.05F * k) + i / 4.0F) % 1.0F, r = R * (1 - ph);
			Draw.ring(m, b, 0.14F, Math.max(0, r - 0.4F), r, 48, 0.5F, 0.85F, 1.0F, 0.7F * ph);
		}
		float spin = age * (0.02F + 0.12F * k);
		for (int i = 0; i < 4; i++) {
			float a = spin + i * Mth.HALF_PI;
			Draw.line(m, b, 0.16F, Mth.cos(a) * 2.5F, Mth.sin(a) * 2.5F, Mth.cos(a) * (R - 1.5F), Mth.sin(a) * (R - 1.5F), 0.35F, 0.3F, 0.7F, 1.0F, 0.6F * pulse);
		}
		Runes.circle(m, b, 0.15F, CRATER + 2, -spin, 6, Math.min(1, k * 2), 9.1F, 0.4F, 0.8F, 1.0F, 0.8F);
		// standing light round the rim, growing
		for (int i = 0; i < 8; i++) {
			float a = spin * 0.5F + i * Mth.TWO_PI / 8;
			float x = Mth.cos(a) * R, z = Mth.sin(a) * R, h = 3 + 22 * k;
			Draw.quad(m, b, x - 0.3F, 0, z, x + 0.3F, 0, z, x + 0.3F, h, z, x - 0.3F, h, z, 0.4F, 0.7F, 1.0F, 0.4F * k);
			Draw.quad(m, b, x, 0, z - 0.3F, x, 0, z + 0.3F, x, h, z + 0.3F, x, h, z - 0.3F, 0.4F, 0.7F, 1.0F, 0.4F * k);
		}
		// the aiming beam: thin, then thicker and flickering white in the last moments
		boolean locked = k > 0.8F;
		float flick = locked ? 0.6F + 0.4F * Mth.sin(age * 4.0F) : 1.0F;
		Draw.tube(m, b, 0.08F + 0.7F * k * k, -2, TOP, 8, 0.6F, locked ? 1.0F : 0.75F, 1.0F, (0.3F + 0.5F * k) * flick);
		Draw.tube(m, b, 0.5F + 2.5F * k * k, -2, TOP, 12, 0.2F, 0.5F, 1.0F, 0.12F * k * flick);
	}

	private static void fire(Matrix4fc m, VertexConsumer b, float t, float age, Vector3f right, Vector3f up) {
		float grow = Draw.smooth(0, 4, t);
		float fade = t < BEAM * 0.7F ? 1 : Math.max(0, 1 - (t - BEAM * 0.7F) / (BEAM * 0.3F + 20));
		float w = grow * fade * (0.92F + 0.08F * Mth.sin(age * 2.7F)) * (1 + 0.15F * Draw.smooth(0, 60, t));
		if (w > 0.01F) {
			// the beam: nested tubes, the light in each flowing downward in bands
			float[][] layer = {{12.0F, 0.15F, 0.3F, 1.0F, 0.07F}, {8.5F, 0.25F, 0.55F, 1.0F, 0.12F}, {6.0F, 0.4F, 0.8F, 1.0F, 0.22F},
				{4.0F, 0.75F, 0.95F, 1.0F, 0.45F}, {2.4F, 1.0F, 1.0F, 1.0F, 0.95F}};
			for (int L = 0; L < layer.length; L++) {
				float[] c = layer[L];
				float rr = c[0] * w;
				int sides = 28;
				float step = 14.0F;
				for (float y = -CRATER; y < TOP; y += step) {
					float y1 = Math.min(TOP, y + step);
					// light bands sliding down the beam
					float n = Draw.noise(y * 0.035F + age * 0.55F, L * 3.1F, 0) * 0.8F + Draw.noise(y * 0.11F + age * 1.3F, L, 5) * 0.4F;
					float a = c[4] * fade * (0.55F + 0.7F * n) * (y > 260 ? Math.max(0, 1 - (y - 260) / 160) : 1);
					float sstep = Mth.TWO_PI / sides;
					for (int i = 0; i < sides; i++) {
						float s0 = i * sstep + age * 0.05F * (L % 2 == 0 ? 1 : -1), s1 = s0 + sstep;
						Draw.quad(m, b, Mth.cos(s0) * rr, y, Mth.sin(s0) * rr, Mth.cos(s1) * rr, y, Mth.sin(s1) * rr,
							Mth.cos(s1) * rr, y1, Mth.sin(s1) * rr, Mth.cos(s0) * rr, y1, Mth.sin(s0) * rr, c[1], c[2], c[3], a);
					}
				}
			}
			// two helices of energy winding down round it
			for (int h = 0; h < 2; h++) {
				float rr = 9.5F * w;
				for (int i = 0; i < 90; i++) {
					float y0 = i * 2.4F, y1 = y0 + 2.4F;
					float a0 = h * Mth.PI + y0 * 0.11F + age * 0.35F, a1 = h * Mth.PI + y1 * 0.11F + age * 0.35F;
					float al = 0.5F * fade * (1 - i / 90.0F);
					Draw.quad(m, b, Mth.cos(a0) * rr, y0 - 0.6F, Mth.sin(a0) * rr, Mth.cos(a1) * rr, y1 - 0.6F, Mth.sin(a1) * rr,
						Mth.cos(a1) * rr, y1 + 0.6F, Mth.sin(a1) * rr, Mth.cos(a0) * rr, y0 + 0.6F, Mth.sin(a0) * rr, 0.6F, 0.9F, 1.0F, al);
				}
			}
			// rings racing down it
			for (int i = 0; i < 9; i++) {
				float y = 150 - ((age * 5.0F + i * 17.0F) % 152.0F);
				float rr = (8.0F + 2.0F * Mth.sin(age * 0.3F + i)) * w;
				Draw.ring(m, b, y, rr - 0.7F, rr, 40, 0.6F, 0.9F, 1.0F, 0.55F * fade);
			}
			// a white glare where it meets the ground
			Draw.halo(m, b, right, up, 0, 1.5F, 0, 0, 16 * w, 32, new float[] {1, 1, 1, 0.8F * fade}, new float[] {0.3F, 0.6F, 1, 0});
			Draw.halo(m, b, right, up, 0, 1.5F, 0, 0, 32 * w, 32, new float[] {0.4F, 0.7F, 1, 0.25F * fade}, new float[] {0.2F, 0.4F, 1, 0});
		}
		// the sky circles flare white as the beam goes through, and blow outward
		for (int i = 0; i < RING_Y.length; i++) {
			float k = Mth.clamp((t - i * 1.5F) / 22.0F, 0, 1);
			if (k >= 1) continue;
			float R = RING_R[i] * (1 + 1.4F * k * k);
			Runes.circle(m, b, RING_Y[i], R, age * 0.08F, 5 + i % 3, 1, i * 1.7F, 1.0F, 1.0F, 1.0F, (1 - k) * 0.9F);
		}
		// molten ground, and the shockwaves
		Draw.ring(m, b, 0.25F, 0, CRATER * 0.9F * grow, 48, 1.0F, 0.45F, 0.1F, 0.4F * fade);
		Draw.ring(m, b, 0.3F, CRATER * 0.9F, (CRATER + 4) * grow, 48, 1.0F, 0.3F, 0.05F, 0.25F * fade);
		shock(m, b, t, 0, 12, (float) BLAST + 24, 2.2F, 1.0F);
		shock(m, b, t, 3, 18, (float) BLAST + 40, 1.4F, 0.7F);
		shock(m, b, t, 30, 24, (float) BLAST + 10, 1.0F, 0.45F);
	}

	private static void shock(Matrix4fc m, VertexConsumer b, float t, float start, float dur, float max, float width, float a) {
		float p = (t - start) / dur;
		if (p <= 0 || p >= 1) return;
		float r = Mth.sqrt(p) * max;
		Draw.ring(m, b, 0.4F, Math.max(0, r - width), r, 64, 0.75F, 0.9F, 1.0F, a * (1 - p));
		Draw.ring(m, b, 0.4F, Math.max(0, r - width * 4), r - width, 64, 0.4F, 0.6F, 1.0F, a * 0.3F * (1 - p));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(AnnihilatorStrikeEntity entity, State state, float partial) {
		super.extractRenderState(entity, state, partial);
		state.sinceFire = entity.sinceFire(partial);
	}

	public static class State extends EntityRenderState {
		public float sinceFire;
	}
}

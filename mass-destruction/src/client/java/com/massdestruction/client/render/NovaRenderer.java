package com.massdestruction.client.render;

import com.massdestruction.MassDestruction;
import com.massdestruction.entity.NovaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import static com.massdestruction.entity.NovaEntity.*;

/**
 * The nova. While it gathers: circles of runes one above another in the sky, a red wall of runes round the place, and
 * a star of light above the top circle. Then a spark falls through the circles, each flaring as it passes. Then the
 * fire, built from a few hundred soft cards: a white-hot ball that climbs, flattens and rolls under at its rim into a
 * mushroom on a stem, cooling from white through orange and red to a crust of smoke with fire in its cracks; a
 * ring of cloud flashes out round it, a surge of dust runs along the ground, and at last it is only smoke.
 */
public class NovaRenderer extends EntityRenderer<NovaEntity, NovaRenderer.State> {
	private static final Identifier PUFF = MassDestruction.id("textures/fx/puff.png");
	private static final int CAP = 170, STEM = 70, SURGE = 80;
	/** Fixed random numbers per card: [kind-specific a, b, c, size, noise seed]. */
	private static final float[][] SEEDS = new float[CAP + STEM + SURGE][5];

	static {
		Random r = new Random(0x0A0B0C);
		for (float[] s : SEEDS) for (int i = 0; i < s.length; i++) s[i] = r.nextFloat();
	}

	public NovaRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected boolean affectedByCulling(NovaEntity entity) {
		return false;
	}

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		float t = s.ageInTicks;
		Vector3f right = Draw.right(camera.orientation), up = Draw.up(camera.orientation);
		collector.submitCustomGeometry(pose, RenderTypes.lightning(), (p, b) -> glow(p.pose(), b, t, right, up));
		if (t >= BOOM) {
			float sec = (t - BOOM) / 20.0F;
			Vector3f eye = new Vector3f((float) (camera.pos.x - s.x), (float) (camera.pos.y - s.y), (float) (camera.pos.z - s.z));
			List<float[]> cards = cloud(sec, eye);
			collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(PUFF), (p, b) -> {
				for (float[] c : cards) Draw.card(p, b, right, up, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8]);
			});
		}
		super.submit(s, pose, collector, camera);
	}

	// ------------------------------------------------------------------ light

	private static void glow(Matrix4fc m, VertexConsumer b, float t, Vector3f right, Vector3f up) {
		float top = RING_Y[RING_Y.length - 1];
		if (t < BOOM) {
			float k = Math.min(1, t / GATHER);
			// the spark falls through the circles, top to ground
			float fall = t < GATHER ? -1 : (t - GATHER) / FALL, sparkY = top + 4 - (top + 4) * Math.max(0, fall);
			for (int i = 0; i < RING_Y.length; i++) {
				float written = Mth.clamp((t - ringWritten(i)) / 12.0F, 0, 1);
				float flare = fall >= 0 && sparkY <= RING_Y[i] + 1 ? Math.max(0, 1 - (RING_Y[i] - sparkY) / 14) : 0;
				float turn = t * (i % 2 == 0 ? 1 : -1) * (0.02F + 0.005F * i);
				Runes.circle(m, b, RING_Y[i], RING_R[i], turn, RING_SIDES[i], written, i * 2.3F,
					1.0F, 0.25F + 0.7F * flare, 0.05F + 0.8F * flare, 0.8F + 0.4F * flare);
			}
			// the wall of runes rising round the place
			Runes.wall(m, b, RING_R[0], 0, 3 + 9 * k, t * 0.03F, 4.4F, 1.0F, 0.25F, 0.05F, 0.75F * k);
			Draw.ring(m, b, 0.1F, RING_R[0] - 0.3F, RING_R[0], 64, 1.0F, 0.3F, 0.05F, 0.8F * k);
			// light gathered to a point above the top circle
			float star = fall < 0 ? k : 1;
			Draw.halo(m, b, right, up, 0, fall < 0 ? top + 4 : sparkY, 0, 0, 1.0F + 3.0F * star, 20, new float[] {1, 0.95F, 0.85F, star}, new float[] {1, 0.4F, 0.1F, 0});
			Draw.halo(m, b, right, up, 0, fall < 0 ? top + 4 : sparkY, 0, 0, 3.0F + 9.0F * star, 20, new float[] {1, 0.4F, 0.1F, 0.35F * star}, new float[] {1, 0.2F, 0.05F, 0});
			if (fall >= 0) Draw.tube(m, b, 0.25F, sparkY, top + 4, 8, 1, 0.8F, 0.5F, 0.6F);
			return;
		}
		float s = t - BOOM, sec = s / 20.0F;
		// the circles blow outward and are gone
		for (int i = 0; i < RING_Y.length; i++) {
			float k = Mth.clamp((s - i) / 18.0F, 0, 1);
			if (k < 1) Runes.circle(m, b, RING_Y[i], RING_R[i] * (1 + 1.6F * k * k), t * 0.06F, RING_SIDES[i], 1, i * 2.3F, 1, 0.9F, 0.7F, 1 - k);
		}
		float R = radius(sec), H = height(sec), hot = (float) Math.exp(-sec * 0.35);
		// the fire lights the air round it
		Draw.halo(m, b, right, up, 0, H, 0, R * 0.5F, R * 2.2F, 32, new float[] {1, 0.55F, 0.15F, 0.35F * hot}, new float[] {1, 0.25F, 0.05F, 0});
		Draw.halo(m, b, right, up, 0, 2, 0, 0, R * 2.6F, 32, new float[] {1, 0.5F, 0.15F, 0.25F * hot}, new float[] {1, 0.2F, 0.05F, 0});
		// the ring of cloud that flashes out round it
		if (sec > 0.2F && sec < 3.0F) {
			float k = (sec - 0.2F) / 2.8F;
			Draw.ring(m, b, H * 0.55F, R * (1.0F + 2.6F * k), R * (1.15F + 2.9F * k), 64, 1, 0.95F, 0.9F, 0.5F * (1 - k));
		}
		// the ring along the ground
		float wr = s * WAVE_SPEED;
		if (wr < WAVE_REACH * 1.5F) {
			float fade = 1 - wr / (WAVE_REACH * 1.5F);
			Draw.ring(m, b, 0.35F, Math.max(0, wr - 2.5F), wr, 72, 1.0F, 0.75F, 0.45F, 0.8F * fade);
			Draw.ring(m, b, 0.3F, Math.max(0, wr - 9), wr - 2.5F, 72, 1.0F, 0.4F, 0.1F, 0.25F * fade);
		}
		// the scorched ground glows for a while
		Draw.ring(m, b, 0.2F, 0, SCORCH, 48, 1.0F, 0.3F, 0.05F, 0.3F * hot);
	}

	public static float radius(float sec) {
		return 4 + 20 * (1 - (float) Math.exp(-sec * 1.6));
	}

	public static float height(float sec) {
		return radius(sec) * 0.7F + 32 * (1 - (float) Math.exp(-sec * 0.33));
	}

	// ------------------------------------------------------------------ the cloud

	/** Cards as [x, y, z, size, spin, r, g, b, a], sorted far to near so the soft edges blend. */
	private static List<float[]> cloud(float sec, Vector3f eye) {
		List<float[]> out = new ArrayList<>(CAP + STEM + SURGE);
		float R = radius(sec), H = height(sec);
		float m = Draw.smooth(0.4F, 2.8F, sec), hot = (float) Math.exp(-sec * 0.35);
		float calm = Draw.smooth(0.0F, 0.4F, sec);
		float end = CLOUD / 20.0F, gone = 1 - Draw.smooth(end - 4.5F, end, sec);
		if (gone <= 0) return out;
		float drift = Math.max(0, sec - 6) * 0.6F;   // late on it rises and spreads as it turns to smoke
		for (int i = 0; i < CAP; i++) {
			float[] sd = SEEDS[i];
			// where it sits on the ball (an even spread over a sphere)
			float yy = 1 - 2 * (i + 0.5F) / CAP, rad = Mth.sqrt(1 - yy * yy), th = i * 2.39996F;
			float depth = 0.7F + 0.3F * sd[0];
			float bx = Mth.cos(th) * rad * depth, by = yy * depth / (1 + 0.45F * m), bz = Mth.sin(th) * rad * depth;
			// and where it is on the rolling ring the cap becomes: up the middle, out across the top, down the rim, in underneath
			float psi = sd[1] * Mth.TWO_PI - sec * 0.8F;
			float major = 0.85F, minor = 0.5F * (0.7F + 0.3F * sd[2]);
			float tx = Mth.cos(th) * (major + minor * Mth.cos(psi)), ty = minor * Mth.sin(psi) * 0.8F, tz = Mth.sin(th) * (major + minor * Mth.cos(psi));
			float k = m * 0.85F;
			float x = Mth.lerp(k, bx, tx) * R, y = H + Mth.lerp(k, by, ty) * R + drift * 4, z = Mth.lerp(k, bz, tz) * R;
			float spread = 1 + drift * 0.08F;
			x *= spread;
			z *= spread;
			float inner = 1 - Mth.sqrt(bx * bx + by * by + bz * bz);
			float under = Mth.lerp(k, -by, -ty);
			float temp = hot * (0.45F + 0.75F * Draw.noise(sd[4] * 40, sec * 0.7F, i * 0.13F) + 0.5F * inner + 0.25F * under) + (1 - calm);
			float size = R * (0.42F + 0.35F * sd[3]) * (1 + 0.15F * sec) + drift;
			out.add(card(x, y, z, size, sd[2] * 6 + sec * (sd[0] - 0.5F), temp, gone));
		}
		for (int j = 0; j < STEM; j++) {
			float[] sd = SEEDS[CAP + j];
			float y = sd[0] * H * 0.95F;
			float sr = R * (0.18F + 0.32F * (float) Math.exp(-y / (R * 0.35F))) * (0.5F + 0.5F * sd[1]);
			float th = sd[2] * Mth.TWO_PI + sec * 0.4F;
			float temp = hot * (0.35F + 0.7F * Draw.noise(sd[4] * 40, sec * 0.8F, j * 0.2F) + 0.3F * (1 - y / H)) + (1 - calm) * 0.5F;
			float size = R * (0.25F + 0.2F * sd[3]) + drift * 0.6F;
			out.add(card(Mth.cos(th) * sr, y + drift * 2, Mth.sin(th) * sr, size, sd[3] * 6, temp, gone * m));
		}
		// the surge of dust and smoke running out along the ground
		float sr = Math.min(64, 6 + sec * 16);
		float surge = gone * (1 - Draw.smooth(4, 10, sec));
		for (int k = 0; k < SURGE; k++) {
			float[] sd = SEEDS[CAP + STEM + k];
			float th = sd[0] * Mth.TWO_PI, r = sr * (0.75F + 0.35F * sd[1]);
			float size = 3 + sec * 1.6F + sd[3] * 3;
			float[] c = card(Mth.cos(th) * r, 1 + sd[2] * 3 + size * 0.3F, Mth.sin(th) * r, size, sd[3] * 6, hot * 0.25F * sd[2], surge * 0.85F);
			c[5] = Mth.lerp(0.6F, c[5], 0.42F);
			c[6] = Mth.lerp(0.6F, c[6], 0.36F);
			c[7] = Mth.lerp(0.6F, c[7], 0.3F);
			out.add(c);
		}
		out.sort((a, bb) -> Float.compare(dist(bb, eye), dist(a, eye)));
		return out;
	}

	private static float dist(float[] c, Vector3f eye) {
		float dx = c[0] - eye.x, dy = c[1] - eye.y, dz = c[2] - eye.z;
		return dx * dx + dy * dy + dz * dz;
	}

	/** A card of fire or smoke: painted in a few tones from its heat, as Small Magic paints its fireball. */
	private static float[] card(float x, float y, float z, float size, float spin, float temp, float alpha) {
		float[] c = {0.1F, 0.085F, 0.08F};
		c = mix(c, new float[] {0.5F, 0.06F, 0.02F}, Draw.smooth(0.10F, 0.16F, temp));
		c = mix(c, new float[] {1.0F, 0.24F, 0.04F}, Draw.smooth(0.25F, 0.32F, temp));
		c = mix(c, new float[] {1.0F, 0.58F, 0.12F}, Draw.smooth(0.45F, 0.52F, temp));
		c = mix(c, new float[] {1.0F, 0.93F, 0.72F}, Draw.smooth(0.70F, 0.80F, temp));
		return new float[] {x, y, z, size, spin, c[0], c[1], c[2], Mth.clamp(alpha, 0, 1) * 0.92F};
	}

	private static float[] mix(float[] a, float[] b, float k) {
		return new float[] {Mth.lerp(k, a[0], b[0]), Mth.lerp(k, a[1], b[1]), Mth.lerp(k, a[2], b[2])};
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(NovaEntity entity, State state, float partial) {
		super.extractRenderState(entity, state, partial);
		state.ageInTicks = entity.age(partial);
	}

	public static class State extends EntityRenderState {
	}
}

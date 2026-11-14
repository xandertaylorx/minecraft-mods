package com.massdestruction.client;

import com.massdestruction.entity.AnnihilatorStrikeEntity;
import com.massdestruction.entity.EffectEntity;
import com.massdestruction.entity.NovaEntity;
import com.massdestruction.entity.SingularityEntity;
import com.massdestruction.entity.TornadoEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * What the big effects do to the whole view: the day darkens (violet under a black hole, red while a nova gathers,
 * grey under a storm), a white instant when something goes off, and the camera shakes. Summed over every effect
 * near the camera, each scaled by how close it is.
 */
public final class ScreenFx {
	private ScreenFx() {
	}

	private static float tr, tg, tb, ta;
	private static float flash;
	private static float shake;

	public static float shake() {
		return shake;
	}

	private static void tint(float r, float g, float b, float a) {
		if (a <= 0) return;
		float total = ta + a;
		tr = (tr * ta + r * a) / total;
		tg = (tg * ta + g * a) / total;
		tb = (tb * ta + b * a) / total;
		ta = Math.min(0.85F, total);
	}

	private static float near(Vec3 eye, Entity e, double range) {
		return (float) Mth.clamp(1.0 - eye.distanceTo(e.position()) / range, 0.0, 1.0);
	}

	/** Recomputed every frame from the effects in the level. */
	public static void update(float partial) {
		tr = tg = tb = ta = 0;
		flash = 0;
		shake = 0;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.getCameraEntity() == null) return;
		Vec3 eye = mc.getCameraEntity().getEyePosition(partial);
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof EffectEntity)) continue;
			if (e instanceof AnnihilatorStrikeEntity s) {
				float t = s.sinceFire(partial), n = near(eye, s, 220);
				if (t < 0) {
					float k = 1 + t / AnnihilatorStrikeEntity.CHARGE;
					tint(0.02F, 0.04F, 0.15F, 0.35F * k * n);
				} else if (t < AnnihilatorStrikeEntity.BEAM) {
					flash = Math.max(flash, Math.max(0, 1 - t / 6) * Math.min(1, n * 1.6F));
					shake = Math.max(shake, n * (t < 20 ? 2.2F : 0.9F) * (1 - t / AnnihilatorStrikeEntity.BEAM));
					tint(0.3F, 0.6F, 1.0F, 0.12F * n * (1 - t / AnnihilatorStrikeEntity.BEAM));
				}
			} else if (e instanceof NovaEntity nv) {
				float t = nv.age(partial), n = near(eye, nv, 260);
				if (t < NovaEntity.GATHER) {
					tint(0.35F, 0.03F, 0.0F, 0.45F * (t / NovaEntity.GATHER) * n);
				} else if (t < NovaEntity.BOOM) {
					tint(0.35F, 0.03F, 0.0F, 0.5F * n);
				} else {
					float s = t - NovaEntity.BOOM;
					flash = Math.max(flash, Mth.clamp(1.2F - s / 8, 0, 1) * Math.min(1, n * 2));
					shake = Math.max(shake, n * 3.0F * (float) Math.exp(-s / 30.0));
					tint(1.0F, 0.35F, 0.05F, 0.3F * n * (float) Math.exp(-s / 60.0));
				}
			} else if (e instanceof SingularityEntity h) {
				float n = near(eye, h, 140), burst = h.sinceBurst(partial);
				if (burst < 0) {
					float grip = h.grip(partial), k = h.closing(partial);
					tint(0.12F, 0.0F, 0.25F, (0.35F * Math.min(1, grip) + 0.4F * k) * n);
					if (k > 0) tint(0, 0, 0, 0.5F * k * k * n);
					shake = Math.max(shake, n * (0.25F * grip + 2.0F * k));
				} else {
					flash = Math.max(flash, Mth.clamp(1 - burst / 7, 0, 1) * n);
					shake = Math.max(shake, n * 2.5F * (float) Math.exp(-burst / 12.0));
				}
			} else if (e instanceof TornadoEntity td) {
				float n = near(eye, td, 160);
				tint(0.12F, 0.12F, 0.14F, 0.4F * td.storm(partial) * n);
				float d = (float) eye.distanceTo(td.position());
				if (td.live() && d < 30) shake = Math.max(shake, 0.8F * (1 - d / 30));
				float t = td.age(partial) - TornadoEntity.GATHER;
				if (t >= 0 && t < 4) flash = Math.max(flash, (1 - t / 4) * n);
			}
		}
	}

	public static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
		update(delta.getGameTimeDeltaPartialTick(false));
		int w = g.guiWidth(), h = g.guiHeight();
		if (ta > 0.003F) g.fill(0, 0, w, h, argb(ta, tr, tg, tb));
		if (flash > 0.003F) g.fill(0, 0, w, h, argb(Math.min(1, flash) * 0.92F, 1, 0.97F, 0.92F));
	}

	private static int argb(float a, float r, float g, float b) {
		return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
	}
}

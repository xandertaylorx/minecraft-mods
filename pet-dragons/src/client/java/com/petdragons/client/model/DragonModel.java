package com.petdragons.client.model;

import com.petdragons.client.render.DragonRenderState;
import java.util.function.Function;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Animates the parts built in {@link DragonLayer}: walking, wing beats, a swaying tail, and an open jaw when breathing. */
public class DragonModel extends EntityModel<DragonRenderState> {
	private final ModelPart body, neck, head, jaw;
	private final ModelPart[] tail = new ModelPart[4];
	private final ModelPart wingL, wingR, tipL, tipR, wmemL, wmemR, tmemL, tmemR;
	private final ModelPart legFL, legFR, legBL, legBR, lowFL, lowFR, lowBL, lowBR;
	private final float[] legRest = new float[4];

	public DragonModel(ModelPart root) {
		super(root);
		Function<String, ModelPart> get = root.createPartLookup();
		this.body = get.apply("body");
		this.neck = get.apply("neck");
		this.head = get.apply("head");
		this.jaw = get.apply("jaw");
		for (int i = 0; i < 4; i++) this.tail[i] = get.apply("tail" + i);
		this.wingL = get.apply("wing_l");
		this.wingR = get.apply("wing_r");
		this.tipL = get.apply("tip_l");
		this.tipR = get.apply("tip_r");
		this.wmemL = get.apply("wmem_l");
		this.wmemR = get.apply("wmem_r");
		this.tmemL = get.apply("tmem_l");
		this.tmemR = get.apply("tmem_r");
		this.legFL = get.apply("leg_fl");
		this.legFR = get.apply("leg_fr");
		this.legBL = get.apply("leg_bl");
		this.legBR = get.apply("leg_br");
		this.lowFL = get.apply("low_fl");
		this.lowFR = get.apply("low_fr");
		this.lowBL = get.apply("low_bl");
		this.lowBR = get.apply("low_br");
	}

	@Override
	public void setupAnim(DragonRenderState s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		float yaw = s.yRot * Mth.DEG_TO_RAD, pitch = s.xRot * Mth.DEG_TO_RAD;
		float neckTilt = 0.55F;

		// hatchlings have big heads
		float headScale = Mth.lerp(s.maturity, 1.55F, 1.0F);
		this.head.xScale = this.head.yScale = this.head.zScale = headScale;

		// head follows where it looks; the neck takes a little of it
		this.neck.xRot = neckTilt + (s.flying ? -0.25F : 0.0F) + (s.sitting ? 0.15F : 0.0F);
		this.neck.yRot = yaw * 0.35F;
		this.head.xRot = pitch - this.neck.xRot * 0.8F + Mth.sin(t * 0.05F) * 0.03F;
		this.head.yRot = yaw * 0.65F;
		this.jaw.xRot = s.breathing ? 0.75F + Mth.sin(t * 1.6F) * 0.08F : 0.06F + Mth.sin(t * 0.08F) * 0.03F;

		// tail sways, and streams out behind a flying dragon
		float walk = s.walkAnimationSpeed;
		for (int i = 0; i < 4; i++) {
			this.tail[i].yRot = Mth.sin(t * 0.09F - i * 0.7F) * (0.12F + 0.1F * i) + Mth.cos(s.walkAnimationPos * 0.6662F) * 0.1F * walk;
			this.tail[i].xRot = s.flying ? -0.08F : (i == 0 ? -0.1F : 0.02F * i);
		}

		// legs
		float swing = Mth.cos(s.walkAnimationPos * 0.6662F) * 1.1F * walk;
		float swing2 = Mth.cos(s.walkAnimationPos * 0.6662F + Mth.PI) * 1.1F * walk;
		if (s.flying) {
			this.legFL.xRot = this.legFR.xRot = -0.9F;
			this.legBL.xRot = this.legBR.xRot = 0.6F + Mth.sin(t * 0.4F) * 0.05F;
			this.lowFL.xRot = this.lowFR.xRot = 1.0F;
			this.lowBL.xRot = this.lowBR.xRot = -0.5F;
		} else if (s.sitting) {
			this.legFL.xRot = this.legFR.xRot = -0.2F;
			this.legBL.xRot = this.legBR.xRot = -0.9F;
			this.lowFL.xRot = this.lowFR.xRot = 0.1F;
			this.lowBL.xRot = this.lowBR.xRot = 1.2F;
			this.body.xRot = -0.18F;
		} else {
			this.legFL.xRot = swing;
			this.legFR.xRot = swing2;
			this.legBL.xRot = swing2;
			this.legBR.xRot = swing;
			this.lowFL.xRot = Math.max(0, -swing) * 0.8F;
			this.lowFR.xRot = Math.max(0, -swing2) * 0.8F;
			this.lowBL.xRot = Math.max(0, swing2) * 0.8F;
			this.lowBR.xRot = Math.max(0, swing) * 0.8F;
		}

		// wings
		if (s.flying) {
			float beat = Mth.sin(s.flap) * 0.85F;
			this.wingL.yRot = this.wingR.yRot = 0.0F;
			this.wingL.zRot = -beat - 0.1F;
			this.wingR.zRot = beat + 0.1F;
			this.tipL.zRot = -beat * 0.6F + 0.15F;
			this.tipR.zRot = beat * 0.6F - 0.15F;
			this.tipL.yRot = this.tipR.yRot = 0.0F;
			this.setMembranes(1.0F);
			this.body.xRot = -0.08F;
		} else {
			float breathe = Mth.sin(t * 0.06F) * 0.03F;
			this.wingL.yRot = -1.15F;
			this.wingR.yRot = 1.15F;
			this.wingL.zRot = 0.25F + breathe;
			this.wingR.zRot = -0.25F - breathe;
			this.tipL.yRot = 2.5F;
			this.tipR.yRot = -2.5F;
			this.tipL.zRot = this.tipR.zRot = 0.0F;
			this.setMembranes(0.15F);
			if (!s.sitting) this.body.xRot = 0.0F;
		}
		// a ridden dragon lifts its head
		if (s.ridden && !s.flying) this.head.xRot -= 0.1F;
	}

	private void setMembranes(float widthScale) {
		this.wmemL.xScale = this.wmemR.xScale = widthScale;
		this.tmemL.xScale = this.tmemR.xScale = widthScale;
		this.wmemL.zScale = this.wmemR.zScale = Math.max(widthScale, 0.4F);
		this.tmemL.zScale = this.tmemR.zScale = Math.max(widthScale, 0.4F);
	}
}

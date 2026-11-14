package com.petdragons.client.render;

import com.petdragons.PetDragons;
import com.petdragons.client.PetDragonsClient;
import com.petdragons.client.model.DragonModel;
import com.petdragons.entity.DragonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class DragonRenderer extends MobRenderer<DragonEntity, DragonRenderState, DragonModel> {
	public DragonRenderer(EntityRendererProvider.Context context) {
		super(context, new DragonModel(context.bakeLayer(PetDragonsClient.DRAGON_LAYER)), 0.9F);
	}

	@Override
	public DragonRenderState createRenderState() {
		return new DragonRenderState();
	}

	@Override
	public Identifier getTextureLocation(DragonRenderState state) {
		return PetDragons.id("textures/entity/dragon/" + state.color.key() + ".png");
	}

	@Override
	public void extractRenderState(DragonEntity dragon, DragonRenderState state, float partial) {
		super.extractRenderState(dragon, state, partial);
		state.color = dragon.color();
		state.flying = dragon.isFlying();
		state.breathing = dragon.isBreathing();
		state.sitting = dragon.isInSittingPose();
		state.ridden = dragon.isVehicle();
		state.maturity = dragon.maturity();
		// wing beats: faster while climbing or carrying a rider, slower when gliding along
		state.flap = (dragon.tickCount + partial) * (state.ridden ? 0.55F : 0.4F);
		state.shadowRadius = 0.5F + 0.5F * state.maturity;
	}
}

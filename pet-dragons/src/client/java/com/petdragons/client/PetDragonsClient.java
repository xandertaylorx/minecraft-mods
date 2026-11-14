package com.petdragons.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.petdragons.Net;
import com.petdragons.PetDragons;
import com.petdragons.client.model.DragonLayer;
import com.petdragons.client.render.DragonRenderer;
import com.petdragons.entity.DragonEntity;
import com.petdragons.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.geom.ModelLayerLocation;

public class PetDragonsClient implements ClientModInitializer {
	public static final ModelLayerLocation DRAGON_LAYER = new ModelLayerLocation(PetDragons.id("dragon"), "main");
	public static KeyMapping breathKey;
	private static boolean breathing;

	@Override
	public void onInitializeClient() {
		ModelLayerRegistry.registerModelLayer(DRAGON_LAYER, DragonLayer::create);
		EntityRendererRegistry.register(ModEntities.DRAGON, DragonRenderer::new);
		breathKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.pet_dragons.breathe", InputConstants.Type.KEYBOARD, InputConstants.KEY_R, KeyMapping.Category.GAMEPLAY));
		// hold the breath key while riding to breathe fire
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			boolean want = mc.player != null && mc.player.getVehicle() instanceof DragonEntity && breathKey.isDown();
			if (want != breathing && mc.getConnection() != null) {
				breathing = want;
				ClientPlayNetworking.send(new Net.Breathe(want));
			}
		});
	}
}

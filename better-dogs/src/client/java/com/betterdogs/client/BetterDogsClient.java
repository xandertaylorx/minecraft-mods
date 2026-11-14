package com.betterdogs.client;

import com.betterdogs.Net;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.animal.wolf.Wolf;

public class BetterDogsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(Net.DogState.TYPE, (state, ctx) -> ctx.client().execute(() -> {
			Minecraft mc = ctx.client();
			if (mc.gui.screen() instanceof DogScreen screen && screen.wolf.getId() == state.entityId()) {
				screen.apply(state.settings());
			} else if (state.open() && mc.level != null && mc.level.getEntity(state.entityId()) instanceof Wolf wolf) {
				mc.gui.setScreen(new DogScreen(wolf, state.settings()));
			}
		}));
	}
}

package com.blockos.client;

import com.blockos.client.screen.ComputerScreen;
import com.blockos.net.Net;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public class BlockOSClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(Net.Sync.TYPE, (sync, ctx) -> ctx.client().execute(() -> {
			Minecraft mc = ctx.client();
			if (mc.gui.screen() instanceof ComputerScreen screen && screen.pos.equals(sync.pos())) {
				screen.apply(sync);
			} else if (sync.open()) {
				mc.gui.setScreen(new ComputerScreen(sync));
			}
		}));
	}
}

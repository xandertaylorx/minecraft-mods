package com.massdestruction.client;

import com.massdestruction.DebrisPayload;
import com.massdestruction.MassDestruction;
import com.massdestruction.client.render.AnnihilatorRenderer;
import com.massdestruction.client.render.NovaRenderer;
import com.massdestruction.client.render.SingularityRenderer;
import com.massdestruction.client.render.TornadoRenderer;
import com.massdestruction.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

public class MassDestructionClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.ANNIHILATOR_STRIKE, AnnihilatorRenderer::new);
		EntityRendererRegistry.register(ModEntities.NOVA, NovaRenderer::new);
		EntityRendererRegistry.register(ModEntities.SINGULARITY, SingularityRenderer::new);
		EntityRendererRegistry.register(ModEntities.TORNADO, TornadoRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(DebrisPayload.TYPE, (payload, ctx) -> ctx.client().execute(() -> ClientDebris.receive(payload)));
		HudElementRegistry.addFirst(MassDestruction.id("screen_fx"), ScreenFx::draw);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level != null && mc.level.getGameTime() % 40 == 0) ClientDebris.retain(id -> mc.level.getEntity(id) != null);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> ClientDebris.clear());
	}
}

package com.heroarsenal.client;

import com.heroarsenal.client.render.OrbitalStrikeRenderer;
import com.heroarsenal.client.render.ReturningWeaponRenderer;
import com.heroarsenal.registry.ModEntities;
import com.heroarsenal.registry.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.item.ItemStack;

public class HeroArsenalClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.MJOLNIR,
			ctx -> new ReturningWeaponRenderer<>(ctx, () -> new ItemStack(ModItems.MJOLNIR), ReturningWeaponRenderer.Spin.TUMBLE, 1.25F));
		EntityRendererRegistry.register(ModEntities.STAR_SHIELD,
			ctx -> new ReturningWeaponRenderer<>(ctx, () -> new ItemStack(ModItems.STAR_SHIELD), ReturningWeaponRenderer.Spin.FLAT, 1.0F));
		EntityRendererRegistry.register(ModEntities.WEB_SHOT, ctx -> new ThrownItemRenderer<>(ctx, 0.75F, false));
		EntityRendererRegistry.register(ModEntities.ORBITAL_STRIKE, OrbitalStrikeRenderer::new);
	}
}

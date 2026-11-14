package com.massdestruction;

import com.massdestruction.registry.ModBlocks;
import com.massdestruction.registry.ModEntities;
import com.massdestruction.registry.ModItems;
import com.massdestruction.registry.ModTab;
import com.massdestruction.world.RiftPortals;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class MassDestruction implements ModInitializer {
	public static final String MOD_ID = "mass_destruction";
	/** The third realm. Its generator and look live in data/mass_destruction/dimension*. */
	public static final ResourceKey<Level> RIFT = ResourceKey.create(Registries.DIMENSION, id("the_rift"));

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModBlocks.init();
		ModItems.init();
		ModEntities.init();
		ModTab.init();
		PayloadTypeRegistry.clientboundPlay().register(DebrisPayload.TYPE, DebrisPayload.CODEC);
		RiftPortals.init();
	}
}

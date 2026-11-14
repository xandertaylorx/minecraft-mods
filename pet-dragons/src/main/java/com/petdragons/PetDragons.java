package com.petdragons;

import com.petdragons.registry.ModBlocks;
import com.petdragons.registry.ModEntities;
import com.petdragons.registry.ModItems;
import com.petdragons.registry.ModTab;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

public class PetDragons implements ModInitializer {
	public static final String MOD_ID = "pet_dragons";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModBlocks.init();
		ModItems.init();
		ModEntities.init();
		ModTab.init();
		Net.init();
	}
}

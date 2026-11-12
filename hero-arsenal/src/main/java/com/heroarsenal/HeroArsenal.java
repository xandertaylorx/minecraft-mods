package com.heroarsenal;

import com.heroarsenal.registry.ModEntities;
import com.heroarsenal.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;

public class HeroArsenal implements ModInitializer {
	public static final String MOD_ID = "hero_arsenal";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModEntities.init();
		ModItems.init();

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(ModItems.MJOLNIR);
			output.accept(ModItems.STAR_SHIELD);
			output.accept(ModItems.PULSE_GAUNTLET);
			output.accept(ModItems.WEB_SHOOTER);
		});
	}
}

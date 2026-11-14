package com.petdragons.registry;

import com.petdragons.DragonColor;
import com.petdragons.PetDragons;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModTab {
	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, PetDragons.id("pet_dragons"),
		FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.pet_dragons"))
			.icon(() -> new ItemStack(ModItems.EGGS.get(DragonColor.EMBER)))
			.displayItems((params, out) -> {
				for (DragonColor c : DragonColor.values()) out.accept(ModItems.EGGS.get(c));
			})
			.build());

	private ModTab() {
	}

	public static void init() {
	}
}

package com.massdestruction.registry;

import com.massdestruction.MassDestruction;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Its own creative tab, with the crying obsidian for the Rift portal frame next to the key. */
public final class ModTab {
	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MassDestruction.id("mass_destruction"),
		FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.mass_destruction"))
			.icon(() -> new ItemStack(ModItems.SINGULARITY_CORE))
			.displayItems((params, out) -> {
				out.accept(ModItems.ANNIHILATOR);
				out.accept(ModItems.NOVA_ROD);
				out.accept(ModItems.SINGULARITY_CORE);
				out.accept(ModItems.STORM_CALLER);
				out.accept(ModItems.RIFT_KEY);
				out.accept(Items.CRYING_OBSIDIAN);
			})
			.build());

	private ModTab() {
	}

	public static void init() {
	}
}

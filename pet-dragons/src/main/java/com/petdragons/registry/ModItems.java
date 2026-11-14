package com.petdragons.registry;

import com.petdragons.DragonColor;
import com.petdragons.PetDragons;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class ModItems {
	public static final Map<DragonColor, Item> EGGS = new EnumMap<>(DragonColor.class);

	static {
		for (DragonColor color : DragonColor.values()) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, PetDragons.id(color.key() + "_dragon_egg"));
			Item.Properties props = new Item.Properties().setId(key).useBlockDescriptionPrefix().rarity(Rarity.EPIC).stacksTo(1).fireResistant();
			EGGS.put(color, Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(ModBlocks.EGGS.get(color), props)));
		}
	}

	private ModItems() {
	}

	public static void init() {
	}
}

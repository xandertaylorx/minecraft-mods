package com.massdestruction.registry;

import com.massdestruction.MassDestruction;
import com.massdestruction.item.AnnihilatorItem;
import com.massdestruction.item.NovaRodItem;
import com.massdestruction.item.RiftKeyItem;
import com.massdestruction.item.SingularityCoreItem;
import com.massdestruction.item.StormCallerItem;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class ModItems {
	public static final Item ANNIHILATOR = register("annihilator", AnnihilatorItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
	public static final Item NOVA_ROD = register("nova_rod", NovaRodItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
	public static final Item SINGULARITY_CORE = register("singularity_core", SingularityCoreItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
	public static final Item STORM_CALLER = register("storm_caller", StormCallerItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
	public static final Item RIFT_KEY = register("rift_key", RiftKeyItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant());

	private ModItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MassDestruction.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
	}
}

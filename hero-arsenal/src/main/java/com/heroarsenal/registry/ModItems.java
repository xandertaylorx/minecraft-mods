package com.heroarsenal.registry;

import com.heroarsenal.HeroArsenal;
import com.heroarsenal.item.MjolnirItem;
import com.heroarsenal.item.OrbitalLaserItem;
import com.heroarsenal.item.PulseGauntletItem;
import com.heroarsenal.item.StarShieldItem;
import com.heroarsenal.item.WebShooterItem;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class ModItems {
	public static final Item MJOLNIR = register(
		"mjolnir",
		MjolnirItem::new,
		new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant().attributes(MjolnirItem.createAttributes())
	);

	public static final Item STAR_SHIELD = register(
		"star_shield",
		StarShieldItem::new,
		StarShieldItem.withBlocking(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant())
	);

	public static final Item PULSE_GAUNTLET = register(
		"pulse_gauntlet",
		PulseGauntletItem::new,
		new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()
	);

	public static final Item WEB_SHOOTER = register(
		"web_shooter",
		WebShooterItem::new,
		new Item.Properties().stacksTo(1).rarity(Rarity.RARE)
	);

	public static final Item ORBITAL_LASER = register(
		"orbital_laser",
		OrbitalLaserItem::new,
		new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()
	);

	private ModItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, HeroArsenal.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
		// Loads the class so the static registrations run.
	}
}

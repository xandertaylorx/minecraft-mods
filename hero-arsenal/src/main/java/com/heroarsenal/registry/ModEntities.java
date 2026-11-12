package com.heroarsenal.registry;

import com.heroarsenal.HeroArsenal;
import com.heroarsenal.entity.MjolnirEntity;
import com.heroarsenal.entity.StarShieldEntity;
import com.heroarsenal.entity.WebShotEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final EntityType<MjolnirEntity> MJOLNIR = register(
		"mjolnir",
		EntityType.Builder.<MjolnirEntity>of(MjolnirEntity::new, MobCategory.MISC).noLootTable().sized(0.6F, 0.6F).clientTrackingRange(8).updateInterval(1)
	);

	public static final EntityType<StarShieldEntity> STAR_SHIELD = register(
		"star_shield",
		EntityType.Builder.<StarShieldEntity>of(StarShieldEntity::new, MobCategory.MISC).noLootTable().sized(0.8F, 0.3F).clientTrackingRange(8).updateInterval(1)
	);

	public static final EntityType<WebShotEntity> WEB_SHOT = register(
		"web_shot",
		EntityType.Builder.<WebShotEntity>of(WebShotEntity::new, MobCategory.MISC).noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10)
	);

	private ModEntities() {
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, HeroArsenal.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
		// Loads the class so the static registrations run.
	}
}

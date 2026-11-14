package com.petdragons.registry;

import com.petdragons.PetDragons;
import com.petdragons.entity.DragonEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final EntityType<DragonEntity> DRAGON = register("dragon",
		EntityType.Builder.<DragonEntity>of(DragonEntity::new, MobCategory.CREATURE).sized(1.2F, 1.1F).eyeHeight(1.2F)
			.passengerAttachments(1.15F).fireImmune().clientTrackingRange(10).updateInterval(2));

	private ModEntities() {
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, PetDragons.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(DRAGON, DragonEntity.createAttributes());
	}
}

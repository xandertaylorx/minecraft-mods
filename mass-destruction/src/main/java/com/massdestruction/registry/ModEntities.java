package com.massdestruction.registry;

import com.massdestruction.MassDestruction;
import com.massdestruction.entity.AnnihilatorStrikeEntity;
import com.massdestruction.entity.NovaEntity;
import com.massdestruction.entity.SingularityEntity;
import com.massdestruction.entity.TornadoEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final EntityType<AnnihilatorStrikeEntity> ANNIHILATOR_STRIKE = register("annihilator_strike",
		EntityType.Builder.<AnnihilatorStrikeEntity>of(AnnihilatorStrikeEntity::new, MobCategory.MISC).noLootTable().noSave().fireImmune().sized(0.5F, 0.5F).clientTrackingRange(20).updateInterval(20));
	public static final EntityType<NovaEntity> NOVA = register("nova",
		EntityType.Builder.<NovaEntity>of(NovaEntity::new, MobCategory.MISC).noLootTable().noSave().fireImmune().sized(0.5F, 0.5F).clientTrackingRange(20).updateInterval(20));
	public static final EntityType<SingularityEntity> SINGULARITY = register("singularity",
		EntityType.Builder.<SingularityEntity>of(SingularityEntity::new, MobCategory.MISC).noLootTable().noSave().fireImmune().sized(0.5F, 0.5F).clientTrackingRange(20).updateInterval(10));
	public static final EntityType<TornadoEntity> TORNADO = register("tornado",
		EntityType.Builder.<TornadoEntity>of(TornadoEntity::new, MobCategory.MISC).noLootTable().noSave().fireImmune().sized(0.5F, 0.5F).clientTrackingRange(20).updateInterval(1));

	private ModEntities() {
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MassDestruction.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
	}
}

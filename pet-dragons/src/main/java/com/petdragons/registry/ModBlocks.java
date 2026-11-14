package com.petdragons.registry;

import com.petdragons.DragonColor;
import com.petdragons.PetDragons;
import com.petdragons.block.DragonEggBlock;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
	public static final Map<DragonColor, Block> EGGS = new EnumMap<>(DragonColor.class);

	static {
		for (DragonColor color : DragonColor.values()) {
			String name = color.key() + "_dragon_egg";
			ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, PetDragons.id(name));
			BlockBehaviour.Properties props = BlockBehaviour.Properties.of().setId(key)
				.strength(1.0F, 3.0F).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 4).pushReaction(PushReaction.POPPED);
			EGGS.put(color, Registry.register(BuiltInRegistries.BLOCK, key, new DragonEggBlock(color, props)));
		}
	}

	private ModBlocks() {
	}

	public static void init() {
	}
}

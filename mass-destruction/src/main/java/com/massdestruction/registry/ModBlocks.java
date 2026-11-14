package com.massdestruction.registry;

import com.massdestruction.MassDestruction;
import com.massdestruction.block.RiftPortalBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
	public static final Block RIFT_PORTAL = register("rift_portal", new RiftPortalBlock(props("rift_portal")
		.noCollision().strength(-1.0F, 3600000.0F).sound(SoundType.GLASS).lightLevel(s -> 12).pushReaction(PushReaction.IMMOVEABLE)));

	private ModBlocks() {
	}

	private static BlockBehaviour.Properties props(String name) {
		return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, MassDestruction.id(name)));
	}

	private static Block register(String name, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK, MassDestruction.id(name), block);
	}

	public static void init() {
	}
}

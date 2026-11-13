package com.blockos;

import com.blockos.block.ComputerBlock;
import com.blockos.block.ComputerBlockEntity;
import com.blockos.net.Net;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class BlockOS implements ModInitializer {
	public static final String MOD_ID = "blockos";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static final ResourceKey<Block> COMPUTER_KEY = ResourceKey.create(Registries.BLOCK, id("computer"));
	public static final Block COMPUTER = Registry.register(BuiltInRegistries.BLOCK, COMPUTER_KEY,
		new ComputerBlock(BlockBehaviour.Properties.of()
			.setId(COMPUTER_KEY)
			.mapColor(MapColor.COLOR_GRAY)
			.strength(2.0F, 6.0F)
			.sound(SoundType.METAL)
			.lightLevel(state -> 7)          // the screen glows a little
			.requiresCorrectToolForDrops()));

	public static final ResourceKey<Item> COMPUTER_ITEM_KEY = ResourceKey.create(Registries.ITEM, id("computer"));
	public static final Item COMPUTER_ITEM = Registry.register(BuiltInRegistries.ITEM, COMPUTER_ITEM_KEY,
		new BlockItem(COMPUTER, new Item.Properties().setId(COMPUTER_ITEM_KEY).useBlockDescriptionPrefix().rarity(Rarity.UNCOMMON)));

	public static final BlockEntityType<ComputerBlockEntity> COMPUTER_BE = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, id("computer"), new BlockEntityType<>(ComputerBlockEntity::new, Set.of(COMPUTER)));

	@Override
	public void onInitialize() {
		Net.init();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(out -> out.accept(COMPUTER_ITEM));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(out -> out.accept(COMPUTER_ITEM));
	}
}

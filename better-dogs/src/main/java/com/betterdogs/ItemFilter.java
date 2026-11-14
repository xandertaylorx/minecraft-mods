package com.betterdogs;

import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

/** Decides which dropped items a dog fetches for each Collect setting. */
public final class ItemFilter {
	private ItemFilter() {
	}

	/** Edible, but nobody wants it. */
	private static final Set<Item> BAD_FOOD = Set.of(Items.ROTTEN_FLESH, Items.POISONOUS_POTATO, Items.SPIDER_EYE, Items.PUFFERFISH);

	/** Worth keeping even though it isn't food, a tool or rare. */
	private static final Set<Item> VALUABLES = Set.of(
		Items.DIAMOND, Items.EMERALD, Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP, Items.ANCIENT_DEBRIS,
		Items.GOLD_INGOT, Items.IRON_INGOT, Items.COPPER_INGOT, Items.GOLD_NUGGET, Items.IRON_NUGGET,
		Items.RAW_GOLD, Items.RAW_IRON, Items.RAW_COPPER, Items.COAL, Items.CHARCOAL, Items.LAPIS_LAZULI, Items.REDSTONE,
		Items.QUARTZ, Items.AMETHYST_SHARD, Items.DIAMOND_BLOCK, Items.EMERALD_BLOCK, Items.GOLD_BLOCK, Items.IRON_BLOCK,
		Items.ENDER_PEARL, Items.ENDER_EYE, Items.BLAZE_ROD, Items.BREEZE_ROD, Items.GHAST_TEAR, Items.MAGMA_CREAM,
		Items.BONE, Items.STRING, Items.GUNPOWDER, Items.LEATHER, Items.FEATHER, Items.SLIME_BALL, Items.EGG,
		Items.RABBIT_HIDE, Items.INK_SAC, Items.GLOW_INK_SAC, Items.PHANTOM_MEMBRANE, Items.ARROW, Items.SPECTRAL_ARROW,
		Items.SHULKER_SHELL, Items.NAUTILUS_SHELL, Items.PRISMARINE_SHARD, Items.PRISMARINE_CRYSTALS, Items.ARMADILLO_SCUTE,
		Items.TURTLE_SCUTE, Items.HONEYCOMB, Items.EXPERIENCE_BOTTLE, Items.NAME_TAG, Items.SADDLE, Items.LEAD,
		Items.GLOWSTONE_DUST, Items.BOOK, Items.PAPER, Items.SUGAR_CANE, Items.WHEAT, Items.COCOA_BEANS);

	public static boolean accepts(DogSettings.Collect mode, ItemStack stack) {
		if (stack.isEmpty()) return false;
		return switch (mode) {
			case OFF -> false;
			case ALL -> true;
			case FOOD -> isFood(stack);
			case GOOD -> isFood(stack) || isGood(stack);
		};
	}

	public static boolean isFood(ItemStack stack) {
		return stack.has(DataComponents.FOOD) && !BAD_FOOD.contains(stack.getItem());
	}

	private static boolean isGood(ItemStack stack) {
		if (VALUABLES.contains(stack.getItem())) return true;
		if (stack.has(DataComponents.MAX_DAMAGE)) return true;              // tools, weapons, armor
		if (stack.isEnchanted() || stack.has(DataComponents.STORED_ENCHANTMENTS)) return true;
		if (stack.getRarity() != Rarity.COMMON) return true;                // discs, totems, heads, nether stars...
		return stack.is(ItemTags.COAL_ORES) || stack.is(ItemTags.IRON_ORES) || stack.is(ItemTags.GOLD_ORES)
			|| stack.is(ItemTags.DIAMOND_ORES) || stack.is(ItemTags.EMERALD_ORES) || stack.is(ItemTags.LAPIS_ORES)
			|| stack.is(ItemTags.REDSTONE_ORES) || stack.is(ItemTags.COPPER_ORES);
	}
}

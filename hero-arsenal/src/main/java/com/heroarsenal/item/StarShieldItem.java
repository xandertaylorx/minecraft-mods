package com.heroarsenal.item;

import com.heroarsenal.entity.StarShieldEntity;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.Level;

/**
 * Star Shield.
 * - Hold right-click to block, like a normal shield (but it never breaks).
 * - Sneak + right-click to throw it: it ricochets between enemies and off walls, then comes back.
 */
public class StarShieldItem extends Item {
	public StarShieldItem(Item.Properties properties) {
		super(properties);
	}

	/** Same blocking as a vanilla shield: blocks 100% of frontal damage, raises instantly. */
	public static Item.Properties withBlocking(Item.Properties properties) {
		return properties.delayedComponent(
			DataComponents.BLOCKS_ATTACKS,
			context -> new BlocksAttacks(
				0.0F,
				0.5F,
				List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 1.0F)),
				new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
				Optional.of(context.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
				Optional.of(SoundEvents.SHIELD_BLOCK),
				Optional.of(SoundEvents.SHIELD_BREAK)
			)
		);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!player.isShiftKeyDown()) {
			return super.use(level, player, hand); // block
		}

		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel serverLevel) {
			StarShieldEntity shield = new StarShieldEntity(serverLevel, player, stack.copyWithCount(1));
			shield.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 2.4F, 0.1F);
			shield.pickup = AbstractArrow.Pickup.ALLOWED;
			shield.setReturnSlot(hand == InteractionHand.OFF_HAND ? Inventory.SLOT_OFFHAND : player.getInventory().getSelectedSlot());
			serverLevel.addFreshEntity(shield);
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.4F);
			player.awardStat(Stats.ITEM_USED.get(this));
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}

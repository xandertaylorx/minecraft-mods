package com.heroarsenal.item;

import com.heroarsenal.Thunder;
import com.heroarsenal.entity.MjolnirEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * Mjolnir.
 * - Hold right-click to wind up (raises it over your head, sparks build up), release to throw. It comes back.
 * - Sneak + right-click to call lightning wherever you're looking.
 * - Melee hits sometimes call lightning; critical hits (attacking while falling) always do.
 */
public class MjolnirItem extends Item {
	private static final int MIN_CHARGE_TICKS = 6;
	private static final int FULL_CHARGE_TICKS = 20;
	private static final int CALL_LIGHTNING_RANGE = 64;
	private static final int CALL_LIGHTNING_COOLDOWN = 30;

	public MjolnirItem(Item.Properties properties) {
		super(properties);
	}

	public static ItemAttributeModifiers createAttributes() {
		return ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 11.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -3.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.build();
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (player.isShiftKeyDown()) {
			HitResult hit = player.pick(CALL_LIGHTNING_RANGE, 1.0F, false);
			if (hit.getType() == HitResult.Type.MISS) {
				return InteractionResult.FAIL;
			}
			if (level instanceof ServerLevel serverLevel) {
				Thunder.strike(serverLevel, hit.getLocation(), player);
				player.getCooldowns().addCooldown(stack, CALL_LIGHTNING_COOLDOWN);
				player.awardStat(Stats.ITEM_USED.get(this));
			}
			return InteractionResult.SUCCESS;
		}

		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		// Same wind-up pose as the trident: hammer raised behind your head.
		return ItemUseAnimation.TRIDENT;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int ticksRemaining) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		int held = this.getUseDuration(stack, user) - ticksRemaining;

		// Sparks crackle around you and get stronger while charging.
		if (held % 2 == 0) {
			int count = Math.min(2 + held / 3, 10);
			serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, user.getX(), user.getY() + user.getBbHeight() + 0.3, user.getZ(), count, 0.5, 0.4, 0.5, 0.05);
		}
		if (held == FULL_CHARGE_TICKS) {
			level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.35F, 1.8F);
			serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
				user.getX(), user.getY() + user.getBbHeight() + 0.3, user.getZ(), 25, 0.6, 0.6, 0.6, 0.3);
		}
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int ticksRemaining) {
		if (!(user instanceof Player player)) {
			return false;
		}
		int held = this.getUseDuration(stack, user) - ticksRemaining;
		if (held < MIN_CHARGE_TICKS) {
			return false;
		}

		if (level instanceof ServerLevel serverLevel) {
			boolean fullyCharged = held >= FULL_CHARGE_TICKS;
			MjolnirEntity hammer = new MjolnirEntity(serverLevel, player, stack.copyWithCount(1));
			hammer.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, fullyCharged ? 3.2F : 2.2F, 0.2F);
			hammer.pickup = AbstractArrow.Pickup.ALLOWED;
			if (player.getMainHandItem() == stack) {
				hammer.setReturnSlot(player.getInventory().getSelectedSlot());
			}
			serverLevel.addFreshEntity(hammer);
			serverLevel.playSound(null, hammer, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
			player.awardStat(Stats.ITEM_USED.get(this));
			// Leaves your hand even in creative, so the catch actually means something.
			stack.shrink(1);
		}
		return true;
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (attacker.level() instanceof ServerLevel level) {
			boolean critical = attacker.fallDistance > 0.0 && !attacker.onGround();
			if (critical || attacker.getRandom().nextFloat() < 0.2F) {
				Thunder.strike(level, target.position(), attacker);
			}
		}
	}
}

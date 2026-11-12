package com.heroarsenal.item;

import com.heroarsenal.Motion;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Pulse Gauntlet.
 * - Right-click: fire an energy blast in a straight line that hits the first mob for big knockback.
 * - Sneak + right-click: thrusters launch you where you're looking.
 * - While it's in your hand or offhand you take no fall damage (the thrusters soften the landing).
 */
public class PulseGauntletItem extends Item {
	private static final double RANGE = 40.0;
	private static final float BLAST_DAMAGE = 8.0F;
	private static final int BLAST_COOLDOWN = 12;
	private static final int THRUST_COOLDOWN = 16;

	public PulseGauntletItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}

		if (player.isShiftKeyDown()) {
			this.thrust(serverLevel, player);
			player.getCooldowns().addCooldown(stack, THRUST_COOLDOWN);
		} else {
			this.blast(serverLevel, player);
			player.getCooldowns().addCooldown(stack, BLAST_COOLDOWN);
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		return InteractionResult.SUCCESS;
	}

	private void blast(ServerLevel level, Player player) {
		Vec3 start = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = start.add(look.scale(RANGE));

		BlockHitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getLocation();
		}

		AABB searchBox = player.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0);
		@Nullable EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
			player, start, end, searchBox, e -> e.isPickable() && !e.isSpectator() && e != player, start.distanceToSqr(end)
		);
		if (entityHit != null) {
			end = entityHit.getLocation();
			Entity target = entityHit.getEntity();
			DamageSource source = player.damageSources().indirectMagic(player, player);
			target.hurtServer(level, source, BLAST_DAMAGE);
			if (target instanceof LivingEntity) {
				Motion.setVelocity(target, target.getDeltaMovement().add(look.scale(1.8)).add(0.0, 0.4, 0.0));
			}
			target.igniteForSeconds(2.0F);
		}

		// Beam: a line of glowing particles from your hand to the impact point.
		Vec3 hand = start.add(look.scale(0.6)).subtract(0.0, 0.25, 0.0);
		double length = hand.distanceTo(end);
		Vec3 step = end.subtract(hand).normalize().scale(0.4);
		Vec3 point = hand;
		for (double d = 0; d < length; d += 0.4) {
			level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
			point = point.add(step);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.FIREWORK, end.x, end.y, end.z, 20, 0.2, 0.2, 0.2, 0.15);

		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.6F);
		level.playSound(null, end.x, end.y, end.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.8F);
	}

	private void thrust(ServerLevel level, Player player) {
		Vec3 look = player.getLookAngle();
		Vec3 velocity = player.getDeltaMovement().scale(0.3).add(look.scale(1.6)).add(0.0, 0.35, 0.0);
		Motion.setVelocity(player, velocity);

		level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY(), player.getZ(), 25, 0.2, 0.1, 0.2, 0.05);
		level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY(), player.getZ(), 15, 0.2, 0.1, 0.2, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
			owner.resetFallDistance();
		}
	}
}

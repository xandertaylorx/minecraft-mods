package com.heroarsenal.entity;

import com.heroarsenal.Thunder;
import com.heroarsenal.registry.ModEntities;
import com.heroarsenal.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The thrown hammer: smashes the first thing it hits with lightning, then flies home. */
public class MjolnirEntity extends ReturningWeaponEntity {
	private static final float THROW_DAMAGE = 14.0F;

	public MjolnirEntity(EntityType<? extends MjolnirEntity> type, Level level) {
		super(type, level);
	}

	public MjolnirEntity(Level level, LivingEntity owner, ItemStack stack) {
		super(ModEntities.MJOLNIR, level, owner, stack);
	}

	@Override
	protected int maxOutboundTicks() {
		return 24;
	}

	@Override
	protected void onStrikeEntity(Entity target) {
		Entity owner = this.getOwner();
		DamageSource source = this.damageSources().thrown(this, owner == null ? this : owner);

		float damage = THROW_DAMAGE;
		if (this.level() instanceof ServerLevel level) {
			damage = EnchantmentHelper.modifyDamage(level, this.getWeaponItem(), target, source, damage);
		}

		if (target.hurtOrSimulate(source, damage) && target instanceof LivingEntity living) {
			this.doKnockback(living, source);
			this.doPostHurtEffects(living);
		}

		if (this.level() instanceof ServerLevel level) {
			Thunder.strike(level, target.position(), owner);
		}

		this.playSound(SoundEvents.TRIDENT_HIT, 1.0F, 0.8F);
		this.setDeltaMovement(this.getDeltaMovement().scale(-0.3));
		this.startReturning();
	}

	@Override
	protected void onStrikeBlock(BlockHitResult hitResult) {
		Vec3 hit = hitResult.getLocation();
		if (this.level() instanceof ServerLevel level) {
			Thunder.strike(level, hit, this.getOwner());
			level.sendParticles(ParticleTypes.EXPLOSION, hit.x, hit.y, hit.z, 1, 0.0, 0.0, 0.0, 0.0);
		}

		this.playSound(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0F, 0.9F);
		Vec3 motion = this.getDeltaMovement();
		this.setPos(hit.subtract(motion.normalize().scale(0.3)));
		this.setDeltaMovement(motion.scale(-0.3));
		this.startReturning();
	}

	@Override
	protected void spawnTrailParticles() {
		Vec3 pos = this.position();
		Vec3 motion = this.getDeltaMovement();
		for (int i = 0; i < 3; i++) {
			double t = i / 3.0;
			this.level().addParticle(
				ParticleTypes.ELECTRIC_SPARK,
				pos.x - motion.x * t + (this.random.nextDouble() - 0.5) * 0.5,
				pos.y + 0.2 - motion.y * t + (this.random.nextDouble() - 0.5) * 0.5,
				pos.z - motion.z * t + (this.random.nextDouble() - 0.5) * 0.5,
				0.0, 0.0, 0.0
			);
		}
		if (this.random.nextInt(3) == 0) {
			this.level().addParticle(ParticleTypes.CLOUD, pos.x, pos.y + 0.2, pos.z, 0.0, 0.0, 0.0);
		}
	}

	@Override
	protected ItemStack getDefaultPickupItem() {
		return new ItemStack(ModItems.MJOLNIR);
	}
}

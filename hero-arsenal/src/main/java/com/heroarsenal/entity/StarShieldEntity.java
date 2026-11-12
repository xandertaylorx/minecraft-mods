package com.heroarsenal.entity;

import com.heroarsenal.registry.ModEntities;
import com.heroarsenal.registry.ModItems;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The thrown shield: ricochets from enemy to enemy (and off walls), then flies home.
 */
public class StarShieldEntity extends ReturningWeaponEntity {
	private static final float HIT_DAMAGE = 9.0F;
	private static final int MAX_BOUNCES = 4;
	private static final double BOUNCE_SEARCH_RANGE = 12.0;
	private static final double SPEED = 2.4;

	private int bounces;
	private final IntSet alreadyHit = new IntOpenHashSet();

	public StarShieldEntity(EntityType<? extends StarShieldEntity> type, Level level) {
		super(type, level);
	}

	public StarShieldEntity(Level level, LivingEntity owner, ItemStack stack) {
		super(ModEntities.STAR_SHIELD, level, owner, stack);
	}

	@Override
	protected int maxOutboundTicks() {
		return 20;
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return super.canHitEntity(entity) && !this.alreadyHit.contains(entity.getId());
	}

	@Override
	protected void onStrikeEntity(Entity target) {
		this.alreadyHit.add(target.getId());
		Entity owner = this.getOwner();
		DamageSource source = this.damageSources().thrown(this, owner == null ? this : owner);
		if (target.hurtOrSimulate(source, HIT_DAMAGE) && target instanceof LivingEntity living) {
			this.doKnockback(living, source);
		}
		this.playSound(SoundEvents.SHIELD_BLOCK.value(), 1.2F, 1.3F);
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 12, 0.3, 0.3, 0.3, 0.3);
		}

		this.bounces++;
		Optional<Entity> next = this.bounces < MAX_BOUNCES ? this.findNextTarget() : Optional.empty();
		if (next.isPresent()) {
			Entity nextTarget = next.get();
			this.redirect(nextTarget.getBoundingBox().getCenter().subtract(this.position()), SPEED);
		} else {
			this.setDeltaMovement(this.getDeltaMovement().scale(-0.3));
			this.startReturning();
		}
	}

	private Optional<Entity> findNextTarget() {
		Entity owner = this.getOwner();
		return this.level()
			.getEntities(this, this.getBoundingBox().inflate(BOUNCE_SEARCH_RANGE), e -> e instanceof LivingEntity
				&& e instanceof Enemy
				&& e.isAlive()
				&& e != owner
				&& !this.alreadyHit.contains(e.getId())
				&& this.hasLineOfSightTo(e))
			.stream()
			.min(Comparator.comparingDouble(this::distanceToSqr));
	}

	private boolean hasLineOfSightTo(Entity e) {
		return e instanceof LivingEntity living && living.hasLineOfSight(this);
	}

	@Override
	protected void onStrikeBlock(BlockHitResult hit) {
		this.playSound(SoundEvents.ANVIL_LAND, 0.4F, 1.8F);
		this.bounces++;
		if (this.bounces >= MAX_BOUNCES) {
			this.startReturning();
			return;
		}

		// Ricochet: reflect off the face it hit.
		Vec3 motion = this.getDeltaMovement();
		Direction face = hit.getDirection();
		Vec3 reflected = switch (face.getAxis()) {
			case X -> new Vec3(-motion.x, motion.y, motion.z);
			case Y -> new Vec3(motion.x, -motion.y, motion.z);
			case Z -> new Vec3(motion.x, motion.y, -motion.z);
		};
		this.setPos(hit.getLocation().add(face.getStepX() * 0.3, face.getStepY() * 0.3, face.getStepZ() * 0.3));

		Optional<Entity> next = this.findNextTarget();
		if (next.isPresent()) {
			this.redirect(next.get().getBoundingBox().getCenter().subtract(this.position()), SPEED);
		} else {
			this.redirect(reflected, SPEED);
		}
	}

	@Override
	protected void spawnTrailParticles() {
		if (this.random.nextInt(2) == 0) {
			Vec3 pos = this.position();
			this.level().addParticle(ParticleTypes.CRIT, pos.x, pos.y + 0.1, pos.z, 0.0, 0.0, 0.0);
		}
	}

	@Override
	protected ItemStack getDefaultPickupItem() {
		return new ItemStack(ModItems.STAR_SHIELD);
	}
}

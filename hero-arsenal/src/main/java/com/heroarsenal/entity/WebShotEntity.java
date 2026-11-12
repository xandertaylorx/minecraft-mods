package com.heroarsenal.entity;

import com.heroarsenal.Motion;
import com.heroarsenal.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A ball of web. Sticks mobs in place, or yanks the shooter toward the block it hits. */
public class WebShotEntity extends ThrowableItemProjectile {
	private static final int MAX_LIFE_TICKS = 40;

	public WebShotEntity(EntityType<? extends WebShotEntity> type, Level level) {
		super(type, level);
	}

	public WebShotEntity(Level level, LivingEntity owner) {
		super(ModEntities.WEB_SHOT, owner, level, new ItemStack(Items.COBWEB));
	}

	@Override
	protected Item getDefaultItem() {
		return Items.COBWEB;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.01;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			Vec3 pos = this.position();
			this.level().addParticle(ParticleTypes.ITEM_COBWEB, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
			// A thin strand back toward the shooter.
			Entity owner = this.getOwner();
			if (owner != null) {
				Vec3 hand = owner.getEyePosition().subtract(0.0, 0.3, 0.0);
				for (int i = 1; i < 6; i++) {
					Vec3 p = hand.lerp(pos, this.random.nextDouble());
					this.level().addParticle(ParticleTypes.WHITE_ASH, p.x, p.y, p.z, 0.0, 0.0, 0.0);
				}
			}
		} else if (this.tickCount > MAX_LIFE_TICKS) {
			this.discard();
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hitResult) {
		super.onHitEntity(hitResult);
		if (hitResult.getEntity() instanceof LivingEntity target) {
			target.hurtServer((ServerLevel) this.level(), this.damageSources().thrown(this, this.getOwner()), 1.0F);
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 5), this.getOwner());
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1), this.getOwner());
			((ServerLevel) this.level()).sendParticles(ParticleTypes.ITEM_COBWEB, target.getX(), target.getY(0.5), target.getZ(), 30, 0.4, 0.5, 0.4, 0.05);
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult hitResult) {
		super.onHitBlock(hitResult);
		if (this.getOwner() instanceof Player player && !player.isShiftKeyDown()) {
			// Swing: pull toward where the web stuck, with a little lift so you arc instead of faceplanting.
			Vec3 toAnchor = hitResult.getLocation().subtract(player.position());
			double pull = Math.min(2.2, 0.6 + toAnchor.length() * 0.08);
			Vec3 velocity = toAnchor.normalize().scale(pull).add(0.0, 0.45, 0.0);
			Motion.setVelocity(player, velocity);
		}
		Vec3 hit = hitResult.getLocation();
		((ServerLevel) this.level()).sendParticles(ParticleTypes.ITEM_COBWEB, hit.x, hit.y, hit.z, 15, 0.2, 0.2, 0.2, 0.05);
	}

	@Override
	protected void onHit(HitResult hitResult) {
		if (this.level().isClientSide()) {
			return;
		}
		super.onHit(hitResult);
		this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.COBWEB_PLACE, SoundSource.PLAYERS, 1.0F, 1.2F);
		this.discard();
	}
}

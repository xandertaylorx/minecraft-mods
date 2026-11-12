package com.heroarsenal.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A thrown weapon that flies straight (no gravity), hits things, then flies back through anything
 * in its way into the thrower's hand. Subclasses decide what happens on a hit.
 */
public abstract class ReturningWeaponEntity extends AbstractArrow {
	private static final EntityDataAccessor<Boolean> RETURNING = SynchedEntityData.defineId(ReturningWeaponEntity.class, EntityDataSerializers.BOOLEAN);

	/** Ticks spent on the current outbound leg. Reset on a bounce. */
	protected int outboundTicks;
	private int returnTicks;
	/** Hotbar/offhand slot it was thrown from, so it lands back where it was. */
	private int returnSlot = -1;

	protected ReturningWeaponEntity(EntityType<? extends ReturningWeaponEntity> type, Level level) {
		super(type, level);
	}

	protected ReturningWeaponEntity(EntityType<? extends ReturningWeaponEntity> type, Level level, LivingEntity owner, ItemStack stack) {
		super(type, owner, level, stack, null);
	}

	/** How long it flies out before turning around on its own (20 ticks = 1 second). */
	protected abstract int maxOutboundTicks();

	/** Server + client: called when it hits an entity on the way out. */
	protected abstract void onStrikeEntity(Entity target);

	/** Server + client: called when it hits a block on the way out. */
	protected abstract void onStrikeBlock(BlockHitResult hit);

	/** Client only: trail effect, called every tick. */
	protected abstract void spawnTrailParticles();

	public void setReturnSlot(int slot) {
		this.returnSlot = slot;
	}

	public boolean isReturning() {
		return this.entityData.get(RETURNING);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(RETURNING, false);
	}

	@Override
	public void tick() {
		this.outboundTicks++;

		if (!this.level().isClientSide() && !this.isReturning() && (this.outboundTicks > this.maxOutboundTicks() || this.isInGround())) {
			this.startReturning();
		}

		if (this.isReturning()) {
			Entity owner = this.getOwner();
			if (owner == null || !owner.isAlive() || owner.isSpectator() || owner.level() != this.level()) {
				// Nobody to return to: drop it where it is.
				if (this.level() instanceof ServerLevel level) {
					this.spawnAtLocation(level, this.getPickupItem(), 0.1F);
					this.discard();
					return;
				}
			} else {
				this.returnTicks++;
				this.setNoPhysics(true);
				Vec3 target = owner.getEyePosition().subtract(0.0, 0.5, 0.0);
				Vec3 toOwner = target.subtract(this.position());
				double distance = toOwner.length();

				if (!this.level().isClientSide() && distance < 1.8) {
					this.catchBy(owner);
					return;
				}

				// Speeds up the longer it's been flying home, and steers hard toward you.
				double speed = Math.min(3.0, 1.0 + this.returnTicks * 0.12);
				Vec3 desired = toOwner.normalize().scale(Math.min(speed, distance));
				this.setDeltaMovement(this.getDeltaMovement().lerp(desired, 0.4));
			}
		}

		if (this.level().isClientSide()) {
			this.spawnTrailParticles();
		}

		super.tick();
	}

	protected void startReturning() {
		if (this.isReturning()) {
			return;
		}
		this.entityData.set(RETURNING, true);
		this.setNoPhysics(true);
		this.setInGround(false);
		this.playSound(SoundEvents.TRIDENT_RETURN, 2.0F, 0.8F);
	}

	/** Point it somewhere new for another outbound leg (used for bounces). */
	protected void redirect(Vec3 direction, double speed) {
		this.setDeltaMovement(direction.normalize().scale(speed));
		this.outboundTicks = 0;
	}

	private void catchBy(Entity owner) {
		if (owner instanceof Player player) {
			ItemStack stack = this.getPickupItem();
			Inventory inventory = player.getInventory();
			boolean caught;
			if (this.returnSlot >= 0 && inventory.getItem(this.returnSlot).isEmpty()) {
				inventory.setItem(this.returnSlot, stack);
				caught = true;
			} else {
				caught = inventory.add(stack);
			}

			if (caught) {
				player.take(this, 1);
				this.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 0.8F, 1.4F);
			} else if (this.level() instanceof ServerLevel level) {
				this.spawnAtLocation(level, stack, 0.1F);
			}
		}
		this.discard();
	}

	@Override
	protected @Nullable EntityHitResult findHitEntity(Vec3 from, Vec3 to) {
		return this.isReturning() ? null : super.findHitEntity(from, to);
	}

	@Override
	protected final void onHitEntity(EntityHitResult hitResult) {
		this.onStrikeEntity(hitResult.getEntity());
	}

	@Override
	protected final void onHitBlock(BlockHitResult hitResult) {
		// Deliberately not calling super: these weapons never stick into the ground.
		this.onStrikeBlock(hitResult);
	}

	@Override
	public void playerTouch(Player player) {
		// Only the thrower can catch it, and only on the way back.
		if (!this.level().isClientSide() && this.isReturning() && this.ownedBy(player)) {
			this.catchBy(player);
		}
	}

	@Override
	protected double getDefaultGravity() {
		return 0.0;
	}

	@Override
	protected float getAirDrag() {
		return 1.0F;
	}

	@Override
	protected float getWaterInertia() {
		return 0.95F;
	}

	@Override
	protected void tickDespawn() {
		// Never despawns — it always finds its way home.
	}

	@Override
	public ItemStack getWeaponItem() {
		return this.getPickupItemStackOrigin();
	}

	@Override
	protected SoundEvent getDefaultHitGroundSoundEvent() {
		return SoundEvents.MACE_SMASH_GROUND;
	}

	@Override
	public boolean shouldRender(double camX, double camY, double camZ) {
		return true;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Returning", this.isReturning());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(RETURNING, input.getBooleanOr("Returning", false));
	}
}

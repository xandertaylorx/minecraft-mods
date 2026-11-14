package com.massdestruction.entity;

import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Base for the big effects (laser strike, nova, black hole, tornado). Each runs a timeline keyed to the world's game
 * time, synced once when it is spawned, so every client sees the same moment even if it walks into range halfway
 * through. They are never saved: they are short, and half a black hole after a reload would be worse than none.
 */
public abstract class EffectEntity extends Entity {
	private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(EffectEntity.class, EntityDataSerializers.LONG);

	protected @Nullable Entity owner;
	protected @Nullable UUID ownerId;
	protected boolean breaksBlocks = true;

	protected EffectEntity(EntityType<? extends EffectEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	protected void begin(@Nullable Entity owner, boolean breaksBlocks) {
		this.owner = owner;
		this.ownerId = owner == null ? null : owner.getUUID();
		this.breaksBlocks = breaksBlocks;
		this.entityData.set(START, this.level().getGameTime());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(START, Long.MIN_VALUE);
	}

	/** Ticks since it began (with the partial tick, for smooth drawing). Works the same on server and client. */
	public float age(float partialTick) {
		long start = this.entityData.get(START);
		if (start == Long.MIN_VALUE) return this.tickCount + partialTick;   // summoned with a command
		return (float) (this.level().getGameTime() - start) + partialTick;
	}

	public @Nullable Entity owner() {
		return this.owner;
	}

	public boolean isOwnedBy(Entity e) {
		return this.ownerId != null && this.ownerId.equals(e.getUUID());
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		double size = 320.0 * getViewScale();
		return distance < size * size;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}

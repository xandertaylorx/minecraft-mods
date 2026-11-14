package com.massdestruction.entity;

import com.massdestruction.DebrisPayload;
import com.massdestruction.Wreck;
import com.massdestruction.registry.ModEntities;
import com.massdestruction.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A tornado, after Small Magic's. A circle of runes is written on the ground, a lid of dark cloud winds itself up
 * overhead with rings of runes under it, then lightning comes down through the rings and the funnel is driven down
 * after it. From then on it goes where its owner points the Storm Caller (or wanders, if they put it away), and
 * everything near its foot goes round it and up: mobs and items are carried up and thrown out, leaves and plants are
 * stripped, and the ground is torn up a block at a time and carried round. Over lava or fire it becomes a pillar of
 * flame that sets alight what it crosses; over water it is a spout. After 30 seconds, or when dismissed, it draws
 * back up into the cloud.
 */
public class TornadoEntity extends EffectEntity {
	public static final int GATHER = 50;
	public static final int SLAM = 8;
	public static final int LIFE = 640;
	public static final int LIFT = 30;
	/** Funnel height and the radius of its foot and its top. */
	public static final float HEIGHT = 46.0F;
	public static final float R0 = 2.6F;
	public static final float R1 = 13.0F;
	public static final double REACH = 17.0;
	public static final int KIND_WIND = 0, KIND_FIRE = 1, KIND_WATER = 2;

	private static final EntityDataAccessor<Integer> LIFT_AT = SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.INT);

	private final DebrisPayload.Batch batch = new DebrisPayload.Batch();
	private @Nullable Vec3 wander;
	private Vec3 velocity = Vec3.ZERO;
	private int fireTicks;

	public TornadoEntity(EntityType<? extends TornadoEntity> type, Level level) {
		super(type, level);
	}

	public TornadoEntity(Level level, Vec3 at, @Nullable Entity owner, boolean breaksBlocks) {
		this(ModEntities.TORNADO, level);
		this.snapTo(at.x, at.y, at.z);
		this.begin(owner, breaksBlocks);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(LIFT_AT, -1);
		builder.define(KIND, KIND_WIND);
	}

	public int liftAt() {
		return this.entityData.get(LIFT_AT);
	}

	public int kind() {
		return this.entityData.get(KIND);
	}

	public void dismiss() {
		if (this.liftAt() < 0) this.entityData.set(LIFT_AT, Math.max(GATHER + SLAM, (int) this.age(0.0F)));
	}

	/** How far down the funnel reaches, 0 (up in the cloud) to 1 (touching the ground). */
	public float down(float partial) {
		float t = this.age(partial);
		if (t < GATHER) return 0.0F;
		float d = Math.min(1.0F, (t - GATHER) / SLAM);
		int lift = this.liftAt();
		if (lift >= 0) d *= 1.0F - Mth.clamp((t - lift) / LIFT, 0.0F, 1.0F);
		return d;
	}

	/** How much of the storm (cloud, dark) there is, 0..1. */
	public float storm(float partial) {
		float t = this.age(partial);
		float s = Math.min(1.0F, t / (GATHER * 0.8F));
		int lift = this.liftAt();
		if (lift >= 0) s *= 1.0F - Mth.clamp((t - lift - LIFT * 0.5F) / LIFT, 0.0F, 1.0F);
		return s;
	}

	public boolean live() {
		return this.down(0.0F) > 0.99F;
	}

	@Override
	public void tick() {
		super.tick();
		float t = this.age(0.0F);
		if (this.level().isClientSide()) {
			this.clientTick(t);
			return;
		}
		ServerLevel level = (ServerLevel) this.level();
		int ti = (int) t;
		if (this.liftAt() < 0 && ti >= LIFE) this.dismiss();
		if (this.liftAt() >= 0 && t > this.liftAt() + LIFT * 1.6F) {
			this.discard();
			return;
		}
		if (ti == 0) {
			Wreck.sound(level, this.position(), SoundEvents.ELYTRA_FLYING, 4.0F, 0.4F);
			Wreck.sound(level, this.position().add(0, 30, 0), SoundEvents.LIGHTNING_BOLT_THUNDER, 6.0F, 0.4F);
		}
		if (ti == GATHER) this.strike(level);
		if (ti >= GATHER) {
			if (ti % 38 == 0) Wreck.sound(level, this.position(), SoundEvents.ELYTRA_FLYING, 8.0F, 0.35F + this.random.nextFloat() * 0.1F);
			if (ti % 23 == 0) Wreck.sound(level, this.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 6.0F, 0.4F);
			if (ti % 90 == 40) Wreck.sound(level, this.position().add(0, 40, 0), SoundEvents.LIGHTNING_BOLT_THUNDER, 8.0F, 0.5F + this.random.nextFloat() * 0.3F);
		}
		float down = this.down(0.0F);
		if (down > 0.5F) {
			this.steer(level);
			this.swirl(level, down);
			if (this.breaksBlocks) this.tear(level, down);
			if (ti % 10 == 0) this.sense(level);
			this.batch.flush(this);
		}
	}

	/** Lightning comes down through the rings into the circle, and the funnel follows it. */
	private void strike(ServerLevel level) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(this.getX(), this.getY(), this.getZ());
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		Wreck.sound(level, this.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 8.0F, 0.6F);
		Wreck.sound(level, this.position(), SoundEvents.WIND_CHARGE_BURST.value(), 8.0F, 0.4F);
		for (Entity e : Wreck.victims(level, this, this.position(), 10, 6, this.owner, true)) {
			Vec3 off = e.position().subtract(this.position());
			Vec3 flat = new Vec3(off.x, 0, off.z);
			Wreck.push(e, (flat.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : flat.normalize()).scale(1.2).add(0, 0.6, 0));
		}
	}

	/** Goes where its owner points the Storm Caller; otherwise wanders. Always hugs the ground. */
	private void steer(ServerLevel level) {
		Vec3 want = null;
		if (this.owner instanceof Player p && p.isAlive() && p.level() == level && p.distanceToSqr(this) < 140 * 140
			&& (p.getMainHandItem().is(ModItems.STORM_CALLER) || p.getOffhandItem().is(ModItems.STORM_CALLER))) {
			Vec3 eye = p.getEyePosition();
			BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(120)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
			if (hit.getType() != HitResult.Type.MISS) want = hit.getLocation();
		}
		if (want == null) {
			if (this.wander == null || this.tickCount % 90 == 0 || this.wander.distanceToSqr(this.position()) < 4) {
				double a = this.random.nextDouble() * Math.PI * 2;
				this.wander = this.position().add(Math.cos(a) * 14, 0, Math.sin(a) * 14);
			}
			want = this.wander;
		}
		Vec3 to = new Vec3(want.x - this.getX(), 0, want.z - this.getZ());
		double d = to.length();
		Vec3 aim = d < 1.0 ? Vec3.ZERO : to.scale(Math.min(0.36, d * 0.05) / d);
		this.velocity = this.velocity.lerp(aim, 0.08);
		double nx = this.getX() + this.velocity.x, nz = this.getZ() + this.velocity.z;
		BlockPos top = Wreck.surface(level, Mth.floor(nx), Mth.floor(this.getY()) + 8, Mth.floor(nz), 40);
		double ny = top == null ? this.getY() : top.getY() + 1.0;
		this.setPos(nx, Mth.lerp(0.25, this.getY(), ny), nz);
	}

	/** What it is standing over decides what it is: fire, water or just wind. */
	private void sense(ServerLevel level) {
		BlockPos base = this.blockPosition();
		int hot = 0, wet = 0;
		for (BlockPos p : BlockPos.betweenClosed(base.offset(-3, -2, -3), base.offset(3, 1, 3))) {
			BlockState s = level.getBlockState(p);
			if (s.getFluidState().is(FluidTags.LAVA) || s.is(BlockTags.FIRE) || s.is(Blocks.MAGMA_BLOCK)) hot++;
			else if (s.getFluidState().is(FluidTags.WATER)) wet++;
		}
		if (hot >= 3) this.fireTicks = 400;
		int kind = this.fireTicks > 0 ? KIND_FIRE : wet >= 12 ? KIND_WATER : KIND_WIND;
		this.fireTicks = Math.max(0, this.fireTicks - 10);
		if (kind != this.kind()) this.entityData.set(KIND, kind);
	}

	/** Everything near its foot goes round it and up, and is thrown out at the top. */
	private void swirl(ServerLevel level, float down) {
		Vec3 base = this.position();
		boolean fire = this.kind() == KIND_FIRE;
		for (Entity e : Wreck.victims(level, this, base.add(0, HEIGHT * 0.4, 0), REACH + 6, HEIGHT * 0.6, this.owner, true)) {
			Vec3 off = e.position().subtract(base);
			double h = off.y, flat = Math.sqrt(off.x * off.x + off.z * off.z);
			double r = R0 + (R1 - R0) * Mth.clamp(h / HEIGHT, 0, 1);
			if (flat > REACH + r * 0.5 || h < -4 || h > HEIGHT + 4) continue;
			Vec3 in = flat < 0.01 ? Vec3.ZERO : new Vec3(-off.x / flat, 0, -off.z / flat);
			Vec3 round = new Vec3(-in.z, 0, in.x);
			double near = Mth.clamp(1.0 - flat / (REACH + r * 0.5), 0, 1);
			Vec3 v;
			if (h > HEIGHT * 0.75) {
				// thrown out at the top
				v = e.getDeltaMovement().add(in.scale(-0.35)).add(round.scale(0.3)).add(0, 0.05, 0);
			} else {
				double hold = flat < r ? -0.12 : 0.16 * near;
				v = e.getDeltaMovement().scale(0.8).add(round.scale((0.45 + 0.4 * near) * down)).add(in.scale(hold))
					.add(0, (flat < r * 1.4 ? 0.28 : 0.08 * near) * down, 0);
			}
			Wreck.setVelocity(e, v);
			if (e instanceof LivingEntity && flat < r * 1.3 && this.tickCount % 12 == 0) {
				e.hurtServer(level, this.damageSources().flyIntoWall(), 2.0F);
				if (fire) e.igniteForSeconds(6.0F);
			}
		}
	}

	/** Tears up the ground under it a block at a time; strips leaves and plants a little farther out. */
	private void tear(ServerLevel level, float down) {
		BlockPos base = this.blockPosition();
		boolean fire = this.kind() == KIND_FIRE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 9; i++) {
			double a = this.random.nextDouble() * Math.PI * 2, r = Math.sqrt(this.random.nextDouble()) * (i < 6 ? R0 * 2.4 : REACH * 0.7);
			int x = base.getX() + Mth.floor(Math.cos(a) * r), z = base.getZ() + Mth.floor(Math.sin(a) * r);
			// plants, leaves and loose things up to the treetops go first
			for (int y = base.getY() + 14; y >= base.getY() - 3; y--) {
				pos.set(x, y, z);
				BlockState s = level.getBlockState(pos);
				if (s.isAir() || !s.getFluidState().isEmpty()) continue;
				if (!Wreck.canDestroy(level, pos, s)) break;
				boolean light = s.is(BlockTags.LEAVES) || s.canBeReplaced() || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS);
				boolean ground = y <= base.getY() && i < 6;
				boolean wall = y > base.getY() && r < R0 * 2.4 && s.getDestroySpeed(level, pos) < 4.0F;
				if (!light && !ground && !wall) continue;
				if (!light) this.batch.add(s, x, y, z);
				if (!light && this.random.nextFloat() < 0.12F && !s.hasBlockEntity()) {
					// some of it is thrown far, and lands as a block somewhere else
					double fa = this.random.nextDouble() * Math.PI * 2, sp = 1.2 + this.random.nextDouble();
					Wreck.fling(level, pos.immutable(), s, new Vec3(Math.cos(fa) * sp, 1.6 + this.random.nextDouble(), Math.sin(fa) * sp));
				} else {
					Wreck.remove(level, pos, true);
				}
				if (fire && this.random.nextFloat() < 0.3F) {
					BlockPos below = pos.below();
					if (!level.getBlockState(below).isAir()) Wreck.ignite(level, below);
				}
				break;
			}
		}
	}

	private void clientTick(float t) {
		Level level = this.level();
		Vec3 c = this.position();
		float down = this.down(0.0F);
		int kind = this.kind();
		if (t < GATHER) {
			// dust and leaves wind up out of the circle in a small whirl that grows
			float k = t / GATHER;
			for (int i = 0; i < 2 + (int) (6 * k); i++) {
				double a = this.random.nextDouble() * Math.PI * 2, r = 1 + this.random.nextDouble() * 6 * (0.3 + k);
				level.addParticle(i % 2 == 0 ? ParticleTypes.POOF : ParticleTypes.ASH, c.x + Math.cos(a) * r, c.y + 0.3, c.z + Math.sin(a) * r,
					-Math.sin(a) * 0.3, 0.1 + 0.3 * k, Math.cos(a) * 0.3);
			}
			return;
		}
		if (down < 0.3F) return;
		BlockState ground = level.getBlockState(this.blockPosition().below());
		for (int i = 0; i < 8; i++) {
			double a = this.random.nextDouble() * Math.PI * 2, r = R0 * (0.8 + this.random.nextDouble() * 1.6);
			double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
			double sp = 0.6 + this.random.nextDouble() * 0.5;
			if (i < 3) {
				level.addAlwaysVisibleParticle(kind == KIND_FIRE ? ParticleTypes.FLAME : kind == KIND_WATER ? ParticleTypes.SPLASH : ParticleTypes.CLOUD,
					true, x, c.y + this.random.nextDouble() * 3, z, -Math.sin(a) * sp, 0.4 + this.random.nextDouble() * 0.5, Math.cos(a) * sp);
			} else if (i < 5 && !ground.isAir()) {
				level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, c.y + 0.2, z, -Math.sin(a) * sp, 0.6, Math.cos(a) * sp);
			} else {
				double h = this.random.nextDouble() * HEIGHT * 0.8, rr = R0 + (R1 - R0) * h / HEIGHT;
				level.addAlwaysVisibleParticle(kind == KIND_FIRE ? ParticleTypes.LAVA : ParticleTypes.WHITE_ASH, true, c.x + Math.cos(a) * rr, c.y + h, c.z + Math.sin(a) * rr,
					-Math.sin(a) * sp * 1.5, 0.3, Math.cos(a) * sp * 1.5);
			}
		}
		if (kind == KIND_FIRE) {
			for (int i = 0; i < 3; i++) {
				level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, c.x + this.random.nextGaussian() * 3, c.y + HEIGHT * this.random.nextDouble(), c.z + this.random.nextGaussian() * 3, 0, 0.3, 0);
			}
		}
	}
}

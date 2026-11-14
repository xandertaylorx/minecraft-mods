package com.massdestruction.entity;

import com.massdestruction.DebrisPayload;
import com.massdestruction.Wreck;
import com.massdestruction.registry.ModEntities;
import com.massdestruction.world.RiftPortals;
import java.util.ArrayDeque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A black hole, after Small Magic's singularity. It opens over the place and grows for as long as it lasts. Round it
 * turns a disc of what it has taken, white at the inner edge and dying out through gold and rose to violet, and a band
 * of runes. Everything goes to it: mobs and items are dragged in and spun round, and the ground is torn up block by
 * block, nearest first, and set circling in the disc. When it shuts (after 13 seconds, or when its owner sneaks and
 * uses the core again) it falls in on itself, then throws everything it held out again, level, in a ring.
 */
public class SingularityEntity extends EffectEntity {
	public static final float HEIGHT = 13.0F;
	/** Ticks it stays open on its own, ticks it takes to fall in, and how long the burst is drawn for. */
	public static final int LAST = 260;
	public static final int SHUT = 11;
	public static final int BURST = 60;
	/** Hole radius when full grown, and the disc's inner and outer edge in hole radii. */
	public static final float RH = 3.4F;
	public static final float INNER = 1.35F;
	public static final float DISC = 4.4F;
	public static final double PULL = 46.0;
	private static final int EAT_MAX = 27;
	private static final List<Wreck.Cell> BALL = Wreck.ball(EAT_MAX, 1.0, 1.0, 0x5106L);

	private static final EntityDataAccessor<Integer> SHUT_AT = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.INT);

	private int cursor;
	private int eaten;
	private final ArrayDeque<BlockState> held = new ArrayDeque<>();
	private final DebrisPayload.Batch batch = new DebrisPayload.Batch();

	public SingularityEntity(EntityType<? extends SingularityEntity> type, Level level) {
		super(type, level);
	}

	public SingularityEntity(Level level, Vec3 ground, @Nullable Entity owner, boolean breaksBlocks) {
		this(ModEntities.SINGULARITY, level);
		this.snapTo(ground.x, ground.y + HEIGHT, ground.z);
		this.begin(owner, breaksBlocks);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(SHUT_AT, -1);
	}

	/** Age at which it began to shut, or -1 while open. */
	public int shutAt() {
		return this.entityData.get(SHUT_AT);
	}

	public void close() {
		if (this.shutAt() < 0) this.entityData.set(SHUT_AT, Math.max(1, (int) this.age(0.0F)));
	}

	/** The hole's radius at this moment (blocks). Same on server and client. */
	public float radius(float partial) {
		float t = this.age(partial);
		float opened = 1.0F - (float) Math.pow(1.0F - Math.min(1.0F, t / 14.0F), 3);
		int shut = this.shutAt();
		float held = shut < 0 ? t : shut;
		float size = 0.3F + 0.7F * (1.0F - (float) Math.exp(-held * 0.022));
		float r = RH * size * opened;
		if (shut >= 0) {
			float k = Math.min(1.0F, (t - shut) / SHUT);
			r *= Math.max(0.03F, (float) Math.pow(1.0F - k, 2.2));
		}
		return r;
	}

	/** 0 while open, 0..1 while falling in. */
	public float closing(float partial) {
		int shut = this.shutAt();
		return shut < 0 ? 0.0F : Mth.clamp((this.age(partial) - shut) / SHUT, 0.0F, 1.0F);
	}

	/** Ticks since it burst, or negative before. */
	public float sinceBurst(float partial) {
		int shut = this.shutAt();
		return shut < 0 ? -1.0F : this.age(partial) - shut - SHUT;
	}

	/** How hard it draws right now: grows with it, and much harder in the moment it falls in. */
	public float grip(float partial) {
		if (this.sinceBurst(partial) >= 0) return 0.0F;
		float t = this.age(partial);
		float opened = Math.min(1.0F, t / 14.0F);
		return opened * (0.45F + 0.55F * this.radius(partial) / RH) * (1.0F + 2.5F * this.closing(partial));
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
		Vec3 c = this.position();
		if (this.shutAt() < 0 && t >= LAST) this.close();
		float burst = this.sinceBurst(0.0F);
		if (burst >= 0) {
			if (burst < 1) this.burst(level);
			if (burst > BURST) this.discard();
			return;
		}
		int ti = (int) t;
		if (ti == 0) {
			Wreck.sound(level, c, SoundEvents.END_PORTAL_SPAWN, 8.0F, 0.5F);
			Wreck.sound(level, c, SoundEvents.WARDEN_SONIC_CHARGE, 6.0F, 0.3F);
		}
		if (ti % 40 == 5) Wreck.sound(level, c, SoundEvents.PORTAL_AMBIENT, 10.0F, 0.4F);
		if (ti % 30 == 12) Wreck.sound(level, c, SoundEvents.BEACON_AMBIENT, 8.0F, 0.3F);
		if (this.shutAt() >= 0 && ti == this.shutAt()) {
			Wreck.sound(level, c, SoundEvents.PORTAL_TRAVEL, 4.0F, 1.6F);
			Wreck.sound(level, c, SoundEvents.WARDEN_SONIC_CHARGE, 8.0F, 1.4F);
		}

		float grip = this.grip(0.0F);
		float r = this.radius(0.0F);
		this.pull(level, c, grip, r);
		if (this.breaksBlocks) this.eat(level, t, r, grip);
		this.batch.flush(this);
		if (ti % 20 == 7) RiftPortals.tryLightNear(level, BlockPos.containing(c), 9);
		if (ti % 20 == 7) RiftPortals.tryLightNear(level, BlockPos.containing(c).below((int) HEIGHT), 9);
	}

	private void pull(ServerLevel level, Vec3 c, float grip, float r) {
		for (Entity e : Wreck.victims(level, this, c, PULL, PULL, this.owner, true)) {
			Vec3 to = c.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
			double d = to.length();
			if (d > PULL || d < 0.01) continue;
			Vec3 dir = to.scale(1.0 / d);
			Vec3 side = new Vec3(-dir.z, 0, dir.x);
			double a = grip * 0.11 * Math.min(3.0, 14.0 / (d + 2.0));
			Vec3 v = e.getDeltaMovement().scale(0.92).add(dir.scale(a)).add(side.scale(a * 0.7)).add(0, a * 0.25, 0);
			Wreck.setVelocity(e, v);
			if (d < r * 1.5 + 1.0) {
				if (e instanceof ItemEntity || e instanceof ExperienceOrb) {
					e.discard();
				} else if (e instanceof LivingEntity && this.tickCount % 5 == 0) {
					e.hurtServer(level, this.damageSources().magic(), 7.0F);
				}
			}
		}
	}

	/** Tears out blocks inside a sphere that grows with it, nearest first. */
	private void eat(ServerLevel level, float t, float r, float grip) {
		float reach = r * 2.0F + 3.0F + 20.0F * (1.0F - (float) Math.exp(-t / 70.0F));
		if (this.shutAt() >= 0) reach += 4.0F;   // a last hard pull as it falls in
		BlockPos c = this.blockPosition();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int budget = (int) (60 + 260 * grip), checked = 0;
		while (this.cursor < BALL.size() && budget > 0 && checked < 9000) {
			Wreck.Cell cell = BALL.get(this.cursor);
			BlockPos o = cell.offset();
			if (Math.sqrt(o.getX() * o.getX() + o.getY() * o.getY() + o.getZ() * o.getZ()) > reach) break;
			this.cursor++;
			checked++;
			pos.setWithOffset(c, o);
			if (!level.isLoaded(pos)) continue;
			BlockState s = level.getBlockState(pos);
			if (!Wreck.canDestroy(level, pos, s) || !s.getFluidState().isEmpty()) continue;
			// about a third of what goes is drawn circling in the disc; the rest is simply swallowed
			if (this.random.nextFloat() < 0.38F) {
				this.batch.add(s, pos.getX(), pos.getY(), pos.getZ());
				if (this.held.size() > 220) this.held.pollFirst();
				if (!s.hasBlockEntity()) this.held.addLast(s);
			}
			Wreck.remove(level, pos, cell.rim());
			this.eaten++;
			budget--;
		}
	}

	/** Falls in on itself, then throws everything it held out again in a level ring. */
	private void burst(ServerLevel level) {
		Vec3 c = this.position();
		Wreck.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 20.0F, 0.4F);
		Wreck.sound(level, c, SoundEvents.WARDEN_SONIC_BOOM, 12.0F, 0.6F);
		Wreck.sound(level, c, SoundEvents.END_PORTAL_SPAWN, 10.0F, 1.5F);
		Wreck.sound(level, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 16.0F, 1.6F);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0.85F, 0.7F, 1.0F), c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 10, 6, 1, 6, 0);
		for (Entity e : Wreck.victims(level, this, c, PULL, PULL, this.owner, true)) {
			Vec3 off = e.position().subtract(c);
			double d = off.length();
			if (d > PULL) continue;
			double near = 1.0 - d / PULL;
			Vec3 flat = new Vec3(off.x, 0, off.z);
			Vec3 away = flat.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : flat.normalize();
			Wreck.push(e, away.scale(1.5 + 3.5 * near).add(0, 0.5 + 0.6 * near, 0));
			if (e instanceof LivingEntity && near > 0.5) e.hurtServer(level, this.damageSources().explosion(this, this.owner), 10.0F + 20.0F * (float) near);
		}
		if (this.breaksBlocks) {
			int n = Math.min(70, this.held.size());
			for (int i = 0; i < n; i++) {
				BlockState s = this.held.pollLast();
				if (s == null) break;
				double a = this.random.nextDouble() * Math.PI * 2, sp = 1.2 + this.random.nextDouble() * 1.8;
				BlockPos at = BlockPos.containing(c.x + Math.cos(a) * 2, c.y, c.z + Math.sin(a) * 2);
				if (!level.getBlockState(at).isAir()) continue;
				level.setBlock(at, s, 2);
				Wreck.fling(level, at, s, new Vec3(Math.cos(a) * sp, 0.2 + this.random.nextDouble() * 0.6, Math.sin(a) * sp));
			}
		}
	}

	private void clientTick(float t) {
		Level level = this.level();
		Vec3 c = this.position();
		float burst = this.sinceBurst(0.0F);
		if (burst >= 0) {
			if (burst < 1) {
				level.setSkyFlashTime(6);
				for (int i = 0; i < 300; i++) {
					double a = this.random.nextDouble() * Math.PI * 2, sp = 0.8 + this.random.nextDouble() * 2.6;
					level.addAlwaysVisibleParticle(i % 3 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.END_ROD, true, c.x, c.y, c.z,
						Math.cos(a) * sp, (this.random.nextDouble() - 0.5) * 0.3, Math.sin(a) * sp);
				}
			}
			return;
		}
		float grip = this.grip(0.0F);
		// sparks and motes wind in from all round, and dust is drawn up off the ground
		int n = 4 + (int) (14 * grip);
		for (int i = 0; i < n; i++) {
			double a = this.random.nextDouble() * Math.PI * 2, d = 8 + 26 * this.random.nextDouble(), up = this.random.nextDouble();
			double x = c.x + Math.cos(a) * d, z = c.z + Math.sin(a) * d, y = c.y - HEIGHT + 0.5 + up * up * (HEIGHT + 6);
			double tv = 0.35 + 0.3 * this.random.nextDouble();
			Vec3 in = new Vec3(c.x - x, c.y - y, c.z - z).normalize().scale(0.25 + 0.4 * grip);
			level.addAlwaysVisibleParticle(i % 4 == 0 ? ParticleTypes.REVERSE_PORTAL : i % 4 == 1 ? ParticleTypes.WITCH : ParticleTypes.END_ROD, true, x, y, z,
				-Math.sin(a) * tv + in.x, in.y + 0.05, Math.cos(a) * tv + in.z);
		}
		if (this.random.nextFloat() < grip) {
			double a = this.random.nextDouble() * Math.PI * 2, d = 3 + this.random.nextDouble() * 14;
			level.addParticle(ParticleTypes.CLOUD, c.x + Math.cos(a) * d, c.y - HEIGHT + 0.3, c.z + Math.sin(a) * d, -Math.cos(a) * 0.2, 0.25, -Math.sin(a) * 0.2);
		}
	}
}

package com.heroarsenal.entity;

import com.heroarsenal.Motion;
import com.heroarsenal.registry.ModEntities;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The marked target of an Orbital Laser strike. It sits still on the ground and runs a fixed timeline:
 * <ol>
 *   <li>Charge ({@link #CHARGE_TICKS}): target ring + rising sparks + warning sounds. No world work at all.</li>
 *   <li>Beam ({@link #BEAM_TICKS}): plasma beam from the sky. Entities are hit in a few pulses, and the crater
 *       is carved a bounded number of blocks per tick. Fire is placed once the crater is finished.</li>
 * </ol>
 * Clients get the strike through the normal entity tracking, and the timeline is keyed to the world's game
 * time ({@link #FIRE_TIME}), so a player who walks into range mid-strike still sees the right phase.
 */
public class OrbitalStrikeEntity extends Entity {
	public static final int CHARGE_TICKS = 50;
	public static final int BEAM_TICKS = 34;
	/** Crater radius in blocks. */
	public static final int CRATER_RADIUS = 6;
	/** Entities inside this radius get hit. */
	public static final double BLAST_RADIUS = 8.0;
	private static final float MAX_DAMAGE = 28.0F;
	private static final float MIN_DAMAGE = 8.0F;
	private static final float BURN_DAMAGE = 4.0F;
	/** Hard limits on world work per tick, so a strike never causes a lag spike. */
	private static final int BLOCKS_REMOVED_PER_TICK = 80;
	private static final int POSITIONS_CHECKED_PER_TICK = 400;
	private static final int MAX_FIRES = 14;
	/** Blocks at least this blast-resistant (obsidian, bedrock, reinforced deepslate...) survive. */
	private static final float MAX_RESISTANCE = 1200.0F;

	/** Crater offsets, nearest first, with a little jitter so the rim looks ragged. Shared by all strikes. */
	private static final List<BlockPos> CRATER_OFFSETS = buildCraterOffsets();

	private static final EntityDataAccessor<Long> FIRE_TIME = SynchedEntityData.defineId(OrbitalStrikeEntity.class, EntityDataSerializers.LONG);

	private @Nullable Entity owner;
	private boolean breaksBlocks = true;
	private boolean impacted;
	private int craterCursor;
	private boolean firesPlaced;

	public OrbitalStrikeEntity(EntityType<? extends OrbitalStrikeEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	public OrbitalStrikeEntity(Level level, Vec3 target, @Nullable Entity owner, boolean breaksBlocks) {
		this(ModEntities.ORBITAL_STRIKE, level);
		this.snapTo(target.x, target.y, target.z);
		this.owner = owner;
		this.breaksBlocks = breaksBlocks;
		this.entityData.set(FIRE_TIME, level.getGameTime() + CHARGE_TICKS);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(FIRE_TIME, Long.MIN_VALUE);
	}

	/** Ticks since the beam fired; negative while still charging. Works the same on server and client. */
	public float ticksSinceFire(float partialTick) {
		long fireTime = this.entityData.get(FIRE_TIME);
		if (fireTime == Long.MIN_VALUE) {
			// Summoned with a command: start charging now.
			fireTime = this.level().getGameTime() + CHARGE_TICKS - this.tickCount;
		}
		return (float) (this.level().getGameTime() - fireTime) + partialTick;
	}

	@Override
	public void tick() {
		super.tick();
		float t = this.ticksSinceFire(0.0F);
		if (this.level().isClientSide()) {
			this.clientEffects(t);
			return;
		}

		ServerLevel level = (ServerLevel) this.level();
		if (t < 0) {
			this.chargeSounds(level, (int) t + CHARGE_TICKS);
			return;
		}

		if (!this.impacted) {
			this.impacted = true;
			this.impact(level);
		}
		int beamTick = (int) t;
		if (beamTick == 10 || beamTick == 20) {
			this.hitEntities(level, false);
		}
		if (beamTick % 12 == 6) {
			this.sound(level, SoundEvents.BEACON_AMBIENT, 4.0F, 1.6F);
		}
		if (this.breaksBlocks) {
			this.carveCrater(level);
		}
		if (beamTick >= BEAM_TICKS && (!this.breaksBlocks || this.firesPlaced)) {
			this.sound(level, SoundEvents.BEACON_DEACTIVATE, 3.0F, 0.7F);
			this.discard();
		}
	}

	// ---------------------------------------------------------------- server

	private void chargeSounds(ServerLevel level, int chargeTick) {
		if (chargeTick == 0) {
			this.sound(level, SoundEvents.BEACON_ACTIVATE, 4.0F, 0.6F);
		} else if (chargeTick == 8) {
			this.sound(level, SoundEvents.WARDEN_SONIC_CHARGE, 4.0F, 0.75F);
		}
		// Warning beeps that speed up and rise in pitch.
		int interval = chargeTick < 25 ? 10 : 5;
		if (chargeTick % interval == 0) {
			this.sound(level, SoundEvents.NOTE_BLOCK_BIT.value(), 3.0F, 0.9F + chargeTick / (float) CHARGE_TICKS);
		}
	}

	private void impact(ServerLevel level) {
		Vec3 pos = this.position();
		this.sound(level, SoundEvents.WARDEN_SONIC_BOOM, 6.0F, 0.6F);
		this.sound(level, SoundEvents.GENERIC_EXPLODE.value(), 8.0F, 0.55F);
		this.sound(level, SoundEvents.LIGHTNING_BOLT_THUNDER, 10.0F, 1.3F);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 0.5, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 1.0, pos.z, 8, 3.0, 1.0, 3.0, 0.0);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0.7F, 0.95F, 1.0F), pos.x, pos.y + 1.0, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
		this.hitEntities(level, true);
	}

	/** One entity scan, only on the impact tick and two follow-up pulses (never per tick). */
	private void hitEntities(ServerLevel level, boolean blast) {
		Vec3 center = this.position();
		AABB area = new AABB(center, center).inflate(BLAST_RADIUS, BLAST_RADIUS * 0.75, BLAST_RADIUS);
		DamageSource source = this.damageSources().explosion(this, this.owner);
		for (Entity target : level.getEntities(this, area, e -> e.isAlive()
			&& !e.isSpectator()
			&& e != this.owner
			&& !(e instanceof ItemEntity)
			&& !(e instanceof ExperienceOrb)
			&& !(e instanceof OrbitalStrikeEntity)
			&& (this.owner == null || !e.isPassengerOfSameVehicle(this.owner)))) {
			Vec3 offset = target.position().subtract(center);
			double distance = offset.horizontalDistance();
			if (distance > BLAST_RADIUS) {
				continue;
			}
			double closeness = 1.0 - distance / BLAST_RADIUS;
			if (blast) {
				target.hurtServer(level, source, Mth.lerp((float) closeness, MIN_DAMAGE, MAX_DAMAGE));
				double resist = target instanceof LivingEntity living ? living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 0.0;
				double strength = (0.8 + 1.8 * closeness) * Math.max(0.0, 1.0 - resist);
				Vec3 away = distance < 0.3 ? Vec3.ZERO : new Vec3(offset.x, 0.0, offset.z).normalize();
				Motion.setVelocity(target, target.getDeltaMovement().add(away.scale(strength)).add(0.0, 0.5 + 0.6 * closeness, 0.0));
				target.igniteForSeconds(6.0F);
			} else if (distance < BLAST_RADIUS * 0.6) {
				// Anything still standing in the beam keeps burning.
				target.hurtServer(level, source, BURN_DAMAGE);
				target.igniteForSeconds(4.0F);
			}
		}
	}

	/** Removes at most {@link #BLOCKS_REMOVED_PER_TICK} blocks per tick, nearest to the center first. */
	private void carveCrater(ServerLevel level) {
		BlockPos center = this.blockPosition();
		int removed = 0;
		int checked = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		while (this.craterCursor < CRATER_OFFSETS.size() && removed < BLOCKS_REMOVED_PER_TICK && checked < POSITIONS_CHECKED_PER_TICK) {
			BlockPos offset = CRATER_OFFSETS.get(this.craterCursor++);
			checked++;
			pos.setWithOffset(center, offset);
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F || state.getBlock().getExplosionResistance() >= MAX_RESISTANCE) {
				continue;
			}
			// Vaporized: no drops. Containers still spill their contents like they do in an explosion.
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			removed++;
		}

		if (this.craterCursor >= CRATER_OFFSETS.size() && !this.firesPlaced) {
			this.firesPlaced = true;
			this.placeFires(level, center);
		}
	}

	private void placeFires(ServerLevel level, BlockPos center) {
		int placed = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int attempt = 0; attempt < 40 && placed < MAX_FIRES; attempt++) {
			double angle = this.random.nextDouble() * Math.PI * 2.0;
			double r = Math.sqrt(this.random.nextDouble()) * (CRATER_RADIUS + 1.5);
			int x = center.getX() + Mth.floor(Math.cos(angle) * r);
			int z = center.getZ() + Mth.floor(Math.sin(angle) * r);
			// Walk down to the first air block that sits on something solid.
			for (int y = center.getY() + 3; y >= center.getY() - CRATER_RADIUS - 2; y--) {
				pos.set(x, y, z);
				if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir()) {
					BlockState fire = BaseFireBlock.getState(level, pos);
					if (fire.canSurvive(level, pos)) {
						level.setBlock(pos, fire, Block.UPDATE_ALL);
						placed++;
					}
					break;
				}
			}
		}
	}

	private void sound(ServerLevel level, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	private static List<BlockPos> buildCraterOffsets() {
		record Entry(BlockPos offset, double score) {
		}
		List<Entry> entries = new ArrayList<>();
		java.util.Random jitter = new java.util.Random(0x0B17A1L);
		int r = CRATER_RADIUS;
		for (int dx = -r - 1; dx <= r + 1; dx++) {
			for (int dz = -r - 1; dz <= r + 1; dz++) {
				// A bowl: full radius sideways, a bit shallower downward, and a clearing above the ground.
				for (int dy = -r; dy <= r; dy++) {
					double vertical = dy < 0 ? dy * 1.3 : dy * 0.8;
					double d = Math.sqrt(dx * dx + dz * dz + vertical * vertical);
					double edge = r + (jitter.nextDouble() - 0.5) * 1.6;
					if (d <= edge) {
						entries.add(new Entry(new BlockPos(dx, dy, dz), d));
					}
				}
			}
		}
		entries.sort(Comparator.comparingDouble(Entry::score));
		return entries.stream().map(Entry::offset).toList();
	}

	// ---------------------------------------------------------------- client

	/** A handful of particles per tick at most. The ring and beam themselves are drawn by the renderer. */
	private void clientEffects(float t) {
		Level level = this.level();
		Vec3 c = this.position();
		if (t < 0) {
			float charge = 1.0F + t / CHARGE_TICKS;
			double radius = BLAST_RADIUS * (0.35 + 0.65 * charge);
			int count = 2 + (int) (charge * 3);
			for (int i = 0; i < count; i++) {
				double a = this.random.nextDouble() * Math.PI * 2.0;
				double r = radius * (0.85 + this.random.nextDouble() * 0.15);
				level.addParticle(ParticleTypes.END_ROD, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 0.0, 0.08 + 0.15 * charge, 0.0);
			}
			if (this.random.nextFloat() < charge) {
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, c.x + this.random.nextGaussian() * 0.6, c.y + 0.3 + this.random.nextDouble() * 3.0,
					c.z + this.random.nextGaussian() * 0.6, 0.0, 0.2, 0.0);
			}
		} else if (t < BEAM_TICKS) {
			if (t < 1.0F) {
				level.setSkyFlashTime(3);
			}
			for (int i = 0; i < 4; i++) {
				double a = this.random.nextDouble() * Math.PI * 2.0;
				double r = this.random.nextDouble() * CRATER_RADIUS;
				double x = c.x + Math.cos(a) * r;
				double z = c.z + Math.sin(a) * r;
				level.addParticle(i == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME, x, c.y - 1.0 + this.random.nextDouble() * 2.0, z,
					(x - c.x) * 0.04, 0.15 + this.random.nextDouble() * 0.2, (z - c.z) * 0.04);
			}
		}
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		double size = 256.0 * getViewScale();
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
		// Never saved (see ModEntities: noSave). A strike is short-lived.
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}

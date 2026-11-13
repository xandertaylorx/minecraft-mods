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
import net.minecraft.world.entity.item.FallingBlockEntity;
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
 *   <li>Charge ({@link #CHARGE_TICKS}): target rings + rising sparks + warning sounds. No world work at all.</li>
 *   <li>Beam ({@link #BEAM_TICKS}): plasma beam from the sky. Debris is flung out, entities are hit in a few
 *       pulses, the crater is carved a bounded number of blocks per tick, then the crater floor is scorched
 *       (magma, blackstone, basalt) and set on fire, also in bounded batches.</li>
 * </ol>
 * Clients get the strike through the normal entity tracking, and the timeline is keyed to the world's game
 * time ({@link #FIRE_TIME}), so a player who walks into range mid-strike still sees the right phase.
 */
public class OrbitalStrikeEntity extends Entity {
	public static final int CHARGE_TICKS = 50;
	public static final int BEAM_TICKS = 60;
	/** Crater radius in blocks. */
	public static final int CRATER_RADIUS = 12;
	/** Entities inside this radius get hit. */
	public static final double BLAST_RADIUS = 16.0;
	private static final float MAX_DAMAGE = 40.0F;
	private static final float MIN_DAMAGE = 10.0F;
	private static final float BURN_DAMAGE = 5.0F;
	/** Hard limits on world work per tick, so a strike never causes a lag spike. */
	private static final int BLOCKS_REMOVED_PER_TICK = 300;
	private static final int POSITIONS_CHECKED_PER_TICK = 2000;
	private static final int LINING_COLUMNS_PER_TICK = 60;
	private static final int MAX_DEBRIS = 32;
	private static final int MAX_FIRES = 60;
	/** Blocks at least this blast-resistant (obsidian, bedrock, reinforced deepslate...) survive. */
	private static final float MAX_RESISTANCE = 1200.0F;

	/** Crater offsets, nearest first, with jitter so the rim looks ragged. Shared by all strikes. */
	private static final List<CraterBlock> CRATER = buildCrater();
	/** Floor columns (x, z offsets) to scorch once the crater is carved. */
	private static final List<int[]> LINING_COLUMNS = buildLiningColumns();

	private static final EntityDataAccessor<Long> FIRE_TIME = SynchedEntityData.defineId(OrbitalStrikeEntity.class, EntityDataSerializers.LONG);

	private record CraterBlock(BlockPos offset, boolean shell) {
	}

	private @Nullable Entity owner;
	private boolean breaksBlocks = true;
	private boolean impacted;
	private int craterCursor;
	private int liningCursor;
	private int firesPlaced;

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
		if (beamTick > 0 && beamTick % 10 == 0 && beamTick < BEAM_TICKS) {
			this.hitEntities(level, false);
		}
		if (beamTick == 6) {
			this.sound(level, SoundEvents.GENERIC_EXPLODE.value(), 10.0F, 0.4F);
		}
		if (beamTick % 10 == 4) {
			this.sound(level, SoundEvents.BEACON_AMBIENT, 6.0F, 1.4F);
			this.sound(level, SoundEvents.FIRE_AMBIENT, 4.0F, 0.6F);
		}
		boolean worldDone = true;
		if (this.breaksBlocks) {
			worldDone = this.carveCrater(level) && this.scorch(level);
		}
		if (beamTick >= BEAM_TICKS && worldDone) {
			this.sound(level, SoundEvents.BEACON_DEACTIVATE, 4.0F, 0.6F);
			this.discard();
		}
	}

	// ---------------------------------------------------------------- server

	private void chargeSounds(ServerLevel level, int chargeTick) {
		if (chargeTick == 0) {
			this.sound(level, SoundEvents.BEACON_ACTIVATE, 5.0F, 0.5F);
		} else if (chargeTick == 8) {
			this.sound(level, SoundEvents.WARDEN_SONIC_CHARGE, 5.0F, 0.7F);
		} else if (chargeTick == CHARGE_TICKS - 12) {
			this.sound(level, SoundEvents.RESPAWN_ANCHOR_CHARGE, 5.0F, 0.5F);
		}
		// Warning beeps that speed up and rise in pitch.
		int interval = chargeTick < 20 ? 10 : chargeTick < 38 ? 5 : 2;
		if (chargeTick % interval == 0) {
			this.sound(level, SoundEvents.NOTE_BLOCK_BIT.value(), 3.0F, 0.8F + 1.2F * chargeTick / CHARGE_TICKS);
		}
	}

	private void impact(ServerLevel level) {
		Vec3 pos = this.position();
		this.sound(level, SoundEvents.WARDEN_SONIC_BOOM, 8.0F, 0.5F);
		this.sound(level, SoundEvents.GENERIC_EXPLODE.value(), 12.0F, 0.5F);
		this.sound(level, SoundEvents.GENERIC_EXPLODE.value(), 12.0F, 0.8F);
		this.sound(level, SoundEvents.LIGHTNING_BOLT_THUNDER, 16.0F, 1.2F);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 0.5, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
		for (int i = 0; i < 4; i++) {
			double a = i * Math.PI / 2.0 + this.random.nextDouble();
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x + Math.cos(a) * 7.0, pos.y + 1.0, pos.z + Math.sin(a) * 7.0, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 1.5, pos.z, 16, 6.0, 1.5, 6.0, 0.0);
		level.sendParticles(ParticleTypes.SONIC_BOOM, pos.x, pos.y + 1.0, pos.z, 6, 5.0, 0.5, 5.0, 0.0);
		level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.5, pos.z, 40, 4.0, 0.5, 4.0, 0.0);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0.7F, 0.95F, 1.0F), pos.x, pos.y + 1.0, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
		this.hitEntities(level, true);
		if (this.breaksBlocks) {
			this.flingDebris(level);
		}
	}

	/** One entity scan on impact and one every 10 ticks of the beam (never per tick). */
	private void hitEntities(ServerLevel level, boolean blast) {
		Vec3 center = this.position();
		AABB area = new AABB(center, center).inflate(BLAST_RADIUS, BLAST_RADIUS * 0.75, BLAST_RADIUS);
		DamageSource source = this.damageSources().explosion(this, this.owner);
		for (Entity target : level.getEntities(this, area, e -> e.isAlive()
			&& !e.isSpectator()
			&& e != this.owner
			&& !(e instanceof ItemEntity)
			&& !(e instanceof ExperienceOrb)
			&& !(e instanceof FallingBlockEntity)
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
				double strength = (1.2 + 2.8 * closeness) * Math.max(0.0, 1.0 - resist);
				Vec3 away = distance < 0.3 ? Vec3.ZERO : new Vec3(offset.x, 0.0, offset.z).normalize();
				Motion.setVelocity(target, target.getDeltaMovement().add(away.scale(strength)).add(0.0, 0.7 + 0.8 * closeness, 0.0));
				target.igniteForSeconds(8.0F);
			} else if (distance < CRATER_RADIUS) {
				// Anything still standing in the blast zone keeps burning.
				target.hurtServer(level, source, BURN_DAMAGE);
				target.igniteForSeconds(5.0F);
			}
		}
	}

	/** Throws a capped number of real blocks out of the impact zone. They land as blocks around the crater. */
	private void flingDebris(ServerLevel level) {
		BlockPos center = this.blockPosition();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int thrown = 0;
		for (int attempt = 0; attempt < MAX_DEBRIS * 2 && thrown < MAX_DEBRIS; attempt++) {
			double angle = this.random.nextDouble() * Math.PI * 2.0;
			double r = 1.0 + this.random.nextDouble() * (CRATER_RADIUS - 2);
			int x = center.getX() + Mth.floor(Math.cos(angle) * r);
			int z = center.getZ() + Mth.floor(Math.sin(angle) * r);
			for (int y = center.getY() + 3; y >= center.getY() - 4; y--) {
				pos.set(x, y, z);
				BlockState state = level.getBlockState(pos);
				if (state.isAir()) {
					continue;
				}
				if (this.canDestroy(level, pos, state) && !state.hasBlockEntity() && state.getFluidState().isEmpty()) {
					FallingBlockEntity debris = FallingBlockEntity.fall(level, pos.immutable(), state);
					debris.dropItem = false;
					double speed = 0.6 + this.random.nextDouble() * 0.9;
					Motion.setVelocity(debris, new Vec3(Math.cos(angle) * speed, 0.9 + this.random.nextDouble() * 0.9, Math.sin(angle) * speed));
					thrown++;
				}
				break;
			}
		}
	}

	/** Removes at most {@link #BLOCKS_REMOVED_PER_TICK} blocks per tick, nearest to the center first. Returns true when done. */
	private boolean carveCrater(ServerLevel level) {
		BlockPos center = this.blockPosition();
		int removed = 0;
		int checked = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		while (this.craterCursor < CRATER.size() && removed < BLOCKS_REMOVED_PER_TICK && checked < POSITIONS_CHECKED_PER_TICK) {
			CraterBlock entry = CRATER.get(this.craterCursor++);
			checked++;
			pos.setWithOffset(center, entry.offset());
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !this.canDestroy(level, pos, state)) {
				continue;
			}
			// Vaporized: no drops. Interior blocks skip neighbor updates (they're all going anyway);
			// the outer shell does full updates so water flows in and torches on the rim pop off.
			int flags = entry.shell() ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
			removed++;
		}
		return this.craterCursor >= CRATER.size();
	}

	/** Scorches the crater floor and sets it alight, a bounded number of columns per tick. Returns true when done. */
	private boolean scorch(ServerLevel level) {
		BlockPos center = this.blockPosition();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int end = Math.min(LINING_COLUMNS.size(), this.liningCursor + LINING_COLUMNS_PER_TICK);
		for (; this.liningCursor < end; this.liningCursor++) {
			int[] column = LINING_COLUMNS.get(this.liningCursor);
			int x = center.getX() + column[0];
			int z = center.getZ() + column[1];
			// Walk down to the crater floor.
			for (int y = center.getY() + 2; y >= center.getY() - CRATER_RADIUS - 2; y--) {
				pos.set(x, y, z);
				BlockState state = level.getBlockState(pos);
				if (state.isAir()) {
					continue;
				}
				if (state.getFluidState().isEmpty() && !state.hasBlockEntity() && this.canDestroy(level, pos, state)) {
					float roll = this.random.nextFloat();
					BlockState lining = roll < 0.3F ? Blocks.MAGMA_BLOCK.defaultBlockState()
						: roll < 0.55F ? Blocks.BLACKSTONE.defaultBlockState()
						: roll < 0.7F ? Blocks.BASALT.defaultBlockState()
						: null;
					if (lining != null) {
						level.setBlock(pos, lining, Block.UPDATE_CLIENTS);
					}
					BlockPos above = pos.above();
					if (this.firesPlaced < MAX_FIRES && this.random.nextFloat() < 0.22F && level.getBlockState(above).isAir()) {
						BlockState fire = BaseFireBlock.getState(level, above);
						if (fire.canSurvive(level, above)) {
							level.setBlock(above, fire, Block.UPDATE_ALL);
							this.firesPlaced++;
						}
					}
				}
				break;
			}
		}
		return this.liningCursor >= LINING_COLUMNS.size();
	}

	private boolean canDestroy(ServerLevel level, BlockPos pos, BlockState state) {
		return state.getDestroySpeed(level, pos) >= 0.0F && state.getBlock().getExplosionResistance() < MAX_RESISTANCE;
	}

	private void sound(ServerLevel level, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	private static List<CraterBlock> buildCrater() {
		record Entry(BlockPos offset, double score, double edge) {
		}
		List<Entry> entries = new ArrayList<>();
		java.util.Random jitter = new java.util.Random(0x0B17A1L);
		int r = CRATER_RADIUS;
		for (int dx = -r - 1; dx <= r + 1; dx++) {
			for (int dz = -r - 1; dz <= r + 1; dz++) {
				// A deep bowl below ground and a wide clearing above it.
				for (int dy = -r; dy <= r; dy++) {
					double vertical = dy < 0 ? dy * 1.15 : dy * 0.75;
					double d = Math.sqrt(dx * dx + dz * dz + vertical * vertical);
					double edge = r + (jitter.nextDouble() - 0.5) * 2.4;
					if (d <= edge) {
						entries.add(new Entry(new BlockPos(dx, dy, dz), d, edge));
					}
				}
			}
		}
		entries.sort(Comparator.comparingDouble(Entry::score));
		return entries.stream().map(e -> new CraterBlock(e.offset(), e.score() > e.edge() - 1.5)).toList();
	}

	private static List<int[]> buildLiningColumns() {
		List<int[]> columns = new ArrayList<>();
		int r = CRATER_RADIUS + 1;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz <= r * r) {
					columns.add(new int[] {dx, dz});
				}
			}
		}
		columns.sort(Comparator.comparingInt(c -> c[0] * c[0] + c[1] * c[1]));
		return columns;
	}

	// ---------------------------------------------------------------- client

	/** Around ten particles per tick at most. The rings and beam themselves are drawn by the renderer. */
	private void clientEffects(float t) {
		Level level = this.level();
		Vec3 c = this.position();
		if (t < 0) {
			float charge = 1.0F + t / CHARGE_TICKS;
			double radius = BLAST_RADIUS * (0.4 + 0.6 * charge);
			int count = 3 + (int) (charge * 5);
			for (int i = 0; i < count; i++) {
				double a = this.random.nextDouble() * Math.PI * 2.0;
				double r = radius * (0.85 + this.random.nextDouble() * 0.15);
				level.addParticle(i % 3 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD,
					c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 0.0, 0.1 + 0.25 * charge, 0.0);
			}
			if (this.random.nextFloat() < charge) {
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, c.x + this.random.nextGaussian() * 0.8, c.y + 0.3 + this.random.nextDouble() * 6.0,
					c.z + this.random.nextGaussian() * 0.8, 0.0, 0.3, 0.0);
			}
		} else if (t < BEAM_TICKS) {
			if (t < 2.0F) {
				level.setSkyFlashTime(5);
			}
			for (int i = 0; i < 3; i++) {
				level.addParticle(ParticleTypes.LAVA, c.x + this.random.nextGaussian() * 2.0, c.y, c.z + this.random.nextGaussian() * 2.0, 0.0, 0.0, 0.0);
			}
			for (int i = 0; i < 4; i++) {
				double a = this.random.nextDouble() * Math.PI * 2.0;
				double r = this.random.nextDouble() * CRATER_RADIUS;
				double x = c.x + Math.cos(a) * r;
				double z = c.z + Math.sin(a) * r;
				level.addParticle(i == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME, x, c.y - 2.0 + this.random.nextDouble() * 3.0, z,
					(x - c.x) * 0.05, 0.2 + this.random.nextDouble() * 0.3, (z - c.z) * 0.05);
			}
			if (((int) t & 1) == 0) {
				level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true,
					c.x + this.random.nextGaussian() * 3.0, c.y, c.z + this.random.nextGaussian() * 3.0, 0.0, 0.12, 0.0);
			}
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, c.x + this.random.nextGaussian() * 1.2, c.y + this.random.nextDouble() * 20.0,
				c.z + this.random.nextGaussian() * 1.2, this.random.nextGaussian() * 0.4, 0.0, this.random.nextGaussian() * 0.4);
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

package com.massdestruction.entity;

import com.massdestruction.Wreck;
import com.massdestruction.registry.ModEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Annihilator's strike. Charge: rune circles are written one above another in the sky over the target, the
 * ground ring tightens and a thin aiming beam comes down. Fire: a beam twenty blocks wide drops through the circles
 * (they flare and burst), digs a crater 18 blocks deep, glasses the sand, burns the grass and sets the rim alight,
 * and throws everything near it away. World work is bounded per tick, so it never freezes the server.
 */
public class AnnihilatorStrikeEntity extends EffectEntity {
	public static final int CHARGE = 80;
	public static final int BEAM = 140;
	public static final int CRATER = 18;
	public static final int SCORCH = 27;
	public static final double BLAST = 32.0;
	/** Heights of the rune circles over the target, and their radii. Shared with the renderer. */
	public static final float[] RING_Y = {16, 30, 46, 64, 86, 112};
	public static final float[] RING_R = {22, 14, 19, 11, 26, 9};

	private static final int REMOVE_PER_TICK = 1400;
	private static final int CHECK_PER_TICK = 7000;
	private static final int COLUMNS_PER_TICK = 70;
	private static final List<Wreck.Cell> HOLE = Wreck.ball(CRATER, 1.0, 0.65, 0xA77A1L);
	private static final List<int[]> COLUMNS = Wreck.disc(SCORCH);

	private boolean fired;
	private int cursor;
	private int column;
	private int fires;

	public AnnihilatorStrikeEntity(EntityType<? extends AnnihilatorStrikeEntity> type, Level level) {
		super(type, level);
	}

	public AnnihilatorStrikeEntity(Level level, Vec3 at, @Nullable Entity owner, boolean breaksBlocks) {
		this(ModEntities.ANNIHILATOR_STRIKE, level);
		this.snapTo(at.x, at.y, at.z);
		this.begin(owner, breaksBlocks);
	}

	/** Ticks since the beam fired; negative while it charges. */
	public float sinceFire(float partial) {
		return this.age(partial) - CHARGE;
	}

	@Override
	public void tick() {
		super.tick();
		float t = this.sinceFire(0.0F);
		if (this.level().isClientSide()) {
			this.clientTick(t);
			return;
		}
		ServerLevel level = (ServerLevel) this.level();
		if (t < 0) {
			this.chargeSounds(level, (int) t + CHARGE);
			return;
		}
		int bt = (int) t;
		if (!this.fired) {
			this.fired = true;
			this.impact(level);
		}
		if (bt > 0 && bt % 8 == 0 && bt < BEAM) this.hit(level, false);
		if (bt % 12 == 3 && bt < BEAM) {
			Wreck.sound(level, this.position(), SoundEvents.BEACON_AMBIENT, 8.0F, 0.6F);
			Wreck.sound(level, this.position(), SoundEvents.FIRE_AMBIENT, 6.0F, 0.5F);
		}
		if (bt == 10 || bt == 40) Wreck.sound(level, this.position(), SoundEvents.GENERIC_EXPLODE.value(), 14.0F, 0.35F);
		boolean done = true;
		if (this.breaksBlocks) done = this.carve(level) & this.scorch(level);
		if (bt >= BEAM && done) {
			Wreck.sound(level, this.position(), SoundEvents.BEACON_DEACTIVATE, 6.0F, 0.4F);
			this.discard();
		}
	}

	private void chargeSounds(ServerLevel level, int ct) {
		Vec3 p = this.position();
		if (ct == 0) Wreck.sound(level, p, SoundEvents.BEACON_ACTIVATE, 6.0F, 0.4F);
		if (ct == 5) Wreck.sound(level, p, SoundEvents.WARDEN_SONIC_CHARGE, 6.0F, 0.5F);
		if (ct == 40) Wreck.sound(level, p, SoundEvents.WARDEN_SONIC_CHARGE, 6.0F, 0.8F);
		if (ct == CHARGE - 16) Wreck.sound(level, p, SoundEvents.RESPAWN_ANCHOR_CHARGE, 6.0F, 0.4F);
		if (ct == CHARGE - 6) Wreck.sound(level, p, SoundEvents.TRIDENT_THUNDER.value(), 10.0F, 0.6F);
		// each circle in the sky chimes as it is written
		for (int i = 0; i < RING_Y.length; i++) {
			if (ct == ringWritten(i)) Wreck.sound(level, p.add(0, RING_Y[i] * 0.3, 0), SoundEvents.AMETHYST_BLOCK_RESONATE, 6.0F, 0.5F + i * 0.15F);
		}
		int every = ct < 30 ? 10 : ct < 60 ? 5 : 2;
		if (ct % every == 0) Wreck.sound(level, p, SoundEvents.NOTE_BLOCK_BIT.value(), 4.0F, 0.6F + 1.4F * ct / CHARGE);
	}

	/** The charge tick at which sky circle {@code i} is written. */
	public static int ringWritten(int i) {
		return 6 + i * 9;
	}

	private void impact(ServerLevel level) {
		Vec3 p = this.position();
		Wreck.sound(level, p, SoundEvents.WARDEN_SONIC_BOOM, 12.0F, 0.4F);
		Wreck.sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 16.0F, 0.4F);
		Wreck.sound(level, p, SoundEvents.LIGHTNING_BOLT_THUNDER, 20.0F, 0.7F);
		Wreck.sound(level, p, SoundEvents.DRAGON_FIREBALL_EXPLODE, 16.0F, 0.5F);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y + 1, p.z, 1, 0, 0, 0, 0);
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4 + this.random.nextDouble() * 0.5;
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x + Math.cos(a) * 11, p.y + 1, p.z + Math.sin(a) * 11, 1, 0, 0, 0, 0);
		}
		level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y + 1, p.z, 12, 9, 0.5, 9, 0);
		level.sendParticles(ParticleTypes.LAVA, p.x, p.y + 0.5, p.z, 80, 7, 0.5, 7, 0);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0.75F, 0.9F, 1.0F), p.x, p.y + 2, p.z, 1, 0, 0, 0, 0);
		this.hit(level, true);
		if (this.breaksBlocks) this.throwDebris(level, 90);
	}

	private void hit(ServerLevel level, boolean blast) {
		Vec3 c = this.position();
		DamageSource source = this.damageSources().explosion(this, this.owner);
		for (Entity e : Wreck.victims(level, this, c, BLAST, BLAST, this.owner, false)) {
			Vec3 off = e.position().subtract(c);
			double d = off.horizontalDistance();
			if (d > BLAST) continue;
			double near = 1.0 - d / BLAST;
			if (blast) {
				e.hurtServer(level, source, Wreck.lerp(near, 14.0F, 80.0F));
				Vec3 away = d < 0.3 ? Vec3.ZERO : new Vec3(off.x, 0, off.z).normalize();
				Wreck.push(e, away.scale(1.6 + 3.4 * near).add(0, 0.9 + 1.2 * near, 0));
				e.igniteForSeconds(10.0F);
			} else if (d < CRATER + 2) {
				e.hurtServer(level, source, 9.0F);
				e.igniteForSeconds(6.0F);
				Wreck.push(e, new Vec3(off.x, 0, off.z).normalize().scale(0.6).add(0, 0.4, 0));
			}
		}
	}

	private void throwDebris(ServerLevel level, int max) {
		BlockPos centre = this.blockPosition();
		int thrown = 0;
		for (int attempt = 0; attempt < max * 3 && thrown < max; attempt++) {
			double a = this.random.nextDouble() * Math.PI * 2;
			double r = 2 + this.random.nextDouble() * (CRATER - 2);
			BlockPos top = Wreck.surface(level, centre.getX() + Mth.floor(Math.cos(a) * r), centre.getY() + 6, centre.getZ() + Mth.floor(Math.sin(a) * r), 14);
			if (top == null) continue;
			BlockState state = level.getBlockState(top);
			if (!Wreck.canDestroy(level, top, state)) continue;
			double speed = 0.8 + this.random.nextDouble() * 1.6;
			Wreck.fling(level, top, state, new Vec3(Math.cos(a) * speed, 1.2 + this.random.nextDouble() * 1.4, Math.sin(a) * speed));
			thrown++;
		}
	}

	private boolean carve(ServerLevel level) {
		BlockPos c = this.blockPosition();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int removed = 0, checked = 0;
		while (this.cursor < HOLE.size() && removed < REMOVE_PER_TICK && checked < CHECK_PER_TICK) {
			Wreck.Cell cell = HOLE.get(this.cursor++);
			checked++;
			pos.setWithOffset(c, cell.offset());
			if (!level.isLoaded(pos)) continue;
			BlockState state = level.getBlockState(pos);
			if (!Wreck.canDestroy(level, pos, state)) continue;
			Wreck.remove(level, pos, cell.rim());
			removed++;
		}
		return this.cursor >= HOLE.size();
	}

	/** The crater floor turns to magma and glassy black rock; around it sand turns to glass and grass burns. */
	private boolean scorch(ServerLevel level) {
		if (this.cursor < HOLE.size() * 0.6) return false;   // let the hole open first
		BlockPos c = this.blockPosition();
		int end = Math.min(COLUMNS.size(), this.column + COLUMNS_PER_TICK);
		for (; this.column < end; this.column++) {
			int[] col = COLUMNS.get(this.column);
			double r = Math.sqrt(col[0] * col[0] + col[1] * col[1]);
			BlockPos top = Wreck.surface(level, c.getX() + col[0], c.getY() + 14, c.getZ() + col[1], 40);
			if (top == null) continue;
			BlockState state = level.getBlockState(top);
			if (!Wreck.canDestroy(level, top, state) || state.hasBlockEntity() || !state.getFluidState().isEmpty()) continue;
			float roll = this.random.nextFloat();
			BlockState now = null;
			if (r < CRATER + 1) {
				now = roll < 0.35F ? Blocks.MAGMA_BLOCK.defaultBlockState() : roll < 0.6F ? Blocks.BLACKSTONE.defaultBlockState()
					: roll < 0.75F ? Blocks.BASALT.defaultBlockState() : roll < 0.8F ? Blocks.OBSIDIAN.defaultBlockState() : null;
			} else {
				float edge = (float) ((r - CRATER) / (SCORCH - CRATER));
				if (roll < edge * 0.9F) continue;   // patchier toward the edge
				if (state.is(BlockTags.SAND)) now = Blocks.GLASS.defaultBlockState();
				else if (state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.canBeReplaced()) {
					Wreck.remove(level, top, true);
					continue;
				} else if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) {
					now = roll < 0.5F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState();
				} else if (state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)) {
					now = Blocks.SMOOTH_BASALT.defaultBlockState();
				}
			}
			if (now != null) level.setBlock(top, now, 2);
			if (this.fires < 110 && this.random.nextFloat() < (r < CRATER ? 0.2F : 0.12F) && Wreck.ignite(level, top)) this.fires++;
		}
		return this.column >= COLUMNS.size();
	}

	private void clientTick(float t) {
		Level level = this.level();
		Vec3 c = this.position();
		if (t < 0) {
			float k = 1.0F + t / CHARGE;
			// light drawn in toward the target from all round, faster as it charges
			for (int i = 0; i < 2 + (int) (k * 8); i++) {
				double a = this.random.nextDouble() * Math.PI * 2, r = BLAST * (0.6 + 0.5 * this.random.nextDouble());
				double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r, y = c.y + 0.5 + this.random.nextDouble() * 6;
				level.addAlwaysVisibleParticle(i % 4 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD, true, x, y, z,
					(c.x - x) * 0.06 * k, 0.08, (c.z - z) * 0.06 * k);
			}
			if (this.random.nextFloat() < k) {
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, c.x + this.random.nextGaussian(), c.y + this.random.nextDouble() * 30, c.z + this.random.nextGaussian(), 0, -0.6, 0);
			}
		} else if (t < BEAM) {
			if (t < 2) level.setSkyFlashTime(6);
			for (int i = 0; i < 6; i++) {
				double a = this.random.nextDouble() * Math.PI * 2, r = this.random.nextDouble() * CRATER;
				double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
				level.addAlwaysVisibleParticle(i < 2 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME, true, x, c.y - 6 + this.random.nextDouble() * 8, z,
					(x - c.x) * 0.06, 0.3 + this.random.nextDouble() * 0.5, (z - c.z) * 0.06);
			}
			for (int i = 0; i < 3; i++) {
				level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, c.x + this.random.nextGaussian() * 6, c.y - 2, c.z + this.random.nextGaussian() * 6, 0, 0.18, 0);
				level.addAlwaysVisibleParticle(ParticleTypes.LAVA, true, c.x + this.random.nextGaussian() * 5, c.y - 4, c.z + this.random.nextGaussian() * 5, 0, 0, 0);
			}
			for (int i = 0; i < 4; i++) {
				level.addAlwaysVisibleParticle(ParticleTypes.ELECTRIC_SPARK, true, c.x + this.random.nextGaussian() * 3, c.y + this.random.nextDouble() * 60, c.z + this.random.nextGaussian() * 3,
					this.random.nextGaussian() * 0.8, -0.5, this.random.nextGaussian() * 0.8);
			}
		}
	}
}

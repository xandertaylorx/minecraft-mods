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
 * The Nova Rod's explosion, after Small Magic's: it is gathered for a few seconds while circles of runes are written
 * one above another in the sky and a red wall of runes rises round the place. Then a spark falls through the circles,
 * each flaring as it passes, and where it lands there is a white instant, then one great ball of fire that climbs
 * and rolls under into a mushroom on a stem and cools to smoke. A ring runs out along the ground that strips the
 * trees, flattens the grass and throws down what is built; the middle is a crater with its edge left burning.
 */
public class NovaEntity extends EffectEntity {
	public static final int GATHER = 64;
	public static final int FALL = 6;
	public static final int BOOM = GATHER + FALL;
	/** How long the cloud is drawn for after the blast. */
	public static final int CLOUD = 320;
	public static final int CRATER = 14;
	public static final int SCORCH = 24;
	public static final double BLAST = 34.0;
	/** How far the ring along the ground runs, and how fast (blocks per tick). */
	public static final float WAVE_REACH = 56.0F;
	public static final float WAVE_SPEED = 2.2F;
	public static final float[] RING_Y = {6, 12, 18, 26, 34, 44};
	public static final float[] RING_R = {15, 8, 12, 6.5F, 19, 5};
	public static final int[] RING_SIDES = {3, 4, 6, 5, 8, 3};

	private static final List<Wreck.Cell> HOLE = Wreck.ball(CRATER, 1.35, 0.8, 0x0A0A5L);
	private static final List<int[]> COLUMNS = Wreck.disc((int) WAVE_REACH);

	private boolean boomed;
	private int cursor;
	private int column;
	private int fires;
	private float waveDone;

	public NovaEntity(EntityType<? extends NovaEntity> type, Level level) {
		super(type, level);
	}

	public NovaEntity(Level level, Vec3 at, @Nullable Entity owner, boolean breaksBlocks) {
		this(ModEntities.NOVA, level);
		this.snapTo(at.x, at.y, at.z);
		this.begin(owner, breaksBlocks);
	}

	public static int ringWritten(int i) {
		return 4 + i * 9;
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
		Vec3 p = this.position();
		int ti = (int) t;
		if (ti < BOOM) {
			for (int i = 0; i < RING_Y.length; i++) {
				if (ti == ringWritten(i)) Wreck.sound(level, p.add(0, 6, 0), SoundEvents.AMETHYST_BLOCK_RESONATE, 6.0F, 0.5F + 0.12F * i);
			}
			if (ti == 0) Wreck.sound(level, p, SoundEvents.BEACON_ACTIVATE, 6.0F, 0.3F);
			if (ti == 20) Wreck.sound(level, p, SoundEvents.PORTAL_TRIGGER, 4.0F, 0.6F);
			if (ti == GATHER - 10) Wreck.sound(level, p, SoundEvents.WARDEN_SONIC_CHARGE, 8.0F, 0.6F);
			if (ti == GATHER) Wreck.sound(level, p.add(0, 30, 0), SoundEvents.FIREWORK_ROCKET_LAUNCH, 10.0F, 0.5F);
			return;
		}
		float since = t - BOOM;
		if (!this.boomed) {
			this.boomed = true;
			this.boom(level);
		}
		if (this.breaksBlocks) {
			this.carve(level);
			this.wave(level, Math.min(WAVE_REACH, since * WAVE_SPEED));
		}
		if (ti % 20 == 0 && since < 120) Wreck.sound(level, p.add(0, 20, 0), SoundEvents.FIRE_AMBIENT, 10.0F, 0.4F);
		if (since > CLOUD) this.discard();
	}

	private void boom(ServerLevel level) {
		Vec3 p = this.position();
		Wreck.sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 24.0F, 0.3F);
		Wreck.sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 24.0F, 0.5F);
		Wreck.sound(level, p, SoundEvents.DRAGON_FIREBALL_EXPLODE, 20.0F, 0.4F);
		Wreck.sound(level, p, SoundEvents.LIGHTNING_BOLT_THUNDER, 24.0F, 0.5F);
		Wreck.sound(level, p, SoundEvents.WITHER_SPAWN, 10.0F, 0.6F);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 1.0F, 0.85F, 0.6F), p.x, p.y + 4, p.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y + 2, p.z, 6, 6, 2, 6, 0);
		level.sendParticles(ParticleTypes.LAVA, p.x, p.y + 1, p.z, 120, 8, 1, 8, 0);
		DamageSource source = this.damageSources().explosion(this, this.owner);
		for (Entity e : Wreck.victims(level, this, p, BLAST * 1.4, BLAST, this.owner, false)) {
			Vec3 off = e.position().subtract(p);
			double d = off.length();
			if (d > BLAST * 1.4) continue;
			double near = Mth.clamp(1.0 - d / BLAST, 0.0, 1.0);
			if (near > 0) e.hurtServer(level, source, Wreck.lerp(near, 10.0F, 90.0F));
			Vec3 away = d < 0.3 ? new Vec3(0, 1, 0) : off.normalize();
			Wreck.push(e, away.scale(1.0 + 4.0 * Math.max(near, 0.15)).add(0, 0.8 + near, 0));
			if (near > 0.3) e.igniteForSeconds(12.0F);
		}
		if (this.breaksBlocks) {
			BlockPos c = this.blockPosition();
			for (int i = 0; i < 70; i++) {
				double a = this.random.nextDouble() * Math.PI * 2, r = 1 + this.random.nextDouble() * CRATER;
				BlockPos top = Wreck.surface(level, c.getX() + Mth.floor(Math.cos(a) * r), c.getY() + 5, c.getZ() + Mth.floor(Math.sin(a) * r), 12);
				if (top == null) continue;
				BlockState s = level.getBlockState(top);
				if (!Wreck.canDestroy(level, top, s)) continue;
				double sp = 1 + this.random.nextDouble() * 1.8;
				Wreck.fling(level, top, s, new Vec3(Math.cos(a) * sp, 1.4 + this.random.nextDouble() * 1.6, Math.sin(a) * sp));
			}
		}
	}

	private void carve(ServerLevel level) {
		BlockPos c = this.blockPosition();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int removed = 0, checked = 0;
		while (this.cursor < HOLE.size() && removed < 1600 && checked < 8000) {
			Wreck.Cell cell = HOLE.get(this.cursor++);
			checked++;
			pos.setWithOffset(c, cell.offset());
			if (!level.isLoaded(pos)) continue;
			BlockState s = level.getBlockState(pos);
			if (!Wreck.canDestroy(level, pos, s)) continue;
			Wreck.remove(level, pos, cell.rim());
			removed++;
		}
	}

	/** The ring along the ground: as it passes, trees lose their leaves, plants go, glass breaks, the near ground burns. */
	private void wave(ServerLevel level, float reach) {
		if (reach <= this.waveDone) return;
		BlockPos c = this.blockPosition();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		while (this.column < COLUMNS.size()) {
			int[] col = COLUMNS.get(this.column);
			double r = Math.sqrt(col[0] * col[0] + col[1] * col[1]);
			if (r > reach) break;
			this.column++;
			int x = c.getX() + col[0], z = c.getZ() + col[1];
			// strip the column from well above the ground down to it
			for (int y = c.getY() + 24; y >= c.getY() - 6; y--) {
				pos.set(x, y, z);
				BlockState s = level.getBlockState(pos);
				if (s.isAir()) continue;
				if (s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN)
					|| s.is(BlockTags.IMPERMEABLE) || s.is(Blocks.GLASS_PANE) || s.is(BlockTags.SAPLINGS) || s.is(Blocks.SNOW)) {
					if (Wreck.canDestroy(level, pos, s)) Wreck.remove(level, pos, true);
					continue;
				}
				if (!s.getFluidState().isEmpty()) break;
				if (y > c.getY() + 3 && r < SCORCH * 1.4 && this.random.nextFloat() < 0.15F && Wreck.canDestroy(level, pos, s) && !s.hasBlockEntity()) {
					// walls and trunks near the middle are knocked over outward
					double sp = 0.4 + this.random.nextDouble() * 0.6;
					Wreck.fling(level, pos.immutable(), s, new Vec3(col[0] / Math.max(r, 1) * sp * 2, 0.5 + this.random.nextDouble() * 0.5, col[1] / Math.max(r, 1) * sp * 2));
					continue;
				}
				// the ground
				if (y <= c.getY() + 3 && Wreck.canDestroy(level, pos, s) && !s.hasBlockEntity()) {
					float roll = this.random.nextFloat();
					if (r < SCORCH) {
						BlockState now = r < CRATER + 3
							? (roll < 0.3F ? Blocks.MAGMA_BLOCK : roll < 0.6F ? Blocks.BLACKSTONE : Blocks.BASALT).defaultBlockState()
							: (roll < 0.5F ? Blocks.COARSE_DIRT : roll < 0.75F ? Blocks.BLACKSTONE : Blocks.ROOTED_DIRT).defaultBlockState();
						if (s.is(BlockTags.SAND)) now = Blocks.GLASS.defaultBlockState();
						level.setBlock(pos, now, 2);
						if (this.fires < 140 && roll < 0.22F && Wreck.ignite(level, pos)) this.fires++;
					} else if (s.is(Blocks.GRASS_BLOCK) && roll < 0.5F) {
						level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
					}
					break;
				}
			}
		}
		this.waveDone = reach;
	}

	private void clientTick(float t) {
		Level level = this.level();
		Vec3 c = this.position();
		if (t < GATHER) {
			float k = t / GATHER;
			// light is drawn in from all round to a point above the highest circle
			Vec3 top = c.add(0, RING_Y[RING_Y.length - 1] + 4, 0);
			for (int i = 0; i < 3 + (int) (10 * k); i++) {
				double a = this.random.nextDouble() * Math.PI * 2, r = 30 + 30 * this.random.nextDouble();
				Vec3 from = c.add(Math.cos(a) * r, 2 + this.random.nextDouble() * 40, Math.sin(a) * r);
				Vec3 v = top.subtract(from).scale(0.05 + 0.04 * k);
				level.addAlwaysVisibleParticle(i % 3 == 0 ? ParticleTypes.FLAME : ParticleTypes.END_ROD, true, from.x, from.y, from.z, v.x, v.y, v.z);
			}
			// and the grass leans in: dust drifts toward the place
			for (int i = 0; i < 4; i++) {
				double a = this.random.nextDouble() * Math.PI * 2, r = 4 + this.random.nextDouble() * 20;
				level.addParticle(ParticleTypes.ASH, c.x + Math.cos(a) * r, c.y + 0.3, c.z + Math.sin(a) * r, -Math.cos(a) * 0.3, 0.02, -Math.sin(a) * 0.3);
			}
		} else if (t >= BOOM) {
			float s = t - BOOM;
			if (s < 2) level.setSkyFlashTime(8);
			if (s < 60) {
				for (int i = 0; i < 6; i++) {
					double a = this.random.nextDouble() * Math.PI * 2, r = this.random.nextDouble() * SCORCH;
					level.addAlwaysVisibleParticle(i < 2 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME, true, c.x + Math.cos(a) * r, c.y + 0.5, c.z + Math.sin(a) * r,
						Math.cos(a) * 0.5, 0.2 + this.random.nextDouble() * 0.4, Math.sin(a) * 0.5);
				}
			}
			if (s < CLOUD - 60 && ((int) s & 1) == 0) {
				level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, c.x + this.random.nextGaussian() * 8, c.y, c.z + this.random.nextGaussian() * 8, 0, 0.12, 0);
			}
		}
	}
}

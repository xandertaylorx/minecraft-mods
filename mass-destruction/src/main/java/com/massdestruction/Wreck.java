package com.massdestruction;

import com.massdestruction.entity.EffectEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Shared pieces of destruction: what may be broken, who gets hit, and shapes of holes, nearest first. */
public final class Wreck {
	private Wreck() {
	}

	/** Blocks at least this blast-resistant (obsidian, bedrock, reinforced deepslate, the portal frame...) survive. */
	public static final float MAX_RESISTANCE = 1200.0F;

	/** Breaking blocks follows the tnt_explodes game rule, spawn protection and adventure mode, like TNT. */
	public static boolean mayBreakBlocks(ServerLevel level, @Nullable Player player, BlockPos at) {
		if (!level.getGameRules().get(GameRules.TNT_EXPLODES)) return false;
		return player == null || player.mayBuild() && level.mayInteract(player, at);
	}

	public static boolean canDestroy(ServerLevel level, BlockPos pos, BlockState state) {
		return !state.isAir() && state.getDestroySpeed(level, pos) >= 0.0F && state.getBlock().getExplosionResistance() < MAX_RESISTANCE;
	}

	/** Plain removal. Inner blocks skip neighbour updates (they are all going anyway); the rim updates so torches drop. */
	public static void remove(ServerLevel level, BlockPos pos, boolean rim) {
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), rim ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	public static void setVelocity(Entity entity, Vec3 velocity) {
		entity.setDeltaMovement(velocity);
		entity.resetFallDistance();
		if (entity instanceof ServerPlayer player) {
			player.connection.send(new ClientboundSetEntityMotionPacket(player));
		} else {
			entity.needsSync = true;
		}
	}

	/** Pushes, scaled down by knockback resistance. */
	public static void push(Entity entity, Vec3 add) {
		double resist = entity instanceof LivingEntity living ? living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 0.0;
		setVelocity(entity, entity.getDeltaMovement().add(add.scale(Math.max(0.0, 1.0 - resist))));
	}

	/** Everything in range that can be hurt or thrown, except the effect's owner (your own weapons never hit you). */
	public static List<Entity> victims(ServerLevel level, Entity self, Vec3 c, double radius, double height, @Nullable Entity owner, boolean items) {
		AABB box = new AABB(c, c).inflate(radius, height, radius);
		return level.getEntities(self, box, e -> e.isAlive() && !e.isSpectator() && e != owner
			&& !(e instanceof EffectEntity) && !(e instanceof FallingBlockEntity)
			&& (items || !(e instanceof ItemEntity) && !(e instanceof ExperienceOrb))
			&& !(e instanceof Player p && p.isCreative() && p.getAbilities().flying)
			&& (owner == null || !e.isPassengerOfSameVehicle(owner)));
	}

	public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	/** Throws a real block out of the ground (it lands as a block somewhere else). */
	public static void fling(ServerLevel level, BlockPos pos, BlockState state, Vec3 velocity) {
		if (state.hasBlockEntity() || !state.getFluidState().isEmpty()) return;
		FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
		block.dropItem = false;
		setVelocity(block, velocity);
	}

	/** Fire on top of a block, if it can stand there. */
	public static boolean ignite(ServerLevel level, BlockPos ground) {
		BlockPos above = ground.above();
		if (!level.getBlockState(above).isAir()) return false;
		BlockState fire = BaseFireBlock.getState(level, above);
		if (!fire.canSurvive(level, above)) return false;
		level.setBlock(above, fire, Block.UPDATE_ALL);
		return true;
	}

	/** The top non-air block in a column, searching down from {@code fromY}; null if none within {@code depth}. */
	public static @Nullable BlockPos surface(ServerLevel level, int x, int fromY, int z, int depth) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, fromY, z);
		for (int i = 0; i < depth; i++, pos.move(0, -1, 0)) {
			if (pos.getY() < level.getMinY()) return null;
			if (!level.getBlockState(pos).isAir()) return pos.immutable();
		}
		return null;
	}

	/** A hole shape, offsets nearest the centre first, with a ragged rim. The second value marks the rim. */
	public record Cell(BlockPos offset, boolean rim) {
	}

	private static final Map<String, List<Cell>> SHAPES = new ConcurrentHashMap<>();

	/**
	 * A ball of radius {@code r}, squashed: {@code down} > 1 makes it shallower below the centre, {@code up} > 1 lower
	 * above it. Built once per shape and shared.
	 */
	public static List<Cell> ball(int r, double down, double up, long seed) {
		return SHAPES.computeIfAbsent(r + "/" + down + "/" + up + "/" + seed, k -> {
			record Entry(BlockPos offset, double d, double edge) {
			}
			List<Entry> list = new ArrayList<>();
			java.util.Random jitter = new java.util.Random(seed);
			for (int dx = -r - 1; dx <= r + 1; dx++) {
				for (int dz = -r - 1; dz <= r + 1; dz++) {
					for (int dy = -r - 1; dy <= r + 1; dy++) {
						double v = dy < 0 ? dy * down : dy * up;
						double d = Math.sqrt(dx * dx + dz * dz + v * v);
						double edge = r + (jitter.nextDouble() - 0.5) * Math.max(1.5, r * 0.14);
						if (d <= edge) list.add(new Entry(new BlockPos(dx, dy, dz), d, edge));
					}
				}
			}
			list.sort(Comparator.comparingDouble(Entry::d));
			return list.stream().map(e -> new Cell(e.offset(), e.d() > e.edge() - 1.6)).toList();
		});
	}

	/** Columns of a disc, nearest first. */
	public static List<int[]> disc(int r) {
		List<int[]> columns = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz <= r * r) columns.add(new int[] {dx, dz});
			}
		}
		columns.sort(Comparator.comparingInt(c -> c[0] * c[0] + c[1] * c[1]));
		return columns;
	}

	public static float lerp(double t, float a, float b) {
		return Mth.lerp((float) Mth.clamp(t, 0.0, 1.0), a, b);
	}
}

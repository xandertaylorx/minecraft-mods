package com.massdestruction.world;

import com.massdestruction.MassDestruction;
import com.massdestruction.block.RiftPortalBlock;
import com.massdestruction.registry.ModBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * The way into the Rift. Build a frame of crying obsidian, any size from 2x3 inside up to 21x21, standing up like a
 * Nether portal. Then either right-click the inside with a Rift Key, or open a black hole next to it: the hole tears
 * the frame open. Going through puts you at the same x and z on the other side, on a new frame if there is none near.
 */
public final class RiftPortals {
	private RiftPortals() {
	}

	public static final ResourceKey<PoiType> POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, MassDestruction.id("rift_portal"));
	private static final int MAX_SIDE = 21;
	private static final int SEARCH = 48;

	public static void init() {
		PoiHelper.register(POI.identifier(), 0, 1, ModBlocks.RIFT_PORTAL);
	}

	private static boolean allowedIn(Level level) {
		return level.dimension() == Level.OVERWORLD || level.dimension() == MassDestruction.RIFT || level.dimension() == Level.NETHER;
	}

	/** Fills the frame around {@code inside} if it is a closed crying obsidian frame. True if it lit. */
	public static boolean tryLight(ServerLevel level, BlockPos inside) {
		if (!allowedIn(level) || !level.getBlockState(inside).isAir()) return false;
		for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
			List<BlockPos> cells = fill(level, inside, axis);
			if (cells == null) continue;
			BlockState sheet = ModBlocks.RIFT_PORTAL.defaultBlockState().setValue(RiftPortalBlock.AXIS, axis);
			for (BlockPos p : cells) level.setBlock(p, sheet, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			Vec3 c = Vec3.atCenterOf(cells.get(cells.size() / 2));
			level.playSound(null, c.x, c.y, c.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.5F, 1.2F);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 1.0F, 0.6F);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 200, 1.5, 2.0, 1.5, 0.3);
			return true;
		}
		return false;
	}

	/** Lights any crying obsidian frame near a point (used by black holes). */
	public static boolean tryLightNear(ServerLevel level, BlockPos centre, int radius) {
		if (!allowedIn(level)) return false;
		int tried = 0;
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-radius, -radius, -radius), centre.offset(radius, radius, radius))) {
			if (!level.getBlockState(p).is(Blocks.CRYING_OBSIDIAN)) continue;
			for (Direction d : new Direction[] {Direction.UP, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH}) {
				BlockPos in = p.relative(d);
				if (level.getBlockState(in).isAir() && tryLight(level, in.immutable())) return true;
			}
			if (++tried > 24) return false;
		}
		return false;
	}

	/** Flood fill of air in one vertical plane; null if it leaks, hits anything but crying obsidian, or is too big. */
	private static List<BlockPos> fill(ServerLevel level, BlockPos start, Direction.Axis axis) {
		Direction a = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
		Direction[] dirs = {Direction.UP, Direction.DOWN, a, a.getOpposite()};
		List<BlockPos> cells = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> todo = new ArrayDeque<>();
		todo.add(start);
		seen.add(start);
		int minA = Integer.MAX_VALUE, maxA = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		while (!todo.isEmpty()) {
			BlockPos p = todo.poll();
			BlockState s = level.getBlockState(p);
			if (s.is(Blocks.CRYING_OBSIDIAN)) continue;
			if (!s.isAir()) return null;
			cells.add(p);
			int along = axis == Direction.Axis.X ? p.getX() : p.getZ();
			minA = Math.min(minA, along);
			maxA = Math.max(maxA, along);
			minY = Math.min(minY, p.getY());
			maxY = Math.max(maxY, p.getY());
			if (maxA - minA >= MAX_SIDE || maxY - minY >= MAX_SIDE || cells.size() > MAX_SIDE * MAX_SIDE) return null;
			for (Direction d : dirs) {
				BlockPos n = p.relative(d);
				if (seen.add(n)) todo.add(n);
			}
		}
		if (maxA - minA < 1 || maxY - minY < 2) return null;   // at least 2 wide and 3 tall inside
		return cells;
	}

	// ------------------------------------------------------------------ crossing over

	public static TeleportTransition destination(ServerLevel from, Entity entity, BlockPos at) {
		ResourceKey<Level> toKey = from.dimension() == MassDestruction.RIFT ? Level.OVERWORLD : MassDestruction.RIFT;
		ServerLevel to = from.getServer().getLevel(toKey);
		if (to == null) to = from;
		final ServerLevel dest = to;
		double scale = from.dimension() == Level.NETHER ? 8.0 : 1.0;
		BlockPos aim = dest.getWorldBorder().clampToBounds(entity.getX() * scale, entity.getY(), entity.getZ() * scale);
		BlockPos exit = findPortal(dest, aim).orElseGet(() -> buildPortal(dest, aim, from.getBlockState(at).getOptionalValue(RiftPortalBlock.AXIS).orElse(Direction.Axis.X)));
		// stand at the bottom of the sheet, in its middle
		BlockPos foot = exit;
		while (to.getBlockState(foot.below()).is(ModBlocks.RIFT_PORTAL)) foot = foot.below();
		Vec3 pos = Vec3.atBottomCenterOf(foot);
		return new TeleportTransition(to, pos, Vec3.ZERO, entity.getYRot(), entity.getXRot(),
			TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET));
	}

	private static Optional<BlockPos> findPortal(ServerLevel level, BlockPos near) {
		PoiManager poi = level.getPoiManager();
		poi.ensureLoadedAndValid(level, near, SEARCH);
		return poi.findClosest(h -> h.is(POI), p -> level.getBlockState(p).is(ModBlocks.RIFT_PORTAL), near, SEARCH, PoiManager.Occupancy.ANY);
	}

	/**
	 * A new 2x3 frame where you arrive. In the Rift there may be nothing but sky below, so it stands on a small island of
	 * its own; in the Overworld it stands on the ground.
	 */
	private static BlockPos buildPortal(ServerLevel level, BlockPos near, Direction.Axis axis) {
		level.getChunk(near.getX() >> 4, near.getZ() >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX(), near.getZ());
		boolean island = y <= level.getMinY() + 2;
		if (island) y = level.dimension() == MassDestruction.RIFT ? 110 : 80;
		y = Math.min(y, level.getMaxY() - 8);
		Direction along = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
		Direction across = along.getClockWise();
		BlockPos base = new BlockPos(near.getX(), y, near.getZ());
		// clear room to stand, and a floor under it
		for (int i = -2; i <= 3; i++) {
			for (int j = -2; j <= 2; j++) {
				for (int h = 0; h <= 5; h++) {
					BlockPos p = base.relative(along, i).relative(across, j).above(h);
					if (level.getBlockState(p).getDestroySpeed(level, p) >= 0) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
				BlockPos floor = base.relative(along, i).relative(across, j).below();
				if (island || level.getBlockState(floor).canBeReplaced()) {
					level.setBlock(floor, (Math.abs(i) + Math.abs(j)) % 3 == 0 ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.CALCITE.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
		if (island) {
			// a rough cone hanging under the floor, so it looks like a shard of the Rift and not a slab
			for (int d = 2; d <= 6; d++) {
				int r = 4 - d / 2;
				for (int i = -r; i <= r; i++) {
					for (int j = -r; j <= r; j++) {
						if (i * i + j * j > r * r + 1) continue;
						BlockPos p = base.relative(along, i).relative(across, j).below(d);
						level.setBlock(p, d > 4 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.TUFF.defaultBlockState(), Block.UPDATE_ALL);
					}
				}
			}
		}
		// frame: 4 wide, 5 tall
		BlockState frame = Blocks.CRYING_OBSIDIAN.defaultBlockState();
		BlockState sheet = ModBlocks.RIFT_PORTAL.defaultBlockState().setValue(RiftPortalBlock.AXIS, axis);
		for (int i = -1; i <= 2; i++) {
			for (int h = -1; h <= 3; h++) {
				BlockPos p = base.relative(along, i).above(h);
				boolean edge = i == -1 || i == 2 || h == -1 || h == 3;
				level.setBlock(p, edge ? frame : sheet, edge ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
		}
		return base;
	}
}

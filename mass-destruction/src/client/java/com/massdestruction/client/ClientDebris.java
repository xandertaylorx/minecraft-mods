package com.massdestruction.client;

import com.massdestruction.DebrisPayload;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks torn out of the ground, kept on the client per effect (black hole or tornado) and drawn by that effect's
 * renderer, which decides how they move. The server only says what was taken and from where.
 */
public final class ClientDebris {
	private ClientDebris() {
	}

	public static final class Piece {
		public final BlockState state;
		public final double x, y, z;
		/** Game time it was taken at. */
		public final float born;
		/** Random numbers fixed for this piece: orbit, spin axis, speed... */
		public final float r1, r2, r3, r4;

		Piece(BlockState state, double x, double y, double z, float born, RandomSource random) {
			this.state = state;
			this.x = x;
			this.y = y;
			this.z = z;
			this.born = born;
			this.r1 = random.nextFloat();
			this.r2 = random.nextFloat();
			this.r3 = random.nextFloat();
			this.r4 = random.nextFloat();
		}
	}

	private static final Int2ObjectOpenHashMap<List<Piece>> BY_EFFECT = new Int2ObjectOpenHashMap<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static final int MAX = 320;

	public static void receive(DebrisPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		float now = mc.level.getGameTime();
		List<Piece> list = BY_EFFECT.computeIfAbsent(payload.entityId(), k -> new ArrayList<>());
		for (DebrisPayload.Piece p : payload.pieces()) {
			list.add(new Piece(p.blockState(), p.x() + 0.5, p.y() + 0.5, p.z() + 0.5, now, RANDOM));
		}
		while (list.size() > MAX) list.remove(0);
	}

	public static List<Piece> of(int entityId) {
		List<Piece> list = BY_EFFECT.get(entityId);
		return list == null ? List.of() : list;
	}

	/** Forgets pieces older than {@code ticks} for an effect. */
	public static void prune(int entityId, float now, float ticks) {
		List<Piece> list = BY_EFFECT.get(entityId);
		if (list != null) list.removeIf(p -> now - p.born > ticks);
	}

	/** Drops everything for effects that no longer exist. */
	public static void retain(java.util.function.IntPredicate alive) {
		BY_EFFECT.keySet().removeIf(id -> !alive.test(id));
	}

	public static void clear() {
		BY_EFFECT.clear();
	}
}

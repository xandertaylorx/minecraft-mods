package com.petdragons.block;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** How long each egg has been warm toward its next stage. Lost on restart, which only costs a few seconds of warmth. */
final class Warmth {
	private static final Map<Long, Integer> MAP = new HashMap<>();

	private Warmth() {
	}

	private static long key(ServerLevel level, BlockPos pos) {
		return pos.asLong() * 31L + level.dimension().identifier().hashCode();
	}

	static int get(ServerLevel level, BlockPos pos) {
		return MAP.getOrDefault(key(level, pos), 0);
	}

	static void set(ServerLevel level, BlockPos pos, int v) {
		if (v <= 0) MAP.remove(key(level, pos));
		else MAP.put(key(level, pos), v);
	}
}

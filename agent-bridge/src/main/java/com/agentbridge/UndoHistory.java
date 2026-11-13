package com.agentbridge;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Remembers what blocks looked like before each agent action, so it can be put back. Server thread only. */
public final class UndoHistory {
	/** Block flags used for every agent edit: tell clients, and don't spill container contents. Same as /fill. */
	public static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
	private static final int MAX_STORED_BLOCKS = 4_000_000;

	public record Action(String label, ResourceKey<Level> dimension, Map<Long, BlockState> before) {}

	private final Deque<Action> actions = new ArrayDeque<>();
	private int stored;

	/** Collects the old blocks for one tool call. */
	public static final class Recorder {
		private final ServerLevel level;
		private final Map<Long, BlockState> before = new LinkedHashMap<>();
		private int changed;

		public Recorder(ServerLevel level) {
			this.level = level;
		}

		/** Places a block and remembers what was there. Returns true if the block changed. */
		public boolean set(BlockPos pos, BlockState state) {
			BlockState old = level.getBlockState(pos);
			if (old == state) return false;
			if (!level.setBlock(pos, state, PLACE_FLAGS)) return false;
			before.putIfAbsent(pos.asLong(), old);
			changed++;
			return true;
		}

		public int changed() {
			return changed;
		}

		/** Runs neighbour updates once everything is placed, like /fill does. */
		public void finish() {
			for (Map.Entry<Long, BlockState> e : before.entrySet()) {
				level.updateNeighboursOnBlockSet(BlockPos.of(e.getKey()), e.getValue());
			}
		}
	}

	public synchronized void push(String label, Recorder recorder) {
		if (recorder.before.isEmpty()) return;
		actions.push(new Action(label, recorder.level.dimension(), recorder.before));
		stored += recorder.before.size();
		while (actions.size() > Math.max(1, BridgeConfig.INSTANCE.undoHistory) || (stored > MAX_STORED_BLOCKS && actions.size() > 1)) {
			stored -= actions.removeLast().before.size();
		}
	}

	/** Undoes up to {@code steps} actions. Returns a description of what was undone. */
	public synchronized String undo(MinecraftServer server, int steps) {
		if (actions.isEmpty()) return "Nothing to undo.";
		StringBuilder out = new StringBuilder();
		int done = 0;
		while (done < steps && !actions.isEmpty()) {
			Action a = actions.pop();
			stored -= a.before.size();
			ServerLevel level = server.getLevel(a.dimension);
			if (level == null) continue;
			Recorder r = new Recorder(level);
			for (Map.Entry<Long, BlockState> e : a.before.entrySet()) {
				r.set(BlockPos.of(e.getKey()), e.getValue());
			}
			r.finish();
			out.append("Undid \"").append(a.label).append("\" (").append(a.before.size()).append(" blocks restored)\n");
			done++;
		}
		out.append(actions.size()).append(" action(s) left to undo.");
		return out.toString();
	}

	public synchronized int size() {
		return actions.size();
	}

	public synchronized void clear() {
		actions.clear();
		stored = 0;
	}
}

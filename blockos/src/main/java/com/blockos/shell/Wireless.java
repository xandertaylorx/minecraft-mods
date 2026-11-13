package com.blockos.shell;

import com.blockos.block.ComputerBlockEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Wireless messages between computers on numbered channels. Works across any distance and dimension. */
public final class Wireless {
	private static final Map<GlobalPos, Integer> LISTENERS = new HashMap<>();

	private Wireless() {
	}

	public static void listen(ServerLevel level, BlockPos pos, int channel) {
		LISTENERS.put(GlobalPos.of(level.dimension(), pos.immutable()), channel);
	}

	public static void forget(ServerLevel level, BlockPos pos) {
		LISTENERS.remove(GlobalPos.of(level.dimension(), pos));
	}

	/** Returns how many computers got the message. */
	public static int send(ServerLevel from, BlockPos sender, int channel, String senderName, String message) {
		MinecraftServer server = from.getServer();
		GlobalPos self = GlobalPos.of(from.dimension(), sender);
		int delivered = 0;
		for (Map.Entry<GlobalPos, Integer> e : Map.copyOf(LISTENERS).entrySet()) {
			if (e.getValue() != channel || e.getKey().equals(self)) continue;
			ServerLevel level = server.getLevel(e.getKey().dimension());
			if (level == null || !level.isLoaded(e.getKey().pos())) continue;
			if (level.getBlockEntity(e.getKey().pos()) instanceof ComputerBlockEntity c) {
				c.receive(channel, senderName, message);
				delivered++;
			} else {
				LISTENERS.remove(e.getKey());
			}
		}
		return delivered;
	}
}

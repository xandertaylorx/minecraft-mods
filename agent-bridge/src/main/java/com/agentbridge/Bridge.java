package com.agentbridge;

import com.agentbridge.mcp.McpHttpServer;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** The on/off state of the bridge for the running server. */
public final class Bridge {
	private static MinecraftServer server;
	private static McpHttpServer http;
	private static UUID ownerId;
	private static final UndoHistory UNDO = new UndoHistory();
	private static volatile String clientName = "Agent";

	private Bridge() {}

	public static synchronized boolean isRunning() {
		return http != null;
	}

	public static synchronized void start(MinecraftServer srv, ServerPlayer owner) throws Exception {
		if (http != null) {
			ownerId = owner.getUUID();
			return;
		}
		McpHttpServer created = new McpHttpServer(srv, BridgeConfig.INSTANCE);
		created.start();
		http = created;
		server = srv;
		ownerId = owner.getUUID();
		if (AgentBridge.clientHooks != null) AgentBridge.clientHooks.onBridgeToggled(true);
		AgentBridge.LOGGER.info("Agent Bridge listening on {}", BridgeConfig.INSTANCE.url());
	}

	public static synchronized void stop(MinecraftServer srv) {
		if (http == null || (srv != null && srv != server)) return;
		com.agentbridge.chat.AgentChat.stopAll();
		http.stop();
		http = null;
		server = null;
		UNDO.clear();
		if (AgentBridge.clientHooks != null) AgentBridge.clientHooks.onBridgeToggled(false);
		AgentBridge.LOGGER.info("Agent Bridge stopped");
	}

	public static UndoHistory undo() {
		return UNDO;
	}

	/** The player the agent acts for: whoever turned the bridge on, or the first player online. */
	public static ServerPlayer player(MinecraftServer srv) {
		if (ownerId != null) {
			ServerPlayer p = srv.getPlayerList().getPlayer(ownerId);
			if (p != null) return p;
		}
		List<ServerPlayer> players = srv.getPlayerList().getPlayers();
		return players.isEmpty() ? null : players.getFirst();
	}

	public static void setClientName(String name) {
		if (name != null && !name.isBlank()) clientName = name.length() > 32 ? name.substring(0, 32) : name;
	}

	public static String clientName() {
		return clientName;
	}

	/** Shows a short line in the action bar of the player the agent works for. Call on the server thread. */
	public static void activity(MinecraftServer srv, String text) {
		if (!BridgeConfig.INSTANCE.showActivity) return;
		ServerPlayer p = player(srv);
		if (p != null) {
			p.sendOverlayMessage(Component.literal("🤖 " + clientName + ": ").withStyle(ChatFormatting.AQUA)
				.append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
		}
	}
}

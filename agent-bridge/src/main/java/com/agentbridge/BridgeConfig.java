package com.agentbridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import net.fabricmc.loader.api.FabricLoader;

/** Stored in config/agent-bridge.json. The token stays the same between launches so you only connect your AI once. */
public final class BridgeConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("agent-bridge.json");

	public static BridgeConfig INSTANCE = new BridgeConfig();

	/** Port the MCP server listens on. It only ever binds to 127.0.0.1. */
	public int port = 25599;
	/** Secret the AI client must send as "Authorization: Bearer <token>". */
	public String token = "";
	/** Most blocks a single tool call may change. */
	public int maxBlocksPerCall = 200_000;
	/** Biggest box scan_area may read. */
	public int maxScanVolume = 32_768;
	/** How many build actions undo remembers. */
	public int undoHistory = 50;
	/** Allow the run_command tool. Commands run with game-master permission (no /op, /stop, /ban). */
	public boolean allowCommands = true;
	/** Show what the agent is doing in the action bar. */
	public boolean showActivity = true;

	// ---- In-game chat (@claude / @codex) ----
	/** Allow talking to Claude Code and Codex from Minecraft chat. */
	public boolean chatEnabled = true;
	/** Full path to the claude command. Empty means find it automatically. */
	public String claudePath = "";
	/** Full path to the codex command. Empty means find it automatically. */
	public String codexPath = "";
	/** Model for Claude Code, e.g. "sonnet" or "opus". Empty uses your Claude Code default. */
	public String claudeModel = "";
	/** Model for Codex. Empty uses your Codex default. */
	public String codexModel = "";
	/** Show each tool the AI uses as a grey chat line. */
	public boolean showToolCalls = true;
	/** Stop an AI job that runs longer than this. */
	public int chatTimeoutMinutes = 20;
	/** Most steps Claude may take for one message. */
	public int claudeMaxTurns = 60;

	public static void load() {
		try {
			if (Files.exists(FILE)) {
				BridgeConfig loaded = GSON.fromJson(Files.readString(FILE), BridgeConfig.class);
				if (loaded != null) INSTANCE = loaded;
			}
		} catch (Exception e) {
			AgentBridge.LOGGER.warn("Could not read {}, using defaults", FILE, e);
		}
		if (INSTANCE.token == null || INSTANCE.token.length() < 16) {
			INSTANCE.token = newToken();
		}
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(INSTANCE));
		} catch (IOException e) {
			AgentBridge.LOGGER.warn("Could not write {}", FILE, e);
		}
	}

	public static String newToken() {
		byte[] bytes = new byte[24];
		new SecureRandom().nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

	public String url() {
		return "http://127.0.0.1:" + port + "/mcp";
	}

	public String claudeCommand() {
		return "claude mcp add --transport http minecraft " + url() + " --header \"Authorization: Bearer " + token + "\"";
	}

	public String codexConfig() {
		return "[mcp_servers.minecraft]\nurl = \"" + url() + "\"\nhttp_headers = { Authorization = \"Bearer " + token + "\" }\n";
	}
}

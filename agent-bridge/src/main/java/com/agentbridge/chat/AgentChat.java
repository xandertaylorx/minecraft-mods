package com.agentbridge.chat;

import com.agentbridge.AgentBridge;
import com.agentbridge.Bridge;
import com.agentbridge.BridgeConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lets players talk to Claude Code and Codex from Minecraft chat ("@claude build a tower" or "/claude ...").
 * Each message runs the AI's own command-line tool in the background, already connected to this world
 * through the bridge, and its replies are posted back into chat. Conversations carry on until "@claude new".
 */
public final class AgentChat {
	public enum Kind {
		CLAUDE("Claude", "claude", ChatFormatting.GOLD),
		CODEX("Codex", "codex", ChatFormatting.GREEN);

		public final String displayName;
		public final String command;
		final ChatFormatting color;

		Kind(String displayName, String command, ChatFormatting color) {
			this.displayName = displayName;
			this.command = command;
			this.color = color;
		}
	}

	private static final class Job {
		volatile Process process;
		volatile boolean stopped;
	}

	private static final Map<Kind, Job> JOBS = new EnumMap<>(Kind.class);
	private static final Map<Kind, String> SESSIONS = new EnumMap<>(Kind.class);
	private static final Deque<String> RECENT = new ArrayDeque<>();

	private AgentChat() {}

	// ---------------------------------------------------------------- entry points

	/** Handles one chat message to an AI. Call on the server thread. */
	public static void handle(MinecraftServer server, ServerPlayer player, Kind kind, String raw) {
		String message = raw == null ? "" : raw.trim();
		String lower = message.toLowerCase(Locale.ROOT);
		if (message.isEmpty() || lower.equals("help")) {
			help(player, kind);
			return;
		}
		if (lower.equals("stop") || lower.equals("cancel")) {
			info(server, kind, stop(kind) ? "Stopped." : "I'm not working on anything right now.");
			return;
		}
		if (lower.equals("new") || lower.equals("reset") || lower.equals("clear")) {
			synchronized (SESSIONS) {
				SESSIONS.remove(kind);
			}
			info(server, kind, "Starting a fresh conversation. I've forgotten what we talked about.");
			return;
		}
		if (!BridgeConfig.INSTANCE.chatEnabled) {
			player.sendSystemMessage(Component.literal("Chatting with AI is turned off (chatEnabled in config/agent-bridge.json).").withStyle(ChatFormatting.RED));
			return;
		}
		if (isBusy(kind)) {
			player.sendSystemMessage(Component.literal(kind.displayName + " is still working on your last message. Say @" + kind.command
				+ " stop to cancel it.").withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (!Bridge.isRunning()) {
			try {
				Bridge.start(server, player);
				player.sendSystemMessage(Component.literal("🤖 Turned Agent Bridge on so " + kind.displayName + " can reach the game. /agent off turns it off.")
					.withStyle(ChatFormatting.GRAY));
			} catch (Exception e) {
				player.sendSystemMessage(Component.literal("Could not start Agent Bridge: " + e.getMessage()).withStyle(ChatFormatting.RED));
				return;
			}
		}

		Job job = new Job();
		synchronized (JOBS) {
			JOBS.put(kind, job);
		}
		String playerName = player.getName().getString();
		info(server, kind, "On it… (say @" + kind.command + " stop to cancel)");
		Thread t = new Thread(() -> run(server, kind, job, playerName, message), "AgentBridge-" + kind.displayName);
		t.setDaemon(true);
		t.start();
	}

	public static boolean isBusy(Kind kind) {
		synchronized (JOBS) {
			return JOBS.containsKey(kind);
		}
	}

	/** Cancels a running job. Returns true if there was one. */
	public static boolean stop(Kind kind) {
		Job job;
		synchronized (JOBS) {
			job = JOBS.get(kind);
		}
		if (job == null) return false;
		job.stopped = true;
		Process p = job.process;
		if (p != null) {
			p.descendants().forEach(ProcessHandle::destroy);
			p.destroy();
		}
		return true;
	}

	public static void stopAll() {
		for (Kind k : Kind.values()) stop(k);
	}

	/** Last lines posted to chat, for tests. */
	public static List<String> recentLines() {
		synchronized (RECENT) {
			return new ArrayList<>(RECENT);
		}
	}

	// ---------------------------------------------------------------- running the CLI

	private static void run(MinecraftServer server, Kind kind, Job job, String playerName, String message) {
		Output out = new Output(server, kind);
		try {
			BridgeConfig cfg = BridgeConfig.INSTANCE;
			String exe = ShellEnv.find(kind.command, kind == Kind.CLAUDE ? cfg.claudePath : cfg.codexPath);
			if (exe == null) {
				out.error(kind == Kind.CLAUDE
					? "I couldn't find Claude Code on this computer. Install it from claude.com/claude-code and log in, or set claudePath in config/agent-bridge.json."
					: "I couldn't find Codex on this computer. Install it (npm i -g @openai/codex) and log in, or set codexPath in config/agent-bridge.json.");
				return;
			}

			Path dir = FabricLoader.getInstance().getConfigDir().resolve("agent-bridge");
			Path workspace = dir.resolve("workspace");
			Files.createDirectories(workspace);
			String session;
			synchronized (SESSIONS) {
				session = SESSIONS.get(kind);
			}
			String instructions = instructions(kind, playerName);
			List<String> args;
			String prompt;
			if (kind == Kind.CLAUDE) {
				Path mcp = dir.resolve("claude-mcp.json");
				Files.writeString(mcp, claudeMcpJson(cfg));
				args = new ArrayList<>(List.of(
					"-p", "--output-format", "stream-json", "--verbose",
					"--mcp-config", mcp.toString(), "--strict-mcp-config",
					"--tools", "",
					"--allowedTools", "mcp__minecraft__*",
					"--append-system-prompt", instructions,
					"--max-turns", String.valueOf(Math.max(1, cfg.claudeMaxTurns))));
				if (!cfg.claudeModel.isBlank()) args.addAll(List.of("--model", cfg.claudeModel));
				if (session != null) args.addAll(List.of("--resume", session));
				prompt = message;
			} else {
				args = new ArrayList<>(List.of(
					"exec", "--json", "--skip-git-repo-check",
					"-C", workspace.toString(),
					"-s", "read-only",
					"-c", "approval_policy=\"never\"",
					"-c", "mcp_servers.minecraft.url=\"" + cfg.url() + "\"",
					"-c", "mcp_servers.minecraft.http_headers={ Authorization = \"Bearer " + cfg.token + "\" }",
					"-c", "mcp_servers.minecraft.default_tools_approval_mode=\"approve\"",
					"-c", "mcp_servers.minecraft.tool_timeout_sec=120"));
				if (!cfg.codexModel.isBlank()) args.addAll(List.of("-m", cfg.codexModel));
				if (session != null) args.addAll(List.of("resume", session));
				args.add("-");
				// Codex has no flag for extra instructions, so they go at the top of the first message.
				prompt = session == null ? instructions + "\n\nThe player says: " + message : message;
			}

			ProcessBuilder pb = new ProcessBuilder(ShellEnv.command(exe, args)).directory(workspace.toFile());
			Map<String, String> env = pb.environment();
			env.put("PATH", ShellEnv.path());
			// If the game itself was started from inside Claude Code, don't let the child think it's nested.
			env.remove("CLAUDECODE");
			env.remove("CLAUDE_CODE_ENTRYPOINT");
			env.remove("CLAUDE_CODE_SSE_PORT");

			if (job.stopped) return;
			Process process = pb.start();
			job.process = process;
			try (OutputStream stdin = process.getOutputStream()) {
				stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
			}

			StringBuilder stderr = new StringBuilder();
			Thread errReader = new Thread(() -> {
				try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
					String line;
					while ((line = r.readLine()) != null) {
						synchronized (stderr) {
							stderr.append(line).append('\n');
							if (stderr.length() > 8000) stderr.delete(0, stderr.length() - 4000);
						}
					}
				} catch (Exception ignored) {
				}
			}, "AgentBridge-" + kind.displayName + "-stderr");
			errReader.setDaemon(true);
			errReader.start();

			Thread watchdog = new Thread(() -> {
				try {
					if (!process.waitFor(Math.max(1, cfg.chatTimeoutMinutes), TimeUnit.MINUTES)) {
						out.error("That took longer than " + cfg.chatTimeoutMinutes + " minutes, so I stopped.");
						job.stopped = true;
						process.descendants().forEach(ProcessHandle::destroy);
						process.destroy();
					}
				} catch (InterruptedException ignored) {
				}
			}, "AgentBridge-" + kind.displayName + "-timeout");
			watchdog.setDaemon(true);
			watchdog.start();

			try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = r.readLine()) != null) {
					if (job.stopped) continue;
					JsonObject event;
					try {
						JsonElement el = JsonParser.parseString(line);
						if (!el.isJsonObject()) continue;
						event = el.getAsJsonObject();
					} catch (Exception notJson) {
						continue;
					}
					if (kind == Kind.CLAUDE) claudeEvent(kind, event, out);
					else codexEvent(kind, event, out);
				}
			}
			int code = process.waitFor();
			errReader.join(2000);
			watchdog.interrupt();
			if (job.stopped) return;
			if (code != 0 && !out.errored) {
				String tail;
				synchronized (stderr) {
					tail = lastLines(stderr.toString(), 3);
				}
				out.error("Something went wrong (exit code " + code + ")" + (tail.isBlank() ? "." : ": " + tail));
			} else if (!out.saidAnything && !out.errored) {
				out.say("Done.");
			}
		} catch (Exception e) {
			AgentBridge.LOGGER.warn("{} chat failed", kind.displayName, e);
			if (!job.stopped) out.error("Something went wrong: " + e.getMessage());
		} finally {
			synchronized (JOBS) {
				JOBS.remove(kind, job);
			}
		}
	}

	/** Claude Code stream-json: system/init, assistant (text + tool_use), result. */
	private static void claudeEvent(Kind kind, JsonObject e, Output out) {
		String type = str(e, "type");
		if (e.has("session_id")) remember(kind, str(e, "session_id"));
		switch (type) {
			case "assistant" -> {
				JsonObject msg = e.has("message") && e.get("message").isJsonObject() ? e.getAsJsonObject("message") : null;
				if (msg == null || !msg.has("content") || !msg.get("content").isJsonArray()) return;
				for (JsonElement c : msg.getAsJsonArray("content")) {
					if (!c.isJsonObject()) continue;
					JsonObject block = c.getAsJsonObject();
					switch (str(block, "type")) {
						case "text" -> out.say(str(block, "text"));
						case "tool_use" -> out.tool(str(block, "name"));
						default -> {
						}
					}
				}
			}
			case "result" -> {
				boolean failed = (e.has("is_error") && e.get("is_error").getAsBoolean()) || !"success".equals(str(e, "subtype"));
				String text = str(e, "result");
				if (failed) {
					String sub = str(e, "subtype");
					if (sub.equals("error_max_turns")) out.error("I hit my step limit before finishing. Say \"@claude keep going\" to continue.");
					else out.error(text.isBlank() ? "Claude Code reported an error (" + sub + ")." : text);
				} else {
					out.say(text);
				}
			}
			default -> {
			}
		}
	}

	/** Codex exec --json: thread.started, item.started/completed, turn.failed, error. */
	private static void codexEvent(Kind kind, JsonObject e, Output out) {
		String type = str(e, "type");
		if (e.has("thread_id")) remember(kind, str(e, "thread_id"));
		switch (type) {
			case "item.started", "item.completed" -> {
				if (!e.has("item") || !e.get("item").isJsonObject()) return;
				JsonObject item = e.getAsJsonObject("item");
				String itemType = str(item, "type");
				if (type.equals("item.completed") && itemType.equals("agent_message")) {
					out.say(str(item, "text"));
				} else if (type.equals("item.started") && itemType.contains("mcp")) {
					String tool = str(item, "tool");
					if (tool.isBlank()) tool = str(item, "name");
					out.tool(tool);
				} else if (type.equals("item.completed") && itemType.equals("error")) {
					out.error(str(item, "message"));
				}
			}
			case "turn.failed" -> {
				String msg = e.has("error") && e.get("error").isJsonObject() ? str(e.getAsJsonObject("error"), "message") : str(e, "message");
				out.error(msg.isBlank() ? "Codex couldn't finish that." : msg);
			}
			case "error" -> {
				String msg = str(e, "message");
				if (!msg.isBlank()) out.error(msg);
			}
			default -> {
			}
		}
	}

	private static void remember(Kind kind, String session) {
		if (session == null || session.isBlank()) return;
		synchronized (SESSIONS) {
			SESSIONS.put(kind, session);
		}
	}

	// ---------------------------------------------------------------- chat output

	/** Posts what one AI job says. Thread-safe; messages are sent on the server thread. */
	private static final class Output {
		private final MinecraftServer server;
		private final Kind kind;
		private final Set<String> said = new HashSet<>();
		volatile boolean saidAnything;
		volatile boolean errored;

		Output(MinecraftServer server, Kind kind) {
			this.server = server;
			this.kind = kind;
		}

		void say(String text) {
			String clean = clean(text);
			if (clean.isBlank() || !said.add(clean)) return; // Claude repeats its last message as the final result
			saidAnything = true;
			for (String line : splitLines(clean)) {
				record(kind.displayName + ": " + line);
				server.execute(() -> server.getPlayerList().broadcastSystemMessage(Component.literal("<" + kind.displayName + "> ").withStyle(kind.color)
					.append(Component.literal(line).withStyle(ChatFormatting.WHITE)), false));
			}
		}

		void tool(String name) {
			if (name == null || name.isBlank()) return;
			String shortName = name.startsWith("mcp__minecraft__") ? name.substring("mcp__minecraft__".length()) : name;
			record(kind.displayName + " tool: " + shortName);
			if (!BridgeConfig.INSTANCE.showToolCalls) return;
			server.execute(() -> server.getPlayerList().broadcastSystemMessage(
				Component.literal("  ⚒ " + shortName.replace('_', ' ')).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false));
		}

		void error(String text) {
			if (errored) return;
			errored = true;
			String clean = clean(text);
			record(kind.displayName + " error: " + clean);
			server.execute(() -> server.getPlayerList().broadcastSystemMessage(Component.literal("<" + kind.displayName + "> ").withStyle(kind.color)
				.append(Component.literal(clean).withStyle(ChatFormatting.RED)), false));
		}
	}

	private static void info(MinecraftServer server, Kind kind, String text) {
		record(kind.displayName + " info: " + text);
		server.getPlayerList().broadcastSystemMessage(Component.literal("<" + kind.displayName + "> ").withStyle(kind.color)
			.append(Component.literal(text).withStyle(ChatFormatting.GRAY)), false);
	}

	private static void help(ServerPlayer player, Kind kind) {
		String c = "@" + kind.command;
		player.sendSystemMessage(Component.literal("Talk to " + kind.displayName + " in chat:").withStyle(kind.color)
			.append(Component.literal("\n  " + c + " <message>  ask it anything, like \"" + c + " build a lighthouse\"\n  " + c
				+ " stop  cancel what it's doing\n  " + c + " new  start a fresh conversation\nYou can also use /" + kind.command + " <message>.")
				.withStyle(ChatFormatting.GRAY)));
	}

	private static void record(String line) {
		AgentBridge.LOGGER.info("[chat] {}", line);
		synchronized (RECENT) {
			RECENT.addLast(line);
			while (RECENT.size() > 200) RECENT.removeFirst();
		}
	}

	/** Makes AI text fit Minecraft chat: no markdown, a few short lines. */
	static String clean(String text) {
		if (text == null) return "";
		String s = text.replace("\r", "")
			.replace("**", "").replace("__", "").replace("`", "")
			.replaceAll("(?m)^#{1,6}\\s*", "")
			.replaceAll("(?m)^\\s*[*-]\\s+", "• ")
			.replaceAll("\\[([^\\]]+)\\]\\((https?://[^)]+)\\)", "$1 ($2)")
			.replaceAll("\n{2,}", "\n")
			.trim();
		return s;
	}

	private static List<String> splitLines(String text) {
		List<String> lines = new ArrayList<>();
		for (String line : text.split("\n")) {
			if (line.isBlank()) continue;
			if (lines.size() == 8) {
				lines.add("…");
				break;
			}
			lines.add(line.length() > 500 ? line.substring(0, 500) + "…" : line);
		}
		return lines;
	}

	private static String lastLines(String text, int n) {
		String[] lines = text.strip().split("\n");
		StringBuilder s = new StringBuilder();
		for (int i = Math.max(0, lines.length - n); i < lines.length; i++) {
			if (!s.isEmpty()) s.append(' ');
			s.append(lines[i].strip());
		}
		return s.length() > 400 ? s.substring(s.length() - 400) : s.toString();
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	private static String instructions(Kind kind, String playerName) {
		return "You are " + kind.displayName + ", talking with the Minecraft player " + playerName + " through in-game chat. "
			+ "You control their live world with the minecraft MCP tools (get_status, get_surface, scan_area, build_structure, fill, set_blocks, undo, "
			+ "run_command, move_player, screenshot and more). Everything you write is shown in Minecraft chat, so keep replies to one to three short, "
			+ "friendly sentences of plain text: no markdown, no code, no lists, no tables. Don't use the say tool, because your replies already appear in chat. "
			+ "Act instead of describing: call get_status first to find the player, check the terrain before building, then build. "
			+ "When you finish, say briefly what you did and where. If a request is unclear, make a sensible choice rather than asking lots of questions.";
	}

	private static String claudeMcpJson(BridgeConfig cfg) {
		JsonObject headers = new JsonObject();
		headers.addProperty("Authorization", "Bearer " + cfg.token);
		JsonObject server = new JsonObject();
		server.addProperty("type", "http");
		server.addProperty("url", cfg.url());
		server.add("headers", headers);
		JsonObject servers = new JsonObject();
		servers.add("minecraft", server);
		JsonObject root = new JsonObject();
		root.add("mcpServers", servers);
		return root.toString();
	}
}

package com.agentbridge.mcp;

import com.agentbridge.AgentBridge;
import com.agentbridge.Bridge;
import com.agentbridge.BridgeConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.server.MinecraftServer;

/**
 * A minimal MCP server over the "Streamable HTTP" transport (JSON responses, no SSE stream).
 * Listens on 127.0.0.1 only and requires a bearer token.
 */
public final class McpHttpServer {
	private static final List<String> PROTOCOL_VERSIONS = List.of("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05");
	private static final int MAX_BODY = 16 * 1024 * 1024;

	private final MinecraftServer server;
	private final BridgeConfig config;
	private final Tools tools;
	private HttpServer http;
	private ExecutorService executor;

	public McpHttpServer(MinecraftServer server, BridgeConfig config) {
		this.server = server;
		this.config = config;
		this.tools = new Tools(server);
	}

	public void start() throws IOException {
		http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), config.port), 16);
		executor = Executors.newFixedThreadPool(4, r -> {
			Thread t = new Thread(r, "AgentBridge-HTTP");
			t.setDaemon(true);
			return t;
		});
		http.setExecutor(executor);
		http.createContext("/mcp", this::handle);
		http.createContext("/", ex -> send(ex, 404, "text/plain", "Agent Bridge: the MCP endpoint is /mcp"));
		http.start();
	}

	public void stop() {
		if (http != null) http.stop(0);
		if (executor != null) executor.shutdownNow();
	}

	private void handle(HttpExchange ex) throws IOException {
		try {
			if (!originAllowed(ex)) {
				send(ex, 403, "application/json", "{\"error\":\"Origin not allowed\"}");
				return;
			}
			if (!authorized(ex)) {
				send(ex, 401, "application/json", "{\"error\":\"Missing or wrong bearer token. Run /agent setup in Minecraft to copy the connect command.\"}");
				return;
			}
			switch (ex.getRequestMethod()) {
				case "POST" -> handlePost(ex);
				case "DELETE" -> send(ex, 200, "application/json", "{}");
				default -> {
					// No server-initiated SSE stream: everything is request/response.
					ex.getResponseHeaders().set("Allow", "POST, DELETE");
					send(ex, 405, "text/plain", "");
				}
			}
		} catch (Exception e) {
			AgentBridge.LOGGER.warn("MCP request failed", e);
			try {
				send(ex, 500, "application/json", "{\"error\":\"internal error\"}");
			} catch (IOException ignored) {
			}
		} finally {
			ex.close();
		}
	}

	private void handlePost(HttpExchange ex) throws IOException {
		byte[] body;
		try (InputStream in = ex.getRequestBody()) {
			body = in.readNBytes(MAX_BODY + 1);
		}
		if (body.length > MAX_BODY) {
			send(ex, 413, "application/json", "{\"error\":\"request too large\"}");
			return;
		}
		JsonElement parsed;
		try {
			parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
		} catch (Exception e) {
			send(ex, 400, "application/json", error(null, -32700, "Parse error").toString());
			return;
		}

		if (parsed.isJsonArray()) {
			JsonArray responses = new JsonArray();
			for (JsonElement el : parsed.getAsJsonArray()) {
				JsonObject r = dispatch(el, ex);
				if (r != null) responses.add(r);
			}
			if (responses.isEmpty()) send(ex, 202, null, "");
			else send(ex, 200, "application/json", responses.toString());
		} else {
			JsonObject r = dispatch(parsed, ex);
			if (r == null) send(ex, 202, null, "");
			else send(ex, 200, "application/json", r.toString());
		}
	}

	/** Handles one JSON-RPC message. Returns null for notifications and responses. */
	private JsonObject dispatch(JsonElement el, HttpExchange ex) {
		if (!el.isJsonObject()) return error(null, -32600, "Invalid Request");
		JsonObject msg = el.getAsJsonObject();
		JsonElement id = msg.get("id");
		if (!msg.has("method")) return null; // a response from the client; we never send requests
		String method = msg.get("method").getAsString();
		JsonObject params = msg.has("params") && msg.get("params").isJsonObject() ? msg.getAsJsonObject("params") : new JsonObject();
		boolean notification = id == null || id.isJsonNull();

		try {
			JsonObject result = switch (method) {
				case "initialize" -> initialize(params, ex);
				case "ping" -> new JsonObject();
				case "tools/list" -> tools.list();
				case "tools/call" -> tools.call(params);
				case "resources/list" -> listOf("resources");
				case "resources/templates/list" -> listOf("resourceTemplates");
				case "prompts/list" -> listOf("prompts");
				case "logging/setLevel" -> new JsonObject();
				default -> {
					if (method.startsWith("notifications/")) yield null;
					throw new RpcException(-32601, "Method not found: " + method);
				}
			};
			if (notification || result == null) return null;
			JsonObject resp = new JsonObject();
			resp.addProperty("jsonrpc", "2.0");
			resp.add("id", id);
			resp.add("result", result);
			return resp;
		} catch (RpcException e) {
			return notification ? null : error(id, e.code, e.getMessage());
		} catch (Exception e) {
			AgentBridge.LOGGER.warn("MCP method {} failed", method, e);
			return notification ? null : error(id, -32603, "Internal error: " + e.getMessage());
		}
	}

	private JsonObject initialize(JsonObject params, HttpExchange ex) {
		String requested = params.has("protocolVersion") ? params.get("protocolVersion").getAsString() : PROTOCOL_VERSIONS.getFirst();
		String version = PROTOCOL_VERSIONS.contains(requested) ? requested : PROTOCOL_VERSIONS.getFirst();

		String name = "Agent";
		if (params.has("clientInfo") && params.getAsJsonObject("clientInfo").has("name")) {
			name = prettyClientName(params.getAsJsonObject("clientInfo").get("name").getAsString());
		}
		Bridge.setClientName(name);
		String shown = name;
		server.execute(() -> Bridge.activity(server, "connected (" + shown + ")"));

		JsonObject result = new JsonObject();
		result.addProperty("protocolVersion", version);
		JsonObject caps = new JsonObject();
		JsonObject toolsCap = new JsonObject();
		toolsCap.addProperty("listChanged", false);
		caps.add("tools", toolsCap);
		result.add("capabilities", caps);
		JsonObject info = new JsonObject();
		info.addProperty("name", "minecraft-agent-bridge");
		info.addProperty("title", "Minecraft (Agent Bridge)");
		info.addProperty("version", "1.0.0");
		result.add("serverInfo", info);
		result.addProperty("instructions", Tools.INSTRUCTIONS);
		ex.getResponseHeaders().set("Mcp-Session-Id", UUID.randomUUID().toString());
		return result;
	}

	private static String prettyClientName(String raw) {
		String lower = raw.toLowerCase();
		if (lower.contains("claude")) return "Claude";
		if (lower.contains("codex")) return "Codex";
		return raw;
	}

	private static JsonObject listOf(String key) {
		JsonObject o = new JsonObject();
		o.add(key, new JsonArray());
		return o;
	}

	private boolean authorized(HttpExchange ex) {
		String header = ex.getRequestHeaders().getFirst("Authorization");
		if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return false;
		byte[] given = header.substring(7).trim().getBytes(StandardCharsets.UTF_8);
		byte[] expected = config.token.getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(given, expected);
	}

	/** Blocks web pages from talking to the bridge (DNS-rebinding protection). AI CLIs send no Origin. */
	private static boolean originAllowed(HttpExchange ex) {
		String origin = ex.getRequestHeaders().getFirst("Origin");
		if (origin == null || origin.isEmpty() || origin.equals("null")) return true;
		try {
			String host = URI.create(origin).getHost();
			return host != null && (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1"));
		} catch (Exception e) {
			return false;
		}
	}

	private static JsonObject error(JsonElement id, int code, String message) {
		JsonObject resp = new JsonObject();
		resp.addProperty("jsonrpc", "2.0");
		resp.add("id", id);
		JsonObject err = new JsonObject();
		err.addProperty("code", code);
		err.addProperty("message", message);
		resp.add("error", err);
		return resp;
	}

	private static void send(HttpExchange ex, int status, String contentType, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		if (contentType != null) ex.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
		if (bytes.length == 0) {
			ex.sendResponseHeaders(status, -1);
			return;
		}
		ex.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = ex.getResponseBody()) {
			out.write(bytes);
		}
	}

	public static final class RpcException extends RuntimeException {
		final int code;

		public RpcException(int code, String message) {
			super(message);
			this.code = code;
		}
	}
}

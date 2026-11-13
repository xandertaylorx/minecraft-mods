package com.agentbridge.test;

import com.agentbridge.BridgeConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * Turns the bridge on in a real world and talks to it over HTTP exactly like an MCP client would.
 * Run with: ./gradlew runClientGameTest   (results are printed as "AB-TEST" lines)
 *
 * Set AGENT_BRIDGE_LIVE=1 to keep the world open afterwards for a real AI client: the URL and token are written to
 * build/run/clientGameTest/agent-bridge-live.txt and the test waits until a file named agent-bridge-done appears there.
 */
public class AgentBridgeClientTest implements FabricClientGameTest {
	private final List<String> results = new ArrayList<>();
	private final HttpClient http = HttpClient.newHttpClient();
	private int nextId = 1;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			TestServerContext server = sp.getServer();
			for (String c : List.of("gamemode creative @a", "time set noon", "weather clear", "tp @a 0 -60 0 180 20")) {
				server.runCommand(c);
			}
			ctx.waitTicks(20);

			// Before /agent on, nothing listens.
			check("server is closed before /agent on", post(ctx, rpc("ping", new JsonObject()), true).status == -1);

			server.runCommand("execute as @p run agent on");
			ctx.waitTicks(5);

			// Auth
			check("wrong token is rejected", post(ctx, rpc("ping", new JsonObject()), false).status == 401);

			// Handshake
			JsonObject initParams = JsonParser.parseString(
				"{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"claude-code\",\"version\":\"test\"}}").getAsJsonObject();
			Resp init = post(ctx, rpc("initialize", initParams), true);
			check("initialize works", init.status == 200 && init.json().getAsJsonObject("result").get("protocolVersion").getAsString().equals("2025-06-18"));
			check("initialized notification gets 202", post(ctx, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", true).status == 202);

			Resp list = post(ctx, rpc("tools/list", new JsonObject()), true);
			int toolCount = list.json().getAsJsonObject("result").getAsJsonArray("tools").size();
			check("tools/list returns 12 tools (got " + toolCount + ")", toolCount == 12);

			String status = callText(ctx, "get_status", "{}");
			check("get_status reports position", status.contains("Position: x=0.5") || status.contains("Position: x=0"));
			log("get_status:\n" + status);

			// Build a small hut with build_structure: 5x5, stone floor, plank walls, door gap.
			String hut = """
				{"origin":{"x":-2,"y":-60,"z":-12},
				 "palette":{"#":"stone_bricks","p":"oak_planks","g":"glass_pane","s":"oak_stairs[facing=south]"},
				 "layers":[
				   ["#####","#####","#####","#####","#####"],
				   ["ppppp","p___p","p___p","p___p","pp_pp"],
				   ["pgggp","g___g","g___g","g___g","pp_pp"],
				   ["ppppp","p___p","p___p","p___p","ppppp"],
				   ["sssss","sssss","sssss","sssss","sssss"]
				 ]}""";
			String built = callText(ctx, "build_structure", hut);
			check("build_structure places blocks: " + built.split("\n")[0], built.contains("blocks changed") && !built.contains("Problems"));
			check("stone brick floor is in the world",
				server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(0, -60, -10)).is(Blocks.STONE_BRICKS)));
			check("door gap is air",
				server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(0, -59, -8)).isAir()));

			String scan = callText(ctx, "scan_area", "{\"x1\":-2,\"y1\":-60,\"z1\":-12,\"x2\":2,\"y2\":-58,\"z2\":-8}");
			check("scan_area sees the hut", scan.contains("stone_bricks") && scan.contains("oak_planks") && scan.contains("glass_pane"));
			log("scan_area:\n" + scan);

			String surface = callText(ctx, "get_surface", "{\"radius\":4}");
			check("get_surface returns heights", surface.contains("Top block y"));

			String fill = callText(ctx, "fill", "{\"x1\":5,\"y1\":-60,\"z1\":-12,\"x2\":9,\"y2\":-56,\"z2\":-8,\"block\":\"gold_block\",\"mode\":\"hollow\"}");
			check("fill hollow: " + fill, fill.startsWith("Changed 98 blocks") || fill.startsWith("Changed"));
			check("hollow box is hollow",
				server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(7, -58, -10)).isAir()
					&& s.overworld().getBlockState(new BlockPos(5, -58, -10)).is(Blocks.GOLD_BLOCK)));

			JsonObject badBlock = call(ctx, "set_blocks", "{\"blocks\":[{\"x\":0,\"y\":-55,\"z\":0,\"block\":\"not_a_real_block\"},{\"x\":1,\"y\":-55,\"z\":0,\"block\":\"diamond_block\"}]}");
			String badText = text(badBlock);
			check("set_blocks reports bad blocks but places good ones: " + badText.replace('\n', ' '), badText.contains("Placed 1") && badText.contains("not_a_real_block"));

			String undo = callText(ctx, "undo", "{\"steps\":2}");
			check("undo restores: " + undo.replace('\n', ' '), undo.contains("Undid"));
			check("gold box is gone after undo",
				server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(5, -58, -10)).isAir()
					&& s.overworld().getBlockState(new BlockPos(1, -55, 0)).isAir()));
			check("hut is still there",
				server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(0, -60, -10)).is(Blocks.STONE_BRICKS)));

			String cmd = callText(ctx, "run_command", "{\"command\":\"/time set 1000\"}");
			check("run_command runs: " + cmd.replace('\n', ' '), cmd.contains("Succeeded"));
			JsonObject op = call(ctx, "run_command", "{\"command\":\"op Someone\"}");
			check("run_command cannot /op", op.get("isError").getAsBoolean());

			String moved = callText(ctx, "move_player", "{\"x\":0.5,\"y\":-60,\"z\":0.5,\"look_at\":{\"x\":0,\"y\":-58,\"z\":-10}}");
			check("move_player: " + moved, moved.startsWith("Player now at"));
			callText(ctx, "say", "{\"message\":\"Hello from the test agent!\"}");
			String ents = callText(ctx, "get_entities", "{\"radius\":16}");
			check("get_entities answers", ents.contains("entities within"));

			ctx.waitTicks(10);
			JsonObject shot = call(ctx, "screenshot", "{\"max_width\":960}");
			boolean shotOk = shot.getAsJsonArray("content").get(0).getAsJsonObject().get("type").getAsString().equals("image");
			check("screenshot returns an image", shotOk);
			if (shotOk) {
				byte[] png = Base64.getDecoder().decode(shot.getAsJsonArray("content").get(0).getAsJsonObject().get("data").getAsString());
				Path out = Path.of("screenshots", "agent_screenshot.png");
				Files.createDirectories(out.getParent());
				Files.write(out, png);
				check("screenshot is a PNG (" + png.length + " bytes)", png.length > 1000 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G');
			}
			ctx.takeScreenshot("01_after_scripted_build");

			if (System.getenv("AGENT_BRIDGE_LIVE") != null) {
				Path info = Path.of("agent-bridge-live.txt");
				Path done = Path.of("agent-bridge-done");
				Files.deleteIfExists(done);
				Files.writeString(info, BridgeConfig.INSTANCE.url() + "\n" + BridgeConfig.INSTANCE.token + "\n");
				log("LIVE: waiting for an AI client. Create " + done.toAbsolutePath() + " to finish.");
				for (int i = 0; i < 20 * 60 * 15 && !Files.exists(done); i++) ctx.waitTick();
				ctx.takeScreenshot("02_after_live_agent");
			}

			server.runCommand("execute as @p run agent off");
			ctx.waitTicks(5);
			check("server is closed after /agent off", post(ctx, rpc("ping", new JsonObject()), true).status == -1);
		} catch (Exception e) {
			results.add("FAIL exception: " + e);
			e.printStackTrace();
		}

		long failures = results.stream().filter(r -> r.startsWith("FAIL")).count();
		for (String r : results) System.out.println("AB-TEST " + r);
		System.out.println("AB-TEST SUMMARY " + (results.size() - failures) + " passed, " + failures + " failed");
		if (failures > 0) throw new AssertionError(failures + " Agent Bridge checks failed");
	}

	private record Resp(int status, String body) {
		JsonObject json() {
			return JsonParser.parseString(body).getAsJsonObject();
		}
	}

	private String rpc(String method, JsonObject params) {
		JsonObject o = new JsonObject();
		o.addProperty("jsonrpc", "2.0");
		o.addProperty("id", nextId++);
		o.addProperty("method", method);
		o.add("params", params);
		return o.toString();
	}

	/** Sends a request without blocking the game, ticking until the answer arrives. */
	private Resp post(ClientGameTestContext ctx, String body, boolean goodToken) {
		HttpRequest req = HttpRequest.newBuilder(URI.create(BridgeConfig.INSTANCE.url()))
			.header("Content-Type", "application/json")
			.header("Accept", "application/json, text/event-stream")
			.header("Authorization", "Bearer " + (goodToken ? BridgeConfig.INSTANCE.token : "wrong"))
			.POST(HttpRequest.BodyPublishers.ofString(body)).build();
		CompletableFuture<Resp> f = http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
			.thenApply(r -> new Resp(r.statusCode(), r.body()))
			.exceptionally(t -> new Resp(-1, String.valueOf(t)));
		for (int i = 0; i < 20 * 60 && !f.isDone(); i++) ctx.waitTick();
		return f.getNow(new Resp(-2, "timeout"));
	}

	private JsonObject call(ClientGameTestContext ctx, String tool, String argsJson) {
		JsonObject params = new JsonObject();
		params.addProperty("name", tool);
		params.add("arguments", JsonParser.parseString(argsJson));
		Resp r = post(ctx, rpc("tools/call", params), true);
		if (r.status != 200) throw new IllegalStateException(tool + " -> HTTP " + r.status + ": " + r.body);
		return r.json().getAsJsonObject("result");
	}

	private String callText(ClientGameTestContext ctx, String tool, String argsJson) {
		return text(call(ctx, tool, argsJson));
	}

	private static String text(JsonObject result) {
		return result.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
	}

	private void check(String name, boolean ok) {
		results.add((ok ? "PASS " : "FAIL ") + name);
	}

	private void log(String s) {
		System.out.println("AB-LOG " + s);
	}
}

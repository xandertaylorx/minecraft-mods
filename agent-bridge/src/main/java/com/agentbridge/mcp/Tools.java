package com.agentbridge.mcp;

import com.agentbridge.AgentBridge;
import com.agentbridge.Bridge;
import com.agentbridge.BridgeConfig;
import com.agentbridge.ClientHooks;
import com.agentbridge.UndoHistory;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Every tool the AI can call. Handlers marked server-thread run on the Minecraft server thread. */
public final class Tools {
	public static final String INSTRUCTIONS = """
		You are controlling a live Minecraft (Java Edition 26.3) world through the Agent Bridge mod. The player is watching.
		Coordinates: +x is east, -x west, +z south, -z north, +y up. Blocks use ids like "stone", "oak_planks",
		or with states like "oak_stairs[facing=east,half=bottom]" (the same syntax as /setblock).
		Workflow: call get_status first to find the player, then look at the area with get_surface or scan_area
		before building, so builds sit on the ground and do not cut into terrain by accident.
		For anything bigger than a few blocks use build_structure (one call, layer by layer) or fill (boxes).
		Every build tool can be reverted with undo. Use screenshot to check how a build looks, after moving the player
		with move_player (look_at) to get a good view. run_command can run any game-master command (/time, /summon, /give ...).
		Keep the player informed with say for long builds.
		""";

	/** Characters used for palettes in scan_area / build_structure. '.', ' ' and '_' are reserved. */
	private static final String PALETTE_CHARS =
		"#abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@$%&*+=?!~^<>/|;:',-()[]{}";

	private interface Handler {
		JsonObject run(JsonObject args) throws Exception;
	}

	private record Tool(String name, String title, String description, JsonObject schema, boolean serverThread, boolean readOnly, Handler handler) {}

	private final MinecraftServer server;
	private final Map<String, Tool> tools = new LinkedHashMap<>();

	public Tools(MinecraftServer server) {
		this.server = server;

		add("get_status", "Get player and world status",
			"Where the player is and what they are looking at, plus game mode, health, held item, time, weather, dimension and world height limits. Call this first.",
			schema(), true, true, a -> getStatus());

		add("scan_area", "Scan blocks in a box",
			"Reads every block in a box and returns it as a palette plus one text grid per y layer (bottom to top). "
				+ "In each layer, rows go north to south (z increasing) and characters go west to east (x increasing). "
				+ "'.' is air. The output uses the same format build_structure takes. Max volume " + BridgeConfig.INSTANCE.maxScanVolume + " blocks.",
			schema(box(), "x1", "y1", "z1", "x2", "y2", "z2"), true, true, this::scanArea);

		add("get_surface", "Get terrain heights",
			"Top-down map around a point: the y of the highest non-air block and which block it is, for every column. "
				+ "Use it to find the ground before building. Radius up to 48.",
			schema(props(
				"x", integer("Center x (default: player x)"),
				"z", integer("Center z (default: player z)"),
				"radius", integer("How far out to look, 1-48 (default 16)")
			)), true, true, this::getSurface);

		add("get_entities", "List nearby entities",
			"Mobs, animals, items and other players near the player, with their type, position and health.",
			schema(props("radius", integer("Search radius, up to 128 (default 32)"))), true, true, this::getEntities);

		add("set_blocks", "Place individual blocks",
			"Places a list of blocks at exact coordinates. Each entry is {\"x\":0,\"y\":64,\"z\":0,\"block\":\"stone\"}. "
				+ "Use \"air\" to remove a block. Good for details; use build_structure or fill for big jobs.",
			schema(props("blocks", arrayOf(blockEntry(), "Blocks to place")), "blocks"), true, false, this::setBlocks);

		add("fill", "Fill a box",
			"Fills the box between two corners with one block, like /fill but with no size limit beyond the bridge limit. "
				+ "mode: replace (default, everything), keep (only air), hollow (walls/floor/roof, inside cleared to air), "
				+ "outline (walls/floor/roof only, inside untouched). only_replace limits it to one existing block type, e.g. \"grass_block\". "
				+ "Use block \"air\" to clear an area.",
			schema(fillProps(), "x1", "y1", "z1", "x2", "y2", "z2", "block"), true, false, this::fill);

		add("build_structure", "Build from layers",
			"Builds a structure from a palette and text layers. layers[0] is at origin.y, layers[1] at origin.y+1, and so on. "
				+ "Each layer is a list of rows: row 0 is at origin.z, row 1 at origin.z+1 (southward). "
				+ "Character 0 of a row is at origin.x, character 1 at origin.x+1 (eastward). "
				+ "Each palette key is one character mapped to a block, e.g. {\"#\":\"cobblestone\",\"s\":\"oak_stairs[facing=north]\"}. "
				+ "'.' or ' ' leaves the existing block alone, '_' sets air.",
			schema(props(
				"origin", point("Corner with the lowest x, y and z"),
				"palette", objectOf("Single character -> block id"),
				"layers", arrayOfArrays("Layers bottom to top; each layer is a list of row strings")
			), "origin", "palette", "layers"), true, false, this::buildStructure);

		add("undo", "Undo builds",
			"Reverts the last agent build actions (set_blocks, fill, build_structure). Does not undo run_command.",
			schema(props("steps", integer("How many actions to undo (default 1)"))), true, false, this::undo);

		add("run_command", "Run a game command",
			"Runs a Minecraft command as the player, with game-master permission, and returns its output. "
				+ "Examples: \"time set day\", \"weather clear\", \"summon minecraft:pig ~ ~ ~\", \"give @s diamond 5\". The leading slash is optional. "
				+ "Blocks changed by commands cannot be undone with undo.",
			schema(props("command", string("The command")), "command"), true, false, this::runCommand);

		add("move_player", "Move or turn the player",
			"Teleports the player and/or turns their camera. All fields are optional. look_at turns the camera to face a point, "
				+ "which is handy before taking a screenshot.",
			schema(props(
				"x", number("Target x"), "y", number("Target y"), "z", number("Target z"),
				"yaw", number("Horizontal angle: 0 south, 90 west, 180 north, -90 east"),
				"pitch", number("Vertical angle: -90 straight up, 0 level, 90 straight down"),
				"look_at", point("Point to face")
			)), true, false, this::movePlayer);

		add("say", "Message the player",
			"Shows a chat message to everyone in the game, labelled with your name.",
			schema(props("message", string("What to say")), "message"), true, false, this::say);

		add("screenshot", "See the game screen",
			"Takes a screenshot of the player's view so you can see your build. Only works when the mod runs in the game client (single-player or LAN host).",
			schema(props(
				"hide_hud", bool("Hide the hotbar and crosshair (default true)"),
				"max_width", integer("Shrink the image to at most this many pixels wide (default 1280)")
			)), false, true, this::screenshot);
	}

	// ---------------------------------------------------------------- MCP plumbing

	public JsonObject list() {
		JsonArray arr = new JsonArray();
		for (Tool t : tools.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("name", t.name);
			o.addProperty("title", t.title);
			o.addProperty("description", t.description);
			o.add("inputSchema", t.schema);
			JsonObject ann = new JsonObject();
			ann.addProperty("title", t.title);
			ann.addProperty("readOnlyHint", t.readOnly);
			ann.addProperty("destructiveHint", !t.readOnly && !t.name.equals("say") && !t.name.equals("move_player"));
			ann.addProperty("openWorldHint", false);
			o.add("annotations", ann);
			arr.add(o);
		}
		JsonObject result = new JsonObject();
		result.add("tools", arr);
		return result;
	}

	public JsonObject call(JsonObject params) {
		String name = params.has("name") ? params.get("name").getAsString() : "";
		Tool tool = tools.get(name);
		if (tool == null) throw new McpHttpServer.RpcException(-32602, "Unknown tool: " + name);
		JsonObject args = params.has("arguments") && params.get("arguments").isJsonObject() ? params.getAsJsonObject("arguments") : new JsonObject();
		try {
			if (!tool.serverThread) return tool.handler.run(args);
			return server.submit(() -> {
				try {
					return tool.handler.run(args);
				} catch (ToolError e) {
					return errorResult(e.getMessage());
				} catch (Exception e) {
					AgentBridge.LOGGER.warn("Tool {} failed", name, e);
					return errorResult(name + " failed: " + e);
				}
			}).get(60, TimeUnit.SECONDS);
		} catch (ToolError e) {
			return errorResult(e.getMessage());
		} catch (TimeoutException e) {
			return errorResult("The game did not respond within 60 seconds. Is the world paused or still loading?");
		} catch (ExecutionException e) {
			return errorResult(name + " failed: " + e.getCause());
		} catch (Exception e) {
			return errorResult(name + " failed: " + e);
		}
	}

	private void add(String name, String title, String description, JsonObject schema, boolean serverThread, boolean readOnly, Handler handler) {
		tools.put(name, new Tool(name, title, description, schema, serverThread, readOnly, handler));
	}

	// ---------------------------------------------------------------- tools

	private JsonObject getStatus() {
		ServerPlayer p = player();
		ServerLevel level = p.level();
		StringBuilder s = new StringBuilder();
		s.append("Player: ").append(p.getName().getString()).append('\n');
		s.append(String.format(Locale.ROOT, "Position: x=%.1f y=%.1f z=%.1f (block %d %d %d)%n", p.getX(), p.getY(), p.getZ(),
			p.getBlockX(), p.getBlockY(), p.getBlockZ()));
		s.append(String.format(Locale.ROOT, "Facing: %s (yaw %.0f, pitch %.0f)%n", p.getDirection().getName(), Mth.wrapDegrees(p.getYRot()), p.getXRot()));
		HitResult hit = p.pick(64, 1.0F, false);
		if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos bp = bhr.getBlockPos();
			s.append("Looking at: ").append(describe(level.getBlockState(bp))).append(" at ").append(bp.getX()).append(' ').append(bp.getY()).append(' ')
				.append(bp.getZ()).append(" (").append(bhr.getDirection().getName()).append(" face)\n");
		} else {
			s.append("Looking at: nothing within 64 blocks\n");
		}
		s.append("Standing on: ").append(describe(level.getBlockState(p.blockPosition().below()))).append('\n');
		s.append("Dimension: ").append(level.dimension().identifier()).append('\n');
		s.append("Game mode: ").append(p.gameMode().getSerializedName()).append('\n');
		s.append(String.format(Locale.ROOT, "Health: %.1f/%.1f, food: %d/20%n", p.getHealth(), p.getMaxHealth(), p.getFoodData().getFoodLevel()));
		ItemStack held = p.getMainHandItem();
		s.append("Holding: ").append(held.isEmpty() ? "nothing" : held.getCount() + "x " + BuiltInRegistries.ITEM.getKey(held.getItem())).append('\n');
		long time = level.getOverworldClockTime() % 24000L;
		s.append("Time of day: ").append(time).append(" (").append(time < 12000 ? "day" : time < 13000 ? "sunset" : time < 23000 ? "night" : "sunrise").append(")\n");
		s.append("Weather: ").append(level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear").append('\n');
		s.append("Build height: y=").append(level.getMinY()).append(" to y=").append(level.getMaxY()).append('\n');
		s.append("Undo history: ").append(Bridge.undo().size()).append(" action(s)\n");
		s.append("Limits: ").append(BridgeConfig.INSTANCE.maxBlocksPerCall).append(" blocks per call, scan up to ")
			.append(BridgeConfig.INSTANCE.maxScanVolume).append(" blocks");
		return textResult(s.toString());
	}

	private JsonObject scanArea(JsonObject a) {
		ServerLevel level = player().level();
		Box b = Box.of(a);
		long volume = b.volume();
		if (volume > BridgeConfig.INSTANCE.maxScanVolume) {
			throw new ToolError("Box is " + volume + " blocks, the scan limit is " + BridgeConfig.INSTANCE.maxScanVolume + ". Scan a smaller area or several pieces.");
		}
		Palette palette = new Palette();
		palette.reserve('.', Blocks.AIR.defaultBlockState());
		StringBuilder layers = new StringBuilder();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int emptyLayers = 0;
		for (int y = b.minY; y <= b.maxY; y++) {
			StringBuilder layer = new StringBuilder();
			boolean any = false;
			for (int z = b.minZ; z <= b.maxZ; z++) {
				for (int x = b.minX; x <= b.maxX; x++) {
					BlockState st = level.getBlockState(pos.set(x, y, z));
					char c = st.isAir() ? '.' : palette.charFor(st);
					if (c != '.') any = true;
					layer.append(c);
				}
				layer.append('\n');
			}
			if (any) {
				layers.append("y=").append(y).append(":\n").append(layer);
			} else {
				layers.append("y=").append(y).append(": all air\n");
				emptyLayers++;
			}
		}
		StringBuilder out = new StringBuilder();
		out.append(String.format(Locale.ROOT, "Box from (%d, %d, %d) to (%d, %d, %d): %d wide (x) x %d tall (y) x %d deep (z).%n",
			b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, b.sizeX(), b.sizeY(), b.sizeZ()));
		out.append("Rows go north to south (z = ").append(b.minZ).append(" first), characters west to east (x = ").append(b.minX).append(" first).\n");
		out.append("Palette:\n").append(palette.legend());
		out.append(layers);
		return textResult(out.toString());
	}

	private JsonObject getSurface(JsonObject a) {
		ServerPlayer p = player();
		ServerLevel level = p.level();
		int cx = optInt(a, "x", p.getBlockX());
		int cz = optInt(a, "z", p.getBlockZ());
		int r = Mth.clamp(optInt(a, "radius", 16), 1, 48);
		Palette palette = new Palette();
		StringBuilder heights = new StringBuilder();
		StringBuilder blocks = new StringBuilder();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minH = Integer.MAX_VALUE, maxH = Integer.MIN_VALUE;
		int[][] h = new int[2 * r + 1][2 * r + 1];
		for (int z = cz - r; z <= cz + r; z++) {
			for (int x = cx - r; x <= cx + r; x++) {
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				h[z - cz + r][x - cx + r] = top;
				minH = Math.min(minH, top);
				maxH = Math.max(maxH, top);
				BlockState st = level.getBlockState(pos.set(x, top, z));
				blocks.append(st.isAir() ? '.' : palette.charFor(st));
			}
			blocks.append('\n');
		}
		for (int[] row : h) {
			for (int i = 0; i < row.length; i++) {
				if (i > 0) heights.append(' ');
				heights.append(row[i]);
			}
			heights.append('\n');
		}
		String out = String.format(Locale.ROOT,
			"Surface from x=%d..%d, z=%d..%d (centre %d, %d). Ground y ranges %d to %d.%n"
				+ "Rows go north to south (z = %d first), columns west to east (x = %d first).%n%n"
				+ "Top block y for each column:%n%s%nTop block type (palette below):%n%s%nPalette:%n%s",
			cx - r, cx + r, cz - r, cz + r, cx, cz, minH, maxH, cz - r, cx - r, heights, blocks, palette.legend());
		return textResult(out);
	}

	private JsonObject getEntities(JsonObject a) {
		ServerPlayer p = player();
		double r = Mth.clamp(optInt(a, "radius", 32), 1, 128);
		List<Entity> list = new ArrayList<>(p.level().getEntities(p, new AABB(p.position(), p.position()).inflate(r), e -> true));
		list.sort((e1, e2) -> Double.compare(e1.distanceToSqr(p), e2.distanceToSqr(p)));
		StringBuilder s = new StringBuilder();
		s.append(list.size()).append(" entities within ").append((int) r).append(" blocks");
		if (list.size() > 100) s.append(" (showing the 100 nearest)");
		s.append(":\n");
		for (Entity e : list.subList(0, Math.min(100, list.size()))) {
			s.append("- ").append(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath());
			if (e.hasCustomName() || e instanceof ServerPlayer) s.append(" \"").append(e.getName().getString()).append('"');
			s.append(String.format(Locale.ROOT, " at %.1f %.1f %.1f (%.0f blocks away)", e.getX(), e.getY(), e.getZ(), Math.sqrt(e.distanceToSqr(p))));
			if (e instanceof LivingEntity le) s.append(String.format(Locale.ROOT, ", health %.0f/%.0f", le.getHealth(), le.getMaxHealth()));
			s.append('\n');
		}
		return textResult(s.toString());
	}

	private JsonObject setBlocks(JsonObject a) {
		ServerLevel level = player().level();
		JsonArray arr = requireArray(a, "blocks");
		if (arr.size() > BridgeConfig.INSTANCE.maxBlocksPerCall) {
			throw new ToolError("Too many blocks (" + arr.size() + "), the limit is " + BridgeConfig.INSTANCE.maxBlocksPerCall + " per call.");
		}
		BlockParser parser = new BlockParser(level);
		UndoHistory.Recorder rec = new UndoHistory.Recorder(level);
		List<String> problems = new ArrayList<>();
		int skipped = 0;
		for (int i = 0; i < arr.size(); i++) {
			try {
				JsonObject e = arr.get(i).getAsJsonObject();
				BlockPos pos = new BlockPos(reqInt(e, "x"), reqInt(e, "y"), reqInt(e, "z"));
				if (level.isOutsideBuildHeight(pos)) throw new ToolError("y=" + pos.getY() + " is outside the build height");
				if (!rec.set(pos, parser.parse(reqString(e, "block")))) skipped++;
			} catch (ToolError | IllegalStateException | ClassCastException e) {
				if (problems.size() < 10) problems.add("#" + i + ": " + e.getMessage());
			}
		}
		rec.finish();
		Bridge.undo().push("set_blocks (" + rec.changed() + ")", rec);
		Bridge.activity(server, "placed " + rec.changed() + " block" + (rec.changed() == 1 ? "" : "s"));
		return summary("Placed " + rec.changed() + " blocks (" + skipped + " were already that block).", problems);
	}

	private JsonObject fill(JsonObject a) {
		ServerLevel level = player().level();
		Box b = Box.of(a);
		checkVolume(b.volume());
		checkHeight(level, b);
		BlockParser parser = new BlockParser(level);
		BlockState block = parser.parse(reqString(a, "block"));
		String mode = optString(a, "mode", "replace").toLowerCase(Locale.ROOT);
		if (!Set.of("replace", "keep", "hollow", "outline").contains(mode)) throw new ToolError("mode must be replace, keep, hollow or outline");
		Block only = a.has("only_replace") ? parser.parse(a.get("only_replace").getAsString()).getBlock() : null;
		BlockState air = Blocks.AIR.defaultBlockState();

		UndoHistory.Recorder rec = new UndoHistory.Recorder(level);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = b.minY; y <= b.maxY; y++) {
			for (int z = b.minZ; z <= b.maxZ; z++) {
				for (int x = b.minX; x <= b.maxX; x++) {
					boolean edge = x == b.minX || x == b.maxX || y == b.minY || y == b.maxY || z == b.minZ || z == b.maxZ;
					BlockState target = switch (mode) {
						case "hollow" -> edge ? block : air;
						case "outline" -> edge ? block : null;
						default -> block;
					};
					if (target == null) continue;
					pos.set(x, y, z);
					BlockState current = level.getBlockState(pos);
					if (mode.equals("keep") && !current.isAir()) continue;
					if (only != null && !current.is(only)) continue;
					rec.set(pos.immutable(), target);
				}
			}
		}
		rec.finish();
		Bridge.undo().push("fill " + describe(block) + " (" + rec.changed() + ")", rec);
		Bridge.activity(server, "filled " + rec.changed() + " blocks with " + describe(block));
		return textResult("Changed " + rec.changed() + " blocks in the box (" + b.volume() + " blocks in total).");
	}

	private JsonObject buildStructure(JsonObject a) {
		ServerLevel level = player().level();
		JsonObject o = requireObject(a, "origin");
		int ox = reqInt(o, "x"), oy = reqInt(o, "y"), oz = reqInt(o, "z");
		BlockParser parser = new BlockParser(level);

		Map<Character, BlockState> palette = new HashMap<>();
		List<String> problems = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : requireObject(a, "palette").entrySet()) {
			String key = e.getKey();
			if (key.length() != 1) throw new ToolError("Palette key \"" + key + "\" must be exactly one character.");
			char c = key.charAt(0);
			if (c == '.' || c == ' ') throw new ToolError("'.' and ' ' are reserved for \"leave unchanged\"; pick another character.");
			palette.put(c, parser.parse(e.getValue().getAsString()));
		}
		palette.put('_', Blocks.AIR.defaultBlockState());

		JsonArray layers = requireArray(a, "layers");
		long total = 0;
		for (JsonElement layer : layers) for (JsonElement row : layer.getAsJsonArray()) total += row.getAsString().length();
		checkVolume(total);

		UndoHistory.Recorder rec = new UndoHistory.Recorder(level);
		Set<Character> unknown = new java.util.TreeSet<>();
		for (int ly = 0; ly < layers.size(); ly++) {
			int y = oy + ly;
			JsonArray rows = layers.get(ly).getAsJsonArray();
			if (level.isOutsideBuildHeight(y)) {
				problems.add("layer " + ly + " (y=" + y + ") is outside the build height, skipped");
				continue;
			}
			for (int rz = 0; rz < rows.size(); rz++) {
				String row = rows.get(rz).getAsString();
				for (int rx = 0; rx < row.length(); rx++) {
					char c = row.charAt(rx);
					if (c == '.' || c == ' ') continue;
					BlockState st = palette.get(c);
					if (st == null) {
						unknown.add(c);
						continue;
					}
					rec.set(new BlockPos(ox + rx, y, oz + rz), st);
				}
			}
		}
		rec.finish();
		if (!unknown.isEmpty()) problems.add("characters not in the palette were skipped: " + unknown);
		Bridge.undo().push("build_structure (" + rec.changed() + ")", rec);
		Bridge.activity(server, "built " + rec.changed() + " blocks");
		return summary("Built structure at " + ox + " " + oy + " " + oz + ": " + rec.changed() + " blocks changed.", problems);
	}

	private JsonObject undo(JsonObject a) {
		int steps = Mth.clamp(optInt(a, "steps", 1), 1, 100);
		String result = Bridge.undo().undo(server, steps);
		Bridge.activity(server, "undo");
		return textResult(result);
	}

	private JsonObject runCommand(JsonObject a) {
		if (!BridgeConfig.INSTANCE.allowCommands) throw new ToolError("run_command is turned off in config/agent-bridge.json (allowCommands).");
		ServerPlayer p = player();
		String cmd = reqString(a, "command").trim();
		if (cmd.startsWith("/")) cmd = cmd.substring(1);
		String root = cmd.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
		if (root.equals("agent")) throw new ToolError("The agent cannot change the bridge itself.");

		StringBuilder output = new StringBuilder();
		CommandSource collector = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				output.append(message.getString()).append('\n');
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		boolean[] success = {false};
		int[] value = {0};
		CommandSourceStack stack = p.createCommandSourceStack()
			.withSource(collector)
			.withPermission(LevelBasedPermissionSet.GAMEMASTER)
			.withCallback((ok, result) -> {
				success[0] |= ok;
				value[0] = result;
			});
		server.getCommands().performPrefixedCommand(stack, cmd);
		Bridge.activity(server, "/" + (cmd.length() > 60 ? cmd.substring(0, 60) + "…" : cmd));
		String text = "/" + cmd + "\n" + (success[0] ? "Succeeded (result " + value[0] + ")" : "Did not succeed") + "\n"
			+ (output.isEmpty() ? "(no output)" : output.toString().trim());
		return success[0] ? textResult(text) : errorResult(text);
	}

	private JsonObject movePlayer(JsonObject a) {
		ServerPlayer p = player();
		double x = a.has("x") ? a.get("x").getAsDouble() : p.getX();
		double y = a.has("y") ? a.get("y").getAsDouble() : p.getY();
		double z = a.has("z") ? a.get("z").getAsDouble() : p.getZ();
		float yaw = a.has("yaw") ? a.get("yaw").getAsFloat() : p.getYRot();
		float pitch = a.has("pitch") ? a.get("pitch").getAsFloat() : p.getXRot();
		if (a.has("look_at")) {
			JsonObject t = a.getAsJsonObject("look_at");
			Vec3 eye = new Vec3(x, y + p.getEyeHeight(), z);
			double dx = t.get("x").getAsDouble() - eye.x, dy = t.get("y").getAsDouble() - eye.y, dz = t.get("z").getAsDouble() - eye.z;
			yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
		}
		pitch = Mth.clamp(pitch, -90.0F, 90.0F);
		p.teleportTo(p.level(), x, y, z, Set.<Relative>of(), yaw, pitch, true);
		Bridge.activity(server, "moved you");
		return textResult(String.format(Locale.ROOT, "Player now at %.1f %.1f %.1f, yaw %.0f, pitch %.0f.", x, y, z, Mth.wrapDegrees(yaw), pitch));
	}

	private JsonObject say(JsonObject a) {
		String msg = reqString(a, "message");
		if (msg.length() > 1000) msg = msg.substring(0, 1000);
		server.getPlayerList().broadcastSystemMessage(
			Component.literal("<" + Bridge.clientName() + "> ").withStyle(ChatFormatting.AQUA).append(Component.literal(msg).withStyle(ChatFormatting.WHITE)), false);
		return textResult("Said it.");
	}

	private JsonObject screenshot(JsonObject a) throws Exception {
		ClientHooks hooks = AgentBridge.clientHooks;
		if (hooks == null) throw new ToolError("Screenshots only work when the mod runs inside the game client (single-player or LAN host), not on a dedicated server.");
		boolean hideHud = !a.has("hide_hud") || a.get("hide_hud").getAsBoolean();
		int maxWidth = Mth.clamp(optInt(a, "max_width", 1280), 320, 3840);
		server.execute(() -> Bridge.activity(server, "took a screenshot"));
		byte[] png;
		try {
			png = hooks.screenshot(hideHud, maxWidth).get(20, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			throw new ToolError("The game window did not render a frame in time. Is Minecraft minimised?");
		} catch (ExecutionException e) {
			throw new ToolError("Screenshot failed: " + e.getCause().getMessage());
		}
		JsonObject img = new JsonObject();
		img.addProperty("type", "image");
		img.addProperty("data", Base64.getEncoder().encodeToString(png));
		img.addProperty("mimeType", "image/png");
		JsonArray content = new JsonArray();
		content.add(img);
		JsonObject result = new JsonObject();
		result.add("content", content);
		return result;
	}

	// ---------------------------------------------------------------- helpers

	private ServerPlayer player() {
		ServerPlayer p = Bridge.player(server);
		if (p == null) throw new ToolError("No player is in the world right now.");
		return p;
	}

	private static void checkVolume(long volume) {
		if (volume > BridgeConfig.INSTANCE.maxBlocksPerCall) {
			throw new ToolError("That touches " + volume + " blocks, the limit is " + BridgeConfig.INSTANCE.maxBlocksPerCall + " per call. Split it up.");
		}
	}

	private static void checkHeight(ServerLevel level, Box b) {
		if (level.isOutsideBuildHeight(b.minY) || level.isOutsideBuildHeight(b.maxY)) {
			throw new ToolError("The box goes outside the build height (y=" + level.getMinY() + " to " + level.getMaxY() + ").");
		}
	}

	/** Block id plus only the properties that differ from the default, without the minecraft: prefix. */
	static String describe(BlockState state) {
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
		if (id.startsWith("minecraft:")) id = id.substring(10);
		BlockState def = state.getBlock().defaultBlockState();
		StringBuilder props = new StringBuilder();
		for (Property<?> p : state.getProperties()) {
			if (!state.getValue(p).equals(def.getValue(p))) {
				if (!props.isEmpty()) props.append(',');
				props.append(p.getName()).append('=').append(valueName(state, p));
			}
		}
		return props.isEmpty() ? id : id + "[" + props + "]";
	}

	private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> p) {
		return p.getName(state.getValue(p));
	}

	private static final class BlockParser {
		private final HolderLookup<Block> lookup;
		private final Map<String, BlockState> cache = new HashMap<>();

		BlockParser(ServerLevel level) {
			this.lookup = level.registryAccess().lookupOrThrow(Registries.BLOCK);
		}

		BlockState parse(String text) {
			return cache.computeIfAbsent(text.trim(), t -> {
				try {
					return BlockStateParser.parseForBlock(lookup, t, false).blockState();
				} catch (Exception e) {
					throw new ToolError("Bad block \"" + t + "\": " + e.getMessage());
				}
			});
		}
	}

	private static final class Palette {
		private final Map<BlockState, Character> chars = new LinkedHashMap<>();
		private final Map<Character, BlockState> reserved = new LinkedHashMap<>();
		private int next;

		void reserve(char c, BlockState state) {
			reserved.put(c, state);
		}

		char charFor(BlockState st) {
			return chars.computeIfAbsent(st, s -> {
				int i = next++;
				return i < PALETTE_CHARS.length() ? PALETTE_CHARS.charAt(i) : (char) (0x00C0 + i - PALETTE_CHARS.length());
			});
		}

		String legend() {
			StringBuilder s = new StringBuilder();
			for (Map.Entry<Character, BlockState> e : reserved.entrySet()) s.append(e.getKey()).append(" = ").append(describe(e.getValue())).append('\n');
			for (Map.Entry<BlockState, Character> e : chars.entrySet()) s.append(e.getValue()).append(" = ").append(describe(e.getKey())).append('\n');
			return s.toString();
		}
	}

	private record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		static Box of(JsonObject a) {
			int x1 = reqInt(a, "x1"), y1 = reqInt(a, "y1"), z1 = reqInt(a, "z1");
			int x2 = reqInt(a, "x2"), y2 = reqInt(a, "y2"), z2 = reqInt(a, "z2");
			return new Box(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
		}

		int sizeX() {
			return maxX - minX + 1;
		}

		int sizeY() {
			return maxY - minY + 1;
		}

		int sizeZ() {
			return maxZ - minZ + 1;
		}

		long volume() {
			return (long) sizeX() * sizeY() * sizeZ();
		}
	}

	static final class ToolError extends RuntimeException {
		ToolError(String message) {
			super(message);
		}
	}

	private static JsonObject textResult(String text) {
		JsonObject t = new JsonObject();
		t.addProperty("type", "text");
		t.addProperty("text", text);
		JsonArray content = new JsonArray();
		content.add(t);
		JsonObject result = new JsonObject();
		result.add("content", content);
		result.addProperty("isError", false);
		return result;
	}

	private static JsonObject errorResult(String text) {
		JsonObject r = textResult(text);
		r.addProperty("isError", true);
		return r;
	}

	private static JsonObject summary(String headline, List<String> problems) {
		if (problems.isEmpty()) return textResult(headline);
		return textResult(headline + "\nProblems:\n- " + String.join("\n- ", problems));
	}

	private static int reqInt(JsonObject o, String k) {
		if (!o.has(k) || o.get(k).isJsonNull()) throw new ToolError("Missing \"" + k + "\"");
		try {
			return (int) Math.floor(o.get(k).getAsDouble());
		} catch (Exception e) {
			throw new ToolError("\"" + k + "\" must be a number");
		}
	}

	private static int optInt(JsonObject o, String k, int def) {
		return o.has(k) && !o.get(k).isJsonNull() ? reqInt(o, k) : def;
	}

	private static String reqString(JsonObject o, String k) {
		if (!o.has(k) || !o.get(k).isJsonPrimitive()) throw new ToolError("Missing \"" + k + "\"");
		return o.get(k).getAsString();
	}

	private static String optString(JsonObject o, String k, String def) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
	}

	private static JsonArray requireArray(JsonObject o, String k) {
		if (!o.has(k) || !o.get(k).isJsonArray()) throw new ToolError("\"" + k + "\" must be an array");
		return o.getAsJsonArray(k);
	}

	private static JsonObject requireObject(JsonObject o, String k) {
		if (!o.has(k) || !o.get(k).isJsonObject()) throw new ToolError("\"" + k + "\" must be an object");
		return o.getAsJsonObject(k);
	}

	// ---------------------------------------------------------------- JSON schema builders

	private static JsonObject schema() {
		return schema(new JsonObject());
	}

	private static JsonObject schema(JsonObject properties, String... required) {
		JsonObject s = new JsonObject();
		s.addProperty("type", "object");
		s.add("properties", properties);
		if (required.length > 0) {
			JsonArray req = new JsonArray();
			for (String r : required) req.add(r);
			s.add("required", req);
		}
		return s;
	}

	private static JsonObject props(Object... pairs) {
		JsonObject o = new JsonObject();
		for (int i = 0; i < pairs.length; i += 2) o.add((String) pairs[i], (JsonObject) pairs[i + 1]);
		return o;
	}

	private static JsonObject typed(String type, String description) {
		JsonObject o = new JsonObject();
		o.addProperty("type", type);
		o.addProperty("description", description);
		return o;
	}

	private static JsonObject integer(String d) {
		return typed("integer", d);
	}

	private static JsonObject number(String d) {
		return typed("number", d);
	}

	private static JsonObject string(String d) {
		return typed("string", d);
	}

	private static JsonObject bool(String d) {
		return typed("boolean", d);
	}

	private static JsonObject point(String d) {
		JsonObject o = typed("object", d);
		o.add("properties", props("x", number("x"), "y", number("y"), "z", number("z")));
		JsonArray req = new JsonArray();
		req.add("x");
		req.add("y");
		req.add("z");
		o.add("required", req);
		return o;
	}

	private static JsonObject objectOf(String d) {
		JsonObject o = typed("object", d);
		o.add("additionalProperties", typed("string", "Block id"));
		return o;
	}

	private static JsonObject arrayOf(JsonObject items, String d) {
		JsonObject o = typed("array", d);
		o.add("items", items);
		return o;
	}

	private static JsonObject arrayOfArrays(String d) {
		JsonObject inner = typed("array", "One layer: row strings, north to south");
		inner.add("items", typed("string", "One row, west to east"));
		return arrayOf(inner, d);
	}

	private static JsonObject blockEntry() {
		JsonObject o = typed("object", "A block to place");
		o.add("properties", props("x", integer("x"), "y", integer("y"), "z", integer("z"), "block", string("Block id, e.g. stone or oak_stairs[facing=east]")));
		JsonArray req = new JsonArray();
		for (String r : new String[] {"x", "y", "z", "block"}) req.add(r);
		o.add("required", req);
		return o;
	}

	private static JsonObject box() {
		return props(
			"x1", integer("First corner x"), "y1", integer("First corner y"), "z1", integer("First corner z"),
			"x2", integer("Opposite corner x"), "y2", integer("Opposite corner y"), "z2", integer("Opposite corner z"));
	}

	private static JsonObject fillProps() {
		JsonObject p = box();
		p.add("block", string("Block to fill with, e.g. stone_bricks, or air to clear"));
		JsonObject mode = typed("string", "replace, keep, hollow or outline (default replace)");
		JsonArray values = new JsonArray();
		for (String v : new String[] {"replace", "keep", "hollow", "outline"}) values.add(v);
		mode.add("enum", values);
		p.add("mode", mode);
		p.add("only_replace", string("Only change blocks of this type"));
		return p;
	}
}

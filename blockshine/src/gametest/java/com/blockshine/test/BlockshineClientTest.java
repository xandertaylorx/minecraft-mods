package com.blockshine.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.irisshaders.iris.Iris;

/**
 * Loads the Blockshine pack in a real client with Iris + Sodium, takes screenshots and measures FPS.
 * Run: ./gradlew runClientGameTest
 * Screenshots: build/run/clientGameTest/screenshots. Results: lines starting with "BS-TEST".
 */
public class BlockshineClientTest implements FabricClientGameTest {
	private static final String PACK = "Blockshine";
	private final List<String> results = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try {
			Path gameDir = ctx.computeOnClient(mc -> mc.gameDirectory.toPath());
			Path packSrc = Path.of(System.getProperty("blockshine.packDir"));
			Path packDst = gameDir.resolve("shaderpacks").resolve(PACK);
			copyDir(packSrc, packDst);
			Map<String, Map<String, String>> profiles = readProfiles(packSrc.resolve("shaders/shaders.properties"));

			ctx.runOnClient(mc -> {
				mc.options.framerateLimit().set(260);
				mc.options.enableVsync().set(false);
				mc.options.renderDistance().set(12);
				// The test sits still, so vanilla's AFK limiter would cap FPS at 30 after a minute.
				mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
				// If the window loses focus the game opens the pause menu, which caps FPS at 60. Don't.
				mc.options.pauseOnLostFocus = false;
			});

			try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
				TestServerContext server = sp.getServer();
				TestInput input = ctx.getInput();
				buildScene(server);
				ctx.waitTicks(160); // Sodium renders chunks itself, so the chunk-wait helpers never finish; wait instead
				input.pressKey(o -> o.keyToggleGui);
				ctx.waitTicks(20);

				// ---- FPS: two rounds, keep each setup's best. macOS throttles the window while it's
				// covered by other windows, which can randomly sink one reading; the best of two is reliable.
				String other = System.getProperty("blockshine.compareWith", "");
				boolean compare = !other.isEmpty() && Files.exists(Path.of(other));
				Map<String, Integer> best = new LinkedHashMap<>();
				int rounds = Boolean.getBoolean("blockshine.quick") ? 1 : 2;
				for (int round = 0; round < rounds; round++) {
					setShaders(ctx, false, gameDir, null);
					look(ctx, 0.0F, 12.0F);
					if (round == 0) ctx.takeScreenshot("00_vanilla_noon");
					best.merge("vanilla (no shaders)", measureFps(ctx), Math::max);
					for (String profile : List.of("POTATO", "LOW", "MEDIUM", "HIGH")) {
						setShaders(ctx, true, gameDir, profiles.get(profile));
						look(ctx, 0.0F, 12.0F);
						if (round == 0) {
							String err = Iris.getStoredError().map(Throwable::toString).orElse(null);
							check(profile + " compiles and loads", err == null && PACK.equals(Iris.getCurrentPackName()), err);
							ctx.takeScreenshot("01_" + profile.toLowerCase() + "_noon");
						}
						best.merge(profile, measureFps(ctx), Math::max);
					}
					if (compare) {
						String otherName = Path.of(other).getFileName().toString();
						try {
							Files.copy(Path.of(other), gameDir.resolve("shaderpacks").resolve(otherName), StandardCopyOption.REPLACE_EXISTING);
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						}
						ctx.runOnClient(mc -> {
							try {
								Iris.getIrisConfig().setShaderPackName(otherName);
								Iris.getIrisConfig().setShadersEnabled(true);
								Iris.getIrisConfig().save();
								Iris.reload();
							} catch (IOException e) {
								throw new UncheckedIOException(e);
							}
						});
						ctx.waitTicks(40);
						look(ctx, 0.0F, 12.0F);
						best.merge(otherName + " (default settings)", measureFps(ctx), Math::max);
						if (round == 0) {
							ctx.takeScreenshot("02_compare_" + otherName.replace(".zip", ""));
							// Same views the Blockshine shots use, for side-by-side looks (kept in build/, never committed)
							compareShots(ctx, server, "cmp_other");
						}
					}
				}
				int vanillaFps = best.get("vanilla (no shaders)");
				best.forEach((k, v) -> result("FPS " + k + ": " + v + " (" + pct(v, vanillaFps) + "% of vanilla)"));

				// ---- Optional: benchmark the compare pack with different option sets ----
				// -PotherVariants=/path/to/file, one "label|KEY=VALUE KEY2=VALUE" per line (empty options = defaults).
				// Options go into "<pack>.txt" next to the zip, which is where Iris keeps a pack's settings.
				String variantsFile = System.getProperty("blockshine.otherVariants", "");
				if (compare && !variantsFile.isEmpty()) try {
					String otherName = Path.of(other).getFileName().toString();
					Path optFile = gameDir.resolve("shaderpacks").resolve(otherName + ".txt");
					for (String line : Files.readAllLines(Path.of(variantsFile))) {
						if (line.isBlank() || line.startsWith("#") || !line.contains("|")) continue;
						String label = line.substring(0, line.indexOf('|')).trim();
						StringBuilder sb = new StringBuilder();
						for (String kv : line.substring(line.indexOf('|') + 1).trim().split("\\s+"))
							if (kv.contains("=")) sb.append(kv).append('\n');
						Files.writeString(optFile, sb.toString());
						ctx.runOnClient(mc -> {
							try {
								Iris.getIrisConfig().setShaderPackName(otherName);
								Iris.getIrisConfig().setShadersEnabled(true);
								Iris.getIrisConfig().save();
								Iris.reload();
							} catch (IOException e) {
								throw new UncheckedIOException(e);
							}
						});
						ctx.waitTicks(40);
						look(ctx, 0.0F, 12.0F);
						String err = Iris.getStoredError().map(Throwable::toString).orElse(null);
						int fps = Math.max(measureFps(ctx), measureFps(ctx));
						result("FPS other[" + label + "]: " + fps + " (" + pct(fps, vanillaFps) + "% of vanilla)" + (err == null ? "" : " ERROR " + err));
						ctx.takeScreenshot("03_variant_" + label.replaceAll("[^A-Za-z0-9]+", "_"));
					}
					Files.deleteIfExists(optFile);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}

				// ---- Optional: isolate what makes HIGH slow (-Dblockshine.isolate=true) ----
				if (Boolean.getBoolean("blockshine.isolate")) {
					String[][] variants = {
						{"HIGH again", ""},
						{"HIGH -SSR", "WATER_SSR=false"},
						{"HIGH -godrays", "VOLUMETRIC_LIGHT=false"},
						{"HIGH clouds4", "CLOUD_DETAIL=4"},
						{"HIGH shadow1024", "shadowMapResolution=1024"},
						{"HIGH filter1", "SHADOW_FILTER=1"},
						{"HIGH again2", ""},
					};
					for (String[] v : variants) {
						Map<String, String> opts = new LinkedHashMap<>(profiles.get("HIGH"));
						if (!v[1].isEmpty()) opts.put(v[1].split("=")[0], v[1].split("=")[1]);
						setShaders(ctx, true, gameDir, opts);
						look(ctx, 0.0F, 12.0F);
						result("FPS " + v[0] + ": " + measureFps(ctx));
					}
				}

				// ---- Times of day on MEDIUM ----
				setShaders(ctx, true, gameDir, profiles.get("MEDIUM"));
				compareShots(ctx, server, "cmp_ours");
				shot(ctx, server, "time set 1500", 0.0F, 12.0F, "10_medium_morning");
				shot(ctx, server, "time set 12400", 90.0F, 2.0F, "11_medium_sunset_west");
				shot(ctx, server, "time set 12400", 0.0F, 12.0F, "12_medium_sunset_scene");
				shot(ctx, server, "time set 18000", 0.0F, 12.0F, "13_medium_night");
				server.runCommand("time set 6000");
				shot(ctx, server, "weather rain", 0.0F, 12.0F, "14_medium_rain");
				server.runCommand("weather clear");
				server.runCommand("tp @a 0 -59 4 0 0");
				shot(ctx, server, "time set 6000", 0.0F, 50.0F, "15_medium_water_down");
				server.runCommand("tp @a 0 -60 0 0 0");

				// ---- Atmosphere through the day (MEDIUM) ----
				shot(ctx, server, "time set 23300", -90.0F, 0.0F, "16_medium_sunrise_mist_east");
				shot(ctx, server, "time set 9500", 90.0F, -25.0F, "17_medium_lens_flare");
				shot(ctx, server, "time set 13600", -90.0F, -12.0F, "18_medium_moonrise_east");
				shot(ctx, server, "time set 18000", 0.0F, -35.0F, "19_medium_night_sky");

				// ---- God rays on HIGH: through the big oak toward the setting sun ----
				setShaders(ctx, true, gameDir, profiles.get("HIGH"));
				shot(ctx, server, "time set 11800", 90.0F, -4.0F, "20_high_shafts_through_trees");
				server.runCommand("tp @a 9 -60 8 90 0");
				shot(ctx, server, "time set 11900", 95.0F, -6.0F, "21_high_godrays_canopy");
				shot(ctx, server, "time set 11900", 60.0F, -4.0F, "22_high_godrays_side");
				shot(ctx, server, "time set 23400", -90.0F, -2.0F, "23_high_sunrise_rays");

				// ---- Website hero shots at full HD ----
				input.resizeWindow(1920, 1080);
				ctx.waitTicks(40);
				server.runCommand("tp @a 2 -60 -2 0 0");
				shot(ctx, server, "time set 12100", 20.0F, 4.0F, "90_hero_sunset_scene");
				server.runCommand("tp @a 9 -60 8 90 0");
				shot(ctx, server, "time set 11900", 95.0F, -6.0F, "91_hero_godrays");
				shot(ctx, server, "time set 12150", 95.0F, -3.0F, "93_hero_sunset_west");
				server.runCommand("tp @a 2 -60 -2 0 0");
				shot(ctx, server, "time set 15500", 0.0F, -6.0F, "92_hero_night");
				input.resizeWindow(854, 480);
				ctx.waitTicks(20);
				server.runCommand("tp @a 0 -60 0 0 0");

				// ---- Nether ----
				setShaders(ctx, true, gameDir, profiles.get("MEDIUM"));
				// Glass box so nearby lava can't flow in
				server.runCommand("execute in minecraft:the_nether run tp @a 0 69 0");
				ctx.waitTicks(60);   // let the chunk load first, or the fill below silently does nothing
				server.runCommand("execute in minecraft:the_nether run fill -6 67 -6 6 77 6 minecraft:glass hollow");
				server.runCommand("execute in minecraft:the_nether run fill -5 69 -5 5 76 5 minecraft:air");
				server.runCommand("execute in minecraft:the_nether run fill -5 68 -5 5 68 5 minecraft:netherrack");
				server.runCommand("execute in minecraft:the_nether run setblock 2 69 4 minecraft:glowstone");
				server.runCommand("execute in minecraft:the_nether run tp @a 0 69 0");
				ctx.waitTicks(60);
				ctx.waitTicks(160); // Sodium renders chunks itself, so the chunk-wait helpers never finish; wait instead
				look(ctx, 0.0F, 25.0F);
				ctx.waitTicks(20);
				String err = Iris.getStoredError().map(Throwable::toString).orElse(null);
				check("Nether programs load", err == null, err);
				ctx.takeScreenshot("30_nether");
			}
		} finally {
			for (String r : this.results) {
				System.out.println("BS-TEST " + r);
			}
		}
	}

	// ------------------------------------------------------------------

	private static void buildScene(TestServerContext s) {
		for (String c : List.of(
			"gamemode creative @a", "time set 6000", "weather clear", "gamerule advance_time false", "gamerule advance_weather false",
			"tp @a 0 -60 0 0 0",
			// pond
			"fill -7 -62 7 7 -61 15 minecraft:water",
			"fill -8 -61 6 8 -61 6 minecraft:sand", "fill -8 -61 16 8 -61 16 minecraft:sand",
			// trees
			"place feature minecraft:oak 11 -60 12", "place feature minecraft:fancy_oak -12 -60 14",
			"place feature minecraft:birch 13 -60 20", "place feature minecraft:oak -9 -60 22",
			"place feature minecraft:oak 4 -60 24", "place feature minecraft:fancy_oak -2 -60 30",
			// plants
			"fill -10 -60 2 10 -60 5 minecraft:short_grass replace minecraft:air",
			"setblock -3 -60 3 minecraft:poppy", "setblock 2 -60 4 minecraft:dandelion", "setblock 5 -60 3 minecraft:cornflower",
			"setblock -6 -60 4 minecraft:tall_grass[half=lower]", "setblock -6 -59 4 minecraft:tall_grass[half=upper]",
			"setblock 7 -60 4 minecraft:rose_bush[half=lower]", "setblock 7 -59 4 minecraft:rose_bush[half=upper]",
			// lights
			"setblock -2 -60 6 minecraft:air", "setblock -2 -61 6 minecraft:cobblestone", "setblock -2 -60 6 minecraft:torch",
			"setblock 3 -61 6 minecraft:cobblestone", "setblock 3 -60 6 minecraft:lantern",
			"setblock 9 -61 8 minecraft:glowstone", "setblock -9 -61 9 minecraft:sea_lantern",
			// a small stone hut for shadow + torch light
			"fill 15 -60 2 19 -57 6 minecraft:stone_bricks hollow", "setblock 17 -59 2 minecraft:air", "setblock 17 -60 2 minecraft:air",
			"setblock 17 -59 4 minecraft:wall_torch[facing=north]",
			"fill 9 -60 6 13 -57 10 minecraft:oak_planks hollow", "fill 9 -60 6 13 -60 10 minecraft:cobblestone",
			"fill 9 -56 6 13 -56 10 minecraft:spruce_planks", "setblock 11 -59 6 minecraft:glass_pane", "setblock 11 -60 6 minecraft:air",
			"setblock 10 -59 5 minecraft:lantern", "setblock 12 -60 5 minecraft:torch", "fill 8 -61 4 14 -61 5 minecraft:dirt_path"
		)) {
			s.runCommand(c);
		}
	}

	/** A fixed set of views used to compare looks between packs. */
	private void compareShots(ClientGameTestContext ctx, TestServerContext server, String prefix) {
		server.runCommand("tp @a 0 -60 0 0 0");
		shot(ctx, server, "time set 6000", 0.0F, 12.0F, prefix + "_a_noon");
		server.runCommand("tp @a 0 -59 4 0 0");
		shot(ctx, server, "time set 6000", 0.0F, 40.0F, prefix + "_b_water");
		server.runCommand("tp @a 9 -60 8 90 0");
		shot(ctx, server, "time set 11900", 95.0F, -6.0F, prefix + "_c_sunset_water");
		server.runCommand("tp @a -10 -60 4 0 0");
		shot(ctx, server, "time set 4000", 160.0F, -10.0F, prefix + "_d_leaves");
		server.runCommand("tp @a 0 -60 0 0 0");
		shot(ctx, server, "time set 18000", 0.0F, 8.0F, prefix + "_e_night");
		server.runCommand("tp @a 12 -60 -1 0 0");
		shot(ctx, server, "time set 6000", -50.0F, 10.0F, prefix + "_f_blocks_house");
		shot(ctx, server, "time set 18000", -50.0F, 10.0F, prefix + "_g_night_house");
		server.runCommand("tp @a -11 -60 12 0 0");
		shot(ctx, server, "time set 6000", 160.0F, -55.0F, prefix + "_h_under_leaves");
		server.runCommand("tp @a -2 -60 1 0 0");
		shot(ctx, server, "time set 6000", 0.0F, 40.0F, prefix + "_i_grass_close");
		shot(ctx, server, "time set 14000", 0.0F, 5.0F, prefix + "_j_dusk");
		server.runCommand("tp @a 0 -60 0 0 0");
		server.runCommand("time set 6000");
	}

	private void shot(ClientGameTestContext ctx, TestServerContext server, String cmd, float yaw, float pitch, String name) {
		server.runCommand(cmd);
		look(ctx, yaw, pitch);
		ctx.waitTicks(40);
		ctx.takeScreenshot(name);
	}

	private static void look(ClientGameTestContext ctx, float yaw, float pitch) {
		ctx.getInput().lookAt(yaw, pitch);
		ctx.waitTicks(5);
	}

	/** Writes the profile's option values for Iris, then (re)loads shaders. */
	private static void setShaders(ClientGameTestContext ctx, boolean on, Path gameDir, Map<String, String> options) {
		try {
			if (options != null) {
				StringBuilder sb = new StringBuilder();
				options.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
				Files.writeString(gameDir.resolve("shaderpacks").resolve(PACK + ".txt"), sb.toString());
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		ctx.runOnClient(mc -> {
			try {
				Iris.getIrisConfig().setShaderPackName(PACK);
				Iris.getIrisConfig().setShadersEnabled(on);
				Iris.getIrisConfig().save();
				Iris.reload();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
		ctx.waitTicks(40);
	}

	/** Median of 8 once-per-second FPS readings after a long warm-up. */
	private static int measureFps(ClientGameTestContext ctx) {
		ctx.waitTicks(120);
		int[] samples = new int[8];
		for (int i = 0; i < samples.length; i++) {
			ctx.waitTicks(21);
			samples[i] = ctx.computeOnClient(mc -> mc.getFps());
		}
		java.util.Arrays.sort(samples);
		return (samples[3] + samples[4]) / 2;
	}

	private static int pct(int a, int b) {
		return b == 0 ? 0 : Math.round(a * 100.0F / b);
	}

	private void check(String what, boolean ok, String detail) {
		this.results.add((ok ? "PASS " : "FAIL ") + what + (ok || detail == null ? "" : " :: " + detail));
	}

	private void result(String line) {
		this.results.add("INFO " + line);
	}

	/** Parses "profile.NAME=OPT !OPT KEY=VALUE" lines from shaders.properties. */
	private static Map<String, Map<String, String>> readProfiles(Path props) {
		Map<String, Map<String, String>> out = new LinkedHashMap<>();
		try {
			for (String line : Files.readAllLines(props)) {
				line = line.trim();
				if (!line.startsWith("profile.") || !line.contains("=")) continue;
				String name = line.substring("profile.".length(), line.indexOf('='));
				Map<String, String> opts = new LinkedHashMap<>();
				for (String tok : line.substring(line.indexOf('=') + 1).trim().split("\\s+")) {
					if (tok.startsWith("!")) opts.put(tok.substring(1), "false");
					else if (tok.contains("=")) opts.put(tok.substring(0, tok.indexOf('=')), tok.substring(tok.indexOf('=') + 1));
					else opts.put(tok, "true");
				}
				out.put(name, opts);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out;
	}

	private static void copyDir(Path src, Path dst) {
		try (Stream<Path> walk = Files.walk(src)) {
			for (Path p : (Iterable<Path>) walk::iterator) {
				Path t = dst.resolve(src.relativize(p).toString());
				if (Files.isDirectory(p)) Files.createDirectories(t);
				else Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}

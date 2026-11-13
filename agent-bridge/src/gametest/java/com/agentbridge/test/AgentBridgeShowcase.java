package com.agentbridge.test;

import com.agentbridge.BridgeConfig;
import com.agentbridge.chat.AgentChat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.server.level.ServerPlayer;

/**
 * Takes the website screenshots: a normal world, the Blockshine shader pack, and real @claude chat.
 *
 *   AGENT_BRIDGE_SCRIPT=showcase.txt AGENT_BRIDGE_SEED=12345 ./gradlew runClientGameTest -Pshowcase=1
 *
 * The script is one action per line:
 *   cmd <command>          run a server command (no slash)
 *   chat <message>         type in chat; for @claude/@codex messages, wait until the AI is done
 *   wait <ticks>           let the game run
 *   look <yaw> <pitch>     turn the camera
 *   shot <name>            screenshot with chat and HUD
 *   clean <name>           screenshot without HUD
 *   where                  print the player's position
 *   fly                    start flying, so the camera stays put in the air
 *   tools on|off           show or hide the grey tool lines in chat
 *   # comment
 * Screenshots land in build/run/clientGameTest/screenshots.
 */
public class AgentBridgeShowcase implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (System.getProperty("agentbridge.showcase") == null) return;
		String scriptPath = System.getenv("AGENT_BRIDGE_SCRIPT");
		if (scriptPath == null) throw new IllegalStateException("Set AGENT_BRIDGE_SCRIPT to a showcase script");
		List<String> script;
		try {
			script = Files.readAllLines(Path.of(scriptPath));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		BridgeConfig.INSTANCE.port = 25611;
		Path gameDir = ctx.computeOnClient(mc -> mc.gameDirectory.toPath());
		ctx.runOnClient(mc -> {
			mc.options.renderDistance().set(16);
			mc.options.framerateLimit().set(120);
			mc.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
			mc.options.pauseOnLostFocus = false;
			mc.options.fov().set(75);
			mc.options.guiScale().set(3);
		});
		if (FabricLoader.getInstance().isModLoaded("iris")) {
			String pack = System.getProperty("agentbridge.shaderPack");
			copyDir(Path.of(pack), gameDir.resolve("shaderpacks").resolve("Blockshine"));
			ctx.runOnClient(mc -> IrisToggle.enable("Blockshine"));
			ctx.waitTicks(40);
		}

		String seed = System.getenv().getOrDefault("AGENT_BRIDGE_SEED", "");
		try (TestSingleplayerContext sp = ctx.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed(seed);
				s.setAllowCommands(true);
			})
			.create()) {
			TestServerContext server = sp.getServer();
			for (String c : List.of("gamemode creative @a", "gamerule advance_time false", "gamerule advance_weather false",
				"weather clear", "time set 11500", "difficulty peaceful")) {
				server.runCommand(c);
			}
			server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(p -> s.getPlayerList().op(p.nameAndId())));
			ctx.getInput().resizeWindow(1920, 1080);
			ctx.waitTicks(200); // Sodium renders chunks itself, so the chunk-wait helpers never finish

			for (String raw : script) {
				String line = raw.strip();
				if (line.isEmpty() || line.startsWith("#")) continue;
				String[] parts = line.split("\\s+", 2);
				String arg = parts.length > 1 ? parts[1] : "";
				System.out.println("AB-SHOW " + line);
				switch (parts[0]) {
					case "cmd" -> server.runCommand(arg);
					case "wait" -> ctx.waitTicks(Integer.parseInt(arg));
					case "look" -> {
						String[] yp = arg.split("\\s+");
						ctx.getInput().lookAt(Float.parseFloat(yp[0]), Float.parseFloat(yp[1]));
						ctx.waitTicks(5);
					}
					case "chat" -> {
						ctx.runOnClient(mc -> mc.player.connection.sendChat(arg));
						ctx.waitTicks(10);
						String lower = arg.toLowerCase(Locale.ROOT);
						AgentChat.Kind kind = lower.startsWith("@codex") ? AgentChat.Kind.CODEX : lower.startsWith("@claude") ? AgentChat.Kind.CLAUDE : null;
						if (kind != null) {
							for (int i = 0; i < 20 * 60 * 10 && AgentChat.isBusy(kind); i++) ctx.waitTick();
						}
						ctx.waitTicks(30);
					}
					case "shot" -> ctx.takeScreenshot(arg);
					case "fly" -> ctx.runOnClient(mc -> {
						mc.player.getAbilities().flying = true;
						mc.player.onUpdateAbilities();
					});
					case "tools" -> BridgeConfig.INSTANCE.showToolCalls = arg.equals("on");
					case "clean" -> {
						ctx.getInput().pressKey(o -> o.keyToggleGui);
						ctx.waitTicks(5);
						ctx.takeScreenshot(arg);
						ctx.getInput().pressKey(o -> o.keyToggleGui);
						ctx.waitTicks(5);
					}
					case "where" -> server.runOnServer(s -> {
						ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
						System.out.printf(Locale.ROOT, "AB-SHOW at %.1f %.1f %.1f yaw %.0f pitch %.0f biome %s%n", p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot(),
							p.level().getBiome(p.blockPosition()).unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
					});
					default -> System.out.println("AB-SHOW unknown action: " + line);
				}
			}
			for (String l : AgentChat.recentLines()) System.out.println("AB-CHAT " + l);
		}
	}

	private static void copyDir(Path src, Path dst) {
		try (Stream<Path> files = Files.walk(src)) {
			for (Path p : (Iterable<Path>) files::iterator) {
				Path to = dst.resolve(src.relativize(p).toString());
				if (Files.isDirectory(p)) Files.createDirectories(to);
				else Files.copy(p, to, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Kept in its own class so Iris classes only load when Iris is installed. */
	private static final class IrisToggle {
		static void enable(String pack) {
			try {
				net.irisshaders.iris.Iris.getIrisConfig().setShaderPackName(pack);
				net.irisshaders.iris.Iris.getIrisConfig().setShadersEnabled(true);
				net.irisshaders.iris.Iris.getIrisConfig().save();
				net.irisshaders.iris.Iris.reload();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}
}

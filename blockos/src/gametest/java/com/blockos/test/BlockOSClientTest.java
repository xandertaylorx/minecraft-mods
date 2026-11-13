package com.blockos.test;

import com.blockos.block.ComputerBlockEntity;
import com.blockos.client.screen.ComputerScreen;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.RedstoneLampBlock;

/**
 * Real-client test: places computers, checks redstone / files / programs / wireless on the server,
 * then opens the computer like a player and screenshots every app.
 * Run: ./gradlew runClientGameTest   Results: "OS-TEST" lines. Screenshots: build/run/clientGameTest/screenshots
 */
public class BlockOSClientTest implements FabricClientGameTest {
	private static final BlockPos PC = new BlockPos(0, -60, 3);       // faces north, toward the player
	private static final BlockPos PC2 = new BlockPos(6, -60, 3);
	private static final BlockPos LAMP_RIGHT = new BlockPos(-1, -60, 3);  // right side of a north-facing computer = west
	private static final BlockPos INPUT_LEFT = new BlockPos(1, -60, 3);   // left side = east
	private final List<String> results = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			TestInput input = ctx.getInput();
			for (String c : List.of("gamemode creative @a", "time set 6000", "weather clear", "gamerule advance_time false",
				"tp @a 0.5 -60 0.5 0 0",
				"setblock 0 -60 3 blockos:computer[facing=north]",
				"setblock 6 -60 3 blockos:computer[facing=north]",
				"setblock -1 -60 3 minecraft:redstone_lamp",
				"setblock 1 -60 3 minecraft:redstone_block",
				"setblock 0 -59 3 minecraft:redstone_lamp")) {
				server.runCommand(c);
			}
			ctx.waitTicks(40);

			// ---------------- server-side behaviour ----------------
			cmd(server, PC, "redstone right on");
			ctx.waitTicks(4);
			check("redstone right on lights the lamp", server.computeOnServer(s -> level(s).getBlockState(LAMP_RIGHT).getValue(RedstoneLampBlock.LIT)));
			cmd(server, PC, "redstone right off");
			ctx.waitTicks(6);
			check("redstone right off turns it off", !server.computeOnServer(s -> level(s).getBlockState(LAMP_RIGHT).getValue(RedstoneLampBlock.LIT)));

			cmd(server, PC, "input left");
			check("reads redstone input on the left (15)", lastLine(server, PC).contains("left = 15"));

			cmd(server, PC, "write notes/test.txt hello from a test");
			cmd(server, PC, "cat notes/test.txt");
			check("write + cat a file", lastLine(server, PC).equals("hello from a test"));

			cmd(server, PC, "calc (2 + 3) * 4 ^ 2");
			check("calc works", lastLine(server, PC).endsWith("= 80"));

			cmd(server, PC, "nonsense");
			check("unknown commands explain themselves", lastLine(server, PC).startsWith("Unknown command"));

			// A program that blinks the top lamp 3 times then writes a file, with the screen closed
			withPc(server, PC, c -> c.writeFile("programs/test.bs", String.join("\n",
				"set n = 0",
				"repeat 3",
				"  redstone top on",
				"  wait 0.1",
				"  redstone top off",
				"  wait 0.1",
				"  set n = n + 1",
				"end",
				"if n == 3 and input(left) > 0",
				"  write result.txt done ${n * 10}",
				"else",
				"  write result.txt wrong",
				"end")));
			cmd(server, PC, "run test");
			check("program starts", server.computeOnServer(s -> "programs/test.bs".equals(pc(s, PC).runningScript())));
			ctx.waitTicks(30);
			check("program ran to the end (result.txt = 'done 30')", "done 30".equals(server.computeOnServer(s -> pc(s, PC).files().get("result.txt"))));
			check("program finished", server.computeOnServer(s -> pc(s, PC).runningScript() == null));

			withPc(server, PC, c -> c.writeFile("programs/bad.bs", "loop\n  wait 1\n"));
			cmd(server, PC, "run bad");
			check("compile errors are reported", lastLine(server, PC).contains("missing 'end'"));

			// Wireless between two computers
			cmd(server, PC2, "listen 7");
			cmd(server, PC, "send 7 hello neighbour");
			check("wireless send reports 1 receiver", lastLine(server, PC).contains("Sent to 1 computer"));
			check("other computer received it", lastLine(server, PC2).contains("hello neighbour"));
			check("message() reads the inbox", "hello neighbour".equals(server.computeOnServer(s -> pc(s, PC2).popMessage())));

			cmd(server, PC, "label Base Computer");
			cmd(server, PC, "redstone top on");     // lamp on top glows for the screenshot

			// ---------------- the real screen ----------------
			input.lookAt(PC);
			ctx.waitTicks(5);
			input.pressKey(o -> o.keyUse);
			ctx.waitTicks(30);   // boot animation
			boolean opened = ctx.computeOnClient(mc -> mc.gui.screen() instanceof ComputerScreen);
			check("right-click opens BlockOS", opened);
			if (opened) {
				ctx.takeScreenshot("01_desktop");
				app(ctx, "Terminal");
				input.typeChars("help");
				input.pressKey(InputConstants.KEY_RETURN);
				ctx.waitTicks(10);
				ctx.takeScreenshot("02_terminal_help");
				input.typeChars("ls programs");
				input.pressKey(InputConstants.KEY_RETURN);
				input.typeChars("time");
				input.pressKey(InputConstants.KEY_RETURN);
				input.typeChars("scan");
				input.pressKey(InputConstants.KEY_RETURN);
				ctx.waitTicks(10);
				ctx.takeScreenshot("03_terminal_commands");
				check("terminal typing runs commands on the computer", server.computeOnServer(s -> pc(s, PC).files() != null)
					&& ctx.computeOnClient(mc -> ((ComputerScreen) mc.gui.screen()).terminal().stream().anyMatch(l -> l.startsWith("front"))));

				app(ctx, "Files");
				ctx.takeScreenshot("04_files");
				ctx.runOnClient(mc -> ((ComputerScreen) mc.gui.screen()).openFile("programs/alarm.bs"));
				ctx.waitTicks(5);
				ctx.takeScreenshot("05_notepad_script");
				app(ctx, "Paint");
				drawSmiley(ctx);
				ctx.takeScreenshot("06_paint");
				app(ctx, "Snake");
				input.pressKey(InputConstants.KEY_SPACE);
				ctx.waitTicks(25);
				input.pressKey(InputConstants.KEY_DOWN);
				ctx.waitTicks(10);
				ctx.takeScreenshot("07_snake");
				app(ctx, "Calc");
				input.typeChars("sqrt(144)+7*3");
				input.pressKey(InputConstants.KEY_RETURN);
				ctx.waitTicks(3);
				ctx.takeScreenshot("08_calculator");
				app(ctx, "Clock");
				ctx.takeScreenshot("09_clock");
				cmd(server, PC, "listen 7");   // same channel as the other computer
				ctx.waitTicks(5);
				app(ctx, "Messenger");
				input.typeChars("anyone home?");
				input.pressKey(InputConstants.KEY_RETURN);
				ctx.waitTicks(10);
				ctx.takeScreenshot("10_messenger");
				check("messenger sends wirelessly", lastLine(server, PC2).contains("anyone home?"));
				app(ctx, "Settings");
				ctx.takeScreenshot("11_settings");
				ctx.runOnClient(mc -> {
					ComputerScreen s = (ComputerScreen) mc.gui.screen();
					s.saveFile(".theme", "ocean");
					s.saveFile(".wallpaper", "sunset");
					s.openApp((com.blockos.client.apps.App) null);
				});
				ctx.waitTicks(10);
				ctx.takeScreenshot("12_desktop_ocean_sunset");
				check("theme + wallpaper saved on the computer", "ocean".equals(server.computeOnServer(s -> pc(s, PC).files().get(".theme"))));
				ctx.runOnClient(mc -> mc.gui.screen().onClose());
				ctx.waitTicks(10);
			}
			// Back in the world: the computer, a lit lamp on top
			input.lookAt(0.0F, 15.0F);
			ctx.waitTicks(10);
			ctx.takeScreenshot("13_in_world");
		} finally {
			for (String r : this.results) System.out.println("OS-TEST " + r);
		}
	}

	// ------------------------------------------------------------------ helpers

	private static ServerLevel level(MinecraftServer s) {
		return s.overworld();
	}

	private static ComputerBlockEntity pc(MinecraftServer s, BlockPos pos) {
		return (ComputerBlockEntity) level(s).getBlockEntity(pos);
	}

	private static void withPc(TestServerContext server, BlockPos pos, java.util.function.Consumer<ComputerBlockEntity> f) {
		server.runOnServer(s -> f.accept(pc(s, pos)));
	}

	private static void cmd(TestServerContext server, BlockPos pos, String line) {
		server.runOnServer(s -> pc(s, pos).runCommand(level(s), null, line));
	}

	private static String lastLine(TestServerContext server, BlockPos pos) {
		return server.computeOnServer(s -> {
			// The computer's terminal isn't public; read it through a sync-free path
			java.lang.reflect.Field f;
			try {
				f = ComputerBlockEntity.class.getDeclaredField("terminal");
				f.setAccessible(true);
				@SuppressWarnings("unchecked")
				List<String> t = (List<String>) f.get(pc(s, pos));
				return t.isEmpty() ? "" : t.get(t.size() - 1);
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
	}

	private static void app(ClientGameTestContext ctx, String name) {
		ctx.runOnClient(mc -> ((ComputerScreen) mc.gui.screen()).openApp(name));
		ctx.waitTicks(5);
	}

	/** Draws a smiley in Paint using real mouse clicks on the canvas. */
	private static void drawSmiley(ClientGameTestContext ctx) {
		int[][] pts = {{14, 8}, {15, 8}, {14, 9}, {15, 9}, {30, 8}, {31, 8}, {30, 9}, {31, 9},
			{12, 18}, {13, 19}, {14, 20}, {15, 21}, {16, 21}, {17, 22}, {18, 22}, {19, 22}, {20, 22}, {21, 22}, {22, 22},
			{23, 22}, {24, 22}, {25, 22}, {26, 22}, {27, 22}, {28, 21}, {29, 21}, {30, 20}, {31, 19}, {32, 18}};
		Function<int[], double[]> toScreen = p -> ctx.computeOnClient(mc -> {
			ComputerScreen s = (ComputerScreen) mc.gui.screen();
			var paint = s.app(com.blockos.client.apps.PaintApp.class);
			return paint.cellCenter(p[0], p[1]);
		});
		ctx.runOnClient(mc -> ((ComputerScreen) mc.gui.screen()).app(com.blockos.client.apps.PaintApp.class).selectColor(6));
		for (int[] p : pts) {
			double[] xy = toScreen.apply(p);
			double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
			ctx.getInput().setCursorPos(xy[0] * scale, xy[1] * scale);
			ctx.getInput().pressMouse(0);
		}
		ctx.waitTicks(3);
	}

	private void check(String what, boolean ok) {
		this.results.add((ok ? "PASS " : "FAIL ") + what);
	}
}

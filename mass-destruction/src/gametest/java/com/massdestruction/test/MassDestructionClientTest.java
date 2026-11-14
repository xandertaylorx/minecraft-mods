package com.massdestruction.test;

import com.massdestruction.MassDestruction;
import com.massdestruction.client.ClientDebris;
import com.massdestruction.entity.AnnihilatorStrikeEntity;
import com.massdestruction.entity.NovaEntity;
import com.massdestruction.entity.SingularityEntity;
import com.massdestruction.entity.TornadoEntity;
import com.massdestruction.registry.ModBlocks;
import com.massdestruction.registry.ModEntities;
import com.massdestruction.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Plays through every tool in a real client, in a real village, and saves screenshots.
 * Run with: ./gradlew runClientGameTest
 * Screenshots land in build/run/clientGameTest/screenshots; results are printed as "MD-TEST" lines.
 */
public class MassDestructionClientTest implements FabricClientGameTest {
	private final List<String> results = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed("mass-destruction-7")).create()) {
			settle(ctx, sp);
			TestServerContext server = sp.getServer();
			TestInput input = ctx.getInput();
			// see (and run) things well past the default test distance, as a normal game would
			ctx.runOnClient(mc -> {
				mc.options.renderDistance().set(12);
				mc.options.simulationDistance().set(12);
			});
			ctx.waitTicks(20);
			for (String c : List.of("gamemode creative @a", "time set 6000", "weather clear", "gamerule advance_time false",
				"gamerule advance_weather false", "difficulty peaceful")) {
				server.runCommand(c);
			}
			// find a village to knock down
			BlockPos village = server.computeOnServer(s -> {
				BlockPos found = s.overworld().findNearestMapStructure(StructureTags.VILLAGE, s.overworld().getRespawnData().pos(), 60, false);
				return found == null ? s.overworld().getRespawnData().pos() : found;
			});
			results.add("INFO village at " + village.toShortString());
			view(ctx, server, village.getX(), 120, village.getZ() - 70, 0, 30);
			settle(ctx, sp);
			for (String c : List.of(
				"item replace entity @a hotbar.0 with mass_destruction:annihilator",
				"item replace entity @a hotbar.1 with mass_destruction:nova_rod",
				"item replace entity @a hotbar.2 with mass_destruction:singularity_core",
				"item replace entity @a hotbar.3 with mass_destruction:storm_caller",
				"item replace entity @a hotbar.4 with mass_destruction:rift_key")) {
				server.runCommand(c);
			}

			// --------------------------------------------------- Annihilator
			BlockPos a = ground(server, village.getX(), village.getZ());
			view(ctx, server, a.getX() + 0.5, a.getY() + 38, a.getZ() - 80, 0, 18);
			ctx.takeScreenshot("00_village_before");
			select(ctx, 0);
			input.lookAt(a);
			input.pressKey(o -> o.keyUse);
			ctx.waitTicks(3);
			int strikes = count(server, s -> s.overworld().getEntities(ModEntities.ANNIHILATOR_STRIKE, e -> true).size());
			check("Annihilator marked a target", strikes == 1);
			BlockPos hitA = strikes == 1 ? server.computeOnServer(s -> s.overworld().getEntities(ModEntities.ANNIHILATOR_STRIKE, e -> true).getFirst().blockPosition()) : a;
			results.add("INFO annihilator aimed at " + a.toShortString() + ", hit " + hitA.toShortString());
			check("Annihilator is on cooldown", server.computeOnServer(s -> player(s).getCooldowns().isOnCooldown(player(s).getInventory().getItem(0))));
			// pull back so the circles in the sky are in view
			view(ctx, server, hitA.getX() + 0.5, hitA.getY() + 12, hitA.getZ() - 62, 0, -22);
			ctx.waitTicks(55);
			ctx.takeScreenshot("01_annihilator_charging");
			ctx.waitTicks(28);
			ctx.takeScreenshot("02_annihilator_beam");
			ctx.waitTicks(40);
			ctx.takeScreenshot("03_annihilator_beam_late");
			view(ctx, server, a.getX() + 0.5, a.getY() + 30, a.getZ() - 60, 0, 25);
			boolean done = waitFor(server, s -> s.overworld().getEntities(ModEntities.ANNIHILATOR_STRIKE, e -> true).isEmpty(), 400);
			check("Annihilator strike finished", done);
			int carved = count(server, s -> air(s.overworld(), hitA.below(8), 4));
			check("Annihilator carved a deep crater (" + carved + "/729 air 8 below)", carved > 600);
			check("Annihilator left the ground burning", server.computeOnServer(s -> any(s.overworld(), hitA, 26, 16, st -> st.is(Blocks.FIRE) || st.is(Blocks.MAGMA_BLOCK))));
			ctx.waitTicks(10);
			ctx.takeScreenshot("04_annihilator_crater");

			// --------------------------------------------------- Nova
			BlockPos n = ground(server, village.getX() + 70, village.getZ() + 10);
			view(ctx, server, n.getX() + 0.5, n.getY() + 14, n.getZ() - 66, 0, -14);
			select(ctx, 1);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				s.overworld().addFreshEntity(new NovaEntity(s.overworld(), Vec3.atBottomCenterOf(n.above()), p, true));
			});
			ctx.waitTicks(46);
			results.add("INFO client sees " + ctx.computeOnClient(mc -> {
				StringBuilder b = new StringBuilder();
				for (var e : mc.level.entitiesForRendering()) if (e instanceof com.massdestruction.entity.EffectEntity fx) b.append(e.getType().toShortString()).append(" age=").append((int) fx.age(0)).append(" dist=").append((int) e.distanceTo(mc.player)).append("; ");
				return b.toString();
			}));
			ctx.takeScreenshot("05_nova_gathering");
			ctx.waitTicks(NovaEntity.GATHER + 3 - 46);
			ctx.takeScreenshot("06_nova_spark_falls");
			ctx.waitTicks(NovaEntity.FALL + 2);
			ctx.takeScreenshot("07_nova_flash");
			ctx.waitTicks(14);
			ctx.takeScreenshot("08_nova_fireball");
			ctx.waitTicks(40);
			ctx.takeScreenshot("09_nova_mushroom");
			ctx.waitTicks(80);
			ctx.takeScreenshot("10_nova_mushroom_late");
			ctx.waitTicks(80);
			ctx.takeScreenshot("11_nova_smoke");
			int novaHole = count(server, s -> air(s.overworld(), n.below(5), 3));
			check("Nova left a crater (" + novaHole + "/343 air 5 below)", novaHole > 250);
			check("Nova stripped leaves within 30 blocks", server.computeOnServer(s -> !any(s.overworld(), n.above(4), 18, 6, st -> st.is(BlockTags.LEAVES))));

			// --------------------------------------------------- Singularity
			BlockPos h = ground(server, village.getX() - 70, village.getZ() + 10);
			// a crying obsidian frame nearby: the black hole should tear it open
			BlockPos frame = h.offset(6, 0, 0);
			server.runOnServer(s -> buildFrame(s.overworld(), frame));
			view(ctx, server, h.getX() + 0.5, h.getY() + 22, h.getZ() - 62, 0, 4);
			select(ctx, 2);
			server.runOnServer(s -> s.overworld().addFreshEntity(new SingularityEntity(s.overworld(), Vec3.atBottomCenterOf(h.above()), player(s), true)));
			ctx.waitTicks(30);
			ctx.takeScreenshot("12_singularity_opening");
			ctx.waitTicks(70);
			ctx.takeScreenshot("13_singularity_feeding");
			int debris = ctx.computeOnClient(mc -> mc.level.getEntitiesOfClass(SingularityEntity.class, mc.player.getBoundingBox().inflate(200)).stream()
				.mapToInt(e -> ClientDebris.of(e.getId()).size()).sum());
			check("Black hole is pulling blocks into its disc (" + debris + " on the client)", debris > 20);
			ctx.waitTicks(80);
			ctx.takeScreenshot("14_singularity_big");
			view(ctx, server, h.getX() + 0.5, h.getY() + SingularityEntity.HEIGHT + 3, h.getZ() - 30, 0, 4);
			ctx.waitTicks(5);
			ctx.takeScreenshot("15_singularity_close");
			int eaten = count(server, s -> air(s.overworld(), h.below(2), 5));
			check("Black hole ate the ground under it (" + eaten + "/1331 air)", eaten > 700);
			check("Black hole tore the crying obsidian frame open", server.computeOnServer(s -> any(s.overworld(), frame, 4, 5, st -> st.is(ModBlocks.RIFT_PORTAL))));
			// shut it with sneak + use
			input.holdShift();
			ctx.waitTicks(2);
			input.pressKey(o -> o.keyUse);
			input.releaseShift();
			ctx.waitTicks(6);
			ctx.takeScreenshot("16_singularity_collapsing");
			ctx.waitTicks(10);
			ctx.takeScreenshot("17_singularity_burst");
			boolean shut = waitFor(server, s -> s.overworld().getEntities(ModEntities.SINGULARITY, e -> true).isEmpty(), 120);
			check("Sneak + use shut the black hole and it burst", shut);
			view(ctx, server, h.getX() + 0.5, h.getY() + 30, h.getZ() - 40, 0, 30);
			ctx.waitTicks(30);
			ctx.takeScreenshot("18_singularity_after");

			// --------------------------------------------------- Tornado
			BlockPos t = ground(server, village.getX(), village.getZ() + 80);
			view(ctx, server, t.getX() + 0.5, t.getY() + 18, t.getZ() - 75, 0, -8);
			select(ctx, 3);
			input.lookAt(t);
			input.pressKey(o -> o.keyUse);
			ctx.waitTicks(3);
			check("Storm Caller called a tornado", count(server, s -> s.overworld().getEntities(ModEntities.TORNADO, e -> true).size()) == 1);
			ctx.waitTicks(36);
			ctx.takeScreenshot("19_tornado_gathering");
			ctx.waitTicks(TornadoEntity.GATHER - 39 + 2);
			ctx.takeScreenshot("20_tornado_strike");
			ctx.waitTicks(40);
			ctx.takeScreenshot("21_tornado_down");
			Vec3 before = server.computeOnServer(s -> s.overworld().getEntities(ModEntities.TORNADO, e -> true).getFirst().position());
			// point the Storm Caller off to the right: it should follow
			BlockPos aim = ground(server, t.getX() + 30, t.getZ() - 10);
			input.lookAt(aim);
			ctx.waitTicks(90);
			ctx.takeScreenshot("22_tornado_steered");
			Vec3 after = server.computeOnServer(s -> s.overworld().getEntities(ModEntities.TORNADO, e -> true).getFirst().position());
			check("Tornado followed where the Storm Caller pointed (" + fmt(before.distanceTo(Vec3.atCenterOf(aim))) + " -> " + fmt(after.distanceTo(Vec3.atCenterOf(aim))) + ")",
				after.distanceTo(Vec3.atCenterOf(aim)) < before.distanceTo(Vec3.atCenterOf(aim)) - 3);
			int carried = ctx.computeOnClient(mc -> mc.level.getEntitiesOfClass(TornadoEntity.class, mc.player.getBoundingBox().inflate(200)).stream()
				.mapToInt(e -> ClientDebris.of(e.getId()).size()).sum());
			check("Tornado is carrying torn-up blocks (" + carried + ")", carried > 5);
			view(ctx, server, after.x - 22, after.y + 6, after.z - 22, -45, -12);
			ctx.waitTicks(20);
			ctx.takeScreenshot("23_tornado_close");
			input.holdShift();
			ctx.waitTicks(2);
			input.pressKey(o -> o.keyUse);
			input.releaseShift();
			boolean lifted = waitFor(server, s -> s.overworld().getEntities(ModEntities.TORNADO, e -> true).isEmpty(), 120);
			check("Sneak + use sent the tornado away", lifted);

			// --------------------------------------------------- The Rift
			BlockPos r = ground(server, village.getX() + 30, village.getZ() - 40);
			BlockPos portal = r.above();
			server.runOnServer(s -> buildFrame(s.overworld(), portal));
			view(ctx, server, portal.getX() + 1.0, portal.getY() + 1.5, portal.getZ() - 5, 0, 5);
			select(ctx, 4);
			boolean lit = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				BlockPos floor = portal.offset(1, 0, 0);   // the bottom bar of the frame, clicked on its top face
				BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
				new ItemStack(ModItems.RIFT_KEY).useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, hit));
				return s.overworld().getBlockState(floor.above()).is(ModBlocks.RIFT_PORTAL);
			});
			check("Rift Key tore the frame open", lit);
			ctx.waitTicks(20);
			ctx.takeScreenshot("24_rift_portal");
			server.runCommand("tp @a " + (portal.getX() + 2) + " " + (portal.getY() + 1) + " " + (portal.getZ() + 0.5));
			boolean crossed = waitFor(server, s -> player(s).level().dimension() == MassDestruction.RIFT, 200);
			check("Walking into the portal goes to the Rift", crossed);
			settle(ctx, sp);
			ctx.waitTicks(60);
			ctx.takeScreenshot("25_rift_arrival");
			BlockPos arrival = server.computeOnServer(s -> player(s).blockPosition());
			boolean exit = server.computeOnServer(s -> any(s.getLevel(MassDestruction.RIFT), player(s).blockPosition(), 3, 4, st -> st.is(ModBlocks.RIFT_PORTAL)));
			check("A way back was built in the Rift", exit);
			// look round the Rift
			server.runCommand("execute in mass_destruction:the_rift run tp @a ~ 225 ~-60 0 32");
			flying(server);
			settle(ctx, sp);
			ctx.waitTicks(40);
			ctx.takeScreenshot("26_rift_vista");
			server.runCommand("execute in mass_destruction:the_rift run tp @a ~ ~ ~ 180 10");
			ctx.waitTicks(40);
			ctx.takeScreenshot("27_rift_vista_2");
			String biomes = server.computeOnServer(s -> {
				ServerLevel rift = s.getLevel(MassDestruction.RIFT);
				java.util.Set<String> seen = new java.util.TreeSet<>();
				BlockPos c = player(s).blockPosition();
				for (int dx = -800; dx <= 800; dx += 100) {
					for (int dz = -800; dz <= 800; dz += 100) {
						seen.add(rift.getBiome(new BlockPos(c.getX() + dx, 90, c.getZ() + dz)).unwrapKey().map(k -> k.identifier().getPath()).orElse("?"));
					}
				}
				return String.join(",", seen);
			});
			check("The Rift has its three biomes (" + biomes + ")", biomes.contains("crystal_wastes") && biomes.contains("blossom_reach") && biomes.contains("ember_isles"));
			// and back again through the way home
			BlockPos home = server.computeOnServer(s -> {
				ServerLevel rift = s.getLevel(MassDestruction.RIFT);
				return BlockPos.betweenClosedStream(arrival.offset(-4, -4, -4), arrival.offset(4, 4, 4))
					.filter(p -> rift.getBlockState(p).is(ModBlocks.RIFT_PORTAL)).findFirst().map(BlockPos::immutable).orElse(null);
			});
			if (home != null) {
				server.runCommand("execute in mass_destruction:the_rift run tp @a " + (home.getX() + 0.5) + " " + home.getY() + " " + (home.getZ() + 0.5));
				boolean back = waitFor(server, s -> player(s).level().dimension() == Level.OVERWORLD, 200);
				check("The way home goes back to the Overworld", back);
			} else {
				check("Found the way home", false);
			}
		} finally {
			for (String r : this.results) System.out.println("MD-TEST " + r);
		}
	}

	// ------------------------------------------------------------------ helpers

	private void check(String what, boolean ok) {
		this.results.add((ok ? "PASS " : "FAIL ") + what);
	}

	private static String fmt(double d) {
		return String.format("%.1f", d);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	/** Waits for the world round the camera to be drawn (or 8 seconds, whichever comes first). */
	private static void settle(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		try {
			sp.getConnection().waitForChunksRender();
		} catch (Throwable t) {
			ctx.waitTicks(160);
		}
	}

	private static void select(ClientGameTestContext ctx, int slot) {
		ctx.getInput().pressKey(o -> o.keyHotbarSlots[slot]);
		ctx.waitTicks(5);
	}

	private static void flying(TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		});
	}

	/** Puts the camera (the player, flying) somewhere, looking a given way. */
	private static void view(ClientGameTestContext ctx, TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand(String.format("execute in minecraft:overworld run tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		flying(server);
		ctx.waitTicks(4);
		ctx.getInput().lookAt(yaw, pitch);
		ctx.waitTicks(2);
	}

	/** The top solid block at x, z (loads the chunk). */
	private static BlockPos ground(TestServerContext server, int x, int z) {
		return server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			level.getChunk(x >> 4, z >> 4);
			return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
		});
	}

	/** Air blocks in a cube of half-size r round c. */
	private static int air(ServerLevel level, BlockPos c, int r) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) if (level.getBlockState(p).isAir()) n++;
		return n;
	}

	private static boolean any(ServerLevel level, BlockPos c, int r, int h, Predicate<net.minecraft.world.level.block.state.BlockState> test) {
		return BlockPos.betweenClosedStream(c.offset(-r, -h, -r), c.offset(r, h, r)).anyMatch(p -> test.test(level.getBlockState(p)));
	}

	/** A 4 wide, 5 tall crying obsidian frame standing on {@code base} along x, with air inside. */
	private static void buildFrame(ServerLevel level, BlockPos base) {
		for (int i = -5; i <= 8; i++) {
			for (int y = 0; y <= 12; y++) {
				for (int dz = -6; dz <= 4; dz++) level.setBlock(base.offset(i, y, dz), Blocks.AIR.defaultBlockState(), 3);
			}
		}
		for (int i = 0; i <= 3; i++) {
			for (int y = 0; y <= 4; y++) {
				boolean edge = i == 0 || i == 3 || y == 0 || y == 4;
				if (edge) level.setBlock(base.offset(i, y, 0), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
			}
		}
		for (int i = -1; i <= 4; i++) {
			for (int dz = -2; dz <= 2; dz++) level.setBlock(base.offset(i, -1, dz), Blocks.STONE.defaultBlockState(), 3);
		}
	}

	private static int count(TestServerContext server, java.util.function.Function<MinecraftServer, Integer> f) {
		return server.computeOnServer(f::apply);
	}

	private static boolean waitFor(TestServerContext server, Predicate<MinecraftServer> condition, int timeout) {
		try {
			server.waitFor(condition, timeout);
			return true;
		} catch (Throwable t) {
			return false;
		}
	}
}

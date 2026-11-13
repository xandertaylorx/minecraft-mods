package com.heroarsenal.test;

import com.heroarsenal.registry.ModEntities;
import com.heroarsenal.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;

/**
 * Plays through every item in a real client and saves screenshots.
 * Run with: ./gradlew runClientGameTest
 * Screenshots land in build/run/clientGameTest/screenshots, results are printed as "HA-TEST" lines.
 */
public class HeroArsenalClientTest implements FabricClientGameTest {
	private final List<String> results = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			TestServerContext server = sp.getServer();
			TestInput input = ctx.getInput();

			for (String c : List.of(
				"gamemode creative @a", "time set noon", "weather clear", "difficulty easy",
				"tp @a 0 -60 0 0 0",
				"fill -8 -60 16 8 -55 16 minecraft:stone",
				"item replace entity @a hotbar.0 with hero_arsenal:mjolnir",
				"item replace entity @a hotbar.1 with hero_arsenal:star_shield",
				"item replace entity @a hotbar.2 with hero_arsenal:pulse_gauntlet",
				"item replace entity @a hotbar.3 with hero_arsenal:web_shooter",
				"item replace entity @a hotbar.4 with hero_arsenal:orbital_laser"
			)) {
				server.runCommand(c);
			}
			ctx.waitTicks(40);
			input.lookAt(0.0F, 10.0F);

			// --- How things look in hand ---
			selectSlot(ctx, 0);
			ctx.takeScreenshot("01_mjolnir_first_person");
			camera(ctx, CameraType.THIRD_PERSON_FRONT);
			ctx.takeScreenshot("02_mjolnir_third_person");

			selectSlot(ctx, 1);
			ctx.takeScreenshot("03_shield_third_person");
			input.holdKey(o -> o.keyUse);
			ctx.waitTicks(10);
			ctx.takeScreenshot("04_shield_blocking_third_person");
			input.releaseKey(o -> o.keyUse);
			camera(ctx, CameraType.FIRST_PERSON);
			ctx.takeScreenshot("05_shield_first_person");
			input.holdKey(o -> o.keyUse);
			ctx.waitTicks(10);
			ctx.takeScreenshot("06_shield_blocking_first_person");
			input.releaseKey(o -> o.keyUse);

			selectSlot(ctx, 2);
			ctx.takeScreenshot("07_gauntlet_first_person");
			selectSlot(ctx, 3);
			ctx.takeScreenshot("08_web_shooter_first_person");

			// --- Mjolnir: throw, lightning, return ---
			spawnHusks(server, "0 -60 8", "2 -60 11", "-2 -60 12");
			selectSlot(ctx, 0);
			input.lookAt(0.0F, 8.0F);
			input.holdKeyFor(o -> o.keyUse, 25);
			ctx.waitTicks(3);
			ctx.takeScreenshot("09_mjolnir_flying");
			ctx.waitTicks(4);
			ctx.takeScreenshot("10_mjolnir_impact");
			boolean back = waitFor(server, s -> player(s).getInventory().getItem(0).is(ModItems.MJOLNIR), 200);
			check("Mjolnir returned to slot 1", back);
			check("Mjolnir hurt/killed a husk", countHurtOrDeadHusks(server) >= 1);
			ctx.takeScreenshot("11_mjolnir_caught");

			// --- Star Shield: throw, ricochet, return ---
			server.runCommand("kill @e[type=husk]");
			spawnHusks(server, "0 -60 8", "4 -60 10", "-4 -60 11");
			ctx.waitTicks(10);
			selectSlot(ctx, 1);
			input.lookAt(0.0F, 8.0F);
			input.holdShift();
			ctx.waitTicks(2);
			input.pressKey(o -> o.keyUse);
			input.releaseShift();
			ctx.waitTicks(4);
			ctx.takeScreenshot("12_shield_flying");
			boolean shieldBack = waitFor(server, s -> player(s).getInventory().getItem(1).is(ModItems.STAR_SHIELD), 200);
			check("Shield returned to slot 2", shieldBack);
			int hurt = countHurtOrDeadHusks(server);
			check("Shield bounced into 2+ husks (hit " + hurt + ")", hurt >= 2);

			// --- Pulse Gauntlet: blast + thrust ---
			server.runCommand("kill @e[type=husk]");
			spawnHusks(server, "0 -60 8");
			ctx.waitTicks(10);
			selectSlot(ctx, 2);
			input.lookAt(0.0F, 8.0F);
			input.pressKey(o -> o.keyUse);
			ctx.waitTicks(1);
			ctx.takeScreenshot("13_gauntlet_blast");
			ctx.waitTicks(10);
			// Test husks have NoAI, which also freezes knockback, so check damage instead.
			check("Gauntlet blast hit the husk", countHurtOrDeadHusks(server) >= 3);

			double yBefore = server.computeOnServer(s -> player(s).getY());
			ctx.waitTicks(20); // cooldown
			input.lookAt(0.0F, -45.0F);
			input.holdShift();
			ctx.waitTicks(2);
			input.pressKey(o -> o.keyUse);
			input.releaseShift();
			ctx.waitTicks(6);
			ctx.takeScreenshot("14_gauntlet_thrust");
			double yAfter = server.computeOnServer(s -> player(s).getY());
			check("Gauntlet thrust launched the player (y " + fmt(yBefore) + " -> " + fmt(yAfter) + ")", yAfter > yBefore + 1.5);

			// --- Web Shooter: stick a mob, then swing ---
			server.runCommand("kill @e[type=husk]");
			server.runCommand("tp @a 0 -60 0 0 0");
			spawnHusks(server, "0 -60 6");
			ctx.waitTicks(30);
			selectSlot(ctx, 3);
			input.lookAt(0.0F, 10.0F);
			input.pressKey(o -> o.keyUse);
			ctx.waitTicks(8);
			ctx.takeScreenshot("15_web_hit");
			boolean slowed = server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.HUSK, h -> true).stream()
				.anyMatch(h -> h.hasEffect(MobEffects.SLOWNESS)));
			check("Web slowed the husk", slowed);

			server.runCommand("kill @e[type=husk]");
			ctx.waitTicks(15);
			double zBefore = server.computeOnServer(s -> player(s).getZ());
			input.lookAt(0.0F, -10.0F);
			input.pressKey(o -> o.keyUse);
			ctx.waitTicks(10);
			ctx.takeScreenshot("16_web_swing");
			double zAfter = server.computeOnServer(s -> player(s).getZ());
			check("Web swing pulled the player toward the wall (z " + fmt(zBefore) + " -> " + fmt(zAfter) + ")", zAfter > zBefore + 2.0);

			// --- Orbital Laser: mark, charge, beam, crater, cooldown ---
			server.runCommand("kill @e[type=husk]");
			server.runCommand("tp @a 0 -60 -40 0 0");
			spawnHusks(server, "0 -60 -28", "2 -60 -27", "-2 -60 -29");
			ctx.waitTicks(30);
			selectSlot(ctx, 4);
			ctx.takeScreenshot("17_orbital_laser_first_person");
			input.lookAt(0.0F, 8.0F);
			input.pressKey(o -> o.keyUse);
			ctx.waitTicks(2);
			int strikes = countStrikes(server);
			check("Orbital Laser marked a target (" + strikes + " strike)", strikes == 1);
			boolean cooling = server.computeOnServer(s -> player(s).getCooldowns().isOnCooldown(player(s).getInventory().getItem(4)));
			check("Orbital Laser is on cooldown after use", cooling);
			input.pressKey(o -> o.keyUse);
			ctx.waitTicks(2);
			check("Orbital Laser can't be spammed during cooldown", countStrikes(server) == 1);
			int healthyDuringCharge = 3 - countHurtOrDeadHusks(server);
			check("Nothing is hurt while it charges", healthyDuringCharge == 3);
			ctx.waitTicks(25);
			ctx.takeScreenshot("18_orbital_laser_charging");
			camera(ctx, CameraType.THIRD_PERSON_BACK);
			ctx.waitTicks(22);
			ctx.takeScreenshot("19_orbital_laser_beam");
			ctx.waitTicks(10);
			ctx.takeScreenshot("20_orbital_laser_beam_late");
			int hurtByLaser = countHurtOrDeadHusks(server);
			check("Orbital beam hit all 3 husks (hit " + hurtByLaser + ")", hurtByLaser == 3);
			boolean gone = waitFor(server, s -> s.overworld().getEntities(ModEntities.ORBITAL_STRIKE, e -> true).isEmpty(), 120);
			check("Strike finished and cleaned itself up", gone);
			int carved = server.computeOnServer(s -> {
				int air = 0;
				for (int dx = -3; dx <= 3; dx++) {
					for (int dz = -3; dz <= 3; dz++) {
						for (int y = -63; y <= -61; y++) {
							if (s.overworld().getBlockState(new BlockPos(dx, y, -28 + dz)).isAir()) {
								air++;
							}
						}
					}
				}
				return air;
			});
			check("Beam carved a crater (" + carved + " of 147 ground blocks gone)", carved > 100);
			boolean fire = server.computeOnServer(s -> BlockPos.betweenClosedStream(-8, -64, -36, 8, -58, -20)
				.anyMatch(p -> s.overworld().getBlockState(p).is(Blocks.FIRE)));
			check("Crater is on fire", fire);
			ctx.takeScreenshot("21_orbital_laser_crater");
			camera(ctx, CameraType.FIRST_PERSON);
		} finally {
			for (String r : this.results) {
				System.out.println("HA-TEST " + r);
			}
		}
	}

	private void check(String what, boolean ok) {
		this.results.add((ok ? "PASS " : "FAIL ") + what);
	}

	private static String fmt(double d) {
		return String.format("%.1f", d);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void selectSlot(ClientGameTestContext ctx, int slot) {
		ctx.getInput().pressKey(o -> o.keyHotbarSlots[slot]);
		ctx.waitTicks(10);
	}

	private static void camera(ClientGameTestContext ctx, CameraType type) {
		ctx.runOnClient(mc -> mc.options.setCameraType(type));
		ctx.waitTicks(5);
	}

	private static void spawnHusks(TestServerContext server, String... positions) {
		for (String pos : positions) {
			server.runCommand("summon husk " + pos + " {NoAI:1b,PersistenceRequired:1b}");
		}
	}

	private static double huskZ(TestServerContext server) {
		return server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.HUSK, h -> true).stream()
			.mapToDouble(Husk::getZ).findFirst().orElse(Double.NaN));
	}

	/** Husks start at full health and are summoned 3 at a time; counts how many got hit. */
	private static int countHurtOrDeadHusks(TestServerContext server) {
		return server.computeOnServer(s -> {
			List<? extends Husk> husks = s.overworld().getEntities(EntityTypes.HUSK, h -> true);
			int hurt = (int) husks.stream().filter(h -> h.getHealth() < h.getMaxHealth()).count();
			return hurt + (3 - husks.size());
		});
	}

	private static int countStrikes(TestServerContext server) {
		return server.computeOnServer(s -> s.overworld().getEntities(ModEntities.ORBITAL_STRIKE, e -> true).size());
	}

	private static boolean waitFor(TestServerContext server, Predicate<MinecraftServer> condition, int timeout) {
		try {
			server.waitFor(condition, timeout);
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	@SuppressWarnings("unused")
	private static boolean holding(MinecraftServer s, int slot, Item item) {
		return player(s).getInventory().getItem(slot).is(item);
	}
}

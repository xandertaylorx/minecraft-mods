package com.betterdogs.test;

import com.betterdogs.DogLogic;
import com.betterdogs.DogSettings;
import com.betterdogs.Net;
import com.betterdogs.client.DogScreen;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Real-client test: tames a dog, opens its settings with sneak + right-click, clicks the buttons and checks every feature
 * on the server. Run: ./gradlew runClientGameTest   Results: "DOG-TEST" lines. Screenshots: build/run/clientGameTest/screenshots
 */
public class BetterDogsClientTest implements FabricClientGameTest {
	private final List<String> results = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			TestInput input = ctx.getInput();
			for (String c : List.of("gamemode creative @a", "time set 6000", "weather clear", "gamerule advance_time false",
				"gamerule spawn_mobs false", "tp @a 0.5 -60 0.5 0 0")) {
				server.runCommand(c);
			}
			ctx.waitTicks(40);

			// A tamed, sitting dog right in front of the player
			server.runOnServer(s -> {
				Wolf w = EntityTypes.WOLF.create(level(s), EntitySpawnReason.COMMAND);
				w.snapTo(0.5, -60, 2.5, 180, 0);
				level(s).addFreshEntity(w);
				w.tame(player(s));
				w.setOrderedToSit(true);
				w.setInSittingPose(true);
			});
			ctx.waitTicks(10);
			check("dog is tamed by the player", server.computeOnServer(s -> dog(s).isOwnedBy(player(s))));

			// ---------------- opening the screen ----------------
			input.lookAt(new BlockPos(0, -60, 2));
			ctx.waitTicks(5);
			input.pressKey(o -> o.keyUse);
			ctx.waitTicks(10);
			check("plain right-click does NOT open the menu (vanilla sit/stand still works)", ctx.computeOnClient(mc -> !(mc.gui.screen() instanceof DogScreen)));
			check("plain right-click still toggles sitting", server.computeOnServer(s -> !dog(s).isOrderedToSit()));
			server.runOnServer(s -> {
				dog(s).setOrderedToSit(true);
				dog(s).setInSittingPose(true);
				dog(s).snapTo(0.5, -60, 2.5, 180, 0);
			});
			ctx.waitTicks(5);
			input.holdKey(o -> o.keyShift);
			ctx.waitTicks(3);
			input.pressKey(o -> o.keyUse);
			ctx.waitTicks(10);
			input.releaseKey(o -> o.keyShift);
			boolean opened = ctx.computeOnClient(mc -> mc.gui.screen() instanceof DogScreen);
			check("sneak + right-click opens the dog settings", opened);
			if (opened) {
				ctx.takeScreenshot("01_settings");
				click(ctx, "Invincible");
				ctx.waitTicks(5);
				check("clicking Invincible turns it on (server)", server.computeOnServer(s -> DogLogic.settings(dog(s)).invincible()));
				click(ctx, "Auto-collect");
				click(ctx, "Auto-collect");
				ctx.waitTicks(5);
				check("Auto-collect cycles to Good stuff", server.computeOnServer(s -> DogLogic.settings(dog(s)).collect() == DogSettings.Collect.GOOD));
				click(ctx, "Glow");
				click(ctx, "Collar");
				ctx.waitTicks(5);
				check("collar button changes the collar color", server.computeOnServer(s -> dog(s).getCollarColor() != DyeColor.RED));
				ctx.runOnClient(mc -> mc.gui.screen().onClose());
				ctx.waitTicks(5);
				input.holdKey(o -> o.keyShift);
				ctx.waitTicks(3);
				input.pressKey(o -> o.keyUse);
				ctx.waitTicks(10);
				input.releaseKey(o -> o.keyShift);
				check("reopened screen shows the saved settings", ctx.computeOnClient(mc -> mc.gui.screen() instanceof DogScreen ds
					&& ds.settings().invincible() && ds.settings().glow()));
				ctx.takeScreenshot("02_settings_changed");
				ctx.runOnClient(mc -> {
					if (mc.gui.screen() != null) mc.gui.screen().onClose();
				});
				ctx.waitTicks(5);
			}

			// ---------------- invincible / owner safe ----------------
			change(server, "invincible", "true");
			check("invincible: survives 1000 damage, health untouched", server.computeOnServer(s -> {
				Wolf w = dog(s);
				float before = w.getHealth();
				w.hurtServer(level(s), level(s).damageSources().generic(), 1000);
				w.hurtServer(level(s), level(s).damageSources().lava(), 50);
				return w.isAlive() && w.getHealth() == before;
			}));
			change(server, "invincible", "false");
			ctx.waitTicks(12);
			check("safe from you: owner's hits do nothing", server.computeOnServer(s -> {
				Wolf w = dog(s);
				float before = w.getHealth();
				w.hurtServer(level(s), level(s).damageSources().playerAttack(player(s)), 6);
				return w.getHealth() == before;
			}));
			ctx.waitTicks(12);
			check("not invincible: other damage still hurts", server.computeOnServer(s -> {
				Wolf w = dog(s);
				float before = w.getHealth();
				w.hurtServer(level(s), level(s).damageSources().generic(), 6);
				return w.getHealth() < before;
			}));

			// ---------------- regeneration ----------------
			change(server, "regen", "true");
			float hpBefore = server.computeOnServer(s -> dog(s).getHealth());
			ctx.waitTicks(90);
			check("regeneration heals over time", server.computeOnServer(s -> dog(s).getHealth()) > hpBefore);

			// ---------------- auto-collect ----------------
			server.runOnServer(s -> {
				dog(s).setOrderedToSit(false);
				dog(s).setInSittingPose(false);
				player(s).getInventory().clearContent();
			});
			change(server, "collect", "FOOD");
			drop(server, Items.COOKED_BEEF, 5, 7, -60, 6);
			drop(server, Items.DIRT, 3, -6, -60, 6);
			input.lookAt(-35.0F, 25.0F);
			ctx.waitTicks(25);
			ctx.takeScreenshot("03_fetching");
			waitForItem(ctx, server, Items.COOKED_BEEF, 300);
			check("Food only: fetched the cooked beef into the player's inventory", countInv(server, Items.COOKED_BEEF) == 5);
			ctx.waitTicks(40);
			check("Food only: left the dirt on the ground", countInv(server, Items.DIRT) == 0 && groundCount(server, Items.DIRT) == 3);

			change(server, "collect", "GOOD");
			drop(server, Items.DIAMOND, 2, 6, -60, -5);
			drop(server, Items.ROTTEN_FLESH, 4, -6, -60, -5);
			waitForItem(ctx, server, Items.DIAMOND, 300);
			check("Good stuff: fetched the diamonds", countInv(server, Items.DIAMOND) == 2);
			ctx.waitTicks(40);
			check("Good stuff: skipped rotten flesh and dirt", countInv(server, Items.ROTTEN_FLESH) == 0 && countInv(server, Items.DIRT) == 0);

			change(server, "collect", "ALL");
			waitForItem(ctx, server, Items.DIRT, 300);
			waitForItem(ctx, server, Items.ROTTEN_FLESH, 300);
			check("Everything: fetched the dirt and rotten flesh too", countInv(server, Items.DIRT) == 3 && countInv(server, Items.ROTTEN_FLESH) == 4);
			check("items-fetched counter went up", server.computeOnServer(s -> DogLogic.settings(dog(s)).collected()) == 14);

			server.runOnServer(s -> {
				ItemEntity thrown = new ItemEntity(level(s), 4.5, -60, 3.5, new ItemStack(Items.COOKED_BEEF, 1));
				thrown.setThrower(player(s));
				level(s).addFreshEntity(thrown);
			});
			ctx.waitTicks(100);
			check("never picks up items the owner threw away", groundCount(server, Items.COOKED_BEEF) == 1);
			change(server, "collect", "OFF");

			// ---------------- glow, swift, rename, collar ----------------
			change(server, "swift", "true");
			change(server, "glow", "true");
			ctx.waitTicks(2);
			check("glow outlines the dog", server.computeOnServer(s -> dog(s).hasGlowingTag()));
			check("swift paws adds a speed boost", server.computeOnServer(s -> dog(s).getAttribute(
				net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getValue() > 0.4));
			change(server, "name", "Biscuit");
			change(server, "collar", "lime");
			check("rename + collar from the menu", server.computeOnServer(s -> "Biscuit".equals(dog(s).getCustomName().getString())
				&& dog(s).getCollarColor() == DyeColor.LIME));

			// ---------------- call to me ----------------
			server.runOnServer(s -> dog(s).teleportTo(20.5, -60, 20.5));
			ctx.waitTicks(3);
			change(server, "call", "");
			check("Call to me teleports the dog to you", server.computeOnServer(s -> dog(s).distanceToSqr(player(s)) < 4));

			// ---------------- behavior ----------------
			change(server, "behavior", "PASSIVE");
			server.runOnServer(s -> {
				Husk h = spawnHusk(s, 5.5, 3.5);
				dog(s).setTarget(h);
			});
			ctx.waitTicks(5);
			check("Passive: never keeps a target", server.computeOnServer(s -> dog(s).getTarget() == null));

			change(server, "danger_bark", "true");
			ctx.waitTicks(60);
			check("Danger alert makes hostile mobs near you glow", server.computeOnServer(s ->
{
				var husks = level(s).getEntitiesOfClass(Husk.class, player(s).getBoundingBox().inflate(16));
				return !husks.isEmpty() && husks.stream().allMatch(h -> h.hasEffect(MobEffects.GLOWING));
			}));

			int killsBefore = server.computeOnServer(s -> DogLogic.settings(dog(s)).kills());
			change(server, "behavior", "GUARD");
			change(server, "invincible", "true");
			ctx.waitTicks(20);
			ctx.takeScreenshot("04_guard");
			check("Guard: goes after a hostile mob near you on its own", server.computeOnServer(s -> dog(s).getTarget() instanceof Husk));
			for (int i = 0; i < 60 && !server.computeOnServer(s -> level(s).getEntitiesOfClass(Husk.class, player(s).getBoundingBox().inflate(20)).isEmpty()); i++) {
				ctx.waitTicks(10);
			}
			check("Guard: defeats it and counts the kill", server.computeOnServer(s -> DogLogic.settings(dog(s)).kills()) > killsBefore);

			// ---------------- saving ----------------
			check("settings survive save/load (codec round trip)", server.computeOnServer(s -> {
				DogSettings now = DogLogic.settings(dog(s));
				var json = DogSettings.CODEC.encodeStart(JsonOps.INSTANCE, now).getOrThrow();
				return DogSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().equals(now);
			}));

			server.runOnServer(s -> {
				Wolf w = dog(s);
				w.teleportTo(0.5, -60, 3.0);
				w.setOrderedToSit(true);
				w.setInSittingPose(true);
			});
			input.lookAt(0.0F, 35.0F);
			ctx.waitTicks(30);
			ctx.takeScreenshot("05_in_world");
		} finally {
			for (String r : this.results) System.out.println("DOG-TEST " + r);
		}
	}

	// ------------------------------------------------------------------ helpers

	private static ServerLevel level(MinecraftServer s) {
		return s.overworld();
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().get(0);
	}

	private static Wolf dog(MinecraftServer s) {
		return level(s).getEntitiesOfClass(Wolf.class, player(s).getBoundingBox().inflate(64)).get(0);
	}

	private static void change(TestServerContext server, String key, String value) {
		server.runOnServer(s -> Net.apply(dog(s), player(s), key, value));
	}

	private static Husk spawnHusk(MinecraftServer s, double x, double z) {
		Husk h = EntityTypes.HUSK.create(level(s), EntitySpawnReason.COMMAND);
		h.snapTo(x, -60, z, 0, 0);
		h.setPersistenceRequired();
		level(s).addFreshEntity(h);
		return h;
	}

	private static void drop(TestServerContext server, net.minecraft.world.item.Item item, int count, double x, double y, double z) {
		server.runOnServer(s -> {
			ItemEntity e = new ItemEntity(level(s), x + 0.5, y, z + 0.5, new ItemStack(item, count));
			e.setDeltaMovement(0, 0, 0);
			level(s).addFreshEntity(e);
		});
	}

	private static int countInv(TestServerContext server, net.minecraft.world.item.Item item) {
		return server.computeOnServer(s -> player(s).getInventory().countItem(item));
	}

	private static int groundCount(TestServerContext server, net.minecraft.world.item.Item item) {
		return server.computeOnServer(s -> level(s).getEntitiesOfClass(ItemEntity.class, player(s).getBoundingBox().inflate(30),
			e -> e.getItem().is(item)).stream().mapToInt(e -> e.getItem().getCount()).sum());
	}

	private static void waitForItem(ClientGameTestContext ctx, TestServerContext server, net.minecraft.world.item.Item item, int ticks) {
		for (int i = 0; i < ticks / 5 && countInv(server, item) == 0; i++) ctx.waitTicks(5);
		ctx.waitTicks(2);
	}

	/** Clicks the screen button whose label starts with the given text, with the real mouse. */
	private static void click(ClientGameTestContext ctx, String label) {
		Function<String, double[]> find = l -> ctx.computeOnClient(mc -> {
			for (var c : mc.gui.screen().children()) {
				if (c instanceof AbstractWidget w && w.getMessage().getString().startsWith(l)) {
					double scale = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
					return new double[] {(w.getX() + w.getWidth() / 2.0) * scale, (w.getY() + w.getHeight() / 2.0) * scale};
				}
			}
			return null;
		});
		double[] xy = find.apply(label);
		if (xy == null) throw new AssertionError("No button " + label);
		ctx.getInput().setCursorPos(xy[0], xy[1]);
		ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(3);
	}

	private void check(String what, boolean ok) {
		this.results.add((ok ? "PASS " : "FAIL ") + what);
	}
}

package com.petdragons.test;

import com.petdragons.DragonColor;
import com.petdragons.block.DragonEggBlock;
import com.petdragons.entity.DragonEntity;
import com.petdragons.registry.ModBlocks;
import com.petdragons.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Raises dragons in a real client and saves screenshots: each size, each colour, a hatching egg, a ride, flight and breath.
 * Run with: ./gradlew runClientGameTest
 * Screenshots land in build/run/clientGameTest/screenshots; results are printed as "PD-TEST" lines.
 */
public class PetDragonsClientTest implements FabricClientGameTest {
	private final List<String> results = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> s.setSeed("pet-dragons-3")).create()) {
			settle(ctx, sp);
			TestServerContext server = sp.getServer();
			TestInput input = ctx.getInput();
			for (String c : List.of("gamemode creative @a", "time set 6000", "weather clear", "gamerule advance_time false", "gamerule advance_weather false", "difficulty peaceful")) {
				server.runCommand(c);
			}
			BlockPos origin = ground(server, 0, 0);
			BlockPos base = origin.above(30);
			// a flat stone stage high enough to be clear of trees
			server.runOnServer(s -> {
				for (int x = -30; x <= 30; x++) for (int z = -30; z <= 30; z++) {
					s.overworld().setBlock(base.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 2);
					for (int y = 1; y <= 12; y++) s.overworld().setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
				}
			});
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

			// ------------------------------------------------ every colour, every size
			DragonColor[] colors = DragonColor.values();
			for (int i = 0; i < colors.length; i++) {
				final int idx = i;
				server.runOnServer(s -> spawn(s, base.offset(-8 + idx * 4, 1, 0), colors[idx], idx < 2 ? 0 : idx < 4 ? DragonEntity.JUVENILE_AT : DragonEntity.ADULT_AT, true));
			}
			ctx.waitTicks(10);
			view(ctx, server, base.getX() + 0.5, base.getY() + 3.0, base.getZ() + 9.5, 180, 12);
			ctx.takeScreenshot("00_lineup_front");
			view(ctx, server, base.getX() + 12.5, base.getY() + 3.0, base.getZ() + 7.5, 135, 14);
			ctx.takeScreenshot("01_lineup_side");
			int n = server.computeOnServer(s -> s.overworld().getEntities(ModEntities.DRAGON, e -> true).size());
			check("five dragons spawned (" + n + ")", n == 5);
			server.runOnServer(s -> s.overworld().getEntities(ModEntities.DRAGON, e -> true).forEach(e -> e.discard()));

			// ------------------------------------------------ close-ups of each stage
			for (DragonEntity.Stage stage : DragonEntity.Stage.values()) {
				int growth = stage == DragonEntity.Stage.HATCHLING ? 0 : stage == DragonEntity.Stage.JUVENILE ? DragonEntity.JUVENILE_AT : DragonEntity.ADULT_AT;
				server.runOnServer(s -> spawn(s, base.offset(0, 1, 0), DragonColor.EMBER, growth, true));
				ctx.waitTicks(10);
				float dist = stage == DragonEntity.Stage.ADULT ? 7.5F : stage == DragonEntity.Stage.JUVENILE ? 5.0F : 3.0F;
				view(ctx, server, base.getX() + 0.5 + dist * 0.55, base.getY() + 2.2 + (stage == DragonEntity.Stage.ADULT ? 1.0 : 0), base.getZ() + 0.5 + dist * 0.85, 147, stage == DragonEntity.Stage.ADULT ? 14 : 24);
				ctx.takeScreenshot("02_closeup_" + stage.name().toLowerCase());
				server.runOnServer(s -> s.overworld().getEntities(ModEntities.DRAGON, e -> true).forEach(e -> e.discard()));
			}

			// ------------------------------------------------ an egg hatches beside a campfire
			BlockPos eggAt = base.offset(0, 1, 0);
			server.runOnServer(s -> {
				ServerLevel l = s.overworld();
				l.setBlock(eggAt, ModBlocks.EGGS.get(DragonColor.VERDANT).defaultBlockState(), 3);
				l.setBlock(eggAt.offset(1, 0, 0), Blocks.CAMPFIRE.defaultBlockState(), 3);
			});
			view(ctx, server, eggAt.getX() + 0.5, eggAt.getY() + 2.0, eggAt.getZ() + 3.5, 180, 25);
			ctx.takeScreenshot("03_egg_fresh");
			boolean cracked = waitFor(server, s -> s.overworld().getBlockState(eggAt).getValue(DragonEggBlock.HATCH) >= 2, 900);
			check("egg cracked while warm", cracked);
			ctx.takeScreenshot("04_egg_cracked");
			boolean hatched = waitFor(server, s -> !s.overworld().getEntities(ModEntities.DRAGON, e -> true).isEmpty(), 900);
			check("egg hatched into a dragon", hatched);
			ctx.waitTicks(15);
			ctx.takeScreenshot("05_hatchling");
			DragonEntity.Stage st = server.computeOnServer(s -> s.overworld().getEntities(ModEntities.DRAGON, e -> true).stream().findFirst().map(DragonEntity::stage).orElse(null));
			check("hatchling is a hatchling", st == DragonEntity.Stage.HATCHLING);
			boolean tame = server.computeOnServer(s -> s.overworld().getEntities(ModEntities.DRAGON, e -> true).stream().anyMatch(DragonEntity::isTame));
			check("hatchling is tame to the nearest player", tame);
			server.runOnServer(s -> s.overworld().getEntities(ModEntities.DRAGON, e -> true).forEach(e -> e.discard()));
			server.runOnServer(s -> {
				s.overworld().setBlock(eggAt.offset(1, 0, 0), Blocks.AIR.defaultBlockState(), 3);
			});

			// ------------------------------------------------ ride, fly and breathe
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getAbilities().flying = false;
				p.getAbilities().mayfly = false;
				p.onUpdateAbilities();
				p.teleportTo(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
				DragonEntity d = spawn(s, base.offset(0, 1, -2), DragonColor.EMBER, DragonEntity.ADULT_AT, false);
				d.tame(p);
				p.startRiding(d);
			});
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.waitTicks(20);
			ctx.takeScreenshot("06_riding");
			boolean riding = server.computeOnServer(s -> player(s).getVehicle() instanceof DragonEntity);
			check("player is riding the adult dragon", riding);
			double y0 = server.computeOnServer(s -> player(s).getVehicle().getY());
			input.holdKeyFor(o -> o.keyJump, 30);
			ctx.waitTicks(6);
			double y1 = server.computeOnServer(s -> player(s).getVehicle() == null ? -1 : player(s).getVehicle().getY());
			ctx.takeScreenshot("07_takeoff");
			check("dragon rose when jump was held (" + String.format("%.1f -> %.1f", y0, y1) + ")", y1 > y0 + 1.0);
			input.holdKeyFor(o -> o.keyUp, 40);
			ctx.takeScreenshot("08_flying");
			boolean flying = server.computeOnServer(s -> player(s).getVehicle() instanceof DragonEntity d && d.isFlying());
			check("dragon is flying", flying);

			// breath: land, put a zombie in front, hold fire
			server.runOnServer(s -> {
				DragonEntity d = (DragonEntity) player(s).getVehicle();
				if (d == null) return;
				d.teleportTo(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
				d.setNoGravity(false);
				player(s).setYRot(180.0F);
				player(s).setXRot(8.0F);
				net.minecraft.world.entity.animal.cow.Cow z = EntityTypes.COW.create(s.overworld(), EntitySpawnReason.TRIGGERED);
				z.snapTo(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 6.5);
				z.setNoAi(true);
				z.setPersistenceRequired();
				s.overworld().addFreshEntity(z);
				d.setRiderBreathing(true);
			});
			ctx.getInput().lookAt(0, 8);
			ctx.waitTicks(25);
			ctx.takeScreenshot("09_breathing");
			float hp = server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.COW, e -> true).stream().findFirst().map(z -> z.getHealth()).orElse(0.0F));
			check("breath hurt the cow (hp " + hp + ")", hp < 10.0F);
			server.runOnServer(s -> {
				if (player(s).getVehicle() instanceof DragonEntity d) d.setRiderBreathing(false);
			});
		} finally {
			for (String r : this.results) System.out.println("PD-TEST " + r);
		}
	}

	private static DragonEntity spawn(MinecraftServer s, BlockPos at, DragonColor color, int growth, boolean noAi) {
		DragonEntity d = ModEntities.DRAGON.create(s.overworld(), EntitySpawnReason.TRIGGERED);
		d.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
		d.setColor(color);
		d.setGrowth(growth);
		d.setHealth(d.getMaxHealth());
		if (noAi) {
			d.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
			d.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED).setBaseValue(0.0);
		}
		d.setPersistenceRequired();
		s.overworld().addFreshEntity(d);
		return d;
	}

	private void check(String what, boolean ok) {
		this.results.add((ok ? "PASS " : "FAIL ") + what);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void settle(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		try {
			sp.getConnection().waitForChunksRender();
		} catch (Throwable t) {
			ctx.waitTicks(160);
		}
	}

	private static void view(ClientGameTestContext ctx, TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand(String.format("execute in minecraft:overworld run tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		});
		ctx.waitTicks(4);
		ctx.getInput().lookAt(yaw, pitch);
		ctx.waitTicks(6);
	}

	private static BlockPos ground(TestServerContext server, int x, int z) {
		return server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			level.getChunk(x >> 4, z >> 4);
			return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
		});
	}

	private static boolean waitFor(TestServerContext server, java.util.function.Predicate<MinecraftServer> condition, int timeout) {
		try {
			server.waitFor(condition, timeout);
			return true;
		} catch (Throwable t) {
			return false;
		}
	}
}

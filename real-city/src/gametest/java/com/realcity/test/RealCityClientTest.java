package com.realcity.test;

import com.realcity.RealCity;
import com.realcity.RealCityConfig;
import com.realcity.Sky;
import com.realcity.Weather;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.clock.WorldClocks;

/**
 * Real-client test: runs /setcity for a few cities against the live weather service, checks the world's clock follows
 * each city and takes screenshots. Results: "CITY-TEST" lines. Screenshots: build/run/clientGameTest/screenshots
 */
public class RealCityClientTest implements FabricClientGameTest {
	private final List<String> results = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			for (String c : List.of("gamemode creative @a", "gamerule spawn_mobs false", "tp @a 0.5 -60 0.5 200 -8")) {
				server.runCommand(c);
			}
			ctx.waitTicks(40);

			for (String city : System.getProperty("realcity.test.cities", "Los Angeles,Manchester UK,Sydney,Tokyo").split(",")) {
				server.runCommand("setcity " + city);
				for (int i = 0; i < 400 && RealCity.weather == null; i++) ctx.waitTicks(1);
				ctx.waitTicks(120); // let the sky and weather settle
				Weather w = RealCity.weather;
				check(city + ": weather arrived", w != null);
				if (w == null) continue;
				long ticks = server.computeOnServer(s -> s.clockManager()
					.getInstance(s.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD)).totalTicks());
				long expect = Sky.totalTicks(RealCity.now(), w.sunrise(), w.sunset());
				check(city + ": clock matches the real time (" + ticks % 24000 + " vs " + expect % 24000 + ")", Math.abs(ticks - expect) < 400);
				check(city + ": rain matches (" + w.describe() + ")", server.computeOnServer(s -> s.getWeatherData().isRaining()) == (w.sky() != Weather.Sky.CLEAR));
				ctx.takeScreenshot("city_" + city.split(" ")[0].toLowerCase());
			}

			long fails = results.stream().filter(r -> r.startsWith("FAIL")).count();
			System.out.println("CITY-TEST done: " + (results.size() - fails) + " passed, " + fails + " failed");
			if (fails > 0) throw new AssertionError(fails + " checks failed");
		}
	}

	private void check(String name, boolean ok) {
		results.add((ok ? "PASS " : "FAIL ") + name);
		System.out.println("CITY-TEST " + (ok ? "PASS" : "FAIL") + " " + name);
	}
}

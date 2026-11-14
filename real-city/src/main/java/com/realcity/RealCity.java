package com.realcity;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real City: /setcity Manchester and the world follows that city: time of day, sunrise and sunset,
 * moon phase and weather.
 */
public class RealCity implements ModInitializer {
	public static final String MOD_ID = "real_city";
	public static final Logger LOGGER = LoggerFactory.getLogger("RealCity");

	static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
	/** Open-Meteo updates every 15 minutes, so asking every 10 is plenty. */
	private static final long REFRESH_MS = 10 * 60_000L;
	private static final long RETRY_MS = 60_000L;
	/** How long a synced weather state lasts if we lose the internet: 15 minutes of ticks. */
	private static final int WEATHER_TICKS = 18_000;

	/** Latest weather for the saved city, null until the first fetch succeeds. */
	public static volatile Weather weather;
	private static volatile boolean fetching;
	private static long lastAttempt;
	private static int tick;
	/** Players who turned their action bar off with /city hud. */
	static final Set<UUID> hudOff = new HashSet<>();

	@Override
	public void onInitialize() {
		RealCityConfig.load();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> RealCityCommand.register(dispatcher));
		ServerTickEvents.END_SERVER_TICK.register(RealCity::onTick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> greet(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> hudOff.remove(handler.getPlayer().getUUID()));
		LOGGER.info("Real City ready. City: {}", RealCityConfig.INSTANCE.hasCity() ? RealCityConfig.INSTANCE.fullName() : "(none, use /setcity)");
	}

	static ZoneId zone() {
		try {
			return ZoneId.of(RealCityConfig.INSTANCE.timezone);
		} catch (Exception e) {
			return ZoneId.of("UTC");
		}
	}

	public static ZonedDateTime now() {
		return Instant.now().atZone(zone());
	}

	private static void onTick(MinecraftServer server) {
		tick++;
		RealCityConfig cfg = RealCityConfig.INSTANCE;
		if (!cfg.hasCity()) return;

		if (tick % 40 == 0 && cfg.hud) hud(server);
		if (!cfg.enabled) return;

		Weather w = weather;
		if (w != null && tick % 100 == 0) applyTime(server, w);

		long ms = System.currentTimeMillis();
		boolean stale = w == null || ms - w.fetchedAt() > REFRESH_MS || !w.date().equals(now().toLocalDate());
		if (stale && !fetching && ms - lastAttempt > RETRY_MS) refresh(server, null);
	}

	/** Fetches fresh weather, then applies it. {@code then} (may be null) runs on the server thread afterwards. */
	static void refresh(MinecraftServer server, Runnable then) {
		RealCityConfig cfg = RealCityConfig.INSTANCE;
		if (!cfg.hasCity() || fetching) return;
		fetching = true;
		lastAttempt = System.currentTimeMillis();
		Geo.weather(cfg.latitude, cfg.longitude).whenComplete((fresh, err) -> server.execute(() -> {
			fetching = false;
			if (err != null) {
				LOGGER.warn("Could not fetch weather for {}: {}", cfg.city, err.getMessage());
				return;
			}
			Weather old = weather;
			weather = fresh;
			applyAll(server);
			if (cfg.enabled && cfg.syncWeather && cfg.announce && old != null && old.sky() != fresh.sky()
				|| cfg.enabled && cfg.syncWeather && cfg.announce && old != null && old.snowy() != fresh.snowy()) {
				announce(server, fresh);
			}
			if (then != null) then.run();
		}));
	}

	/** Pushes everything we know into the world right now. */
	static void applyAll(MinecraftServer server) {
		Weather w = weather;
		RealCityConfig cfg = RealCityConfig.INSTANCE;
		if (w == null || !cfg.enabled || !cfg.hasCity()) return;
		applyTime(server, w);
		if (cfg.syncWeather) applyWeather(server, w);
	}

	private static Holder<WorldClock> overworldClock(MinecraftServer server) {
		return server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD);
	}

	static void applyTime(MinecraftServer server, Weather w) {
		RealCityConfig cfg = RealCityConfig.INSTANCE;
		if (!cfg.syncTime) return;
		Holder<WorldClock> clock = overworldClock(server);
		server.clockManager().setPaused(clock, true);
		server.clockManager().setTotalTicks(clock, Sky.totalTicks(now(), w.sunrise(), w.sunset()));
	}

	static void applyWeather(MinecraftServer server, Weather w) {
		switch (w.sky()) {
			case CLEAR -> server.setWeatherParameters(WEATHER_TICKS, 0, false, false);
			case RAIN -> server.setWeatherParameters(0, WEATHER_TICKS, true, false);
			case THUNDER -> server.setWeatherParameters(0, WEATHER_TICKS, true, true);
		}
	}

	/** Lets the sky run on its own again (used by /realcity off). */
	static void releaseTime(MinecraftServer server) {
		server.clockManager().setPaused(overworldClock(server), false);
	}

	private static void announce(MinecraftServer server, Weather w) {
		String text = switch (w.sky()) {
			case CLEAR -> "The rain has stopped in " + RealCityConfig.INSTANCE.city + ".";
			case RAIN -> (w.snowy() ? "It's started snowing" : "It's started raining") + " in " + RealCityConfig.INSTANCE.city + ".";
			case THUNDER -> "A thunderstorm has rolled into " + RealCityConfig.INSTANCE.city + ".";
		};
		server.getPlayerList().broadcastSystemMessage(prefix().append(Component.literal(text).withStyle(ChatFormatting.AQUA)), false);
	}

	static MutableComponent prefix() {
		return Component.literal("[Real City] ").withStyle(ChatFormatting.GOLD);
	}

	private static void greet(ServerPlayer player) {
		RealCityConfig cfg = RealCityConfig.INSTANCE;
		if (!cfg.hasCity() || !cfg.enabled) return;
		Weather w = weather;
		String line = "Live from " + cfg.city + ": " + now().format(CLOCK);
		if (w != null) line += ", " + Math.round(w.temp()) + "°C, " + w.describe().toLowerCase();
		player.sendSystemMessage(prefix().append(Component.literal(line).withStyle(ChatFormatting.YELLOW)));
	}

	private static void hud(MinecraftServer server) {
		RealCityConfig cfg = RealCityConfig.INSTANCE;
		Weather w = weather;
		String bar = cfg.city + "  " + now().format(CLOCK);
		if (w != null) bar += "  " + Math.round(w.temp()) + "°C  " + w.describe();
		Component msg = Component.literal(bar).withStyle(ChatFormatting.WHITE);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!hudOff.contains(p.getUUID())) p.sendSystemMessage(msg, true);
		}
	}
}

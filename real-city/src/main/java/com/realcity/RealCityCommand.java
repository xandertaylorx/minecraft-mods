package com.realcity;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.time.ZonedDateTime;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** /setcity, /city and /realcity. */
public final class RealCityCommand {
	private RealCityCommand() {}

	/** Anyone in singleplayer (even with cheats off), game masters on a server. */
	private static boolean canConfigure(CommandSourceStack src) {
		// The server is null when Minecraft builds the command list for a joining player
		return (src.getServer() != null && src.getServer().isSingleplayer()) || Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(src);
	}

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("setcity")
			.requires(RealCityCommand::canConfigure)
			.executes(ctx -> info(ctx.getSource()))
			.then(Commands.argument("city", StringArgumentType.greedyString())
				.executes(ctx -> setCity(ctx.getSource(), StringArgumentType.getString(ctx, "city")))));

		d.register(Commands.literal("city")
			.executes(ctx -> info(ctx.getSource()))
			.then(Commands.literal("hud")
				.executes(ctx -> hud(ctx.getSource(), null))
				.then(Commands.argument("on", BoolArgumentType.bool())
					.executes(ctx -> hud(ctx.getSource(), BoolArgumentType.getBool(ctx, "on"))))));

		d.register(Commands.literal("realcity")
			.requires(RealCityCommand::canConfigure)
			.executes(ctx -> info(ctx.getSource()))
			.then(Commands.literal("on").executes(ctx -> enable(ctx.getSource(), true)))
			.then(Commands.literal("off").executes(ctx -> enable(ctx.getSource(), false)))
			.then(Commands.literal("refresh").executes(ctx -> refresh(ctx.getSource())))
			.then(Commands.literal("time").then(Commands.argument("on", BoolArgumentType.bool())
				.executes(ctx -> toggle(ctx.getSource(), "time", BoolArgumentType.getBool(ctx, "on")))))
			.then(Commands.literal("weather").then(Commands.argument("on", BoolArgumentType.bool())
				.executes(ctx -> toggle(ctx.getSource(), "weather", BoolArgumentType.getBool(ctx, "on")))))
			.then(Commands.literal("announce").then(Commands.argument("on", BoolArgumentType.bool())
				.executes(ctx -> toggle(ctx.getSource(), "announce", BoolArgumentType.getBool(ctx, "on"))))));
	}

	private static void say(CommandSourceStack src, String text, ChatFormatting color) {
		src.sendSuccess(() -> RealCity.prefix().append(Component.literal(text).withStyle(color)), false);
	}

	private static int setCity(CommandSourceStack src, String input) {
		MinecraftServer server = src.getServer();
		say(src, "Looking up " + input + "...", ChatFormatting.GRAY);
		Geo.find(input).whenComplete((city, err) -> server.execute(() -> {
			if (err != null) {
				src.sendFailure(Component.literal("Couldn't reach the weather service: " + rootMessage(err) + ". Check your internet and try again."));
				return;
			}
			if (city == null) {
				src.sendFailure(Component.literal("Couldn't find \"" + input + "\". Try the city and country, like /setcity Manchester, UK"));
				return;
			}
			RealCityConfig cfg = RealCityConfig.INSTANCE;
			cfg.city = city.name();
			cfg.region = city.region();
			cfg.country = city.country();
			cfg.timezone = city.timezone();
			cfg.latitude = city.lat();
			cfg.longitude = city.lon();
			cfg.enabled = true;
			RealCityConfig.save();
			RealCity.weather = null;
			say(src, "City set to " + cfg.fullName() + ". Getting the weather...", ChatFormatting.GREEN);
			RealCity.refresh(server, () -> {
				Weather w = RealCity.weather;
				if (w == null) say(src, "Saved, but the weather lookup failed. It will keep retrying.", ChatFormatting.RED);
				else {
					server.getPlayerList().broadcastSystemMessage(RealCity.prefix().append(Component.literal(
						"The world now follows " + cfg.city + ": " + RealCity.now().format(RealCity.CLOCK) + ", "
							+ Math.round(w.temp()) + "°C, " + w.describe().toLowerCase() + ".").withStyle(ChatFormatting.YELLOW)), false);
				}
			});
		}));
		return 1;
	}

	private static String rootMessage(Throwable t) {
		while (t.getCause() != null) t = t.getCause();
		return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
	}

	private static int info(CommandSourceStack src) {
		RealCityConfig cfg = RealCityConfig.INSTANCE;
		if (!cfg.hasCity()) {
			say(src, "No city set yet. Try /setcity Manchester, UK or /setcity Los Angeles", ChatFormatting.YELLOW);
			return 0;
		}
		ZonedDateTime now = RealCity.now();
		Weather w = RealCity.weather;
		say(src, cfg.fullName() + (cfg.enabled ? "" : "  (sync is OFF)"), ChatFormatting.GREEN);
		line(src, "Local time", now.format(RealCity.CLOCK) + "  (" + cfg.timezone + ")");
		if (w != null) {
			line(src, "Weather", w.describe() + ", " + fmt(w.temp()) + "°C / " + fmt(w.temp() * 9 / 5 + 32) + "°F"
				+ " (feels like " + fmt(w.feels()) + "°C), wind " + fmt(w.wind()) + " km/h");
			if (w.sunrise() != null && w.sunset() != null) {
				line(src, "Sun", "rises " + w.sunrise().format(RealCity.CLOCK) + ", sets " + w.sunset().format(RealCity.CLOCK));
			}
		} else {
			line(src, "Weather", "not loaded yet");
		}
		int phase = Sky.moonPhase(now.toLocalDate().atTime(12, 0).atZone(now.getZone()));
		line(src, "Moon", Sky.MOON_NAMES[phase]);
		line(src, "Syncing", (cfg.syncTime ? "time " : "") + (cfg.syncWeather ? "weather" : "")
			+ (!cfg.syncTime && !cfg.syncWeather ? "nothing" : ""));
		return 1;
	}

	private static String fmt(double v) {
		return String.valueOf(Math.round(v));
	}

	private static void line(CommandSourceStack src, String label, String value) {
		src.sendSuccess(() -> Component.literal("  " + label + ": ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(value).withStyle(ChatFormatting.WHITE)), false);
	}

	private static int hud(CommandSourceStack src, Boolean on) {
		ServerPlayer p = src.getPlayer();
		if (p == null) {
			src.sendFailure(Component.literal("Only players have an action bar."));
			return 0;
		}
		boolean show = on != null ? on : RealCity.hudOff.contains(p.getUUID());
		if (show) RealCity.hudOff.remove(p.getUUID());
		else RealCity.hudOff.add(p.getUUID());
		say(src, "Action bar " + (show ? "on" : "off") + ".", ChatFormatting.GREEN);
		return 1;
	}

	private static int enable(CommandSourceStack src, boolean on) {
		RealCityConfig cfg = RealCityConfig.INSTANCE;
		cfg.enabled = on;
		RealCityConfig.save();
		if (on) {
			if (!cfg.hasCity()) {
				say(src, "Pick a city first: /setcity Manchester, UK", ChatFormatting.YELLOW);
				return 0;
			}
			RealCity.applyAll(src.getServer());
			say(src, "Real City is on. The world follows " + cfg.city + ".", ChatFormatting.GREEN);
		} else {
			RealCity.releaseTime(src.getServer());
			say(src, "Real City is off. Time and weather are back to normal.", ChatFormatting.YELLOW);
		}
		return 1;
	}

	private static int toggle(CommandSourceStack src, String what, boolean on) {
		RealCityConfig cfg = RealCityConfig.INSTANCE;
		switch (what) {
			case "time" -> {
				cfg.syncTime = on;
				if (!on) RealCity.releaseTime(src.getServer());
			}
			case "weather" -> cfg.syncWeather = on;
			default -> cfg.announce = on;
		}
		RealCityConfig.save();
		RealCity.applyAll(src.getServer());
		say(src, "Sync " + what + ": " + (on ? "on" : "off"), ChatFormatting.GREEN);
		return 1;
	}

	private static int refresh(CommandSourceStack src) {
		if (!RealCityConfig.INSTANCE.hasCity()) {
			say(src, "Pick a city first: /setcity Manchester, UK", ChatFormatting.YELLOW);
			return 0;
		}
		RealCity.refresh(src.getServer(), () -> say(src, "Updated.", ChatFormatting.GREEN));
		return 1;
	}
}

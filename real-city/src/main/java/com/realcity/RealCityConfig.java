package com.realcity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** config/realcity.json, rewritten whenever the city or a switch changes. */
public final class RealCityConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static RealCityConfig INSTANCE = new RealCityConfig();

	public String city = "";
	public String region = "";
	public String country = "";
	public String timezone = "";
	public double latitude;
	public double longitude;
	/** Master switch. */
	public boolean enabled = true;
	/** Sync the sky (time of day, sunrise, sunset, moon phase). */
	public boolean syncTime = true;
	/** Sync rain and thunder. */
	public boolean syncWeather = true;
	/** Show the city, time and weather above the hotbar. Each player can switch it off with /city hud. */
	public boolean hud = true;
	/** Tell chat when the real weather changes. */
	public boolean announce = true;

	public boolean hasCity() {
		return !city.isEmpty() && !timezone.isEmpty();
	}

	public String title() {
		return city;
	}

	public String fullName() {
		StringBuilder sb = new StringBuilder(city);
		if (!region.isEmpty() && !region.equals(city)) sb.append(", ").append(region);
		if (!country.isEmpty()) sb.append(", ").append(country);
		return sb.toString();
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("realcity.json");
	}

	public static void load() {
		try {
			Path p = path();
			if (Files.exists(p)) {
				RealCityConfig c = GSON.fromJson(Files.readString(p), RealCityConfig.class);
				if (c != null) INSTANCE = c;
			}
		} catch (Exception e) {
			RealCity.LOGGER.warn("Could not read realcity.json, using defaults", e);
		}
	}

	public static void save() {
		try {
			Files.writeString(path(), GSON.toJson(INSTANCE));
		} catch (IOException e) {
			RealCity.LOGGER.warn("Could not save realcity.json", e);
		}
	}
}

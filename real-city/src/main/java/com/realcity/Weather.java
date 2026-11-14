package com.realcity;

import com.google.gson.JsonObject;
import java.time.LocalDate;
import java.time.LocalTime;

/** What the weather is doing in the city right now, from Open-Meteo. */
public record Weather(double temp, double feels, double wind, int code, LocalTime sunrise, LocalTime sunset,
					  LocalDate date, long fetchedAt) {

	public enum Sky { CLEAR, RAIN, THUNDER }

	public Sky sky() {
		if (code >= 95) return Sky.THUNDER;
		if (code >= 51 && code != 45 && code != 48) return Sky.RAIN; // drizzle, rain, snow, showers
		return Sky.CLEAR;
	}

	public boolean snowy() {
		return (code >= 71 && code <= 77) || code == 85 || code == 86;
	}

	public String describe() {
		return switch (code) {
			case 0 -> "Clear sky";
			case 1 -> "Mostly clear";
			case 2 -> "Partly cloudy";
			case 3 -> "Overcast";
			case 45, 48 -> "Foggy";
			case 51, 53, 55 -> "Drizzle";
			case 56, 57 -> "Freezing drizzle";
			case 61 -> "Light rain";
			case 63 -> "Rain";
			case 65 -> "Heavy rain";
			case 66, 67 -> "Freezing rain";
			case 71 -> "Light snow";
			case 73 -> "Snow";
			case 75 -> "Heavy snow";
			case 77 -> "Snow grains";
			case 80 -> "Light showers";
			case 81 -> "Showers";
			case 82 -> "Violent showers";
			case 85, 86 -> "Snow showers";
			case 95 -> "Thunderstorm";
			case 96, 99 -> "Thunderstorm with hail";
			default -> "Unknown";
		};
	}

	static Weather parse(JsonObject json, long now) {
		JsonObject cur = json.getAsJsonObject("current");
		JsonObject daily = json.getAsJsonObject("daily");
		LocalTime sunrise = time(daily, "sunrise");
		LocalTime sunset = time(daily, "sunset");
		String t = cur.get("time").getAsString(); // 2026-11-14T19:00, already in the city's timezone
		return new Weather(cur.get("temperature_2m").getAsDouble(), cur.get("apparent_temperature").getAsDouble(),
			cur.get("wind_speed_10m").getAsDouble(), cur.get("weather_code").getAsInt(), sunrise, sunset,
			LocalDate.parse(t.substring(0, 10)), now);
	}

	private static LocalTime time(JsonObject daily, String key) {
		try {
			String s = daily.getAsJsonArray(key).get(0).getAsString();
			return LocalTime.parse(s.substring(s.indexOf('T') + 1));
		} catch (Exception e) {
			return null; // polar day or night
		}
	}
}

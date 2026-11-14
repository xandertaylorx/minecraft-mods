package com.realcity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Talks to Open-Meteo (free, no key): city search and current weather. Blocking calls run off the server thread. */
public final class Geo {
	private Geo() {}

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final Map<String, String> ALIASES = Map.of("uk", "gb", "usa", "us", "america", "us",
		"u.s.", "us", "u.k.", "gb", "britain", "gb", "great britain", "gb", "emirates", "ae", "uae", "ae");

	public record City(String name, String region, String country, double lat, double lon, String timezone) {}

	private static JsonObject get(String url) throws IOException, InterruptedException {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
			.header("User-Agent", "RealCity-Minecraft-Mod").GET().build();
		HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode());
		return JsonParser.parseString(res.body()).getAsJsonObject();
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	/** "Manchester", "Manchester, UK", "los angeles", "Paris France"... Completes with null if nothing matches. */
	public static CompletableFuture<City> find(String input) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				String q = input.trim();
				String name = q, qualifier = "";
				int comma = q.lastIndexOf(',');
				if (comma > 0) {
					name = q.substring(0, comma).trim();
					qualifier = q.substring(comma + 1).trim();
				}
				City c = search(name, qualifier);
				if (c == null && comma < 0 && q.contains(" ")) {
					// "manchester uk": retry with the last word as the country or state
					int sp = q.lastIndexOf(' ');
					c = search(q.substring(0, sp).trim(), q.substring(sp + 1).trim());
				}
				return c;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	private static City search(String name, String qualifier) throws IOException, InterruptedException {
		if (name.isEmpty()) return null;
		JsonObject json = get("https://geocoding-api.open-meteo.com/v1/search?count=20&language=en&name=" + enc(name));
		if (!json.has("results")) return null;
		String qual = qualifier.toLowerCase(Locale.ROOT);
		qual = ALIASES.getOrDefault(qual, qual);
		JsonArray results = json.getAsJsonArray("results");
		for (JsonElement el : results) {
			JsonObject r = el.getAsJsonObject();
			if (!qual.isEmpty() && !matches(r, qual)) continue;
			if (!r.has("timezone")) continue;
			return new City(str(r, "name"), str(r, "admin1"), str(r, "country"),
				r.get("latitude").getAsDouble(), r.get("longitude").getAsDouble(), r.get("timezone").getAsString());
		}
		return null;
	}

	private static boolean matches(JsonObject r, String qual) {
		return str(r, "country_code").toLowerCase(Locale.ROOT).equals(qual)
			|| str(r, "country").toLowerCase(Locale.ROOT).contains(qual)
			|| str(r, "admin1").toLowerCase(Locale.ROOT).contains(qual)
			|| str(r, "admin2").toLowerCase(Locale.ROOT).contains(qual);
	}

	private static String str(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
	}

	public static CompletableFuture<Weather> weather(double lat, double lon) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				JsonObject json = get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
					+ "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m"
					+ "&daily=sunrise,sunset&timezone=auto&forecast_days=1");
				return Weather.parse(json, System.currentTimeMillis());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}
}

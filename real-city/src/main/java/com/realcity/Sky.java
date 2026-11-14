package com.realcity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Turns real-world time into Minecraft time. Pure maths, no Minecraft classes. */
public final class Sky {
	private Sky() {}

	// Minecraft: tick 0 = 6:00, noon = 6000, sunset ~ 12500, night 13000+, sunrise ~ 23500.
	static final double SUNRISE_TICK = -500;
	static final double SUNSET_TICK = 12500;
	static final double DAY_TICKS = SUNSET_TICK - SUNRISE_TICK; // 13000 ticks of daylight
	static final double NIGHT_TICKS = 24000 - DAY_TICKS;        // 11000 ticks of night

	static final String[] MOON_NAMES = {"Full moon", "Waning gibbous", "Last quarter", "Waning crescent",
		"New moon", "Waxing crescent", "First quarter", "Waxing gibbous"};
	private static final double SYNODIC = 29.530588853;
	private static final long NEW_MOON_EPOCH = 947182440L; // 2000-01-06 18:14 UTC

	/**
	 * Tick of the day (0..23999) for a real local time. Real sunrise lands on Minecraft's sunrise and real
	 * sunset on Minecraft's sunset, so long summer days and short winter days both show up in the sky.
	 */
	public static int tickOfDay(double minuteOfDay, LocalTime sunrise, LocalTime sunset) {
		double sr = sunrise == null ? -1 : sunrise.toSecondOfDay() / 60.0;
		double ss = sunset == null ? -1 : sunset.toSecondOfDay() / 60.0;
		double tick;
		if (sr < 0 || ss <= sr || ss - sr > 1400 || ss - sr < 40) {
			// Polar day/night or missing data: plain 24h clock, midnight = 18000
			tick = minuteOfDay * 24000 / 1440 + 18000;
		} else if (minuteOfDay >= sr && minuteOfDay < ss) {
			tick = SUNRISE_TICK + (minuteOfDay - sr) / (ss - sr) * DAY_TICKS;
		} else {
			double nightLen = 1440 - (ss - sr);
			double since = Math.floorMod((long) Math.floor((minuteOfDay - ss) * 1000), 1440 * 1000L) / 1000.0;
			tick = SUNSET_TICK + since / nightLen * NIGHT_TICKS;
		}
		return (int) Math.floorMod((long) Math.floor(tick), 24000L);
	}

	/** Minecraft's moon phase index (0 = full ... 4 = new) for an instant. */
	public static int moonPhase(ZonedDateTime when) {
		double days = (when.toEpochSecond() - NEW_MOON_EPOCH) / 86400.0;
		double f = ((days % SYNODIC) + SYNODIC) % SYNODIC / SYNODIC; // 0 = new, 0.5 = full
		return (4 + (int) Math.round(f * 8)) % 8;
	}

	/**
	 * The value to put on Minecraft's overworld clock: the day number carries the moon phase (it cycles every
	 * 8 days), the remainder is the time of day.
	 */
	public static long totalTicks(ZonedDateTime now, LocalTime sunrise, LocalTime sunset) {
		ZoneId zone = now.getZone();
		double minute = now.toLocalTime().toNanoOfDay() / 6.0e10;
		int tod = tickOfDay(minute, sunrise, sunset);
		// The moon belongs to the night it rises in, so before sunrise use yesterday's phase
		LocalDate day = now.toLocalDate();
		if (sunrise != null && now.toLocalTime().isBefore(sunrise)) day = day.minusDays(1);
		int phase = moonPhase(day.atTime(12, 0).atZone(zone));
		return 192000L * 1000 + phase * 24000L + tod;
	}
}

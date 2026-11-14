package com.betterdogs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Everything you can set for one dog. Stored on the wolf itself (a Fabric data attachment), so it's saved with the world
 * and follows the dog everywhere. Immutable: change it with the with... methods and put it back on the wolf.
 */
public record DogSettings(
	boolean invincible,
	Collect collect,
	Behavior behavior,
	boolean regen,
	boolean ownerSafe,
	boolean glow,
	boolean swift,
	boolean dangerBark,
	int collected,
	int kills) {

	public static final DogSettings DEFAULT = new DogSettings(false, Collect.OFF, Behavior.DEFEND, false, true, false, false, false, 0, 0);

	/** What the dog picks up for you. */
	public enum Collect {
		OFF("Off", "Doesn't pick anything up"),
		FOOD("Food only", "Meat, fish, bread, crops you can eat..."),
		GOOD("Good stuff", "Food, ores, gems, ingots, mob drops, tools, armor and rare items. Skips dirt, cobble, seeds and other junk"),
		ALL("Everything", "Every item lying on the ground near you");

		public final String label;
		public final String help;

		Collect(String label, String help) {
			this.label = label;
			this.help = help;
		}
	}

	/** How the dog fights. */
	public enum Behavior {
		DEFEND("Defend", "Normal dog: fights what attacks you and what you attack"),
		GUARD("Guard", "Also hunts any hostile mob that comes close (except creepers)"),
		PASSIVE("Passive", "Never fights anything");

		public final String label;
		public final String help;

		Behavior(String label, String help) {
			this.label = label;
			this.help = help;
		}
	}

	private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
		E[] values = type.getEnumConstants();
		return Codec.STRING.xmap(s -> {
			for (E e : values) if (e.name().equalsIgnoreCase(s)) return e;
			return values[0];
		}, e -> e.name().toLowerCase());
	}

	public static final Codec<DogSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.BOOL.optionalFieldOf("invincible", false).forGetter(DogSettings::invincible),
		enumCodec(Collect.class).optionalFieldOf("collect", Collect.OFF).forGetter(DogSettings::collect),
		enumCodec(Behavior.class).optionalFieldOf("behavior", Behavior.DEFEND).forGetter(DogSettings::behavior),
		Codec.BOOL.optionalFieldOf("regen", false).forGetter(DogSettings::regen),
		Codec.BOOL.optionalFieldOf("owner_safe", true).forGetter(DogSettings::ownerSafe),
		Codec.BOOL.optionalFieldOf("glow", false).forGetter(DogSettings::glow),
		Codec.BOOL.optionalFieldOf("swift", false).forGetter(DogSettings::swift),
		Codec.BOOL.optionalFieldOf("danger_bark", false).forGetter(DogSettings::dangerBark),
		Codec.INT.optionalFieldOf("collected", 0).forGetter(DogSettings::collected),
		Codec.INT.optionalFieldOf("kills", 0).forGetter(DogSettings::kills)
	).apply(i, DogSettings::new));

	public static final StreamCodec<ByteBuf, DogSettings> STREAM_CODEC = StreamCodec.of(
		(buf, s) -> {
			int flags = (s.invincible ? 1 : 0) | (s.regen ? 2 : 0) | (s.ownerSafe ? 4 : 0) | (s.glow ? 8 : 0) | (s.swift ? 16 : 0) | (s.dangerBark ? 32 : 0);
			buf.writeByte(flags);
			buf.writeByte(s.collect.ordinal());
			buf.writeByte(s.behavior.ordinal());
			buf.writeInt(s.collected);
			buf.writeInt(s.kills);
		},
		buf -> {
			int flags = buf.readByte();
			Collect collect = Collect.values()[Math.floorMod(buf.readByte(), Collect.values().length)];
			Behavior behavior = Behavior.values()[Math.floorMod(buf.readByte(), Behavior.values().length)];
			return new DogSettings((flags & 1) != 0, collect, behavior, (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0,
				(flags & 16) != 0, (flags & 32) != 0, buf.readInt(), buf.readInt());
		});

	public DogSettings withInvincible(boolean v) {
		return new DogSettings(v, collect, behavior, regen, ownerSafe, glow, swift, dangerBark, collected, kills);
	}

	public DogSettings withCollect(Collect v) {
		return new DogSettings(invincible, v, behavior, regen, ownerSafe, glow, swift, dangerBark, collected, kills);
	}

	public DogSettings withBehavior(Behavior v) {
		return new DogSettings(invincible, collect, v, regen, ownerSafe, glow, swift, dangerBark, collected, kills);
	}

	public DogSettings withRegen(boolean v) {
		return new DogSettings(invincible, collect, behavior, v, ownerSafe, glow, swift, dangerBark, collected, kills);
	}

	public DogSettings withOwnerSafe(boolean v) {
		return new DogSettings(invincible, collect, behavior, regen, v, glow, swift, dangerBark, collected, kills);
	}

	public DogSettings withGlow(boolean v) {
		return new DogSettings(invincible, collect, behavior, regen, ownerSafe, v, swift, dangerBark, collected, kills);
	}

	public DogSettings withSwift(boolean v) {
		return new DogSettings(invincible, collect, behavior, regen, ownerSafe, glow, v, dangerBark, collected, kills);
	}

	public DogSettings withDangerBark(boolean v) {
		return new DogSettings(invincible, collect, behavior, regen, ownerSafe, glow, swift, v, collected, kills);
	}

	public DogSettings addCollected(int n) {
		return new DogSettings(invincible, collect, behavior, regen, ownerSafe, glow, swift, dangerBark, collected + n, kills);
	}

	public DogSettings addKill() {
		return new DogSettings(invincible, collect, behavior, regen, ownerSafe, glow, swift, dangerBark, collected, kills + 1);
	}
}

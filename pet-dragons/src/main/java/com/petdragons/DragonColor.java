package com.petdragons;

import java.util.Locale;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;

/** The five kinds of dragon. Each has its own look, its own breath and its own egg. */
public enum DragonColor {
	EMBER("Ember"),
	FROST("Frost"),
	VERDANT("Verdant"),
	VOID("Void"),
	SUN("Sun");

	public final String title;

	DragonColor(String title) {
		this.title = title;
	}

	public String key() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public static DragonColor byIndex(int i) {
		DragonColor[] all = values();
		return all[Math.floorMod(i, all.length)];
	}

	/** The main particle of this breath, and a second one mixed in for texture. */
	public ParticleOptions breath() {
		return switch (this) {
			case EMBER -> ParticleTypes.FLAME;
			case FROST -> ParticleTypes.SNOWFLAKE;
			case VERDANT -> ParticleTypes.SPORE_BLOSSOM_AIR;
			case VOID -> PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F);
			case SUN -> ParticleTypes.END_ROD;
		};
	}

	public ParticleOptions breathMix() {
		return switch (this) {
			case EMBER -> ParticleTypes.SMOKE;
			case FROST -> ParticleTypes.CLOUD;
			case VERDANT -> ParticleTypes.ITEM_SLIME;
			case VOID -> ParticleTypes.PORTAL;
			case SUN -> ParticleTypes.FLAME;
		};
	}
}

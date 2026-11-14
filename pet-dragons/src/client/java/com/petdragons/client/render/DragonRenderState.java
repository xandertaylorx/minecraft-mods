package com.petdragons.client.render;

import com.petdragons.DragonColor;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class DragonRenderState extends LivingEntityRenderState {
	public DragonColor color = DragonColor.EMBER;
	public boolean flying;
	public boolean breathing;
	public boolean sitting;
	public boolean ridden;
	/** 0 = just hatched, 1 = full grown. */
	public float maturity;
	public float flap;
}

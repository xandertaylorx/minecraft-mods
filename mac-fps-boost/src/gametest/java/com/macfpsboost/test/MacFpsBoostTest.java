package com.macfpsboost.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * On a Retina Mac, SDL reports a pixel density of 2.0 when the game renders at full Retina resolution.
 * With Mac FPS Boost it must be 1.0 (one game pixel per window point).
 */
public class MacFpsBoostTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		float density = ctx.computeOnClient(mc -> mc.getWindow().getPixelDensity());
		int fbW = ctx.computeOnClient(mc -> mc.getWindow().getWidth());
		int fbH = ctx.computeOnClient(mc -> mc.getWindow().getHeight());
		boolean mac = System.getProperty("os.name", "").toLowerCase().contains("mac");
		boolean expectBoost = mac && !Boolean.getBoolean("macfpsboost.disable");
		boolean ok = !expectBoost || density <= 1.01F;
		System.out.println("MFB-TEST " + (ok ? "PASS" : "FAIL") + " pixel density " + density
			+ ", framebuffer " + fbW + "x" + fbH + (mac ? " (macOS)" : " (not macOS, mod does nothing)")
			+ (expectBoost ? "" : " [mod switched off]"));
		if (!ok) throw new AssertionError("Still rendering at Retina resolution");
	}
}

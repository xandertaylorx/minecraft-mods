package com.macfpsboost.mixin;

import com.mojang.blaze3d.platform.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Minecraft creates its window with SDL_WINDOW_HIGH_PIXEL_DENSITY, which on a Retina Mac makes the game
 * render at the display's full pixel resolution (e.g. 2x width and 2x height = 4x the pixels).
 * Removing that one flag on macOS renders at the normal size and lets macOS scale it up, which is
 * the single biggest FPS win on a Mac, especially with shaders.
 */
@Mixin(Window.class)
public class WindowMixin {
	private static final Logger LOGGER = LoggerFactory.getLogger("Mac FPS Boost");
	private static final long SDL_WINDOW_HIGH_PIXEL_DENSITY = 0x0000000000002000L;

	@ModifyArg(
		method = "createWindow",
		at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/device/GpuBackend;createWindow(Ljava/lang/String;IIJ)J"),
		index = 3
	)
	private long macfpsboost$dropRetina(long flags) {
		if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) {
			return flags;
		}
		if (Boolean.getBoolean("macfpsboost.disable")) {
			LOGGER.info("Disabled with -Dmacfpsboost.disable=true; keeping Retina resolution");
			return flags;
		}
		LOGGER.info("macOS detected: rendering at normal resolution instead of Retina (about 4x fewer pixels)");
		return flags & ~SDL_WINDOW_HIGH_PIXEL_DENSITY;
	}
}

package com.massdestruction.client.mixin;

import com.massdestruction.client.ScreenFx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Camera shake near the big effects (added where the game applies its own hurt wobble). */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "bobHurt", at = @At("HEAD"))
	private void massdestruction$shake(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
		float s = ScreenFx.shake();
		if (s <= 0.01F) return;
		double t = System.nanoTime() / 1.0e9;
		float a = (float) (Math.sin(t * 37.0) * 0.6 + Math.sin(t * 61.0 + 1.3) * 0.4);
		float b = (float) (Math.sin(t * 43.0 + 2.1) * 0.6 + Math.sin(t * 71.0) * 0.4);
		pose.rotateDegrees(Axis.ZP, a * s * 1.2F);
		pose.rotateDegrees(Axis.XP, b * s * 0.8F);
	}
}

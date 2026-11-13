package com.agentbridge.client;

import com.agentbridge.AgentBridge;
import com.agentbridge.ClientHooks;
import com.mojang.blaze3d.pipeline.RenderTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

public class AgentBridgeClient implements ClientModInitializer, ClientHooks {
	/** The player's own "pause on lost focus" setting, restored when the bridge turns off. */
	private Boolean savedPauseOnLostFocus;

	@Override
	public void onInitializeClient() {
		AgentBridge.clientHooks = this;
	}

	@Override
	public void onBridgeToggled(boolean enabled) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			// While you talk to the AI in another window, the game must keep running or nothing would update.
			if (enabled) {
				if (savedPauseOnLostFocus == null) savedPauseOnLostFocus = mc.options.pauseOnLostFocus;
				mc.options.pauseOnLostFocus = false;
			} else if (savedPauseOnLostFocus != null) {
				mc.options.pauseOnLostFocus = savedPauseOnLostFocus;
				savedPauseOnLostFocus = null;
			}
		});
	}

	@Override
	public CompletableFuture<byte[]> screenshot(boolean hideHud, int maxWidth) {
		Minecraft mc = Minecraft.getInstance();
		CompletableFuture<byte[]> result = new CompletableFuture<>();
		mc.execute(() -> {
			boolean toggled = hideHud && !mc.gui.hud.isHidden();
			if (toggled) mc.gui.hud.toggle();
			// Tasks run before the frame is drawn, so wait one frame for the HUD change to show up.
			mc.execute(() -> mc.execute(() -> {
				try {
					capture(mc, maxWidth, result);
				} catch (Throwable t) {
					result.completeExceptionally(t);
				} finally {
					if (toggled && mc.gui.hud.isHidden()) mc.gui.hud.toggle();
				}
			}));
		});
		return result;
	}

	private static void capture(Minecraft mc, int maxWidth, CompletableFuture<byte[]> result) {
		RenderTarget target = mc.gameRenderer.mainRenderTarget();
		int factor = 1;
		for (int f = 1; f <= 8; f++) {
			if (target.width % f == 0 && target.height % f == 0) {
				factor = f;
				if (target.width / f <= maxWidth) break;
			}
		}
		Screenshot.takeScreenshot(target, factor, image -> {
			try (image) {
				Path tmp = Files.createTempFile("agent-bridge-", ".png");
				try {
					image.writeToFile(tmp);
					result.complete(Files.readAllBytes(tmp));
				} finally {
					Files.deleteIfExists(tmp);
				}
			} catch (Throwable t) {
				result.completeExceptionally(t);
			}
		});
	}
}

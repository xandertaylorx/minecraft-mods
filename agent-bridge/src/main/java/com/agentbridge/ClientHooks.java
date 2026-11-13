package com.agentbridge;

import java.util.concurrent.CompletableFuture;

/** Things only the game client can do. Implemented in the client source set. */
public interface ClientHooks {
	/** Captures the game window as PNG bytes. */
	CompletableFuture<byte[]> screenshot(boolean hideHud, int maxWidth);

	/** Called when the bridge turns on or off, so the client can stop pausing when the window loses focus. */
	void onBridgeToggled(boolean enabled);
}

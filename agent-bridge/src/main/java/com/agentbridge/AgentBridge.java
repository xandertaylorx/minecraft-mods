package com.agentbridge;

import com.agentbridge.chat.AgentChat;
import com.agentbridge.chat.ChatCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentBridge implements ModInitializer {
	public static final String MOD_ID = "agent_bridge";
	public static final Logger LOGGER = LoggerFactory.getLogger("AgentBridge");

	/** Set by the client entrypoint. Null on a dedicated server. */
	public static volatile ClientHooks clientHooks;

	@Override
	public void onInitialize() {
		BridgeConfig.load();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			AgentCommand.register(dispatcher);
			ChatCommands.register(dispatcher);
		});
		ChatCommands.registerChatListener();
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			AgentChat.stopAll();
			Bridge.stop(server);
		});
	}
}

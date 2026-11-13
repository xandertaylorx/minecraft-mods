package com.agentbridge.chat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Locale;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/** "/claude <message>", "/codex <message>", and plain chat messages starting with @claude or @codex. */
public final class ChatCommands {
	private ChatCommands() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		for (AgentChat.Kind kind : AgentChat.Kind.values()) {
			dispatcher.register(Commands.literal(kind.command)
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> run(ctx, kind, ""))
				.then(Commands.argument("message", StringArgumentType.greedyString())
					.executes(ctx -> run(ctx, kind, StringArgumentType.getString(ctx, "message")))));
		}
	}

	public static void registerChatListener() {
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			String text = message.signedContent().trim();
			for (AgentChat.Kind kind : AgentChat.Kind.values()) {
				String prefix = "@" + kind.command;
				String lower = text.toLowerCase(Locale.ROOT);
				if (lower.equals(prefix) || lower.startsWith(prefix + " ") || lower.startsWith(prefix + ",") || lower.startsWith(prefix + ":")) {
					String rest = text.substring(prefix.length()).replaceFirst("^[\\s,:]+", "");
					if (!sender.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
						sender.sendSystemMessage(Component.literal("Only players with cheats or op can talk to " + kind.displayName + ".").withStyle(ChatFormatting.RED));
					} else {
						// Run after the player's own message shows up in chat.
						sender.level().getServer().execute(() -> AgentChat.handle(sender.level().getServer(), sender, kind, rest));
					}
					break;
				}
			}
			return true;
		});
	}

	private static int run(CommandContext<CommandSourceStack> ctx, AgentChat.Kind kind, String message) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			ctx.getSource().sendFailure(Component.literal("Run /" + kind.command + " as a player."));
			return 0;
		}
		if (!message.isBlank() && !message.equalsIgnoreCase("help")) {
			// Echo the message so everyone sees what was asked.
			ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(
				Component.literal("<" + player.getName().getString() + "> @" + kind.command + " " + message), false);
		}
		AgentChat.handle(ctx.getSource().getServer(), player, kind, message);
		return 1;
	}
}

package com.agentbridge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.net.BindException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/** /agent on | off | status | setup | undo [steps] | newtoken */
public final class AgentCommand {
	private AgentCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("agent")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.executes(AgentCommand::status)
			.then(Commands.literal("on").executes(AgentCommand::on))
			.then(Commands.literal("off").executes(AgentCommand::off))
			.then(Commands.literal("status").executes(AgentCommand::status))
			.then(Commands.literal("setup").executes(AgentCommand::setup))
			.then(Commands.literal("undo")
				.executes(ctx -> undo(ctx, 1))
				.then(Commands.argument("steps", IntegerArgumentType.integer(1, 100))
					.executes(ctx -> undo(ctx, IntegerArgumentType.getInteger(ctx, "steps")))))
			.then(Commands.literal("newtoken").executes(AgentCommand::newToken)));
	}

	private static int on(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		ServerPlayer player = src.getPlayer();
		if (player == null) {
			src.sendFailure(Component.literal("Run /agent on as a player, so the agent knows who it works for."));
			return 0;
		}
		boolean wasRunning = Bridge.isRunning();
		try {
			Bridge.start(src.getServer(), player);
		} catch (BindException e) {
			src.sendFailure(Component.literal("Port " + BridgeConfig.INSTANCE.port + " is already in use. Change \"port\" in config/agent-bridge.json."));
			return 0;
		} catch (Exception e) {
			AgentBridge.LOGGER.error("Could not start Agent Bridge", e);
			src.sendFailure(Component.literal("Could not start Agent Bridge: " + e.getMessage()));
			return 0;
		}
		src.sendSuccess(() -> Component.literal(wasRunning ? "🤖 Agent Bridge is already on." : "🤖 Agent Bridge is ON.")
			.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
		src.sendSuccess(() -> Component.literal("AI agents can now see and build in this world. Turn it off any time with /agent off.")
			.withStyle(ChatFormatting.GRAY), false);
		sendSetup(src);
		return 1;
	}

	private static int off(CommandContext<CommandSourceStack> ctx) {
		if (!Bridge.isRunning()) {
			ctx.getSource().sendSuccess(() -> Component.literal("Agent Bridge is already off."), false);
			return 0;
		}
		Bridge.stop(null);
		ctx.getSource().sendSuccess(() -> Component.literal("🤖 Agent Bridge is OFF. Agents can no longer reach the game.").withStyle(ChatFormatting.RED), false);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		if (Bridge.isRunning()) {
			src.sendSuccess(() -> Component.literal("🤖 Agent Bridge is ON at " + BridgeConfig.INSTANCE.url()
				+ ". Last client: " + Bridge.clientName() + ". Undo history: " + Bridge.undo().size() + " action(s).").withStyle(ChatFormatting.GREEN), false);
		} else {
			src.sendSuccess(() -> Component.literal("🤖 Agent Bridge is off. Turn it on with /agent on.").withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	private static int setup(CommandContext<CommandSourceStack> ctx) {
		sendSetup(ctx.getSource());
		return 1;
	}

	private static int undo(CommandContext<CommandSourceStack> ctx, int steps) {
		String result = Bridge.undo().undo(ctx.getSource().getServer(), steps);
		ctx.getSource().sendSuccess(() -> Component.literal(result), false);
		return 1;
	}

	private static int newToken(CommandContext<CommandSourceStack> ctx) {
		BridgeConfig.INSTANCE.token = BridgeConfig.newToken();
		BridgeConfig.save();
		ctx.getSource().sendSuccess(() -> Component.literal("Made a new token. Old connections stop working; reconnect with the commands below.")
			.withStyle(ChatFormatting.YELLOW), false);
		sendSetup(ctx.getSource());
		return 1;
	}

	private static void sendSetup(CommandSourceStack src) {
		BridgeConfig c = BridgeConfig.INSTANCE;
		MutableComponent line = Component.literal("Connect an agent (one time): ").withStyle(ChatFormatting.GRAY)
			.append(copyButton("[Copy Claude Code command]", c.claudeCommand(), "Paste this into your terminal"))
			.append(Component.literal(" "))
			.append(copyButton("[Copy Codex config]", c.codexConfig(), "Paste this into ~/.codex/config.toml"));
		src.sendSuccess(() -> line, false);
	}

	private static MutableComponent copyButton(String label, String value, String hover) {
		return Component.literal(label).withStyle(style -> style
			.withColor(ChatFormatting.AQUA)
			.withUnderlined(true)
			.withClickEvent(new ClickEvent.CopyToClipboard(value))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover + "\n\n" + value))));
	}
}

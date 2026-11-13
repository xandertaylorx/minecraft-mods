package com.blockos.net;

import com.blockos.BlockOS;
import com.blockos.block.ComputerBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Messages between the computer screen (client) and the computer block (server). */
public final class Net {
	private Net() {
	}

	// ---- client -> server ----

	/** Run a terminal command. */
	public record RunCommand(BlockPos pos, String line) implements CustomPacketPayload {
		public static final Type<RunCommand> TYPE = new Type<>(BlockOS.id("run_command"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RunCommand> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, RunCommand::pos, ByteBufCodecs.stringUtf8(1024), RunCommand::line, RunCommand::new);

		@Override
		public Type<RunCommand> type() {
			return TYPE;
		}
	}

	/** Save (or with delete=true, remove) a file from an app like Notepad or Paint. */
	public record SaveFile(BlockPos pos, String path, String content, boolean delete) implements CustomPacketPayload {
		public static final Type<SaveFile> TYPE = new Type<>(BlockOS.id("save_file"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SaveFile> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, SaveFile::pos, ByteBufCodecs.stringUtf8(128), SaveFile::path,
			ByteBufCodecs.stringUtf8(ComputerBlockEntity.MAX_STORAGE), SaveFile::content, ByteBufCodecs.BOOL, SaveFile::delete, SaveFile::new);

		@Override
		public Type<SaveFile> type() {
			return TYPE;
		}
	}

	public record Close(BlockPos pos) implements CustomPacketPayload {
		public static final Type<Close> TYPE = new Type<>(BlockOS.id("close"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Close> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Close::pos, Close::new);

		@Override
		public Type<Close> type() {
			return TYPE;
		}
	}

	// ---- server -> client ----

	/** Full computer state. open=true asks the client to open the screen. */
	public record Sync(BlockPos pos, String label, String filesJson, String terminalJson, String running, int channel, boolean open)
		implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(BlockOS.id("sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, Sync::pos,
			ByteBufCodecs.stringUtf8(64), Sync::label,
			ByteBufCodecs.stringUtf8(ComputerBlockEntity.MAX_STORAGE * 2), Sync::filesJson,
			ByteBufCodecs.stringUtf8(65536), Sync::terminalJson,
			ByteBufCodecs.stringUtf8(128), Sync::running,
			ByteBufCodecs.VAR_INT, Sync::channel,
			ByteBufCodecs.BOOL, Sync::open,
			Sync::new);

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(RunCommand.TYPE, RunCommand.CODEC);
		PayloadTypeRegistry.serverboundPlay().registerLarge(SaveFile.TYPE, SaveFile.CODEC, ComputerBlockEntity.MAX_STORAGE * 4);
		PayloadTypeRegistry.serverboundPlay().register(Close.TYPE, Close.CODEC);
		PayloadTypeRegistry.clientboundPlay().registerLarge(Sync.TYPE, Sync.CODEC, ComputerBlockEntity.MAX_STORAGE * 6);

		ServerPlayNetworking.registerGlobalReceiver(RunCommand.TYPE, (p, ctx) -> withComputer(ctx.player(), p.pos(), c -> c.runCommand((ServerLevel) ctx.player().level(), ctx.player(), p.line())));
		ServerPlayNetworking.registerGlobalReceiver(SaveFile.TYPE, (p, ctx) -> withComputer(ctx.player(), p.pos(), c -> {
			if (p.delete()) c.deleteFile(p.path());
			else {
				String err = c.writeFile(p.path(), p.content());
				if (err != null) c.print(err);
			}
		}));
		ServerPlayNetworking.registerGlobalReceiver(Close.TYPE, (p, ctx) -> withComputer(ctx.player(), p.pos(), c -> c.closeFor(ctx.player())));
	}

	private static void withComputer(ServerPlayer player, BlockPos pos, java.util.function.Consumer<ComputerBlockEntity> action) {
		// Only players standing near the computer can use it
		if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 64.0) return;
		if (player.level().getBlockEntity(pos) instanceof ComputerBlockEntity c) action.accept(c);
	}

	public static void sendSync(ServerPlayer player, BlockPos pos, String label, String files, String terminal, String running, int channel, boolean open) {
		ServerPlayNetworking.send(player, new Sync(pos, label, files, terminal, running, channel, open));
	}
}

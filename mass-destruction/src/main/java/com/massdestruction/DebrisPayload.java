package com.massdestruction;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * server -> client: blocks that a black hole or tornado just tore out of the ground. The client draws them flying
 * round the effect at full frame rate (a real entity per block would stutter and cost far more).
 */
public record DebrisPayload(int entityId, List<Piece> pieces) implements CustomPacketPayload {
	public static final Type<DebrisPayload> TYPE = new Type<>(MassDestruction.id("debris"));

	public record Piece(int state, int x, int y, int z) {
		static final StreamCodec<RegistryFriendlyByteBuf, Piece> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Piece::state, ByteBufCodecs.VAR_INT, Piece::x, ByteBufCodecs.VAR_INT, Piece::y, ByteBufCodecs.VAR_INT, Piece::z, Piece::new);

		public BlockState blockState() {
			return Block.stateById(this.state);
		}
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, DebrisPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, DebrisPayload::entityId, Piece.CODEC.apply(ByteBufCodecs.list(256)), DebrisPayload::pieces, DebrisPayload::new);

	@Override
	public Type<DebrisPayload> type() {
		return TYPE;
	}

	/** Collects pieces during a tick and sends them in one packet to everyone who can see the effect. */
	public static final class Batch {
		private final List<Piece> pieces = new ArrayList<>();

		public void add(BlockState state, int x, int y, int z) {
			if (this.pieces.size() < 256) this.pieces.add(new Piece(Block.getId(state), x, y, z));
		}

		public void flush(Entity owner) {
			if (this.pieces.isEmpty()) return;
			DebrisPayload payload = new DebrisPayload(owner.getId(), List.copyOf(this.pieces));
			this.pieces.clear();
			for (ServerPlayer player : PlayerLookup.tracking(owner)) {
				if (ServerPlayNetworking.canSend(player, TYPE)) ServerPlayNetworking.send(player, payload);
			}
		}
	}
}

package com.petdragons;

import com.petdragons.entity.DragonEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class Net {
	/** The rider holds (or lets go of) the breath key. */
	public record Breathe(boolean on) implements CustomPacketPayload {
		public static final Type<Breathe> TYPE = new Type<>(PetDragons.id("breathe"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Breathe> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Breathe::on, Breathe::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private Net() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Breathe.TYPE, Breathe.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Breathe.TYPE, (payload, ctx) -> ctx.server().execute(() -> {
			if (ctx.player().getVehicle() instanceof DragonEntity dragon && dragon.getControllingPassenger() == ctx.player()) {
				dragon.setRiderBreathing(payload.on());
			}
		}));
	}
}

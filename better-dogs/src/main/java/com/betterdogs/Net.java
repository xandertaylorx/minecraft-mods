package com.betterdogs;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.DyeColor;

/** Messages between the dog settings screen (client) and the dog (server). */
public final class Net {
	private Net() {
	}

	/** server -> client: a dog's settings. open=true opens the screen, false just refreshes it. */
	public record DogState(int entityId, DogSettings settings, boolean open) implements CustomPacketPayload {
		public static final Type<DogState> TYPE = new Type<>(BetterDogs.id("dog_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DogState> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DogState::entityId, DogSettings.STREAM_CODEC, DogState::settings, ByteBufCodecs.BOOL, DogState::open, DogState::new);

		@Override
		public Type<DogState> type() {
			return TYPE;
		}
	}

	/** client -> server: change one setting or do an action (see {@link #apply}). */
	public record Change(int entityId, String key, String value) implements CustomPacketPayload {
		public static final Type<Change> TYPE = new Type<>(BetterDogs.id("change"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Change> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Change::entityId, ByteBufCodecs.stringUtf8(32), Change::key, ByteBufCodecs.stringUtf8(64), Change::value, Change::new);

		@Override
		public Type<Change> type() {
			return TYPE;
		}
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(DogState.TYPE, DogState.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Change.TYPE, Change.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Change.TYPE, (c, ctx) -> {
			ServerPlayer player = ctx.player();
			if (!(player.level().getEntity(c.entityId()) instanceof Wolf wolf) || !wolf.isTame() || !wolf.isOwnedBy(player)) return;
			if (player.distanceToSqr(wolf) > 48 * 48) return;
			apply(wolf, player, c.key(), c.value());
			send(player, wolf, false);
		});
	}

	public static void send(ServerPlayer player, Wolf wolf, boolean open) {
		ServerPlayNetworking.send(player, new DogState(wolf.getId(), DogLogic.settings(wolf), open));
	}

	public static void apply(Wolf wolf, ServerPlayer owner, String key, String value) {
		boolean on = Boolean.parseBoolean(value);
		switch (key) {
			case "invincible" -> DogLogic.update(wolf, s -> s.withInvincible(on));
			case "regen" -> DogLogic.update(wolf, s -> s.withRegen(on));
			case "owner_safe" -> DogLogic.update(wolf, s -> s.withOwnerSafe(on));
			case "glow" -> DogLogic.update(wolf, s -> s.withGlow(on));
			case "swift" -> DogLogic.update(wolf, s -> s.withSwift(on));
			case "danger_bark" -> DogLogic.update(wolf, s -> s.withDangerBark(on));
			case "collect" -> {
				for (DogSettings.Collect m : DogSettings.Collect.values()) if (m.name().equals(value)) DogLogic.update(wolf, s -> s.withCollect(m));
			}
			case "behavior" -> {
				for (DogSettings.Behavior b : DogSettings.Behavior.values()) if (b.name().equals(value)) {
					DogLogic.update(wolf, s -> s.withBehavior(b));
					if (b == DogSettings.Behavior.PASSIVE) wolf.setTarget(null);
				}
			}
			case "name" -> {
				String name = value.strip();
				wolf.setCustomName(name.isEmpty() ? null : Component.literal(name.length() > 32 ? name.substring(0, 32) : name));
			}
			case "collar" -> {
				DyeColor color = DyeColor.byName(value, null);
				if (color != null) DogLogic.setCollar(wolf, color);
			}
			case "sit" -> {
				wolf.setOrderedToSit(on);
				wolf.setInSittingPose(on);
				wolf.getNavigation().stop();
				wolf.setTarget(null);
			}
			case "call" -> DogLogic.callToOwner(wolf, owner);
			default -> {
			}
		}
	}
}

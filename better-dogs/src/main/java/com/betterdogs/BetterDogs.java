package com.betterdogs;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.wolf.Wolf;

public class BetterDogs implements ModInitializer {
	public static final String MOD_ID = "better_dogs";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/** Each tamed dog's settings, saved with the dog. */
	public static final AttachmentType<DogSettings> SETTINGS = AttachmentRegistry.create(id("settings"),
		builder -> builder.persistent(DogSettings.CODEC));

	@Override
	public void onInitialize() {
		Net.init();

		// Sneak + right-click your own tamed dog to open its settings
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!player.isShiftKeyDown() || player.isSpectator() || !(entity instanceof Wolf wolf) || !wolf.isTame()) return InteractionResult.PASS;
			if (level.isClientSide()) return InteractionResult.SUCCESS;
			if (hand != InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;
			if (!wolf.isOwnedBy(player)) {
				player.sendSystemMessage(Component.literal("That's not your dog."));
				return InteractionResult.SUCCESS;
			}
			Net.send((ServerPlayer) player, wolf, true);
			return InteractionResult.SUCCESS;
		});

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> DogLogic.allowDamage(entity, source));
		ServerLivingEntityEvents.AFTER_DEATH.register(DogLogic::afterDeath);
	}
}

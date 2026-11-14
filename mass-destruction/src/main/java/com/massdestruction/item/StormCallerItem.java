package com.massdestruction.item;

import com.massdestruction.entity.TornadoEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Right-click a spot up to 120 blocks away to call a tornado down on it. While you hold the Storm Caller the tornado
 * goes wherever you point it. Sneak + right-click sends your tornadoes back up into the clouds.
 */
public class StormCallerItem extends Item {
	public static final double RANGE = 120.0;

	public StormCallerItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
		if (player.isSecondaryUseActive()) {
			var mine = server.getEntitiesOfClass(TornadoEntity.class, new AABB(player.blockPosition()).inflate(256), t -> t.isOwnedBy(player) && t.liftAt() < 0);
			mine.forEach(TornadoEntity::dismiss);
			if (mine.isEmpty()) ((net.minecraft.server.level.ServerPlayer) player).sendSystemMessage(Component.translatable("message.mass_destruction.no_tornado"), true);
			return InteractionResult.SUCCESS;
		}
		Aim.Target target = Aim.find(server, player, RANGE);
		if (target == null) {
			Aim.miss(server, player, stack);
			return InteractionResult.FAIL;
		}
		server.addFreshEntity(new TornadoEntity(server, target.at(), player, target.breaks()));
		server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.5F);
		player.getCooldowns().addCooldown(stack, 60);
		return InteractionResult.SUCCESS;
	}
}

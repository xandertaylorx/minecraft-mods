package com.massdestruction.item;

import com.massdestruction.entity.NovaEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Right-click a spot up to 160 blocks away: runes gather in the sky, then a mushroom of fire goes up. */
public class NovaRodItem extends Item {
	public static final double RANGE = 160.0;

	public NovaRodItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
		Aim.Target target = Aim.find(server, player, RANGE);
		if (target == null) {
			Aim.miss(server, player, stack);
			return InteractionResult.FAIL;
		}
		server.addFreshEntity(new NovaEntity(server, target.at(), player, target.breaks()));
		server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.5F);
		player.getCooldowns().addCooldown(stack, 140);
		return InteractionResult.SUCCESS;
	}
}

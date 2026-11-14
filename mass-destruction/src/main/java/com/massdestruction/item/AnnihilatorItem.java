package com.massdestruction.item;

import com.massdestruction.entity.AnnihilatorStrikeEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Right-click a spot up to 200 blocks away: four seconds later a beam twenty blocks wide comes down from orbit. */
public class AnnihilatorItem extends Item {
	public static final double RANGE = 200.0;

	public AnnihilatorItem(Item.Properties properties) {
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
		server.addFreshEntity(new AnnihilatorStrikeEntity(server, target.at(), player, target.breaks()));
		server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 0.6F);
		player.getCooldowns().addCooldown(stack, 120);
		return InteractionResult.SUCCESS;
	}
}

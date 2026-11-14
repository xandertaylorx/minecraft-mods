package com.massdestruction.item;

import com.massdestruction.world.RiftPortals;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Right-click the inside of a crying obsidian frame to tear the Rift open. */
public class RiftKeyItem extends Item {
	public RiftKeyItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
		boolean lit = RiftPortals.tryLight(level, ctx.getClickedPos().relative(ctx.getClickedFace()));
		if (!lit && ctx.getPlayer() != null) {
			((net.minecraft.server.level.ServerPlayer) ctx.getPlayer()).sendSystemMessage(Component.translatable("message.mass_destruction.no_frame"), true);
		}
		return lit ? InteractionResult.SUCCESS : InteractionResult.FAIL;
	}
}

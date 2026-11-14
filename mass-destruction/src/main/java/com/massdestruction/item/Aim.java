package com.massdestruction.item;

import com.massdestruction.Wreck;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Where a player is pointing: the top of the block they look at (or in front of it, for walls). */
public final class Aim {
	private Aim() {
	}

	public record Target(Vec3 at, BlockPos block, boolean breaks) {
	}

	public static @Nullable Target find(ServerLevel level, Player player, double range) {
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
		if (hit.getType() == HitResult.Type.MISS) return null;
		BlockPos block = hit.getBlockPos();
		Vec3 at = hit.getDirection() == Direction.UP
			? new Vec3(hit.getLocation().x, block.getY() + 1.0, hit.getLocation().z)
			: Vec3.atBottomCenterOf(block.relative(hit.getDirection()));
		return new Target(at, block, Wreck.mayBreakBlocks(level, player, block));
	}

	public static void miss(ServerLevel level, Player player, ItemStack stack) {
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.8F, 1.4F);
		player.getCooldowns().addCooldown(stack, 10);
	}
}

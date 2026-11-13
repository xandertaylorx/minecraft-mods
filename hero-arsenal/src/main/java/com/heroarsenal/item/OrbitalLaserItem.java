package com.heroarsenal.item;

import com.heroarsenal.entity.OrbitalStrikeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Orbital Laser.
 * - Right-click a block up to 128 blocks away to mark it. A target ring charges up for 2.5 seconds,
 *   then a plasma beam hits from the sky: crater, fire, big damage and knockback in an 8-block radius.
 * - 15 second cooldown. The item does nothing while idle (no inventory ticking).
 */
public class OrbitalLaserItem extends Item {
	public static final double RANGE = 128.0;
	public static final int COOLDOWN = 300;
	private static final int MISS_COOLDOWN = 10;

	public OrbitalLaserItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}

		Vec3 start = player.getEyePosition();
		Vec3 end = start.add(player.getLookAngle().scale(RANGE));
		BlockHitResult hit = serverLevel.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.MISS) {
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.8F, 1.4F);
			player.getCooldowns().addCooldown(stack, MISS_COOLDOWN);
			return InteractionResult.FAIL;
		}

		// Stand the strike on top of the block that was hit (or in front of it, for walls and ceilings).
		BlockPos block = hit.getBlockPos();
		Vec3 target = hit.getDirection() == Direction.UP
			? new Vec3(hit.getLocation().x, block.getY() + 1.0, hit.getLocation().z)
			: Vec3.atBottomCenterOf(block.relative(hit.getDirection()));

		// Respects spawn protection, adventure mode and the tnt_explodes game rule for block damage.
		boolean breaksBlocks = serverLevel.getGameRules().get(GameRules.TNT_EXPLODES)
			&& player.mayBuild()
			&& serverLevel.mayInteract(player, block);

		serverLevel.addFreshEntity(new OrbitalStrikeEntity(serverLevel, target, player, breaksBlocks));
		serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.8F);
		player.getCooldowns().addCooldown(stack, COOLDOWN);
		player.awardStat(Stats.ITEM_USED.get(this));
		return InteractionResult.SUCCESS;
	}
}

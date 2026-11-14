package com.massdestruction.block;

import com.massdestruction.world.RiftPortals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The torn sheet of the Rift inside a crying obsidian frame. Stand in it for two seconds to cross over. */
public class RiftPortalBlock extends Block implements Portal {
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	private static final VoxelShape X = Block.box(0, 0, 6, 16, 16, 10);
	private static final VoxelShape Z = Block.box(6, 0, 0, 10, 16, 16);

	public RiftPortalBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(AXIS) == Direction.Axis.X ? X : Z;
	}

	/** Breaks (with the rest of the sheet) as soon as a frame block or a neighbouring piece of the sheet is gone. */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir, BlockPos nPos, BlockState nState, RandomSource random) {
		Direction.Axis axis = state.getValue(AXIS);
		boolean inPlane = dir.getAxis() == Direction.Axis.Y || dir.getAxis() == axis;
		if (inPlane && !nState.is(this) && !nState.is(Blocks.CRYING_OBSIDIAN)) return Blocks.AIR.defaultBlockState();
		return state;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
		if (entity.canUsePortal(false)) entity.setAsInsidePortal(this, pos);
	}

	@Override
	public int getPortalTransitionTime(ServerLevel level, Entity entity) {
		return entity instanceof Player p ? (p.getAbilities().invulnerable ? 1 : 40) : 0;
	}

	@Override
	public TeleportTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
		return RiftPortals.destination(level, entity, pos);
	}

	@Override
	public Portal.Transition getLocalTransition() {
		return Portal.Transition.CONFUSION;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(120) == 0) {
			level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.4F, 0.5F + random.nextFloat() * 0.3F, false);
		}
		for (int i = 0; i < 2; i++) {
			double x = pos.getX() + random.nextDouble(), y = pos.getY() + random.nextDouble(), z = pos.getZ() + random.nextDouble();
			double side = (random.nextBoolean() ? 1 : -1) * 0.6;
			if (state.getValue(AXIS) == Direction.Axis.X) z = pos.getZ() + 0.5 + side * 0.2;
			else x = pos.getX() + 0.5 + side * 0.2;
			level.addParticle(i == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.END_ROD, x, y, z, 0, random.nextFloat() < 0.5 ? 0.03 : -0.03, 0);
		}
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return ItemStack.EMPTY;
	}
}

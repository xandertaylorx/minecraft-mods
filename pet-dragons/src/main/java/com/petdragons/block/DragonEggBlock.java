package com.petdragons.block;

import com.petdragons.DragonColor;
import com.petdragons.entity.DragonEntity;
import com.petdragons.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A dragon egg. Put it next to something hot (fire, a campfire, lava, magma) and it slowly cracks: every few seconds of
 * warmth it moves one stage on, and after the last it hatches a hatchling that is tame to whoever is nearest.
 */
public class DragonEggBlock extends Block {
	public static final IntegerProperty HATCH = IntegerProperty.create("hatch", 0, 3);
	private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 15, 13);
	/** Ticks of warmth for each stage. */
	private static final int STAGE_TICKS = 140;

	public final DragonColor color;

	public DragonEggBlock(DragonColor color, Properties properties) {
		super(properties);
		this.color = color;
		this.registerDefaultState(this.stateDefinition.any().setValue(HATCH, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HATCH);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
		if (!level.isClientSide()) level.scheduleTick(pos, this, 20);
	}

	/** Anything hot within two blocks warms the egg. */
	public static boolean warm(Level level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.FIRE) || s.is(Blocks.MAGMA_BLOCK) || s.getFluidState().is(FluidTags.LAVA) || s.is(Blocks.LAVA_CAULDRON)
				|| s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!warm(level, pos)) {
			level.scheduleTick(pos, this, 40);
			return;
		}
		int stage = state.getValue(HATCH);
		int age = this.warmth(level, pos);
		if (age < STAGE_TICKS) {
			level.scheduleTick(pos, this, 10);
			level.sendParticles(ParticleTypes.SMALL_FLAME, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 1, 0.2, 0.1, 0.2, 0.01);
			this.setWarmth(level, pos, age + 10);
			return;
		}
		this.setWarmth(level, pos, 0);
		if (stage < 3) {
			level.setBlock(pos, state.setValue(HATCH, stage + 1), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.TURTLE_EGG_CRACK, SoundSource.BLOCKS, 0.9F, 0.9F + random.nextFloat() * 0.2F);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.25, 0.3, 0.25, 0.05);
			level.scheduleTick(pos, this, 10);
			return;
		}
		this.hatch(level, pos);
	}

	private void hatch(ServerLevel level, BlockPos pos) {
		Player owner = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 24.0, false);
		level.removeBlock(pos, false);
		DragonEntity dragon = ModEntities.DRAGON.create(level, EntitySpawnReason.TRIGGERED);
		if (dragon == null) return;
		dragon.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		dragon.setColor(this.color);
		if (owner != null) dragon.tame(owner);
		level.addFreshEntity(dragon);
		level.playSound(null, pos, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.NEUTRAL, 0.6F, 1.9F);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, this.defaultBlockState()), pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 40, 0.3, 0.3, 0.3, 0.1);
		level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.05);
	}

	/** Warmth is kept per egg in a small side table, so the block needs no block entity. */
	private int warmth(ServerLevel level, BlockPos pos) {
		return Warmth.get(level, pos);
	}

	private void setWarmth(ServerLevel level, BlockPos pos, int v) {
		Warmth.set(level, pos, v);
	}
}

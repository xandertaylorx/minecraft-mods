package com.heroarsenal.client.render;

import com.heroarsenal.entity.ReturningWeaponEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.function.Supplier;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Draws a thrown weapon using its own 3D item model, glowing and spinning. */
public class ReturningWeaponRenderer<T extends ReturningWeaponEntity> extends EntityRenderer<T, ReturningWeaponRenderer.State> {
	public enum Spin {
		/** End over end (hammer), and head-first on the way home. */
		TUMBLE,
		/** Flat like a frisbee (shield). */
		FLAT
	}

	private final ItemModelResolver itemModelResolver;
	private final Supplier<ItemStack> displayStack;
	private final Spin spin;
	private final float scale;
	private ItemStack cachedStack;

	public ReturningWeaponRenderer(EntityRendererProvider.Context context, Supplier<ItemStack> displayStack, Spin spin, float scale) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
		this.displayStack = displayStack;
		this.spin = spin;
		this.scale = scale;
	}

	@Override
	protected int getBlockLightLevel(T entity, BlockPos pos) {
		return 15;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.25F, 0.0F);
		poseStack.rotateDegrees(Axis.YP, state.heading);
		switch (this.spin) {
			case TUMBLE -> {
				if (state.returning) {
					poseStack.rotateDegrees(Axis.XP, 90.0F - state.pitch);
				} else {
					poseStack.rotateDegrees(Axis.XP, state.ageInTicks * 50.0F);
				}
			}
			case FLAT -> {
				// Tilt with the flight path a bit, lie flat, then spin fast.
				poseStack.rotateDegrees(Axis.XP, -state.pitch * 0.5F);
				poseStack.rotateDegrees(Axis.YP, state.ageInTicks * 70.0F);
				poseStack.rotateDegrees(Axis.XP, 90.0F);
			}
		}
		poseStack.scale(this.scale, this.scale, this.scale);
		state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	protected AABB getBoundingBoxForCulling(T entity, float partialTicks) {
		return super.getBoundingBoxForCulling(entity, partialTicks).inflate(1.5);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(T entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		if (this.cachedStack == null) {
			this.cachedStack = this.displayStack.get();
		}
		this.itemModelResolver.updateForNonLiving(state.item, this.cachedStack, ItemDisplayContext.FIXED, entity);

		Vec3 motion = entity.getDeltaMovement();
		if (motion.horizontalDistanceSqr() > 1.0E-4) {
			state.heading = (float) (Mth.atan2(motion.x, motion.z) * Mth.RAD_TO_DEG);
		}
		state.pitch = (float) (Mth.atan2(motion.y, motion.horizontalDistance()) * Mth.RAD_TO_DEG);
		state.returning = entity.isReturning();
	}

	public static class State extends EntityRenderState {
		public final ItemStackRenderState item = new ItemStackRenderState();
		public float heading;
		public float pitch;
		public boolean returning;
	}
}

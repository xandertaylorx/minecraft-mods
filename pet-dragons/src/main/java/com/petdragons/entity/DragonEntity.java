package com.petdragons.entity;

import com.petdragons.DragonColor;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A pet dragon. It hatches from an egg as a hatchling, grows into a juvenile and then a full-grown dragon (feed it raw
 * meat or fish to speed that up), and breathes a different kind of fire for each of its five colours. Juveniles and
 * adults can be ridden (right-click with an empty hand); adults can fly: jump to take off, look where you want to
 * go, and hold the breath key to burn what is in front of you.
 */
public class DragonEntity extends TamableAnimal {
	public static final int JUVENILE_AT = 4800;
	public static final int ADULT_AT = 14400;
	public static final int FOOD_GROWTH = 1200;
	/** Breath meter: it drains while breathing and refills while not. */
	public static final int BREATH_MAX = 100;

	private static final EntityDataAccessor<Integer> GROWTH = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> BREATHING = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.BOOLEAN);

	public enum Stage {
		HATCHLING(0.45F, 20.0, 3.0), JUVENILE(0.8F, 40.0, 6.0), ADULT(1.5F, 80.0, 10.0);

		public final float scale;
		public final double health, attack;

		Stage(float scale, double health, double attack) {
			this.scale = scale;
			this.health = health;
			this.attack = attack;
		}
	}

	private boolean riderBreathing;
	private boolean aiBreathing;
	private int breath = BREATH_MAX;
	private int breathCooldown;
	private boolean hovering;
	private Stage lastStage = Stage.HATCHLING;

	public DragonEntity(EntityType<? extends DragonEntity> type, Level level) {
		super(type, level);
		this.updateControls();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FLYING_SPEED, 0.6)
			.add(Attributes.ATTACK_DAMAGE, 3.0)
			.add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.SCALE, 0.45)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	// ------------------------------------------------------------------ data

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(GROWTH, 0);
		builder.define(COLOR, 0);
		builder.define(FLYING, false);
		builder.define(BREATHING, false);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Growth", this.growth());
		output.putInt("Color", this.color().ordinal());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(GROWTH, input.getIntOr("Growth", 0));
		this.entityData.set(COLOR, input.getIntOr("Color", 0));
		this.applyStage(false);
	}

	public int growth() {
		return this.entityData.get(GROWTH);
	}

	public void setGrowth(int ticks) {
		this.entityData.set(GROWTH, Math.max(0, ticks));
		this.applyStage(false);
	}

	public DragonColor color() {
		return DragonColor.byIndex(this.entityData.get(COLOR));
	}

	public void setColor(DragonColor color) {
		this.entityData.set(COLOR, color.ordinal());
	}

	public Stage stage() {
		int g = this.growth();
		return g >= ADULT_AT ? Stage.ADULT : g >= JUVENILE_AT ? Stage.JUVENILE : Stage.HATCHLING;
	}

	/** How grown it is, 0 (just hatched) to 1 (adult). */
	public float maturity() {
		return Math.min(1.0F, this.growth() / (float) ADULT_AT);
	}

	public boolean canFly() {
		return this.stage() == Stage.ADULT;
	}

	public boolean isFlying() {
		return this.entityData.get(FLYING);
	}

	public boolean isBreathing() {
		return this.entityData.get(BREATHING);
	}

	public void setRiderBreathing(boolean on) {
		this.riderBreathing = on;
	}

	public int breathMeter() {
		return this.breath;
	}

	/** Sets size, health, damage and how it moves for its stage. */
	private void applyStage(boolean heal) {
		Stage s = this.stage();
		this.getAttribute(Attributes.SCALE).setBaseValue(s.scale);
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(s.health);
		this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(s.attack);
		if (heal) this.setHealth(this.getMaxHealth());
		else if (this.getHealth() > this.getMaxHealth()) this.setHealth(this.getMaxHealth());
		if (s != this.lastStage || this.moveControl == null) {
			this.lastStage = s;
			this.updateControls();
		}
	}

	private void updateControls() {
		if (this.canFly()) {
			this.moveControl = new FlyingMoveControl<>(this, 20, false);
			this.navigation = new FlyingPathNavigation(this, this.level());
		} else {
			this.moveControl = new MoveControl<>(this);
			this.navigation = new GroundPathNavigation(this, this.level());
		}
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new GroundPathNavigation(this, level);
	}

	// ------------------------------------------------------------------ goals

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new FloatGoal(this));
		this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
		this.goalSelector.addGoal(3, new BreathGoal(this));
		this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.2, true));
		this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.2, 7.0F, 2.5F));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 1.0) {
			@Override
			public boolean canUse() {
				return DragonEntity.this.canFly() && DragonEntity.this.getRandom().nextInt(3) == 0 && super.canUse();
			}
		});
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0) {
			@Override
			public boolean canUse() {
				return !DragonEntity.this.canFly() && super.canUse();
			}
		});
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
		this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
	}

	/** Turns on the breath while its target is in range and in sight. */
	private static class BreathGoal extends Goal {
		private final DragonEntity dragon;

		BreathGoal(DragonEntity dragon) {
			this.dragon = dragon;
			this.setFlags(EnumSet.of(Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity t = this.dragon.getTarget();
			return t != null && t.isAlive() && this.dragon.stage() != Stage.HATCHLING && !this.dragon.isVehicle() && this.dragon.breath > 25
				&& this.dragon.distanceTo(t) > 3.0F && this.dragon.distanceTo(t) < this.dragon.breathRange() && this.dragon.hasLineOfSight(t);
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity t = this.dragon.getTarget();
			return t != null && t.isAlive() && this.dragon.breath > 0 && this.dragon.distanceTo(t) < this.dragon.breathRange() + 2 && this.dragon.hasLineOfSight(t);
		}

		@Override
		public void start() {
			this.dragon.aiBreathing = true;
		}

		@Override
		public void stop() {
			this.dragon.aiBreathing = false;
		}

		@Override
		public void tick() {
			LivingEntity t = this.dragon.getTarget();
			if (t != null) this.dragon.getLookControl().setLookAt(t, 60.0F, 60.0F);
		}
	}

	// ------------------------------------------------------------------ riding

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return this.getFirstPassenger() instanceof Player p && this.isOwnedBy(p) ? p : null;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.stage() != Stage.HATCHLING && this.getPassengers().isEmpty() && passenger instanceof Player;
	}

	@Override
	protected void tickRidden(Player player, Vec3 input) {
		super.tickRidden(player, input);
		this.setRot(player.getYRot(), player.getXRot() * 0.5F);
		this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
		this.setTarget(null);
		this.getNavigation().stop();
	}

	@Override
	protected Vec3 getRiddenInput(Player player, Vec3 input) {
		float side = player.xxa * 0.5F;
		float fwd = player.zza;
		if (fwd <= 0.0F) fwd *= 0.5F;
		double up = 0.0;
		if (this.canFly()) {
			if (player.isJumping()) {
				up = 0.9;
			} else if (this.hovering && fwd > 0.0F) {
				up = -Math.sin(Math.toRadians(player.getXRot())) * fwd * 1.4;
			}
		}
		return new Vec3(side, up, fwd);
	}

	@Override
	protected float getRiddenSpeed(Player player) {
		return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * (player.isSprinting() ? 1.5F : 1.0F);
	}

	@Override
	protected float getFlyingSpeed() {
		return this.getControllingPassenger() != null && this.hovering ? 0.05F * (this.getControllingPassenger().isSprinting() ? 1.5F : 1.0F) : 0.02F;
	}

	/** On the side that is steering (the rider's client): jump to take off, stay up until you touch down again. */
	@Override
	public void travel(Vec3 input) {
		LivingEntity rider = this.getControllingPassenger();
		if (rider instanceof Player player && this.isLocalInstanceAuthoritative() && this.canFly()) {
			if (player.isJumping()) this.hovering = true;
			else if (this.onGround() && this.hovering) this.hovering = false;
			this.setNoGravity(this.hovering);
		} else if (rider == null && this.hovering) {
			this.hovering = false;
			this.setNoGravity(false);
		}
		super.travel(input);
	}

	// ------------------------------------------------------------------ ticking

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) return;
		ServerLevel level = (ServerLevel) this.level();
		if (this.stage() != Stage.ADULT || this.tickCount % 20 == 0) {
			if (this.growth() < ADULT_AT) {
				int before = this.growth();
				this.entityData.set(GROWTH, before + 1);
				Stage now = this.stage();
				if (now != this.lastStage) {
					this.applyStage(true);
					level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.NEUTRAL, 0.8F, now == Stage.ADULT ? 0.8F : 1.3F);
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + this.getBbHeight() * 0.5, this.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
				}
			}
		}
		boolean flying = this.canFly() && !this.onGround() && !this.isInWater() && (this.getControllingPassenger() != null || this.isNoGravity() || this.getDeltaMovement().y > -0.05);
		if (flying != this.isFlying()) this.entityData.set(FLYING, flying);

		boolean wants = (this.riderBreathing && this.getControllingPassenger() != null || this.aiBreathing) && this.stage() != Stage.HATCHLING;
		boolean on = wants && this.breath > 0 && this.breathCooldown <= 0;
		if (on != this.isBreathing()) {
			this.entityData.set(BREATHING, on);
			if (on) level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.NEUTRAL, 1.0F, 1.2F - this.maturity() * 0.4F);
		}
		if (on) {
			this.breath = Math.max(0, this.breath - 1);
			if (this.breath == 0) this.breathCooldown = 50;
			this.breathe(level);
		} else {
			if (this.breathCooldown > 0) this.breathCooldown--;
			else if (this.tickCount % 2 == 0 && this.breath < BREATH_MAX) this.breath++;
		}
		if (this.tickCount % 200 == 0 && this.getHealth() < this.getMaxHealth() && this.isTame()) this.heal(1.0F);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.isFlying()) {
			if (!this.level().isClientSide() && this.tickCount % 12 == 0) {
				this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.NEUTRAL, 0.5F * this.getScale(), 1.3F - this.maturity() * 0.3F);
			}
			Vec3 v = this.getDeltaMovement();
			if (!this.isNoGravity() && v.y < 0.0) this.setDeltaMovement(v.x, v.y * 0.6, v.z);
		}
	}

	@Override
	public boolean causeFallDamage(double distance, float damageModifier, DamageSource source) {
		return false;
	}

	// ------------------------------------------------------------------ breath

	public double breathRange() {
		return 8.0 + 5.0 * this.getScale();
	}

	/** The mouth, and the way it is breathing. */
	private Vec3 mouth() {
		Vec3 look = this.getLookAngle();
		return this.getEyePosition().add(look.scale(0.9 * this.getScale())).add(0, -0.1 * this.getScale(), 0);
	}

	private Vec3 breathDirection() {
		LivingEntity rider = this.getControllingPassenger();
		LivingEntity t = this.getTarget();
		if (rider != null) return rider.getLookAngle();
		if (t != null) return t.getEyePosition().subtract(this.getEyePosition()).normalize();
		return this.getLookAngle();
	}

	private void breathe(ServerLevel level) {
		DragonColor color = this.color();
		Vec3 dir = this.breathDirection();
		Vec3 from = this.mouth();
		double range = this.breathRange();
		BlockHitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
		double reach = hit.getType() == HitResult.Type.MISS ? range : from.distanceTo(hit.getLocation());
		// a stream of particles, wider farther out
		int n = 3 + (int) (this.getScale() * 3);
		for (int i = 0; i < n; i++) {
			double d = this.random.nextDouble() * reach;
			double spread = 0.12 + d * 0.07 * this.getScale();
			Vec3 at = from.add(dir.scale(d));
			level.sendParticles(i % 3 == 2 ? color.breathMix() : color.breath(), at.x, at.y, at.z, 1, spread, spread, spread, 0.02 + d * 0.004);
		}
		if (this.tickCount % 6 == 0) level.playSound(null, from.x, from.y, from.z, SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.7F, 0.8F + this.random.nextFloat() * 0.3F);
		if (this.tickCount % 4 == 0) this.burn(level, color, from, dir, reach);
		if (hit.getType() != HitResult.Type.MISS && this.tickCount % 5 == 0) this.scorch(level, color, hit);
	}

	/** Everything in the cone is hurt, and gets an effect that depends on the colour. */
	private void burn(ServerLevel level, DragonColor color, Vec3 from, Vec3 dir, double reach) {
		LivingEntity owner = this.getOwner();
		float damage = (float) (2.0 + this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.35);
		AABB box = new AABB(from, from.add(dir.scale(reach))).inflate(2.5);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
			if (e == this || e == owner || this.hasPassenger(e) || e instanceof TamableAnimal pet && owner != null && pet.isOwnedBy(owner)) continue;
			Vec3 to = e.getBoundingBox().getCenter().subtract(from);
			double dist = to.length();
			if (dist > reach + 1.0 || dist < 0.01 || to.normalize().dot(dir) < 0.82) continue;
			e.hurtServer(level, this.damageSources().mobAttack(this), damage);
			switch (color) {
				case EMBER -> e.igniteForSeconds(5.0F);
				case FROST -> {
					e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 60, e.getTicksFrozen() + 60));
					e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2), this);
				}
				case VERDANT -> e.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), this);
				case VOID -> e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 0), this);
				case SUN -> {
					e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0), this);
					e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), this);
				}
			}
		}
	}

	/** Where the breath lands: embers start fires, frost freezes water and puts fires out. */
	private void scorch(ServerLevel level, DragonColor color, BlockHitResult hit) {
		if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) return;
		BlockPos at = hit.getBlockPos();
		BlockPos front = at.relative(hit.getDirection());
		BlockState s = level.getBlockState(at);
		if (color == DragonColor.EMBER) {
			if (level.getBlockState(front).isAir() && Blocks.FIRE.defaultBlockState().canSurvive(level, front) && this.random.nextInt(3) == 0) {
				level.setBlockAndUpdate(front, Blocks.FIRE.defaultBlockState());
			}
		} else if (color == DragonColor.FROST) {
			if (s.is(Blocks.WATER) && s.getFluidState().isSource()) level.setBlockAndUpdate(at, Blocks.ICE.defaultBlockState());
			else if (s.is(Blocks.LAVA) && s.getFluidState().isSource()) level.setBlockAndUpdate(at, Blocks.OBSIDIAN.defaultBlockState());
			else if (level.getBlockState(front).is(Blocks.FIRE)) level.removeBlock(front, false);
			else if (level.getBlockState(front).isAir() && Blocks.SNOW.defaultBlockState().canSurvive(level, front) && this.random.nextInt(2) == 0) {
				level.setBlockAndUpdate(front, Blocks.SNOW.defaultBlockState());
			}
		}
	}

	// ------------------------------------------------------------------ interaction

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(ItemTags.MEAT) || stack.is(ItemTags.FISHES);
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!this.isTame()) {
			if (this.isFood(stack)) {
				if (!this.level().isClientSide()) {
					stack.consume(1, player);
					if (this.random.nextInt(3) == 0) {
						this.tame(player);
						this.level().broadcastEntityEvent(this, (byte) 7);
					} else {
						this.level().broadcastEntityEvent(this, (byte) 6);
					}
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (!this.isOwnedBy(player)) return InteractionResult.PASS;
		if (this.isFood(stack)) {
			boolean needsFood = this.growth() < ADULT_AT || this.getHealth() < this.getMaxHealth();
			if (!needsFood) return InteractionResult.PASS;
			if (!this.level().isClientSide()) {
				stack.consume(1, player);
				this.heal(6.0F);
				if (this.growth() < ADULT_AT) this.setGrowth(Math.min(ADULT_AT, this.growth() + FOOD_GROWTH));
				((ServerLevel) this.level()).sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + this.getBbHeight(), this.getZ(), 5, 0.4, 0.3, 0.4, 0.02);
				this.level().playSound(null, this.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.8F, 1.0F);
				if (this.stage() != this.lastStage) this.applyStage(true);
			}
			return InteractionResult.SUCCESS;
		}
		if (player.isShiftKeyDown()) {
			if (!this.level().isClientSide()) {
				this.setOrderedToSit(!this.isOrderedToSit());
				this.setInSittingPose(this.isOrderedToSit());
				this.navigation.stop();
				this.setTarget(null);
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.isEmpty() && this.stage() != Stage.HATCHLING) {
			if (!this.level().isClientSide()) {
				this.setOrderedToSit(false);
				this.setInSittingPose(false);
				player.startRiding(this);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}

	@Override
	public boolean canMate(net.minecraft.world.entity.animal.Animal partner) {
		return false;
	}

	@Override
	protected boolean canFlyToOwner() {
		return this.canFly();
	}

	// ------------------------------------------------------------------ sounds

	@Override
	public float getVoicePitch() {
		return 1.9F - this.maturity() * 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.1F;
	}

	@Override
	protected float getSoundVolume() {
		return 0.35F + this.maturity() * 0.5F;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return this.stage() == Stage.HATCHLING ? SoundEvents.PARROT_AMBIENT : SoundEvents.ENDER_DRAGON_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENDER_DRAGON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENDER_DRAGON_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.WOLF_STEP.value(), 0.15F, 0.8F);
	}

	@Override
	public int getAmbientSoundInterval() {
		return 260;
	}
}

package com.betterdogs;

import com.betterdogs.mixin.WolfAccessor;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.DyeColor;

/** What the settings actually do to a dog, every tick and when it gets hurt. */
public final class DogLogic {
	private DogLogic() {
	}

	private static final Identifier SWIFT_ID = BetterDogs.id("swift_paws");

	public static DogSettings settings(Wolf wolf) {
		return wolf.getAttachedOrElse(BetterDogs.SETTINGS, DogSettings.DEFAULT);
	}

	public static void update(Wolf wolf, UnaryOperator<DogSettings> change) {
		wolf.setAttached(BetterDogs.SETTINGS, change.apply(settings(wolf)));
	}

	/** Called at the end of every wolf tick (from WolfMixin). */
	public static void tick(Wolf wolf) {
		if (!(wolf.level() instanceof ServerLevel level) || !wolf.isAlive()) return;
		if (!wolf.isTame()) {
			if (wolf.hasAttached(BetterDogs.SETTINGS)) wolf.removeAttached(BetterDogs.SETTINGS);
			return;
		}
		DogSettings s = settings(wolf);

		if (wolf.hasGlowingTag() != s.glow()) wolf.setGlowingTag(s.glow());

		AttributeInstance speed = wolf.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			boolean has = speed.hasModifier(SWIFT_ID);
			if (s.swift() && !has) speed.addTransientModifier(new AttributeModifier(SWIFT_ID, 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			else if (!s.swift() && has) speed.removeModifier(SWIFT_ID);
		}

		if (s.behavior() == DogSettings.Behavior.PASSIVE && wolf.getTarget() != null) wolf.setTarget(null);

		if (s.invincible() && wolf.isOnFire()) wolf.clearFire();

		if (s.regen() && wolf.tickCount % 40 == 0 && wolf.getHealth() < wolf.getMaxHealth()) {
			wolf.heal(1.0F);
			if (wolf.tickCount % 120 == 0) level.sendParticles(ParticleTypes.HEART, wolf.getX(), wolf.getY() + 0.9, wolf.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
		}

		if (s.dangerBark() && wolf.tickCount % 50 == 0 && !wolf.isOrderedToSit() && wolf.getOwner() instanceof ServerPlayer owner
			&& owner.level() == level && owner.distanceToSqr(wolf) < 32 * 32) {
			List<Monster> threats = level.getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(14.0, 6.0, 14.0),
				m -> m.isAlive() && !m.hasEffect(MobEffects.GLOWING));
			if (!threats.isEmpty()) {
				for (Monster m : threats) m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100));
				wolf.playAmbientSound();
				level.sendParticles(ParticleTypes.ANGRY_VILLAGER, wolf.getX(), wolf.getY() + 1.0, wolf.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
			}
		}
	}

	/** ALLOW_DAMAGE: false cancels the hit. */
	public static boolean allowDamage(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof Wolf wolf) || !wolf.isTame()) return true;
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;   // /kill and the void still work
		DogSettings s = settings(wolf);
		if (s.invincible()) return false;
		return !(s.ownerSafe() && source.getEntity() instanceof LivingEntity attacker && wolf.isOwnedBy(attacker));
	}

	/** Counts mobs a dog finishes off. */
	public static void afterDeath(LivingEntity dead, DamageSource source) {
		if (source.getEntity() instanceof Wolf wolf && wolf.isTame() && dead != wolf.getOwner()) update(wolf, DogSettings::addKill);
	}

	public static void setCollar(Wolf wolf, DyeColor color) {
		((WolfAccessor) wolf).betterdogs$setCollarColor(color);
	}

	/** Brings the dog next to its owner. */
	public static void callToOwner(Wolf wolf, ServerPlayer owner) {
		if (owner.level() != wolf.level()) {
			owner.sendSystemMessage(Component.literal("Your dog is in another dimension."), true);
			return;
		}
		wolf.setOrderedToSit(false);
		wolf.getNavigation().stop();
		wolf.setTarget(null);
		wolf.teleportTo(owner.getX(), owner.getY(), owner.getZ());
		((ServerLevel) wolf.level()).sendParticles(ParticleTypes.POOF, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
	}
}

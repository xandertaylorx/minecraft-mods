package com.betterdogs.mixin;

import com.betterdogs.DogLogic;
import com.betterdogs.DogSettings;
import com.betterdogs.FetchItemsGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the fetch and guard goals to every wolf and runs the per-tick settings. */
@Mixin(Wolf.class)
public abstract class WolfMixin extends TamableAnimal {
	protected WolfMixin(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void betterdogs$addGoals(CallbackInfo ci) {
		Wolf self = (Wolf) (Object) this;
		// Same priority as the melee attack: beats "follow owner", never interrupts a fight
		this.goalSelector.addGoal(5, new FetchItemsGoal(self));
		// Guard mode: go after hostile mobs nearby (not creepers, they'd blow up next to you)
		this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(self, Monster.class, true,
			(target, level) -> self.isTame() && !self.isOrderedToSit() && !(target instanceof Creeper)
				&& DogLogic.settings(self).behavior() == DogSettings.Behavior.GUARD
				&& self.getOwner() != null && target.distanceToSqr(self.getOwner()) < 16 * 16));
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void betterdogs$tick(CallbackInfo ci) {
		DogLogic.tick((Wolf) (Object) this);
	}
}

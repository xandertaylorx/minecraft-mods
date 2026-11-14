package com.betterdogs;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Auto-collect: the dog runs to dropped items near its owner that match its Collect setting, picks them up and
 * brings them straight into the owner's inventory (the item flies to the owner like a normal pickup).
 */
public class FetchItemsGoal extends Goal {
	private static final double SEARCH = 12.0;          // how far from the dog it looks
	private static final double OWNER_RANGE = 20.0;     // only items this close to the owner, so it doesn't wander off
	private static final int GIVE_UP_TICKS = 160;

	private final Wolf wolf;
	private @Nullable ItemEntity target;
	private int ticks;
	private int searchCooldown;
	/** Items it couldn't reach, ignored for a while (item uuid -> game time to retry). */
	private final Map<UUID, Long> skip = new HashMap<>();

	public FetchItemsGoal(Wolf wolf) {
		this.wolf = wolf;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	private @Nullable Player owner() {
		return this.wolf.getOwner() instanceof Player p && p.level() == this.wolf.level() && p.isAlive() && !p.isSpectator() ? p : null;
	}

	private boolean allowed() {
		return this.wolf.isTame() && !this.wolf.isOrderedToSit() && !this.wolf.isPassenger() && !this.wolf.isLeashed()
			&& this.wolf.getTarget() == null && DogLogic.settings(this.wolf).collect() != DogSettings.Collect.OFF;
	}

	@Override
	public boolean canUse() {
		if (--this.searchCooldown > 0 || !this.allowed()) return false;
		this.searchCooldown = 10;
		Player owner = this.owner();
		if (owner == null || owner.distanceToSqr(this.wolf) > 24 * 24) return false;
		this.target = this.findItem(owner);
		return this.target != null;
	}

	private @Nullable ItemEntity findItem(Player owner) {
		DogSettings.Collect mode = DogLogic.settings(this.wolf).collect();
		long now = this.wolf.level().getGameTime();
		this.skip.values().removeIf(t -> t < now);
		ItemEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (ItemEntity item : this.wolf.level().getEntitiesOfClass(ItemEntity.class, this.wolf.getBoundingBox().inflate(SEARCH, 4.0, SEARCH))) {
			if (!item.isAlive() || item.hasPickUpDelay() || this.skip.containsKey(item.getUUID())) continue;
			if (item.getOwner() == owner) continue;                       // never steal things the owner threw away
			if (item.distanceToSqr(owner) > OWNER_RANGE * OWNER_RANGE) continue;
			if (!ItemFilter.accepts(mode, item.getItem())) continue;
			double d = item.distanceToSqr(this.wolf);
			if (d < bestDist) {
				bestDist = d;
				best = item;
			}
		}
		return best;
	}

	@Override
	public boolean canContinueToUse() {
		return this.target != null && this.target.isAlive() && this.ticks < GIVE_UP_TICKS && this.allowed() && this.owner() != null;
	}

	@Override
	public void start() {
		this.ticks = 0;
		if (this.target != null) this.wolf.getNavigation().moveTo(this.target, 1.25);
	}

	@Override
	public void stop() {
		if (this.target != null && this.target.isAlive() && this.ticks >= GIVE_UP_TICKS) {
			this.skip.put(this.target.getUUID(), this.wolf.level().getGameTime() + 600);
		}
		this.target = null;
		this.wolf.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		ItemEntity item = this.target;
		Player owner = this.owner();
		if (item == null || owner == null) return;
		this.ticks++;
		this.wolf.getLookControl().setLookAt(item, 30.0F, 30.0F);
		if (this.wolf.getBoundingBox().inflate(0.8, 0.5, 0.8).intersects(item.getBoundingBox())) {
			deliver(this.wolf, item, owner);
			this.target = null;
			return;
		}
		if (this.ticks % 10 == 0) this.wolf.getNavigation().moveTo(item, 1.25);
	}

	/** Moves the item from the ground into the owner's inventory (or at their feet if it's full). */
	static void deliver(Wolf wolf, ItemEntity item, LivingEntity owner) {
		ItemStack stack = item.getItem().copy();
		int count = stack.getCount();
		owner.take(item, count);                                      // pickup animation: the item flies to the owner
		if (owner instanceof Player p) p.getInventory().add(stack);   // shrinks the stack by what fit
		if (!stack.isEmpty()) {
			ItemEntity drop = new ItemEntity(owner.level(), owner.getX(), owner.getY() + 0.3, owner.getZ(), stack);
			drop.setThrower(owner);                                    // so the dog doesn't bring it straight back
			owner.level().addFreshEntity(drop);
		}
		item.discard();
		if (owner.level() instanceof ServerLevel level) {
			level.playSound(null, wolf.getX(), wolf.getY(), wolf.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.3F, 1.4F);
		}
		DogLogic.update(wolf, s -> s.addCollected(count));
	}
}

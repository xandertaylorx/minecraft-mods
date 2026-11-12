package com.heroarsenal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Lightning that looks and sounds real but never hurts the person who called it. */
public final class Thunder {
	public static final double RADIUS = 3.0;

	private Thunder() {
	}

	public static void strike(ServerLevel level, Vec3 pos, @Nullable Entity caller) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt == null) {
			return;
		}

		bolt.snapTo(pos.x, pos.y, pos.z);
		// Visual-only: no block fires and no damage to the caller. We apply the hit ourselves below.
		bolt.setVisualOnly(true);
		if (caller instanceof ServerPlayer player) {
			bolt.setCause(player);
		}
		level.addFreshEntity(bolt);

		AABB area = new AABB(pos, pos).inflate(RADIUS);
		for (Entity target : level.getEntities(caller, area, e -> e.isAlive()
			&& !(e instanceof LightningBolt)
			&& !(e instanceof ItemEntity)
			&& !(e instanceof ExperienceOrb)
			&& !(e instanceof com.heroarsenal.entity.MjolnirEntity)
			&& (caller == null || !e.isPassengerOfSameVehicle(caller)))) {
			// thunderHit = real lightning effects: damage + fire, creepers get charged, pigs turn into piglins, etc.
			target.thunderHit(level, bolt);
		}
	}
}

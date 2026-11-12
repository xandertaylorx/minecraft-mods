package com.heroarsenal;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Moving players from the server needs an explicit packet, since players control their own movement. */
public final class Motion {
	private Motion() {
	}

	public static void setVelocity(Entity entity, Vec3 velocity) {
		entity.setDeltaMovement(velocity);
		entity.resetFallDistance();
		if (entity instanceof ServerPlayer player) {
			player.connection.send(new ClientboundSetEntityMotionPacket(player));
		} else {
			entity.needsSync = true;
		}
	}
}

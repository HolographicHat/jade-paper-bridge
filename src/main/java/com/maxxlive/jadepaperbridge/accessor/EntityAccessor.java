package com.maxxlive.jadepaperbridge.accessor;

import com.maxxlive.jadepaperbridge.JadePaperBridge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * @param entity the targeted entity; for ender dragon parts this is the dragon itself
 */
public record EntityAccessor(
		ServerLevel level,
		ServerPlayer player,
		CompoundTag serverData,
		boolean showDetails,
		Entity entity,
		Vec3 hitVec,
		JadePaperBridge plugin) implements Accessor {

	@Override
	public Entity target() {
		return entity;
	}
}

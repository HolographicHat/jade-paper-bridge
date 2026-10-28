package com.maxxlive.jadepaperbridge.accessor;

import com.maxxlive.jadepaperbridge.JadePaperBridge;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public record BlockAccessor(
		ServerLevel level,
		ServerPlayer player,
		CompoundTag serverData,
		boolean showDetails,
		BlockHitResult hit,
		BlockState blockState,
		@Nullable BlockEntity blockEntity,
		ItemStack serversideRep,
		JadePaperBridge plugin) implements Accessor {

	public Block block() {
		return blockState.getBlock();
	}

	public BlockPos pos() {
		return hit.getBlockPos();
	}

	public Direction side() {
		return hit.getDirection();
	}

	@Override
	public @Nullable Object target() {
		return blockEntity;
	}

	public JadePaperBridge plugin() {
		return plugin;
	}
}

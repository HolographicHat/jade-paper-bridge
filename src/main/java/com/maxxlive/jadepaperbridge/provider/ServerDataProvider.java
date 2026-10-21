package com.maxxlive.jadepaperbridge.provider;

import com.maxxlive.jadepaperbridge.accessor.Accessor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

/**
 * Counterpart of {@code snownee.jade.api.IServerDataProvider}. The uid must match the uid of the
 * provider in the Jade client, otherwise the client ignores it.
 */
public interface ServerDataProvider<A extends Accessor> {
	Identifier uid();

	void appendServerData(CompoundTag data, A accessor);
}

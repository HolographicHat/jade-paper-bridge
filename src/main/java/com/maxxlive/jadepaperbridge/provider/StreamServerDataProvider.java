package com.maxxlive.jadepaperbridge.provider;

import org.jspecify.annotations.Nullable;

import com.maxxlive.jadepaperbridge.accessor.Accessor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamEncoder;

/**
 * Counterpart of {@code snownee.jade.api.StreamServerDataProvider}: the value is encoded with a stream codec
 * and stored as a byte array under the provider uid.
 */
public interface StreamServerDataProvider<A extends Accessor, D> extends ServerDataProvider<A> {
	@Nullable D streamData(A accessor);

	StreamEncoder<? super RegistryFriendlyByteBuf, D> streamCodec();

	@Override
	default void appendServerData(CompoundTag data, A accessor) {
		D value = streamData(accessor);
		if (value != null) {
			data.put(uid().toString(), accessor.encodeAsNbt(streamCodec(), value));
		}
	}
}

package com.maxxlive.jadepaperbridge.accessor;

import org.jspecify.annotations.Nullable;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamEncoder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side view of what the Jade client is looking at.
 * Mirrors {@code snownee.jade.api.Accessor} without the client-only parts.
 */
public interface Accessor {
	ServerLevel level();

	ServerPlayer player();

	/**
	 * The tag that is sent back to the client. It starts as the data the client sent along with its request.
	 */
	CompoundTag serverData();

	boolean showDetails();

	/**
	 * The block entity (blocks) or entity (entities). May be null for blocks without a block entity.
	 */
	@Nullable Object target();

	default <D> Tag encodeAsNbt(StreamEncoder<? super RegistryFriendlyByteBuf, D> codec, D value) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level().registryAccess());
		try {
			codec.encode(buf, value);
			byte[] bytes = new byte[buf.readableBytes()];
			buf.readBytes(bytes);
			return new ByteArrayTag(bytes);
		} finally {
			buf.release();
		}
	}
}

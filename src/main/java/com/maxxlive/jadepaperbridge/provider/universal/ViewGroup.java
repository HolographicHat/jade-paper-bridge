package com.maxxlive.jadepaperbridge.provider.universal;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Wire-compatible copy of {@code snownee.jade.api.view.ViewGroup}.
 */
public class ViewGroup<T> {
	public static <B extends ByteBuf, T> StreamCodec<B, ViewGroup<T>> codec(StreamCodec<B, T> viewCodec) {
		return StreamCodec.composite(
				ByteBufCodecs.<B, T>list().apply(viewCodec),
				$ -> $.views,
				ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8),
				$ -> Optional.ofNullable($.id),
				ByteBufCodecs.optional(ByteBufCodecs.COMPOUND_TAG),
				$ -> Optional.ofNullable($.extraData),
				ViewGroup::new);
	}

	/**
	 * Codec for the pair of (extension provider uid, groups) that Jade stores under the storage provider uid.
	 */
	public static <B extends ByteBuf, T> StreamCodec<B, Map.Entry<Identifier, List<ViewGroup<T>>>> listCodec(StreamCodec<B, T> viewCodec) {
		return StreamCodec.composite(
				Identifier.STREAM_CODEC,
				Map.Entry::getKey,
				ByteBufCodecs.<B, ViewGroup<T>>list().apply(codec(viewCodec)),
				Map.Entry::getValue,
				Map::entry);
	}

	public List<T> views;
	public @Nullable String id;
	protected @Nullable CompoundTag extraData;

	public ViewGroup(List<T> views) {
		this(views, Optional.empty(), Optional.empty());
	}

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	public ViewGroup(List<T> views, Optional<String> id, Optional<CompoundTag> extraData) {
		this.views = views;
		this.id = id.orElse(null);
		this.extraData = extraData.orElse(null);
	}

	public CompoundTag getExtraData() {
		if (extraData == null) {
			extraData = new CompoundTag();
		}
		return extraData;
	}
}

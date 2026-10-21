package com.maxxlive.jadepaperbridge.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Encoder matching the decoder of {@code snownee.jade.util.JadeCodecs#PRIMITIVE_STREAM_CODEC}, used for the
 * synced server config values.
 */
public final class PrimitiveCodec {
	public static final StreamCodec<ByteBuf, Object> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public Object decode(ByteBuf buf) {
			byte b = buf.readByte();
			return switch (b) {
				case 0 -> false;
				case 1 -> true;
				case 2 -> ByteBufCodecs.VAR_INT.decode(buf);
				case 3 -> ByteBufCodecs.FLOAT.decode(buf);
				case 4 -> ByteBufCodecs.STRING_UTF8.decode(buf);
				default -> {
					if (b > 20) {
						yield b - 20;
					}
					throw new IllegalArgumentException("Unknown primitive type: " + b);
				}
			};
		}

		@Override
		public void encode(ByteBuf buf, Object o) {
			switch (o) {
				case Boolean b -> buf.writeByte(b ? 1 : 0);
				case Number n -> {
					float f = n.floatValue();
					if (f != (int) f) {
						buf.writeByte(3);
						ByteBufCodecs.FLOAT.encode(buf, f);
						return;
					}
					int i = n.intValue();
					// the decoder maps bytes above 20 to (b - 20), so only 1..107 fit into the short form
					if (i >= 1 && i <= Byte.MAX_VALUE - 20) {
						buf.writeByte(i + 20);
					} else {
						buf.writeByte(2);
						ByteBufCodecs.VAR_INT.encode(buf, i);
					}
				}
				case String s -> {
					buf.writeByte(4);
					ByteBufCodecs.STRING_UTF8.encode(buf, s);
				}
				default -> throw new IllegalArgumentException("Unknown primitive type: %s (%s)".formatted(o, o.getClass()));
			}
		}
	};

	private PrimitiveCodec() {
	}
}

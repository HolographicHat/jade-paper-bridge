package com.maxxlive.jadepaperbridge.provider.universal;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.maxxlive.jadepaperbridge.JadeIds;
import com.maxxlive.jadepaperbridge.accessor.BlockAccessor;
import com.maxxlive.jadepaperbridge.provider.StreamServerDataProvider;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamEncoder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Fluid storage for vanilla cauldrons. On Fabric this data comes from Fabric's transfer API, which exposes
 * empty, water and lava cauldrons. Amounts are in Fabric droplets because the Jade client runs on Fabric.
 */
public class FluidStorageProvider implements StreamServerDataProvider<BlockAccessor, Map.Entry<Identifier, List<ViewGroup<FluidStorageProvider.Data>>>> {
	public static final FluidStorageProvider INSTANCE = new FluidStorageProvider();
	private static final long BUCKET = 81000;

	/**
	 * Wire format of {@code snownee.jade.api.view.FluidView.Data} with a single {@code JadeFluidObject}.
	 */
	public record Data(List<FluidObject> fluids, long capacity) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
				FluidObject.STREAM_CODEC.apply(ByteBufCodecs.list()),
				Data::fluids,
				ByteBufCodecs.LONG,
				Data::capacity,
				Data::new);
	}

	public record FluidObject(Holder<Fluid> type, long amount, DataComponentPatch components) {
		public static final StreamCodec<RegistryFriendlyByteBuf, FluidObject> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.holderRegistry(Registries.FLUID),
				FluidObject::type,
				ByteBufCodecs.LONG,
				FluidObject::amount,
				DataComponentPatch.STREAM_CODEC,
				FluidObject::components,
				FluidObject::new);

		static FluidObject of(Fluid fluid, long amount) {
			return new FluidObject(fluid.builtInRegistryHolder(), amount, DataComponentPatch.EMPTY);
		}
	}

	private static final StreamCodec<RegistryFriendlyByteBuf, Map.Entry<Identifier, List<ViewGroup<Data>>>> STREAM_CODEC = ViewGroup.listCodec(
			Data.STREAM_CODEC);

	@Override
	public Map.@Nullable Entry<Identifier, List<ViewGroup<Data>>> streamData(BlockAccessor accessor) {
		BlockState state = accessor.blockState();
		FluidObject fluid;
		if (state.is(Blocks.CAULDRON)) {
			fluid = FluidObject.of(Fluids.EMPTY, 0);
		} else if (state.is(Blocks.WATER_CAULDRON)) {
			fluid = FluidObject.of(Fluids.WATER, state.getValue(LayeredCauldronBlock.LEVEL) * BUCKET / LayeredCauldronBlock.MAX_FILL_LEVEL);
		} else if (state.is(Blocks.LAVA_CAULDRON)) {
			fluid = FluidObject.of(Fluids.LAVA, BUCKET);
		} else {
			return null;
		}
		ViewGroup<Data> group = new ViewGroup<>(List.of(new Data(List.of(fluid), BUCKET)));
		return Map.entry(JadeIds.UNIVERSAL_FLUID_STORAGE_DEFAULT, List.of(group));
	}

	@Override
	public StreamEncoder<? super RegistryFriendlyByteBuf, Map.Entry<Identifier, List<ViewGroup<Data>>>> streamCodec() {
		return STREAM_CODEC;
	}

	@Override
	public Identifier uid() {
		return JadeIds.UNIVERSAL_FLUID_STORAGE;
	}
}

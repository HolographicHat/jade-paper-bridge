package com.maxxlive.jadepaperbridge.provider.block;

import java.util.List;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import com.maxxlive.jadepaperbridge.JadeIds;
import com.maxxlive.jadepaperbridge.accessor.BlockAccessor;
import com.maxxlive.jadepaperbridge.provider.StreamServerDataProvider;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamEncoder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CalibratedSculkSensorBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SelectableSlotContainer;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.CalibratedSculkSensorBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.ListBackedContainer;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerStateData;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Server halves of the block data providers in {@code snownee.jade.addon.vanilla} and {@code snownee.jade.addon.core}.
 */
public final class BlockProviders {
	private BlockProviders() {
	}

	public static final class BrewingStand implements StreamServerDataProvider<BlockAccessor, BrewingStand.Data> {
		public static final BrewingStand INSTANCE = new BrewingStand();

		public record Data(int fuel, int time) {
			public static final StreamCodec<ByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
					ByteBufCodecs.VAR_INT,
					Data::fuel,
					ByteBufCodecs.VAR_INT,
					Data::time,
					Data::new);
		}

		@Override
		public @Nullable Data streamData(BlockAccessor accessor) {
			if (!(accessor.blockEntity() instanceof BrewingStandBlockEntity brewingStand)) {
				return null;
			}
			return new Data(brewingStand.fuel, brewingStand.brewTime);
		}

		@Override
		public StreamEncoder<? super RegistryFriendlyByteBuf, Data> streamCodec() {
			return Data.STREAM_CODEC;
		}

		@Override
		public Identifier uid() {
			return JadeIds.MC_BREWING_STAND;
		}
	}

	public static final class Beehive implements StreamServerDataProvider<BlockAccessor, Byte> {
		public static final Beehive INSTANCE = new Beehive();

		@Override
		public @Nullable Byte streamData(BlockAccessor accessor) {
			if (!(accessor.blockEntity() instanceof BeehiveBlockEntity beehive)) {
				return null;
			}
			int bees = beehive.getOccupantCount();
			return (byte) (beehive.isFull() ? bees : -bees);
		}

		@Override
		public StreamEncoder<? super RegistryFriendlyByteBuf, Byte> streamCodec() {
			return ByteBufCodecs.BYTE;
		}

		@Override
		public Identifier uid() {
			return JadeIds.MC_BEEHIVE;
		}
	}

	public static final class CommandBlock implements StreamServerDataProvider<BlockAccessor, String> {
		public static final CommandBlock INSTANCE = new CommandBlock();

		@Override
		public @Nullable String streamData(BlockAccessor accessor) {
			if (!accessor.player().canUseGameMasterBlocks()) {
				return null;
			}
			if (!(accessor.blockEntity() instanceof CommandBlockEntity commandBlockEntity)) {
				return null;
			}
			String command = commandBlockEntity.getCommandBlock().getCommand();
			if (command.length() > 40) {
				command = command.substring(0, 37) + "...";
			}
			return command;
		}

		@Override
		public StreamEncoder<? super RegistryFriendlyByteBuf, String> streamCodec() {
			return ByteBufCodecs.STRING_UTF8;
		}

		@Override
		public Identifier uid() {
			return JadeIds.MC_COMMAND_BLOCK;
		}
	}

	public static final class HopperLock implements StreamServerDataProvider<BlockAccessor, Boolean> {
		public static final HopperLock INSTANCE = new HopperLock();

		@Override
		public Boolean streamData(BlockAccessor accessor) {
			return !accessor.blockState().getValue(BlockStateProperties.ENABLED);
		}

		@Override
		public StreamEncoder<? super RegistryFriendlyByteBuf, Boolean> streamCodec() {
			return ByteBufCodecs.BOOL;
		}

		@Override
		public Identifier uid() {
			return JadeIds.MC_HOPPER_LOCK;
		}
	}

	public static final class Jukebox implements StreamServerDataProvider<BlockAccessor, ItemStack> {
		public static final Jukebox INSTANCE = new Jukebox();

		@Override
		public @Nullable ItemStack streamData(BlockAccessor accessor) {
			if (!(accessor.blockEntity() instanceof JukeboxBlockEntity jukebox)) {
				return null;
			}
			return jukebox.getTheItem();
		}

		@Override
		public StreamEncoder<? super RegistryFriendlyByteBuf, ItemStack> streamCodec() {
			return ItemStack.OPTIONAL_STREAM_CODEC;
		}

		@Override
		public Identifier uid() {
			return JadeIds.MC_JUKEBOX;
		}
	}

	public static final class Lectern implements StreamServerDataProvider<BlockAccessor, ItemStack> {
		public static final Lectern INSTANCE = new Lectern();

		@Override
		public @Nullable ItemStack streamData(BlockAccessor accessor) {
			if (!(accessor.blockEntity() instanceof LecternBlockEntity lectern)) {
				return null;
			}
			return lectern.getBook();
		}

		@Override
		public StreamEncoder<? super RegistryFriendlyByteBuf, ItemStack> streamCodec() {
			return ItemStack.OPTIONAL_STREAM_CODEC;
		}

		@Override
		public Identifier uid() {
			return JadeIds.MC_LECTERN;
		}
	}

	public static final class Redstone implements StreamServerDataProvider<BlockAccessor, Integer> {
		public static final Redstone INSTANCE = new Redstone();

		@Override
		public @Nullable Integer streamData(BlockAccessor accessor) {
			BlockEntity blockEntity = accessor.blockEntity();
			if (blockEntity instanceof ComparatorBlockEntity comparator) {
				return comparator.getOutputSignal();
			} else if (blockEntity instanceof CalibratedSculkSensorBlockEntity) {
				Direction direction = accessor.blockState().getValue(CalibratedSculkSensorBlock.FACING).getOpposite();
				return accessor.level().getSignal(accessor.pos().relative(direction), direction);
			}
			return null;
		}

		@Override
		public StreamEncoder<? super RegistryFriendlyByteBuf, Integer> streamCodec() {
			return ByteBufCodecs.VAR_INT;
		}

		@Override
		public Identifier uid() {
			return JadeIds.MC_REDSTONE;
		}
	}

	public static final class Furnace implements StreamServerDataProvider<BlockAccessor, Furnace.Data> {
		public static final Furnace INSTANCE = new Furnace();

		public record Data(int progress, int total, List<ItemStack> inventory) {
			public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
					ByteBufCodecs.VAR_INT,
					Data::progress,
					ByteBufCodecs.VAR_INT,
					Data::total,
					ItemStack.OPTIONAL_LIST_STREAM_CODEC,
					Data::inventory,
					Data::new);
		}

		@Override
		public @Nullable Data streamData(BlockAccessor accessor) {
			if (!(accessor.blockEntity() instanceof AbstractFurnaceBlockEntity furnace)) {
				return null;
			}
			return new Data(
					furnace.cookingTimer,
					furnace.cookingTotalTime,
					List.of(furnace.getItem(0), furnace.getItem(1), furnace.getItem(2)));
		}

		@Override
		public StreamEncoder<? super RegistryFriendlyByteBuf, Data> streamCodec() {
			return Data.STREAM_CODEC;
		}

		@Override
		public Identifier uid() {
			return JadeIds.MC_FURNACE;
		}
	}

	public static final class Shelf implements StreamServerDataProvider<BlockAccessor, ItemStack> {
		public static final Shelf INSTANCE = new Shelf();

		@Override
		public @Nullable ItemStack streamData(BlockAccessor accessor) {
			if (!(accessor.block() instanceof SelectableSlotContainer slotContainer) ||
					!(accessor.blockEntity() instanceof ListBackedContainer container)) {
				return null;
			}
			Direction facing = accessor.blockState().getValueOrElse(HorizontalDirectionalBlock.FACING, accessor.side());
			OptionalInt slot = slotContainer.getHitSlot(accessor.hit(), facing);
			if (slot.isEmpty() || slot.getAsInt() >= container.getContainerSize()) {
				return null;
			}
			return container.getItem(slot.getAsInt());
		}

		@Override
		public StreamEncoder<? super RegistryFriendlyByteBuf, ItemStack> streamCodec() {
			return ItemStack.OPTIONAL_STREAM_CODEC;
		}

		@Override
		public Identifier uid() {
			return JadeIds.MC_SHELF;
		}
	}

	public static final class MobSpawnerCooldown implements StreamServerDataProvider<BlockAccessor, Integer> {
		public static final MobSpawnerCooldown INSTANCE = new MobSpawnerCooldown();

		@Override
		public @Nullable Integer streamData(BlockAccessor accessor) {
			if (!(accessor.blockEntity() instanceof TrialSpawnerBlockEntity spawner) || spawner.getState() != TrialSpawnerState.COOLDOWN) {
				return null;
			}
			TrialSpawnerStateData spawnerData = spawner.getTrialSpawner().getStateData();
			long gameTime = accessor.level().getGameTime();
			if (spawner.getTrialSpawner().canSpawnInLevel(accessor.level()) && gameTime < spawnerData.cooldownEndsAt) {
				return (int) (spawnerData.cooldownEndsAt - gameTime);
			}
			return null;
		}

		@Override
		public StreamEncoder<? super RegistryFriendlyByteBuf, Integer> streamCodec() {
			return ByteBufCodecs.VAR_INT;
		}

		@Override
		public Identifier uid() {
			return JadeIds.MC_MOB_SPAWNER_COOLDOWN;
		}
	}

	/**
	 * Custom names of named block entities (renamed chests, furnaces, ...).
	 */
	public static final class ObjectName implements StreamServerDataProvider<BlockAccessor, Component> {
		public static final ObjectName INSTANCE = new ObjectName();

		@Override
		public @Nullable Component streamData(BlockAccessor accessor) {
			BlockEntity blockEntity = accessor.blockEntity();
			if (!(blockEntity instanceof Nameable nameable)) {
				return null;
			}
			if (nameable instanceof ChestBlockEntity && accessor.block() instanceof ChestBlock &&
					accessor.blockState().getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
				MenuProvider menuProvider = accessor.blockState().getMenuProvider(accessor.level(), accessor.pos());
				if (menuProvider != null) {
					Component name = menuProvider.getDisplayName();
					if (!(name.getContents() instanceof TranslatableContents contents) ||
							!"container.chestDouble".equals(contents.getKey())) {
						return name;
					}
				}
			} else if (nameable.hasCustomName()) {
				return nameable.getDisplayName();
			}
			return blockEntity.components().get(DataComponents.ITEM_NAME);
		}

		@Override
		public StreamEncoder<? super RegistryFriendlyByteBuf, Component> streamCodec() {
			return ComponentSerialization.STREAM_CODEC;
		}

		@Override
		public Identifier uid() {
			return JadeIds.CORE_OBJECT_NAME;
		}
	}
}

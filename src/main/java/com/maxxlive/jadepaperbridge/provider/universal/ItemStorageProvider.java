package com.maxxlive.jadepaperbridge.provider.universal;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import com.ghostchu.quickshop.api.shop.permission.BuiltInShopPermission;
import com.ghostchu.quickshop.util.Util;
import com.maxxlive.jadepaperbridge.util.DominionUtil;
import net.minecraft.world.level.block.entity.*;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.maxxlive.jadepaperbridge.JadeIds;
import com.maxxlive.jadepaperbridge.accessor.Accessor;
import com.maxxlive.jadepaperbridge.accessor.BlockAccessor;
import com.maxxlive.jadepaperbridge.accessor.EntityAccessor;
import com.maxxlive.jadepaperbridge.provider.ServerDataProvider;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.LockCode;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.ChestBlock;

/**
 * Port of {@code snownee.jade.addon.universal.ItemStorageProvider} together with the two vanilla
 * extensions ({@code item_storage.default} and {@code campfire}). Fabric's transfer API is replaced
 * by plain vanilla {@link Container}s.
 */
public class ItemStorageProvider<A extends Accessor> implements ServerDataProvider<A> {
	public static final ItemStorageProvider<BlockAccessor> BLOCK = new ItemStorageProvider<>();
	public static final ItemStorageProvider<EntityAccessor> ENTITY = new ItemStorageProvider<>();

	private static final Cache<Object, ItemCollector<?>> targetCache = CacheBuilder.newBuilder()
			.weakKeys()
			.expireAfterAccess(Duration.ofMinutes(1))
			.build();

	/**
	 * Stacks above 99 do not fit the vanilla codec, so Jade moves the real count into custom data.
	 */
	private static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> ITEM_CODEC = ItemStack.OPTIONAL_STREAM_CODEC.map(
			stack -> stack,
			stack -> {
				int count = stack.getCount();
				if (count <= 99) {
					return stack;
				}
				stack = stack.copyWithCount(99);
				CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).update($ -> $.putInt("__JadeCount", count));
				stack.set(DataComponents.CUSTOM_DATA, data);
				return stack;
			});
	private static final StreamCodec<RegistryFriendlyByteBuf, Map.Entry<Identifier, List<ViewGroup<ItemStack>>>> STREAM_CODEC = ViewGroup.listCodec(
			ITEM_CODEC);

	// quickshop-bukkit/src/main/java/com/ghostchu/quickshop/listener/PlayerLockClickListener.java
	private boolean checkQuickShopAccess(A accessor) {
		if (!(accessor.target() instanceof BaseContainerBlockEntity block)) {
			return true;
		}
		var blockLvl = block.getLevel();
		var blockLoc = block.getLocation();
		if (blockLvl == null || blockLoc == null) {
			return true;
		}
		if (Util.isBlacklistWorld(blockLvl.getWorld()) || !Util.isShoppables(block.getBlockState().getBukkitMaterial())) {
			return true;
		}
		var shopApi = accessor.plugin().quickShopApi();
		var shopMgr = shopApi.getShopManager();
		var shop = shopMgr.getShop(blockLoc);
		if (shop == null) {
			return true;
		}
		var bukkitPlayer = (org.bukkit.entity.Player) accessor.player().getBukkitEntity();
		return !shopApi.getConfig().getBoolean("shop.lock")
				|| shop.playerAuthorize(bukkitPlayer.getUniqueId(), BuiltInShopPermission.ACCESS_INVENTORY)
				|| bukkitPlayer.hasPermission("quickshop.other.open");
	}

	private static final NamespacedKey JLI_CHEST_ID_KEY = new NamespacedKey("justlootit", "id");

	@Override
	public void appendServerData(CompoundTag tag, A accessor) {
		if (accessor.target() instanceof AbstractFurnaceBlockEntity) {
			return;
		}
		Object target = accessor.target();
		Player player = accessor.player();
		if (target instanceof BaseContainerBlockEntity te) {
			if (te.persistentDataContainer.has(JLI_CHEST_ID_KEY, PersistentDataType.LONG)) {
				tag.putBoolean("Loot", true);
				return;
			}
			if (!player.isCreative() && !player.isSpectator()) {
				if (!checkQuickShopAccess(accessor) || DominionUtil.isRestricted(accessor)) {
					tag.putBoolean("Locked", true);
					return;
				}
			}
		}
		Map.Entry<Identifier, List<ViewGroup<ItemStack>>> entry = extensionData(accessor);
		if (entry != null) {
			for (ViewGroup<ItemStack> group : entry.getValue()) {
				if (group.views.size() > ItemCollector.MAX_SIZE) {
					group.views = group.views.subList(0, ItemCollector.MAX_SIZE);
				}
			}
			tag.put(JadeIds.UNIVERSAL_ITEM_STORAGE.toString(), accessor.encodeAsNbt(STREAM_CODEC, entry));
			return;
		}
		if (target instanceof RandomizableContainer containerEntity && containerEntity.getLootTable() != null) {
			tag.putBoolean("Loot", true);
		} else if (target instanceof ContainerEntity containerEntity && containerEntity.getContainerLootTable() != null) {
			tag.putBoolean("Loot", true);
		} else if (!player.isCreative() && !player.isSpectator() && target instanceof BaseContainerBlockEntity te) {
			if (te.lockKey != LockCode.NO_LOCK) {
				tag.putBoolean("Locked", true);
			}
		}
	}

	private static Map.@Nullable Entry<Identifier, List<ViewGroup<ItemStack>>> extensionData(Accessor accessor) {
		if (accessor.target() instanceof CampfireBlockEntity campfire) {
			return Map.entry(JadeIds.MC_CAMPFIRE, campfireGroups(campfire));
		}
		List<ViewGroup<ItemStack>> groups = defaultGroups(accessor);
		return groups == null ? null : Map.entry(JadeIds.UNIVERSAL_ITEM_STORAGE_DEFAULT, groups);
	}

	private static List<ViewGroup<ItemStack>> campfireGroups(CampfireBlockEntity campfire) {
		List<ItemStack> list = Lists.newArrayList();
		for (int i = 0; i < campfire.cookingTime.length; i++) {
			ItemStack stack = campfire.getItems().get(i);
			if (stack.isEmpty()) {
				continue;
			}
			stack = stack.copy();
			int time = campfire.cookingTime[i] - campfire.cookingProgress[i];
			CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
					.update(tag -> tag.putInt("jade:cooking", time));
			stack.set(DataComponents.CUSTOM_DATA, customData);
			list.add(stack);
		}
		return List.of(new ViewGroup<>(list));
	}

	private static @Nullable List<ViewGroup<ItemStack>> defaultGroups(Accessor accessor) {
		Object target = accessor.target();
		if (target == null) {
			return createItemCollector(accessor).update(accessor);
		}
		if (target instanceof RandomizableContainer te && te.getLootTable() != null) {
			return null;
		}
		if (target instanceof ContainerEntity containerEntity && containerEntity.getContainerLootTable() != null) {
			return null;
		}
		if (target instanceof ArmorStand) {
			return null;
		}
		Player player = accessor.player();
		if (!player.isCreative() && !player.isSpectator() && target instanceof BaseContainerBlockEntity te) {
			if (te.lockKey != LockCode.NO_LOCK) {
				return null;
			}
		}
		if (target instanceof EnderChestBlockEntity) {
			PlayerEnderChestContainer inventory = player.getEnderChestInventory();
			return new ItemCollector<>(new ItemIterator.ContainerItemIterator($ -> inventory, 0)).update(accessor);
		}
		ItemCollector<?> itemCollector;
		try {
			itemCollector = targetCache.get(target, () -> createItemCollector(accessor));
		} catch (ExecutionException e) {
			return null;
		}
		return itemCollector.update(accessor);
	}

	private static ItemCollector<?> createItemCollector(Accessor accessor) {
		Object target = accessor.target();
		if (target instanceof Player) {
			return ItemCollector.EMPTY;
		}
		if (target instanceof AbstractHorse) {
			return new ItemCollector<>(new ItemIterator.ContainerItemIterator(
					a -> a.target() instanceof AbstractHorse horse ? horse.inventory : null, 0));
		}
		Container container = findContainer(accessor);
		if (container instanceof ChestBlockEntity) {
			return new ItemCollector<>(new ItemIterator.ContainerItemIterator(
					a -> {
						if (a.target() instanceof ChestBlockEntity be) {
							if (be.getBlockState().getBlock() instanceof ChestBlock chestBlock) {
								Container compound = ChestBlock.getContainer(
										chestBlock,
										be.getBlockState(),
										Objects.requireNonNull(be.getLevel()),
										be.getBlockPos(),
										true);
								if (compound != null) {
									return compound;
								}
							}
							return be;
						}
						return null;
					}, 0));
		}
		if (container != null) {
			return new ItemCollector<>(new ItemIterator.ContainerItemIterator(ItemStorageProvider::findContainer, 0));
		}
		return ItemCollector.EMPTY;
	}

	private static @Nullable Container findContainer(Accessor accessor) {
		Object target = accessor.target();
		if (target == null && accessor instanceof BlockAccessor blockAccessor &&
				blockAccessor.block() instanceof WorldlyContainerHolder holder) {
			return holder.getContainer(blockAccessor.blockState(), accessor.level(), blockAccessor.pos());
		} else if (target instanceof Container container) {
			return container;
		}
		return null;
	}

	@Override
	public Identifier uid() {
		return JadeIds.UNIVERSAL_ITEM_STORAGE;
	}
}

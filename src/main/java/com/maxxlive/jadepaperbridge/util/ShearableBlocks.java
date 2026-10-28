package com.maxxlive.jadepaperbridge.util;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.mojang.datafixers.util.Either;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootContext;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.jspecify.annotations.Nullable;

import com.google.common.collect.ImmutableList;

import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.AlternativesEntry;
import net.minecraft.world.level.storage.loot.entries.CompositeEntryBase;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.predicates.AnyOfCondition;
import net.minecraft.world.level.storage.loot.predicates.CompositeLootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;

/**
 * Port of {@code snownee.jade.addon.harvest.LootTableMineableCollector}: finds blocks whose loot table
 * drops something only when mined with shears. The client uses the list for the harvest tool hint.
 */
public final class ShearableBlocks {
	private static final Field LOOT_TABLE_POOLS = Reflect.field(LootTable.class, "pools");
	private static final Field LOOT_POOL_ENTRIES = Reflect.field(LootPool.class, "entries");
	private static final Field LOOT_POOL_CONDITION = Reflect.field(LootPool.class, "compositeCondition");
	private static final Field ENTRY_CONDITION = Reflect.field(LootPoolEntryContainer.class, "compositeCondition");
	private static final Field COMPOSITE_CHILDREN = Reflect.field(CompositeEntryBase.class, "children");
	private static final Field NESTED_CONTENT = Reflect.field(NestedLootTable.class, "contents");
	private static final Field CONDITION_PREDICATE = Reflect.field(CompositeLootItemCondition.class, "composedPredicate");

	private final HolderLookup.RegistryLookup<LootTable> lootRegistry;
	private final ItemStack toolItem;

	private ShearableBlocks(HolderLookup.RegistryLookup<LootTable> lootRegistry, ItemStack toolItem) {
		this.lootRegistry = lootRegistry;
		this.toolItem = toolItem;
	}

	public static List<Block> collect(HolderLookup.Provider registries) {
		ShearableBlocks collector = new ShearableBlocks(registries.lookupOrThrow(Registries.LOOT_TABLE), Items.SHEARS.getDefaultInstance());
		ImmutableList.Builder<Block> list = ImmutableList.builder();
		for (Block block : BuiltInRegistries.BLOCK) {
			if (block.getLootTable().isEmpty()) {
				continue;
			}
			LootTable lootTable = collector.lootRegistry.get(block.getLootTable().get()).map(Holder::value).orElse(null);
			if (collector.doLootTable(lootTable)) {
				list.add(block);
			}
		}
		list.add(Blocks.TRIPWIRE);
		return list.build();
	}

	private boolean doLootTable(@Nullable LootTable lootTable) {
		if (lootTable == null || lootTable == LootTable.EMPTY) {
			return false;
		}
		for (LootPool pool : Reflect.<List<LootPool>>get(LOOT_TABLE_POOLS, lootTable)) {
			if (doLootPool(pool)) {
				return true;
			}
		}
		return false;
	}

	private boolean doLootPool(LootPool lootPool) {
		List<LootPoolEntryContainer> entries = Reflect.get(LOOT_POOL_ENTRIES, lootPool);
		if (entries.isEmpty()) {
			return false;
		}
		Predicate<LootContext> condition = Reflect.get(LOOT_POOL_CONDITION, lootPool);
		if (isCorrectConditions(condition)) {
			return true;
		}
		for (LootPoolEntryContainer entry : entries) {
			if (doLootPoolEntry(entry)) {
				return true;
			}
		}
		return false;
	}

	private boolean doLootPoolEntry(LootPoolEntryContainer entry) {
		if (entry instanceof AlternativesEntry alternativesEntry) {
			for (LootPoolEntryContainer child : Reflect.<List<LootPoolEntryContainer>>get(COMPOSITE_CHILDREN, alternativesEntry)) {
				if (doLootPoolEntry(child)) {
					return true;
				}
			}
		} else if (entry instanceof NestedLootTable nestedLootTable) {
			var contents = Reflect.<Either<ResourceKey<LootTable>, LootTable>>get(NESTED_CONTENT, nestedLootTable);
			var lootDataResolver = ((CraftServer) Bukkit.getServer()).getHandle().getServer().reloadableRegistries().lookup();
			var lootTable = contents.map(
			    (name) -> lootDataResolver.get(name).map(Holder::value).orElse(LootTable.EMPTY),
				(table) -> table
			);
            return doLootTable(lootTable);
		} else {
			Predicate<LootContext> condition = Reflect.get(ENTRY_CONDITION, entry);
			return isCorrectConditions(condition);
		}
		return false;
	}

	private boolean isCorrectConditions(Predicate<LootContext> condition) {
		if (condition instanceof MatchTool(Optional<ItemPredicate> predicate)) {
			ItemPredicate itemPredicate = predicate.orElse(null);
			return itemPredicate != null && itemPredicate.test(toolItem);
		} else if (condition instanceof AnyOfCondition anyOfCondition) {
            return isCorrectConditions(Reflect.get(CONDITION_PREDICATE, anyOfCondition));
		}
		return false;
	}
}

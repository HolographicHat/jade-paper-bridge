package com.maxxlive.jadepaperbridge;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.jspecify.annotations.Nullable;

import com.maxxlive.jadepaperbridge.accessor.BlockAccessor;
import com.maxxlive.jadepaperbridge.accessor.EntityAccessor;
import com.maxxlive.jadepaperbridge.network.JadeNetworkHandler;
import com.maxxlive.jadepaperbridge.provider.ProviderRegistry;
import com.maxxlive.jadepaperbridge.provider.block.BlockProviders;
import com.maxxlive.jadepaperbridge.provider.entity.EntityProviders;
import com.maxxlive.jadepaperbridge.provider.universal.FluidStorageProvider;
import com.maxxlive.jadepaperbridge.provider.universal.ItemStorageProvider;
import com.maxxlive.jadepaperbridge.util.ShearableBlocks;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.CalibratedSculkSensorBlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.ShelfBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;

public final class JadePaperBridge extends JavaPlugin implements Listener {
	private final ProviderRegistry<BlockAccessor> blockProviders = new ProviderRegistry<>();
	private final ProviderRegistry<EntityAccessor> entityProviders = new ProviderRegistry<>();
	private final JadeNetworkHandler networkHandler = new JadeNetworkHandler(this);
	private Settings settings = new Settings(21, Set.of(), Map.of(), false);
	private List<Block> shearableBlocks = List.of();

	@Override
	public void onEnable() {
		saveDefaultConfig();
		settings = Settings.load(getConfig(), getLogger());
		registerProviders();
		updateShearableBlocks();

		Messenger messenger = getServer().getMessenger();
		for (var channel : List.of(JadeIds.PACKET_CLIENT_HANDSHAKE, JadeIds.PACKET_REQUEST_BLOCK, JadeIds.PACKET_REQUEST_ENTITY)) {
			messenger.registerIncomingPluginChannel(this, channel.toString(), networkHandler);
		}
		for (var channel : List.of(JadeIds.PACKET_SERVER_HANDSHAKE, JadeIds.PACKET_RECEIVE_DATA, JadeIds.PACKET_SHOW_OVERLAY)) {
			messenger.registerOutgoingPluginChannel(this, channel.toString());
		}
		getServer().getPluginManager().registerEvents(this, this);

		JadePaperBridgeCommand command = new JadePaperBridgeCommand(this);
		var pluginCommand = getCommand("jadepaperbridge");
		if (pluginCommand != null) {
			pluginCommand.setExecutor(command);
			pluginCommand.setTabCompleter(command);
		}

		getLogger().info("Serving %d block and %d entity data providers to Jade clients (protocol %s)".formatted(
				blockProviders.ids().size(),
				entityProviders.ids().size(),
				JadeNetworkHandler.PROTOCOL_VERSION));
	}

	@Override
	public void onDisable() {
		getServer().getMessenger().unregisterIncomingPluginChannel(this);
		getServer().getMessenger().unregisterOutgoingPluginChannel(this);
	}

	/**
	 * Mirrors the registrations in Jade's VanillaPlugin, UniversalPlugin and CorePlugin.
	 */
	private void registerProviders() {
		blockProviders.register(BlockProviders.BrewingStand.INSTANCE, BrewingStandBlockEntity.class);
		blockProviders.register(BlockProviders.Beehive.INSTANCE, BeehiveBlockEntity.class);
		blockProviders.register(BlockProviders.CommandBlock.INSTANCE, CommandBlockEntity.class);
		blockProviders.register(BlockProviders.HopperLock.INSTANCE, HopperBlockEntity.class);
		blockProviders.register(BlockProviders.Jukebox.INSTANCE, JukeboxBlockEntity.class);
		blockProviders.register(BlockProviders.Lectern.INSTANCE, LecternBlockEntity.class);
		blockProviders.register(BlockProviders.Redstone.INSTANCE, ComparatorBlockEntity.class);
		blockProviders.register(BlockProviders.Redstone.INSTANCE, CalibratedSculkSensorBlockEntity.class);
		blockProviders.register(BlockProviders.Furnace.INSTANCE, AbstractFurnaceBlockEntity.class);
		blockProviders.register(BlockProviders.Shelf.INSTANCE, ChiseledBookShelfBlockEntity.class);
		blockProviders.register(BlockProviders.Shelf.INSTANCE, ShelfBlockEntity.class);
		blockProviders.register(BlockProviders.MobSpawnerCooldown.INSTANCE, TrialSpawnerBlockEntity.class);
		blockProviders.register(BlockProviders.ObjectName.INSTANCE, BlockEntity.class);
		blockProviders.register(ItemStorageProvider.BLOCK, Block.class);
		blockProviders.register(FluidStorageProvider.INSTANCE, AbstractCauldronBlock.class);

		entityProviders.register(EntityProviders.AnimalOwner.INSTANCE, Entity.class);
		entityProviders.register(EntityProviders.StatusEffects.INSTANCE, LivingEntity.class);
		entityProviders.register(EntityProviders.MobGrowth.INSTANCE, AgeableMob.class);
		entityProviders.register(EntityProviders.MobGrowth.INSTANCE, Tadpole.class);
		entityProviders.register(EntityProviders.MobBreeding.INSTANCE, Animal.class);
		entityProviders.register(EntityProviders.MobBreeding.INSTANCE, Villager.class);
		entityProviders.register(EntityProviders.MobBreeding.INSTANCE, Allay.class);
		entityProviders.register(EntityProviders.VillagerRestock.INSTANCE, Villager.class);
		entityProviders.register(EntityProviders.NextEntityDrop.INSTANCE, Chicken.class);
		entityProviders.register(EntityProviders.NextEntityDrop.INSTANCE, Armadillo.class);
		entityProviders.register(EntityProviders.NextEntityDrop.INSTANCE, Sniffer.class);
		entityProviders.register(EntityProviders.ZombieVillagerConversion.INSTANCE, ZombieVillager.class);
		entityProviders.register(EntityProviders.PetArmor.INSTANCE, Mob.class);
		entityProviders.register(EntityProviders.Waxed.INSTANCE, CopperGolem.class);
		entityProviders.register(EntityProviders.Health.INSTANCE, LivingEntity.class);
		entityProviders.register(ItemStorageProvider.ENTITY, Entity.class);

		blockProviders.removeIf(settings.disabledProviders());
		entityProviders.removeIf(settings.disabledProviders());
	}

	private void updateShearableBlocks() {
		try {
			shearableBlocks = ShearableBlocks.collect(MinecraftServer.getServer().reloadableRegistries().lookup());
		} catch (Exception e) {
			getLogger().log(Level.SEVERE, "Failed to collect shearable blocks", e);
		}
	}

	/**
	 * Reloads config.yml and sends the new state to every connected Jade client.
	 */
	public void reload() {
		reloadConfig();
		settings = Settings.load(getConfig(), getLogger());
		blockProviders.clear();
		entityProviders.clear();
		registerProviders();
		resendHandshakes();
	}

	private void resendHandshakes() {
		for (UUID uuid : networkHandler.connectedPlayers()) {
			Player player = Bukkit.getPlayer(uuid);
			if (player != null) {
				networkHandler.sendServerHandshake(((CraftPlayer) player).getHandle());
			}
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		networkHandler.forget(event.getPlayer().getUniqueId());
	}

	@EventHandler
	public void onResourcesReloaded(ServerResourcesReloadedEvent event) {
		updateShearableBlocks();
		resendHandshakes();
	}

	public void debug(Level level, String message, @Nullable Throwable throwable) {
		if (settings.debug()) {
			getLogger().log(level, message, throwable);
		}
	}

	public Settings settings() {
		return settings;
	}

	public ProviderRegistry<BlockAccessor> blockProviders() {
		return blockProviders;
	}

	public ProviderRegistry<EntityAccessor> entityProviders() {
		return entityProviders;
	}

	public List<Block> shearableBlocks() {
		return shearableBlocks;
	}

	public JadeNetworkHandler networkHandler() {
		return networkHandler;
	}
}

package com.maxxlive.jadepaperbridge.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jspecify.annotations.Nullable;

import com.maxxlive.jadepaperbridge.JadeIds;
import com.maxxlive.jadepaperbridge.JadePaperBridge;
import com.maxxlive.jadepaperbridge.accessor.Accessor;
import com.maxxlive.jadepaperbridge.accessor.BlockAccessor;
import com.maxxlive.jadepaperbridge.accessor.EntityAccessor;
import com.maxxlive.jadepaperbridge.provider.ProviderRegistry;
import com.maxxlive.jadepaperbridge.provider.ServerDataProvider;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Implements the server side of Jade's network protocol (see {@code snownee.jade.network}).
 * Paper calls plugin message listeners on the main thread, so everything here runs on the main thread.
 */
public final class JadeNetworkHandler implements PluginMessageListener {
	public static final String PROTOCOL_VERSION = "9";
	private static final long REQUEST_INTERVAL_NANOS = 50_000_000L;
	private static final int MAX_DATA_SIZE = 16 * 1024;
	private static final int MAX_REQUESTED_PROVIDERS = 256;

	private final JadePaperBridge plugin;
	private final Map<UUID, PlayerState> players = new HashMap<>();
	private int oversizedWarnings;

	private static final class PlayerState {
		private boolean connected;
		private long lastRequestNanos;
	}

	public JadeNetworkHandler(JadePaperBridge plugin) {
		this.plugin = plugin;
	}

	public void forget(UUID player) {
		players.remove(player);
	}

	public Set<UUID> connectedPlayers() {
		Set<UUID> result = new HashSet<>();
		players.forEach((uuid, state) -> {
			if (state.connected) {
				result.add(uuid);
			}
		});
		return result;
	}

	@Override
	public void onPluginMessageReceived(String channel, Player bukkitPlayer, byte[] message) {
		ServerPlayer player = ((CraftPlayer) bukkitPlayer).getHandle();
		// an exception escaping here would get the player kicked by Paper
		try {
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(message), player.level().registryAccess());
			if (channel.equals(JadeIds.PACKET_CLIENT_HANDSHAKE.toString())) {
				handleHandshake(player, ByteBufCodecs.STRING_UTF8.decode(buf));
			} else if (channel.equals(JadeIds.PACKET_REQUEST_BLOCK.toString())) {
				if (tryAcquireRequest(player)) {
					handleBlockRequest(player, buf);
				}
			} else if (channel.equals(JadeIds.PACKET_REQUEST_ENTITY.toString())) {
				if (tryAcquireRequest(player)) {
					handleEntityRequest(player, buf);
				}
			}
		} catch (Exception e) {
			plugin.debug(Level.WARNING, "Failed to handle %s from %s".formatted(channel, bukkitPlayer.getName()), e);
		}
	}

	private boolean tryAcquireRequest(ServerPlayer player) {
		PlayerState state = players.get(player.getUUID());
		if (state == null || !state.connected) {
			return false;
		}
		long now = System.nanoTime();
		if (now - state.lastRequestNanos < REQUEST_INTERVAL_NANOS) {
			return false;
		}
		state.lastRequestNanos = now;
		return true;
	}

	private void handleHandshake(ServerPlayer player, String protocolVersion) {
		if (!PROTOCOL_VERSION.equals(protocolVersion)) {
			player.sendSystemMessage(Component.translatable("jade.protocolMismatch", "JadePaperBridge " + plugin.getPluginMeta().getVersion()));
			plugin.getLogger().info("%s uses Jade protocol %s, but only %s is supported".formatted(
					player.getPlainTextName(),
					protocolVersion,
					PROTOCOL_VERSION));
			return;
		}
		players.computeIfAbsent(player.getUUID(), $ -> new PlayerState()).connected = true;
		sendServerHandshake(player);
	}

	public void sendServerHandshake(ServerPlayer player) {
		Map<Identifier, Object> configs = plugin.settings().configOverrides();
		List<Identifier> blockProviderIds = plugin.blockProviders().ids();
		List<Identifier> entityProviderIds = plugin.entityProviders().ids();
		send(player, JadeIds.PACKET_SERVER_HANDSHAKE, buf -> {
			ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, PrimitiveCodec.STREAM_CODEC).encode(buf, new HashMap<>(configs));
			ByteBufCodecs.registry(Registries.BLOCK).apply(ByteBufCodecs.list()).encode(buf, plugin.shearableBlocks());
			ByteBufCodecs.<ByteBuf, Identifier>list().apply(Identifier.STREAM_CODEC).encode(buf, blockProviderIds);
			ByteBufCodecs.<ByteBuf, Identifier>list().apply(Identifier.STREAM_CODEC).encode(buf, entityProviderIds);
		});
		plugin.debug(Level.INFO, "Sent handshake to %s".formatted(player.getPlainTextName()), null);
	}

	public void sendShowOverlay(ServerPlayer player, boolean show) {
		send(player, JadeIds.PACKET_SHOW_OVERLAY, buf -> buf.writeBoolean(show));
	}

	private void handleBlockRequest(ServerPlayer player, RegistryFriendlyByteBuf buf) {
		boolean showDetails = buf.readBoolean();
		BlockHitResult hit = BlockHitResult.STREAM_CODEC.decode(buf);
		ItemStack serversideRep = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
		CompoundTag tag = ByteBufCodecs.COMPOUND_TAG.decode(buf);
		Set<ServerDataProvider<BlockAccessor>> requested = readProviders(buf, plugin.blockProviders());

		BlockPos pos = hit.getBlockPos();
		tag.putInt("x", pos.getX());
		tag.putInt("y", pos.getY());
		tag.putInt("z", pos.getZ());

		ServerLevel level = player.level();
		if (isOutOfReach(player, pos, player.blockInteractionRange()) || !level.isLoaded(pos)) {
			sendData(player, tag);
			return;
		}

		BlockState blockState = level.getBlockState(pos);
		BlockEntity blockEntity = blockState.hasBlockEntity() ? level.getBlockEntity(pos) : null;
		BlockAccessor accessor = new BlockAccessor(level, player, tag, showDetails, hit, blockState, blockEntity, serversideRep);
		tag.putString("BlockId", BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString());

		appendData(accessor, plugin.blockProviders().providersFor(blockState.getBlock(), blockEntity), requested);
		sendData(player, tag);
	}

	private void handleEntityRequest(ServerPlayer player, RegistryFriendlyByteBuf buf) {
		boolean showDetails = buf.readBoolean();
		int id = ByteBufCodecs.VAR_INT.decode(buf);
		int partIndex = ByteBufCodecs.VAR_INT.decode(buf);
		Vec3 hitVec = new Vec3(ByteBufCodecs.VECTOR3F.decode(buf));
		CompoundTag tag = ByteBufCodecs.COMPOUND_TAG.decode(buf);
		Set<ServerDataProvider<EntityAccessor>> requested = readProviders(buf, plugin.entityProviders());

		Entity entity = getPartEntity(player.level().getEntity(id), partIndex);
		if (entity == null) {
			return;
		}
		if (entity instanceof EnderDragonPart part) {
			entity = part.parentMob;
		}
		EntityAccessor accessor = new EntityAccessor(player.level(), player, tag, showDetails, entity, hitVec);
		tag.putInt("EntityId", entity.getId());

		if (isOutOfReach(player, entity.blockPosition(), player.entityInteractionRange()) || !canBeTarget(entity, player)) {
			sendData(player, tag);
			return;
		}

		appendData(accessor, plugin.entityProviders().providersFor(entity), requested);
		sendData(player, tag);
	}

	private <A extends Accessor> Set<ServerDataProvider<A>> readProviders(RegistryFriendlyByteBuf buf, ProviderRegistry<A> registry) {
		int size = ByteBufCodecs.VAR_INT.decode(buf);
		if (size < 0 || size > MAX_REQUESTED_PROVIDERS) {
			throw new IllegalArgumentException("Too many providers requested: " + size);
		}
		Set<ServerDataProvider<A>> providers = new HashSet<>();
		for (int i = 0; i < size; i++) {
			ServerDataProvider<A> provider = registry.byId(ByteBufCodecs.VAR_INT.decode(buf));
			if (provider != null) {
				providers.add(provider);
			}
		}
		return providers;
	}

	private <A extends Accessor> void appendData(A accessor, List<ServerDataProvider<A>> providers, Set<ServerDataProvider<A>> requested) {
		for (ServerDataProvider<A> provider : providers) {
			if (!requested.contains(provider)) {
				continue;
			}
			try {
				provider.appendServerData(accessor.serverData(), accessor);
			} catch (Exception e) {
				plugin.debug(Level.WARNING, "Provider %s failed".formatted(provider.uid()), e);
			}
		}
	}

	private boolean isOutOfReach(ServerPlayer player, BlockPos pos, double baseReach) {
		double maxDistance = Mth.square(baseReach + plugin.settings().maxPositionDeviation());
		return pos.distSqr(player.blockPosition()) > maxDistance;
	}

	private static boolean canBeTarget(Entity target, ServerPlayer player) {
		if (target.isRemoved() || target.isSpectator() || target == player.getVehicle()) {
			return false;
		}
		return !target.isInvisibleTo(player) || hasEquipment(target);
	}

	private static boolean hasEquipment(Entity entity) {
		if (!(entity instanceof LivingEntity living)) {
			return false;
		}
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			if (slot.isArmor() && living.hasItemInSlot(slot)) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable Entity getPartEntity(@Nullable Entity parent, int index) {
		if (parent == null || index < 0) {
			return parent;
		}
		if (parent instanceof EnderDragon dragon) {
			EnderDragonPart[] parts = dragon.getSubEntities();
			if (index < parts.length) {
				return parts[index];
			}
		}
		return parent;
	}

	private void sendData(ServerPlayer player, CompoundTag tag) {
		int size = tag.sizeInBytes();
		if (size > MAX_DATA_SIZE) {
			if (oversizedWarnings++ < 1) {
				plugin.debug(Level.INFO, "Data size is too large: %d, max: %d".formatted(size, MAX_DATA_SIZE), null);
			}
			int c = 0;
			do {
				if (++c > 10) {
					return;
				}
				removeLargest(tag, 0, 1);
			} while (tag.sizeInBytes() > MAX_DATA_SIZE);
		}
		send(player, JadeIds.PACKET_RECEIVE_DATA, buf -> ByteBufCodecs.COMPOUND_TAG.encode(buf, tag));
	}

	private static boolean removeLargest(CompoundTag tag, int depth, int maxDepth) {
		int largestSize = 0;
		String largestKey = null;
		Tag largestValue = null;
		for (String key : tag.keySet()) {
			Tag childTag = Objects.requireNonNull(tag.get(key));
			int size = childTag.sizeInBytes();
			if (size > largestSize) {
				largestSize = size;
				largestKey = key;
				largestValue = childTag;
			}
		}
		if (largestKey == null) {
			return false;
		}
		if (depth < maxDepth && largestValue instanceof CompoundTag compound) {
			if (!removeLargest(compound, depth + 1, maxDepth)) {
				tag.remove(largestKey);
			}
		} else {
			tag.remove(largestKey);
		}
		return true;
	}

	/**
	 * Sends the packet directly instead of using {@code Player#sendPluginMessage}, which silently drops it
	 * when the client has not announced the channel yet (the Jade handshake can arrive before that).
	 */
	private static void send(ServerPlayer player, Identifier id, Consumer<RegistryFriendlyByteBuf> writer) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.level().registryAccess());
		try {
			writer.accept(buf);
			byte[] bytes = new byte[buf.readableBytes()];
			buf.readBytes(bytes);
			player.connection.send(new ClientboundCustomPayloadPacket(new DiscardedPayload(id, bytes)));
		} finally {
			buf.release();
		}
	}
}

package com.maxxlive.jadepaperbridge;

import net.minecraft.resources.Identifier;

/**
 * Identifiers copied from {@code snownee.jade.api.JadeIds}. They are part of the protocol and must not change.
 */
public final class JadeIds {
	public static final Identifier PACKET_RECEIVE_DATA = jade("receive_data");
	public static final Identifier PACKET_SERVER_HANDSHAKE = jade("server_handshake");
	public static final Identifier PACKET_CLIENT_HANDSHAKE = jade("client_handshake");
	public static final Identifier PACKET_REQUEST_ENTITY = jade("request_entity");
	public static final Identifier PACKET_REQUEST_BLOCK = jade("request_block");
	public static final Identifier PACKET_SHOW_OVERLAY = jade("show_overlay");

	public static final Identifier CORE_OBJECT_NAME = jade("object_name");

	public static final Identifier UNIVERSAL_ITEM_STORAGE = mc("item_storage");
	public static final Identifier UNIVERSAL_ITEM_STORAGE_DEFAULT = mc("item_storage.default");
	public static final Identifier UNIVERSAL_FLUID_STORAGE = mc("fluid_storage");
	public static final Identifier UNIVERSAL_FLUID_STORAGE_DEFAULT = mc("fluid_storage.default");

	public static final Identifier MC_ANIMAL_OWNER = mc("animal_owner");
	public static final Identifier MC_BEEHIVE = mc("beehive");
	public static final Identifier MC_BREWING_STAND = mc("brewing_stand");
	public static final Identifier MC_CAMPFIRE = mc("campfire");
	public static final Identifier MC_COMMAND_BLOCK = mc("command_block");
	public static final Identifier MC_ENTITY_HEALTH = mc("entity_health");
	public static final Identifier MC_FURNACE = mc("furnace");
	public static final Identifier MC_HOPPER_LOCK = mc("hopper_lock");
	public static final Identifier MC_JUKEBOX = mc("jukebox");
	public static final Identifier MC_LECTERN = mc("lectern");
	public static final Identifier MC_MOB_BREEDING = mc("mob_breeding");
	public static final Identifier MC_MOB_GROWTH = mc("mob_growth");
	public static final Identifier MC_MOB_SPAWNER_COOLDOWN = mc("mob_spawner.cooldown");
	public static final Identifier MC_NEXT_ENTITY_DROP = mc("next_entity_drop");
	public static final Identifier MC_PET_ARMOR = mc("pet_armor");
	public static final Identifier MC_POTION_EFFECTS = mc("potion_effects");
	public static final Identifier MC_REDSTONE = mc("redstone");
	public static final Identifier MC_SHELF = mc("shelf");
	public static final Identifier MC_VILLAGER_RESTOCK = mc("villager_restock");
	public static final Identifier MC_WAXED = mc("waxed");
	public static final Identifier MC_ZOMBIE_VILLAGER = mc("zombie_villager");

	private JadeIds() {
	}

	private static Identifier jade(String path) {
		return Identifier.fromNamespaceAndPath("jade", path);
	}

	private static Identifier mc(String path) {
		return Identifier.withDefaultNamespace(path);
	}
}

package com.maxxlive.jadepaperbridge.provider.entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.jspecify.annotations.Nullable;

import com.maxxlive.jadepaperbridge.JadeIds;
import com.maxxlive.jadepaperbridge.accessor.EntityAccessor;
import com.maxxlive.jadepaperbridge.provider.ServerDataProvider;
import com.maxxlive.jadepaperbridge.provider.StreamServerDataProvider;
import com.maxxlive.jadepaperbridge.util.Reflect;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamEncoder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * Server halves of the entity data providers in {@code snownee.jade.addon.vanilla}.
 */
public final class EntityProviders {
	private EntityProviders() {
	}

	public static final class AnimalOwner implements StreamServerDataProvider<EntityAccessor, Component> {
		public static final AnimalOwner INSTANCE = new AnimalOwner();

		@Override
		public @Nullable Component streamData(EntityAccessor accessor) {
			if (!(accessor.entity() instanceof OwnableEntity ownable)) {
				return null;
			}
			EntityReference<LivingEntity> reference = ownable.getOwnerReference();
			if (reference == null) {
				return null;
			}
			UUID uuid = reference.getUUID();
			Entity owner = accessor.level().getEntity(uuid);
			if (owner != null) {
				return entityName(owner);
			}
			// only reads the server's user cache, never blocks on a profile lookup
			String name = Bukkit.getOfflinePlayer(uuid).getName();
			return name == null ? null : Component.literal(name);
		}

		private static Component entityName(Entity entity) {
			Component customName = entity.getCustomName();
			if (customName != null) {
				return customName;
			}
			if (entity instanceof Player) {
				return entity.getDisplayName();
			}
			return entity.getName();
		}

		@Override
		public StreamEncoder<? super RegistryFriendlyByteBuf, Component> streamCodec() {
			return ComponentSerialization.STREAM_CODEC;
		}

		@Override
		public Identifier uid() {
			return JadeIds.MC_ANIMAL_OWNER;
		}
	}

	public static final class StatusEffects implements StreamServerDataProvider<EntityAccessor, List<StatusEffects.Effect>> {
		public static final StatusEffects INSTANCE = new StatusEffects();
		private static final StreamCodec<RegistryFriendlyByteBuf, List<Effect>> STREAM_CODEC = ByteBufCodecs.<RegistryFriendlyByteBuf, Effect>list()
				.apply(Effect.STREAM_CODEC);

		/**
		 * The times are used by the client for a fade-in animation. Jade tracks them with a mixin; we send 0,
		 * which makes the client show the effect right away.
		 */
		public record Effect(MobEffectInstance effect, long updateTime, long addTime) {
			public static final StreamCodec<RegistryFriendlyByteBuf, Effect> STREAM_CODEC = StreamCodec.composite(
					MobEffectInstance.STREAM_CODEC,
					Effect::effect,
					ByteBufCodecs.LONG,
					Effect::updateTime,
					ByteBufCodecs.LONG,
					Effect::addTime,
					Effect::new);
		}

		@Override
		public @Nullable List<Effect> streamData(EntityAccessor accessor) {
			if (!(accessor.entity() instanceof LivingEntity living)) {
				return null;
			}
			List<Effect> effects = living.getActiveEffects()
					.stream()
					.filter(MobEffectInstance::isVisible)
					.map($ -> new Effect($, 0, 0))
					.toList();
			return effects.isEmpty() ? null : effects;
		}

		@Override
		public StreamEncoder<? super RegistryFriendlyByteBuf, List<Effect>> streamCodec() {
			return STREAM_CODEC;
		}

		@Override
		public Identifier uid() {
			return JadeIds.MC_POTION_EFFECTS;
		}
	}

	public static final class MobGrowth implements StreamServerDataProvider<EntityAccessor, Integer> {
		public static final MobGrowth INSTANCE = new MobGrowth();
		private static final Method TADPOLE_TICKS_LEFT = Reflect.method(Tadpole.class, "getTicksLeftUntilAdult");

		@Override
		public @Nullable Integer streamData(EntityAccessor accessor) {
			int time = -1;
			Entity entity = accessor.entity();
			if (entity instanceof AgeableMob ageable) {
				time = -ageable.getAge();
			} else if (entity instanceof Tadpole tadpole) {
				time = Reflect.<Integer>invoke(TADPOLE_TICKS_LEFT, tadpole);
			}
			return time > 0 ? time : null;
		}

		@Override
		public StreamEncoder<? super RegistryFriendlyByteBuf, Integer> streamCodec() {
			return ByteBufCodecs.VAR_INT;
		}

		@Override
		public Identifier uid() {
			return JadeIds.MC_MOB_GROWTH;
		}
	}

	public static final class MobBreeding implements StreamServerDataProvider<EntityAccessor, Integer> {
		public static final MobBreeding INSTANCE = new MobBreeding();
		private static final int IN_LOVE = -1;

		@Override
		public @Nullable Integer streamData(EntityAccessor accessor) {
			int time = 0;
			Entity entity = accessor.entity();
			if (entity instanceof Allay allay) {
				if (allay.duplicationCooldown > 0 && allay.duplicationCooldown < Integer.MAX_VALUE) {
					time = (int) allay.duplicationCooldown;
				}
			} else if (entity instanceof Villager villager) {
				time = villager.getAge();
			} else if (entity instanceof Animal animal) {
				if (animal.isInLove()) {
					return IN_LOVE;
				}
				time = animal.getAge();
			}
			return time > 0 ? time : null;
		}

		@Override
		public StreamEncoder<? super RegistryFriendlyByteBuf, Integer> streamCodec() {
			return ByteBufCodecs.VAR_INT;
		}

		@Override
		public Identifier uid() {
			return JadeIds.MC_MOB_BREEDING;
		}
	}

	public static final class VillagerRestock implements StreamServerDataProvider<EntityAccessor, VillagerRestock.Data> {
		public static final VillagerRestock INSTANCE = new VillagerRestock();
		private static final int MAX_RESTOCKS_PER_DAY = 2;
		private static final long RESTOCK_COOLDOWN = 2400L;
		private static final long NEW_DAY_INTERVAL = 12000L;
		private static final Field LAST_RESTOCK_GAME_TIME = Reflect.field(Villager.class, "lastRestockGameTime");

		public record Data(int restocksToday, int ticksUntilRestock) {
			public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
					ByteBufCodecs.VAR_INT,
					Data::restocksToday,
					ByteBufCodecs.VAR_INT,
					Data::ticksUntilRestock,
					Data::new);
		}

		@Override
		public @Nullable Data streamData(EntityAccessor accessor) {
			if (!(accessor.entity() instanceof Villager villager)) {
				return null;
			}
			boolean needsRestock = false;
			for (MerchantOffer offer : villager.getOffers()) {
				if (offer.getUses() > 0) {
					needsRestock = true;
					break;
				}
			}
			if (!needsRestock) {
				return null;
			}
			int restocksToday = villager.numberOfRestocksToday;
			long lastRestockGameTime = Reflect.<Long>get(LAST_RESTOCK_GAME_TIME, villager);
			long interval = restocksToday < MAX_RESTOCKS_PER_DAY ? RESTOCK_COOLDOWN : NEW_DAY_INTERVAL;
			long remaining = restocksToday == 0 ? 0L : Math.max(0L, lastRestockGameTime + interval - villager.level().getGameTime());
			return new Data(restocksToday, (int) remaining);
		}

		@Override
		public StreamEncoder<? super RegistryFriendlyByteBuf, Data> streamCodec() {
			return Data.STREAM_CODEC;
		}

		@Override
		public Identifier uid() {
			return JadeIds.MC_VILLAGER_RESTOCK;
		}
	}

	public static final class NextEntityDrop implements ServerDataProvider<EntityAccessor> {
		public static final NextEntityDrop INSTANCE = new NextEntityDrop();
		private static final Field ARMADILLO_SCUTE_TIME = Reflect.field(Armadillo.class, "scuteTime");

		@Override
		public void appendServerData(CompoundTag tag, EntityAccessor accessor) {
			int max = 24000 * 2;
			if (accessor.entity() instanceof Chicken chicken) {
				if (!chicken.isBaby() && chicken.eggTime < max) {
					tag.putInt("NextEggIn", chicken.eggTime);
				}
			} else if (accessor.entity() instanceof Armadillo armadillo) {
				int scuteTime = Reflect.<Integer>get(ARMADILLO_SCUTE_TIME, armadillo);
				if (!armadillo.isBaby() && scuteTime < max) {
					tag.putInt("NextScuteIn", scuteTime);
				}
			} else if (accessor.entity() instanceof Sniffer sniffer) {
				long time = sniffer.getBrain().getTimeUntilExpiry(MemoryModuleType.SNIFF_COOLDOWN);
				if (time > 0 && time < max) {
					tag.putInt("NextSniffIn", (int) time);
				}
			}
		}

		@Override
		public Identifier uid() {
			return JadeIds.MC_NEXT_ENTITY_DROP;
		}
	}

	public static final class ZombieVillagerConversion implements StreamServerDataProvider<EntityAccessor, Integer> {
		public static final ZombieVillagerConversion INSTANCE = new ZombieVillagerConversion();

		@Override
		public @Nullable Integer streamData(EntityAccessor accessor) {
			if (!(accessor.entity() instanceof ZombieVillager zombieVillager)) {
				return null;
			}
			int time = zombieVillager.villagerConversionTime;
			return time > 0 ? time : null;
		}

		@Override
		public StreamEncoder<? super RegistryFriendlyByteBuf, Integer> streamCodec() {
			return ByteBufCodecs.VAR_INT;
		}

		@Override
		public Identifier uid() {
			return JadeIds.MC_ZOMBIE_VILLAGER;
		}
	}

	public static final class PetArmor implements StreamServerDataProvider<EntityAccessor, ItemStack> {
		public static final PetArmor INSTANCE = new PetArmor();

		@Override
		public @Nullable ItemStack streamData(EntityAccessor accessor) {
			if (!(accessor.entity() instanceof Mob mob)) {
				return null;
			}
			ItemStack armor = mob.getBodyArmorItem();
			return armor.isEmpty() ? null : armor;
		}

		@Override
		public StreamEncoder<? super RegistryFriendlyByteBuf, ItemStack> streamCodec() {
			return ItemStack.OPTIONAL_STREAM_CODEC;
		}

		@Override
		public Identifier uid() {
			return JadeIds.MC_PET_ARMOR;
		}
	}

	public static final class Waxed implements StreamServerDataProvider<EntityAccessor, Unit> {
		public static final Waxed INSTANCE = new Waxed();

		@Override
		public @Nullable Unit streamData(EntityAccessor accessor) {
			if (!(accessor.entity() instanceof CopperGolem golem)) {
				return null;
			}
			return golem.nextWeatheringTick == CopperGolem.IGNORE_WEATHERING_TICK ? Unit.INSTANCE : null;
		}

		@Override
		public StreamEncoder<? super RegistryFriendlyByteBuf, Unit> streamCodec() {
			return Unit.STREAM_CODEC;
		}

		@Override
		public Identifier uid() {
			return JadeIds.MC_WAXED;
		}
	}

	public static final class Health implements StreamServerDataProvider<EntityAccessor, Float> {
		public static final Health INSTANCE = new Health();

		@Override
		public @Nullable Float streamData(EntityAccessor accessor) {
			if (!(accessor.entity() instanceof LivingEntity living)) {
				return null;
			}
			float absorption = living.getAbsorptionAmount();
			return absorption > 0 ? absorption : 0;
		}

		@Override
		public StreamEncoder<? super RegistryFriendlyByteBuf, Float> streamCodec() {
			return ByteBufCodecs.FLOAT;
		}

		@Override
		public Identifier uid() {
			return JadeIds.MC_ENTITY_HEALTH;
		}
	}
}

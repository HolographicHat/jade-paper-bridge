package com.maxxlive.jadepaperbridge.provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.maxxlive.jadepaperbridge.accessor.Accessor;

import net.minecraft.resources.Identifier;

/**
 * Ordered set of server data providers. The order defines the network ids: the client receives
 * {@link #ids()} in the handshake and refers to providers by their index in that list.
 */
public final class ProviderRegistry<A extends Accessor> {
	private record Registration<A extends Accessor>(ServerDataProvider<A> provider, Class<?> target) {
	}

	private final List<Registration<A>> registrations = new ArrayList<>();
	private final List<ServerDataProvider<A>> providers = new ArrayList<>();

	public void register(ServerDataProvider<A> provider, Class<?> target) {
		registrations.add(new Registration<>(provider, target));
		if (!providers.contains(provider)) {
			providers.add(provider);
		}
	}

	public void clear() {
		registrations.clear();
		providers.clear();
	}

	public void removeIf(Collection<Identifier> disabled) {
		registrations.removeIf($ -> disabled.contains($.provider.uid()));
		providers.removeIf($ -> disabled.contains($.uid()));
	}

	public List<Identifier> ids() {
		return providers.stream().map(ServerDataProvider::uid).toList();
	}

	public @Nullable ServerDataProvider<A> byId(int id) {
		return id >= 0 && id < providers.size() ? providers.get(id) : null;
	}

	/**
	 * Providers that are registered for one of the given objects (e.g. the block and its block entity),
	 * in registration order.
	 */
	public List<ServerDataProvider<A>> providersFor(@Nullable Object... objects) {
		List<ServerDataProvider<A>> result = new ArrayList<>();
		for (Registration<A> registration : registrations) {
			if (result.contains(registration.provider)) {
				continue;
			}
			for (Object object : objects) {
				if (registration.target.isInstance(object)) {
					result.add(registration.provider);
					break;
				}
			}
		}
		return result;
	}
}

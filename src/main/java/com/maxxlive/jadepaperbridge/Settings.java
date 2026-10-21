package com.maxxlive.jadepaperbridge;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.configuration.file.FileConfiguration;

import net.minecraft.resources.Identifier;

/**
 * @param maxPositionDeviation counterpart of Jade's {@code jade:max_position_deviation} game rule
 * @param disabledProviders    provider uids that are not offered to clients
 * @param configOverrides      Jade plugin options forced on all clients, like Jade's server-plugin-overrides.json
 */
public record Settings(
		int maxPositionDeviation,
		Set<Identifier> disabledProviders,
		Map<Identifier, Object> configOverrides,
		boolean debug) {

	public static Settings load(FileConfiguration config, Logger logger) {
		Set<Identifier> disabled = new HashSet<>();
		for (String id : config.getStringList("disabled-providers")) {
			Identifier identifier = Identifier.tryParse(id.trim());
			if (identifier == null) {
				logger.warning("Invalid provider id in disabled-providers: " + id);
			} else {
				disabled.add(identifier);
			}
		}

		Map<Identifier, Object> overrides = new LinkedHashMap<>();
		for (String line : config.getStringList("config-overrides")) {
			int index = line.indexOf('=');
			Identifier identifier = index < 0 ? null : Identifier.tryParse(line.substring(0, index).trim());
			if (identifier == null) {
				logger.warning("Invalid entry in config-overrides (expected \"namespace:option=value\"): " + line);
				continue;
			}
			overrides.put(identifier, parseValue(line.substring(index + 1).trim()));
		}

		return new Settings(
				Math.max(0, config.getInt("max-position-deviation", 21)),
				Set.copyOf(disabled),
				Map.copyOf(overrides),
				config.getBoolean("debug", false));
	}

	private static Object parseValue(String value) {
		if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
			return Boolean.parseBoolean(value);
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ignored) {
		}
		try {
			return Float.parseFloat(value);
		} catch (NumberFormatException ignored) {
		}
		return value;
	}
}

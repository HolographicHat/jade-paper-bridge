package com.maxxlive.jadepaperbridge;

import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/**
 * /jadepaperbridge reload | status | overlay &lt;player&gt; &lt;show|hide&gt;
 */
public final class JadePaperBridgeCommand implements TabExecutor {
	private final JadePaperBridge plugin;

	public JadePaperBridgeCommand(JadePaperBridge plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0) {
			return false;
		}
		switch (args[0].toLowerCase()) {
			case "reload" -> {
				plugin.reload();
				sender.sendMessage("JadePaperBridge reloaded.");
			}
			case "status" -> {
				List<String> names = plugin.networkHandler().connectedPlayers().stream()
						.map(Bukkit::getPlayer)
						.filter(Objects::nonNull)
						.map(Player::getName)
						.sorted()
						.toList();
				sender.sendMessage("Jade clients online (%d): %s".formatted(names.size(), String.join(", ", names)));
			}
			case "overlay" -> {
				if (args.length < 3 || !(args[2].equalsIgnoreCase("show") || args[2].equalsIgnoreCase("hide"))) {
					sender.sendMessage("Usage: /" + label + " overlay <player> <show|hide>");
					return true;
				}
				Player target = Bukkit.getPlayerExact(args[1]);
				if (target == null || !plugin.networkHandler().connectedPlayers().contains(target.getUniqueId())) {
					sender.sendMessage(args[1] + " is not online with Jade.");
					return true;
				}
				plugin.networkHandler().sendShowOverlay(((CraftPlayer) target).getHandle(), args[2].equalsIgnoreCase("show"));
				sender.sendMessage("Sent overlay " + args[2].toLowerCase() + " to " + target.getName() + ".");
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		return switch (args.length) {
			case 1 -> filter(List.of("reload", "status", "overlay"), args[0]);
			case 2 -> args[0].equalsIgnoreCase("overlay") ?
					filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]) :
					List.of();
			case 3 -> args[0].equalsIgnoreCase("overlay") ? filter(List.of("show", "hide"), args[2]) : List.of();
			default -> List.of();
		};
	}

	private static List<String> filter(List<String> options, String prefix) {
		return options.stream().filter($ -> $.regionMatches(true, 0, prefix, 0, prefix.length())).toList();
	}
}

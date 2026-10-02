package net.tfminecraft.permcleaner.command;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;

import net.tfminecraft.permcleaner.PermCleaner;
import net.tfminecraft.permcleaner.lp.CleanResult;
import net.tfminecraft.permcleaner.lp.CleanResult.NodeReport;
import net.tfminecraft.permcleaner.lp.SeasonCleanService;

public final class PermCleanerCommand implements CommandExecutor, TabCompleter {

	private static final String USAGE = "/permcleaner <reload|status|inspect|clean|force> [player]";
	private static final int INSPECT_CAP = 40;
	private static final List<String> SUBS = List.of("reload", "status", "inspect", "clean", "force");

	private final PermCleaner plugin;

	public PermCleanerCommand(PermCleaner plugin) {
		this.plugin = plugin;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0) {
			sender.sendMessage(ChatColor.RED + USAGE);
			return true;
		}
		String sub = args[0].toLowerCase(Locale.ROOT);
		switch (sub) {
			case "reload" -> reload(sender);
			case "status" -> resolveTarget(sender, args, target -> status(sender, target));
			case "inspect" -> resolveTarget(sender, args, target -> inspect(sender, target));
			case "clean" -> resolveTarget(sender, args, target -> clean(sender, target, false));
			case "force" -> resolveTarget(sender, args, target -> clean(sender, target, true));
			default -> sender.sendMessage(ChatColor.RED + USAGE);
		}
		return true;
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private void reload(CommandSender sender) {
		plugin.reloadConfig();
		plugin.reloadKeepList();
		sender.sendMessage(ChatColor.GREEN + "PermCleaner reloaded season "
				+ ChatColor.WHITE + plugin.seasonId()
				+ ChatColor.GREEN + " world " + ChatColor.WHITE + plugin.displayWorld());
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private void status(CommandSender sender, Target target) {
		String stored = plugin.stamps() == null
				? null
				: plugin.stamps().getSeason(target.uuid).orElse(null);
		boolean needs = plugin.stamps() != null && plugin.stamps().needsClean(target.uuid, plugin.seasonId());
		Player online = Bukkit.getPlayer(target.uuid);
		boolean worldOk = online == null || plugin.cleaner().worldMatches(online.getWorld());
		sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + target.name);
		sender.sendMessage(ChatColor.GREEN + "Config season " + ChatColor.WHITE + plugin.seasonId());
		sender.sendMessage(ChatColor.GREEN + "Stored season " + ChatColor.WHITE
				+ (stored == null ? "(none)" : stored));
		sender.sendMessage(ChatColor.GREEN + "Needs clean " + ChatColor.WHITE + (needs ? "yes" : "no"));
		sender.sendMessage(ChatColor.GREEN + "World " + ChatColor.WHITE + plugin.displayWorld()
				+ ChatColor.GREEN + " gate " + ChatColor.WHITE + (online == null ? "offline" : (worldOk ? "pass" : "fail")));
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private void inspect(CommandSender sender, Target target) {
		sender.sendMessage(ChatColor.GREEN + "Inspecting " + ChatColor.WHITE + target.name + ChatColor.GREEN + "...");
		plugin.cleaner().inspect(target.uuid, result -> {
			if (!sender.equals(Bukkit.getConsoleSender()) && sender instanceof Player player && !player.isOnline()) {
				return;
			}
			if (result == null) {
				sender.sendMessage(ChatColor.RED + "Inspect failed for " + target.name);
				return;
			}
			sender.sendMessage(ChatColor.GREEN + "Inspect " + ChatColor.WHITE + target.name
					+ ChatColor.GREEN + " remove " + ChatColor.WHITE + result.removed()
					+ ChatColor.GREEN + " keep " + ChatColor.WHITE + result.kept());
			List<NodeReport> nodes = result.nodes();
			int shown = Math.min(INSPECT_CAP, nodes.size());
			for (int i = 0; i < shown; i++) {
				NodeReport node = nodes.get(i);
				ChatColor color = node.remove() ? ChatColor.RED : ChatColor.GREEN;
				String label = node.remove() ? "remove" : "keep";
				sender.sendMessage(color + label + " " + ChatColor.WHITE + node.key()
						+ ChatColor.GRAY + " " + node.context());
			}
			if (nodes.size() > shown) {
				sender.sendMessage(ChatColor.GRAY + "... and " + (nodes.size() - shown) + " more");
			}
		});
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private void clean(CommandSender sender, Target target, boolean force) {
		if (plugin.seasonId().isBlank()) {
			sender.sendMessage(ChatColor.RED + "Cleaning is disabled: season-id is blank.");
			return;
		}
		if (!force && plugin.stamps() != null && !plugin.stamps().needsClean(target.uuid, plugin.seasonId())) {
			sender.sendMessage(ChatColor.YELLOW + target.name + " already stamped for " + plugin.seasonId());
			return;
		}
		boolean started = plugin.cleaner().startClean(target.uuid, target.name, result -> {
			if (!sender.equals(Bukkit.getConsoleSender()) && sender instanceof Player player && !player.isOnline()) {
				return;
			}
			if (result == null) {
				sender.sendMessage(ChatColor.RED + "Clean failed for " + target.name);
				return;
			}
			sender.sendMessage(ChatColor.GREEN + (force ? "Forced " : "Cleaned ")
					+ ChatColor.WHITE + target.name
					+ ChatColor.GREEN + " removed " + ChatColor.WHITE + result.removed());
		});
		if (!started) {
			sender.sendMessage(ChatColor.YELLOW + "Clean already running for " + target.name);
		}
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private void resolveTarget(CommandSender sender, String[] args, Consumer<Target> resolved) {
		if (args.length < 2) {
			if (sender instanceof Player player) {
				resolved.accept(new Target(player.getUniqueId(), player.getName()));
			} else {
				sender.sendMessage(ChatColor.RED + "Console must specify a player.");
			}
			return;
		}
		String name = args[1];
		Player online = Bukkit.getPlayerExact(name);
		if (online != null) {
			resolved.accept(new Target(online.getUniqueId(), online.getName()));
			return;
		}
		OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
		if (cached != null) {
			resolveOffline(sender, name, cached, resolved);
			return;
		}
		if (!Bukkit.getOnlineMode()) {
			UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
			resolveOffline(sender, name, Bukkit.getOfflinePlayer(uuid), resolved);
			return;
		}
		try {
			Bukkit.createProfile(name).update().whenComplete((profile, failure) -> {
				if (!plugin.isEnabled()) return;
				try {
					Bukkit.getScheduler().runTask(plugin, () -> {
						if (!plugin.isEnabled() || (sender instanceof Player player && !player.isOnline())) return;
						if (failure != null) {
							sender.sendMessage(ChatColor.RED + "Player lookup failed for " + name + ". Please try again.");
						} else if (profile.getUniqueId() == null) {
							sender.sendMessage(ChatColor.RED + "Unknown player " + name);
						} else {
							resolveOffline(sender, name, Bukkit.getOfflinePlayer(profile.getUniqueId()), resolved);
						}
					});
				} catch (IllegalPluginAccessException ignored) {
					// The plugin was disabled between completing the lookup and scheduling the result.
				}
			});
		} catch (IllegalArgumentException invalidName) {
			sender.sendMessage(ChatColor.RED + "Unknown player " + name);
		}
	}

	@SuppressWarnings("deprecation")
	private void resolveOffline(CommandSender sender, String name, OfflinePlayer offline, Consumer<Target> resolved) {
		if (offline.hasPlayedBefore() && offline.getUniqueId() != null) {
			resolved.accept(new Target(offline.getUniqueId(), offline.getName() != null ? offline.getName() : name));
		} else {
			sender.sendMessage(ChatColor.RED + "Unknown player " + name);
		}
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			return filter(SUBS, args[0]);
		}
		if (args.length == 2) {
			String sub = args[0].toLowerCase(Locale.ROOT);
			if (sub.equals("status") || sub.equals("inspect") || sub.equals("clean") || sub.equals("force")) {
				return filter(onlineNames(), args[1]);
			}
		}
		return List.of();
	}

	private static List<String> onlineNames() {
		return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
	}

	private static List<String> filter(List<String> options, String prefix) {
		String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
		List<String> out = new ArrayList<>();
		for (String option : options) {
			if (option.toLowerCase(Locale.ROOT).startsWith(p)) {
				out.add(option);
			}
		}
		return out;
	}

	private static final class Target {
		private final UUID uuid;
		private final String name;

		private Target(UUID uuid, String name) {
			this.uuid = uuid;
			this.name = name;
		}
	}
}

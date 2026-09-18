package net.tfminecraft.permcleaner.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

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

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0) {
			sender.sendMessage(ChatColor.RED + USAGE);
			return true;
		}
		String sub = args[0].toLowerCase(Locale.ROOT);
		switch (sub) {
			case "reload" -> reload(sender);
			case "status" -> status(sender, args);
			case "inspect" -> inspect(sender, args);
			case "clean" -> clean(sender, args, false);
			case "force" -> clean(sender, args, true);
			default -> sender.sendMessage(ChatColor.RED + USAGE);
		}
		return true;
	}

	private void reload(CommandSender sender) {
		plugin.reloadConfig();
		plugin.reloadKeepList();
		sender.sendMessage(ChatColor.GREEN + "PermCleaner reloaded season "
				+ ChatColor.WHITE + plugin.seasonId()
				+ ChatColor.GREEN + " world " + ChatColor.WHITE + plugin.displayWorld());
	}

	private void status(CommandSender sender, String[] args) {
		Target target = resolveTarget(sender, args);
		if (target == null) {
			return;
		}
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

	private void inspect(CommandSender sender, String[] args) {
		Target target = resolveTarget(sender, args);
		if (target == null) {
			return;
		}
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

	private void clean(CommandSender sender, String[] args, boolean force) {
		Target target = resolveTarget(sender, args);
		if (target == null) {
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

	private Target resolveTarget(CommandSender sender, String[] args) {
		if (args.length < 2) {
			if (sender instanceof Player player) {
				return new Target(player.getUniqueId(), player.getName());
			}
			sender.sendMessage(ChatColor.RED + "Console must specify a player.");
			return null;
		}
		Player online = Bukkit.getPlayerExact(args[1]);
		if (online != null) {
			return new Target(online.getUniqueId(), online.getName());
		}
		@SuppressWarnings("deprecation")
		OfflinePlayer offline = Bukkit.getOfflinePlayer(args[1]);
		if (offline.hasPlayedBefore() && offline.getUniqueId() != null) {
			String name = offline.getName() != null ? offline.getName() : args[1];
			return new Target(offline.getUniqueId(), name);
		}
		sender.sendMessage(ChatColor.RED + "Unknown player " + args[1]);
		return null;
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

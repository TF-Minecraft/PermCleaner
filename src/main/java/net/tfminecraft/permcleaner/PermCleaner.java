package net.tfminecraft.permcleaner;

import java.io.File;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.permcleaner.command.PermCleanerCommand;
import net.tfminecraft.permcleaner.keep.KeepList;
import net.tfminecraft.permcleaner.listener.SeasonCleanListener;
import net.tfminecraft.permcleaner.lp.SeasonCleanService;
import net.tfminecraft.permcleaner.store.SeasonStampStore;

public class PermCleaner extends JavaPlugin {

	public static PermCleaner plugin;

	private SeasonStampStore stamps;
	private KeepList keepList;
	private SeasonCleanService cleaner;

	@Override
	public void onEnable() {
		plugin = this;
		saveDefaultConfig();
		reloadKeepList();
		if (!requireLuckPerms()) {
			return;
		}
		if (!openStamps()) {
			return;
		}
		cleaner = new SeasonCleanService(this);
		getServer().getPluginManager().registerEvents(new SeasonCleanListener(cleaner), this);
		PermCleanerCommand command = new PermCleanerCommand(this);
		getCommand("permcleaner").setExecutor(command);
		getCommand("permcleaner").setTabCompleter(command);
		getLogger().info("season-id=" + seasonId() + " world=" + displayWorld());
	}

	@Override
	public void onDisable() {
		if (stamps != null) {
			stamps.close();
			stamps = null;
		}
		cleaner = null;
		plugin = null;
	}

	public static PermCleaner getInstance() {
		return plugin;
	}

	public String seasonId() {
		String value = getConfig().getString("season-id", "vardera");
		return value == null ? "" : value.trim();
	}

	public String worldName() {
		String value = getConfig().getString("world", "");
		return value == null ? "" : value.trim();
	}

	public String displayWorld() {
		String world = worldName();
		return world.isBlank() ? "(any)" : world;
	}

	public SeasonStampStore stamps() {
		return stamps;
	}

	public KeepList keepList() {
		return keepList;
	}

	public SeasonCleanService cleaner() {
		return cleaner;
	}

	public void reloadKeepList() {
		keepList = KeepList.fromPatterns(getConfig().getStringList("keep"));
		if (keepList.isEmpty()) {
			getLogger().warning("keep list is empty; every user permission node will be removed on clean");
		}
	}

	private boolean requireLuckPerms() {
		Plugin luckPerms = getServer().getPluginManager().getPlugin("LuckPerms");
		if (luckPerms != null && luckPerms.isEnabled()) {
			return true;
		}
		getLogger().severe("LuckPerms is missing or disabled");
		getServer().getPluginManager().disablePlugin(this);
		return false;
	}

	private boolean openStamps() {
		File dbFile = new File(getDataFolder(), "stamps.db");
		try {
			stamps = new SeasonStampStore(dbFile);
			return true;
		} catch (RuntimeException e) {
			getLogger().severe("Failed to open stamps.db: " + e.getMessage());
			getServer().getPluginManager().disablePlugin(this);
			return false;
		}
	}
}

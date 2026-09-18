package net.tfminecraft.permcleaner.lp;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.tfminecraft.permcleaner.PermCleaner;

public final class SeasonCleanService {

	private final PermCleaner plugin;
	private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

	public SeasonCleanService(PermCleaner plugin) {
		this.plugin = plugin;
	}

	public void consider(Player player, World world) {
		if (player == null || !plugin.isEnabled()) {
			return;
		}
		if (!worldMatches(world)) {
			return;
		}
		UUID uuid = player.getUniqueId();
		if (plugin.stamps() == null || !plugin.stamps().needsClean(uuid, plugin.seasonId())) {
			return;
		}
		startClean(uuid, player.getName(), null);
	}

	public boolean worldMatches(World world) {
		String required = plugin.worldName();
		if (required == null || required.isBlank()) {
			return true;
		}
		if (world == null) {
			return false;
		}
		return world.getName().equalsIgnoreCase(required);
	}

	/**
	 * @return false if a clean is already running for this uuid
	 */
	public boolean startClean(UUID uuid, String name, Consumer<CleanResult> onMain) {
		if (uuid == null || !plugin.isEnabled()) {
			return false;
		}
		if (!inFlight.add(uuid)) {
			return false;
		}
		String seasonId = plugin.seasonId();
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> cleanAsync(uuid, name, seasonId, onMain));
		return true;
	}

	public void inspect(UUID uuid, Consumer<CleanResult> onMain) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				LuckPerms api = LuckPermsProvider.get();
				User user = api.getUserManager().loadUser(uuid).join();
				if (user == null) {
					throw new IllegalStateException("loadUser returned null for " + uuid);
				}
				CleanResult result = UserPermissionCleaner.inspect(user, plugin.keepList());
				runMain(() -> {
					if (onMain != null) {
						onMain.accept(result);
					}
				});
			} catch (Exception e) {
				plugin.getLogger().log(Level.WARNING, "Failed to inspect permissions for " + uuid, e);
				runMain(() -> {
					if (onMain != null) {
						onMain.accept(null);
					}
				});
			}
		});
	}

	private void cleanAsync(UUID uuid, String name, String seasonId, Consumer<CleanResult> onMain) {
		try {
			LuckPerms api = LuckPermsProvider.get();
			User user = api.getUserManager().loadUser(uuid).join();
			if (user == null) {
				throw new IllegalStateException("loadUser returned null for " + uuid);
			}
			CleanResult result = UserPermissionCleaner.apply(user, plugin.keepList());
			if (result.changed()) {
				api.getUserManager().saveUser(user).join();
			}
			if (!plugin.isEnabled()) {
				inFlight.remove(uuid);
				runMain(() -> {
					if (onMain != null) {
						onMain.accept(null);
					}
				});
				return;
			}
			Bukkit.getScheduler().runTask(plugin, () -> stampAndFinish(uuid, name, seasonId, result, onMain));
		} catch (Exception e) {
			plugin.getLogger().log(Level.WARNING, "Failed to clean permissions for " + name, e);
			inFlight.remove(uuid);
			runMain(() -> {
				if (onMain != null) {
					onMain.accept(null);
				}
			});
		}
	}

	private void stampAndFinish(UUID uuid, String name, String seasonId, CleanResult result,
			Consumer<CleanResult> onMain) {
		try {
			if (plugin.isEnabled() && plugin.stamps() != null) {
				plugin.stamps().setSeason(uuid, seasonId);
				plugin.getLogger().info(name + " season " + seasonId + " removed " + result.removed());
			}
			if (onMain != null) {
				onMain.accept(result);
			}
		} catch (Exception e) {
			plugin.getLogger().log(Level.WARNING, "Failed to stamp season for " + name, e);
			if (onMain != null) {
				onMain.accept(null);
			}
		} finally {
			inFlight.remove(uuid);
		}
	}

	private void runMain(Runnable task) {
		if (!plugin.isEnabled()) {
			return;
		}
		if (Bukkit.isPrimaryThread()) {
			task.run();
			return;
		}
		Bukkit.getScheduler().runTask(plugin, task);
	}
}

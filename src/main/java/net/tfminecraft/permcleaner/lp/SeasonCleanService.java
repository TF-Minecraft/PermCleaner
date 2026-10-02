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
		if (player == null || !plugin.isEnabled() || plugin.seasonId().isBlank()) {
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
	 * @return false if disabled, the season is blank, the uuid is null, or a clean is already running
	 */
	public boolean startClean(UUID uuid, String name, Consumer<CleanResult> onMain) {
		if (uuid == null || !plugin.isEnabled()) {
			return false;
		}
		String seasonId = plugin.seasonId();
		if (seasonId.isBlank() || !inFlight.add(uuid)) {
			return false;
		}
		try {
			Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> cleanAsync(uuid, name, seasonId, onMain));
			return true;
		} catch (RuntimeException | Error failure) {
			inFlight.remove(uuid);
			throw failure;
		}
	}

	public void inspect(UUID uuid, Consumer<CleanResult> onMain) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			CleanResult result;
			try {
				LuckPerms api = LuckPermsProvider.get();
				User user = api.getUserManager().loadUser(uuid).join();
				if (user == null) {
					throw new IllegalStateException("loadUser returned null for " + uuid);
				}
				result = UserPermissionCleaner.inspect(user, plugin.keepList());
			} catch (Exception e) {
				plugin.getLogger().log(Level.WARNING, "Failed to inspect permissions for " + uuid, e);
				result = null;
			}
			CleanResult delivered = result;
			runMain(() -> {
				if (onMain != null) {
					onMain.accept(delivered);
				}
			});
		});
	}

	private void cleanAsync(UUID uuid, String name, String seasonId, Consumer<CleanResult> onMain) {
		CleanResult result;
		try {
			LuckPerms api = LuckPermsProvider.get();
			User user = api.getUserManager().loadUser(uuid).join();
			if (user == null) {
				throw new IllegalStateException("loadUser returned null for " + uuid);
			}
			result = UserPermissionCleaner.apply(user, plugin.keepList());
			// A previous failed save may have left this loaded user already mutated.
			// Persist even a no-op retry before recording the season as complete.
			api.getUserManager().saveUser(user).join();
		} catch (Exception e) {
			plugin.getLogger().log(Level.WARNING, "Failed to clean permissions for " + name, e);
			finishFailed(uuid, onMain);
			return;
		} catch (Error failure) {
			try {
				finishFailed(uuid, onMain);
			} catch (RuntimeException | Error notificationFailure) {
				if (notificationFailure != failure) failure.addSuppressed(notificationFailure);
			}
			throw failure;
		}
		if (!plugin.isEnabled()) {
			finishFailed(uuid, onMain);
			return;
		}
		try {
			Bukkit.getScheduler().runTask(plugin, () -> stampAndFinish(uuid, name, seasonId, result, onMain));
		} catch (RuntimeException | Error failure) {
			inFlight.remove(uuid);
			throw failure;
		}
	}

	private void finishFailed(UUID uuid, Consumer<CleanResult> onMain) {
		inFlight.remove(uuid);
		runMain(() -> {
			if (onMain != null) {
				onMain.accept(null);
			}
		});
	}

	private void stampAndFinish(UUID uuid, String name, String seasonId, CleanResult result,
			Consumer<CleanResult> onMain) {
		try {
			CleanResult delivered = result;
			try {
				if (plugin.isEnabled() && plugin.stamps() != null) {
					plugin.stamps().setSeason(uuid, seasonId);
					plugin.getLogger().info(name + " season " + seasonId + " removed " + result.removed());
				}
			} catch (Exception e) {
				plugin.getLogger().log(Level.WARNING, "Failed to stamp season for " + name, e);
				delivered = null;
			}
			if (onMain != null) {
				onMain.accept(delivered);
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

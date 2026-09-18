package net.tfminecraft.permcleaner.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import net.tfminecraft.permcleaner.lp.SeasonCleanService;

public final class SeasonCleanListener implements Listener {

	private final SeasonCleanService service;

	public SeasonCleanListener(SeasonCleanService service) {
		this.service = service;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		service.consider(event.getPlayer(), event.getPlayer().getWorld());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWorldChange(PlayerChangedWorldEvent event) {
		service.consider(event.getPlayer(), event.getPlayer().getWorld());
	}
}

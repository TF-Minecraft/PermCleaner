package net.tfminecraft.permcleaner.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.tfminecraft.permcleaner.lp.SeasonCleanService;

class SeasonCleanListenerTest {

	private SeasonCleanService service;
	private Player player;
	private World world;
	private SeasonCleanListener listener;

	@BeforeEach
	void setUp() {
		service = mock(SeasonCleanService.class);
		player = mock(Player.class);
		world = mock(World.class);
		when(player.getWorld()).thenReturn(world);
		listener = new SeasonCleanListener(service);
	}

	@Test
	void joinConsidersPlayerInCurrentWorld() {
		PlayerJoinEvent event = mock(PlayerJoinEvent.class);
		when(event.getPlayer()).thenReturn(player);

		listener.onJoin(event);

		verify(service).consider(player, world);
	}

	@Test
	void worldChangeConsidersPlayerInNewWorld() {
		PlayerChangedWorldEvent event = mock(PlayerChangedWorldEvent.class);
		when(event.getPlayer()).thenReturn(player);

		listener.onWorldChange(event);

		verify(service).consider(player, world);
	}
}

package net.tfminecraft.permcleaner.command;

import static org.bukkit.ChatColor.GRAY;
import static org.bukkit.ChatColor.GREEN;
import static org.bukkit.ChatColor.RED;
import static org.bukkit.ChatColor.WHITE;
import static org.bukkit.ChatColor.YELLOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.plugin.IllegalPluginAccessException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import net.tfminecraft.permcleaner.PermCleaner;
import net.tfminecraft.permcleaner.lp.CleanResult;
import net.tfminecraft.permcleaner.lp.CleanResult.NodeReport;
import net.tfminecraft.permcleaner.lp.SeasonCleanService;
import net.tfminecraft.permcleaner.store.SeasonStampStore;

// Messages use the plugin's legacy colour codes.
@SuppressWarnings("deprecation")
class PermCleanerCommandTest {

	private static final String USAGE = RED + "/permcleaner <reload|status|inspect|clean|force> [player]";
	private static final UUID ALEX = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID BEA = UUID.fromString("22222222-2222-2222-2222-222222222222");

	private PermCleaner plugin;
	private SeasonCleanService cleaner;
	private SeasonStampStore stamps;
	private ConsoleCommandSender console;
	private Player alex;
	private Player bea;
	private World world;
	private Command command;
	private MockedStatic<Bukkit> bukkit;
	private PermCleanerCommand executor;
	private BukkitScheduler scheduler;
	private final Deque<Runnable> mainTasks = new ArrayDeque<>();
	private final Deque<Runnable> asyncTasks = new ArrayDeque<>();

	@BeforeEach
	void setUp() {
		plugin = mock(PermCleaner.class);
		cleaner = mock(SeasonCleanService.class);
		stamps = mock(SeasonStampStore.class);
		when(plugin.cleaner()).thenReturn(cleaner);
		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.stamps()).thenReturn(stamps);
		when(plugin.seasonId()).thenReturn("vardera");
		when(plugin.displayWorld()).thenReturn("lobby");
		when(stamps.getSeason(any())).thenReturn(Optional.empty());
		when(stamps.needsClean(any(), eq("vardera"))).thenReturn(true);
		when(cleaner.startClean(any(), anyString(), any())).thenReturn(true);

		console = mock(ConsoleCommandSender.class);
		world = mock(World.class);
		alex = player(ALEX, "Alex");
		bea = player(BEA, "Bea");
		command = mock(Command.class);

		bukkit = Mockito.mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
		bukkit.when(() -> Bukkit.getPlayerExact("Bea")).thenReturn(bea);
		bukkit.when(() -> Bukkit.getPlayer(BEA)).thenReturn(bea);
		bukkit.when(() -> Bukkit.getPlayer(ALEX)).thenReturn(alex);
		bukkit.when(() -> Bukkit.getOfflinePlayerIfCached(anyString())).thenReturn(mock(OfflinePlayer.class));
		bukkit.when(Bukkit::getOnlineMode).thenReturn(true);
		scheduler = mock(BukkitScheduler.class);
		when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
			mainTasks.add(invocation.getArgument(1)); return null;
		});
		bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
		when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
			asyncTasks.add(invocation.getArgument(1)); return null;
		});
		bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(invocation -> List.of(alex, bea));

		executor = new PermCleanerCommand(plugin);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	void noArgumentsShowsUsage() {
		assertTrue(run(console));
		assertEquals(List.of(USAGE), messages(console));
	}

	@Test
	void unknownSubcommandShowsUsage() {
		assertTrue(run(console, "purge"));
		assertEquals(List.of(USAGE), messages(console));
	}

	@Test
	void reloadRefreshesConfigAndKeepList() {
		assertTrue(run(console, "RELOAD"));

		verify(plugin).reloadConfig();
		verify(plugin).reloadKeepList();
		assertEquals(List.of(GREEN + "PermCleaner reloaded season " + WHITE + "vardera"
				+ GREEN + " world " + WHITE + "lobby"), messages(console));
	}

	@Test
	void statusForSelfInMatchingWorld() {
		when(stamps.getSeason(ALEX)).thenReturn(Optional.of("old"));
		when(cleaner.worldMatches(world)).thenReturn(true);

		run(alex, "status");

		assertEquals(List.of(
				GREEN + "Player " + WHITE + "Alex",
				GREEN + "Config season " + WHITE + "vardera",
				GREEN + "Stored season " + WHITE + "old",
				GREEN + "Needs clean " + WHITE + "yes",
				GREEN + "World " + WHITE + "lobby" + GREEN + " gate " + WHITE + "pass"), messages(alex));
	}

	@Test
	void statusForOnlinePlayerInOtherWorld() {
		when(stamps.getSeason(BEA)).thenReturn(Optional.of("vardera"));
		when(stamps.needsClean(BEA, "vardera")).thenReturn(false);
		when(cleaner.worldMatches(world)).thenReturn(false);

		run(console, "status", "Bea");

		assertEquals(List.of(
				GREEN + "Player " + WHITE + "Bea",
				GREEN + "Config season " + WHITE + "vardera",
				GREEN + "Stored season " + WHITE + "vardera",
				GREEN + "Needs clean " + WHITE + "no",
				GREEN + "World " + WHITE + "lobby" + GREEN + " gate " + WHITE + "fail"), messages(console));
	}

	@Test
	void statusForOfflinePlayerWithoutStampStore() {
		offline("Cai", "Cai");
		when(plugin.stamps()).thenReturn(null);

		run(console, "status", "Cai");

		assertEquals(List.of(
				GREEN + "Player " + WHITE + "Cai",
				GREEN + "Config season " + WHITE + "vardera",
				GREEN + "Stored season " + WHITE + "(none)",
				GREEN + "Needs clean " + WHITE + "no",
				GREEN + "World " + WHITE + "lobby" + GREEN + " gate " + WHITE + "offline"), messages(console));
		verify(cleaner, never()).worldMatches(any());
	}

	@Test
	void consoleMustNamePlayer() {
		run(console, "status");
		assertEquals(List.of(RED + "Console must specify a player."), messages(console));
	}

	@Test
	void unknownPlayerIsRejected() {
		run(console, "status", "Nobody");
		assertEquals(List.of(RED + "Unknown player Nobody"), messages(console));
	}

	@Test
	void offlinePlayerWithoutUuidIsRejected() {
		OfflinePlayer offline = mock(OfflinePlayer.class);
		when(offline.hasPlayedBefore()).thenReturn(true);
		bukkit.when(() -> Bukkit.getOfflinePlayerIfCached("Ghost")).thenReturn(offline);

		run(console, "status", "Ghost");

		assertEquals(List.of(RED + "Unknown player Ghost"), messages(console));
	}

	@Test
	void offlinePlayerWithoutNameUsesArgument() {
		UUID uuid = offline("dee", null);

		run(console, "clean", "dee");

		verify(cleaner).startClean(eq(uuid), eq("dee"), any());
	}

	@Test
	void inspectRejectsUnknownTarget() {
		run(console, "inspect");
		verify(cleaner, never()).inspect(any(), any());
	}

	@Test
	void inspectListsNodesUpToCap() {
		List<NodeReport> nodes = new ArrayList<>();
		for (int i = 0; i < 41; i++) {
			nodes.add(new NodeReport("node." + i, "PermissionNode", "{}", i % 2 == 0));
		}

		run(console, "inspect", "Bea");
		inspectCallback(BEA).accept(new CleanResult(21, 20, nodes));

		List<String> messages = messages(console);
		assertEquals(43, messages.size());
		assertEquals(GREEN + "Inspecting " + WHITE + "Bea" + GREEN + "...", messages.get(0));
		assertEquals(GREEN + "Inspect " + WHITE + "Bea" + GREEN + " remove " + WHITE + 21
				+ GREEN + " keep " + WHITE + 20, messages.get(1));
		assertEquals(RED + "remove " + WHITE + "node.0" + GRAY + " {}", messages.get(2));
		assertEquals(GREEN + "keep " + WHITE + "node.1" + GRAY + " {}", messages.get(3));
		assertEquals(RED + "remove " + WHITE + "node.38" + GRAY + " {}", messages.get(40));
		assertEquals(GREEN + "keep " + WHITE + "node.39" + GRAY + " {}", messages.get(41));
		assertEquals(GRAY + "... and 1 more", messages.get(42));
	}

	@Test
	void inspectListsAllNodesUnderCap() {
		run(alex, "inspect");
		inspectCallback(ALEX).accept(new CleanResult(0, 1,
				List.of(new NodeReport("group.default", "InheritanceNode", "{}", false))));

		assertEquals(List.of(
				GREEN + "Inspecting " + WHITE + "Alex" + GREEN + "...",
				GREEN + "Inspect " + WHITE + "Alex" + GREEN + " remove " + WHITE + 0
						+ GREEN + " keep " + WHITE + 1,
				GREEN + "keep " + WHITE + "group.default" + GRAY + " {}"), messages(alex));
	}

	@Test
	void inspectReportsFailure() {
		CommandSender sender = mock(CommandSender.class);
		run(sender, "inspect", "Bea");
		inspectCallback(BEA).accept(null);

		assertEquals(List.of(
				GREEN + "Inspecting " + WHITE + "Bea" + GREEN + "...",
				RED + "Inspect failed for Bea"), messages(sender));
	}

	@Test
	void inspectSkipsPlayersWhoLeft() {
		run(alex, "inspect");
		when(alex.isOnline()).thenReturn(false);
		inspectCallback(ALEX).accept(null);

		assertEquals(List.of(GREEN + "Inspecting " + WHITE + "Alex" + GREEN + "..."), messages(alex));
	}

	@Test
	void cleanSkipsStampedPlayer() {
		when(stamps.needsClean(BEA, "vardera")).thenReturn(false);

		run(console, "clean", "Bea");

		verify(cleaner, never()).startClean(any(), anyString(), any());
		assertEquals(List.of(YELLOW + "Bea already stamped for vardera"), messages(console));
	}

	@Test
	void cleanReportsRemovedCount() {
		run(console, "clean", "Bea");
		cleanCallback(BEA).accept(new CleanResult(3, 1, List.of()));

		assertEquals(List.of(GREEN + "Cleaned " + WHITE + "Bea" + GREEN + " removed " + WHITE + 3),
				messages(console));
	}

	@Test
	void cleanWithoutStampStoreStillStarts() {
		when(plugin.stamps()).thenReturn(null);

		run(alex, "clean");
		cleanCallback(ALEX).accept(new CleanResult(0, 0, List.of()));

		assertEquals(List.of(GREEN + "Cleaned " + WHITE + "Alex" + GREEN + " removed " + WHITE + 0),
				messages(alex));
	}

	@Test
	void forceIgnoresStamp() {
		when(stamps.needsClean(BEA, "vardera")).thenReturn(false);

		run(console, "force", "Bea");
		cleanCallback(BEA).accept(new CleanResult(2, 0, List.of()));

		verify(stamps, never()).needsClean(any(), anyString());
		assertEquals(List.of(GREEN + "Forced " + WHITE + "Bea" + GREEN + " removed " + WHITE + 2),
				messages(console));
	}

	@Test
	void cleanReportsFailure() {
		CommandSender sender = mock(CommandSender.class);
		run(sender, "clean", "Bea");
		cleanCallback(BEA).accept(null);

		assertEquals(List.of(RED + "Clean failed for Bea"), messages(sender));
	}

	@Test
	void cleanSkipsPlayersWhoLeft() {
		run(alex, "force");
		when(alex.isOnline()).thenReturn(false);
		cleanCallback(ALEX).accept(new CleanResult(1, 0, List.of()));

		assertTrue(messages(alex).isEmpty());
	}

	@Test
	void cleanReportsRunningClean() {
		when(cleaner.startClean(any(), anyString(), any())).thenReturn(false);

		run(console, "clean", "Bea");

		assertEquals(List.of(YELLOW + "Clean already running for Bea"), messages(console));
	}

	@Test
	void cleanRejectsUnknownTarget() {
		run(console, "force", "Nobody");
		verify(cleaner, never()).startClean(any(), anyString(), any());
	}

	@Test
	void tabCompletesSubcommands() {
		assertEquals(List.of("reload", "status", "inspect", "clean", "force"), tab(""));
		assertEquals(List.of("status"), tab("ST"));
		assertEquals(List.of("reload", "status", "inspect", "clean", "force"), tab((String) null));
	}

	@Test
	void tabCompletesPlayersForTargetedSubcommands() {
		assertEquals(List.of("Alex", "Bea"), tab("status", ""));
		assertEquals(List.of("Bea"), tab("Inspect", "b"));
		assertEquals(List.of("Alex"), tab("clean", "a"));
		assertEquals(List.of("Alex", "Bea"), tab("force", ""));
	}

	@Test
	void tabCompletesNothingElse() {
		assertEquals(List.of(), tab("reload", ""));
		assertEquals(List.of(), tab("status", "Bea", ""));
		assertEquals(List.of(), tab());
	}

	@Test
	void blankSeasonRejectsCleanAndForceWithAnActionableMessage() {
		when(plugin.seasonId()).thenReturn(" ");
		run(console, "clean", "Bea");
		run(console, "force", "Bea");
		verify(cleaner, never()).startClean(any(), anyString(), any());
		assertEquals(List.of(RED + "Cleaning is disabled: season-id is blank.",
			RED + "Cleaning is disabled: season-id is blank."), messages(console));
	}

	@Test
	void cachedOfflineLookupNeverCallsTheBlockingNameResolver() {
		OfflinePlayer cached = mock(OfflinePlayer.class);
		when(cached.getUniqueId()).thenReturn(ALEX);
		when(cached.hasPlayedBefore()).thenReturn(true);
		when(cached.getName()).thenReturn("Alex");
		bukkit.when(() -> Bukkit.getOfflinePlayerIfCached("Alex")).thenReturn(cached);
		run(console, "status", "Alex");
		bukkit.verify(() -> Bukkit.getOfflinePlayer("Alex"), never());
		assertEquals(GREEN + "Player " + WHITE + "Alex", messages(console).get(0));
	}

	@Test
	void uncachedPlayerResolvesAsynchronouslyAndContinuesOnlyOnMainThread() {
		OfflinePlayer offline = uncached("Cai", ALEX);
		run(console, "clean", "Cai");
		assertTrue(messages(console).isEmpty());
		bukkit.verify(() -> Bukkit.getPlayerUniqueId("Cai"), never());
		verify(offline, never()).hasPlayedBefore();
		verify(cleaner, never()).startClean(any(), anyString(), any());
		asyncTasks.remove().run();
		verify(cleaner, never()).startClean(any(), anyString(), any());
		verify(offline, never()).hasPlayedBefore();
		assertEquals(1, mainTasks.size());
		drainMain();
		verify(cleaner).startClean(eq(ALEX), eq("Cai"), any());
		bukkit.verify(() -> Bukkit.getOfflinePlayer(anyString()), never());
	}

	@Test
	void incompleteOrFailedLookupDoesNotCleanAnotherIdentity() {
		bukkit.when(() -> Bukkit.getOfflinePlayerIfCached(anyString())).thenReturn(null);
		run(console, "force", "Missing");
		asyncTasks.remove().run(); drainMain();
		bukkit.when(() -> Bukkit.getPlayerUniqueId("Failed")).thenThrow(new IllegalStateException("network offline"));
		run(console, "inspect", "Failed");
		asyncTasks.remove().run(); drainMain();
		assertEquals(List.of(RED + "Unknown player Missing", RED + "Player lookup failed for Failed. Please try again."), messages(console));
		verify(cleaner, never()).startClean(any(), anyString(), any());
		verify(cleaner, never()).inspect(any(), any());
	}

	@Test
	void offlineModeUsesTheServersOfflineUuid() {
		bukkit.when(Bukkit::getOnlineMode).thenReturn(false);
		UUID uuid = UUID.nameUUIDFromBytes("OfflinePlayer:Cai".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		uncached("Cai", uuid);
		run(console, "inspect", "Cai");
		asyncTasks.remove().run(); drainMain();
		verify(cleaner).inspect(eq(uuid), any());
		bukkit.verify(() -> Bukkit.getPlayerUniqueId("Cai"));
	}

	@Test
	void blankNameIsReportedWithoutThrowingOrScheduling() {
		run(console, "status", " ");
		assertEquals(List.of(RED + "Unknown player  "), messages(console));
		assertTrue(asyncTasks.isEmpty());
	}

	@Test
	void completedLookupIsDroppedAfterDisableOrSenderDisconnect() {
		uncached("Cai", ALEX);
		run(alex, "clean", "Cai");
		when(alex.isOnline()).thenReturn(false);
		asyncTasks.remove().run(); drainMain();
		assertTrue(messages(alex).isEmpty());
		run(console, "clean", "Cai");
		when(plugin.isEnabled()).thenReturn(false);
		asyncTasks.remove().run();
		assertTrue(mainTasks.isEmpty());
		verify(cleaner, never()).startClean(any(), anyString(), any());
	}

	@Test
	void disableBetweenLookupAndMainCallbackCancelsContinuation() {
		uncached("Cai", ALEX);
		run(console, "clean", "Cai");
		asyncTasks.remove().run();
		when(plugin.isEnabled()).thenReturn(false);
		drainMain();
		assertTrue(messages(console).isEmpty());
	}

	@Test
	void schedulerDisableRaceDropsLookupResult() {
		uncached("Cai", ALEX);
		when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenThrow(new IllegalPluginAccessException("disabled"));
		run(console, "clean", "Cai");
		asyncTasks.remove().run();
		assertTrue(messages(console).isEmpty());
		verify(cleaner, never()).startClean(any(), anyString(), any());
	}

	@Test
	void disabledSchedulerDoesNotSubmitNameLookup() {
		uncached("Cai", ALEX);
		when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenThrow(new IllegalPluginAccessException("disabled"));
		run(console, "clean", "Cai");
		assertTrue(asyncTasks.isEmpty());
		bukkit.verify(() -> Bukkit.getPlayerUniqueId(anyString()), never());
	}

	@Test
	void uncachedProxyPlayerUsesServerIdentityWhenBackendOnlineModeIsFalse() {
		bukkit.when(Bukkit::getOnlineMode).thenReturn(false);
		uncached("Cai", ALEX);
		run(console, "clean", "Cai");
		assertEquals(1, asyncTasks.size(), "Identity lookup must defer to Paper's proxy-aware resolver");
		verify(cleaner, never()).startClean(any(), anyString(), any());
		asyncTasks.remove().run(); drainMain();
		verify(cleaner).startClean(eq(ALEX), eq("Cai"), any());
		bukkit.verify(() -> Bukkit.getOfflinePlayer(ALEX));
	}

	private OfflinePlayer uncached(String name, UUID uuid) {
		bukkit.when(() -> Bukkit.getOfflinePlayerIfCached(name)).thenReturn(null);
		bukkit.when(() -> Bukkit.getPlayerUniqueId(name)).thenReturn(uuid);
		OfflinePlayer offline = mock(OfflinePlayer.class);
		when(offline.hasPlayedBefore()).thenReturn(true);
		when(offline.getUniqueId()).thenReturn(uuid);
		when(offline.getName()).thenReturn(name);
		bukkit.when(() -> Bukkit.getOfflinePlayer(uuid)).thenReturn(offline);
		return offline;
	}

	private void drainMain() {
		while (!mainTasks.isEmpty()) mainTasks.remove().run();
	}

	private boolean run(CommandSender sender, String... args) {
		boolean handled = executor.onCommand(sender, command, "permcleaner", args);
		drainMain();
		return handled;
	}

	private List<String> tab(String... args) {
		return executor.onTabComplete(console, command, "permcleaner", args);
	}

	@SuppressWarnings("unchecked")
	private Consumer<CleanResult> inspectCallback(UUID uuid) {
		ArgumentCaptor<Consumer<CleanResult>> captor = ArgumentCaptor.forClass(Consumer.class);
		verify(cleaner).inspect(eq(uuid), captor.capture());
		return captor.getValue();
	}

	@SuppressWarnings("unchecked")
	private Consumer<CleanResult> cleanCallback(UUID uuid) {
		ArgumentCaptor<Consumer<CleanResult>> captor = ArgumentCaptor.forClass(Consumer.class);
		verify(cleaner).startClean(eq(uuid), anyString(), captor.capture());
		return captor.getValue();
	}

	private UUID offline(String arg, String name) {
		UUID uuid = UUID.nameUUIDFromBytes(arg.getBytes());
		OfflinePlayer offline = mock(OfflinePlayer.class);
		when(offline.hasPlayedBefore()).thenReturn(true);
		when(offline.getUniqueId()).thenReturn(uuid);
		when(offline.getName()).thenReturn(name);
		bukkit.when(() -> Bukkit.getOfflinePlayerIfCached(arg)).thenReturn(offline);
		return uuid;
	}

	private Player player(UUID uuid, String name) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.getName()).thenReturn(name);
		when(player.getWorld()).thenReturn(world);
		when(player.isOnline()).thenReturn(true);
		return player;
	}

	private static List<String> messages(CommandSender sender) {
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(sender, atLeast(0)).sendMessage(captor.capture());
		return captor.getAllValues();
	}
}

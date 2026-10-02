package net.tfminecraft.permcleaner.lp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.PermissionNode;
import net.tfminecraft.permcleaner.PermCleaner;
import net.tfminecraft.permcleaner.keep.KeepList;
import net.tfminecraft.permcleaner.store.SeasonStampStore;

class SeasonCleanServiceTest {

	private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");

	private final Deque<Runnable> asyncTasks = new ArrayDeque<>();
	private final Deque<Runnable> mainTasks = new ArrayDeque<>();
	private final List<CleanResult> results = new ArrayList<>();
	private final Consumer<CleanResult> callback = results::add;

	private PermCleaner plugin;
	private SeasonStampStore stamps;
	private Logger logger;
	private UserManager users;
	private NodeMap data;
	private User user;
	private MockedStatic<Bukkit> bukkit;
	private MockedStatic<LuckPermsProvider> luckPerms;
	private SeasonCleanService service;
	private BukkitScheduler scheduler;

	@BeforeEach
	void setUp() {
		plugin = mock(PermCleaner.class);
		stamps = mock(SeasonStampStore.class);
		logger = mock(Logger.class);
		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.seasonId()).thenReturn("vardera");
		when(plugin.worldName()).thenReturn("");
		when(plugin.stamps()).thenReturn(stamps);
		when(plugin.keepList()).thenReturn(KeepList.fromPatterns(List.of("group.*")));
		when(plugin.getLogger()).thenReturn(logger);
		when(stamps.needsClean(PLAYER, "vardera")).thenReturn(true);

		scheduler = mock(BukkitScheduler.class);
		when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
			asyncTasks.add(invocation.getArgument(1));
			return null;
		});
		when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
			mainTasks.add(invocation.getArgument(1));
			return null;
		});
		bukkit = Mockito.mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
		// Callbacks are only ever raised from async tasks, so they hop back through runTask.
		bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);

		data = mock(NodeMap.class);
		user = mock(User.class);
		when(user.data()).thenReturn(data);
		givenNodes(permission("professions.vegetables"));
		users = mock(UserManager.class);
		when(users.loadUser(PLAYER)).thenReturn(CompletableFuture.completedFuture(user));
		when(users.saveUser(user)).thenReturn(CompletableFuture.completedFuture(null));
		LuckPerms api = mock(LuckPerms.class);
		when(api.getUserManager()).thenReturn(users);
		luckPerms = Mockito.mockStatic(LuckPermsProvider.class);
		luckPerms.when(LuckPermsProvider::get).thenReturn(api);

		service = new SeasonCleanService(plugin);
	}

	@AfterEach
	void tearDown() {
		luckPerms.close();
		bukkit.close();
	}

	@Test
	void considerIgnoresMissingPlayer() {
		service.consider(null, null);
		assertNoTasks();
	}

	@Test
	void considerIgnoresDisabledPlugin() {
		when(plugin.isEnabled()).thenReturn(false);
		service.consider(player(), null);
		assertNoTasks();
	}

	@Test
	void considerIgnoresOtherWorlds() {
		when(plugin.worldName()).thenReturn("lobby");
		service.consider(player(), world("survival"));
		assertNoTasks();
	}

	@Test
	void considerIgnoresMissingStampStore() {
		when(plugin.stamps()).thenReturn(null);
		service.consider(player(), null);
		assertNoTasks();
	}

	@Test
	void considerIgnoresStampedPlayers() {
		when(stamps.needsClean(PLAYER, "vardera")).thenReturn(false);
		service.consider(player(), null);
		assertNoTasks();
	}

	@Test
	void considerCleansUnstampedPlayers() {
		when(plugin.worldName()).thenReturn("lobby");
		service.consider(player(), world("Lobby"));
		runAll();

		verify(data).remove(any(Node.class));
		verify(users).saveUser(user);
		verify(stamps).setSeason(PLAYER, "vardera");
		verify(logger).info("Alex season vardera removed 1");
	}

	@Test
	void worldMatchesAnyWorldWhenUnset() {
		assertTrue(service.worldMatches(null));
		when(plugin.worldName()).thenReturn(null);
		assertTrue(service.worldMatches(world("survival")));
	}

	@Test
	void worldMatchesConfiguredWorldIgnoringCase() {
		when(plugin.worldName()).thenReturn("lobby");
		assertTrue(service.worldMatches(world("LOBBY")));
		assertFalse(service.worldMatches(world("survival")));
		assertFalse(service.worldMatches(null));
	}

	@Test
	void startCleanRejectsMissingUuid() {
		assertFalse(service.startClean(null, "Alex", callback));
		assertNoTasks();
	}

	@Test
	void startCleanRejectsDisabledPlugin() {
		when(plugin.isEnabled()).thenReturn(false);
		assertFalse(service.startClean(PLAYER, "Alex", callback));
		assertNoTasks();
	}

	@Test
	void startCleanRejectsDuplicateUntilFinished() {
		assertTrue(service.startClean(PLAYER, "Alex", callback));
		assertFalse(service.startClean(PLAYER, "Alex", callback));
		runAll();
		assertTrue(service.startClean(PLAYER, "Alex", callback));
	}

	@Test
	void cleanSavesStampsAndReports() {
		assertTrue(service.startClean(PLAYER, "Alex", callback));
		runAll();

		verify(users).saveUser(user);
		verify(stamps).setSeason(PLAYER, "vardera");
		assertEquals(1, results.size());
		assertEquals(1, results.get(0).removed());
	}

	@Test
	void cleanPersistsEvenWhenNothingRemoved() {
		givenNodes();
		service.startClean(PLAYER, "Alex", callback);
		runAll();

		verify(users).saveUser(user);
		verify(stamps).setSeason(PLAYER, "vardera");
		assertFalse(results.get(0).changed());
	}

	@Test
	void cleanUsesSeasonCapturedAtStart() {
		service.startClean(PLAYER, "Alex", callback);
		when(plugin.seasonId()).thenReturn("next");
		runAll();

		verify(stamps).setSeason(PLAYER, "vardera");
	}

	@Test
	void cleanWithoutCallbackStillStamps() {
		service.startClean(PLAYER, "Alex", null);
		runAll();

		verify(stamps).setSeason(PLAYER, "vardera");
		assertTrue(service.startClean(PLAYER, "Alex", null));
	}

	@Test
	void cleanReportsFailureWhenDisabledAfterLoad() {
		// Enabled to start, disabled once the async clean finishes, then enabled for the callback.
		when(plugin.isEnabled()).thenReturn(true, false, true);
		service.startClean(PLAYER, "Alex", callback);
		runAll();

		verify(stamps, never()).setSeason(any(), anyString());
		assertEquals(1, results.size());
		assertNull(results.get(0));
		assertTrue(service.startClean(PLAYER, "Alex", callback));
	}

	@Test
	void cleanWithoutCallbackToleratesDisableAfterLoad() {
		when(plugin.isEnabled()).thenReturn(true, false, true);
		service.startClean(PLAYER, "Alex", null);
		runAll();

		verify(stamps, never()).setSeason(any(), anyString());
	}

	@Test
	void cleanDropsCallbackWhenPluginStaysDisabled() {
		when(plugin.isEnabled()).thenReturn(true, false);
		service.startClean(PLAYER, "Alex", callback);
		runAll();

		assertTrue(results.isEmpty());
	}

	@Test
	void cleanReportsFailureWhenUserMissing() {
		when(users.loadUser(PLAYER)).thenReturn(CompletableFuture.completedFuture(null));
		service.startClean(PLAYER, "Alex", callback);
		runAll();

		verify(logger).log(eq(Level.WARNING), eq("Failed to clean permissions for Alex"), any(IllegalStateException.class));
		assertEquals(1, results.size());
		assertNull(results.get(0));
		verify(stamps, never()).setSeason(any(), anyString());
		assertTrue(service.startClean(PLAYER, "Alex", callback));
	}

	@Test
	void cleanFailureWithoutCallbackIsLogged() {
		when(users.loadUser(PLAYER)).thenReturn(CompletableFuture.completedFuture(null));
		service.startClean(PLAYER, "Alex", null);
		runAll();

		verify(logger).log(eq(Level.WARNING), eq("Failed to clean permissions for Alex"), any(IllegalStateException.class));
	}

	@Test
	void failureCallbackIsScheduledOnMainThread() {
		when(users.loadUser(PLAYER)).thenReturn(CompletableFuture.completedFuture(null));
		service.startClean(PLAYER, "Alex", callback);
		runAsync();

		assertTrue(results.isEmpty());
		assertEquals(1, mainTasks.size());
		runAll();
		assertEquals(1, results.size());
	}

	@Test
	void failureCallbackRunsDirectlyWhenAlreadyOnMainThread() {
		bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
		when(users.loadUser(PLAYER)).thenReturn(CompletableFuture.completedFuture(null));
		service.startClean(PLAYER, "Alex", callback);
		runAsync();

		assertTrue(mainTasks.isEmpty());
		assertEquals(1, results.size());
	}

	@Test
	void stampSkippedWhenDisabledBeforeMainThread() {
		when(plugin.isEnabled()).thenReturn(true, true, false);
		service.startClean(PLAYER, "Alex", callback);
		runAll();

		verify(stamps, never()).setSeason(any(), anyString());
		verify(logger, never()).info(anyString());
		assertEquals(1, results.get(0).removed());
	}

	@Test
	void stampSkippedWhenStoreClosedBeforeMainThread() {
		service.startClean(PLAYER, "Alex", callback);
		runAsync();
		when(plugin.stamps()).thenReturn(null);
		runAll();

		verify(stamps, never()).setSeason(any(), anyString());
		assertEquals(1, results.get(0).removed());
	}

	@Test
	void stampFailureIsReported() {
		doThrow(new IllegalStateException("closed")).when(stamps).setSeason(PLAYER, "vardera");
		service.startClean(PLAYER, "Alex", callback);
		runAll();

		verify(logger).log(eq(Level.WARNING), eq("Failed to stamp season for Alex"), any(IllegalStateException.class));
		assertEquals(1, results.size());
		assertNull(results.get(0));
		assertTrue(service.startClean(PLAYER, "Alex", callback));
	}

	@Test
	void stampFailureWithoutCallbackIsLogged() {
		doThrow(new IllegalStateException("closed")).when(stamps).setSeason(PLAYER, "vardera");
		service.startClean(PLAYER, "Alex", null);
		runAll();

		verify(logger).log(eq(Level.WARNING), eq("Failed to stamp season for Alex"), any(IllegalStateException.class));
		assertTrue(service.startClean(PLAYER, "Alex", null));
	}

	@Test
	void inspectReportsWithoutChangingUser() {
		service.inspect(PLAYER, callback);
		runAll();

		assertEquals(1, results.size());
		assertEquals(1, results.get(0).removed());
		verify(data, never()).remove(any(Node.class));
		verify(users, never()).saveUser(any());
		verifyNoInteractions(stamps);
	}

	@Test
	void inspectWithoutCallback() {
		service.inspect(PLAYER, null);
		runAll();

		verify(users).loadUser(PLAYER);
	}

	@Test
	void inspectReportsFailureWhenUserMissing() {
		when(users.loadUser(PLAYER)).thenReturn(CompletableFuture.completedFuture(null));
		service.inspect(PLAYER, callback);
		runAll();

		verify(logger).log(eq(Level.WARNING), eq("Failed to inspect permissions for " + PLAYER), any(IllegalStateException.class));
		assertEquals(1, results.size());
		assertNull(results.get(0));
	}

	@Test
	void inspectFailureWithoutCallbackIsLogged() {
		when(users.loadUser(PLAYER)).thenReturn(CompletableFuture.completedFuture(null));
		service.inspect(PLAYER, null);
		runAll();

		verify(logger).log(eq(Level.WARNING), eq("Failed to inspect permissions for " + PLAYER), any(IllegalStateException.class));
	}

	@Test
	void inspectCallbackIsScheduledOnMainThread() {
		service.inspect(PLAYER, callback);
		runAsync();

		assertTrue(results.isEmpty());
		runAll();
		assertEquals(1, results.size());
	}

	@Test
	void blankSeasonCannotCleanOrReserveAPlayer() {
		when(plugin.seasonId()).thenReturn("  ");
		service.consider(player(), null);
		assertFalse(service.startClean(PLAYER, "Alex", callback));
		assertNoTasks();
		verifyNoInteractions(users, stamps);
		when(plugin.seasonId()).thenReturn("vardera");
		assertTrue(service.startClean(PLAYER, "Alex", callback));
	}

	@Test
	void rejectedAsyncSubmissionReleasesReservationForRetry() {
		RuntimeException rejected = new IllegalStateException("scheduler rejected");
		when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class)))
			.thenThrow(rejected).thenAnswer(invocation -> { asyncTasks.add(invocation.getArgument(1)); return null; });
		assertSame(rejected, assertThrows(IllegalStateException.class,
			() -> service.startClean(PLAYER, "Alex", callback)));
		assertTrue(service.startClean(PLAYER, "Alex", callback));
		runAll();
		assertEquals(1, results.size());
		verify(stamps).setSeason(PLAYER, "vardera");
	}

	@Test
	void failedWorkerReleasesReservationEvenForAnError() {
		luckPerms.when(LuckPermsProvider::get).thenThrow(new NoClassDefFoundError("provider unloaded"));
		assertTrue(service.startClean(PLAYER, "Alex", callback));
		assertThrows(NoClassDefFoundError.class, this::runAsync);
		assertEquals(1, mainTasks.size());
		mainTasks.remove().run();
		assertEquals(1, results.size());
		assertNull(results.get(0));
		assertTrue(service.startClean(PLAYER, "Alex", callback));
		verifyNoInteractions(stamps);
	}

	@Test
	void throwingCallbackIsInvokedOnceAndReleasesReservation() {
		int[] calls = {0};
		IllegalStateException failure = new IllegalStateException("consumer failed");
		assertTrue(service.startClean(PLAYER, "Alex", result -> { calls[0]++; throw failure; }));
		assertSame(failure, assertThrows(IllegalStateException.class, this::runAll));
		assertEquals(1, calls[0]);
		verify(stamps).setSeason(PLAYER, "vardera");
		assertTrue(service.startClean(PLAYER, "Alex", callback));
	}

	@Test
	void inspectThrowingCallbackIsNotReportedAgainAsInspectionFailure() {
		bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
		int[] calls = {0};
		IllegalStateException failure = new IllegalStateException("consumer failed");
		service.inspect(PLAYER, result -> { calls[0]++; throw failure; });
		assertSame(failure, assertThrows(IllegalStateException.class, this::runAll));
		assertEquals(1, calls[0]);
		verify(users).loadUser(PLAYER);
	}

	@Test
	void rejectedMainSubmissionReleasesReservationForRetry() {
		when(scheduler.runTask(eq(plugin), any(Runnable.class)))
			.thenThrow(new IllegalStateException("scheduler rejected"));
		assertTrue(service.startClean(PLAYER, "Alex", callback));
		assertThrows(IllegalStateException.class, this::runAsync);
		assertTrue(service.startClean(PLAYER, "Alex", callback));
		verifyNoInteractions(stamps);
	}

	@Test
	void retryAfterFailedSaveStillPersistsTheMutatedUserBeforeStamping() {
		when(users.saveUser(user)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("storage offline")),
			CompletableFuture.completedFuture(null));
		assertTrue(service.startClean(PLAYER, "Alex", callback));
		runAll();
		assertNull(results.get(0));
		verify(stamps, never()).setSeason(any(), anyString());
		// LuckPerms keeps the mutated user loaded even though persistence failed.
		givenNodes();
		assertTrue(service.startClean(PLAYER, "Alex", callback));
		runAll();
		verify(users, org.mockito.Mockito.times(2)).saveUser(user);
		verify(stamps).setSeason(PLAYER, "vardera");
	}

	@Test
	void errorReportsFailureOnceEvenIfFailureCallbackThrows() {
		bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
		NoClassDefFoundError original = new NoClassDefFoundError("provider unloaded");
		IllegalStateException secondary = new IllegalStateException("consumer failed");
		luckPerms.when(LuckPermsProvider::get).thenThrow(original);
		int[] calls = {0};
		assertTrue(service.startClean(PLAYER, "Alex", result -> { assertNull(result); calls[0]++; throw secondary; }));
		assertSame(original, assertThrows(NoClassDefFoundError.class, this::runAsync));
		assertEquals(1, calls[0]);
		assertEquals(List.of(secondary), List.of(original.getSuppressed()));
		assertTrue(service.startClean(PLAYER, "Alex", callback));
	}

	@Test
	void failureNotificationRejectionCannotMaskTheOriginalWorkerError() {
		NoClassDefFoundError original = new NoClassDefFoundError("provider unloaded");
		IllegalStateException secondary = new IllegalStateException("scheduler stopped");
		luckPerms.when(LuckPermsProvider::get).thenThrow(original);
		when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenThrow(secondary);
		assertTrue(service.startClean(PLAYER, "Alex", callback));
		assertSame(original, assertThrows(NoClassDefFoundError.class, this::runAsync));
		assertEquals(List.of(secondary), List.of(original.getSuppressed()));
		assertTrue(service.startClean(PLAYER, "Alex", callback));
	}

	@Test
	void rejectedStampSubmissionNotifiesFailureBeforeAllowingRetry() {
		IllegalStateException rejected = new IllegalStateException("stamp task rejected");
		when(scheduler.runTask(eq(plugin), any(Runnable.class)))
			.thenThrow(rejected).thenAnswer(invocation -> { mainTasks.add(invocation.getArgument(1)); return null; });
		assertTrue(service.startClean(PLAYER, "Alex", callback));
		assertSame(rejected, assertThrows(IllegalStateException.class, this::runAsync));
		assertEquals(1, mainTasks.size());
		mainTasks.remove().run();
		assertEquals(1, results.size());
		assertNull(results.get(0));
		verifyNoInteractions(stamps);
		assertTrue(service.startClean(PLAYER, "Alex", callback));
	}

	@Test
	void rejectedStampSubmissionPreservesOriginalWhenNotificationThrows() {
		bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
		IllegalStateException rejected = new IllegalStateException("stamp task rejected");
		IllegalArgumentException consumerFailure = new IllegalArgumentException("consumer failed");
		when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenThrow(rejected);
		assertTrue(service.startClean(PLAYER, "Alex", result -> { assertNull(result); throw consumerFailure; }));
		assertSame(rejected, assertThrows(IllegalStateException.class, this::runAsync));
		assertEquals(List.of(consumerFailure), List.of(rejected.getSuppressed()));
		assertTrue(service.startClean(PLAYER, "Alex", callback));
	}

	@Test
	void ordinaryWorkerFailureRetainsThrowingNotificationAndAllowsRetry() {
		bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
		IllegalStateException original = new IllegalStateException("provider unavailable");
		IllegalArgumentException secondary = new IllegalArgumentException("consumer failed");
		luckPerms.when(LuckPermsProvider::get).thenThrow(original);
		int[] calls = {0};
		assertTrue(service.startClean(PLAYER, "Alex", result -> { assertNull(result); calls[0]++; throw secondary; }));
		org.junit.jupiter.api.Assertions.assertDoesNotThrow(this::runAsync);
		assertEquals(1, calls[0]);
		assertEquals(List.of(secondary), List.of(original.getSuppressed()));
		verify(logger).log(Level.WARNING, "Failed to clean permissions for Alex", original);
		assertTrue(service.startClean(PLAYER, "Alex", callback));
	}

	@Test
	void ordinaryWorkerFailureRetainsRejectedNotificationAndAllowsRetry() {
		IllegalStateException original = new IllegalStateException("provider unavailable");
		IllegalArgumentException secondary = new IllegalArgumentException("scheduler stopped");
		luckPerms.when(LuckPermsProvider::get).thenThrow(original);
		when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenThrow(secondary);
		assertTrue(service.startClean(PLAYER, "Alex", callback));
		org.junit.jupiter.api.Assertions.assertDoesNotThrow(this::runAsync);
		assertEquals(List.of(secondary), List.of(original.getSuppressed()));
		assertTrue(results.isEmpty());
		assertTrue(service.startClean(PLAYER, "Alex", callback));
	}

	private void givenNodes(Node... nodes) {
		when(data.toCollection()).thenReturn(List.of(nodes));
	}

	private static PermissionNode permission(String key) {
		PermissionNode node = mock(PermissionNode.class);
		when(node.getKey()).thenReturn(key);
		when(node.getPermission()).thenReturn(key);
		when(node.getContexts()).thenReturn(mock(ImmutableContextSet.class));
		return node;
	}

	private static Player player() {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(PLAYER);
		when(player.getName()).thenReturn("Alex");
		return player;
	}

	private static World world(String name) {
		World world = mock(World.class);
		when(world.getName()).thenReturn(name);
		return world;
	}

	private void runAsync() {
		while (!asyncTasks.isEmpty()) {
			asyncTasks.poll().run();
		}
	}

	private void runAll() {
		while (!asyncTasks.isEmpty() || !mainTasks.isEmpty()) {
			runAsync();
			while (!mainTasks.isEmpty()) {
				mainTasks.poll().run();
			}
		}
	}

	private void assertNoTasks() {
		assertTrue(asyncTasks.isEmpty());
		assertTrue(mainTasks.isEmpty());
	}
}

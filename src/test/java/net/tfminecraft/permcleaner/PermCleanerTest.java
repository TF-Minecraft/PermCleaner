package net.tfminecraft.permcleaner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.papermc.paper.plugin.configuration.PluginMeta;
import io.papermc.paper.plugin.provider.classloader.ConfiguredPluginClassLoader;
import io.papermc.paper.plugin.provider.classloader.PluginClassLoaderGroup;

import net.tfminecraft.permcleaner.command.PermCleanerCommand;
import net.tfminecraft.permcleaner.listener.SeasonCleanListener;

class PermCleanerTest {

	@TempDir
	Path dataFolder;

	private PermCleaner plugin;
	private YamlConfiguration config;
	private Logger logger;
	private PluginManager plugins;
	private Plugin luckPerms;
	private PluginCommand command;

	@BeforeEach
	void setUp() throws IOException {
		// JavaPlugin can only be constructed by the server's plugin loader, so run the real methods on a mock.
		plugin = mock(PermCleaner.class, withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
		config = bundledConfig();
		logger = mock(Logger.class);
		plugins = mock(PluginManager.class);
		luckPerms = mock(Plugin.class);
		command = mock(PluginCommand.class);
		Server server = mock(Server.class);
		when(server.getPluginManager()).thenReturn(plugins);
		when(plugins.getPlugin("LuckPerms")).thenReturn(luckPerms);
		when(luckPerms.isEnabled()).thenReturn(true);

		doReturn(config).when(plugin).getConfig();
		doNothing().when(plugin).saveDefaultConfig();
		doReturn(logger).when(plugin).getLogger();
		doReturn(server).when(plugin).getServer();
		doReturn(dataFolder.toFile()).when(plugin).getDataFolder();
		doReturn(command).when(plugin).getCommand("permcleaner");
	}

	@AfterEach
	void tearDown() {
		plugin.onDisable();
	}

	@Test
	void constructsUnderPluginClassLoader() throws Exception {
		try (PluginLoader loader = new PluginLoader();
				MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getUnsafe).thenReturn(mock(UnsafeValues.class));

			Object instance = loader.loadClass(PermCleaner.class.getName()).getConstructor().newInstance();

			assertSame(instance, loader.getPlugin());
			assertSame(loader, instance.getClass().getClassLoader());
		}
	}

	@Test
	void enableWiresServices() {
		plugin.onEnable();

		assertSame(plugin, PermCleaner.getInstance());
		assertNotNull(plugin.stamps());
		assertNotNull(plugin.cleaner());
		assertTrue(plugin.keepList().keeps("rulequiz.completed"));
		assertTrue(Files.exists(dataFolder.resolve("stamps.db")));
		verify(plugin).saveDefaultConfig();
		verify(plugins).registerEvents(any(SeasonCleanListener.class), eq(plugin));
		verify(command).setExecutor(any(PermCleanerCommand.class));
		verify(command).setTabCompleter(any(PermCleanerCommand.class));
		verify(logger).info("season-id=vardera world=lobby");
		verify(logger, never()).warning(anyString());
		verify(plugins, never()).disablePlugin(any());
	}

	@Test
	void disableClosesStampsAndClearsState() {
		plugin.onEnable();
		plugin.onDisable();

		assertNull(plugin.stamps());
		assertNull(plugin.cleaner());
		assertNull(PermCleaner.getInstance());

		plugin.onDisable();
		assertNull(plugin.stamps());
	}

	@Test
	void enableStopsWithoutLuckPerms() {
		when(plugins.getPlugin("LuckPerms")).thenReturn(null);

		plugin.onEnable();

		verify(logger).severe("LuckPerms is missing or disabled");
		verify(plugins).disablePlugin(plugin);
		assertNull(plugin.stamps());
		assertNull(plugin.cleaner());
		verify(plugins, never()).registerEvents(any(), any());
	}

	@Test
	void enableStopsWhenLuckPermsDisabled() {
		when(luckPerms.isEnabled()).thenReturn(false);

		plugin.onEnable();

		verify(logger).severe("LuckPerms is missing or disabled");
		verify(plugins).disablePlugin(plugin);
		assertNull(plugin.stamps());
	}

	@Test
	void enableStopsWhenStampsCannotOpen() throws IOException {
		Files.createDirectory(dataFolder.resolve("stamps.db"));

		plugin.onEnable();

		verify(logger).severe(startsWith("Failed to open stamps.db: "));
		verify(plugins).disablePlugin(plugin);
		assertNull(plugin.stamps());
		assertNull(plugin.cleaner());
		verify(command, never()).setExecutor(any());
	}

	@Test
	void settingsAreTrimmed() {
		config.set("season-id", "  season-2 ");
		config.set("world", " Lobby ");

		assertEquals("season-2", plugin.seasonId());
		assertEquals("Lobby", plugin.worldName());
		assertEquals("Lobby", plugin.displayWorld());
	}

	@Test
	void missingSettingsUseDefaults() {
		config.set("season-id", null);
		config.set("world", null);

		assertEquals("vardera", plugin.seasonId());
		assertEquals("", plugin.worldName());
		assertEquals("(any)", plugin.displayWorld());
	}

	@Test
	void nullSettingsAreBlank() {
		FileConfiguration empty = mock(FileConfiguration.class);
		doReturn(empty).when(plugin).getConfig();

		assertEquals("", plugin.seasonId());
		assertEquals("", plugin.worldName());
		assertEquals("(any)", plugin.displayWorld());
	}

	@Test
	void reloadKeepListReadsConfig() {
		config.set("keep", List.of("professions.*"));

		plugin.reloadKeepList();

		assertTrue(plugin.keepList().keeps("professions.vegetables"));
		assertFalse(plugin.keepList().keeps("group.default"));
		verify(logger, never()).warning(anyString());
	}

	@Test
	void emptyKeepListWarns() {
		config.set("keep", List.of());

		plugin.reloadKeepList();

		assertTrue(plugin.keepList().isEmpty());
		verify(logger).warning("keep list is empty; every user permission node will be removed on clean");
	}

	private static YamlConfiguration bundledConfig() throws IOException {
		try (InputStream in = PermCleanerTest.class.getResourceAsStream("/config.yml")) {
			assertNotNull(in, "config.yml resource");
			return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}

	/** Loads PermCleaner itself child-first, as Paper does, and delegates everything else. */
	private static final class PluginLoader extends URLClassLoader implements ConfiguredPluginClassLoader {
		private JavaPlugin plugin;

		PluginLoader() {
			super(new URL[] { PermCleaner.class.getProtectionDomain().getCodeSource().getLocation() },
					PermCleanerTest.class.getClassLoader());
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if (!name.equals(PermCleaner.class.getName())) {
				return super.loadClass(name, resolve);
			}
			synchronized (getClassLoadingLock(name)) {
				Class<?> loaded = findLoadedClass(name);
				return loaded != null ? loaded : findClass(name);
			}
		}

		@Override
		public Class<?> loadClass(String name, boolean resolve, boolean checkGlobal, boolean checkLibraries)
				throws ClassNotFoundException {
			return loadClass(name, resolve);
		}

		@Override
		public void init(JavaPlugin plugin) {
			this.plugin = plugin;
		}

		@Override
		public JavaPlugin getPlugin() {
			return plugin;
		}

		@Override
		public PluginMeta getConfiguration() {
			return null;
		}

		@Override
		public PluginClassLoaderGroup getGroup() {
			return null;
		}
	}
}

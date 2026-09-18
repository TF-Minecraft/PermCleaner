package net.tfminecraft.permcleaner.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.Plugins.TLibs.database.SqliteProvider;

class SeasonStampStoreTest {

	private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@Test
	void driverAvailable() {
		SqliteProvider.ensureDriverLoaded();
		assertTrue(SqliteProvider.isAvailable());
	}

	@Test
	void missingRowNeedsClean(@TempDir Path tempDir) {
		SeasonStampStore store = new SeasonStampStore(tempDir.resolve("stamps.db").toFile());
		try {
			assertTrue(store.getSeason(PLAYER).isEmpty());
			assertTrue(store.needsClean(PLAYER, "vardera"));
		} finally {
			store.close();
		}
	}

	@Test
	void setThenMatchAndMismatch(@TempDir Path tempDir) {
		SeasonStampStore store = new SeasonStampStore(tempDir.resolve("stamps.db").toFile());
		try {
			store.setSeason(PLAYER, "vardera");
			assertEquals("vardera", store.getSeason(PLAYER).orElseThrow());
			assertFalse(store.needsClean(PLAYER, "vardera"));
			assertTrue(store.needsClean(PLAYER, "vardera-2"));
		} finally {
			store.close();
		}
	}

	@Test
	void upsertOverwritesSeason(@TempDir Path tempDir) {
		SeasonStampStore store = new SeasonStampStore(tempDir.resolve("stamps.db").toFile());
		try {
			store.setSeason(PLAYER, "old");
			store.setSeason(PLAYER, "vardera");
			assertEquals("vardera", store.getSeason(PLAYER).orElseThrow());
			assertFalse(store.needsClean(PLAYER, "vardera"));
		} finally {
			store.close();
		}
	}

	@Test
	void blankExpectedAlwaysDirty(@TempDir Path tempDir) {
		SeasonStampStore store = new SeasonStampStore(tempDir.resolve("stamps.db").toFile());
		try {
			store.setSeason(PLAYER, "vardera");
			assertTrue(store.needsClean(PLAYER, null));
			assertTrue(store.needsClean(PLAYER, "  "));
		} finally {
			store.close();
		}
	}
}

package net.tfminecraft.permcleaner.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.tlibs.database.SqliteDatabaseException;
import net.tfminecraft.tlibs.database.SqliteProvider;

class SeasonStampStoreTest {

	private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");

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

	@Test
	void nullUuidHasNoSeason(@TempDir Path tempDir) {
		SeasonStampStore store = new SeasonStampStore(tempDir.resolve("stamps.db").toFile());
		try {
			assertTrue(store.getSeason(null).isEmpty());
			assertTrue(store.needsClean(null, "vardera"));
		} finally {
			store.close();
		}
	}

	@Test
	void setSeasonRejectsMissingValues(@TempDir Path tempDir) {
		SeasonStampStore store = new SeasonStampStore(tempDir.resolve("stamps.db").toFile());
		try {
			assertThrows(IllegalArgumentException.class, () -> store.setSeason(null, "vardera"));
			assertThrows(IllegalArgumentException.class, () -> store.setSeason(PLAYER, null));
			assertThrows(IllegalArgumentException.class, () -> store.setSeason(PLAYER, "  "));
			assertTrue(store.getSeason(PLAYER).isEmpty());
		} finally {
			store.close();
		}
	}

	@Test
	void seasonSurvivesReopen(@TempDir Path tempDir) {
		Path db = tempDir.resolve("stamps.db");
		SeasonStampStore first = new SeasonStampStore(db.toFile());
		first.setSeason(PLAYER, "vardera");
		first.close();

		SeasonStampStore second = new SeasonStampStore(db.toFile());
		try {
			assertEquals("vardera", second.getSeason(PLAYER).orElseThrow());
		} finally {
			second.close();
		}
	}

	@Test
	void nullOrBlankStoredSeasonIsMissing(@TempDir Path tempDir) throws SQLException {
		Path db = tempDir.resolve("stamps.db");
		// A table created without the NOT NULL constraint can still hold empty stamps.
		sql(db, "CREATE TABLE stamps (uuid TEXT PRIMARY KEY, season_id TEXT, cleaned_at INTEGER)",
				"INSERT INTO stamps VALUES ('" + PLAYER + "', NULL, 0)",
				"INSERT INTO stamps VALUES ('" + OTHER + "', '  ', 0)");
		SeasonStampStore store = new SeasonStampStore(db.toFile());
		try {
			assertTrue(store.getSeason(PLAYER).isEmpty());
			assertTrue(store.getSeason(OTHER).isEmpty());
			assertTrue(store.needsClean(OTHER, "vardera"));
		} finally {
			store.close();
		}
	}

	@Test
	void readFailureIsWrapped(@TempDir Path tempDir) throws SQLException {
		Path db = tempDir.resolve("stamps.db");
		SeasonStampStore store = new SeasonStampStore(db.toFile());
		try {
			sql(db, "DROP TABLE stamps");
			SqliteDatabaseException error = assertThrows(SqliteDatabaseException.class, () -> store.getSeason(PLAYER));
			assertEquals("Failed to read season stamp for " + PLAYER, error.getMessage());
		} finally {
			store.close();
		}
	}

	private static void sql(Path db, String... statements) throws SQLException {
		SqliteProvider.ensureDriverLoaded();
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
				Statement statement = connection.createStatement()) {
			for (String sql : statements) {
				statement.execute(sql);
			}
		}
	}
}

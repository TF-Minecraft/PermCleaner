package net.tfminecraft.permcleaner.store;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import me.Plugins.TLibs.database.SqliteDatabase;
import me.Plugins.TLibs.database.SqliteDatabaseException;

public final class SeasonStampStore {

	private static final String CREATE_TABLE = """
			CREATE TABLE IF NOT EXISTS stamps (
			  uuid TEXT PRIMARY KEY NOT NULL,
			  season_id TEXT NOT NULL,
			  cleaned_at INTEGER NOT NULL
			)
			""";

	private static final String SELECT_SEASON = "SELECT season_id FROM stamps WHERE uuid = ?";

	private static final String UPSERT = """
			INSERT INTO stamps (uuid, season_id, cleaned_at) VALUES (?, ?, ?)
			ON CONFLICT(uuid) DO UPDATE SET
			  season_id = excluded.season_id,
			  cleaned_at = excluded.cleaned_at
			""";

	private final SqliteDatabase database;

	public SeasonStampStore(File dbFile) {
		this.database = new SqliteDatabase(dbFile);
		this.database.execute(CREATE_TABLE);
	}

	public synchronized Optional<String> getSeason(UUID uuid) {
		if (uuid == null) {
			return Optional.empty();
		}
		String key = key(uuid);
		try (PreparedStatement statement = database.getConnection().prepareStatement(SELECT_SEASON)) {
			statement.setString(1, key);
			try (ResultSet result = statement.executeQuery()) {
				if (!result.next()) {
					return Optional.empty();
				}
				String season = result.getString("season_id");
				if (season == null || season.isBlank()) {
					return Optional.empty();
				}
				return Optional.of(season);
			}
		} catch (SQLException e) {
			throw new SqliteDatabaseException("Failed to read season stamp for " + key, e);
		}
	}

	public synchronized void setSeason(UUID uuid, String seasonId) {
		if (uuid == null) {
			throw new IllegalArgumentException("uuid required");
		}
		if (seasonId == null || seasonId.isBlank()) {
			throw new IllegalArgumentException("seasonId required");
		}
		database.executeUpdate(UPSERT, key(uuid), seasonId, System.currentTimeMillis());
	}

	public synchronized boolean needsClean(UUID uuid, String expected) {
		if (expected == null || expected.isBlank()) {
			return true;
		}
		Optional<String> stored = getSeason(uuid);
		return stored.isEmpty() || !expected.equals(stored.get());
	}

	public synchronized void close() {
		database.close();
	}

	private static String key(UUID uuid) {
		return uuid.toString().toLowerCase(Locale.ROOT);
	}
}

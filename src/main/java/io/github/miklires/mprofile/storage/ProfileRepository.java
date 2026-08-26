package io.github.miklires.mprofile.storage;

import io.github.miklires.mprofile.api.ProfileVisibility;
import io.github.miklires.mprofile.profile.ProfileData;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ProfileRepository implements AutoCloseable {
    private final String jdbcUrl;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("mprofile-db").factory());

    public ProfileRepository(Path dataDirectory) {
        jdbcUrl = "jdbc:h2:file:" + dataDirectory.resolve("profiles").toAbsolutePath().toString().replace('\\', '/')
                + ";DB_CLOSE_ON_EXIT=FALSE";
    }

    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                Class.forName("org.h2.Driver", true, ProfileRepository.class.getClassLoader());
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException("H2 driver is not available", exception);
            }
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version (version INT NOT NULL)");
                try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM schema_version")) {
                    result.next();
                    if (result.getInt(1) == 0) statement.executeUpdate("INSERT INTO schema_version(version) VALUES (1)");
                }
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS profiles (
                          player_id UUID PRIMARY KEY,
                          last_name VARCHAR(16) NOT NULL,
                          biography VARCHAR(120) NOT NULL,
                          visibility VARCHAR(16) NOT NULL,
                          theme VARCHAR(32) NOT NULL,
                          first_seen BIGINT NOT NULL,
                          last_seen BIGINT NOT NULL,
                          playtime_ticks BIGINT NOT NULL,
                          player_kills INT NOT NULL,
                          deaths INT NOT NULL
                        )
                        """);
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not initialize profile database", exception);
            }
        }, executor);
    }

    public CompletableFuture<Optional<ProfileData>> find(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> query("SELECT * FROM profiles WHERE player_id = ?", playerId), executor);
    }

    public CompletableFuture<Optional<ProfileData>> findByName(String name) {
        return CompletableFuture.supplyAsync(() -> query("SELECT * FROM profiles WHERE LOWER(last_name) = LOWER(?)", name), executor);
    }

    public CompletableFuture<Void> save(ProfileData profile) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("""
                    MERGE INTO profiles (player_id,last_name,biography,visibility,theme,first_seen,last_seen,
                      playtime_ticks,player_kills,deaths) KEY(player_id) VALUES (?,?,?,?,?,?,?,?,?,?)
                    """)) {
                statement.setObject(1, profile.playerId());
                statement.setString(2, profile.lastName());
                statement.setString(3, profile.biography());
                statement.setString(4, profile.visibility().name());
                statement.setString(5, profile.theme());
                statement.setLong(6, profile.firstSeen().toEpochMilli());
                statement.setLong(7, profile.lastSeen().toEpochMilli());
                statement.setLong(8, profile.playtimeTicks());
                statement.setInt(9, profile.playerKills());
                statement.setInt(10, profile.deaths());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not save profile " + profile.playerId(), exception);
            }
        }, executor);
    }

    private Optional<ProfileData> query(String sql, Object value) {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read a profile", exception);
        }
    }

    private ProfileData read(ResultSet result) throws SQLException {
        return new ProfileData(result.getObject("player_id", UUID.class), result.getString("last_name"),
                result.getString("biography"), ProfileVisibility.valueOf(result.getString("visibility")),
                result.getString("theme"), Instant.ofEpochMilli(result.getLong("first_seen")),
                Instant.ofEpochMilli(result.getLong("last_seen")), result.getLong("playtime_ticks"),
                result.getInt("player_kills"), result.getInt("deaths"));
    }

    private Connection connection() throws SQLException { return DriverManager.getConnection(jdbcUrl); }

    @Override public void close() { executor.close(); }
}

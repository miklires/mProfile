package io.github.miklires.mprofile.storage;

import io.github.miklires.mprofile.api.ProfileVisibility;
import io.github.miklires.mprofile.profile.ProfileData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileRepositoryTest {
    @TempDir Path directory;

    @Test void initializesAndRoundTripsProfile() {
        try (ProfileRepository repository = new ProfileRepository(directory)) {
            repository.initialize().join();
            UUID playerId = UUID.randomUUID();
            ProfileData profile = new ProfileData(playerId, "Player", "Biography", ProfileVisibility.LIMITED,
                    "BLUE", Instant.EPOCH, Instant.ofEpochSecond(10), 200, 4, 2);
            repository.save(profile).join();
            var loaded = repository.find(playerId).join();
            assertTrue(loaded.isPresent());
            assertEquals(profile, loaded.get());
        }
    }

    @Test void duplicateNamesResolveToMostRecentlySeenProfile() {
        try (ProfileRepository repository = new ProfileRepository(directory)) {
            repository.initialize().join();
            ProfileData old = new ProfileData(UUID.randomUUID(), "Player", "old", ProfileVisibility.PUBLIC,
                    "BLUE", Instant.EPOCH, Instant.ofEpochSecond(10), 0, 0, 0);
            ProfileData current = new ProfileData(UUID.randomUUID(), "Player", "new", ProfileVisibility.PUBLIC,
                    "BLUE", Instant.EPOCH, Instant.ofEpochSecond(20), 0, 0, 0);
            repository.save(old).join();
            repository.save(current).join();
            assertEquals(current.playerId(), repository.findByName("player").join().orElseThrow().playerId());
        }
    }

    @Test void migratesVersionOneProfilesWithSafePreferenceDefaults() throws Exception {
        Class.forName("org.h2.Driver");
        String url = "jdbc:h2:file:" + directory.resolve("profiles").toAbsolutePath().toString().replace('\\', '/')
                + ";DB_CLOSE_ON_EXIT=FALSE";
        UUID playerId = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE schema_version (version INT NOT NULL)");
            statement.executeUpdate("INSERT INTO schema_version(version) VALUES (1)");
            statement.executeUpdate("""
                    CREATE TABLE profiles (
                      player_id UUID PRIMARY KEY, last_name VARCHAR(16) NOT NULL, biography VARCHAR(120) NOT NULL,
                      visibility VARCHAR(16) NOT NULL, theme VARCHAR(32) NOT NULL, first_seen BIGINT NOT NULL,
                      last_seen BIGINT NOT NULL, playtime_ticks BIGINT NOT NULL, player_kills INT NOT NULL,
                      deaths INT NOT NULL)
                    """);
            try (var insert = connection.prepareStatement(
                    "INSERT INTO profiles VALUES (?,?,?,?,?,?,?,?,?,?)")) {
                insert.setObject(1, playerId);
                insert.setString(2, "Player");
                insert.setString(3, "Legacy");
                insert.setString(4, "PUBLIC");
                insert.setString(5, "BLUE");
                insert.setLong(6, 0L);
                insert.setLong(7, 1L);
                insert.setLong(8, 20L);
                insert.setInt(9, 1);
                insert.setInt(10, 2);
                insert.executeUpdate();
            }
        }
        try (ProfileRepository repository = new ProfileRepository(directory)) {
            repository.initialize().join();
            ProfileData migrated = repository.find(playerId).join().orElseThrow();
            assertEquals("NONE", migrated.cover());
            assertTrue(migrated.showStatistics());
            assertTrue(migrated.showActivity());
            assertTrue(migrated.showIntegrations());
            assertTrue(migrated.allowMessages());
        }
    }
}

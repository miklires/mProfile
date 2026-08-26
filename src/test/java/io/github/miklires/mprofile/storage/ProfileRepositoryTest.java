package io.github.miklires.mprofile.storage;

import io.github.miklires.mprofile.api.ProfileVisibility;
import io.github.miklires.mprofile.profile.ProfileData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

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
}

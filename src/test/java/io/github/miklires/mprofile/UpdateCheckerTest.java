package io.github.miklires.mprofile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {
    @Test void comparesNumericParts() {
        assertTrue(VersionComparison.compare("1.10.0", "1.9.9") > 0);
        assertTrue(VersionComparison.compare("1.1.1", "1.2.0") < 0);
        assertEquals(0, VersionComparison.compare("v1.1", "1.1.0"));
    }

    @Test void rejectsLabels() {
        assertThrows(IllegalArgumentException.class, () -> VersionComparison.compare("latest", "1.0.0"));
    }
}

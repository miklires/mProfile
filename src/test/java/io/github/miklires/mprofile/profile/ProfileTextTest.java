package io.github.miklires.mprofile.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileTextTest {
    @Test void removesControlsAndCollapsesWhitespace() {
        assertEquals("Hello world", ProfileText.biography("  Hello\n\t world\u0000  ", 120));
    }

    @Test void clampsDatabaseLimit() {
        assertEquals(120, ProfileText.biography("x".repeat(200), 500).length());
        assertEquals("", ProfileText.biography("text", -10));
    }
}

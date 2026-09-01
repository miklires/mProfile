package io.github.miklires.mprofile.profile;

public final class ProfileText {
    private ProfileText() {}

    public static String biography(String input, int configuredLimit) {
        int limit = Math.clamp(configuredLimit, 0, 120);
        StringBuilder clean = new StringBuilder(Math.min(input.length(), limit));
        boolean previousSpace = false;
        for (int index = 0; index < input.length() && clean.length() < limit; index++) {
            char character = input.charAt(index);
            if (Character.isISOControl(character)) continue;
            boolean space = Character.isWhitespace(character);
            if (space && (previousSpace || clean.isEmpty())) continue;
            clean.append(space ? (char) 32 : character);
            previousSpace = space;
        }
        return clean.toString().stripTrailing();
    }
}

package io.github.miklires.mprofile;

import java.util.ArrayList;
import java.util.List;

final class VersionComparison {
    private VersionComparison() { }

    static int compare(String left, String right) {
        List<Integer> first = parts(left);
        List<Integer> second = parts(right);
        for (int index = 0; index < Math.max(first.size(), second.size()); index++) {
            int result = Integer.compare(index < first.size() ? first.get(index) : 0,
                    index < second.size() ? second.get(index) : 0);
            if (result != 0) return result;
        }
        return 0;
    }

    private static List<Integer> parts(String value) {
        String core = (value == null ? "" : value.strip().replaceFirst("^[vV]", "")).split("[-+]", 2)[0];
        String[] raw = core.split("\\.");
        List<Integer> output = new ArrayList<>();
        for (String part : raw) {
            if (!part.matches("\\d+")) throw new IllegalArgumentException("Invalid version");
            output.add(Integer.parseInt(part));
        }
        if (output.isEmpty()) throw new IllegalArgumentException("Empty version");
        return output;
    }
}

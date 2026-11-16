package usv.dizertatie.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The scan methods a benchmark run can compare, in their canonical order. */
public final class BenchmarkMethods {

    public static final String AXE = "AXE";
    public static final String AI = "AI";
    public static final String COMBINED = "COMBINED";

    public static final List<String> ALL = List.of(AXE, AI, COMBINED);

    private BenchmarkMethods() {
    }

    /**
     * Validates and normalises requested methods: case-insensitive, de-duplicated, canonical order.
     * {@code null} means all methods.
     *
     * @throws IllegalArgumentException when the list is empty or names an unknown method
     */
    public static List<String> parse(List<String> requested) {
        if (requested == null) {
            return ALL;
        }
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("methods must name at least one of AXE, AI, COMBINED");
        }
        List<String> upper = new ArrayList<>();
        for (String m : requested) {
            String u = m == null ? "" : m.strip().toUpperCase(Locale.ROOT);
            if (!ALL.contains(u)) {
                throw new IllegalArgumentException("Unknown benchmark method: " + m + " (expected AXE, AI or COMBINED)");
            }
            upper.add(u);
        }
        return ALL.stream().filter(upper::contains).toList();
    }

    /** Stored form: comma-separated. */
    public static String join(List<String> methods) {
        return String.join(",", methods);
    }

    /** Reads the stored form; {@code null}/blank (runs created before methods were recorded) means all. */
    public static List<String> split(String stored) {
        if (stored == null || stored.isBlank()) {
            return ALL;
        }
        return parse(Arrays.stream(stored.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList());
    }

    public static boolean needsAi(String method) {
        return AI.equals(method) || COMBINED.equals(method);
    }
}

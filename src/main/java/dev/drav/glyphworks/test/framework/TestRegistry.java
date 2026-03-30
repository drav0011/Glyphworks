package dev.drav.glyphworks.test.framework;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Global static registry mapping module IDs to their {@link TestSuite}s.
 *
 * <p>
 * Each module registers its suites during {@code setupTests()} via
 * {@link #register(String, TestSuite)}. The registry is then queried by the
 * test command and headless launcher to resolve run targets.
 */
public final class TestRegistry {

    private static final Logger LOGGER = Logger.getLogger(TestRegistry.class.getName());

    /** module ID → (suite ID → suite), both insertion-ordered. */
    private static final Map<String, LinkedHashMap<String, TestSuite>> REGISTRY = new LinkedHashMap<>();

    private TestRegistry() {
    }

    public static void register(@Nonnull String moduleId, @Nonnull TestSuite suite) {
        REGISTRY.computeIfAbsent(moduleId, id -> new LinkedHashMap<>())
                .put(suite.getId(), suite);
        LOGGER.info("[TestRegistry] Registered suite \"" + moduleId + "." + suite.getId()
                + "\" with " + suite.getTests().size() + " test(s): "
                + suite.getTests().stream().map(TestCase::getName).toList());
    }

    @Nullable
    public static Map<String, TestSuite> getModule(@Nonnull String moduleId) {
        LinkedHashMap<String, TestSuite> suites = REGISTRY.get(moduleId);
        return suites != null ? Collections.unmodifiableMap(suites) : null;
    }

    @Nullable
    public static TestSuite get(@Nonnull String moduleId, @Nonnull String suiteId) {
        Map<String, TestSuite> suites = getModule(moduleId);
        return suites != null ? suites.get(suiteId) : null;
    }

    @Nonnull
    public static Set<String> moduleIds() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

    @Nonnull
    public static Collection<TestSuite> all() {
        return REGISTRY.values().stream()
                .flatMap(m -> m.values().stream())
                .toList();
    }

    /**
     * Resolves the ordered list of {@link TestCase}s to run from the given filter
     * parameters. Any combination of filters is valid:
     *
     * <ul>
     *   <li>All {@code null} → every registered test.</li>
     *   <li>{@code moduleName} only → all tests in that module.</li>
     *   <li>{@code suiteName} without {@code moduleName} → searched across all
     *       modules; an error is reported if the name is ambiguous.</li>
     *   <li>{@code testName} without {@code suiteName} → searched across all
     *       candidate suites; an error is reported if the name is ambiguous.</li>
     * </ul>
     *
     * @param onError called with a human-readable message on any error; the
     *                caller should abort after a {@code null} return
     * @return the queued tests, or {@code null} if an error was reported
     */
    @Nullable
    public static List<TestCase> buildQueue(
            @Nullable String moduleName,
            @Nullable String suiteName,
            @Nullable String testName,
            @Nonnull Consumer<String> onError) {

        // ── Step 1: collect candidate suites tagged with their module ID ──
        List<Map.Entry<String, TestSuite>> candidates = new ArrayList<>();
        if (moduleName != null) {
            LinkedHashMap<String, TestSuite> moduleSuites = REGISTRY.get(moduleName);
            if (moduleSuites == null) {
                onError.accept("Unknown module \"" + moduleName + "\". Available: "
                        + String.join(", ", REGISTRY.keySet()));
                return null;
            }
            for (TestSuite suite : moduleSuites.values()) {
                candidates.add(Map.entry(moduleName, suite));
            }
        } else {
            for (Map.Entry<String, LinkedHashMap<String, TestSuite>> me : REGISTRY.entrySet()) {
                for (TestSuite suite : me.getValue().values()) {
                    candidates.add(Map.entry(me.getKey(), suite));
                }
            }
        }

        // ── Step 2: filter by suite name ──
        if (suiteName != null) {
            List<Map.Entry<String, TestSuite>> matched = candidates.stream()
                    .filter(e -> e.getValue().getId().equals(suiteName))
                    .toList();
            if (matched.isEmpty()) {
                String scope = moduleName != null ? "module \"" + moduleName + "\"" : "any module";
                onError.accept("Unknown suite \"" + suiteName + "\" in " + scope + ". Available: "
                        + candidates.stream().map(e -> e.getValue().getId()).distinct()
                                  .collect(Collectors.joining(", ")));
                return null;
            }
            if (matched.size() > 1) {
                onError.accept("Suite \"" + suiteName + "\" exists in multiple modules: "
                        + matched.stream().map(Map.Entry::getKey).collect(Collectors.joining(", "))
                        + " — add --module to disambiguate.");
                return null;
            }
            candidates = matched;
        }

        // ── Step 3: filter by test name ──
        if (testName != null) {
            List<String> locations = new ArrayList<>();
            List<TestCase> found = new ArrayList<>();
            for (Map.Entry<String, TestSuite> e : candidates) {
                e.getValue().findTest(testName).ifPresent(tc -> {
                    locations.add(e.getKey() + "." + e.getValue().getId());
                    found.add(tc);
                });
            }
            if (found.isEmpty()) {
                String scope = candidates.size() == 1
                        ? "suite \"" + candidates.get(0).getKey() + "." + candidates.get(0).getValue().getId() + "\""
                        : (moduleName != null ? "module \"" + moduleName + "\"" : "any suite");
                onError.accept("Unknown test \"" + testName + "\" in " + scope + ". Available: "
                        + candidates.stream().flatMap(e -> e.getValue().getTests().stream())
                                  .map(TestCase::getName).distinct()
                                  .collect(Collectors.joining(", ")));
                return null;
            }
            if (found.size() > 1) {
                onError.accept("Test \"" + testName + "\" exists in multiple suites: "
                        + String.join(", ", locations)
                        + " — add --suite or --module to disambiguate.");
                return null;
            }
            return found;
        }

        // ── Return all tests from the surviving candidates ──
        List<TestCase> all = new ArrayList<>();
        for (Map.Entry<String, TestSuite> e : candidates) {
            all.addAll(e.getValue().getTests());
        }
        return all;
    }
}

package dev.drav.glyphworks.test;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Global registry of {@link TestSuite}s organised by module.
 *
 * <p>Register suites from each module's {@code setupTests()} override:
 * <pre>{@code
 * TestRegistry.register("grid", new TestSuite("grid_graph").test(...));
 * }</pre>
 *
 * <p>The three-level hierarchy ({@code module → suite → test}) maps directly to
 * the {@code /gtest <module> [suite] [test]} command.
 */
public final class TestRegistry {

    private static final Logger LOGGER = Logger.getLogger(TestRegistry.class.getName());

    /** module ID → (suite ID → suite), both insertion-ordered. */
    private static final Map<String, LinkedHashMap<String, TestSuite>> REGISTRY = new LinkedHashMap<>();

    private TestRegistry() {}

    /**
     * Registers a suite under the given module ID.
     * Re-registering the same module + suite ID pair overwrites the previous entry.
     */
    public static void register(@Nonnull String moduleId, @Nonnull TestSuite suite) {
        REGISTRY.computeIfAbsent(moduleId, id -> new LinkedHashMap<>())
                .put(suite.getId(), suite);
        LOGGER.info("[TestRegistry] Registered suite \"" + moduleId + "." + suite.getId()
                + "\" with " + suite.getTests().size() + " test(s): "
                + suite.getTests().stream().map(TestCase::getName).toList());
    }

    /**
     * Returns an unmodifiable view of all suite IDs → suites for the given module,
     * or {@code null} if the module has not been registered.
     */
    @Nullable
    public static Map<String, TestSuite> getModule(@Nonnull String moduleId) {
        LinkedHashMap<String, TestSuite> suites = REGISTRY.get(moduleId);
        return suites != null ? Collections.unmodifiableMap(suites) : null;
    }

    /**
     * Returns the suite registered under {@code moduleId / suiteId}, or {@code null}.
     */
    @Nullable
    public static TestSuite get(@Nonnull String moduleId, @Nonnull String suiteId) {
        Map<String, TestSuite> suites = getModule(moduleId);
        return suites != null ? suites.get(suiteId) : null;
    }

    /** Returns an unmodifiable view of all registered module IDs, in registration order. */
    @Nonnull
    public static Set<String> moduleIds() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

    /** Returns all suites across all modules, flattened, in registration order. */
    @Nonnull
    public static Collection<TestSuite> all() {
        return REGISTRY.values().stream()
                .flatMap(m -> m.values().stream())
                .toList();
    }
}

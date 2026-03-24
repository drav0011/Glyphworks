package dev.drav.glyphworks.test;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Global registry of {@link TestSuite}s.
 *
 * <p>Register suites once from {@code GlyphworksPlugin.setup()}:
 * <pre>{@code
 * TestRegistry.register(new TestSuite("smoke").test(...));
 * }</pre>
 */
public final class TestRegistry {

    private static final Logger LOGGER = Logger.getLogger(TestRegistry.class.getName());
    private static final Map<String, TestSuite> REGISTRY = new LinkedHashMap<>();

    private TestRegistry() {}

    public static void register(@Nonnull TestSuite suite) {
        REGISTRY.put(suite.getId(), suite);
        LOGGER.info("[TestRegistry] Registered suite \"" + suite.getId() + "\" with " + suite.getTests().size() + " test(s): " + suite.getTests().stream().map(TestCase::getName).toList());
    }

    @Nullable
    public static TestSuite get(@Nonnull String id) {
        return REGISTRY.get(id);
    }

    @Nonnull
    public static Collection<TestSuite> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }
}

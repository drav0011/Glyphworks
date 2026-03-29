package dev.drav.glyphworks.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;

/**
 * A named collection of {@link TestCase}s.
 *
 * <p>Register suites once at plugin setup time via {@link TestRegistry#register(String, TestSuite)}.
 * Suites are run via {@link dev.drav.glyphworks.test.command.GlyphTestCommand}.
 *
 * <pre>{@code
 * TestRegistry.register(moduleId,
 *     new TestSuite("fluid")
 *         .test(new TestCase("placer_places_fluid")
 *             .step(...)
 *         )
 * );
 * }</pre>
 */
public final class TestSuite {

    private final String id;
    private final List<TestCase> tests = new ArrayList<>();

    public TestSuite(@Nonnull String id) {
        this.id = id;
    }

    @Nonnull
    public TestSuite test(@Nonnull TestCase testCase) {
        tests.add(testCase);
        return this;
    }

    @Nonnull public String getId()            { return id; }
    @Nonnull public List<TestCase> getTests() { return Collections.unmodifiableList(tests); }

    @Nonnull
    public Optional<TestCase> findTest(@Nonnull String name) {
        return tests.stream().filter(t -> t.getName().equals(name)).findFirst();
    }
}

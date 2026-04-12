package dev.drav.glyphworks.test.framework;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;

/**
 * A named, ordered collection of {@link TestCase}s belonging to one module.
 *
 * <p>
 * Suites are registered via {@link TestRegistry#register(String, TestSuite)}.
 */
public final class TestSuite {

    private final String id;
    private final List<TestCase> tests = new ArrayList<>();
    private boolean persistence;

    public TestSuite(@Nonnull String id) {
        this.id = id;
    }

    @Nonnull
    public TestSuite test(@Nonnull TestCase testCase) {
        tests.add(testCase);
        return this;
    }

    @Nonnull
    public TestSuite persistence() {
        this.persistence = true;
        return this;
    }

    public boolean isPersistence() {
        return persistence;
    }

    @Nonnull
    public String getId() {
        return id;
    }

    @Nonnull
    public List<TestCase> getTests() {
        return Collections.unmodifiableList(tests);
    }

    @Nonnull
    public Optional<TestCase> findTest(@Nonnull String name) {
        return tests.stream().filter(t -> t.getName().equals(name)).findFirst();
    }
}

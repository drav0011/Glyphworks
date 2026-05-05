package dev.drav.glyphworks.test.framework;

import javax.annotation.Nonnull;

/**
 * Immutable queue entry used by the runner to preserve suite context for each test.
 */
public final class TestRunEntry {

    private final String moduleId;
    private final TestSuite suite;
    private final TestCase testCase;

    public TestRunEntry(@Nonnull String moduleId, @Nonnull TestSuite suite, @Nonnull TestCase testCase) {
        this.moduleId = moduleId;
        this.suite = suite;
        this.testCase = testCase;
    }

    @Nonnull
    public String getModuleId() {
        return moduleId;
    }

    @Nonnull
    public TestSuite getSuite() {
        return suite;
    }

    @Nonnull
    public TestCase getTestCase() {
        return testCase;
    }

    @Nonnull
    public String getSuiteLabel() {
        return moduleId + "." + suite.getId();
    }

    @Nonnull
    public String getTestLabel() {
        return getSuiteLabel() + "." + testCase.getName();
    }
}

package dev.drav.glyphworks.test.tests;

import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Self-tests for the test framework's own mechanics.
 *
 * <p>
 * These tests verify that {@link Steps#run}, {@link Steps#wait},
 * {@link Steps#succeedWhen}, and {@link Steps#assertThat} all behave correctly,
 * and that the auto-pass path for zero-step tests works without crashing.
 *
 * <p>
 * No Hytale world API is exercised here — all tests use a 1×1×1 area.
 */
public final class TestFrameworkTests {

    private static final int EXPECTED_FRAMEWORK_TEST_COUNT = 9;

    private TestFrameworkTests() {
    }

    /**
     * Builds and registers the {@code "framework"} test suite under
     * {@code moduleId}.
     * Call once from a module's {@code setupTests()}.
     */
    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        int[] beforeAllRuns = { 0 };
        int[] beforeEachRuns = { 0 };
        int[] afterEachRuns = { 0 };
        boolean[] afterFinishRan = { false };

        return new TestSuite("framework")
                .beforeAll(Steps.run(ctx -> beforeAllRuns[0]++))
                .beforeEach(Steps.run(ctx -> beforeEachRuns[0]++))
                .afterEach(Steps.run(ctx -> afterEachRuns[0]++))
                .afterAll(Steps.failIf(ctx -> beforeAllRuns[0] != 1,
                        "beforeAll should run exactly once"))
                .afterAll(Steps.failIf(ctx -> beforeEachRuns[0] < EXPECTED_FRAMEWORK_TEST_COUNT,
                        "beforeEach should run for each test"))
                .afterAll(Steps.failIf(ctx -> afterEachRuns[0] < EXPECTED_FRAMEWORK_TEST_COUNT,
                        "afterEach should run for each test"))
                .afterAll(Steps.failIf(ctx -> !afterFinishRan[0], "afterFinish hook should run"))
                .test(runStepExecutes())
                .test(stepOrderingPreserved())
                .test(waitStepDelays())
                .test(succeedWhenResolves())
                .test(assertPassesOnTrue())
                .test(failIfDoesNotFailWhenFalse())
                .test(succeedIfPassesWhenTrue())
                .test(afterFinishRuns(afterFinishRan))
                .test(autoPassEmptyTest());
    }

    // -------------------------------------------------------------------------
    // Test a: Steps.run fires its consumer
    // -------------------------------------------------------------------------

    private static TestCase runStepExecutes() {
        boolean[] ran = { false };

        return new TestCase("run_step_executes", 1, 1, 1)
                .step(Steps.run(ctx -> ran[0] = true))
                .step(Steps.assertThat(ctx -> ran[0], "Steps.run consumer was called"));
    }

    // -------------------------------------------------------------------------
    // Test b: steps execute in order
    // -------------------------------------------------------------------------

    private static TestCase stepOrderingPreserved() {
        int[] order = { 0, 0 };

        return new TestCase("step_ordering_preserved", 1, 1, 1)
                // Step 0: write slot 0
                .step(Steps.run(ctx -> order[0] = 1))
                // Step 1: verify slot 0, then write slot 1
                .step(Steps.assertThat(ctx -> order[0] == 1, "step 0 wrote order[0] before step 1 ran"))
                .step(Steps.run(ctx -> order[1] = 2))
                // Step 2: verify slot 1
                .step(Steps.assertThat(ctx -> order[1] == 2, "step 1 wrote order[1] before step 2 ran"));
    }

    // -------------------------------------------------------------------------
    // Test c: Steps.wait holds execution for roughly TPS ticks
    // -------------------------------------------------------------------------

    private static TestCase waitStepDelays() {
        long[] startMs = { 0 };

        return new TestCase("wait_step_delays", 1, 1, 1)
                .step(Steps.run(ctx -> startMs[0] = System.currentTimeMillis()))
                .step(Steps.wait(ctx -> ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    long elapsed = System.currentTimeMillis() - startMs[0];
                    // Expect at least 90% of the nominal wall-clock time to have passed.
                    // wait(getTps()) should hold for roughly 1 second.
                    long msPerTick = 1000L / ctx.getWorld().getTps();
                    long expected = ctx.getWorld().getTps() * msPerTick;
                    return elapsed >= expected * 9 / 10;
                }, "Steps.wait(ctx -> getTps()) delayed at least 90% of the expected wall-clock time"));
    }

    // -------------------------------------------------------------------------
    // Test d: Steps.succeedWhen polls its predicate per tick
    // -------------------------------------------------------------------------

    private static TestCase succeedWhenResolves() {
        int[] count = { 0 };

        return new TestCase("succeed_when_resolves", 1, 1, 1)
                .step(Steps.succeedWhen(ctx -> ++count[0] >= 5, ctx -> ctx.getWorld().getTps(), "counted 5 ticks"))
                .step(Steps.assertThat(ctx -> count[0] >= 5,
                        "succeedWhen predicate was polled at least 5 times"));
    }

    // -------------------------------------------------------------------------
    // Test e: Steps.assertThat returns DONE when predicate is true
    // -------------------------------------------------------------------------

    private static TestCase assertPassesOnTrue() {
        return new TestCase("assert_passes_on_true", 1, 1, 1)
                .step(Steps.assertThat(ctx -> true, "always-true predicate passes"));
    }

    // -------------------------------------------------------------------------
    // Test f: Steps.failIf does not fail when predicate is false
    // -------------------------------------------------------------------------

    private static TestCase failIfDoesNotFailWhenFalse() {
        return new TestCase("fail_if_does_not_fail_when_false", 1, 1, 1)
                .step(Steps.failIf(ctx -> false, "predicate false should not fail"))
                .step(Steps.succeed());
    }

    // -------------------------------------------------------------------------
    // Test g: Steps.succeedIf returns DONE when predicate is true
    // -------------------------------------------------------------------------

    private static TestCase succeedIfPassesWhenTrue() {
        return new TestCase("succeed_if_passes_when_true", 1, 1, 1)
                .step(Steps.succeedIf(ctx -> true, "predicate true should pass"));
    }

    // -------------------------------------------------------------------------
    // Test h: afterFinish hook runs after test steps
    // -------------------------------------------------------------------------

    private static TestCase afterFinishRuns(boolean[] finalizerRan) {
        return new TestCase("after_finish_runs", 1, 1, 1)
                .step(Steps.succeed())
                .afterFinish(Steps.afterFinish(ctx -> finalizerRan[0] = true));
    }

    // -------------------------------------------------------------------------
    // Test i: zero-step TestCase auto-passes without crashing
    // -------------------------------------------------------------------------

    private static TestCase autoPassEmptyTest() {
        return new TestCase("auto_pass_empty_test", 1, 1, 1);
    }
}

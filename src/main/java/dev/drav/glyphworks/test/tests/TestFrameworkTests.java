package dev.drav.glyphworks.test.tests;

import com.hypixel.hytale.server.core.util.thread.TickingThread;

import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Self-tests for the test framework's own mechanics.
 *
 * <p>
 * These tests verify that {@link Steps#run}, {@link Steps#wait},
 * {@link Steps#waitUntil}, and {@link Steps#assertThat} all behave correctly,
 * and that the auto-pass path for zero-step tests works without crashing.
 *
 * <p>
 * No Hytale world API is exercised here — all tests use a 1×1×1 area.
 */
public final class TestFrameworkTests {

    private static final int WAIT_TICKS = TickingThread.TPS;

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
        return new TestSuite("framework")
                .test(runStepExecutes())
                .test(stepOrderingPreserved())
                .test(waitStepDelays())
                .test(waitUntilResolves())
                .test(assertPassesOnTrue())
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
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    long elapsed = System.currentTimeMillis() - startMs[0];
                    // Expect at least 90% of the nominal wall-clock time to have passed.
                    long expected = (long) WAIT_TICKS * (1000L / TickingThread.TPS);
                    return elapsed >= expected * 9 / 10;
                }, "Steps.wait(" + WAIT_TICKS + ") delayed at least 90% of the expected wall-clock time"));
    }

    // -------------------------------------------------------------------------
    // Test d: Steps.waitUntil polls its predicate per tick
    // -------------------------------------------------------------------------

    private static TestCase waitUntilResolves() {
        int[] count = { 0 };

        return new TestCase("wait_until_resolves", 1, 1, 1)
                .step(Steps.waitUntil(ctx -> ++count[0] >= 5, WAIT_TICKS, "counted 5 ticks"))
                .step(Steps.assertThat(ctx -> count[0] >= 5,
                        "waitUntil predicate was polled at least 5 times"));
    }

    // -------------------------------------------------------------------------
    // Test e: Steps.assertThat returns DONE when predicate is true
    // -------------------------------------------------------------------------

    private static TestCase assertPassesOnTrue() {
        return new TestCase("assert_passes_on_true", 1, 1, 1)
                .step(Steps.assertThat(ctx -> true, "always-true predicate passes"));
    }

    // -------------------------------------------------------------------------
    // Test f: zero-step TestCase auto-passes without crashing
    // -------------------------------------------------------------------------

    private static TestCase autoPassEmptyTest() {
        return new TestCase("auto_pass_empty_test", 1, 1, 1);
    }
}

package dev.drav.glyphworks.test;

/**
 * A single step in a {@link TestCase}.
 *
 * <p>Steps are executed once per server tick until they return {@link StepResult#DONE}
 * or {@link StepResult#failed(String)}. Returning {@link StepResult#PENDING} keeps
 * the step active on the next tick — use this for wait-based steps.
 */
@FunctionalInterface
public interface TestStep {
    StepResult execute(TestRunnerContext ctx);
}

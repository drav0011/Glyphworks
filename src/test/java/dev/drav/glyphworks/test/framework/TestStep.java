package dev.drav.glyphworks.test.framework;

import dev.drav.glyphworks.test.runner.TestRunnerContext;

/**
 * A single unit of work executed by {@link dev.drav.glyphworks.test.runner.TestRunnerSystem}
 * each tick until it returns a terminal {@link StepResult}.
 */
@FunctionalInterface
public interface TestStep {
    StepResult execute(TestRunnerContext ctx);
}

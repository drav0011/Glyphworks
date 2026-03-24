package dev.drav.glyphworks.test;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Built-in {@link TestStep} factories — runner primitives only.
 *
 * <p>Block placement, world queries, and any other Hytale-native operations are
 * done inline by test authors via the {@link TestRunnerContext} passed to each step.
 */
public final class Steps {

    private Steps() {}

    /**
     * Waits for the given number of ticks before advancing.
     *
     * <p>Uses {@code ctx.component.ticksRemaining} as a countdown.
     * The counter is initialised on the first call and reset automatically
     * by the runner when transitioning to the next step or test.
     */
    public static TestStep wait(int ticks) {
        return ctx -> {
            if (ctx.component.ticksRemaining < 0) {
                ctx.component.ticksRemaining = ticks;
            }
            ctx.component.ticksRemaining--;
            return ctx.component.ticksRemaining <= 0 ? StepResult.DONE : StepResult.PENDING;
        };
    }

    /**
     * Executes the given action and immediately returns {@link StepResult#DONE}.
     *
     * <p>Use this for imperative side-effects (e.g. placing a block, spawning an
     * entity) that complete in a single tick.
     */
    public static TestStep run(Consumer<TestRunnerContext> action) {
        return ctx -> {
            action.accept(ctx);
            return StepResult.DONE;
        };
    }

    /**
     * Evaluates the predicate and returns {@link StepResult#DONE} on success or
     * {@link StepResult#failed(String)} with {@code description} on failure.
     */
    public static TestStep assertThat(Predicate<TestRunnerContext> predicate, String description) {
        return ctx -> predicate.test(ctx)
                ? StepResult.DONE
                : StepResult.failed("Assert failed: " + description);
    }
}

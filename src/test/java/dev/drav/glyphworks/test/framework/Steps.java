package dev.drav.glyphworks.test.framework;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import dev.drav.glyphworks.test.runner.TestRunnerContext;

/**
 * Built-in {@link TestStep} factories — runner primitives only.
 *
 * <p>
 * Block placement, world queries, and other Hytale-native operations are
 * done inline by test authors via the
 * {@link dev.drav.glyphworks.test.runner.TestRunnerContext}
 * passed to each step.
 *
 * <p>
 * The primitives are intentionally small and composable:
 * {@link #run}, {@link #wait}, {@link #succeedWhen}, {@link #assertThat},
 * {@link #succeed}, {@link #succeedIf}, {@link #fail}, {@link #failIf},
 * and {@link #afterFinish}.
 */
public final class Steps {

    private Steps() {
    }

    /**
     * Waits for the given number of ticks before advancing.
     *
     * <p>
     * The counter is initialised on the first call and reset automatically
     * by the runner when transitioning to the next step or test.
     */
    public static TestStep wait(int ticks) {
        return ctx -> {
            if (ctx.getTicksRemaining() < 0) {
                ctx.setTicksRemaining(ticks);
            }
            ctx.setTicksRemaining(ctx.getTicksRemaining() - 1);
            return ctx.getTicksRemaining() <= 0 ? StepResult.DONE : StepResult.PENDING;
        };
    }

    /**
     * Waits for a number of ticks computed at runtime from the context.
     *
     * <p>
     * {@code ticksSupplier} is called exactly once on the first tick of this
     * step, so the count can safely reference live world state such as
     * {@code ctx.getWorld().getTps()}.
     * <p>
     * Example:
     * 
     * <pre>
     * Steps.wait(ctx -&gt; 3 * ctx.getWorld().getTps())
     * </pre>
     */
    public static TestStep wait(ToIntFunction<TestRunnerContext> ticksSupplier) {
        return ctx -> {
            if (ctx.getTicksRemaining() < 0) {
                ctx.setTicksRemaining(ticksSupplier.applyAsInt(ctx));
            }
            ctx.setTicksRemaining(ctx.getTicksRemaining() - 1);
            return ctx.getTicksRemaining() <= 0 ? StepResult.DONE : StepResult.PENDING;
        };
    }

    /**
     * Polls {@code predicate} every tick until it returns {@code true} or
     * {@code maxTicks}
     * ticks have elapsed, then fails with {@code description}.
     */
    public static TestStep succeedWhen(Predicate<TestRunnerContext> predicate, int maxTicks, String description) {
        return ctx -> {
            if (predicate.test(ctx)) {
                return StepResult.DONE;
            }
            if (ctx.getTicksRemaining() < 0) {
                ctx.setTicksRemaining(maxTicks);
            }
            ctx.setTicksRemaining(ctx.getTicksRemaining() - 1);
            return ctx.getTicksRemaining() > 0
                    ? StepResult.PENDING
                    : StepResult.failed("Timed out after " + maxTicks + " ticks waiting for: " + description);
        };
    }

    /**
     * Polls {@code predicate} every tick until it returns {@code true} or the
     * runtime-computed tick limit has elapsed, then fails with {@code description}.
     *
     * <p>
     * {@code maxTicksSupplier} is called exactly once on the first tick of this
     * step, so the limit can safely reference live world state such as
     * {@code ctx.getWorld().getTps()}.
     * <p>
     * Example:
     *
     * <pre>
     * Steps.succeedWhen(pred, ctx -&gt; 5 * ctx.getWorld().getTps(), "description")
     * </pre>
     */
    public static TestStep succeedWhen(Predicate<TestRunnerContext> predicate,
            ToIntFunction<TestRunnerContext> maxTicksSupplier, String description) {
        return ctx -> {
            if (predicate.test(ctx)) {
                return StepResult.DONE;
            }
            if (ctx.getTicksRemaining() < 0) {
                ctx.setTicksRemaining(maxTicksSupplier.applyAsInt(ctx));
            }
            ctx.setTicksRemaining(ctx.getTicksRemaining() - 1);
            return ctx.getTicksRemaining() > 0
                    ? StepResult.PENDING
                    : StepResult.failed("Timed out waiting for: " + description);
        };
    }

    /**
     * Immediately fails with the provided reason.
     */
    public static TestStep fail(String reason) {
        return ctx -> StepResult.failed(reason);
    }

    /**
     * Fails when {@code predicate} evaluates to {@code true}; otherwise succeeds.
     */
    public static TestStep failIf(Predicate<TestRunnerContext> predicate, String reason) {
        return ctx -> predicate.test(ctx) ? StepResult.failed(reason) : StepResult.DONE;
    }

    /**
     * Immediately succeeds.
     */
    public static TestStep succeed() {
        return ctx -> StepResult.DONE;
    }

    /**
     * Succeeds only when {@code predicate} is {@code true}; otherwise fails.
     */
    public static TestStep succeedIf(Predicate<TestRunnerContext> predicate, String description) {
        return ctx -> predicate.test(ctx)
                ? StepResult.DONE
                : StepResult.failed("succeedIf failed: " + description);
    }

    /**
     * Executes once in the test's after-finish phase.
     *
     * <p>
     * This runs after the main step list completes (pass or fail), but before
     * suite-level afterEach hooks.
     */
    public static TestStep afterFinish(Consumer<TestRunnerContext> action) {
        return ctx -> {
            action.accept(ctx);
            return StepResult.DONE;
        };
    }

    /**
     * Executes {@code action} as an immediate single-tick side-effect.
     */
    public static TestStep run(Consumer<TestRunnerContext> action) {
        return ctx -> {
            action.accept(ctx);
            return StepResult.DONE;
        };
    }

    /**
     * Asserts that {@code predicate} is true, otherwise fails with
     * {@code description}.
     */
    public static TestStep assertThat(Predicate<TestRunnerContext> predicate, String description) {
        return ctx -> predicate.test(ctx)
                ? StepResult.DONE
                : StepResult.failed("Assert failed: " + description);
    }
}

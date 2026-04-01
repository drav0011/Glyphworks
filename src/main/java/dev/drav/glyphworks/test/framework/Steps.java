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
    public static TestStep waitUntil(Predicate<TestRunnerContext> predicate, int maxTicks, String description) {
        return ctx -> {
            if (predicate.test(ctx))
                return StepResult.DONE;
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
     * Steps.waitUntil(pred, ctx -&gt; 5 * ctx.getWorld().getTps(), "description")
     * </pre>
     */
    public static TestStep waitUntil(Predicate<TestRunnerContext> predicate,
            ToIntFunction<TestRunnerContext> maxTicksSupplier, String description) {
        return ctx -> {
            if (predicate.test(ctx))
                return StepResult.DONE;
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
     * Polls each tick, checking both a success and a fail predicate atomically.
     *
     * <ul>
     * <li>If {@code failPredicate} is true the step immediately returns a failure
     * result (the expected value was overshot).</li>
     * <li>If {@code successPredicate} is true the step returns DONE.</li>
     * <li>Otherwise the step returns PENDING and tries again next tick.</li>
     * </ul>
     *
     * Example usage:
     *
     * <pre>
     * Steps.waitUntilOrFail(
     *         ctx -&gt; getSink(ctx).getAmount() == RATE, // success
     *         ctx -&gt; getSink(ctx).getAmount() &gt; RATE, // overshot → immediate fail
     *         ctx -&gt; ctx.getWorld().getTps(),
     *         "exactly RATE litres transferred in first grid tick")
     * </pre>
     */
    public static TestStep waitUntilOrFail(Predicate<TestRunnerContext> successPredicate,
            Predicate<TestRunnerContext> failPredicate,
            ToIntFunction<TestRunnerContext> maxTicksSupplier,
            String description) {
        return ctx -> {
            if (failPredicate.test(ctx))
                return StepResult.failed("Transfer exceeded expected amount waiting for: " + description);
            if (successPredicate.test(ctx))
                return StepResult.DONE;
            if (ctx.getTicksRemaining() < 0)
                ctx.setTicksRemaining(maxTicksSupplier.applyAsInt(ctx));
            ctx.setTicksRemaining(ctx.getTicksRemaining() - 1);
            return ctx.getTicksRemaining() > 0
                    ? StepResult.PENDING
                    : StepResult.failed("Timed out waiting for: " + description);
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

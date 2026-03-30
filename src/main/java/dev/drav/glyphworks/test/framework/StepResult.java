package dev.drav.glyphworks.test.framework;

import javax.annotation.Nullable;

/**
 * The outcome of a single {@link TestStep} execution.
 *
 * <ul>
 * <li>{@link #DONE} – step finished successfully; advance to the next
 * step.</li>
 * <li>{@link #PENDING} – step is still running; re-run next tick.</li>
 * <li>{@link #failed(String)} – step failed; record the reason and move to the
 * next test.</li>
 * </ul>
 */
public final class StepResult {

    private enum Kind {
        DONE, PENDING, FAILED
    }

    public static final StepResult DONE = new StepResult(Kind.DONE, null);
    public static final StepResult PENDING = new StepResult(Kind.PENDING, null);

    private final Kind kind;
    @Nullable
    private final String failReason;

    private StepResult(Kind kind, @Nullable String failReason) {
        this.kind = kind;
        this.failReason = failReason;
    }

    public static StepResult failed(String reason) {
        return new StepResult(Kind.FAILED, reason);
    }

    public boolean isDone() {
        return kind == Kind.DONE;
    }

    public boolean isPending() {
        return kind == Kind.PENDING;
    }

    public boolean isFailed() {
        return kind == Kind.FAILED;
    }

    @Nullable
    public String getFailReason() {
        return failReason;
    }
}

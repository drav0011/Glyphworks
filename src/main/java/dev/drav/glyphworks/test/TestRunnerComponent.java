package dev.drav.glyphworks.test;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.GlyphworksPlugin;

/**
 * Holds the mutable state of an in-flight test run attached to a player entity.
 *
 * <p>One instance is added to the triggering player via {@code store.addComponent}
 * and removed by {@link TestRunnerSystem} once all tests in the queue have finished.
 *
 * <p>This component is transient — it has no codec and is not persisted.
 */
public final class TestRunnerComponent implements Component<EntityStore> {

    /** The ordered list of tests to run. Set once at construction. */
    final List<TestCase> queue;

    /** Index of the currently running {@link TestCase} in {@link #queue}. */
    int testIndex = 0;

    /** Index of the currently running {@link TestStep} within the current {@link TestCase}. */
    int stepIndex = 0;

    /**
     * Tick countdown used by {@link Steps#wait(int)}.
     * {@code -1} means "not yet initialised for the current step".
     * The runner resets this to {@code -1} whenever it advances to a new step or test.
     */
    int ticksRemaining = -1;

    /**
     * Precomputed world-space origins for each test in the queue.
     * Indexed by {@link #testIndex}. Set by {@link #init}.
     */
    int[] originXs;
    int[] originYs;
    int[] originZs;

    /**
     * Whether the current test's area has been cleared (set to Empty) before its first step.
     * Reset to {@code false} every time {@link #testIndex} advances.
     */
    boolean areaCleared = false;

    /** Accumulated PASS / FAIL strings reported at the end of the run. */
    final List<String> results = new ArrayList<>();

    /** No-arg constructor required by the ECS component factory. Creates an empty, inactive runner. */
    public TestRunnerComponent() {
        this.queue = new ArrayList<>();
    }

    /**
     * Populates the queue and precomputed origins before the first tick.
     * Must be called immediately after the component is added via {@code store.addComponent(ref, type)}.
     *
     * @param originXs  world X of each test's area origin, indexed by queue position
     * @param originYs  world Y of each test's area origin
     * @param originZs  world Z of each test's area origin
     */
    public void init(
            @Nonnull List<TestCase> tests,
            @Nonnull int[] originXs,
            @Nonnull int[] originYs,
            @Nonnull int[] originZs) {
        this.queue.clear();
        this.queue.addAll(tests);
        this.originXs   = originXs;
        this.originYs   = originYs;
        this.originZs   = originZs;
        this.areaCleared = false;
    }

    public static ComponentType<EntityStore, TestRunnerComponent> getComponentType() {
        return GlyphworksPlugin.get().getTestRunnerComponentType();
    }

    /**
     * Transient component — not cloned or serialised.
     */
    @Override
    @Nullable
    public Component<EntityStore> clone() {
        return null;
    }
}

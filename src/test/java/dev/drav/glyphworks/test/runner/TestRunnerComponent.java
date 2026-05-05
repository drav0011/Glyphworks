package dev.drav.glyphworks.test.runner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.test.GlyphworksTestPlugin;
import dev.drav.glyphworks.test.framework.TestRunEntry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * ECS component that holds the mutable state of an in-flight test run.
 *
 * <p>
 * Attached to a bare runner entity spawned inside the isolated test world.
 * {@link TestRunnerSystem} reads and mutates this component each tick.
 */
public final class TestRunnerComponent implements Component<EntityStore> {

    static final int PHASE_BEFORE_EACH = 0;
    static final int PHASE_TEST = 1;
    static final int PHASE_AFTER_FINISH = 2;
    static final int PHASE_AFTER_EACH = 3;
    static final int PHASE_DONE = 4;

    static final class TestExecutionState {
        String suiteLabel;
        int phase = PHASE_BEFORE_EACH;
        int stepIndex = 0;
        int hookStepIndex = 0;
        int ticksRemaining = -1;
        boolean done = false;
        boolean passed = true;
        String failureReason;
    }

    static final class SuiteExecutionState {
        final TestSuite suite;
        final String label;
        final List<Integer> testIndices = new ArrayList<>();
        int beforeAllStepIndex = 0;
        int beforeAllTicksRemaining = -1;
        int afterAllStepIndex = 0;
        int afterAllTicksRemaining = -1;
        boolean beforeAllDone = false;
        boolean beforeAllFailed = false;
        boolean afterAllDone = false;
        String suiteFailureReason;

        SuiteExecutionState(@Nonnull TestSuite suite, @Nonnull String label) {
            this.suite = suite;
            this.label = label;
        }
    }

    final List<TestRunEntry> queue;
    final List<TestExecutionState> testStates = new ArrayList<>();
    final Map<String, SuiteExecutionState> suiteStates = new LinkedHashMap<>();
    int[] originXs;
    int[] originYs;
    int[] originZs;
    boolean cleanupAfterRun = true;
    String testWorldName;
    @Nullable
    PlayerRef playerRef;
    boolean headless = false;
    final List<String> results = new ArrayList<>();
    boolean reported = false;

    public TestRunnerComponent() {
        this.queue = new ArrayList<>();
    }

    public void init(
            @Nonnull List<TestRunEntry> tests,
            @Nonnull int[] originXs,
            @Nonnull int[] originYs,
            @Nonnull int[] originZs,
            boolean cleanupAfterRun,
            @Nonnull String testWorldName,
            @Nullable PlayerRef playerRef,
            boolean headless) {
        this.queue.clear();
        this.queue.addAll(tests);
        this.originXs = Arrays.copyOf(originXs, originXs.length);
        this.originYs = Arrays.copyOf(originYs, originYs.length);
        this.originZs = Arrays.copyOf(originZs, originZs.length);
        this.cleanupAfterRun = cleanupAfterRun;
        this.testWorldName = testWorldName;
        this.playerRef = playerRef;
        this.headless = headless;
        this.reported = false;
        this.results.clear();
        this.testStates.clear();
        this.suiteStates.clear();

        for (int i = 0; i < this.queue.size(); i++) {
            TestRunEntry entry = this.queue.get(i);
            TestExecutionState state = new TestExecutionState();
            state.suiteLabel = entry.getSuiteLabel();
            this.testStates.add(state);

            SuiteExecutionState suiteState = this.suiteStates.computeIfAbsent(
                    entry.getSuiteLabel(), key -> new SuiteExecutionState(entry.getSuite(), key));
            suiteState.testIndices.add(i);
        }
    }

    public static ComponentType<EntityStore, TestRunnerComponent> getComponentType() {
        return GlyphworksTestPlugin.get().getTestRunnerComponentType();
    }

    @Override
    @Nullable
    public Component<EntityStore> clone() {
        return null;
    }
}

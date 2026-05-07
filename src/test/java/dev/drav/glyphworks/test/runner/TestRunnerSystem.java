package dev.drav.glyphworks.test.runner;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.test.framework.StepResult;
import dev.drav.glyphworks.test.framework.TestRunEntry;
import dev.drav.glyphworks.test.framework.TestStep;
import dev.drav.glyphworks.test.world.TestWorldManager;

/**
 * {@link EntityTickingSystem} that drives test execution.
 *
 * <p>
 * Each tick it picks up every entity carrying a {@link TestRunnerComponent},
 * advances its current step, and — once all tests complete — logs the report,
 * notifies the player (if any), cleans up the test world, and optionally shuts
 * the server down (headless mode).
 */
public final class TestRunnerSystem extends EntityTickingSystem<EntityStore> {

    private static final Logger LOGGER = Logger.getLogger(TestRunnerSystem.class.getName());

    @Override
    public Query<EntityStore> getQuery() {
        return TestRunnerComponent.getComponentType();
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        TestRunnerComponent component = chunk.getComponent(index, TestRunnerComponent.getComponentType());
        if (component == null)
            return;

        Ref<EntityStore> ref = chunk.getReferenceTo(index);

        if (component.reported) {
            return;
        }

        World world = store.getExternalData().getWorld();

        runSuiteBeforeAllHooks(world, store, ref, component);
        runTests(world, store, ref, component);
        runSuiteAfterAllHooks(world, store, ref, component);

        if (allTestsDone(component) && allSuitesTerminal(component)) {
            reportAndFinish(ref, component, store, commandBuffer);
        }
    }

    private static void runSuiteBeforeAllHooks(
            @Nonnull World world,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull TestRunnerComponent component) {

        for (TestRunnerComponent.SuiteExecutionState suiteState : component.suiteStates.values()) {
            if (suiteState.beforeAllDone || suiteState.beforeAllFailed) {
                continue;
            }

            StepResult result = executeStepFromList(
                    suiteState.suite.getBeforeAllSteps(),
                    suiteState.beforeAllStepIndex,
                    contextForSuiteHook(world, store, ref, component, suiteState, true),
                    suiteState.label + " beforeAll",
                    suiteState.beforeAllStepIndex);

            if (result.isPending()) {
                continue;
            }

            if (result.isFailed()) {
                suiteState.beforeAllFailed = true;
                suiteState.suiteFailureReason = nullToUnknown(result.getFailReason());
                String entry = "FAIL  SUITE " + suiteState.label + " [beforeAll]: " + suiteState.suiteFailureReason;
                component.results.add(entry);
                LOGGER.info("[GlyphTest] " + entry);
                continue;
            }

            suiteState.beforeAllStepIndex++;
            suiteState.beforeAllTicksRemaining = -1;
            if (suiteState.beforeAllStepIndex >= suiteState.suite.getBeforeAllSteps().size()) {
                suiteState.beforeAllDone = true;
            }
        }
    }

    private static void runTests(
            @Nonnull World world,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull TestRunnerComponent component) {

        for (int i = 0; i < component.queue.size(); i++) {
            TestRunnerComponent.TestExecutionState state = component.testStates.get(i);
            if (state.done) {
                continue;
            }

            TestRunEntry entry = component.queue.get(i);
            TestRunnerComponent.SuiteExecutionState suiteState = component.suiteStates.get(state.suiteLabel);
            if (suiteState == null) {
                continue;
            }

            if (suiteState.beforeAllFailed) {
                state.passed = false;
                state.failureReason = "suite beforeAll failed: " + suiteState.suiteFailureReason;
                markTestDone(component, entry, state);
                continue;
            }

            if (!suiteState.beforeAllDone) {
                continue;
            }

            switch (state.phase) {
                case TestRunnerComponent.PHASE_BEFORE_EACH ->
                    runBeforeEach(world, store, ref, component, entry, state, i);
                case TestRunnerComponent.PHASE_TEST -> runTestSteps(world, store, ref, component, entry, state, i);
                case TestRunnerComponent.PHASE_AFTER_FINISH ->
                    runAfterFinish(world, store, ref, component, entry, state, i);
                case TestRunnerComponent.PHASE_AFTER_EACH ->
                    runAfterEach(world, store, ref, component, entry, state, i);
                default -> {
                }
            }
        }
    }

    private static void runBeforeEach(
            @Nonnull World world,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull TestRunnerComponent component,
            @Nonnull TestRunEntry entry,
            @Nonnull TestRunnerComponent.TestExecutionState state,
            int testIndex) {

        List<TestStep> hooks = entry.getSuite().getBeforeEachSteps();
        StepResult result = executeStepFromList(
                hooks,
                state.hookStepIndex,
                contextForTest(world, store, ref, component, state, testIndex),
                entry.getTestLabel() + " beforeEach",
                state.hookStepIndex);

        if (result.isPending()) {
            return;
        }

        if (result.isFailed()) {
            state.passed = false;
            state.failureReason = "beforeEach failed: " + nullToUnknown(result.getFailReason());
            state.phase = TestRunnerComponent.PHASE_AFTER_FINISH;
            state.hookStepIndex = 0;
            state.ticksRemaining = -1;
            return;
        }

        state.hookStepIndex++;
        state.ticksRemaining = -1;
        if (state.hookStepIndex >= hooks.size()) {
            state.phase = TestRunnerComponent.PHASE_TEST;
            state.stepIndex = 0;
            state.hookStepIndex = 0;
        }
    }

    private static void runTestSteps(
            @Nonnull World world,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull TestRunnerComponent component,
            @Nonnull TestRunEntry entry,
            @Nonnull TestRunnerComponent.TestExecutionState state,
            int testIndex) {

        List<TestStep> steps = entry.getTestCase().getSteps();
        StepResult result = executeStepFromList(
                steps,
                state.stepIndex,
                contextForTest(world, store, ref, component, state, testIndex),
                entry.getTestLabel(),
                state.stepIndex);

        if (result.isPending()) {
            return;
        }

        if (result.isFailed()) {
            state.passed = false;
            state.failureReason = "step " + state.stepIndex + ": " + nullToUnknown(result.getFailReason());
            state.phase = TestRunnerComponent.PHASE_AFTER_FINISH;
            state.hookStepIndex = 0;
            state.ticksRemaining = -1;
            return;
        }

        state.stepIndex++;
        state.ticksRemaining = -1;
        if (state.stepIndex >= steps.size()) {
            state.phase = TestRunnerComponent.PHASE_AFTER_FINISH;
            state.hookStepIndex = 0;
        }
    }

    private static void runAfterEach(
            @Nonnull World world,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull TestRunnerComponent component,
            @Nonnull TestRunEntry entry,
            @Nonnull TestRunnerComponent.TestExecutionState state,
            int testIndex) {

        List<TestStep> hooks = entry.getSuite().getAfterEachSteps();
        StepResult result = executeStepFromList(
                hooks,
                state.hookStepIndex,
                contextForTest(world, store, ref, component, state, testIndex),
                entry.getTestLabel() + " afterEach",
                state.hookStepIndex);

        if (result.isPending()) {
            return;
        }

        if (result.isFailed() && state.passed) {
            state.passed = false;
            state.failureReason = "afterEach failed: " + nullToUnknown(result.getFailReason());
        }

        state.hookStepIndex++;
        state.ticksRemaining = -1;
        if (state.hookStepIndex >= hooks.size()) {
            markTestDone(component, entry, state);
        }
    }

    private static void runAfterFinish(
            @Nonnull World world,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull TestRunnerComponent component,
            @Nonnull TestRunEntry entry,
            @Nonnull TestRunnerComponent.TestExecutionState state,
            int testIndex) {

        List<TestStep> hooks = entry.getTestCase().getAfterFinishSteps();
        StepResult result = executeStepFromList(
                hooks,
                state.hookStepIndex,
                contextForTest(world, store, ref, component, state, testIndex),
                entry.getTestLabel() + " afterFinish",
                state.hookStepIndex);

        if (result.isPending()) {
            return;
        }

        if (result.isFailed() && state.passed) {
            state.passed = false;
            state.failureReason = "afterFinish failed: " + nullToUnknown(result.getFailReason());
        }

        state.hookStepIndex++;
        state.ticksRemaining = -1;
        if (state.hookStepIndex >= hooks.size()) {
            state.phase = TestRunnerComponent.PHASE_AFTER_EACH;
            state.hookStepIndex = 0;
        }
    }

    private static void runSuiteAfterAllHooks(
            @Nonnull World world,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull TestRunnerComponent component) {

        for (Map.Entry<String, TestRunnerComponent.SuiteExecutionState> mapEntry : component.suiteStates.entrySet()) {
            TestRunnerComponent.SuiteExecutionState suiteState = mapEntry.getValue();
            if (suiteState.beforeAllFailed || suiteState.afterAllDone || !suiteState.beforeAllDone) {
                continue;
            }
            if (!areSuiteTestsDone(component, suiteState)) {
                continue;
            }

            StepResult result = executeStepFromList(
                    suiteState.suite.getAfterAllSteps(),
                    suiteState.afterAllStepIndex,
                    contextForSuiteHook(world, store, ref, component, suiteState, false),
                    suiteState.label + " afterAll",
                    suiteState.afterAllStepIndex);

            if (result.isPending()) {
                continue;
            }

            if (result.isFailed()) {
                suiteState.afterAllDone = true;
                suiteState.suiteFailureReason = nullToUnknown(result.getFailReason());
                String entry = "FAIL  SUITE " + suiteState.label + " [afterAll]: " + suiteState.suiteFailureReason;
                component.results.add(entry);
                LOGGER.info("[GlyphTest] " + entry);
                continue;
            }

            suiteState.afterAllStepIndex++;
            suiteState.afterAllTicksRemaining = -1;
            if (suiteState.afterAllStepIndex >= suiteState.suite.getAfterAllSteps().size()) {
                suiteState.afterAllDone = true;
            }
        }
    }

    private static boolean areSuiteTestsDone(
            @Nonnull TestRunnerComponent component,
            @Nonnull TestRunnerComponent.SuiteExecutionState suiteState) {

        for (int testIndex : suiteState.testIndices) {
            if (!component.testStates.get(testIndex).done) {
                return false;
            }
        }
        return true;
    }

    private static boolean allTestsDone(@Nonnull TestRunnerComponent component) {
        for (TestRunnerComponent.TestExecutionState state : component.testStates) {
            if (!state.done) {
                return false;
            }
        }
        return true;
    }

    private static boolean allSuitesTerminal(@Nonnull TestRunnerComponent component) {
        for (TestRunnerComponent.SuiteExecutionState suiteState : component.suiteStates.values()) {
            if (suiteState.beforeAllFailed) {
                continue;
            }
            if (!suiteState.beforeAllDone) {
                return false;
            }
            if (!suiteState.afterAllDone) {
                return false;
            }
        }
        return true;
    }

    private static void markTestDone(
            @Nonnull TestRunnerComponent component,
            @Nonnull TestRunEntry entry,
            @Nonnull TestRunnerComponent.TestExecutionState state) {

        state.done = true;
        state.phase = TestRunnerComponent.PHASE_DONE;

        String result = state.passed
                ? "PASS  " + entry.getTestLabel()
                : "FAIL  " + entry.getTestLabel() + ": " + nullToUnknown(state.failureReason);
        component.results.add(result);
        LOGGER.info("[GlyphTest] " + result);
    }

    @Nonnull
    private static TestRunnerContext contextForTest(
            @Nonnull World world,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull TestRunnerComponent component,
            @Nonnull TestRunnerComponent.TestExecutionState state,
            int testIndex) {

        return new TestRunnerContext(
                world,
                store,
                ref,
                component.playerRef,
                component.originXs[testIndex],
                component.originYs[testIndex],
                component.originZs[testIndex],
                () -> state.ticksRemaining,
                remaining -> state.ticksRemaining = remaining);
    }

    @Nonnull
    private static TestRunnerContext contextForSuiteHook(
            @Nonnull World world,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull TestRunnerComponent component,
            @Nonnull TestRunnerComponent.SuiteExecutionState suiteState,
            boolean beforeAll) {

        int sampleIndex = suiteState.testIndices.isEmpty() ? 0 : suiteState.testIndices.get(0);
        return new TestRunnerContext(
                world,
                store,
                ref,
                component.playerRef,
                component.originXs[sampleIndex],
                component.originYs[sampleIndex],
                component.originZs[sampleIndex],
                beforeAll ? () -> suiteState.beforeAllTicksRemaining : () -> suiteState.afterAllTicksRemaining,
                remaining -> {
                    if (beforeAll) {
                        suiteState.beforeAllTicksRemaining = remaining;
                    } else {
                        suiteState.afterAllTicksRemaining = remaining;
                    }
                });
    }

    @Nonnull
    private static StepResult executeStepFromList(
            @Nonnull List<TestStep> steps,
            int stepIndex,
            @Nonnull TestRunnerContext ctx,
            @Nonnull String label,
            int visibleStepIndex) {

        if (stepIndex >= steps.size()) {
            return StepResult.DONE;
        }

        TestStep step = steps.get(stepIndex);
        try {
            return step.execute(ctx);
        } catch (Exception e) {
            LOGGER.warning("[GlyphTest] Exception in \"" + label + "\" step " + visibleStepIndex + ": " + e);
            return StepResult.failed("Exception: " + e.getMessage());
        }
    }

    @Nonnull
    private static String nullToUnknown(@Nullable String reason) {
        return reason == null || reason.isBlank() ? "unknown failure" : reason;
    }

    private static void reportAndFinish(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull TestRunnerComponent component,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        component.reported = true;

        @Nullable
        PlayerRef player = component.playerRef;

        long passed = component.results.stream().filter(r -> r.startsWith("PASS")).count();
        long total = component.results.size();
        boolean allPassed = passed == total;

        StringBuilder sb = new StringBuilder();
        sb.append("[GlyphTest] ").append(passed).append("/").append(total).append(" passed");
        for (String r : component.results) {
            sb.append("\n  ").append(r);
        }
        String report = sb.toString();

        LOGGER.info(report);
        if (player != null) {
            player.sendMessage(Message.raw(report));
        }

        commandBuffer.removeComponent(ref, TestRunnerComponent.getComponentType());

        String worldName = component.testWorldName;
        if (component.cleanupAfterRun) {
            // Run off the world thread so Universe.removeWorld() can properly join()
            // the world stop before attempting the folder move.
            CompletableFuture.runAsync(() -> TestWorldManager.destroyTestWorld(worldName));
        } else if (player != null) {
            World testWorld = Universe.get().getWorld(worldName);
            UUID playerWorldUuid = player.getWorldUuid();
            World playerWorld = playerWorldUuid != null ? Universe.get().getWorld(playerWorldUuid) : null;
            if (testWorld != null && playerWorld != null) {
                Transform spawnTransform = new Transform(new Vector3d(0, 64, 0));
                final PlayerRef finalPlayer = player;
                final World finalTestWorld = testWorld;
                // Add Teleport via the player's own world's store — the only store
                // that accepts the player's entity reference.
                playerWorld.execute(() -> {
                    Ref<EntityStore> playerRef = finalPlayer.getReference();
                    if (playerRef == null) {
                        LOGGER.warning("[GlyphTest] Cannot teleport player — no entity reference");
                        return;
                    }
                    try {
                        Store<EntityStore> playerStore = playerWorld.getEntityStore().getStore();
                        Player.setGameMode(playerRef, GameMode.Creative, playerStore);
                        Player playerComp = playerStore.getComponent(playerRef, Player.getComponentType());
                        MovementStatesComponent msComp = playerStore.getComponent(playerRef,
                                MovementStatesComponent.getComponentType());
                        if (playerComp != null && msComp != null) {
                            Player.applyMovementStates(playerRef, new SavedMovementStates(true),
                                    msComp.getMovementStates(), playerStore);
                        }
                        playerStore.addComponent(playerRef, Teleport.getComponentType(),
                                Teleport.createForPlayer(finalTestWorld, spawnTransform));
                    } catch (Exception e) {
                        LOGGER.warning("[GlyphTest] Failed to teleport player to test world: " + e.getMessage());
                    }
                });
            }
        }

        if (component.headless) {
            int exitCode = allPassed ? 0 : 1;
            LOGGER.info("[GlyphTest] Headless run complete — shutting down (exit " + exitCode + ")");
            new Thread(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                System.exit(exitCode);
            }, "glyphtest-shutdown").start();
        }
    }
}

package dev.drav.glyphworks.test.runner;

import java.util.List;
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
import dev.drav.glyphworks.test.framework.TestCase;
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

        if (component.testIndex >= component.queue.size()) {
            reportAndFinish(ref, component, store, commandBuffer);
            return;
        }

        World world = store.getExternalData().getWorld();

        TestCase currentTest = component.queue.get(component.testIndex);
        List<TestStep> steps = currentTest.getSteps();

        if (steps.isEmpty()) {
            component.results.add("PASS  " + currentTest.getName());
            advanceTest(component);
            if (component.testIndex >= component.queue.size()) {
                reportAndFinish(ref, component, store, commandBuffer);
            }
            return;
        }

        TestStep step = steps.get(component.stepIndex);
        TestRunnerContext ctx = new TestRunnerContext(world, store, ref, component.playerRef, component);

        StepResult result;
        try {
            result = step.execute(ctx);
        } catch (Exception e) {
            LOGGER.warning("[GlyphTest] Exception in \"" + currentTest.getName()
                    + "\" step " + component.stepIndex + ": " + e);
            result = StepResult.failed("Exception: " + e.getMessage());
        }

        if (result.isPending())
            return;

        if (result.isFailed()) {
            String entry = "FAIL  " + currentTest.getName()
                    + " [step " + component.stepIndex + "]: " + result.getFailReason();
            component.results.add(entry);
            LOGGER.info("[GlyphTest] " + entry);
            advanceTest(component);
        } else {
            component.stepIndex++;
            component.ticksRemaining = -1;
            if (component.stepIndex >= steps.size()) {
                String entry = "PASS  " + currentTest.getName();
                component.results.add(entry);
                LOGGER.info("[GlyphTest] " + entry);
                advanceTest(component);
            }
        }

        if (component.testIndex >= component.queue.size()) {
            reportAndFinish(ref, component, store, commandBuffer);
        }
    }

    private static void advanceTest(@Nonnull TestRunnerComponent component) {
        component.testIndex++;
        component.stepIndex = 0;
        component.ticksRemaining = -1;
    }

    private static void reportAndFinish(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull TestRunnerComponent component,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

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
            if (component.saveBeforeExit) {
                String worldNameForSave = worldName;
                CompletableFuture.runAsync(() -> {
                    LOGGER.info("[GlyphTest] Saving persistence world before exit...");
                    TestWorldManager.destroyTestWorld(worldNameForSave);
                    System.exit(exitCode);
                });
            } else {
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
}

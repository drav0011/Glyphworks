package dev.drav.glyphworks.test.runner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box2D;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.world.TestWorldManager;

/**
 * Shared entry point for kicking off a test run in an isolated void world.
 *
 * <p>
 * Handles grid layout, keep-loaded region computation, world creation, chunk
 * pre-warming, and runner entity spawn. Called by both
 * {@link dev.drav.glyphworks.test.command.TestRunCommand} (interactive) and
 * {@link dev.drav.glyphworks.test.headless.HeadlessTestLauncher} (CI / JVM
 * properties).
 */
public final class TestRunLauncher {

    private static final Logger LOGGER = Logger.getLogger(TestRunLauncher.class.getName());

    private TestRunLauncher() {
    }

    /**
     * Lays out test areas in a grid, creates the test world, pre-warms all
     * required chunks, and spawns the {@link TestRunnerComponent} entity.
     *
     * @param queue           ordered list of test cases to run (must not be empty)
     * @param cleanupAfterRun whether to destroy the world when the run finishes
     * @param player          the player who invoked the run, or {@code null} for
     *                        headless / console runs
     * @param headless        {@code true} to trigger {@code System.exit} when done
     * @param onError         called with a human-readable message if world creation
     *                        fails; the caller is responsible for logging / exiting
     */
    public static void launch(
            List<TestCase> queue,
            boolean cleanupAfterRun,
            @Nullable PlayerRef player,
            boolean headless,
            String runId,
            Consumer<String> onError) {

        // ── Grid layout ──
        int n = queue.size();
        int cols = (int) Math.ceil(Math.sqrt(n));
        int maxWidth = queue.stream().mapToInt(TestCase::getAreaWidth).max().orElse(1);
        int maxDepth = queue.stream().mapToInt(TestCase::getAreaDepth).max().orElse(1);
        int gap = 2;

        int[] originXs = new int[n];
        int[] originYs = new int[n];
        int[] originZs = new int[n];
        for (int i = 0; i < n; i++) {
            int col = i % cols;
            int row = i / cols;
            originXs[i] = col * (maxWidth + gap);
            originYs[i] = 64;
            originZs[i] = row * (maxDepth + gap);
        }

        // ── Keep-loaded region (block coords, XZ plane) ──
        int maxBlockX = 0;
        int maxBlockZ = 0;
        for (int i = 0; i < n; i++) {
            maxBlockX = Math.max(maxBlockX, originXs[i] + queue.get(i).getAreaWidth() - 1);
            maxBlockZ = Math.max(maxBlockZ, originZs[i] + queue.get(i).getAreaDepth() - 1);
        }
        Box2D keepLoaded = new Box2D(0.0, 0.0, (double) maxBlockX, (double) maxBlockZ);

        final List<TestCase> finalQueue = queue;
        final int[] finalXs = originXs;
        final int[] finalYs = originYs;
        final int[] finalZs = originZs;
        final boolean finalCleanup = cleanupAfterRun;

        TestWorldManager.createTestWorld(runId, keepLoaded)
                .thenCompose(testWorld -> {
                    Set<Long> indices = new HashSet<>();
                    for (int i = 0; i < finalQueue.size(); i++) {
                        TestCase tc = finalQueue.get(i);
                        int cMinX = ChunkUtil.chunkCoordinate(finalXs[i]);
                        int cMaxX = ChunkUtil.chunkCoordinate(finalXs[i] + tc.getAreaWidth() - 1);
                        int cMinZ = ChunkUtil.chunkCoordinate(finalZs[i]);
                        int cMaxZ = ChunkUtil.chunkCoordinate(finalZs[i] + tc.getAreaDepth() - 1);
                        for (int cx = cMinX; cx <= cMaxX; cx++) {
                            for (int cz = cMinZ; cz <= cMaxZ; cz++) {
                                indices.add(ChunkUtil.indexChunk(cx, cz));
                            }
                        }
                    }

                    CompletableFuture<?>[] futures = indices.stream()
                            .map(testWorld::getChunkAsync)
                            .toArray(CompletableFuture[]::new);
                    return CompletableFuture.allOf(futures).thenApply(v -> testWorld);
                })
                .whenComplete((testWorld, err) -> {
                    if (err != null) {
                        LOGGER.warning("[GlyphTest] Failed to create test world: " + err.getMessage());
                        onError.accept("Could not create test world - " + err.getMessage());
                        return;
                    }

                    testWorld.execute(() -> {
                        Store<EntityStore> testStore = testWorld.getEntityStore().getStore();

                        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
                        holder.addComponent(
                                TransformComponent.getComponentType(),
                                new TransformComponent(new Vector3d(0, 64, 0), Rotation3f.IDENTITY));

                        Ref<EntityStore> runnerRef = testStore.addEntity(holder, AddReason.SPAWN);

                        TestRunnerComponent runner = testStore.addComponent(
                                runnerRef, TestRunnerComponent.getComponentType());
                        runner.init(finalQueue, finalXs, finalYs, finalZs,
                                finalCleanup, testWorld.getName(), player, headless);
                    });
                });
    }
}

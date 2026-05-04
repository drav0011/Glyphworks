package dev.drav.glyphworks.test.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.hypixel.hytale.math.shape.Box2D;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;

/**
 * Creates and destroys isolated void worlds used for test runs.
 *
 * <p>
 * Regular test worlds (name: {@code glyphworks-test-<runId>}) use
 * {@code deleteOnRemove=true} and leave no disk state after the run.
 * The persistence test world (name: {@link #PERSISTENCE_WORLD_NAME}) omits
 * {@code deleteOnRemove} so it survives a controlled server stop.
 */
public final class TestWorldManager {

    private static final Logger LOGGER = Logger.getLogger(TestWorldManager.class.getName());
    public static final String NAME_PREFIX = "glyphworks-test-";
    public static final String PERSISTENCE_WORLD_NAME = "glyphworks-persistence-test";

    private TestWorldManager() {
    }

    @Nonnull
    public static CompletableFuture<World> createTestWorld(
            @Nonnull String runId, @Nonnull Box2D keepLoadedRegion) {
        String worldName = NAME_PREFIX + runId;

        WorldConfig config = new WorldConfig();
        config.setUuid(UUID.randomUUID());
        config.setDisplayName("GlyphTest " + runId);
        config.setWorldGenProvider(new VoidWorldGenProvider());
        config.setDeleteOnRemove(true);
        config.setCanUnloadChunks(false);
        config.getChunkConfig().setKeepLoadedRegion(keepLoadedRegion);
        config.markChanged();

        Path savePath;
        try {
            savePath = Universe.get().validateWorldPath(worldName);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "[GlyphTest] Invalid test world name: " + worldName, e);
            CompletableFuture<World> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }

        LOGGER.info("[GlyphTest] Creating test world: " + worldName);
        return Universe.get().makeWorld(worldName, savePath, config, true);
    }

    public static void destroyTestWorld(@Nonnull String worldName) {
        try {
            LOGGER.info("[GlyphTest] Destroying test world: " + worldName);
            Universe.get().removeWorld(worldName);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[GlyphTest] Failed to destroy test world: " + worldName, e);
        }
    }

    /**
     * Creates a fresh persistence test world with the fixed name
     * {@link #PERSISTENCE_WORLD_NAME}.
     *
     * <p>
     * Unlike normal test worlds, this world is not marked {@code deleteOnRemove},
     * so it survives a {@code Universe.removeWorld()} call and can be inspected
     * by the assert phase on the next server start.
     */
    @Nonnull
    public static CompletableFuture<World> createPersistenceTestWorld(@Nonnull Box2D keepLoadedRegion) {
        // Phase 1 always creates a clean world. Remove any leftover from a prior run.
        World stale = Universe.get().getWorlds().get(PERSISTENCE_WORLD_NAME);
        if (stale != null) {
            LOGGER.info("[GlyphTest] Stale persistence world loaded — removing before setup.");
            stale.getWorldConfig().setDeleteOnRemove(true);
            Universe.get().removeWorld(PERSISTENCE_WORLD_NAME);
        }

        Path savePath;
        try {
            savePath = Universe.get().validateWorldPath(PERSISTENCE_WORLD_NAME);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "[GlyphTest] Invalid persistence world name", e);
            CompletableFuture<World> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }

        // Delete stale on-disk folder that was not loaded by the engine
        // (e.g. left behind by a crashed assert run).
        if (savePath.toFile().exists()) {
            try {
                Files.walk(savePath)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> p.toFile().delete());
                LOGGER.info("[GlyphTest] Deleted stale persistence world folder at: " + savePath);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "[GlyphTest] Failed to delete stale persistence world folder", e);
            }
        }

        WorldConfig config = new WorldConfig();
        config.setUuid(UUID.randomUUID());
        config.setDisplayName("GlyphTest Persistence (Setup)");
        config.setWorldGenProvider(new VoidWorldGenProvider());
        config.setCanUnloadChunks(false);
        config.getChunkConfig().setKeepLoadedRegion(keepLoadedRegion);
        config.markChanged();

        LOGGER.info("[GlyphTest] Creating persistence test world: " + PERSISTENCE_WORLD_NAME);
        return Universe.get().makeWorld(PERSISTENCE_WORLD_NAME, savePath, config, true);
    }

    /**
     * Returns the persistence test world for the assert phase.
     *
     * <p>
     * The engine auto-loads all saved worlds on startup, so the world is always
     * already present by the time plugins start. If it is not found, the setup
     * phase has not run and the returned future completes exceptionally.
     *
     * @param deleteOnRemove {@code true} to delete the world after the assert run
     *                       (normal cleanup), {@code false} to keep it for debugging
     */
    @Nonnull
    public static CompletableFuture<World> loadPersistenceTestWorld(boolean deleteOnRemove) {
        LOGGER.info("[GlyphTest] Loading persistence test world: " + PERSISTENCE_WORLD_NAME);

        World existing = Universe.get().getWorlds().get(PERSISTENCE_WORLD_NAME);
        if (existing != null) {
            existing.getWorldConfig().setDeleteOnRemove(deleteOnRemove);
            return CompletableFuture.completedFuture(existing);
        }

        String msg = "Persistence world not found — run the setup phase first.";
        LOGGER.warning("[GlyphTest] " + msg);
        CompletableFuture<World> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException(msg));
        return failed;
    }

    @Nonnull
    public static String newRunId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}

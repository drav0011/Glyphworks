package dev.drav.glyphworks.test.world;

import java.nio.file.Path;
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
 * Test worlds (name: {@code glyphworks-test-<runId>}) use
 * {@code deleteOnRemove=true} and leave no disk state after the run.
 */
public final class TestWorldManager {

    private static final Logger LOGGER = Logger.getLogger(TestWorldManager.class.getName());
    public static final String NAME_PREFIX = "glyphworks-test-";

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

    @Nonnull
    public static String newRunId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}

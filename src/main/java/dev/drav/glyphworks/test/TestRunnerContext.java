package dev.drav.glyphworks.test;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Passed to every {@link TestStep} during execution.
 *
 * <p>Provides access to the live Hytale world and entity store so test authors
 * can call native Hytale APIs (block placement, block queries, etc.) directly,
 * without any framework wrapper.
 */
public final class TestRunnerContext {

    private final World world;
    private final Store<EntityStore> store;
    private final Ref<EntityStore> playerRef;
    private final PlayerRef player;

    /** Mutable runner state — package-private for {@link Steps} to read/write. */
    final TestRunnerComponent component;

    TestRunnerContext(
            @Nonnull World world,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerRef,
            @Nonnull PlayerRef player,
            @Nonnull TestRunnerComponent component) {
        this.world    = world;
        this.store    = store;
        this.playerRef = playerRef;
        this.player   = player;
        this.component = component;
    }

    /** The world the player who triggered the test is in. */
    @Nonnull public World getWorld() { return world; }

    /** The entity component store — use to query/modify any entity component. */
    @Nonnull public Store<EntityStore> getStore() { return store; }

    /** The {@link Ref} of the player entity that triggered the test run. */
    @Nonnull public Ref<EntityStore> getPlayerRef() { return playerRef; }

    /** The {@link PlayerRef} component of the triggering player. */
    @Nonnull public PlayerRef getPlayer() { return player; }

    /**
     * World X coordinate of this test's assigned area origin.
     * Use as the base for all block placements so the test stays within its allocated space.
     */
    public int getOriginX() { return component.originXs[component.testIndex]; }

    /**
     * World Y coordinate of this test's assigned area origin (player feet level at run-time).
     */
    public int getOriginY() { return component.originYs[component.testIndex]; }

    /**
     * World Z coordinate of this test's assigned area origin.
     */
    public int getOriginZ() { return component.originZs[component.testIndex]; }
}

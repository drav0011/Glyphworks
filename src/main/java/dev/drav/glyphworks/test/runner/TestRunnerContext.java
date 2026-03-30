package dev.drav.glyphworks.test.runner;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Passed to every {@link dev.drav.glyphworks.test.framework.TestStep} during
 * execution.
 *
 * <p>
 * Provides read-only access to the test world, the entity store, the
 * current test's grid origin, and the (optional) initiating player.
 */
public final class TestRunnerContext {

    private final World world;
    private final Store<EntityStore> store;
    private final Ref<EntityStore> runnerRef;
    @Nullable
    private final PlayerRef player;
    final TestRunnerComponent component;

    TestRunnerContext(
            @Nonnull World world,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> runnerRef,
            @Nullable PlayerRef player,
            @Nonnull TestRunnerComponent component) {
        this.world = world;
        this.store = store;
        this.runnerRef = runnerRef;
        this.player = player;
        this.component = component;
    }

    @Nonnull
    public World getWorld() {
        return world;
    }

    @Nonnull
    public Store<EntityStore> getStore() {
        return store;
    }

    @Nonnull
    public Ref<EntityStore> getRunnerRef() {
        return runnerRef;
    }

    @Nullable
    public PlayerRef getPlayer() {
        return player;
    }

    public int getOriginX() {
        return component.originXs[component.testIndex];
    }

    public int getOriginY() {
        return component.originYs[component.testIndex];
    }

    public int getOriginZ() {
        return component.originZs[component.testIndex];
    }

    /**
     * Used by {@link dev.drav.glyphworks.test.framework.Steps} to track per-step
     * tick counters.
     */
    public int getTicksRemaining() {
        return component.ticksRemaining;
    }

    public void setTicksRemaining(int t) {
        component.ticksRemaining = t;
    }
}

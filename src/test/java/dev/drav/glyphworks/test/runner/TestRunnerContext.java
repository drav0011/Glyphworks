package dev.drav.glyphworks.test.runner;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

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
    private final int originX;
    private final int originY;
    private final int originZ;
    private final IntSupplier ticksRemainingGetter;
    private final IntConsumer ticksRemainingSetter;

    TestRunnerContext(
            @Nonnull World world,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> runnerRef,
            @Nullable PlayerRef player,
            int originX,
            int originY,
            int originZ,
            @Nonnull IntSupplier ticksRemainingGetter,
            @Nonnull IntConsumer ticksRemainingSetter) {
        this.world = world;
        this.store = store;
        this.runnerRef = runnerRef;
        this.player = player;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.ticksRemainingGetter = ticksRemainingGetter;
        this.ticksRemainingSetter = ticksRemainingSetter;
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
        return originX;
    }

    public int getOriginY() {
        return originY;
    }

    public int getOriginZ() {
        return originZ;
    }

    /**
     * Used by {@link dev.drav.glyphworks.test.framework.Steps} to track per-step
     * tick counters.
     */
    public int getTicksRemaining() {
        return ticksRemainingGetter.getAsInt();
    }

    public void setTicksRemaining(int t) {
        ticksRemainingSetter.accept(t);
    }
}

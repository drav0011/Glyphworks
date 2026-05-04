package dev.drav.glyphworks.test.runner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.test.GlyphworksTestPlugin;
import dev.drav.glyphworks.test.framework.TestCase;

/**
 * ECS component that holds the mutable state of an in-flight test run.
 *
 * <p>
 * Attached to a bare runner entity spawned inside the isolated test world.
 * {@link TestRunnerSystem} reads and mutates this component each tick.
 */
public final class TestRunnerComponent implements Component<EntityStore> {

    final List<TestCase> queue;
    int testIndex = 0;
    int stepIndex = 0;
    int ticksRemaining = -1;
    int[] originXs;
    int[] originYs;
    int[] originZs;
    boolean cleanupAfterRun = true;
    boolean saveBeforeExit = false;
    String testWorldName;
    @Nullable
    PlayerRef playerRef;
    boolean headless = false;
    final List<String> results = new ArrayList<>();

    public TestRunnerComponent() {
        this.queue = new ArrayList<>();
    }

    public void init(
            @Nonnull List<TestCase> tests,
            @Nonnull int[] originXs,
            @Nonnull int[] originYs,
            @Nonnull int[] originZs,
            boolean cleanupAfterRun,
            boolean saveBeforeExit,
            @Nonnull String testWorldName,
            @Nullable PlayerRef playerRef,
            boolean headless) {
        this.queue.clear();
        this.queue.addAll(tests);
        this.originXs = Arrays.copyOf(originXs, originXs.length);
        this.originYs = Arrays.copyOf(originYs, originYs.length);
        this.originZs = Arrays.copyOf(originZs, originZs.length);
        this.cleanupAfterRun = cleanupAfterRun;
        this.saveBeforeExit = saveBeforeExit;
        this.testWorldName = testWorldName;
        this.playerRef = playerRef;
        this.headless = headless;
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

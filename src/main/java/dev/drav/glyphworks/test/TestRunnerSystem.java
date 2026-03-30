package dev.drav.glyphworks.test;

import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.joml.Vector3i;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.fluid.util.FluidUtil;
import dev.drav.glyphworks.test.command.TestCommand;

/**
 * Advances in-flight test runs every server tick.
 *
 * <p>Queries any entity that has a {@link TestRunnerComponent} and a {@link PlayerRef}
 * (i.e. the player that triggered {@link TestCommand}).  Each tick it executes the
 * current {@link TestStep}, interprets the {@link StepResult}, and either stays on the
 * step, moves to the next one, or finalises the test run and removes the component.
 */
public final class TestRunnerSystem extends EntityTickingSystem<EntityStore> {

    private static final Logger LOGGER = Logger.getLogger(TestRunnerSystem.class.getName());

    @Override
    public Query<EntityStore> getQuery() {
        // PlayerRef ensures we only tick actual player entities.
        return Query.and(TestRunnerComponent.getComponentType(), PlayerRef.getComponentType());
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        TestRunnerComponent component = chunk.getComponent(index, TestRunnerComponent.getComponentType());
        if (component == null) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(index);

        // All tests done — shouldn't normally happen here but guard anyway.
        if (component.testIndex >= component.queue.size()) {
            reportAndCleanup(ref, component, store, commandBuffer);
            return;
        }

        PlayerRef player = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null) {
            // Player left — remove silently.
            commandBuffer.removeComponent(ref, TestRunnerComponent.getComponentType());
            return;
        }

        World world = store.getExternalData().getWorld();
        TestCase currentTest = component.queue.get(component.testIndex);
        List<TestStep> steps = currentTest.getSteps();

        // Clear the test area to Empty on first entry, before any steps run.
        if (!component.areaCleared) {
            clearTestArea(currentTest, component, world);
            component.areaCleared = true;
        }

        // A test with no steps auto-passes.
        if (steps.isEmpty()) {
            component.results.add("PASS  " + currentTest.getName());
            advanceTest(component);
            if (component.testIndex >= component.queue.size()) {
                reportAndCleanup(ref, component, store, commandBuffer);
            }
            return;
        }

        TestStep step = steps.get(component.stepIndex);
        TestRunnerContext ctx = new TestRunnerContext(world, store, ref, player, component);

        StepResult result;
        try {
            result = step.execute(ctx);
        } catch (Exception e) {
            LOGGER.warning("[GlyphTest] Exception in \"" + currentTest.getName()
                    + "\" step " + component.stepIndex + ": " + e);
            result = StepResult.failed("Exception: " + e.getMessage());
        }

        if (result.isPending()) {
            return;
        }

        if (result.isFailed()) {
            String entry = "FAIL  " + currentTest.getName()
                    + " [step " + component.stepIndex + "]: " + result.getFailReason();
            component.results.add(entry);
            LOGGER.info("[GlyphTest] " + entry);
            advanceTest(component);
        } else {
            // DONE — advance to next step.
            component.stepIndex++;
            component.ticksRemaining = -1;

            if (component.stepIndex >= steps.size()) {
                // All steps passed.
                String entry = "PASS  " + currentTest.getName();
                component.results.add(entry);
                LOGGER.info("[GlyphTest] " + entry);
                advanceTest(component);
            }
        }

        if (component.testIndex >= component.queue.size()) {
            reportAndCleanup(ref, component, store, commandBuffer);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static void advanceTest(@Nonnull TestRunnerComponent component) {
        component.testIndex++;
        component.stepIndex = 0;
        component.ticksRemaining = -1;
        component.areaCleared = false;
    }

    private static void clearTestArea(
            @Nonnull TestCase test,
            @Nonnull TestRunnerComponent component,
            @Nonnull World world) {
        int ox = component.originXs[component.testIndex];
        int oy = component.originYs[component.testIndex];
        int oz = component.originZs[component.testIndex];
        for (int x = ox; x < ox + test.getAreaWidth(); x++) {
            for (int y = oy; y < oy + test.getAreaHeight(); y++) {
                for (int z = oz; z < oz + test.getAreaDepth(); z++) {
                    world.setBlock(x, y, z, "Empty");
                    clearFluid(world, x, y, z);
                }
            }
        }
    }

    private static void clearFluid(@Nonnull World world, int x, int y, int z) {
        ChunkStore chunkStore = world.getChunkStore();
        FluidSection fs = FluidUtil.getFluidSection(chunkStore, chunkStore.getStore(), new Vector3i(x, y, z));
        if (fs == null) return;
        fs.setFluid(x, y, z, 0, (byte) 0);
    }

    private static void reportAndCleanup(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull TestRunnerComponent component,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        PlayerRef player = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());

        long passed = component.results.stream().filter(r -> r.startsWith("PASS")).count();
        long total  = component.results.size();

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

        // Clear all test areas to Empty unless the --no-cleanup flag was used.
        if (component.cleanupAfterRun) {
            World world = store.getExternalData().getWorld();
            for (int i = 0; i < component.queue.size(); i++) {
                TestCase test = component.queue.get(i);
                int ox = component.originXs[i];
                int oy = component.originYs[i];
                int oz = component.originZs[i];
                for (int x = ox; x < ox + test.getAreaWidth(); x++) {
                    for (int y = oy; y < oy + test.getAreaHeight(); y++) {
                        for (int z = oz; z < oz + test.getAreaDepth(); z++) {
                            world.setBlock(x, y, z, "Empty");
                            clearFluid(world, x, y, z);
                        }
                    }
                }
            }
        }

        commandBuffer.removeComponent(ref, TestRunnerComponent.getComponentType());
    }
}

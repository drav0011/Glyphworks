package dev.drav.glyphworks.test;

import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Advances in-flight test runs every server tick.
 *
 * <p>Queries any entity that has a {@link TestRunnerComponent} and a {@link PlayerRef}
 * (i.e. the player that ran {@code /gtest}).  Each tick it executes the current
 * {@link TestStep}, interprets the {@link StepResult}, and either stays on the step,
 * moves to the next one, or finalises the test run and removes the component.
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
            component.results.add("FAIL  " + currentTest.getName()
                    + " [step " + component.stepIndex + "]: " + result.getFailReason());
            advanceTest(component);
        } else {
            // DONE — advance to next step.
            component.stepIndex++;
            component.ticksRemaining = -1;

            if (component.stepIndex >= steps.size()) {
                // All steps passed.
                component.results.add("PASS  " + currentTest.getName());
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
        Store<ChunkStore> store = chunkStore.getStore();
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunkRef == null || !chunkRef.isValid()) return;
        ChunkColumn column = store.getComponent(chunkRef, ChunkColumn.getComponentType());
        if (column == null) return;
        Ref<ChunkStore> sectionRef = column.getSection(ChunkUtil.chunkCoordinate(y));
        if (sectionRef == null) return;
        FluidSection fs = store.getComponent(sectionRef, FluidSection.getComponentType());
        if (fs == null) return;
        fs.setFluid(x, y, z, 0, (byte) 0);
    }

    private static void reportAndCleanup(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull TestRunnerComponent component,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        PlayerRef player = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());

        if (player != null) {
            long passed = component.results.stream().filter(r -> r.startsWith("PASS")).count();
            long total  = component.results.size();

            StringBuilder sb = new StringBuilder();
            sb.append("[GlyphTest] ").append(passed).append("/").append(total).append(" passed\n");
            for (String r : component.results) {
                sb.append("  ").append(r).append("\n");
            }
            String report = sb.toString().stripTrailing();
            player.sendMessage(Message.raw(report));
            LOGGER.info(report);
        }

        commandBuffer.removeComponent(ref, TestRunnerComponent.getComponentType());
    }
}

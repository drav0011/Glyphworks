package dev.drav.glyphworks.crafting.tests.component;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.crafting.component.AutoProcessingBenchBlock;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Two-suite persistence test for {@link AutoProcessingBenchBlock}.
 *
 * <p>
 * Writes a non-default {@code manaConsumptionRate} in the setup phase, then
 * verifies the value survives a server restart in the assert phase.
 *
 * <pre>
 *   # Phase 1
 *   $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.persistence.phase=setup -Dglyphworks.test.persistence.suite=crafting_auto_processing_bench_block_persistence_setup"
 *   ./gradlew runServer
 *
 *   # Phase 2
 *   $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.persistence.phase=assert -Dglyphworks.test.persistence.suite=crafting_auto_processing_bench_block_persistence_assert"
 *   ./gradlew runServer
 * </pre>
 */
public final class AutoProcessingBenchBlockTests {

    private static final String FURNACE_ID = "Glyphworks_Crafting_Bench_Furnace";

    /** Written during setup — intentionally different from the JSON default of 25.0. */
    private static final float WRITTEN_RATE = 50.0f;

    private AutoProcessingBenchBlockTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSetupSuite());
        TestRegistry.register(moduleId, buildAssertSuite());
    }

    // ── Setup suite ───────────────────────────────────────────────────────────

    private static TestSuite buildSetupSuite() {
        return new TestSuite("crafting_auto_processing_bench_block_persistence_setup")
                .persistence("setup")
                .test(setupComponentState());
    }

    private static TestCase setupComponentState() {
        return new TestCase("auto_processing_bench_block_persists_rate", 5, 5, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    w.setBlock(x, y, z, FURNACE_ID);
                    PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
                }))
                .step(Steps.waitUntil(ctx -> {
                    return getComponent(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null;
                }, ctx -> 5 * ctx.getWorld().getTps(), "AutoProcessingBenchBlock initialised"))
                .step(Steps.run(ctx -> {
                    AutoProcessingBenchBlock apbb = getComponent(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (apbb != null) {
                        apbb.setManaConsumptionRate(WRITTEN_RATE);
                    }
                }))
                .step(Steps.assertThat(ctx -> {
                    AutoProcessingBenchBlock apbb = getComponent(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return apbb != null && apbb.getManaConsumptionRate() == WRITTEN_RATE;
                }, "baseline: manaConsumptionRate == " + WRITTEN_RATE + " before server stop"));
    }

    // ── Assert suite ──────────────────────────────────────────────────────────

    private static TestSuite buildAssertSuite() {
        return new TestSuite("crafting_auto_processing_bench_block_persistence_assert")
                .persistence("assert")
                .test(assertComponentState());
    }

    private static TestCase assertComponentState() {
        return new TestCase("auto_processing_bench_block_persists_rate", 5, 5, 5)
                .step(Steps.waitUntil(ctx -> {
                    return getComponent(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null;
                }, ctx -> 5 * ctx.getWorld().getTps(), "AutoProcessingBenchBlock reloaded after restart"))
                .step(Steps.assertThat(ctx -> {
                    AutoProcessingBenchBlock apbb = getComponent(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return apbb != null && apbb.getManaConsumptionRate() == WRITTEN_RATE;
                }, "manaConsumptionRate persists across server restart: expected " + WRITTEN_RATE));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static AutoProcessingBenchBlock getComponent(World w, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(w.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return w.getChunkStore().getStore().getComponent(lu.blockRef(),
                AutoProcessingBenchBlock.getComponentType());
    }
}

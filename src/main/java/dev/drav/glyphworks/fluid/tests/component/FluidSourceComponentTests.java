package dev.drav.glyphworks.fluid.tests.component;

import com.hypixel.hytale.math.vector.Vector3i;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.fluid.component.FluidSourceComponent;
import dev.drav.glyphworks.fluid.event.FluidItemRegistry;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Two-suite persistence test for {@link FluidSourceComponent}.
 *
 * <pre>
 *   # Phase 1
 *   $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.persistence.phase=setup -Dglyphworks.test.persistence.suite=fluid_source_persistence_setup"
 *   ./gradlew runServer
 *
 *   # Phase 2
 *   $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.persistence.phase=assert -Dglyphworks.test.persistence.suite=fluid_source_persistence_assert"
 *   ./gradlew runServer
 * </pre>
 */
public final class FluidSourceComponentTests {

    private static final String SOURCE_ID   = "Glyphworks_Fluid_Source";
    private static final String CHANGED_FLUID = "Lava_Source";

    private FluidSourceComponentTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSetupSuite());
        TestRegistry.register(moduleId, buildAssertSuite());
    }

    // ── Setup suite ───────────────────────────────────────────────────────────

    private static TestSuite buildSetupSuite() {
        return new TestSuite("fluid_source_persistence_setup")
                .persistence("setup")
                .test(setupSourceState());
    }

    private static TestCase setupSourceState() {
        return new TestCase("fluid_source_persists_selector", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    ctx.getWorld().setBlock(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), SOURCE_ID);
                }))
                .step(Steps.waitUntil(ctx -> getSource(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null,
                        ctx -> 5 * ctx.getWorld().getTps(), "source block entity initialised"))
                .step(Steps.run(ctx -> {
                    FluidSourceComponent fsc = getSource(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (fsc != null) {
                        String itemId = FluidItemRegistry.resolveItemId(CHANGED_FLUID);
                        if (itemId != null) {
                            fsc.getSelectorContainer().setItemStackForSlot((short) 0, new ItemStack(itemId, 1, 1000, 1000, null), false);
                        }
                    }
                }))
                .step(Steps.assertThat(ctx -> {
                    FluidSourceComponent fsc = getSource(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return fsc != null && CHANGED_FLUID.equals(fsc.getSelectedFluidId());
                }, "baseline: selector resolves to " + CHANGED_FLUID + " before server stop"));
    }

    // ── Assert suite ──────────────────────────────────────────────────────────

    private static TestSuite buildAssertSuite() {
        return new TestSuite("fluid_source_persistence_assert")
                .persistence("assert")
                .test(assertSourceState());
    }

    private static TestCase assertSourceState() {
        return new TestCase("fluid_source_persists_selector", 3, 3, 3)
                .step(Steps.waitUntil(ctx -> getSource(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null,
                        ctx -> 5 * ctx.getWorld().getTps(), "source reloaded after restart"))
                .step(Steps.assertThat(ctx -> {
                    FluidSourceComponent fsc = getSource(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return fsc != null && CHANGED_FLUID.equals(fsc.getSelectedFluidId());
                }, "FluidSourceComponent persists selector container across server restart"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static FluidSourceComponent getSource(World world, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(world.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return world.getChunkStore().getStore().getComponent(lu.blockRef(), FluidSourceComponent.getComponentType());
    }
}

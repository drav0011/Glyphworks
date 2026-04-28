package dev.drav.glyphworks.fluid.tests.component;

import com.hypixel.hytale.math.vector.Vector3i;

import dev.drav.glyphworks.fluid.component.FluidPipeComponent;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Two-suite persistence test for {@link FluidPipeComponent}.
 *
 * <pre>
 *   # Phase 1
 *   $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.persistence.phase=setup -Dglyphworks.test.persistence.suite=fluid_pipe_persistence_setup"
 *   ./gradlew runServer
 *
 *   # Phase 2
 *   $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.persistence.phase=assert -Dglyphworks.test.persistence.suite=fluid_pipe_persistence_assert"
 *   ./gradlew runServer
 * </pre>
 */
public final class FluidPipeComponentTests {

    private static final String PIPE_ID  = "Glyphworks_Fluid_Pipe";
    private static final String FLUID_ID = "Mana_Source";

    private FluidPipeComponentTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSetupSuite());
        TestRegistry.register(moduleId, buildAssertSuite());
    }

    // ── Setup suite ───────────────────────────────────────────────────────────

    private static TestSuite buildSetupSuite() {
        return new TestSuite("fluid_pipe_persistence_setup")
                .persistence("setup")
                .test(setupPipeState());
    }

    private static TestCase setupPipeState() {
        return new TestCase("fluid_pipe_persists_fluid_id", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    ctx.getWorld().setBlock(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), PIPE_ID);
                }))
                .step(Steps.waitUntil(ctx -> getPipe(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null,
                        ctx -> 5 * ctx.getWorld().getTps(), "pipe block entity initialised"))
                .step(Steps.run(ctx -> {
                    FluidPipeComponent fpc = getPipe(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (fpc != null)
                        fpc.setFluidId(FLUID_ID);
                }))
                .step(Steps.assertThat(ctx -> {
                    FluidPipeComponent fpc = getPipe(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return fpc != null && FLUID_ID.equals(fpc.getFluidId());
                }, "baseline: pipe fluidId is " + FLUID_ID + " before server stop"));
    }

    // ── Assert suite ──────────────────────────────────────────────────────────

    private static TestSuite buildAssertSuite() {
        return new TestSuite("fluid_pipe_persistence_assert")
                .persistence("assert")
                .test(assertPipeState());
    }

    private static TestCase assertPipeState() {
        return new TestCase("fluid_pipe_persists_fluid_id", 3, 3, 3)
                .step(Steps.waitUntil(ctx -> getPipe(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null,
                        ctx -> 5 * ctx.getWorld().getTps(), "pipe reloaded after restart"))
                .step(Steps.assertThat(ctx -> {
                    FluidPipeComponent fpc = getPipe(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return fpc != null && FLUID_ID.equals(fpc.getFluidId());
                }, "FluidPipeComponent persists fluidId across server restart"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static FluidPipeComponent getPipe(com.hypixel.hytale.server.core.universe.world.World world, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(world.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return world.getChunkStore().getStore().getComponent(lu.blockRef(), FluidPipeComponent.getComponentType());
    }
}

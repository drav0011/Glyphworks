package dev.drav.glyphworks.fluid.tests;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.util.thread.TickingThread;

import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.test.Steps;
import dev.drav.glyphworks.test.TestCase;
import dev.drav.glyphworks.test.TestRegistry;
import dev.drav.glyphworks.test.TestSuite;

/**
 * Suite {@code "fluid_sink_system"} — integration tests for
 * {@link dev.drav.glyphworks.fluid.system.FluidSinkSystem}.
 *
 * <p>
 * Each tick the system zeroes the container on every {@code Test_Fluid_Sink}
 * block, simulating an infinite drain with no back-pressure.
 */
public final class FluidSinkSystemTests {

    private static final String SINK_ID = "Glyphworks_Fluid_Sink";
    private static final String FLUID_ID = "Water_Source";
    private static final int WAIT_TICKS = 2 * TickingThread.TPS;

    private FluidSinkSystemTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("fluid_sink_system")
                .test(sinkDrainsContainer())
                .test(sinkIsIdempotentWhenEmpty());
    }

    // -------------------------------------------------------------------------
    // Test 1: sink zeroes amount and clears fluid lock each tick
    // -------------------------------------------------------------------------

    private static TestCase sinkDrainsContainer() {
        return new TestCase("sink_drains_container", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    ctx.getWorld().setBlock(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), SINK_ID);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.run(ctx -> {
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    if (fcc != null)
                        fcc.fill(FLUID_ID, fcc.getCapacity());
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    return fcc != null && fcc.getAmount() == 0 && fcc.getFluidId() == null;
                }, "FluidSinkSystem zeroes the container amount and clears the fluid lock each tick"));
    }

    // -------------------------------------------------------------------------
    // Test 2: sink is a no-op when container is already empty
    // -------------------------------------------------------------------------

    private static TestCase sinkIsIdempotentWhenEmpty() {
        return new TestCase("sink_is_idempotent_when_empty", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    ctx.getWorld().setBlock(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), SINK_ID);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    return fcc != null && fcc.isEmpty();
                }, "FluidSinkSystem is a no-op when the container is already empty"));
    }
}

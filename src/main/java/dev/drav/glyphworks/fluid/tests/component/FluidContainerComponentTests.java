package dev.drav.glyphworks.fluid.tests.component;

import org.joml.Vector3i;

import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.tests.FluidTestUtil;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Two-suite persistence test for {@link FluidContainerComponent}.
 *
 * <p>
 * Run the setup suite in phase 1 to write known state, then run the assert
 * suite in phase 2 (after a server restart) to confirm persistence.
 *
 * <pre>
 *   # Phase 1
 *   $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.persistence.phase=setup -Dglyphworks.test.persistence.suite=fluid_container_persistence_setup"
 *   ./gradlew runServer
 *
 *   # Phase 2
 *   $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.persistence.phase=assert -Dglyphworks.test.persistence.suite=fluid_container_persistence_assert"
 *   ./gradlew runServer
 * </pre>
 */
public final class FluidContainerComponentTests {

    private static final String TANK_ID  = "Glyphworks_Fluid_Tank";
    private static final String FLUID_ID = "Mana_Source";
    private static final int    AMOUNT   = 750;

    private FluidContainerComponentTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSetupSuite());
        TestRegistry.register(moduleId, buildAssertSuite());
    }

    // ── Setup suite ───────────────────────────────────────────────────────────

    private static TestSuite buildSetupSuite() {
        return new TestSuite("fluid_container_persistence_setup")
                .persistence("setup")
                .test(setupContainerState());
    }

    private static TestCase setupContainerState() {
        return new TestCase("fluid_container_persists_state", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    ctx.getWorld().setBlock(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), TANK_ID);
                }))
                .step(Steps.waitUntil(ctx -> {
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    return fcc != null;
                }, ctx -> 5 * ctx.getWorld().getTps(), "tank block entity initialised"))
                .step(Steps.run(ctx -> {
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    if (fcc != null) {
                        int slotCapacity = fcc.getFluidContainer().getCapacityMbPerSlot();
                        FluidStack stack = new FluidStack(FLUID_ID, AMOUNT);
                        fcc.getFluidContainer().addFluidStackToSlot((short) 0, stack, true, false);
                    }
                }))
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    if (fcc == null)
                        return false;
                    int amount = 0;
                    for (short slot = 0; slot < fcc.getFluidContainer().getCapacity(); slot++) {
                        FluidStack stack = fcc.getFluidContainer().getFluidStack(slot);
                        if (stack != null && FLUID_ID.equals(stack.getFluidId())) {
                            amount += stack.getAmount();
                        }
                    }
                    return amount == AMOUNT;
                }, "baseline: container holds " + AMOUNT + "L of " + FLUID_ID + " before server stop"));
    }

    // ── Assert suite ──────────────────────────────────────────────────────────

    private static TestSuite buildAssertSuite() {
        return new TestSuite("fluid_container_persistence_assert")
                .persistence("assert")
                .test(assertContainerState());
    }

    private static TestCase assertContainerState() {
        return new TestCase("fluid_container_persists_state", 3, 3, 3)
                .step(Steps.waitUntil(ctx -> {
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    return fcc != null;
                }, ctx -> 5 * ctx.getWorld().getTps(), "container reloaded after restart"))
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    if (fcc == null)
                        return false;
                    int amount = 0;
                    for (short slot = 0; slot < fcc.getFluidContainer().getCapacity(); slot++) {
                        FluidStack stack = fcc.getFluidContainer().getFluidStack(slot);
                        if (stack != null && FLUID_ID.equals(stack.getFluidId())) {
                            amount += stack.getAmount();
                        }
                    }
                    return amount == AMOUNT;
                }, "FluidContainerComponent persists amount and fluidId across server restart"));
    }
}


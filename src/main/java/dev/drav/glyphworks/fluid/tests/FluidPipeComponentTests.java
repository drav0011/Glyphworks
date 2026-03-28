package dev.drav.glyphworks.fluid.tests;

import dev.drav.glyphworks.fluid.component.FluidPipeComponent;
import dev.drav.glyphworks.test.Steps;
import dev.drav.glyphworks.test.TestCase;
import dev.drav.glyphworks.test.TestRegistry;
import dev.drav.glyphworks.test.TestSuite;

/**
 * Suite {@code "fluid_pipe"} — pure unit tests for {@link FluidPipeComponent}.
 *
 * <p>
 * Verifies the lock/accept logic and that {@link FluidPipeComponent#clone()}
 * always produces an uncontaminated pipe.
 */
public final class FluidPipeComponentTests {

    private FluidPipeComponentTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("fluid_pipe")
                .test(acceptsAnythingWhenUnlocked())
                .test(acceptsSameFluid())
                .test(rejectsDifferentFluid())
                .test(cloneIsAlwaysUncontaminated());
    }

    // -------------------------------------------------------------------------
    // Test 1: null lock accepts any fluid ID
    // -------------------------------------------------------------------------

    private static TestCase acceptsAnythingWhenUnlocked() {
        return new TestCase("accepts_anything_when_unlocked", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FluidPipeComponent pipe = new FluidPipeComponent();
                    return pipe.accepts("Water") && pipe.accepts("Lava") && pipe.accepts("Oil");
                }, "unlocked pipe (lockedFluidId==null) accepts any fluid ID"));
    }

    // -------------------------------------------------------------------------
    // Test 2: locked pipe accepts the matching fluid
    // -------------------------------------------------------------------------

    private static TestCase acceptsSameFluid() {
        return new TestCase("accepts_same_fluid", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FluidPipeComponent pipe = new FluidPipeComponent();
                    pipe.setLockedFluidId("Water");
                    return pipe.accepts("Water");
                }, "pipe locked to 'Water' accepts 'Water'"));
    }

    // -------------------------------------------------------------------------
    // Test 3: locked pipe rejects a different fluid
    // -------------------------------------------------------------------------

    private static TestCase rejectsDifferentFluid() {
        return new TestCase("rejects_different_fluid", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FluidPipeComponent pipe = new FluidPipeComponent();
                    pipe.setLockedFluidId("Water");
                    return !pipe.accepts("Lava");
                }, "pipe locked to 'Water' rejects 'Lava'"));
    }

    // -------------------------------------------------------------------------
    // Test 4: clone() always produces an uncontaminated (lockedFluidId==null) pipe
    // -------------------------------------------------------------------------

    private static TestCase cloneIsAlwaysUncontaminated() {
        return new TestCase("clone_is_always_uncontaminated", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FluidPipeComponent locked = new FluidPipeComponent();
                    locked.setLockedFluidId("Lava");
                    FluidPipeComponent clone = (FluidPipeComponent) locked.clone();
                    return clone != null && clone.getLockedFluidId() == null;
                }, "clone() of a locked pipe has lockedFluidId==null (new pipes start uncontaminated)"));
    }
}

package dev.drav.glyphworks.fluid.tests;

import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.test.Steps;
import dev.drav.glyphworks.test.TestCase;
import dev.drav.glyphworks.test.TestRegistry;
import dev.drav.glyphworks.test.TestSuite;

/**
 * Suite {@code "fluid_container"} — pure unit tests for {@link FluidContainerComponent}.
 *
 * <p>All tests instantiate the component directly (no ECS, no world) and verify
 * the fill/drain state machine, clamping, and clone behaviour.
 */
public final class FluidContainerComponentTests {

    private FluidContainerComponentTests() {}

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("fluid_container")
                .test(fillEmptyContainerSetsLock())
                .test(fillCappedByCapacity())
                .test(fillRejectedOnWrongFluid())
                .test(fillReturnsZeroWhenFull())
                .test(drainReducesAmount())
                .test(drainClearsLockWhenEmpty())
                .test(drainReturnsZeroWhenEmpty())
                .test(setAmountClampedToZero())
                .test(setAmountClampedToCapacity())
                .test(isEmptyTrueBaseCase())
                .test(isEmptyFalseWhenFilled())
                .test(availableSpaceCorrect())
                .test(cloneIsAlwaysEmpty());
    }

    // -------------------------------------------------------------------------
    // Test 1: first fill sets lockedFluidId and returns accepted liters
    // -------------------------------------------------------------------------

    private static TestCase fillEmptyContainerSetsLock() {
        return new TestCase("fill_empty_container_sets_lock", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent c = new FluidContainerComponent(5_000);
                    int accepted = c.fill("Water", 1_000);
                    return accepted == 1_000 && "Water".equals(c.getLockedFluidId()) && c.getAmount() == 1_000;
                }, "first fill() sets lockedFluidId and returns liters accepted"));
    }

    // -------------------------------------------------------------------------
    // Test 2: fill is capped by available capacity
    // -------------------------------------------------------------------------

    private static TestCase fillCappedByCapacity() {
        return new TestCase("fill_capped_by_capacity", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent c = new FluidContainerComponent(1_500);
                    c.fill("Water", 1_000);
                    int accepted = c.fill("Water", 1_000); // only 500 space remains
                    return accepted == 500 && c.getAmount() == 1_500;
                }, "fill() is capped by availableSpace() — returns actual accepted liters"));
    }

    // -------------------------------------------------------------------------
    // Test 3: fill rejected when locked to a different fluid
    // -------------------------------------------------------------------------

    private static TestCase fillRejectedOnWrongFluid() {
        return new TestCase("fill_rejected_on_wrong_fluid", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent c = new FluidContainerComponent(5_000);
                    c.fill("Water", 1_000);
                    int accepted = c.fill("Lava", 1_000);
                    return accepted == 0 && c.getAmount() == 1_000 && "Water".equals(c.getLockedFluidId());
                }, "fill() returns 0 and leaves state unchanged when locked to a different fluid"));
    }

    // -------------------------------------------------------------------------
    // Test 4: fill returns 0 when container is already full
    // -------------------------------------------------------------------------

    private static TestCase fillReturnsZeroWhenFull() {
        return new TestCase("fill_returns_zero_when_full", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent c = new FluidContainerComponent(1_000);
                    c.fill("Water", 1_000);
                    int accepted = c.fill("Water", 1_000);
                    return accepted == 0 && c.getAmount() == 1_000;
                }, "fill() returns 0 when availableSpace() == 0"));
    }

    // -------------------------------------------------------------------------
    // Test 5: drain reduces amount by the drained quantity
    // -------------------------------------------------------------------------

    private static TestCase drainReducesAmount() {
        return new TestCase("drain_reduces_amount", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent c = new FluidContainerComponent(5_000);
                    c.fill("Water", 3_000);
                    int removed = c.drain(1_000);
                    return removed == 1_000 && c.getAmount() == 2_000;
                }, "drain() reduces amount by the number of liters drained and returns that count"));
    }

    // -------------------------------------------------------------------------
    // Test 6: draining to zero clears lockedFluidId
    // -------------------------------------------------------------------------

    private static TestCase drainClearsLockWhenEmpty() {
        return new TestCase("drain_clears_lock_when_empty", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent c = new FluidContainerComponent(1_000);
                    c.fill("Water", 1_000);
                    c.drain(1_000);
                    return c.getAmount() == 0 && c.getLockedFluidId() == null;
                }, "drain() clears lockedFluidId automatically when container reaches zero"));
    }

    // -------------------------------------------------------------------------
    // Test 7: drain returns 0 on an already-empty container
    // -------------------------------------------------------------------------

    private static TestCase drainReturnsZeroWhenEmpty() {
        return new TestCase("drain_returns_zero_when_empty", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent c = new FluidContainerComponent(1_000);
                    int removed = c.drain(500);
                    return removed == 0 && c.getAmount() == 0;
                }, "drain() returns 0 on an empty container"));
    }

    // -------------------------------------------------------------------------
    // Test 8: setAmount clamps negative values to 0
    // -------------------------------------------------------------------------

    private static TestCase setAmountClampedToZero() {
        return new TestCase("set_amount_clamped_to_zero", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent c = new FluidContainerComponent(1_000);
                    c.setAmount(-500);
                    return c.getAmount() == 0;
                }, "setAmount() stores 0 for negative input"));
    }

    // -------------------------------------------------------------------------
    // Test 9: setAmount clamps over-capacity values to capacity
    // -------------------------------------------------------------------------

    private static TestCase setAmountClampedToCapacity() {
        return new TestCase("set_amount_clamped_to_capacity", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent c = new FluidContainerComponent(1_000);
                    c.setAmount(9_999);
                    return c.getAmount() == 1_000;
                }, "setAmount() stores capacity for over-capacity input"));
    }

    // -------------------------------------------------------------------------
    // Test 10: isEmpty() is true for a fresh container
    // -------------------------------------------------------------------------

    private static TestCase isEmptyTrueBaseCase() {
        return new TestCase("is_empty_true_base_case", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent c = new FluidContainerComponent(1_000);
                    return c.isEmpty();
                }, "fresh container: isEmpty() == true (amount==0 and lockedFluidId==null)"));
    }

    // -------------------------------------------------------------------------
    // Test 11: isEmpty() is false after any fill
    // -------------------------------------------------------------------------

    private static TestCase isEmptyFalseWhenFilled() {
        return new TestCase("is_empty_false_when_filled", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent c = new FluidContainerComponent(1_000);
                    c.fill("Water", 500);
                    return !c.isEmpty();
                }, "isEmpty() returns false after at least one liter is added"));
    }

    // -------------------------------------------------------------------------
    // Test 12: availableSpace() == capacity - amount
    // -------------------------------------------------------------------------

    private static TestCase availableSpaceCorrect() {
        return new TestCase("available_space_correct", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent c = new FluidContainerComponent(5_000);
                    c.fill("Water", 2_000);
                    return c.availableSpace() == 3_000;
                }, "availableSpace() == capacity - amount"));
    }

    // -------------------------------------------------------------------------
    // Test 13: clone() always produces an empty, unlocked container
    // -------------------------------------------------------------------------

    private static TestCase cloneIsAlwaysEmpty() {
        return new TestCase("clone_is_always_empty", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent filled = new FluidContainerComponent(5_000);
                    filled.fill("Water", 3_000);
                    FluidContainerComponent clone = (FluidContainerComponent) filled.clone();
                    return clone != null && clone.getAmount() == 0 && clone.getLockedFluidId() == null;
                }, "clone() of a filled+locked container has amount=0 and lockedFluidId=null"));
    }
}

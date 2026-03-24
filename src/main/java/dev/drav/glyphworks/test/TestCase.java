package dev.drav.glyphworks.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * A named sequence of {@link TestStep}s.
 *
 * <p>All steps are executed in order. If any step fails the test stops and
 * records a failure; all remaining steps in that test are skipped.
 *
 * <pre>{@code
 * new TestCase("placer_places_fluid")
 *     .step(Steps.run(ctx -> ctx.getWorld().getChunk(...).setBlock(...)))
 *     .step(Steps.wait(20))
 *     .step(Steps.assertThat(ctx -> ctx.getWorld().getChunk(...).getBlock(...) == waterId,
 *                            "water block present at target position"))
 * }</pre>
 */
public final class TestCase {

    private final String name;

    /** World-space bounding box the test needs — cleared to Empty before the test starts. */
    private final int areaWidth;
    private final int areaDepth;
    private final int areaHeight;

    private final List<TestStep> steps = new ArrayList<>();

    /**
     * @param name       unique test name within its suite
     * @param areaWidth  X extent (blocks) of the area this test requires
     * @param areaDepth  Z extent (blocks)
     * @param areaHeight Y extent (blocks, starting at the assigned origin Y)
     */
    public TestCase(@Nonnull String name, int areaWidth, int areaDepth, int areaHeight) {
        this.name       = name;
        this.areaWidth  = areaWidth;
        this.areaDepth  = areaDepth;
        this.areaHeight = areaHeight;
    }

    @Nonnull
    public TestCase step(@Nonnull TestStep step) {
        steps.add(step);
        return this;
    }

    @Nonnull public String getName()          { return name; }
    public int getAreaWidth()                 { return areaWidth; }
    public int getAreaDepth()                 { return areaDepth; }
    public int getAreaHeight()                { return areaHeight; }
    @Nonnull public List<TestStep> getSteps() { return Collections.unmodifiableList(steps); }
}

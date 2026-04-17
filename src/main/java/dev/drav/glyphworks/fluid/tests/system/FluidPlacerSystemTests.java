package dev.drav.glyphworks.fluid.tests.system;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.protocol.BlockFace;

import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.tests.FluidTestUtil;
import dev.drav.glyphworks.fluid.util.FluidUtil;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "fluid_placer_system"} — integration tests for
 * {@link dev.drav.glyphworks.fluid.system.FluidPlacerSystem}.
 *
 * <p>
 * The placer block is always placed at the centre of the 3×3×3 test area
 * {@code (ox+1, oy+1, oz+1)}. Direction tests use
 * {@link FluidTestUtil#setBlockWithRotation} to orient the block in each of
 * the 6 cardinal directions; the rotation index is derived from
 * {@link com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple}
 * so that {@code GridFaceUtil.rotateBlockFace(Down, rotation)} produces the
 * expected world-space face. Guard-logic tests use the default Down rotation.
 */
public final class FluidPlacerSystemTests {

    private static final String FLUID_ID = "Water_Source";
    private static final String BLOCK_ID = "Glyphworks_Fluid_Placer";
    private static final String SOLID_BLOCK_ID = "Rock_Stone";

    private FluidPlacerSystemTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("fluid_placer_system")
                // Direction tests — one per cardinal face
                .test(placerPlacesFluidToward(BlockFace.Down,  FluidTestUtil.ROTATION_DOWN))
                .test(placerPlacesFluidToward(BlockFace.Up,    FluidTestUtil.ROTATION_UP))
                .test(placerPlacesFluidToward(BlockFace.North, FluidTestUtil.ROTATION_NORTH))
                .test(placerPlacesFluidToward(BlockFace.South, FluidTestUtil.ROTATION_SOUTH))
                .test(placerPlacesFluidToward(BlockFace.East,  FluidTestUtil.ROTATION_EAST))
                .test(placerPlacesFluidToward(BlockFace.West,  FluidTestUtil.ROTATION_WEST))
                // Guard-logic tests (direction-independent — tested with Down)
                .test(placerSkipsWhenBelowThreshold())
                .test(placerSkipsWhenTargetOccupied())
                .test(placerSkipsWhenSolidBlockAtTarget());
    }

    // -------------------------------------------------------------------------
    // Direction tests: one TestCase per cardinal face
    //
    // Layout: block at centre of 3×3×3 area (ox+1, oy+1, oz+1);
    //         target cell is one step in the given direction from the block.
    // -------------------------------------------------------------------------

    private static TestCase placerPlacesFluidToward(BlockFace direction, int rotationIndex) {
        Vector3i faceOffset = FluidTestUtil.targetPos(0, 0, 0, direction);
        String name = "placer_places_fluid_toward_" + direction.name().toLowerCase();
        return new TestCase(name, 3, 3, 3)
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    FluidTestUtil.setBlockWithRotation(ctx.getWorld(), bx, by, bz, BLOCK_ID, rotationIndex);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    if (fcc != null)
                        fcc.fill(FLUID_ID, FluidUtil.LITERS_PER_BLOCK);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    int tx = bx + faceOffset.x, ty = by + faceOffset.y, tz = bz + faceOffset.z;
                    int expectedId = Fluid.getAssetMap().getIndex(FLUID_ID);
                    if (FluidTestUtil.getFluidId(ctx.getWorld(), tx, ty, tz) != expectedId)
                        return false;
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    return fcc != null && fcc.getAmount() == 0;
                }, "FluidPlacerSystem drains 1 000 L and places fluid " + direction.name().toLowerCase()));
    }

    // -------------------------------------------------------------------------
    // Guard-logic tests (Down direction, block at centre of area)
    // -------------------------------------------------------------------------

    private static TestCase placerSkipsWhenBelowThreshold() {
        return new TestCase("placer_skips_when_below_threshold", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    // Rotation 0 = Down; container left at 0 L — system must skip.
                    FluidTestUtil.setBlockWithRotation(ctx.getWorld(), bx, by, bz, BLOCK_ID,
                            FluidTestUtil.ROTATION_DOWN);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    int tx = ctx.getOriginX() + 1, ty = ctx.getOriginY(), tz = ctx.getOriginZ() + 1;
                    return FluidTestUtil.getFluidId(ctx.getWorld(), tx, ty, tz) == 0;
                }, "FluidPlacerSystem does not place fluid when container holds less than 1 000 L"));
    }

    private static TestCase placerSkipsWhenTargetOccupied() {
        return new TestCase("placer_skips_when_target_occupied", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    FluidTestUtil.setBlockWithRotation(ctx.getWorld(), bx, by, bz, BLOCK_ID,
                            FluidTestUtil.ROTATION_DOWN);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    FluidTestUtil.placeFluid(ctx.getWorld(), bx, by - 1, bz, FLUID_ID);
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    if (fcc != null)
                        fcc.fill(FLUID_ID, FluidUtil.LITERS_PER_BLOCK);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    return fcc != null && fcc.getAmount() == FluidUtil.LITERS_PER_BLOCK;
                }, "FluidPlacerSystem does not drain container when target cell is already occupied"));
    }

    private static TestCase placerSkipsWhenSolidBlockAtTarget() {
        return new TestCase("placer_skips_when_solid_block_at_target", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    FluidTestUtil.setBlockWithRotation(ctx.getWorld(), bx, by, bz, BLOCK_ID,
                            FluidTestUtil.ROTATION_DOWN);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ctx.getWorld().setBlock(bx, by - 1, bz, SOLID_BLOCK_ID);
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    if (fcc != null)
                        fcc.fill(FLUID_ID, FluidUtil.LITERS_PER_BLOCK);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    return fcc != null && fcc.getAmount() == FluidUtil.LITERS_PER_BLOCK;
                }, "FluidPlacerSystem does not drain container when target cell contains a solid block"));
    }
}


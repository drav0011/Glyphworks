package dev.drav.glyphworks.fluid.tests.system;

import org.joml.Vector3i;

import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.tests.FluidTestUtil;
import dev.drav.glyphworks.fluid.util.FluidUtil;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "fluid_remover_system"} — integration tests for
 * {@link dev.drav.glyphworks.fluid.system.FluidRemoverSystem}.
 *
 * <p>
 * The remover block is always placed at the centre of the 3×3×3 test area
 * {@code (ox+1, oy+1, oz+1)}. Direction tests use
 * {@link FluidTestUtil#setBlockWithRotation} to orient the block in each of
 * the 6 cardinal directions. Guard-logic tests use the default Down rotation.
 */
public final class FluidRemoverSystemTests {

    private static final String FLUID_ID = "Water_Source";
    private static final String BLOCK_ID = "Glyphworks_Fluid_Remover";
    private static final String PIPE_ID = "Glyphworks_Fluid_Pipe";
    private static final String TANK_ID = "Glyphworks_Fluid_Tank";

    private FluidRemoverSystemTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("fluid_remover_system")
                // Direction tests — one per cardinal face
                .test(removerPicksUpFluidFrom(BlockFace.Down, FluidTestUtil.ROTATION_DOWN))
                .test(removerPicksUpFluidFrom(BlockFace.Up, FluidTestUtil.ROTATION_UP))
                .test(removerPicksUpFluidFrom(BlockFace.North, FluidTestUtil.ROTATION_NORTH))
                .test(removerPicksUpFluidFrom(BlockFace.South, FluidTestUtil.ROTATION_SOUTH))
                .test(removerPicksUpFluidFrom(BlockFace.East, FluidTestUtil.ROTATION_EAST))
                .test(removerPicksUpFluidFrom(BlockFace.West, FluidTestUtil.ROTATION_WEST))
                // Guard-logic tests (direction-independent — tested with Down)
                .test(removerSkipsWhenCellEmpty())
                .test(removerSkipsWhenContainerFull())
                .test(removerDoesNotCreateFluidWithoutWorldSource())
                .test(removerTransfersAllFluidToTank());
    }

    // -------------------------------------------------------------------------
    // Direction tests: one TestCase per cardinal face
    //
    // Layout: block at centre of 3×3×3 area (ox+1, oy+1, oz+1);
    // fluid is placed one step in the given direction from the block.
    // -------------------------------------------------------------------------

    private static TestCase removerPicksUpFluidFrom(BlockFace direction, int rotationIndex) {
        Vector3i faceOffset = FluidTestUtil.targetPos(0, 0, 0, direction);
        String name = "remover_picks_up_fluid_from_" + direction.name().toLowerCase();
        return new TestCase(name, 3, 3, 3)
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    FluidTestUtil.setBlockWithRotation(ctx.getWorld(), bx, by, bz, BLOCK_ID, rotationIndex);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    int tx = bx + faceOffset.x, ty = by + faceOffset.y, tz = bz + faceOffset.z;
                    FluidTestUtil.placeFluid(ctx.getWorld(), tx, ty, tz, FLUID_ID);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    int tx = bx + faceOffset.x, ty = by + faceOffset.y, tz = bz + faceOffset.z;
                    // World cell must be cleared.
                    if (FluidTestUtil.getFluidId(w, tx, ty, tz) != 0)
                        return false;
                    // Container must hold exactly 1 000 mB of the correct fluid in slot 0.
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(w, new Vector3i(bx, by, bz));
                    if (fcc == null)
                        return false;
                    FluidStack slot0 = fcc.getFluidContainer().getFluidStack((short) 0);
                    return slot0 != null
                            && FLUID_ID.equals(slot0.getFluidId())
                            && slot0.getQuantity() == FluidUtil.MB_PER_BLOCK;
                }, "FluidRemoverSystem picks up fluid from " + direction.name().toLowerCase()
                        + " and fills container"));
    }

    // -------------------------------------------------------------------------
    // Guard-logic tests (Down direction, block at centre of area)
    // -------------------------------------------------------------------------

    private static TestCase removerSkipsWhenCellEmpty() {
        return new TestCase("remover_skips_when_cell_empty", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    // Target cell is air — no fluid to pick up.
                    FluidTestUtil.setBlockWithRotation(ctx.getWorld(), bx, by, bz, BLOCK_ID,
                            FluidTestUtil.ROTATION_DOWN);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    return fcc != null && fcc.getFluidContainer().getFluidStack((short) 0) == null;
                }, "FluidRemoverSystem leaves container empty when target cell has no fluid"));
    }

    private static TestCase removerDoesNotCreateFluidWithoutWorldSource() {
        return new TestCase("remover_does_not_create_fluid_without_world_source", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int rx = ctx.getOriginX() + 1, ry = ctx.getOriginY() + 1, rz = ctx.getOriginZ() + 1;
                    w.setBlock(rx, ry, rz, BLOCK_ID);
                    w.setBlock(rx, ry + 1, rz, PIPE_ID);
                    w.setBlock(rx, ry + 1, rz + 1, TANK_ID);
                    PlaceGridBlockEvent.connectBlock(w, new Vector3i(rx, ry, rz));
                    PlaceGridBlockEvent.connectBlock(w, new Vector3i(rx, ry + 1, rz));
                    PlaceGridBlockEvent.connectBlock(w, new Vector3i(rx, ry + 1, rz + 1));
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    int rx = ctx.getOriginX() + 1, ry = ctx.getOriginY() + 1, rz = ctx.getOriginZ() + 1;
                    FluidContainerComponent tankFcc = FluidTestUtil.getContainer(
                            ctx.getWorld(), new Vector3i(rx, ry + 1, rz + 1));
                    return tankFcc != null && tankFcc.getFluidContainer().getFluidStack((short) 0) == null;
                }, "fluid remover does not fill the tank when no world fluid is present"));
    }

    private static TestCase removerTransfersAllFluidToTank() {
        return new TestCase("remover_transfers_all_fluid_to_tank", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int rx = ctx.getOriginX() + 1, ry = ctx.getOriginY() + 1, rz = ctx.getOriginZ() + 1;
                    w.setBlock(rx, ry, rz, BLOCK_ID);
                    w.setBlock(rx, ry + 1, rz, PIPE_ID);
                    w.setBlock(rx, ry + 1, rz + 1, TANK_ID);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int rx = ctx.getOriginX() + 1, ry = ctx.getOriginY() + 1, rz = ctx.getOriginZ() + 1;
                    PlaceGridBlockEvent.connectBlock(w, new Vector3i(rx, ry, rz));
                    PlaceGridBlockEvent.connectBlock(w, new Vector3i(rx, ry + 1, rz));
                    PlaceGridBlockEvent.connectBlock(w, new Vector3i(rx, ry + 1, rz + 1));
                    FluidTestUtil.placeFluid(w, rx, ry - 1, rz, FLUID_ID);
                }))
                .step(Steps.waitUntil(ctx -> {
                    int rx = ctx.getOriginX() + 1, ry = ctx.getOriginY() + 1, rz = ctx.getOriginZ() + 1;
                    FluidContainerComponent removerFcc = FluidTestUtil.getContainer(
                            ctx.getWorld(), new Vector3i(rx, ry, rz));
                    FluidStack slot0 = removerFcc != null ? removerFcc.getFluidContainer().getFluidStack((short) 0) : null;
                    return slot0 != null && slot0.getQuantity() > 0;
                }, ctx -> 5 * ctx.getWorld().getTps(), "remover picks up world fluid into container"))
                .step(Steps.waitUntil(ctx -> {
                    int rx = ctx.getOriginX() + 1, ry = ctx.getOriginY() + 1, rz = ctx.getOriginZ() + 1;
                    FluidContainerComponent removerFcc = FluidTestUtil.getContainer(
                            ctx.getWorld(), new Vector3i(rx, ry, rz));
                    FluidContainerComponent tankFcc = FluidTestUtil.getContainer(
                            ctx.getWorld(), new Vector3i(rx, ry + 1, rz + 1));
                    FluidStack removerSlot0 = removerFcc != null ? removerFcc.getFluidContainer().getFluidStack((short) 0) : null;
                    int tankTotal = 0;
                    if (tankFcc != null) {
                        for (short slot = 0; slot < tankFcc.getFluidContainer().getCapacity(); slot++) {
                            FluidStack stack = tankFcc.getFluidContainer().getFluidStack(slot);
                            if (stack != null)
                                tankTotal += stack.getQuantity();
                        }
                    }
                    return removerSlot0 == null && tankFcc != null && tankTotal == FluidUtil.MB_PER_BLOCK;
                }, ctx -> 10 * ctx.getWorld().getTps(),
                        "remover drains to zero and tank holds all " + FluidUtil.MB_PER_BLOCK + " mB"));
    }

    private static TestCase removerSkipsWhenContainerFull() {
        return new TestCase("remover_skips_when_container_full", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    FluidTestUtil.setBlockWithRotation(ctx.getWorld(), bx, by, bz, BLOCK_ID,
                            FluidTestUtil.ROTATION_DOWN);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    // Fill the container to capacity — system must skip.
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    if (fcc != null) {
                        FluidStack full = new FluidStack(FLUID_ID,
                                fcc.getFluidContainer().getCapacityMbPerSlot(),
                                fcc.getFluidContainer().getCapacityMbPerSlot());
                        fcc.getFluidContainer().addFluidStackToSlot((short) 0, full, true, false);
                    }
                    // Place fluid at the Down target cell.
                    FluidTestUtil.placeFluid(ctx.getWorld(), bx, by - 1, bz, FLUID_ID);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    // World fluid must still be present — system did not consume it.
                    int expectedId = Fluid.getAssetMap().getIndex(FLUID_ID);
                    return FluidTestUtil.getFluidId(ctx.getWorld(), bx, by - 1, bz) == expectedId;
                }, "FluidRemoverSystem does not consume world fluid when container has no available space"));
    }
}


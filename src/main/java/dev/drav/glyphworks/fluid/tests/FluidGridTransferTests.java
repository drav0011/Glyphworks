package dev.drav.glyphworks.fluid.tests;

import javax.annotation.Nullable;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.thread.TickingThread;

import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.component.FluidPipeComponent;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.test.Steps;
import dev.drav.glyphworks.test.TestCase;
import dev.drav.glyphworks.test.TestRegistry;
import dev.drav.glyphworks.test.TestSuite;

/**
 * Suite {@code "fluid_grid_transfer"} — integration tests for
 * {@link dev.drav.glyphworks.transfer.fluid.FluidGridTypeHandler}.
 *
 * <p>All tests use a flat East-West layout (positions relative to origin):
 * <pre>
 *   Test_Fluid_Source(0) → Fluid_Pipe(1) → Fluid_Pipe(2) → Test_Fluid_Sink(3)
 * </pre>
 * {@code Test_Fluid_Source} has a single OUTPUT East face (containerKey="tank")
 * so fluid can only flow <em>out</em> of it.  {@code Test_Fluid_Sink} has a
 * single INPUT West face (containerKey="tank") so fluid can only flow
 * <em>in</em>.  This eliminates the bidirectional oscillation that would
 * otherwise occur between two BIDIR tank blocks.
 */
public final class FluidGridTransferTests {

    private static final String FLUID_ID       = "Water_Source";
    private static final String SOURCE_ID      = "Test_Fluid_Source";
    private static final String SINK_ID        = "Test_Fluid_Sink";
    private static final String PIPE_ID        = "Fluid_Pipe";

    /** Used to pre-lock a pipe to an incompatible fluid type in test 3. */
    private static final String OTHER_FLUID_ID = "Lava_Source";

    /** 3 s — sufficient for the transfer handler to drain FILL_AMOUNT at 25 L/tick. */
    private static final int WAIT_TICKS  = 3 * TickingThread.TPS;

    /**
     * Amount pre-loaded into the source.  500 L / 25 L·tick⁻¹ = 20 ticks
     * to drain — well within {@link #WAIT_TICKS}.
     */
    private static final int FILL_AMOUNT = 500;

    /** Full capacity of the test sink block (from Test_Fluid_Sink.json). */
    private static final int SINK_CAPACITY = 2_000;

    private FluidGridTransferTests() {}

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("fluid_grid_transfer")
                .test(tankTransfersFluidViaPipes())
                .test(pipeLocksTsFluidAfterTransfer())
                .test(pipeBlocksIncompatibleFluid())
                .test(noTransferWhenSinkIsFull());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Places {@code blockId} at the given coordinates and registers grid connections. */
    private static void place(World world, int x, int y, int z, String blockId) {
        world.setBlock(x, y, z, blockId);
        PlaceGridBlockEvent.connectBlock(world, new Vector3i(x, y, z));
    }

    /**
     * Places the standard 4-block layout along the X axis:
     * <pre>
     *   Source(ox) → Pipe(ox+1) → Pipe(ox+2) → Sink(ox+3)
     * </pre>
     */
    private static void placeLayout(World world, int ox, int oy, int oz) {
        place(world, ox,     oy, oz, SOURCE_ID); // OUTPUT East → Pipe1
        place(world, ox + 1, oy, oz, PIPE_ID);   // relay
        place(world, ox + 2, oy, oz, PIPE_ID);   // relay
        place(world, ox + 3, oy, oz, SINK_ID);   // INPUT West ← Pipe2
    }

    /**
     * Returns the {@link FluidContainerComponent} for the grid block at {@code pos},
     * or {@code null} when the lookup fails.
     */
    @Nullable
    private static FluidContainerComponent getContainer(World world, Vector3i pos) {
        GridLookup lu = GridLookup.resolve(world.getChunkStore(), pos);
        if (lu == null) return null;
        return world.getChunkStore().getStore()
                .getComponent(lu.blockRef(), FluidContainerComponent.getComponentType());
    }

    /**
     * Returns the {@link FluidPipeComponent} for the grid block at {@code pos},
     * or {@code null} when the lookup fails.
     */
    @Nullable
    private static FluidPipeComponent getPipe(World world, Vector3i pos) {
        GridLookup lu = GridLookup.resolve(world.getChunkStore(), pos);
        if (lu == null) return null;
        return world.getChunkStore().getStore()
                .getComponent(lu.blockRef(), FluidPipeComponent.getComponentType());
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Test 1: fluid flows from the source through two pipes into the sink.
     *
     * <p>The source is OUTPUT-only (East face) and the sink is INPUT-only
     * (West face), so no bidirectional oscillation can occur.  After filling
     * the source with {@link #FILL_AMOUNT} liters and waiting
     * {@link #WAIT_TICKS}, the source must be empty and the sink must hold
     * exactly {@link #FILL_AMOUNT} liters of the correct fluid type.
     */
    private static TestCase tankTransfersFluidViaPipes() {
        return new TestCase("tank_transfers_fluid_via_pipes", 6, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeLayout(w, ox, oy, oz);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent fcc = getContainer(w, new Vector3i(ox, oy, oz));
                    if (fcc != null) fcc.fill(FLUID_ID, FILL_AMOUNT);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent fccSrc  = getContainer(w, new Vector3i(ox,     oy, oz));
                    FluidContainerComponent fccSink = getContainer(w, new Vector3i(ox + 3, oy, oz));
                    return fccSrc  != null && fccSrc.getAmount()  == 0
                        && fccSink != null && fccSink.getAmount() == FILL_AMOUNT
                        && FLUID_ID.equals(fccSink.getLockedFluidId());
                }, "fluid transfers from source through pipes to sink"));
    }

    /**
     * Test 2: after a successful transfer the traversed pipes are locked to the fluid type.
     *
     * <p>Same layout as test 1. After the full transfer the inner relay pipe
     * (at {@code ox+2}) must have its {@link FluidPipeComponent#getLockedFluidId()}
     * set to {@link #FLUID_ID}.
     */
    private static TestCase pipeLocksTsFluidAfterTransfer() {
        return new TestCase("pipe_locks_to_fluid_after_transfer", 6, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeLayout(w, ox, oy, oz);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent fcc = getContainer(w, new Vector3i(ox, oy, oz));
                    if (fcc != null) fcc.fill(FLUID_ID, FILL_AMOUNT);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidPipeComponent fpc = getPipe(w, new Vector3i(ox + 2, oy, oz));
                    return fpc != null && FLUID_ID.equals(fpc.getLockedFluidId());
                }, "the traversed pipe is locked to the fluid ID after a successful transfer"));
    }

    /**
     * Test 3: a pipe pre-locked to a different fluid blocks all transfer.
     *
     * <p>The inner relay pipe (at {@code ox+2}) is manually locked to
     * {@link #OTHER_FLUID_ID} before the source is filled with
     * {@link #FLUID_ID}.  After {@link #WAIT_TICKS} the handler must have
     * skipped the incompatible path, leaving the source unchanged and the
     * sink empty.
     */
    private static TestCase pipeBlocksIncompatibleFluid() {
        return new TestCase("pipe_blocks_incompatible_fluid", 6, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeLayout(w, ox, oy, oz);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    // Pre-lock the inner relay pipe to a different fluid type.
                    FluidPipeComponent fpc = getPipe(w, new Vector3i(ox + 2, oy, oz));
                    if (fpc != null) fpc.setLockedFluidId(OTHER_FLUID_ID);
                    // Fill the source with water.
                    FluidContainerComponent fcc = getContainer(w, new Vector3i(ox, oy, oz));
                    if (fcc != null) fcc.fill(FLUID_ID, FILL_AMOUNT);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent fccSrc  = getContainer(w, new Vector3i(ox,     oy, oz));
                    FluidContainerComponent fccSink = getContainer(w, new Vector3i(ox + 3, oy, oz));
                    return fccSrc  != null && fccSrc.getAmount()  == FILL_AMOUNT // source unchanged
                        && fccSink != null && fccSink.getAmount() == 0;          // sink stays empty
                }, "a pipe locked to a different fluid prevents any transfer"));
    }

    /**
     * Test 4: no fluid is moved when the sink container is already at full capacity.
     *
     * <p>The sink (INPUT-only, capacity {@link #SINK_CAPACITY}) is pre-filled to
     * its maximum before the source is given {@link #FILL_AMOUNT} liters.  Because
     * the handler finds {@code availableSpace() == 0} on the sink it is skipped,
     * leaving the source's volume unchanged after {@link #WAIT_TICKS}.
     */
    private static TestCase noTransferWhenSinkIsFull() {
        return new TestCase("no_transfer_when_sink_is_full", 6, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeLayout(w, ox, oy, oz);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    // Fill the sink to its full capacity so it cannot accept more fluid.
                    FluidContainerComponent fccSink = getContainer(w, new Vector3i(ox + 3, oy, oz));
                    if (fccSink != null) fccSink.fill(FLUID_ID, SINK_CAPACITY);
                    // Fill the source with the test amount.
                    FluidContainerComponent fccSrc = getContainer(w, new Vector3i(ox, oy, oz));
                    if (fccSrc != null) fccSrc.fill(FLUID_ID, FILL_AMOUNT);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent fccSrc = getContainer(w, new Vector3i(ox, oy, oz));
                    return fccSrc != null && fccSrc.getAmount() == FILL_AMOUNT;
                }, "source is unchanged when the sink container is already at full capacity"));
    }
}

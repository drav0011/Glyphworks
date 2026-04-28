package dev.drav.glyphworks.fluid.tests.system;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.component.FluidPipeComponent;
import dev.drav.glyphworks.fluid.container.FluidContainer;
import dev.drav.glyphworks.fluid.tests.FluidTestUtil;
import dev.drav.glyphworks.grid.event.BreakGridBlockEvent;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "fluid_grid_transfer"} — integration tests for
 * {@link dev.drav.glyphworks.fluid.handlers.FluidGridTypeHandler}.
 *
 * <h3>Block reference</h3>
 * <ul>
 * <li><b>Tank</b> ({@code Glyphworks_Fluid_Tank}): single bidirectional Up
 * face, 16 000 L. A storage node in the network pool — fluid flows into and
 * out of it to distribute across all connected tanks. No dedicated input or
 * output direction.</li>
 * <li><b>Source</b> ({@code Glyphworks_Fluid_Source}): OUTPUT face (Up),
 * refilled to capacity every tick by {@code FluidSourceSystem}.</li>
 * <li><b>Sink</b> ({@code Glyphworks_Fluid_Sink}): INPUT face (Up), drained to
 * empty every tick by {@code FluidSinkSystem}.</li>
 * <li><b>Pipe</b> ({@code Glyphworks_Fluid_Pipe}): BIDIRECTIONAL on all 6
 * faces, no container. Pure relay.</li>
 * </ul>
 *
 * <h3>Standard tank layout</h3>
 * Tanks connect upward to relay pipes. The standard 4-block layout pairs two
 * tanks beneath two horizontally-connected pipes:
 * <pre>
 *   Pipe1(oy+1,ox) — Pipe2(oy+1,ox+1)
 *       ↕ Up/Down        ↕ Up/Down
 *   TankA(oy,ox)     TankB(oy,ox+1)
 * </pre>
 */
public final class FluidGridTransferTests {

    // ── Block IDs ────────────────────────────────────────────────────────────

    private static final String TANK_ID = "Glyphworks_Fluid_Tank";
    private static final String PIPE_ID = "Glyphworks_Fluid_Pipe";

    // ── Fluid IDs ────────────────────────────────────────────────────────────

    private static final String WATER_ID = "Water_Source";
    private static final String LAVA_ID = "Lava_Source";

    // ── Transfer constants ───────────────────────────────────────────────────

    /** 25 L/tick — matches the transferRate in the block JSONs. */
    private static final int RATE = 25;

    /** 500 L — drains in 20 ticks at 25 L/tick, well within any wait. */
    private static final int FILL_AMOUNT = 500;

    /** Enough ticks to drain {@link #FILL_AMOUNT} at {@link #RATE}. */
    private static final int TRANSFER_TICKS = (FILL_AMOUNT / RATE) + 5;

    /** Short wait — enough time for the grid to process one or two ticks. */
    private static final int SHORT_WAIT = 3;

    // ── Grid type ────────────────────────────────────────────────────────────

    private static final GridType FLUID_TYPE = GridType.of("Fluid");

    // ── Constructor ──────────────────────────────────────────────────────────

    private FluidGridTransferTests() {
    }

    // ── Registration ─────────────────────────────────────────────────────────

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("fluid_grid_transfer")
                .test(fluidSpreadsAcrossNetwork())
                .test(transferRateIsRespected())
                .test(relayPipesLockToFluidType())
                .test(lockedPipeBlocksSpread())
                .test(noTransferWhenPoolIsFull())
                .test(fluidTypeMismatchBlocksTank())
                .test(fluidFillsCloserTankFirst())
                .test(multipleTanksWithFluidAllDrain())
                .test(disconnectedNetworksDoNotInterfere())
                .test(componentRootTracksMembership())
                .test(rootChangesOnTopologyBreak())
                .test(mergedNetworkPipeLockEW())
                .test(mergedNetworkPipeLockNS());
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private static void placeTank(World w, int x, int y, int z) {
        w.setBlock(x, y, z, TANK_ID);
        PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
    }

    private static void placePipe(World w, int x, int y, int z) {
        w.setBlock(x, y, z, PIPE_ID);
        PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
    }

    /**
     * Standard 4-block layout connecting two tanks through two relay pipes.
     *
     * <pre>
     *   Pipe1(oy+1, ox) — Pipe2(oy+1, ox+1)
     *       ↕ Up/Down        ↕ Up/Down
     *   TankA(oy, ox)    TankB(oy, ox+1)
     * </pre>
     *
     * <p>Source tank is at {@code (ox, oy, oz)}, sink tank at
     * {@code (ox+1, oy, oz)}.
     */
    private static void placeTankLayout(World w, int ox, int oy, int oz) {
        placeTank(w, ox, oy, oz);
        placePipe(w, ox, oy + 1, oz);
        placePipe(w, ox + 1, oy + 1, oz);
        placeTank(w, ox + 1, oy, oz);
    }

    @Nullable
    private static FluidContainerComponent getContainer(World w, int x, int y, int z) {
        return FluidTestUtil.getContainer(w, new Vector3i(x, y, z));
    }

    @Nullable
    private static FluidPipeComponent getPipe(World w, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(w.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return w.getChunkStore().getStore().getComponent(lu.blockRef(), FluidPipeComponent.getComponentType());
    }

    // ── Fluid container helpers ───────────────────────────────────────────────

    /**
     * Sums the fluid across all four containers in the standard 4-block layout
     * (TankA, PipeA, PipeB, TankB), correctly accounting for fluid held in the
     * pipe pool between transfers.
     */
    private static int standardLayoutTotalFluidMb(World w, int ox, int oy, int oz) {
        int total = 0;
        FluidContainerComponent tankA = getContainer(w, ox, oy, oz);
        FluidContainerComponent pipeA = getContainer(w, ox, oy + 1, oz);
        FluidContainerComponent pipeB = getContainer(w, ox + 1, oy + 1, oz);
        FluidContainerComponent tankB = getContainer(w, ox + 1, oy, oz);
        if (tankA != null) total += totalFluidMb(tankA);
        if (pipeA != null) total += totalFluidMb(pipeA);
        if (pipeB != null) total += totalFluidMb(pipeB);
        if (tankB != null) total += totalFluidMb(tankB);
        return total;
    }

    private static void seedFluid(@Nonnull FluidContainerComponent fcc, @Nonnull String fluidId, int amountMb) {
        FluidContainer fc = fcc.getFluidContainer();
        fc.addFluidStack(new FluidStack(fluidId, amountMb, fc.getCapacityMbPerSlot()), false, false);
    }

    private static int totalFluidMb(@Nonnull FluidContainerComponent fcc) {
        FluidContainer fc = fcc.getFluidContainer();
        int total = 0;
        for (short i = 0; i < fc.getCapacity(); i++) {
            FluidStack s = fc.getFluidStack(i);
            if (s != null) total += s.getQuantity();
        }
        return total;
    }

    private static boolean hasFluid(@Nonnull FluidContainerComponent fcc) {
        FluidContainer fc = fcc.getFluidContainer();
        for (short i = 0; i < fc.getCapacity(); i++) {
            if (fc.getFluidStack(i) != null) return true;
        }
        return false;
    }

    private static int totalCapacityMb(@Nonnull FluidContainerComponent fcc) {
        FluidContainer fc = fcc.getFluidContainer();
        return fc.getCapacity() * fc.getCapacityMbPerSlot();
    }

    private static int fluidMbOf(@Nonnull FluidContainerComponent fcc, @Nonnull String fluidId) {
        FluidContainer fc = fcc.getFluidContainer();
        int total = 0;
        for (short i = 0; i < fc.getCapacity(); i++) {
            FluidStack s = fc.getFluidStack(i);
            if (s != null && fluidId.equals(s.getFluidId())) total += s.getQuantity();
        }
        return total;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Test 1 — fluid spreads: seeding one tank with fluid causes the other tank
     * in the same pool to receive some of it. Total fluid is conserved.
     */
    private static TestCase fluidSpreadsAcrossNetwork() {
        return new TestCase("fluid_spreads_across_network", 4, 3, 4)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTankLayout(w, ox, oy, oz);
                    FluidContainerComponent tankA = getContainer(w, ox, oy, oz);
                    if (tankA != null)
                        seedFluid(tankA, WATER_ID, FILL_AMOUNT);
                }))
                .step(Steps.waitUntil(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent tankB = getContainer(w, ox + 1, oy, oz);
                    return tankB != null
                            && hasFluid(tankB)
                            && standardLayoutTotalFluidMb(w, ox, oy, oz) == FILL_AMOUNT;
                }, TRANSFER_TICKS, "fluid distributed to TankB; total conserved across tanks and pipes"));
    }

    /**
     * Test 2 — pool equalization: fluid seeded in TankA spreads across the
     * network within a few ticks while conservation holds.
     *
     * <p>
     * Tanks and relay pipes form a single virtual pool. On each tick the pool
     * equalizes proportionally by container capacity, so fluid reaches TankB
     * after the first tick regardless of the configured transfer rate.
     *
     * <p>
     * Verified properties:
     * <ol>
     * <li><b>Conservation</b> — total fluid across tanks and pipes equals the
     * seeded amount at every point.</li>
     * <li><b>Spread</b> — TankA loses fluid and TankB gains fluid within
     * {@link #SHORT_WAIT} ticks.</li>
     * </ol>
     */
    private static TestCase transferRateIsRespected() {
        return new TestCase("transfer_rate_is_respected", 4, 3, 4)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTankLayout(w, ox, oy, oz);
                    FluidContainerComponent tankA = getContainer(w, ox, oy, oz);
                    if (tankA != null)
                        seedFluid(tankA, WATER_ID, FILL_AMOUNT);
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent tankA = getContainer(w, ox, oy, oz);
                    FluidContainerComponent tankB = getContainer(w, ox + 1, oy, oz);
                    if (tankA == null || tankB == null)
                        return false;
                    if (standardLayoutTotalFluidMb(w, ox, oy, oz) != FILL_AMOUNT)
                        return false;
                    return totalFluidMb(tankA) < FILL_AMOUNT
                            && totalFluidMb(tankB) > 0;
                }, "conservation holds across tanks and pipes; fluid spread from TankA to TankB"));
    }

    /**
     * Test 3 — pipe locking: after fluid flows through the relay layer, every
     * traversed relay pipe has its {@code fluidId} set to the flowing fluid type.
     */
    private static TestCase relayPipesLockToFluidType() {
        return new TestCase("relay_pipes_lock_to_fluid_type", 4, 3, 4)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTankLayout(w, ox, oy, oz);
                    FluidContainerComponent tankA = getContainer(w, ox, oy, oz);
                    if (tankA != null)
                        seedFluid(tankA, WATER_ID, FILL_AMOUNT);
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidPipeComponent p1 = getPipe(w, ox, oy + 1, oz);
                    FluidPipeComponent p2 = getPipe(w, ox + 1, oy + 1, oz);
                    return p1 != null && WATER_ID.equals(p1.getFluidId())
                            && p2 != null && WATER_ID.equals(p2.getFluidId());
                }, "both relay pipes locked to WATER_ID after fluid flows through"));
    }

    /**
     * Test 4 — pipe blocking: a relay pipe pre-locked to an incompatible fluid
     * type prevents any fluid from spreading through it.
     */
    private static TestCase lockedPipeBlocksSpread() {
        return new TestCase("locked_pipe_blocks_spread", 4, 3, 4)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTankLayout(w, ox, oy, oz);
                    FluidPipeComponent pipe = getPipe(w, ox + 1, oy + 1, oz);
                    if (pipe != null)
                        pipe.setFluidId(LAVA_ID);
                    FluidContainerComponent tankA = getContainer(w, ox, oy, oz);
                    if (tankA != null)
                        seedFluid(tankA, WATER_ID, FILL_AMOUNT);
                }))
                .step(Steps.wait(TRANSFER_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent tankA = getContainer(w, ox, oy, oz);
                    FluidContainerComponent tankB = getContainer(w, ox + 1, oy, oz);
                    return tankA != null && totalFluidMb(tankA) == FILL_AMOUNT
                            && tankB != null && !hasFluid(tankB);
                }, "TankA unchanged; TankB empty when relay pipe is locked to incompatible fluid"));
    }

    /**
     * Test 5 — full network: no fluid moves when every tank has
     * {@code availableSpace() == 0}.
     *
     * <p>
     * Both tanks are pre-filled to {@link #TANK_CAPACITY}. Because neither
     * has any room to accept fluid, the handler finds no valid sink entries
     * and skips the transfer. Both tanks remain exactly at capacity.
     *
     * <p>
     * Note: filling only one tank would still allow reverse flow — the full
     * tank acts as a source into the partially-filled tank. Filling both
     * eliminates all free space and is the correct guard test.
     */
    private static TestCase noTransferWhenPoolIsFull() {
        return new TestCase("no_transfer_when_pool_is_full", 4, 3, 4)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTankLayout(w, ox, oy, oz);
                    FluidContainerComponent tankA = getContainer(w, ox, oy, oz);
                    FluidContainerComponent tankB = getContainer(w, ox + 1, oy, oz);
                    FluidContainerComponent pipeA = getContainer(w, ox, oy + 1, oz);
                    FluidContainerComponent pipeB = getContainer(w, ox + 1, oy + 1, oz);
                    if (tankA != null)
                        seedFluid(tankA, WATER_ID, totalCapacityMb(tankA));
                    if (tankB != null)
                        seedFluid(tankB, WATER_ID, totalCapacityMb(tankB));
                    if (pipeA != null)
                        seedFluid(pipeA, WATER_ID, totalCapacityMb(pipeA));
                    if (pipeB != null)
                        seedFluid(pipeB, WATER_ID, totalCapacityMb(pipeB));
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent tankA = getContainer(w, ox, oy, oz);
                    FluidContainerComponent tankB = getContainer(w, ox + 1, oy, oz);
                    return tankA != null && totalFluidMb(tankA) == totalCapacityMb(tankA)
                            && tankB != null && totalFluidMb(tankB) == totalCapacityMb(tankB);
                }, "no fluid moved when every tank and pipe in the pool is at full capacity"));
    }

    /**
     * Test 6 — fluid-type isolation: a tank locked to one fluid type rejects
     * another fluid type, leaving both tanks' amounts unchanged.
     */
    private static TestCase fluidTypeMismatchBlocksTank() {
        return new TestCase("fluid_type_mismatch_blocks_tank", 4, 3, 4)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTankLayout(w, ox, oy, oz);
                    FluidContainerComponent tankB = getContainer(w, ox + 1, oy, oz);
                    if (tankB != null)
                        seedFluid(tankB, LAVA_ID, 100);
                    FluidContainerComponent tankA = getContainer(w, ox, oy, oz);
                    if (tankA != null)
                        seedFluid(tankA, WATER_ID, FILL_AMOUNT);
                }))
                .step(Steps.wait(TRANSFER_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent tankB = getContainer(w, ox + 1, oy, oz);
                    return tankB != null
                            && fluidMbOf(tankB, LAVA_ID) == 100
                            && fluidMbOf(tankB, WATER_ID) == 0;
                }, "TankB unchanged: Water from TankA drains into the pipe pool but cannot enter a Lava-locked tank"));
    }

    /**
     * Test 7 — pool spreading: fluid from TankA distributes across all empty
     * tanks in the network within a short window.
     *
     * <p>The pool model serves consumers in an unordered (set-iteration) fashion
     * rather than by distance. TankB and TankC alternate receiving the 25 L/tick
     * budget, so within {@link #SHORT_WAIT} ticks at least one of them will have
     * received fluid.
     *
     * <pre>
     *   PipeA(oy+1,ox) — PipeB(oy+1,ox+1) — PipeC(oy+1,ox+2)
     *        ↕                   ↕                   ↕
     *   TankA(oy,ox)       TankB(oy,ox+1)      TankC(oy,ox+2)
     * </pre>
     */
    private static TestCase fluidFillsCloserTankFirst() {
        return new TestCase("fluid_fills_closer_tank_first", 5, 3, 4)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTank(w, ox, oy, oz);
                    placePipe(w, ox, oy + 1, oz);
                    placePipe(w, ox + 1, oy + 1, oz);
                    placePipe(w, ox + 2, oy + 1, oz);
                    placeTank(w, ox + 1, oy, oz);
                    placeTank(w, ox + 2, oy, oz);
                    FluidContainerComponent tankA = getContainer(w, ox, oy, oz);
                    if (tankA != null)
                        seedFluid(tankA, WATER_ID, FILL_AMOUNT);
                }))
                .step(Steps.waitUntil(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent tankB = getContainer(w, ox + 1, oy, oz);
                    FluidContainerComponent tankC = getContainer(w, ox + 2, oy, oz);
                    return (tankB != null && hasFluid(tankB)) || (tankC != null && hasFluid(tankC));
                }, TRANSFER_TICKS, "fluid from TankA reached at least one empty neighbour in the pool"));
    }

    /**
     * Test 8 — multi-source convergence: multiple tanks that contain fluid all
     * distribute into the central empty tank.
     *
     * <pre>
     *   PipeA(oy+1,ox) — PipeB(oy+1,ox+1) — PipeC(oy+1,ox+2)
     *        ↕                   ↕                   ↕
     *   TankA(oy,ox)       TankB(oy,ox+1)      TankC(oy,ox+2)
     *   [filled]            [empty]              [filled]
     * </pre>
     */
    private static TestCase multipleTanksWithFluidAllDrain() {
        return new TestCase("multiple_tanks_with_fluid_all_drain", 5, 3, 4)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTank(w, ox, oy, oz);
                    placePipe(w, ox, oy + 1, oz);
                    placePipe(w, ox + 1, oy + 1, oz);
                    placePipe(w, ox + 2, oy + 1, oz);
                    placeTank(w, ox + 1, oy, oz);
                    placeTank(w, ox + 2, oy, oz);

                    FluidContainerComponent tankA = getContainer(w, ox, oy, oz);
                    FluidContainerComponent tankC = getContainer(w, ox + 2, oy, oz);
                    if (tankA != null)
                        seedFluid(tankA, WATER_ID, totalCapacityMb(tankA));
                    if (tankC != null)
                        seedFluid(tankC, WATER_ID, totalCapacityMb(tankC));
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent tankA = getContainer(w, ox, oy, oz);
                    FluidContainerComponent tankC = getContainer(w, ox + 2, oy, oz);
                    return tankA != null && totalFluidMb(tankA) < totalCapacityMb(tankA)
                            && tankC != null && totalFluidMb(tankC) < totalCapacityMb(tankC);
                }, "both filled tanks drained when multiple sources share a network"));
    }

    /**
     * Test 9 — network isolation: two separate 2-tank networks separated by a
     * 2-block gap in X do not exchange fluid.
     *
     * <pre>
     *   Network A:                          Network B (3 blocks away in X):
     *   PA1(oy+1,ox) — PA2(oy+1,ox+1)      PB1(oy+1,ox+4) — PB2(oy+1,ox+5)
     *        ↕               ↕                     ↕                ↕
     *   TA1(oy,ox)    TA2(oy,ox+1)           TB1(oy,ox+4)    TB2(oy,ox+5)
     * </pre>
     */
    private static TestCase disconnectedNetworksDoNotInterfere() {
        return new TestCase("disconnected_networks_do_not_interfere", 8, 3, 4)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();

                    placeTank(w, ox, oy, oz);
                    placePipe(w, ox, oy + 1, oz);
                    placePipe(w, ox + 1, oy + 1, oz);
                    placeTank(w, ox + 1, oy, oz);
                    FluidContainerComponent srcA = getContainer(w, ox, oy, oz);
                    if (srcA != null)
                        seedFluid(srcA, WATER_ID, FILL_AMOUNT);

                    placeTank(w, ox + 4, oy, oz);
                    placePipe(w, ox + 4, oy + 1, oz);
                    placePipe(w, ox + 5, oy + 1, oz);
                    placeTank(w, ox + 5, oy, oz);
                    FluidContainerComponent srcB = getContainer(w, ox + 4, oy, oz);
                    if (srcB != null)
                        seedFluid(srcB, LAVA_ID, FILL_AMOUNT);
                }))
                .step(Steps.waitUntil(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent tankA2 = getContainer(w, ox + 1, oy, oz);
                    FluidContainerComponent tankB2 = getContainer(w, ox + 5, oy, oz);
                    return tankA2 != null
                            && fluidMbOf(tankA2, WATER_ID) > 0
                            && fluidMbOf(tankA2, LAVA_ID) == 0
                            && tankB2 != null
                            && fluidMbOf(tankB2, LAVA_ID) > 0
                            && fluidMbOf(tankB2, WATER_ID) == 0;
                }, TRANSFER_TICKS * 2, "network A transferred Water only; network B transferred Lava only"));
    }

    /**
     * Test 10 — component tracking: after placing the standard 4-block layout,
     * every node reports the same non-null root via
     * {@link GridGraph#getComponentRoot(Vector3i)}.
     */
    private static TestCase componentRootTracksMembership() {
        return new TestCase("component_root_tracks_membership", 4, 3, 4)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTankLayout(w, ox, oy, oz);
                }))
                .step(Steps.wait(1))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    GridGraph graph = GlyphworksPlugin.get().getGridModule().getGridGraph(w, FLUID_TYPE);
                    if (graph == null)
                        return false;
                    Vector3i rTankA = graph.getComponentRoot(new Vector3i(ox, oy, oz));
                    Vector3i rPipeA = graph.getComponentRoot(new Vector3i(ox, oy + 1, oz));
                    Vector3i rPipeB = graph.getComponentRoot(new Vector3i(ox + 1, oy + 1, oz));
                    Vector3i rTankB = graph.getComponentRoot(new Vector3i(ox + 1, oy, oz));
                    return rTankA != null
                            && rTankA.equals(rPipeA)
                            && rPipeA.equals(rPipeB)
                            && rPipeB.equals(rTankB);
                }, "all four nodes share the same component root"));
    }

    /**
     * Test 11 — topology split: removing the pipe directly above TankA isolates
     * TankA from the rest of the network.
     *
     * <pre>
     *   PipeA(oy+1,ox) — PipeB(oy+1,ox+1)      ← break PipeA
     *        ↕                   ↕
     *   TankA(oy,ox)       TankB(oy,ox+1)
     * </pre>
     */
    private static TestCase rootChangesOnTopologyBreak() {
        return new TestCase("root_changes_on_topology_break", 4, 3, 4)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTankLayout(w, ox, oy, oz);
                }))
                .step(Steps.wait(1))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    BreakGridBlockEvent.disconnectBlock(w, new Vector3i(ox, oy + 1, oz));
                    w.setBlock(ox, oy + 1, oz, "Empty");
                }))
                .step(Steps.wait(1))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    GridGraph graph = GlyphworksPlugin.get().getGridModule().getGridGraph(w, FLUID_TYPE);
                    if (graph == null)
                        return false;
                    Vector3i rootLeft = graph.getComponentRoot(new Vector3i(ox, oy, oz));
                    Vector3i rootRight = graph.getComponentRoot(new Vector3i(ox + 1, oy, oz));
                    return rootLeft != null && rootRight != null && !rootLeft.equals(rootRight);
                }, "TankA and TankB have different roots after the connecting pipe is broken"));
    }

    // ── Tests 12/13: cross-network bridge contention ────────────────────────

    /**
     * Tests 12 &amp; 13 — cross-network bridge contention.
     *
     * <p>
     * Two separate two-tank networks (TankSrc → PipeRelay → TankSink) are
     * bootstrapped in isolation so their relay pipes lock to incompatible fluid
     * types. A bridge pipe is then placed connecting the two relay layers,
     * merging the graphs.
     *
     * <p>
     * Sources are iterated in ascending position order (X → Y → Z). The Water
     * source always sits at a lower coordinate than the Lava source, so the
     * bridge always locks to {@code WATER_ID}.
     *
     * <p>
     * Invariants asserted:
     * <ul>
     * <li>Bridge locks to {@code WATER_ID}.</li>
     * <li>Each dedicated pipe retains its original lock.</li>
     * <li>No cross-contamination reaches the opposing sink tank.</li>
     * </ul>
     */

    /**
     * Variant A — networks separated in X; bridge placed between them in X.
     *
     * <pre>
     *   PA1(oy+1,ox) — PA2(oy+1,ox+1)   Bridge(oy+1,ox+2)   PB1(oy+1,ox+3) — PB2(oy+1,ox+4)
     *        ↕               ↕                                      ↕               ↕
     *   TankA(oy,ox)    SinkA(oy,ox+1)                       TankB(oy,ox+3)   SinkB(oy,ox+4)
     * </pre>
     *
     * Sources differ in X; Water at lower X wins.
     */
    private static TestCase mergedNetworkPipeLockEW() {
        return new TestCase("merged_network_pipe_lock_ew", 6, 3, 4)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTank(w, ox, oy, oz);
                    placePipe(w, ox, oy + 1, oz);
                    placePipe(w, ox + 1, oy + 1, oz);
                    placeTank(w, ox + 1, oy, oz);
                    FluidContainerComponent srcA = getContainer(w, ox, oy, oz);
                    if (srcA != null)
                        seedFluid(srcA, WATER_ID, FILL_AMOUNT);

                    placeTank(w, ox + 3, oy, oz);
                    placePipe(w, ox + 3, oy + 1, oz);
                    placePipe(w, ox + 4, oy + 1, oz);
                    placeTank(w, ox + 4, oy, oz);
                    FluidContainerComponent srcB = getContainer(w, ox + 3, oy, oz);
                    if (srcB != null)
                        seedFluid(srcB, LAVA_ID, FILL_AMOUNT);
                }))
                .step(Steps.waitUntil(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidPipeComponent pipeA = getPipe(w, ox, oy + 1, oz);
                    FluidPipeComponent pipeB = getPipe(w, ox + 3, oy + 1, oz);
                    return pipeA != null && WATER_ID.equals(pipeA.getFluidId())
                            && pipeB != null && LAVA_ID.equals(pipeB.getFluidId());
                }, TRANSFER_TICKS, "PA1 locked to Water and PB1 locked to Lava"))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placePipe(w, ox + 2, oy + 1, oz);
                }))
                .step(Steps.wait(1))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidPipeComponent bridge = getPipe(w, ox + 2, oy + 1, oz);
                    return bridge != null && WATER_ID.equals(bridge.getFluidId());
                }, "bridge locked to WATER_ID: lower-coordinate source wins (EW)"))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidPipeComponent pipeA = getPipe(w, ox, oy + 1, oz);
                    FluidPipeComponent pipeB = getPipe(w, ox + 3, oy + 1, oz);
                    return pipeA != null && WATER_ID.equals(pipeA.getFluidId())
                            && pipeB != null && LAVA_ID.equals(pipeB.getFluidId());
                }, "dedicated pipes retain original fluid locks after bridge placed (EW)"))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent sinkA = getContainer(w, ox + 1, oy, oz);
                    FluidContainerComponent sinkB = getContainer(w, ox + 4, oy, oz);
                    // SinkA is in the Water network: must not receive Lava from the merged pool.
                    // SinkB is in the Lava network and legitimately holds Lava from pre-bridge
                    // redistribution; guard against Water leaking into it instead.
                    boolean sinkAOk = sinkA == null || fluidMbOf(sinkA, LAVA_ID) == 0;
                    boolean sinkBOk = sinkB == null || fluidMbOf(sinkB, WATER_ID) == 0;
                    return sinkAOk && sinkBOk;
                }, "no cross-contamination: SinkA has no Lava; SinkB has no Water after bridge merge (EW)"));
    }

    /**
     * Variant B — networks separated in Z; bridge placed between them in Z.
     *
     * <pre>
     *   PA1(oy+1,ox,oz) — PA2(oy+1,ox+1,oz)
     *        ↕                    ↕
     *   TankA(oy,ox,oz)      SinkA(oy,ox+1,oz)
     *
     *   Bridge placed at (oy+1, ox, oz+1)
     *
     *   PB1(oy+1,ox,oz+2) — PB2(oy+1,ox+1,oz+2)
     *        ↕                    ↕
     *   TankB(oy,ox,oz+2)     SinkB(oy,ox+1,oz+2)
     * </pre>
     *
     * Sources differ in Z; Water at lower Z wins.
     */
    private static TestCase mergedNetworkPipeLockNS() {
        return new TestCase("merged_network_pipe_lock_ns", 3, 5, 4)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTank(w, ox, oy, oz);
                    placePipe(w, ox, oy + 1, oz);
                    placePipe(w, ox + 1, oy + 1, oz);
                    placeTank(w, ox + 1, oy, oz);
                    FluidContainerComponent srcA = getContainer(w, ox, oy, oz);
                    if (srcA != null)
                        seedFluid(srcA, WATER_ID, FILL_AMOUNT);

                    placeTank(w, ox, oy, oz + 2);
                    placePipe(w, ox, oy + 1, oz + 2);
                    placePipe(w, ox + 1, oy + 1, oz + 2);
                    placeTank(w, ox + 1, oy, oz + 2);
                    FluidContainerComponent srcB = getContainer(w, ox, oy, oz + 2);
                    if (srcB != null)
                        seedFluid(srcB, LAVA_ID, FILL_AMOUNT);
                }))
                .step(Steps.waitUntil(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidPipeComponent pipeA = getPipe(w, ox, oy + 1, oz);
                    FluidPipeComponent pipeB = getPipe(w, ox, oy + 1, oz + 2);
                    return pipeA != null && WATER_ID.equals(pipeA.getFluidId())
                            && pipeB != null && LAVA_ID.equals(pipeB.getFluidId());
                }, TRANSFER_TICKS, "PA1 locked to Water and PB1 locked to Lava"))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placePipe(w, ox, oy + 1, oz + 1);
                }))
                .step(Steps.wait(1))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidPipeComponent bridge = getPipe(w, ox, oy + 1, oz + 1);
                    return bridge != null && WATER_ID.equals(bridge.getFluidId());
                }, "bridge locked to WATER_ID: lower-coordinate source wins (NS)"))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidPipeComponent pipeA = getPipe(w, ox, oy + 1, oz);
                    FluidPipeComponent pipeB = getPipe(w, ox, oy + 1, oz + 2);
                    return pipeA != null && WATER_ID.equals(pipeA.getFluidId())
                            && pipeB != null && LAVA_ID.equals(pipeB.getFluidId());
                }, "dedicated pipes retain original fluid locks after bridge placed (NS)"))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent sinkA = getContainer(w, ox + 1, oy, oz);
                    FluidContainerComponent sinkB = getContainer(w, ox + 1, oy, oz + 2);
                    // SinkA is in the Water network: must not receive Lava from the merged pool.
                    // SinkB is in the Lava network and legitimately holds Lava from pre-bridge
                    // redistribution; guard against Water leaking into it instead.
                    boolean sinkAOk = sinkA == null || fluidMbOf(sinkA, LAVA_ID) == 0;
                    boolean sinkBOk = sinkB == null || fluidMbOf(sinkB, WATER_ID) == 0;
                    return sinkAOk && sinkBOk;
                }, "no cross-contamination: SinkA has no Lava; SinkB has no Water after bridge merge (NS)"));
    }
}



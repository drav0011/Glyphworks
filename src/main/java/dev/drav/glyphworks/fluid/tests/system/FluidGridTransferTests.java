package dev.drav.glyphworks.fluid.tests.system;

import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.component.FluidPipeComponent;
import dev.drav.glyphworks.grid.event.BreakGridBlockEvent;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.fluid.tests.FluidTestUtil;
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
 * <li><b>Tank</b> ({@code Glyphworks_Fluid_Tank}): INPUT on North, OUTPUT on
 * South, 16 000 L, no auto-refill/drain. Used for exact-amount tests.</li>
 * <li><b>Source</b> ({@code Glyphworks_Fluid_Source}): OUTPUT face (Up),
 * refilled to capacity every tick by {@code FluidSourceSystem}.</li>
 * <li><b>Sink</b> ({@code Glyphworks_Fluid_Sink}): INPUT face (Up), drained to
 * empty every tick by {@code FluidSinkSystem}.</li>
 * <li><b>Pipe</b> ({@code Glyphworks_Fluid_Pipe}): BIDIRECTIONAL on all 6
 * faces, no container. Pure relay.</li>
 * </ul>
 *
 * <h3>Face orientation</h3>
 * Tanks have fixed North=Input and South=Output faces. Standard test layouts
 * run along the Z axis so that the source tank's South face connects to the
 * pipe chain and the sink tank's North face receives from it. No rotation is
 * needed for Z-axis chains.
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

    /** Capacity of Tank / Sink blocks (from JSON). */
    private static final int TANK_CAPACITY = 16_000;

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
                .test(tankToTankViaPipes())
                .test(transferRateIsRespected())
                .test(pipeLocksTsFluidAfterTransfer())
                .test(pipeBlocksIncompatibleFluid())
                .test(noTransferWhenSinkFull())
                .test(sinkFluidTypeLockBlocksFill())
                .test(multiSinkFillsClosestFirst())
                .test(multiSourceBothDrain())
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
     * Standard 4-block Z-axis layout:
     *
     * <pre>
     *   Tank(oz) →OUT(S)→ Pipe(oz+1) → Pipe(oz+2) →IN(N)→ Tank(oz+3)
     * </pre>
     */
    private static void placeTankLayout(World w, int ox, int oy, int oz) {
        placeTank(w, ox, oy, oz);
        placePipe(w, ox, oy, oz + 1);
        placePipe(w, ox, oy, oz + 2);
        placeTank(w, ox, oy, oz + 3);
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

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Test 1 — basic: fluid of the correct type travels through two relay pipes
     * into an empty tank within {@link #SHORT_WAIT} ticks.
     *
     * <p>Layout along Z axis: Source(oz) → Pipe → Pipe → Sink(oz+3).
     * Source outputs South, sink inputs North. Flow is one-directional.
     */
    private static TestCase tankToTankViaPipes() {
        return new TestCase("tank_to_tank_via_pipes", 3, 3, 6)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTankLayout(w, ox, oy, oz);
                    FluidContainerComponent src = getContainer(w, ox, oy, oz);
                    if (src != null)
                        src.fill(new FluidStack(WATER_ID, FILL_AMOUNT, TANK_CAPACITY));
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent src = getContainer(w, ox, oy, oz);
                    FluidContainerComponent sink = getContainer(w, ox, oy, oz + 3);
                    if (src == null || sink == null)
                        return false;
                    return src.getAmount() + sink.getAmount() == FILL_AMOUNT
                            && sink.getAmount() > 0
                            && WATER_ID.equals(sink.getFluidId());
                }, "fluid reached sink; total conserved; correct fluid type"));
    }

    /**
     * Test 2 — rate: fluid transfers at a bounded, non-zero rate.
     *
     * <p>
     * The per-tick rate precision (exactly {@link #RATE} L per tick) is
     * already verified at the unit level by
     * {@code GridComponentTests.transfer_accumulator_whole} and
     * {@code GridComponentTests.transfer_accumulator_sub_tick}, which test
     * {@link dev.drav.glyphworks.grid.component.GridComponent#drainAccumulator}
     * directly. The accumulator only guarantees the <em>long-run average</em>
     * is {@link #RATE}; individual tick amounts can deviate slightly due to
     * floating-point {@code dt} rounding.
     *
     * <p>
     * This test verifies the ECS-level integration properties that matter:
     * <ol>
     * <li><b>Conservation</b> — total fluid is never created or destroyed.</li>
     * <li><b>Non-zero rate</b> — the handler actually transfers fluid.</li>
     * <li><b>Rate limiting</b> — the handler does not transfer all fluid in one
     * tick (unlimited rate would reach BIDIR equilibrium of
     * {@code FILL_AMOUNT / 2} immediately).</li>
     * </ol>
     */
    private static TestCase transferRateIsRespected() {
        return new TestCase("transfer_rate_is_respected", 3, 3, 6)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTankLayout(w, ox, oy, oz);
                    FluidContainerComponent src = getContainer(w, ox, oy, oz);
                    if (src != null)
                        src.fill(new FluidStack(WATER_ID, FILL_AMOUNT, TANK_CAPACITY));
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent src = getContainer(w, ox, oy, oz);
                    FluidContainerComponent sink = getContainer(w, ox, oy, oz + 3);
                    if (src == null || sink == null)
                        return false;
                    if (src.getAmount() + sink.getAmount() != FILL_AMOUNT)
                        return false;
                    if (sink.getAmount() == 0)
                        return false;
                    return sink.getAmount() < FILL_AMOUNT / 2;
                }, "conservation holds; sink > 0; rate is limited (sink < half total)"));
    }

    /**
     * Test 3 — pipe locking: after a successful transfer, every relay pipe in
     * the path has its {@code fluidId} set to the transferred fluid type.
     */
    private static TestCase pipeLocksTsFluidAfterTransfer() {
        return new TestCase("pipe_locks_to_fluid_after_transfer", 3, 3, 6)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTankLayout(w, ox, oy, oz);
                    FluidContainerComponent src = getContainer(w, ox, oy, oz);
                    if (src != null)
                        src.fill(new FluidStack(WATER_ID, FILL_AMOUNT, TANK_CAPACITY));
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidPipeComponent p1 = getPipe(w, ox, oy, oz + 1);
                    FluidPipeComponent p2 = getPipe(w, ox, oy, oz + 2);
                    return p1 != null && WATER_ID.equals(p1.getFluidId())
                            && p2 != null && WATER_ID.equals(p2.getFluidId());
                }, "both relay pipes locked to WATER_ID after transfer"));
    }

    /**
     * Test 4 — pipe blocking: a pipe pre-locked to Lava blocks any Water transfer.
     *
     * <p>
     * After locking the middle relay to Lava and filling the source with
     * Water, the source must remain unchanged and the sink empty.
     */
    private static TestCase pipeBlocksIncompatibleFluid() {
        return new TestCase("pipe_blocks_incompatible_fluid", 3, 3, 6)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTankLayout(w, ox, oy, oz);
                    FluidPipeComponent pipe = getPipe(w, ox, oy, oz + 2);
                    if (pipe != null)
                        pipe.setFluidId(LAVA_ID);
                    FluidContainerComponent src = getContainer(w, ox, oy, oz);
                    if (src != null)
                        src.fill(new FluidStack(WATER_ID, FILL_AMOUNT, TANK_CAPACITY));
                }))
                .step(Steps.wait(TRANSFER_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent src = getContainer(w, ox, oy, oz);
                    FluidContainerComponent sink = getContainer(w, ox, oy, oz + 3);
                    return src != null && src.getAmount() == FILL_AMOUNT
                            && sink != null && sink.getAmount() == 0;
                }, "source unchanged; sink empty when relay pipe is locked to incompatible fluid"));
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
    private static TestCase noTransferWhenSinkFull() {
        return new TestCase("no_transfer_when_sink_full", 3, 3, 6)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTankLayout(w, ox, oy, oz);
                    FluidContainerComponent src = getContainer(w, ox, oy, oz);
                    FluidContainerComponent sink = getContainer(w, ox, oy, oz + 3);
                    if (src != null)
                        src.fill(new FluidStack(WATER_ID, TANK_CAPACITY, TANK_CAPACITY));
                    if (sink != null)
                        sink.fill(new FluidStack(WATER_ID, TANK_CAPACITY, TANK_CAPACITY));
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent src = getContainer(w, ox, oy, oz);
                    FluidContainerComponent sink = getContainer(w, ox, oy, oz + 3);
                    return src != null && src.getAmount() == TANK_CAPACITY
                            && sink != null && sink.getAmount() == TANK_CAPACITY;
                }, "no fluid moved when all tanks are at capacity (availableSpace == 0)"));
    }

    /**
     * Test 6 — sink fluid type lock: a sink pre-filled with Lava rejects Water.
     *
     * <p>
     * {@link FluidContainerComponent#fill} rejects fluid when the container
     * is already locked to a different fluid type. The source should not drain.
     */
    private static TestCase sinkFluidTypeLockBlocksFill() {
        return new TestCase("sink_fluid_type_lock_blocks_fill", 3, 3, 6)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTankLayout(w, ox, oy, oz);
                    FluidContainerComponent sink = getContainer(w, ox, oy, oz + 3);
                    if (sink != null)
                        sink.fill(new FluidStack(LAVA_ID, 100, TANK_CAPACITY));
                    FluidContainerComponent src = getContainer(w, ox, oy, oz);
                    if (src != null)
                        src.fill(new FluidStack(WATER_ID, FILL_AMOUNT, TANK_CAPACITY));
                }))
                .step(Steps.wait(TRANSFER_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent src = getContainer(w, ox, oy, oz);
                    FluidContainerComponent sink = getContainer(w, ox, oy, oz + 3);
                    return src != null && src.getAmount() == FILL_AMOUNT
                            && sink != null && sink.getAmount() == 100;
                }, "source unchanged; sink retains only Lava when locked to different fluid"));
    }

    /**
     * Test 7 — closest-first ordering: one source, two sinks at different
     * distances.
     *
     * <pre>
     *   Src(ox,oz) →OUT(S)→ Pipe(ox,oz+1) → SinkA(ox,oz+2)      [distance 2]
     *                             →East→
     *                        Pipe(ox+1,oz+1) → SinkB(ox+1,oz+2) [distance 3]
     * </pre>
     */
    private static TestCase multiSinkFillsClosestFirst() {
        return new TestCase("multi_sink_fills_closest_first", 4, 3, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTank(w, ox, oy, oz);
                    FluidContainerComponent src = getContainer(w, ox, oy, oz);
                    if (src != null)
                        src.fill(new FluidStack(WATER_ID, FILL_AMOUNT, TANK_CAPACITY));
                    placePipe(w, ox, oy, oz + 1);
                    placeTank(w, ox, oy, oz + 2);
                    placePipe(w, ox + 1, oy, oz + 1);
                    placeTank(w, ox + 1, oy, oz + 2);
                }))
                .step(Steps.wait(1))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent sinkA = getContainer(w, ox, oy, oz + 2);
                    FluidContainerComponent sinkB = getContainer(w, ox + 1, oy, oz + 2);
                    return sinkA != null && sinkA.getAmount() > 0
                            && sinkB != null && sinkB.getAmount() == 0;
                }, "closer sink fills before farther sink within the same tick budget"));
    }

    /**
     * Test 8 — multi-source: two source tanks in the same network both drain.
     *
     * <pre>
     *   SrcA(ox,oz)   →OUT(S)→ PipeA(ox,oz+1)
     *                                 →East→ PipeC(ox+1,oz+1) →OUT(S)→ Sink(ox+1,oz+2)
     *   SrcB(ox+2,oz) →OUT(S)→ PipeB(ox+2,oz+1)
     * </pre>
     *
     * Distance from each source to Sink = 3 hops.
     */
    private static TestCase multiSourceBothDrain() {
        return new TestCase("multi_source_both_drain", 5, 3, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTank(w, ox, oy, oz);
                    placePipe(w, ox, oy, oz + 1);
                    placePipe(w, ox + 1, oy, oz + 1);
                    placePipe(w, ox + 2, oy, oz + 1);
                    placeTank(w, ox + 2, oy, oz);
                    placeTank(w, ox + 1, oy, oz + 2);

                    FluidContainerComponent srcA = getContainer(w, ox, oy, oz);
                    FluidContainerComponent srcB = getContainer(w, ox + 2, oy, oz);
                    if (srcA != null)
                        srcA.fill(new FluidStack(WATER_ID, TANK_CAPACITY, TANK_CAPACITY));
                    if (srcB != null)
                        srcB.fill(new FluidStack(WATER_ID, TANK_CAPACITY, TANK_CAPACITY));
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent srcA = getContainer(w, ox, oy, oz);
                    FluidContainerComponent srcB = getContainer(w, ox + 2, oy, oz);
                    return srcA != null && srcA.getAmount() < TANK_CAPACITY
                            && srcB != null && srcB.getAmount() < TANK_CAPACITY;
                }, "both source tanks drained when two sources share a network"));
    }

    /**
     * Test 9 — network isolation: two separate Z-axis chains placed side-by-side
     * (offset in X) do not exchange fluid.
     *
     * <pre>
     *   Network A: Src(ox,oz) → Pipe(ox,oz+1) → Tank(ox,oz+2)
     *   Network B: Src(ox+3,oz) → Pipe(ox+3,oz+1) → Tank(ox+3,oz+2)
     * </pre>
     */
    private static TestCase disconnectedNetworksDoNotInterfere() {
        return new TestCase("disconnected_networks_do_not_interfere", 6, 3, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();

                    placeTank(w, ox, oy, oz);
                    placePipe(w, ox, oy, oz + 1);
                    placeTank(w, ox, oy, oz + 2);
                    FluidContainerComponent srcA = getContainer(w, ox, oy, oz);
                    if (srcA != null)
                        srcA.fill(new FluidStack(WATER_ID, FILL_AMOUNT, TANK_CAPACITY));

                    placeTank(w, ox + 3, oy, oz);
                    placePipe(w, ox + 3, oy, oz + 1);
                    placeTank(w, ox + 3, oy, oz + 2);
                    FluidContainerComponent srcB = getContainer(w, ox + 3, oy, oz);
                    if (srcB != null)
                        srcB.fill(new FluidStack(LAVA_ID, FILL_AMOUNT, TANK_CAPACITY));
                }))
                .step(Steps.wait(TRANSFER_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent tankA = getContainer(w, ox, oy, oz + 2);
                    FluidContainerComponent tankB = getContainer(w, ox + 3, oy, oz + 2);
                    return tankA != null && WATER_ID.equals(tankA.getFluidId())
                            && tankB != null && LAVA_ID.equals(tankB.getFluidId());
                }, "network A contains only Water, network B contains only Lava"));
    }

    /**
     * Test 10 — component tracking: after placing a 4-block chain, every node
     * reports the same non-null root via
     * {@link GridGraph#getComponentRoot(Vector3i)}, confirming that the
     * connected-component index is maintained correctly.
     */
    private static TestCase componentRootTracksMembership() {
        return new TestCase("component_root_tracks_membership", 3, 3, 6)
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
                    Vector3i r0 = graph.getComponentRoot(new Vector3i(ox, oy, oz));
                    Vector3i r1 = graph.getComponentRoot(new Vector3i(ox, oy, oz + 1));
                    Vector3i r2 = graph.getComponentRoot(new Vector3i(ox, oy, oz + 2));
                    Vector3i r3 = graph.getComponentRoot(new Vector3i(ox, oy, oz + 3));
                    return r0 != null && r0.equals(r1) && r1.equals(r2) && r2.equals(r3);
                }, "all four nodes share the same component root"));
    }

    /**
     * Test 11 — topology split: breaking the middle pipe of a 3-node Z-axis
     * chain causes the two surviving nodes to belong to different components.
     *
     * <pre>
     *   Tank(oz) → Pipe(oz+1) → Tank(oz+2)
     * </pre>
     */
    private static TestCase rootChangesOnTopologyBreak() {
        return new TestCase("root_changes_on_topology_break", 3, 3, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTank(w, ox, oy, oz);
                    placePipe(w, ox, oy, oz + 1);
                    placeTank(w, ox, oy, oz + 2);
                }))
                .step(Steps.wait(1))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    BreakGridBlockEvent.disconnectBlock(w, new Vector3i(ox, oy, oz + 1));
                    w.setBlock(ox, oy, oz + 1, "Empty");
                }))
                .step(Steps.wait(1))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    GridGraph graph = GlyphworksPlugin.get().getGridModule().getGridGraph(w, FLUID_TYPE);
                    if (graph == null)
                        return false;
                    Vector3i rootLeft = graph.getComponentRoot(new Vector3i(ox, oy, oz));
                    Vector3i rootRight = graph.getComponentRoot(new Vector3i(ox, oy, oz + 2));
                    return rootLeft != null && rootRight != null && !rootLeft.equals(rootRight);
                }, "two surviving nodes have different roots after middle pipe is broken"));
    }

    // ── Tests 12/13: cross-network bridge contention ────────────────────────

    /**
     * Tests 12 &amp; 13 — cross-network bridge contention.
     *
     * <p>
     * Two separate one-pipe networks are bootstrapped in isolation so their
     * relay pipes lock to incompatible fluid types (Water and Lava). A bridge
     * pipe is then placed connecting the two relay pipes, merging both graphs.
     *
     * <p>
     * Sources are iterated in ascending position order (X → Y → Z) by the
     * handler, making the bridge-lock outcome deterministic: the source at the
     * lower coordinate always wins. In both variants, the Water source sits at a
     * strictly lower coordinate than the Lava source, so the bridge always locks
     * to {@code WATER_ID}.
     *
     * <p>
     * Two orientation variants exercise different geometric arrangements to
     * ensure the sort comparator is exercised on both X- and Z-axis differences.
     *
     * <p>
     * Invariants asserted:
     * <ul>
     * <li>The bridge locks to {@code WATER_ID} (lower-coordinate source wins).</li>
     * <li>Each dedicated pipe retains its original fluid lock.</li>
     * <li>No cross-contamination reaches the opposing network's sink.</li>
     * </ul>
     */

    /**
     * Variant A — two Z-axis chains separated in X, bridged in X.
     *
     * <pre>
     *   TankA(ox,oz) → PipeA(ox,oz+1) → SinkA(ox,oz+2)
     *                   Bridge(ox+1,oz+1)   ← placed last
     *   TankB(ox+2,oz) → PipeB(ox+2,oz+1) → SinkB(ox+2,oz+2)
     * </pre>
     *
     * Sources differ in X. Water at lower X wins.
     */
    private static TestCase mergedNetworkPipeLockEW() {
        return new TestCase("merged_network_pipe_lock_ew", 5, 3, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTank(w, ox, oy, oz);
                    placePipe(w, ox, oy, oz + 1);
                    placeTank(w, ox, oy, oz + 2);
                    FluidContainerComponent srcA = getContainer(w, ox, oy, oz);
                    if (srcA != null)
                        srcA.fill(new FluidStack(WATER_ID, FILL_AMOUNT, TANK_CAPACITY));

                    placeTank(w, ox + 2, oy, oz);
                    placePipe(w, ox + 2, oy, oz + 1);
                    placeTank(w, ox + 2, oy, oz + 2);
                    FluidContainerComponent srcB = getContainer(w, ox + 2, oy, oz);
                    if (srcB != null)
                        srcB.fill(new FluidStack(LAVA_ID, FILL_AMOUNT, TANK_CAPACITY));
                }))
                .step(Steps.waitUntil(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidPipeComponent pipeA = getPipe(w, ox, oy, oz + 1);
                    FluidPipeComponent pipeB = getPipe(w, ox + 2, oy, oz + 1);
                    return pipeA != null && WATER_ID.equals(pipeA.getFluidId())
                            && pipeB != null && LAVA_ID.equals(pipeB.getFluidId());
                }, TRANSFER_TICKS, "PipeA locked to Water and PipeB locked to Lava"))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placePipe(w, ox + 1, oy, oz + 1);
                }))
                .step(Steps.wait(1))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidPipeComponent bridge = getPipe(w, ox + 1, oy, oz + 1);
                    return bridge != null && WATER_ID.equals(bridge.getFluidId());
                }, "bridge locked to WATER_ID: lower-coordinate source wins (EW)"))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidPipeComponent pipeA = getPipe(w, ox, oy, oz + 1);
                    FluidPipeComponent pipeB = getPipe(w, ox + 2, oy, oz + 1);
                    return pipeA != null && WATER_ID.equals(pipeA.getFluidId())
                            && pipeB != null && LAVA_ID.equals(pipeB.getFluidId());
                }, "dedicated pipes retain original fluid locks after bridge placed (EW)"))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent sinkA = getContainer(w, ox, oy, oz + 2);
                    FluidContainerComponent sinkB = getContainer(w, ox + 2, oy, oz + 2);
                    boolean sinkAOk = sinkA == null || sinkA.getFluidId() == null
                            || WATER_ID.equals(sinkA.getFluidId());
                    boolean sinkBOk = sinkB == null || sinkB.getFluidId() == null
                            || LAVA_ID.equals(sinkB.getFluidId());
                    return sinkAOk && sinkBOk;
                }, "no cross-contamination: SinkA only Water, SinkB only Lava (EW)"));
    }

    /**
     * Variant B — two Z-axis chains separated in Y, bridged vertically.
     *
     * <pre>
     *   TankA(ox,oy,oz) → PipeA(ox,oy,oz+1) → SinkA(ox,oy,oz+2)     [y = oy]
     *                      Bridge(ox,oy+1,oz+1)                       ← placed last
     *   TankB(ox,oy+2,oz) → PipeB(ox,oy+2,oz+1) → SinkB(ox,oy+2,oz+2) [y = oy+2]
     * </pre>
     *
     * Sources differ in Y. Water at lower Y wins.
     */
    private static TestCase mergedNetworkPipeLockNS() {
        return new TestCase("merged_network_pipe_lock_ns", 3, 5, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTank(w, ox, oy, oz);
                    placePipe(w, ox, oy, oz + 1);
                    placeTank(w, ox, oy, oz + 2);
                    FluidContainerComponent srcA = getContainer(w, ox, oy, oz);
                    if (srcA != null)
                        srcA.fill(new FluidStack(WATER_ID, FILL_AMOUNT, TANK_CAPACITY));

                    placeTank(w, ox, oy + 2, oz);
                    placePipe(w, ox, oy + 2, oz + 1);
                    placeTank(w, ox, oy + 2, oz + 2);
                    FluidContainerComponent srcB = getContainer(w, ox, oy + 2, oz);
                    if (srcB != null)
                        srcB.fill(new FluidStack(LAVA_ID, FILL_AMOUNT, TANK_CAPACITY));
                }))
                .step(Steps.waitUntil(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidPipeComponent pipeA = getPipe(w, ox, oy, oz + 1);
                    FluidPipeComponent pipeB = getPipe(w, ox, oy + 2, oz + 1);
                    return pipeA != null && WATER_ID.equals(pipeA.getFluidId())
                            && pipeB != null && LAVA_ID.equals(pipeB.getFluidId());
                }, TRANSFER_TICKS, "PipeA locked to Water and PipeB locked to Lava"))
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
                    FluidPipeComponent pipeA = getPipe(w, ox, oy, oz + 1);
                    FluidPipeComponent pipeB = getPipe(w, ox, oy + 2, oz + 1);
                    return pipeA != null && WATER_ID.equals(pipeA.getFluidId())
                            && pipeB != null && LAVA_ID.equals(pipeB.getFluidId());
                }, "dedicated pipes retain original fluid locks after bridge placed (NS)"))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent sinkA = getContainer(w, ox, oy, oz + 2);
                    FluidContainerComponent sinkB = getContainer(w, ox, oy + 2, oz + 2);
                    boolean sinkAOk = sinkA == null || sinkA.getFluidId() == null
                            || WATER_ID.equals(sinkA.getFluidId());
                    boolean sinkBOk = sinkB == null || sinkB.getFluidId() == null
                            || LAVA_ID.equals(sinkB.getFluidId());
                    return sinkAOk && sinkBOk;
                }, "no cross-contamination: SinkA only Water, SinkB only Lava (NS)"));
    }
}


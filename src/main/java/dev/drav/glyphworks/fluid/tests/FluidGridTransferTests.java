package dev.drav.glyphworks.fluid.tests;

import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.component.FluidPipeComponent;
import dev.drav.glyphworks.grid.event.BreakGridBlockEvent;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.test.Steps;
import dev.drav.glyphworks.test.TestCase;
import dev.drav.glyphworks.test.TestRegistry;
import dev.drav.glyphworks.test.TestSuite;

/**
 * Suite {@code "fluid_grid_transfer"} — integration tests for
 * {@link dev.drav.glyphworks.fluid.handlers.FluidGridTypeHandler}.
 *
 * <h3>Block reference</h3>
 * <ul>
 * <li><b>Tank</b> ({@code Glyphworks_Fluid_Tank}): BIDIRECTIONAL face, 16 000
 * L,
 * no auto-refill/drain. Used for exact-amount tests.</li>
 * <li><b>Source</b> ({@code Glyphworks_Fluid_Source}): OUTPUT face, refilled to
 * capacity every tick by {@code FluidSourceSystem}. Used for throughput
 * tests.</li>
 * <li><b>Sink</b> ({@code Glyphworks_Fluid_Sink}): INPUT face, drained to empty
 * every tick by {@code FluidSinkSystem}. Used for throughput tests.</li>
 * <li><b>Pipe</b> ({@code Glyphworks_Fluid_Pipe}): BIDIRECTIONAL face, no
 * container. Pure relay.</li>
 * </ul>
 *
 * <h3>Face orientation</h3>
 * Source, Sink, and Tank blocks define their face as {@code Up} in JSON space.
 * When testing horizontal East–West layouts the block must be rotated so that
 * face points toward its neighbour:
 * <ul>
 * <li>Up → East: {@link FluidTestUtil#ROTATION_EAST}</li>
 * <li>Up → West: {@link FluidTestUtil#ROTATION_WEST}</li>
 * </ul>
 */
public final class FluidGridTransferTests {

    // ── Block IDs ────────────────────────────────────────────────────────────

    private static final String TANK_ID = "Glyphworks_Fluid_Tank";
    private static final String SOURCE_ID = "Glyphworks_Fluid_Source";
    private static final String SINK_ID = "Glyphworks_Fluid_Sink";
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
                .test(rootChangesOnTopologyBreak());
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    /**
     * Places a block and registers grid connections.
     * Uses the Up-face rotation constant to orient the block eastward or westward
     * when needed.
     */
    private static void placeTank(World w, int x, int y, int z, int rotation) {
        FluidTestUtil.setBlockWithRotation(w, x, y, z, TANK_ID, rotation);
        PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
    }

    private static void placePipe(World w, int x, int y, int z) {
        w.setBlock(x, y, z, PIPE_ID);
        PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
    }

    /**
     * Standard 4-block East–West layout using pure storage Tanks:
     *
     * <pre>
     *   Tank(ox) ←BIDIR→ Pipe(ox+1) ←BIDIR→ Pipe(ox+2) ←BIDIR→ Tank(ox+3)
     * </pre>
     *
     * All blocks have an Up face in JSON space. The rotation constants in
     * {@link FluidTestUtil} are defined for blocks with a <em>Down</em> face,
     * so they are inverted here: {@code ROTATION_WEST} turns Up→East, and
     * {@code ROTATION_EAST} turns Up→West.
     */
    private static void placeTankLayout(World w, int ox, int oy, int oz) {
        placeTank(w, ox,     oy, oz, FluidTestUtil.ROTATION_WEST); // Up → East
        placePipe(w, ox + 1, oy, oz);
        placePipe(w, ox + 2, oy, oz);
        placeTank(w, ox + 3, oy, oz, FluidTestUtil.ROTATION_EAST); // Up → West
    }

    /**
     * 4-block layout using creative Source (OUTPUT East) and Sink (INPUT West).
     * 
     * <pre>
     *   Source(ox) → Pipe(ox+1) → Pipe(ox+2) → Sink(ox+3)
     * </pre>
     */
    private static void placeSourceSinkLayout(World w, int ox, int oy, int oz) {
        FluidTestUtil.setBlockWithRotation(w, ox, oy, oz, SOURCE_ID, FluidTestUtil.ROTATION_WEST); // Up → East
        PlaceGridBlockEvent.connectBlock(w, new Vector3i(ox, oy, oz));
        placePipe(w, ox + 1, oy, oz);
        placePipe(w, ox + 2, oy, oz);
        FluidTestUtil.setBlockWithRotation(w, ox + 3, oy, oz, SINK_ID, FluidTestUtil.ROTATION_EAST); // Up → West
        PlaceGridBlockEvent.connectBlock(w, new Vector3i(ox + 3, oy, oz));
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
     * <p>
     * Because both tanks are BIDIRECTIONAL, the sink starts draining back as
     * soon as it holds fluid and the system quickly equilibrates. Rather than
     * asserting on the exact split, the test verifies the stable properties
     * that hold at every tick after the first:
     * <ul>
     * <li>Total fluid is conserved: {@code src + sink == FILL_AMOUNT}.</li>
     * <li>Some fluid reached the sink: {@code sink > 0}.</li>
     * <li>The fluid type is correct: {@code sink.getFluidId() == WATER_ID}.</li>
     * </ul>
     */
    private static TestCase tankToTankViaPipes() {
        return new TestCase("tank_to_tank_via_pipes", 6, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTankLayout(w, ox, oy, oz);
                    FluidContainerComponent src = getContainer(w, ox, oy, oz);
                    if (src != null)
                        src.fill(WATER_ID, FILL_AMOUNT);
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent src  = getContainer(w, ox,     oy, oz);
                    FluidContainerComponent sink = getContainer(w, ox + 3, oy, oz);
                    if (src == null || sink == null) return false;
                    return src.getAmount() + sink.getAmount() == FILL_AMOUNT  // conservation
                            && sink.getAmount() > 0                            // fluid reached sink
                            && WATER_ID.equals(sink.getFluidId());             // correct type
                }, "fluid reached sink; total conserved; correct fluid type"));
    }

    /**
     * Test 2 — rate: exactly {@link #RATE} liters move in a single tick.
     *
     * <p>
     * Fills the source with more fluid than the per-tick rate, waits
     * exactly 1 tick, and asserts the sink received exactly {@link #RATE}
     * liters. Validates that the accumulator budget is honoured precisely.
     */
    private static TestCase transferRateIsRespected() {
        return new TestCase("transfer_rate_is_respected", 6, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTankLayout(w, ox, oy, oz);
                    FluidContainerComponent src = getContainer(w, ox, oy, oz);
                    if (src != null)
                        src.fill(WATER_ID, FILL_AMOUNT);
                }))
                .step(Steps.wait(1))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent sink = getContainer(w, ox + 3, oy, oz);
                    return sink != null && sink.getAmount() == RATE;
                }, "exactly RATE liters transferred in one tick"));
    }

    /**
     * Test 3 — pipe locking: after a successful transfer, every relay pipe in
     * the path has its {@code fluidId} set to the transferred fluid type.
     */
    private static TestCase pipeLocksTsFluidAfterTransfer() {
        return new TestCase("pipe_locks_to_fluid_after_transfer", 6, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTankLayout(w, ox, oy, oz);
                    FluidContainerComponent src = getContainer(w, ox, oy, oz);
                    if (src != null)
                        src.fill(WATER_ID, FILL_AMOUNT);
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidPipeComponent p1 = getPipe(w, ox + 1, oy, oz);
                    FluidPipeComponent p2 = getPipe(w, ox + 2, oy, oz);
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
        return new TestCase("pipe_blocks_incompatible_fluid", 6, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTankLayout(w, ox, oy, oz);
                    FluidPipeComponent pipe = getPipe(w, ox + 2, oy, oz);
                    if (pipe != null)
                        pipe.setFluidId(LAVA_ID);
                    FluidContainerComponent src = getContainer(w, ox, oy, oz);
                    if (src != null)
                        src.fill(WATER_ID, FILL_AMOUNT);
                }))
                .step(Steps.wait(TRANSFER_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent src = getContainer(w, ox, oy, oz);
                    FluidContainerComponent sink = getContainer(w, ox + 3, oy, oz);
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
        return new TestCase("no_transfer_when_sink_full", 6, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTankLayout(w, ox, oy, oz);
                    FluidContainerComponent src  = getContainer(w, ox,     oy, oz);
                    FluidContainerComponent sink = getContainer(w, ox + 3, oy, oz);
                    if (src  != null) src.fill(WATER_ID,  TANK_CAPACITY);
                    if (sink != null) sink.fill(WATER_ID, TANK_CAPACITY);
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent src  = getContainer(w, ox,     oy, oz);
                    FluidContainerComponent sink = getContainer(w, ox + 3, oy, oz);
                    return src  != null && src.getAmount()  == TANK_CAPACITY
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
        return new TestCase("sink_fluid_type_lock_blocks_fill", 6, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTankLayout(w, ox, oy, oz);
                    FluidContainerComponent sink = getContainer(w, ox + 3, oy, oz);
                    if (sink != null)
                        sink.fill(LAVA_ID, 100);
                    FluidContainerComponent src = getContainer(w, ox, oy, oz);
                    if (src != null)
                        src.fill(WATER_ID, FILL_AMOUNT);
                }))
                .step(Steps.wait(TRANSFER_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent src = getContainer(w, ox, oy, oz);
                    FluidContainerComponent sink = getContainer(w, ox + 3, oy, oz);
                    return src != null && src.getAmount() == FILL_AMOUNT
                            && sink != null && sink.getAmount() == 100;
                }, "source unchanged; sink retains only Lava when locked to different fluid"));
    }

    /**
     * Test 7 — closest-first ordering: one source, two sinks at different
     * distances.
     *
     * <p>
     * Layout (branching topology, all blocks have Up face in JSON):
     *
     * <pre>
     *   Src(ox,oz) →East→ Pipe(ox+1,oz) →East→ SinkA(ox+2,oz)   [distance 2]
     *                           ↓South
     *                      Pipe(ox+1,oz+1)
     *                           ↓South
     *                      SinkB(ox+1,oz+2)                       [distance 3]
     * </pre>
     *
     * After exactly 1 tick (budget = 25 L), SinkA receives fluid first;
     * SinkB remains empty because the budget is exhausted.
     */
    private static TestCase multiSinkFillsClosestFirst() {
        return new TestCase("multi_sink_fills_closest_first", 4, 4, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    // Source — Up face rotated East (ROTATION_WEST)
                    placeTank(w, ox, oy, oz, FluidTestUtil.ROTATION_WEST);
                    FluidContainerComponent src = getContainer(w, ox, oy, oz);
                    if (src != null)
                        src.fill(WATER_ID, FILL_AMOUNT);
                    // Central relay pipe — connects Source(W), SinkA(E), branch(S)
                    placePipe(w, ox + 1, oy, oz);
                    // SinkA — distance 2, Up face rotated West (ROTATION_EAST)
                    placeTank(w, ox + 2, oy, oz, FluidTestUtil.ROTATION_EAST);
                    // Branch pipe going south — distance 2
                    placePipe(w, ox + 1, oy, oz + 1);
                    // SinkB — distance 3, Up face rotated North (ROTATION_SOUTH)
                    placeTank(w, ox + 1, oy, oz + 2, FluidTestUtil.ROTATION_SOUTH);
                }))
                .step(Steps.wait(1))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent sinkA = getContainer(w, ox + 2, oy, oz);
                    FluidContainerComponent sinkB = getContainer(w, ox + 1, oy, oz + 2);
                    // After 1 tick the budget (25 L) goes to SinkA first (distance 2).
                    // SinkA should have received fluid; SinkB should still be empty.
                    return sinkA != null && sinkA.getAmount() > 0
                            && sinkB != null && sinkB.getAmount() == 0;
                }, "closer sink fills before farther sink within the same tick budget"));
    }

    /**
     * Test 8 — multi-source: two source tanks in the same network both drain.
     *
     * <p>
     * Uses a Y-shaped topology so that the Sink is always closer (distance 3)
     * to each source than the opposite source is (distance 4). This guarantees
     * that regardless of which source the root processes first, each source's
     * BFS exhausts its budget on the Sink before it can reach—and re-fill—the
     * already-drained opposite source.
     *
     * <pre>
     *   SrcA(ox,oz)   →E→  PipeA(ox+1,oz)
     *                              ↓S
     *   Sink(ox+2,oz+1) ←W← PipeC(ox+1,oz+1)   ← central relay
     *                              ↓S
     *   SrcB(ox,oz+2)  →E→  PipeB(ox+1,oz+2)
     * </pre>
     *
     * Distance from each source to Sink = 3 hops (own-pipe→PipeC→Sink).
     * Distance from each source to the other source = 4 hops.
     * With budget = 25 L the Sink is always filled completely before the
     * opposite source could receive anything, so both sources drain.
     */
    private static TestCase multiSourceBothDrain() {
        return new TestCase("multi_source_both_drain", 4, 4, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    // SrcA — Up face → East, ROTATION_WEST
                    placeTank(w, ox, oy, oz,         FluidTestUtil.ROTATION_WEST);
                    placePipe(w, ox + 1, oy, oz);      // PipeA
                    placePipe(w, ox + 1, oy, oz + 1);  // PipeC — central relay
                    placePipe(w, ox + 1, oy, oz + 2);  // PipeB
                    // SrcB — Up face → East, ROTATION_WEST
                    placeTank(w, ox, oy, oz + 2,     FluidTestUtil.ROTATION_WEST);
                    // Sink — Up face → West, ROTATION_EAST
                    placeTank(w, ox + 2, oy, oz + 1, FluidTestUtil.ROTATION_EAST);

                    FluidContainerComponent srcA = getContainer(w, ox, oy, oz);
                    FluidContainerComponent srcB = getContainer(w, ox, oy, oz + 2);
                    // Fill to capacity: neither source has free space, so it cannot
                    // appear as a valid sink in the other source's BFS.
                    if (srcA != null) srcA.fill(WATER_ID, TANK_CAPACITY);
                    if (srcB != null) srcB.fill(WATER_ID, TANK_CAPACITY);
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent srcA = getContainer(w, ox, oy, oz);
                    FluidContainerComponent srcB = getContainer(w, ox, oy, oz + 2);
                    return srcA != null && srcA.getAmount() < TANK_CAPACITY
                            && srcB != null && srcB.getAmount() < TANK_CAPACITY;
                }, "both source tanks drained when two sources share a network"));
    }

    /**
     * Test 9 — network isolation: two separate layouts placed side-by-side
     * (offset in Z) do not exchange fluid across their independent networks.
     *
     * <p>
     * Network A: SrcA(Water) → Pipe → TankA (empty)
     * <br>
     * Network B: SrcB(Lava) → Pipe → TankB (empty)
     *
     * <p>
     * After transfer:
     * <ul>
     * <li>TankA must contain only Water.</li>
     * <li>TankB must contain only Lava.</li>
     * </ul>
     */
    private static TestCase disconnectedNetworksDoNotInterfere() {
        return new TestCase("disconnected_networks_do_not_interfere", 6, 6, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();

                    // Network A at z = oz
                    placeTank(w, ox,     oy, oz,     FluidTestUtil.ROTATION_WEST); // Up → East
                    placePipe(w, ox + 1, oy, oz);
                    placeTank(w, ox + 2, oy, oz,     FluidTestUtil.ROTATION_EAST); // Up → West
                    FluidContainerComponent srcA = getContainer(w, ox, oy, oz);
                    if (srcA != null)
                        srcA.fill(WATER_ID, FILL_AMOUNT);

                    // Network B at z = oz+2 (separated — no shared nodes)
                    placeTank(w, ox,     oy, oz + 2, FluidTestUtil.ROTATION_WEST); // Up → East
                    placePipe(w, ox + 1, oy, oz + 2);
                    placeTank(w, ox + 2, oy, oz + 2, FluidTestUtil.ROTATION_EAST); // Up → West
                    FluidContainerComponent srcB = getContainer(w, ox, oy, oz + 2);
                    if (srcB != null)
                        srcB.fill(LAVA_ID, FILL_AMOUNT);
                }))
                .step(Steps.wait(TRANSFER_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    FluidContainerComponent tankA = getContainer(w, ox + 2, oy, oz);
                    FluidContainerComponent tankB = getContainer(w, ox + 2, oy, oz + 2);
                    return tankA != null && WATER_ID.equals(tankA.getFluidId())
                            && tankB != null && LAVA_ID.equals(tankB.getFluidId())
                            && (tankA.getAmount() == 0 || WATER_ID.equals(tankA.getFluidId()))
                            && (tankB.getAmount() == 0 || LAVA_ID.equals(tankB.getFluidId()));
                }, "network A contains only Water, network B contains only Lava"));
    }

    /**
     * Test 10 — component tracking: after placing a 4-block chain, every node
     * reports the same non-null root via
     * {@link GridGraph#getComponentRoot(Vector3i)}, confirming that the
     * connected-component index is maintained correctly.
     */
    private static TestCase componentRootTracksMembership() {
        return new TestCase("component_root_tracks_membership", 6, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTankLayout(w, ox, oy, oz);
                }))
                .step(Steps.wait(1))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    GridGraph graph = GlyphworksPlugin.get().getGridGraph(w, FLUID_TYPE);
                    if (graph == null)
                        return false;
                    Vector3i r0 = graph.getComponentRoot(new Vector3i(ox, oy, oz));
                    Vector3i r1 = graph.getComponentRoot(new Vector3i(ox + 1, oy, oz));
                    Vector3i r2 = graph.getComponentRoot(new Vector3i(ox + 2, oy, oz));
                    Vector3i r3 = graph.getComponentRoot(new Vector3i(ox + 3, oy, oz));
                    return r0 != null && r0.equals(r1) && r1.equals(r2) && r2.equals(r3);
                }, "all four nodes share the same component root"));
    }

    /**
     * Test 11 — topology split: breaking the middle pipe of a 3-node chain
     * causes the two surviving nodes to belong to two different components.
     *
     * <p>
     * Layout: Tank(ox) — Pipe(ox+1) — Tank(ox+2)
     * <br>
     * After breaking Pipe(ox+1):
     * <ul>
     * <li>Tank(ox) has a root different from Tank(ox+2).</li>
     * <li>Neither root is null.</li>
     * </ul>
     */
    private static TestCase rootChangesOnTopologyBreak() {
        return new TestCase("root_changes_on_topology_break", 5, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    placeTank(w, ox,     oy, oz, FluidTestUtil.ROTATION_WEST); // Up → East
                    placePipe(w, ox + 1, oy, oz);
                    placeTank(w, ox + 2, oy, oz, FluidTestUtil.ROTATION_EAST); // Up → West
                }))
                .step(Steps.wait(1))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    BreakGridBlockEvent.disconnectBlock(w, new Vector3i(ox + 1, oy, oz));
                    w.setBlock(ox + 1, oy, oz, "Empty");
                }))
                .step(Steps.wait(1))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    GridGraph graph = GlyphworksPlugin.get().getGridGraph(w, FLUID_TYPE);
                    if (graph == null)
                        return false;
                    Vector3i rootLeft = graph.getComponentRoot(new Vector3i(ox, oy, oz));
                    Vector3i rootRight = graph.getComponentRoot(new Vector3i(ox + 2, oy, oz));
                    return rootLeft != null && rootRight != null && !rootLeft.equals(rootRight);
                }, "two surviving nodes have different roots after middle pipe is broken"));
    }
}

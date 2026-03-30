package dev.drav.glyphworks.item.tests;

import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
 import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.GlyphworksPlugin;
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
 * Suite {@code "item_grid_transfer"} — integration tests for
 * {@link dev.drav.glyphworks.item.handlers.ItemGridTypeHandler}.
 *
 * <p>
 * Tests use two purpose-built test blocks defined in {@code IO/}:
 * <ul>
 * <li>{@code "Test_Item_Source"} — cube with an {@link ItemContainerBlock}
 * (20 slots) and a single OUTPUT East
 * {@link dev.drav.glyphworks.grid.component.GridComponent}
 * face. Items only flow <em>out</em> of this block.</li>
 * <li>{@code "Test_Item_Sink"} — cube with an {@link ItemContainerBlock}
 * (20 slots) and a single INPUT West face. Items only flow <em>in</em>.</li>
 * </ul>
 *
 * <p>
 * Standard layout (all blocks at the same Y, varying X):
 * 
 * <pre>
 *   Source(ox)  →  Pipe(ox+1)  →  Sink(ox+2)
 * </pre>
 * 
 * After seeding 5 items into the source, the {@code ItemGridTypeHandler} should
 * drain the source and fill the sink within 3 seconds.
 */
public final class ItemGridTransferTests {

    private static final String SOURCE_ID = "Test_Item_Source";
    private static final String SINK_ID = "Test_Item_Sink";
    private static final String PIPE_ID = "Pipe";

    /** Item ID used as test payload — a vanilla Hytale stone block. */
    private static final String ITEM_ID = "Rock_Stone";

    /** Number of item units seeded into the source for each test. */
    private static final int SEED_AMOUNT = 5;

    /** Short wait — enough for one or two grid ticks before seeding or asserting. */
    private static final int SHORT_WAIT = 3;

    /** Grid type used by all item-grid blocks in this suite. */
    private static final GridType ITEM_TYPE = GridType.of("Item");

    private ItemGridTransferTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("item_grid_transfer")
                .test(itemsFlowFromSourceToSink())
                .test(multiHopRelayTransfersItems())
                .test(noTransferWhenSinkIsFull())
                .test(transferRateIsRespected())
                .test(sourceEmptyNoTransfer())
                .test(multiSinkFillsClosestFirst())
                .test(overflowToNextSinkWhenFirstFull())
                .test(multiSourceBothDrain())
                .test(disconnectedNetworksDoNotInterfere())
                .test(componentRootTracksMembership())
                .test(rootChangesOnTopologyBreak());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Places {@code blockId} at the given position and registers its grid
     * connections.
     */
    private static void place(World world, int x, int y, int z, String blockId) {
        world.setBlock(x, y, z, blockId);
        PlaceGridBlockEvent.connectBlock(world, new Vector3i(x, y, z));
    }

    /**
     * Returns the {@link ItemContainerBlock} component for the grid block at
     * {@code pos}, or {@code null} when the lookup fails.
     */
    @Nullable
    private static ItemContainerBlock getBox(World world, Vector3i pos) {
        GridLookup lu = GridLookup.resolve(world.getChunkStore(), pos);
        if (lu == null)
            return null;
        return world.getChunkStore().getStore()
                .getComponent(lu.blockRef(), ItemContainerBlock.getComponentType());
    }

    /**
     * Counts the total number of item units currently held in {@code container}
     * by iterating every slot.
     */
    private static int countItems(ItemContainer container) {
        if (container == null)
            return -1;
        int total = 0;
        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack != null && !stack.isEmpty()) {
                total += stack.getQuantity();
            }
        }
        return total;
    }

    /**
     * Seeds {@code amount} units of {@link #ITEM_ID} into {@code container},
     * ignoring filters.
     */
    private static void seed(ItemContainer container, int amount) {
        if (container == null)
            return;
        container.addItemStack(new ItemStack(ITEM_ID, amount), false, false, false);
    }

    /**
     * Fills every slot of {@code container} with the maximum stack size for
     * {@link #ITEM_ID} as reported by the item registry at runtime. Uses
     * {@link ItemContainer#setItemStackForSlot} to bypass partial-stack merge
     * logic and write each slot directly, guaranteeing no remaining capacity
     * that the item handler could write into.
     */
    private static void fillContainer(ItemContainer container) {
        if (container == null)
            return;
        DefaultAssetMap<String, Item> assetMap = Item.getAssetMap();
        Item item = (assetMap != null) ? assetMap.getAsset(ITEM_ID) : null;
        int maxStack = (item != null && item != Item.UNKNOWN) ? item.getMaxStack() : 1;
        for (short i = 0; i < container.getCapacity(); i++) {
            container.setItemStackForSlot(i, new ItemStack(ITEM_ID, maxStack), false);
        }
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Test 1: items flow from a source through one pipe into a sink.
     *
     * <p>
     * Layout:
     * 
     * <pre>
     *   Source(ox)  →  Pipe(ox+1)  →  Sink(ox+2)
     * </pre>
     * 
     * The source is seeded with {@link #SEED_AMOUNT} items before waiting.
     * After 3 seconds, the source must be empty and the sink must
     * hold exactly {@link #SEED_AMOUNT} items.
     */
    private static TestCase itemsFlowFromSourceToSink() {
        return new TestCase("items_flow_from_source_to_sink", 5, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox, oy, oz, SOURCE_ID);
                    place(w, ox + 1, oy, oz, PIPE_ID);
                    place(w, ox + 2, oy, oz, SINK_ID);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock src = getBox(w, new Vector3i(ox, oy, oz));
                    if (src != null)
                        seed(src.getItemContainer(), SEED_AMOUNT);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock src = getBox(w, new Vector3i(ox, oy, oz));
                    ItemContainerBlock sink = getBox(w, new Vector3i(ox + 2, oy, oz));
                    return src != null && countItems(src.getItemContainer()) == 0
                            && sink != null && countItems(sink.getItemContainer()) == SEED_AMOUNT;
                }, "ItemGridTypeHandler transfers all items from the source container to the sink via a relay pipe"));
    }

    /**
     * Test 2: items traverse a multi-hop pipe chain (Source → 3 Pipes → Sink).
     *
     * <p>
     * Layout:
     * 
     * <pre>
     *   Source(ox)  →  Pipe(ox+1)  →  Pipe(ox+2)  →  Pipe(ox+3)  →  Sink(ox+4)
     * </pre>
     * 
     * Verifies that BFS correctly chains relay nodes across more than one hop.
     */
    private static TestCase multiHopRelayTransfersItems() {
        return new TestCase("multi_hop_relay_transfers_items", 7, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox, oy, oz, SOURCE_ID);
                    place(w, ox + 1, oy, oz, PIPE_ID);
                    place(w, ox + 2, oy, oz, PIPE_ID);
                    place(w, ox + 3, oy, oz, PIPE_ID);
                    place(w, ox + 4, oy, oz, SINK_ID);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock src = getBox(w, new Vector3i(ox, oy, oz));
                    if (src != null)
                        seed(src.getItemContainer(), SEED_AMOUNT);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock src = getBox(w, new Vector3i(ox, oy, oz));
                    ItemContainerBlock sink = getBox(w, new Vector3i(ox + 4, oy, oz));
                    return src != null && countItems(src.getItemContainer()) == 0
                            && sink != null && countItems(sink.getItemContainer()) == SEED_AMOUNT;
                }, "items traverse a 3-pipe relay chain to reach the sink"));
    }

    /**
     * Test 3: no items are transferred when the sink container is already full.
     *
     * <p>
     * The sink is pre-filled to its full {@link #SINK_CAPACITY} before the
     * source is seeded. Because the handler can find no free slot in the sink,
     * {@link #SEED_AMOUNT} items must remain in the source after
     * 3 seconds.
     */
    private static TestCase noTransferWhenSinkIsFull() {
        return new TestCase("no_transfer_when_sink_is_full", 5, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox, oy, oz, SOURCE_ID);
                    place(w, ox + 1, oy, oz, PIPE_ID);
                    place(w, ox + 2, oy, oz, SINK_ID);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    // Fill every slot directly via setItemStackForSlot so that each slot
                    // sits at exactly ITEM_MAX_STACK (100) items — truly full with no
                    // remaining capacity for the handler to write into.
                    ItemContainerBlock sink = getBox(w, new Vector3i(ox + 2, oy, oz));
                    if (sink != null)
                        fillContainer(sink.getItemContainer());
                    ItemContainerBlock src = getBox(w, new Vector3i(ox, oy, oz));
                    if (src != null)
                        seed(src.getItemContainer(), SEED_AMOUNT);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock src = getBox(w, new Vector3i(ox, oy, oz));
                    return src != null && countItems(src.getItemContainer()) == SEED_AMOUNT;
                }, "source items remain when the sink container is full"));
    }
    // ── Tests 4–11: new ─────────────────────────────────────────────────────────

    /**
     * Test 4 — rate: the accumulator releases exactly one item per tick.
     *
     * <p>Layout: {@code Source(ox) → Pipe(ox+1) → Sink(ox+2)}
     *
     * <p>After the grid connects ({@link #SHORT_WAIT} ticks), the source is
     * seeded with {@link #SEED_AMOUNT} items. After exactly one more tick the
     * sink must hold exactly one item and the source four.
     */
    private static TestCase transferRateIsRespected() {
        return new TestCase("transfer_rate_is_respected", 5, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox,     oy, oz, SOURCE_ID);
                    place(w, ox + 1, oy, oz, PIPE_ID);
                    place(w, ox + 2, oy, oz, SINK_ID);
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock src = getBox(w, new Vector3i(ox, oy, oz));
                    if (src != null) seed(src.getItemContainer(), SEED_AMOUNT);
                }))
                .step(Steps.wait(1))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock src  = getBox(w, new Vector3i(ox,     oy, oz));
                    ItemContainerBlock sink = getBox(w, new Vector3i(ox + 2, oy, oz));
                    return src  != null && countItems(src.getItemContainer())  == SEED_AMOUNT - 1
                            && sink != null && countItems(sink.getItemContainer()) == 1;
                }, "exactly one item transferred in one tick (rate = 1.0 item/tick)"));
    }

    /**
     * Test 5 — source empty: when the source holds no items no transfer occurs.
     *
     * <p>Layout: {@code Source(ox) → Pipe(ox+1) → Sink(ox+2)}
     *
     * <p>No items are ever seeded; the sink must remain empty after
     * 3 seconds.
     */
    private static TestCase sourceEmptyNoTransfer() {
        return new TestCase("source_empty_no_transfer", 5, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox,     oy, oz, SOURCE_ID);
                    place(w, ox + 1, oy, oz, PIPE_ID);
                    place(w, ox + 2, oy, oz, SINK_ID);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock sink = getBox(w, new Vector3i(ox + 2, oy, oz));
                    return sink != null && countItems(sink.getItemContainer()) == 0;
                }, "sink remains empty when source has no items"));
    }

    /**
     * Test 6 — closest-first: one source, two sinks at different BFS distances.
     *
     * <p>Branch layout:
     * <pre>
     *   Source(ox,oz) → Pipe(ox+1,oz) → SinkA(ox+2,oz)           [distance 2]
     *                         ↓
     *                   Pipe(ox+1,oz+1) → SinkB(ox+2,oz+1)         [distance 3]
     * </pre>
     *
     * <p>After exactly one tick (budget = 1 item), SinkA at distance 2 receives
     * the item; SinkB at distance 3 stays empty.
     */
    private static TestCase multiSinkFillsClosestFirst() {
        return new TestCase("multi_sink_fills_closest_first", 5, 4, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox,     oy, oz,     SOURCE_ID);
                    place(w, ox + 1, oy, oz,     PIPE_ID);
                    place(w, ox + 2, oy, oz,     SINK_ID);   // SinkA, distance 2
                    place(w, ox + 1, oy, oz + 1, PIPE_ID);   // branch pipe, distance 2
                    place(w, ox + 2, oy, oz + 1, SINK_ID);   // SinkB, distance 3
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock src = getBox(w, new Vector3i(ox, oy, oz));
                    if (src != null) seed(src.getItemContainer(), SEED_AMOUNT);
                }))
                // Wait until SinkA (closer) receives at least one item, then immediately
                // assert SinkB (farther) is still empty — proving the closest-first ordering.
                .step(Steps.waitUntil(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock sinkA = getBox(w, new Vector3i(ox + 2, oy, oz));
                    return sinkA != null && countItems(sinkA.getItemContainer()) > 0;
                }, ctx -> 3 * ctx.getWorld().getTps(), "SinkA (distance 2) received at least one item"))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock sinkA = getBox(w, new Vector3i(ox + 2, oy, oz));
                    ItemContainerBlock sinkB = getBox(w, new Vector3i(ox + 2, oy, oz + 1));
                    return sinkA != null && countItems(sinkA.getItemContainer()) > 0
                            && sinkB != null && countItems(sinkB.getItemContainer()) == 0;
                }, "closer sink (distance 2) filled first; farther sink (distance 3) still empty"));
    }

    /**
     * Test 7 — overflow: when the closer sink is at capacity, items spill to
     * the next-closest sink.
     *
     * <p>Same branch layout as Test 6. SinkA is pre-filled completely (all
     * {@link #SINK_CAPACITY} slots at {@link #ITEM_MAX_STACK} via
     * {@link #fillContainer}), so every transfer attempt to SinkA fails
     * immediately and the handler spills to SinkB on the very first tick.
     */
    private static TestCase overflowToNextSinkWhenFirstFull() {
        return new TestCase("overflow_to_next_sink_when_first_full", 5, 4, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox,     oy, oz,     SOURCE_ID);
                    place(w, ox + 1, oy, oz,     PIPE_ID);
                    place(w, ox + 2, oy, oz,     SINK_ID);
                    place(w, ox + 1, oy, oz + 1, PIPE_ID);
                    place(w, ox + 2, oy, oz + 1, SINK_ID);
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    // Fill every slot of SinkA to ITEM_MAX_STACK via setItemStackForSlot,
                    // leaving no writable capacity. All source items must overflow to SinkB.
                    ItemContainerBlock sinkA = getBox(w, new Vector3i(ox + 2, oy, oz));
                    if (sinkA != null)
                        fillContainer(sinkA.getItemContainer());
                    ItemContainerBlock src = getBox(w, new Vector3i(ox, oy, oz));
                    if (src != null) seed(src.getItemContainer(), SEED_AMOUNT);
                }))
                // Wait until overflow reaches SinkB, confirming SinkA was bypassed.
                .step(Steps.waitUntil(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock sinkB = getBox(w, new Vector3i(ox + 2, oy, oz + 1));
                    return sinkB != null && countItems(sinkB.getItemContainer()) > 0;
                }, ctx -> 3 * ctx.getWorld().getTps(), "overflow reached SinkB"))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock sinkB = getBox(w, new Vector3i(ox + 2, oy, oz + 1));
                    return sinkB != null && countItems(sinkB.getItemContainer()) > 0;
                }, "items overflowed from the full SinkA to SinkB"));
    }

    /**
     * Test 8 — multi-source: two source blocks in one network both drain.
     *
     * <p>Y-shaped topology where each source reaches the central sink at
     * distance 3 (shorter than reaching the other source), so neither source
     * can siphon from the other:
     * <pre>
     *   SrcA(ox,oz)    →E→  PipeA(ox+1,oz)
     *                               ↕S
     *   Sink(ox+2,oz+1) ←W← PipeC(ox+1,oz+1)  (central relay)
     *                               ↕S
     *   SrcB(ox,oz+2)  →E→  PipeB(ox+1,oz+2)
     * </pre>
     *
     * <p>After {@link #SHORT_WAIT} ticks both sources must have fewer items than
     * they started with.
     */
    private static TestCase multiSourceBothDrain() {
        return new TestCase("multi_source_both_drain", 5, 5, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox,     oy, oz,     SOURCE_ID);  // SrcA — Output East
                    place(w, ox + 1, oy, oz,     PIPE_ID);    // PipeA
                    place(w, ox + 1, oy, oz + 1, PIPE_ID);    // PipeC (central relay)
                    place(w, ox + 1, oy, oz + 2, PIPE_ID);    // PipeB
                    place(w, ox,     oy, oz + 2, SOURCE_ID);  // SrcB — Output East
                    place(w, ox + 2, oy, oz + 1, SINK_ID);    // Sink — Input West
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock srcA = getBox(w, new Vector3i(ox, oy, oz));
                    ItemContainerBlock srcB = getBox(w, new Vector3i(ox, oy, oz + 2));
                    if (srcA != null) seed(srcA.getItemContainer(), SEED_AMOUNT);
                    if (srcB != null) seed(srcB.getItemContainer(), SEED_AMOUNT);
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock srcA = getBox(w, new Vector3i(ox, oy, oz));
                    ItemContainerBlock srcB = getBox(w, new Vector3i(ox, oy, oz + 2));
                    return srcA != null && countItems(srcA.getItemContainer()) < SEED_AMOUNT
                            && srcB != null && countItems(srcB.getItemContainer()) < SEED_AMOUNT;
                }, "both source blocks drained when two sources share a network"));
    }

    /**
     * Test 9 — network isolation: two separate Source→Pipe→Sink chains placed
     * apart do not exchange items.
     *
     * <p>Only Network A’s source is seeded. After the transfer completes:
     * <ul>
     *   <li>SinkA must hold {@link #SEED_AMOUNT} items.</li>
     *   <li>SinkB must remain empty.</li>
     * </ul>
     * <pre>
     *   NetA: Source(ox,oz)    → Pipe(ox+1,oz)   → Sink(ox+2,oz)    [z=oz]
     *   NetB: Source(ox,oz+3)  → Pipe(ox+1,oz+3) → Sink(ox+2,oz+3)  [z=oz+3]
     * </pre>
     */
    private static TestCase disconnectedNetworksDoNotInterfere() {
        return new TestCase("disconnected_networks_do_not_interfere", 5, 6, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    // Network A
                    place(w, ox,     oy, oz,     SOURCE_ID);
                    place(w, ox + 1, oy, oz,     PIPE_ID);
                    place(w, ox + 2, oy, oz,     SINK_ID);
                    // Network B (2-block gap in Z ensures no accidental connection)
                    place(w, ox,     oy, oz + 3, SOURCE_ID);
                    place(w, ox + 1, oy, oz + 3, PIPE_ID);
                    place(w, ox + 2, oy, oz + 3, SINK_ID);
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    // Only seed Network A.
                    ItemContainerBlock srcA = getBox(w, new Vector3i(ox, oy, oz));
                    if (srcA != null) seed(srcA.getItemContainer(), SEED_AMOUNT);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock sinkA = getBox(w, new Vector3i(ox + 2, oy, oz));
                    ItemContainerBlock sinkB = getBox(w, new Vector3i(ox + 2, oy, oz + 3));
                    return sinkA != null && countItems(sinkA.getItemContainer()) == SEED_AMOUNT
                            && sinkB != null && countItems(sinkB.getItemContainer()) == 0;
                }, "Network A’s items reached SinkA; Network B’s sink stayed empty"));
    }

    /**
     * Test 10 — component tracking: after placing a 4-node chain, every block
     * must report the same non-null root via
     * {@link GridGraph#getComponentRoot(Vector3i)}.
     *
     * <p>Layout: {@code Source(ox) → Pipe(ox+1) → Pipe(ox+2) → Sink(ox+3)}
     */
    private static TestCase componentRootTracksMembership() {
        return new TestCase("component_root_tracks_membership", 7, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox,     oy, oz, SOURCE_ID);
                    place(w, ox + 1, oy, oz, PIPE_ID);
                    place(w, ox + 2, oy, oz, PIPE_ID);
                    place(w, ox + 3, oy, oz, SINK_ID);
                }))
                .step(Steps.wait(1))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    GridGraph graph = GlyphworksPlugin.get().getGridGraph(w, ITEM_TYPE);
                    if (graph == null) return false;
                    Vector3i r0 = graph.getComponentRoot(new Vector3i(ox,     oy, oz));
                    Vector3i r1 = graph.getComponentRoot(new Vector3i(ox + 1, oy, oz));
                    Vector3i r2 = graph.getComponentRoot(new Vector3i(ox + 2, oy, oz));
                    Vector3i r3 = graph.getComponentRoot(new Vector3i(ox + 3, oy, oz));
                    return r0 != null && r0.equals(r1) && r1.equals(r2) && r2.equals(r3);
                }, "all four nodes share the same component root"));
    }

    /**
     * Test 11 — topology split: breaking the relay pipe in a 3-node chain
     * (Source→Pipe→Sink) causes the two surviving nodes to belong to two
     * different components.
     *
     * <p>After breaking {@code Pipe(ox+1)}:
     * <ul>
     *   <li>Source(ox) has a root different from Sink(ox+2).</li>
     *   <li>Neither root is null.</li>
     * </ul>
     */
    private static TestCase rootChangesOnTopologyBreak() {
        return new TestCase("root_changes_on_topology_break", 5, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox,     oy, oz, SOURCE_ID);
                    place(w, ox + 1, oy, oz, PIPE_ID);
                    place(w, ox + 2, oy, oz, SINK_ID);
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
                    GridGraph graph = GlyphworksPlugin.get().getGridGraph(w, ITEM_TYPE);
                    if (graph == null) return false;
                    Vector3i rootLeft  = graph.getComponentRoot(new Vector3i(ox,     oy, oz));
                    Vector3i rootRight = graph.getComponentRoot(new Vector3i(ox + 2, oy, oz));
                    return rootLeft != null && rootRight != null && !rootLeft.equals(rootRight);
                }, "two surviving nodes have different roots after middle pipe is broken"));
    }}

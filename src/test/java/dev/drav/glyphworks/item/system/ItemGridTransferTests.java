package dev.drav.glyphworks.item.system;

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
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.item.ItemTestUtil;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "item_grid_transfer"} — integration tests for
 * {@link dev.drav.glyphworks.item.handlers.ItemGridTypeHandler}.
 *
 * <p>
 * Tests use {@code "Glyphworks_Item_Container"} — a cube with an
 * {@link ItemContainerBlock} (20 slots), an OUTPUT South face, and an INPUT
 * North face — for both source and sink positions. A container placed at
 * {@code oz} only connects South (nothing to its North), so it acts as a pure
 * source. A container placed at {@code oz+N} only connects North through the
 * pipe network (nothing to its South), so it acts as a pure sink.
 *
 * <p>
 * Standard layout (all blocks at the same Y, varying Z):
 *
 * <pre>
 *   Container(oz)  →  Pipe(oz+1)  →  Container(oz+2)
 * </pre>
 *
 * After seeding 5 items into the source container, the
 * {@code ItemGridTypeHandler} should drain it and fill the sink container
 * within 3 seconds.
 */
public final class ItemGridTransferTests {

    private static final String CONTAINER_ID = "Glyphworks_Item_Container";
    private static final String PIPE_ID = "Glyphworks_Item_Pipe";

    /** Item ID used as test payload — a vanilla Hytale stone block. */
    private static final String ITEM_ID = "Rock_Stone";

    /** Number of item units seeded into the source for each test. */
    private static final int SEED_AMOUNT = 5;

    /**
     * Short wait — enough for one or two grid ticks before seeding or asserting.
     */
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
        return ItemTestUtil.getItemContainerBlock(world, pos);
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
        ItemTestUtil.seedItems(container, ITEM_ID, amount);
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
        return new TestCase("items_flow_from_source_to_sink", 3, 3, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox, oy, oz, CONTAINER_ID);
                    place(w, ox, oy, oz + 1, PIPE_ID);
                    place(w, ox, oy, oz + 2, CONTAINER_ID);
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
                    ItemContainerBlock sink = getBox(w, new Vector3i(ox, oy, oz + 2));
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
        return new TestCase("multi_hop_relay_transfers_items", 3, 3, 7)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox, oy, oz, CONTAINER_ID);
                    place(w, ox, oy, oz + 1, PIPE_ID);
                    place(w, ox, oy, oz + 2, PIPE_ID);
                    place(w, ox, oy, oz + 3, PIPE_ID);
                    place(w, ox, oy, oz + 4, CONTAINER_ID);
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
                    ItemContainerBlock sink = getBox(w, new Vector3i(ox, oy, oz + 4));
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
        return new TestCase("no_transfer_when_sink_is_full", 3, 3, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox, oy, oz, CONTAINER_ID);
                    place(w, ox, oy, oz + 1, PIPE_ID);
                    place(w, ox, oy, oz + 2, CONTAINER_ID);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock sink = getBox(w, new Vector3i(ox, oy, oz + 2));
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

    /**
     * Test 4 — rate: the accumulator limits items to at most 1 per tick.
     *
     * <p>
     * Layout: {@code Container(ox) → Pipe(ox+1) → Container(ox+2)}
     *
     * <p>
     * Items are seeded into the source at placement time so the accumulator
     * starts from zero. After {@link #SHORT_WAIT} ticks three properties must hold:
     * <ol>
     * <li><b>Conservation</b> — no items are created or destroyed.</li>
     * <li><b>Non-zero rate</b> — the handler actually transferred something.</li>
     * <li><b>Rate limiting</b> — not all items transferred at once; fewer than
     * {@link #SEED_AMOUNT} items have reached the sink after only
     * {@link #SHORT_WAIT} ticks.</li>
     * </ol>
     */
    private static TestCase transferRateIsRespected() {
        return new TestCase("transfer_rate_is_respected", 3, 3, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox, oy, oz, CONTAINER_ID);
                    place(w, ox, oy, oz + 1, PIPE_ID);
                    place(w, ox, oy, oz + 2, CONTAINER_ID);
                    ItemContainerBlock src = getBox(w, new Vector3i(ox, oy, oz));
                    if (src != null)
                        seed(src.getItemContainer(), SEED_AMOUNT);
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock src = getBox(w, new Vector3i(ox, oy, oz));
                    ItemContainerBlock sink = getBox(w, new Vector3i(ox, oy, oz + 2));
                    if (src == null || sink == null)
                        return false;
                    int srcCount = countItems(src.getItemContainer());
                    int sinkCount = countItems(sink.getItemContainer());
                    if (srcCount + sinkCount != SEED_AMOUNT)
                        return false;
                    if (sinkCount == 0)
                        return false;
                    return sinkCount < SEED_AMOUNT;
                }, "conservation holds; items transferred; not all items sent at once (rate limited to 1 item/tick)"));
    }

    /**
     * Test 5 — source empty: when the source holds no items no transfer occurs.
     *
     * <p>
     * Layout: {@code Source(ox) → Pipe(ox+1) → Sink(ox+2)}
     *
     * <p>
     * No items are ever seeded; the sink must remain empty after
     * 3 seconds.
     */
    private static TestCase sourceEmptyNoTransfer() {
        return new TestCase("source_empty_no_transfer", 3, 3, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox, oy, oz, CONTAINER_ID);
                    place(w, ox, oy, oz + 1, PIPE_ID);
                    place(w, ox, oy, oz + 2, CONTAINER_ID);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock sink = getBox(w, new Vector3i(ox, oy, oz + 2));
                    return sink != null && countItems(sink.getItemContainer()) == 0;
                }, "sink remains empty when source has no items"));
    }

    /**
     * Test 6 — closest-first: one source, two sinks at different BFS distances.
     *
     * <pre>
     *   Source(ox,oz) → Pipe(ox,oz+1) → SinkA(ox,oz+2)             [distance 2]
     *                        →East→
     *                   Pipe(ox+1,oz+1) → SinkB(ox+1,oz+2)          [distance 3]
     * </pre>
     */
    private static TestCase multiSinkFillsClosestFirst() {
        return new TestCase("multi_sink_fills_closest_first", 4, 3, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox, oy, oz, CONTAINER_ID);
                    place(w, ox, oy, oz + 1, PIPE_ID);
                    place(w, ox, oy, oz + 2, CONTAINER_ID);
                    place(w, ox + 1, oy, oz + 1, PIPE_ID);
                    place(w, ox + 1, oy, oz + 2, CONTAINER_ID);
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock src = getBox(w, new Vector3i(ox, oy, oz));
                    if (src != null)
                        seed(src.getItemContainer(), SEED_AMOUNT);
                }))
                .step(Steps.waitUntil(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock sinkA = getBox(w, new Vector3i(ox, oy, oz + 2));
                    return sinkA != null && countItems(sinkA.getItemContainer()) > 0;
                }, ctx -> 3 * ctx.getWorld().getTps(), "SinkA (distance 2) received at least one item"))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock sinkA = getBox(w, new Vector3i(ox, oy, oz + 2));
                    ItemContainerBlock sinkB = getBox(w, new Vector3i(ox + 1, oy, oz + 2));
                    return sinkA != null && countItems(sinkA.getItemContainer()) > 0
                            && sinkB != null && countItems(sinkB.getItemContainer()) == 0;
                }, "closer sink (distance 2) filled first; farther sink (distance 3) still empty"));
    }

    /**
     * Test 7 — overflow: when the closer sink is at capacity, items spill to
     * the next-closest sink.
     *
     * <p>
     * Same branch layout as Test 6. SinkA is pre-filled completely (all
     * {@link #SINK_CAPACITY} slots at {@link #ITEM_MAX_STACK} via
     * {@link #fillContainer}), so every transfer attempt to SinkA fails
     * immediately and the handler spills to SinkB on the very first tick.
     */
    private static TestCase overflowToNextSinkWhenFirstFull() {
        return new TestCase("overflow_to_next_sink_when_first_full", 4, 3, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox, oy, oz, CONTAINER_ID);
                    place(w, ox, oy, oz + 1, PIPE_ID);
                    place(w, ox, oy, oz + 2, CONTAINER_ID);
                    place(w, ox + 1, oy, oz + 1, PIPE_ID);
                    place(w, ox + 1, oy, oz + 2, CONTAINER_ID);
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock sinkA = getBox(w, new Vector3i(ox, oy, oz + 2));
                    if (sinkA != null)
                        fillContainer(sinkA.getItemContainer());
                    ItemContainerBlock src = getBox(w, new Vector3i(ox, oy, oz));
                    if (src != null)
                        seed(src.getItemContainer(), SEED_AMOUNT);
                }))
                .step(Steps.waitUntil(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock sinkB = getBox(w, new Vector3i(ox + 1, oy, oz + 2));
                    return sinkB != null && countItems(sinkB.getItemContainer()) > 0;
                }, ctx -> 3 * ctx.getWorld().getTps(), "overflow reached SinkB"))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock sinkB = getBox(w, new Vector3i(ox + 1, oy, oz + 2));
                    return sinkB != null && countItems(sinkB.getItemContainer()) > 0;
                }, "items overflowed from the full SinkA to SinkB"));
    }

    /**
     * Test 8 — multi-source: two source blocks in one network both drain.
     *
     * <pre>
     *   SrcA(ox,oz)   →OUT(S)→ PipeA(ox,oz+1)
     *                                →East→ PipeC(ox+1,oz+1) →OUT(S)→ Sink(ox+1,oz+2)
     *   SrcB(ox+2,oz) →OUT(S)→ PipeB(ox+2,oz+1)
     * </pre>
     */
    private static TestCase multiSourceBothDrain() {
        return new TestCase("multi_source_both_drain", 5, 3, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox, oy, oz, CONTAINER_ID);
                    place(w, ox, oy, oz + 1, PIPE_ID);
                    place(w, ox + 1, oy, oz + 1, PIPE_ID);
                    place(w, ox + 2, oy, oz + 1, PIPE_ID);
                    place(w, ox + 2, oy, oz, CONTAINER_ID);
                    place(w, ox + 1, oy, oz + 2, CONTAINER_ID);
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock srcA = getBox(w, new Vector3i(ox, oy, oz));
                    ItemContainerBlock srcB = getBox(w, new Vector3i(ox + 2, oy, oz));
                    if (srcA != null)
                        seed(srcA.getItemContainer(), SEED_AMOUNT);
                    if (srcB != null)
                        seed(srcB.getItemContainer(), SEED_AMOUNT);
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock srcA = getBox(w, new Vector3i(ox, oy, oz));
                    ItemContainerBlock srcB = getBox(w, new Vector3i(ox + 2, oy, oz));
                    return srcA != null && countItems(srcA.getItemContainer()) < SEED_AMOUNT
                            && srcB != null && countItems(srcB.getItemContainer()) < SEED_AMOUNT;
                }, "both source blocks drained when two sources share a network"));
    }

    /**
     * Test 9 — network isolation: two separate chains placed apart do not
     * exchange items.
     *
     * <pre>
     *   NetA: Source(ox,oz) → Pipe(ox,oz+1) → Sink(ox,oz+2)          [x=ox]
     *   NetB: Source(ox+3,oz) → Pipe(ox+3,oz+1) → Sink(ox+3,oz+2)   [x=ox+3]
     * </pre>
     */
    private static TestCase disconnectedNetworksDoNotInterfere() {
        return new TestCase("disconnected_networks_do_not_interfere", 6, 3, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox, oy, oz, CONTAINER_ID);
                    place(w, ox, oy, oz + 1, PIPE_ID);
                    place(w, ox, oy, oz + 2, CONTAINER_ID);

                    place(w, ox + 3, oy, oz, CONTAINER_ID);
                    place(w, ox + 3, oy, oz + 1, PIPE_ID);
                    place(w, ox + 3, oy, oz + 2, CONTAINER_ID);
                }))
                .step(Steps.wait(SHORT_WAIT))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    // Only seed Network A
                    ItemContainerBlock srcA = getBox(w, new Vector3i(ox, oy, oz));
                    if (srcA != null)
                        seed(srcA.getItemContainer(), SEED_AMOUNT);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock sinkA = getBox(w, new Vector3i(ox, oy, oz + 2));
                    ItemContainerBlock sinkB = getBox(w, new Vector3i(ox + 3, oy, oz + 2));
                    return sinkA != null && countItems(sinkA.getItemContainer()) == SEED_AMOUNT
                            && sinkB != null && countItems(sinkB.getItemContainer()) == 0;
                }, "Network A’s items reached SinkA; Network B’s sink stayed empty"));
    }

    /**
     * Test 10 — component tracking: after placing a 4-node chain, every block
     * must report the same non-null root via
     * {@link GridGraph#getComponentRoot(Vector3i)}.
     *
     * <p>
     * Layout: {@code Source(ox) → Pipe(ox+1) → Pipe(ox+2) → Sink(ox+3)}
     */
    private static TestCase componentRootTracksMembership() {
        return new TestCase("component_root_tracks_membership", 3, 3, 6)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox, oy, oz, CONTAINER_ID);
                    place(w, ox, oy, oz + 1, PIPE_ID);
                    place(w, ox, oy, oz + 2, PIPE_ID);
                    place(w, ox, oy, oz + 3, CONTAINER_ID);
                }))
                .step(Steps.wait(1))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    GridGraph graph = GlyphworksPlugin.get().getGridModule().getGridGraph(w, ITEM_TYPE);
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
     * Test 11 — topology split: breaking the relay pipe in a 3-node chain
     * (Source→Pipe→Sink) causes the two surviving nodes to belong to two
     * different components.
     *
     * <p>
     * After breaking {@code Pipe(ox+1)}:
     * <ul>
     * <li>Source(ox) has a root different from Sink(ox+2).</li>
     * <li>Neither root is null.</li>
     * </ul>
     */
    private static TestCase rootChangesOnTopologyBreak() {
        return new TestCase("root_changes_on_topology_break", 3, 3, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox, oy, oz, CONTAINER_ID);
                    place(w, ox, oy, oz + 1, PIPE_ID);
                    place(w, ox, oy, oz + 2, CONTAINER_ID);
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
                    GridGraph graph = GlyphworksPlugin.get().getGridModule().getGridGraph(w, ITEM_TYPE);
                    if (graph == null)
                        return false;
                    Vector3i rootLeft = graph.getComponentRoot(new Vector3i(ox, oy, oz));
                    Vector3i rootRight = graph.getComponentRoot(new Vector3i(ox, oy, oz + 2));
                    return rootLeft != null && rootRight != null && !rootLeft.equals(rootRight);
                }, "two surviving nodes have different roots after middle pipe is broken"));
    }
}


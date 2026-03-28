package dev.drav.glyphworks.transfer.item.tests;

import javax.annotation.Nullable;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.thread.TickingThread;

import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.test.Steps;
import dev.drav.glyphworks.test.TestCase;
import dev.drav.glyphworks.test.TestRegistry;
import dev.drav.glyphworks.test.TestSuite;

/**
 * Suite {@code "item_grid_transfer"} — integration tests for
 * {@link dev.drav.glyphworks.transfer.item.ItemGridTypeHandler}.
 *
 * <p>Tests use two purpose-built test blocks defined in {@code IO/}:
 * <ul>
 *   <li>{@code "Test_Item_Source"} — cube with an {@link ItemContainerBlock}
 *       (20 slots) and a single OUTPUT East {@link dev.drav.glyphworks.grid.component.GridComponent}
 *       face. Items only flow <em>out</em> of this block.</li>
 *   <li>{@code "Test_Item_Sink"} — cube with an {@link ItemContainerBlock}
 *       (20 slots) and a single INPUT West face. Items only flow <em>in</em>.</li>
 * </ul>
 *
 * <p>Standard layout (all blocks at the same Y, varying X):
 * <pre>
 *   Source(ox)  →  Pipe(ox+1)  →  Sink(ox+2)
 * </pre>
 * After seeding 5 items into the source, the {@code ItemGridTypeHandler} should
 * drain the source and fill the sink within {@link #WAIT_TICKS}.
 */
public final class ItemGridTransferTests {

    private static final String SOURCE_ID = "Test_Item_Source";
    private static final String SINK_ID   = "Test_Item_Sink";
    private static final String PIPE_ID   = "Pipe";

    /** Item ID used as test payload — a vanilla Hytale stone block. */
    private static final String ITEM_ID   = "Rock_Stone";

    /** Number of item units seeded into the source for each test. */
    private static final int SEED_AMOUNT  = 5;

    /** Wait long enough for SEED_AMOUNT transfers at rate=1 item/tick. */
    private static final int WAIT_TICKS   = 3 * TickingThread.TPS;

    /** Capacity of each test container (from JSON). */
    private static final int SINK_CAPACITY = 20;

    private ItemGridTransferTests() {}

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("item_grid_transfer")
                .test(itemsFlowFromSourceToSink())
                .test(multiHopRelayTransfersItems())
                .test(noTransferWhenSinkIsFull());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Places {@code blockId} at the given position and registers its grid connections. */
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
        if (lu == null) return null;
        return world.getChunkStore().getStore()
                .getComponent(lu.blockRef(), ItemContainerBlock.getComponentType());
    }

    /**
     * Counts the total number of item units currently held in {@code container}
     * by iterating every slot.
     */
    private static int countItems(ItemContainer container) {
        if (container == null) return -1;
        int total = 0;
        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack != null && !stack.isEmpty()) {
                total += stack.getQuantity();
            }
        }
        return total;
    }

    /** Seeds {@code amount} units of {@link #ITEM_ID} into {@code container}, ignoring filters. */
    private static void seed(ItemContainer container, int amount) {
        if (container == null) return;
        container.addItemStack(new ItemStack(ITEM_ID, amount), false, false, false);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Test 1: items flow from a source through one pipe into a sink.
     *
     * <p>Layout:
     * <pre>
     *   Source(ox)  →  Pipe(ox+1)  →  Sink(ox+2)
     * </pre>
     * The source is seeded with {@link #SEED_AMOUNT} items before waiting.
     * After {@link #WAIT_TICKS}, the source must be empty and the sink must
     * hold exactly {@link #SEED_AMOUNT} items.
     */
    private static TestCase itemsFlowFromSourceToSink() {
        return new TestCase("items_flow_from_source_to_sink", 5, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox,     oy, oz, SOURCE_ID);
                    place(w, ox + 1, oy, oz, PIPE_ID);
                    place(w, ox + 2, oy, oz, SINK_ID);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock src = getBox(w, new Vector3i(ox, oy, oz));
                    if (src != null) seed(src.getItemContainer(), SEED_AMOUNT);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock src  = getBox(w, new Vector3i(ox,     oy, oz));
                    ItemContainerBlock sink = getBox(w, new Vector3i(ox + 2, oy, oz));
                    return src  != null && countItems(src.getItemContainer())  == 0
                        && sink != null && countItems(sink.getItemContainer()) == SEED_AMOUNT;
                }, "ItemGridTypeHandler transfers all items from the source container to the sink via a relay pipe"));
    }

    /**
     * Test 2: items traverse a multi-hop pipe chain (Source → 3 Pipes → Sink).
     *
     * <p>Layout:
     * <pre>
     *   Source(ox)  →  Pipe(ox+1)  →  Pipe(ox+2)  →  Pipe(ox+3)  →  Sink(ox+4)
     * </pre>
     * Verifies that BFS correctly chains relay nodes across more than one hop.
     */
    private static TestCase multiHopRelayTransfersItems() {
        return new TestCase("multi_hop_relay_transfers_items", 7, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox,     oy, oz, SOURCE_ID);
                    place(w, ox + 1, oy, oz, PIPE_ID);
                    place(w, ox + 2, oy, oz, PIPE_ID);
                    place(w, ox + 3, oy, oz, PIPE_ID);
                    place(w, ox + 4, oy, oz, SINK_ID);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock src = getBox(w, new Vector3i(ox, oy, oz));
                    if (src != null) seed(src.getItemContainer(), SEED_AMOUNT);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock src  = getBox(w, new Vector3i(ox,     oy, oz));
                    ItemContainerBlock sink = getBox(w, new Vector3i(ox + 4, oy, oz));
                    return src  != null && countItems(src.getItemContainer())  == 0
                        && sink != null && countItems(sink.getItemContainer()) == SEED_AMOUNT;
                }, "items traverse a 3-pipe relay chain to reach the sink"));
    }

    /**
     * Test 3: no items are transferred when the sink container is already full.
     *
     * <p>The sink is pre-filled to its full {@link #SINK_CAPACITY} before the
     * source is seeded. Because the handler can find no free slot in the sink,
     * {@link #SEED_AMOUNT} items must remain in the source after {@link #WAIT_TICKS}.
     */
    private static TestCase noTransferWhenSinkIsFull() {
        return new TestCase("no_transfer_when_sink_is_full", 5, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, ox,     oy, oz, SOURCE_ID);
                    place(w, ox + 1, oy, oz, PIPE_ID);
                    place(w, ox + 2, oy, oz, SINK_ID);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    // Fill the sink to capacity, then seed the source.
                    ItemContainerBlock sink = getBox(w, new Vector3i(ox + 2, oy, oz));
                    if (sink != null) seed(sink.getItemContainer(), SINK_CAPACITY);
                    ItemContainerBlock src  = getBox(w, new Vector3i(ox, oy, oz));
                    if (src  != null) seed(src.getItemContainer(), SEED_AMOUNT);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    ItemContainerBlock src = getBox(w, new Vector3i(ox, oy, oz));
                    return src != null && countItems(src.getItemContainer()) == SEED_AMOUNT;
                }, "source items remain when the sink container is full"));
    }
}

package dev.drav.glyphworks.transfer.item;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import dev.drav.glyphworks.crafting.component.AutoCraftingBenchBlock;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.thread.TickingThread;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.component.FaceMode;
import dev.drav.glyphworks.grid.component.FacePlane;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.grid.type.GridTypeHandler;
import dev.drav.glyphworks.grid.util.GridFaceUtil;

/**
 * Per-tick handler for the {@code "Item"} grid type.
 *
 * <p>
 * Handles four kinds of nodes:
 * <ul>
 * <li><b>Container node</b> (furnace, chest …): OUTPUT/BIDIRECTIONAL face with
 * a non-null {@code containerKey}. Each tick its container is pushed toward
 * reachable sinks via BFS.</li>
 * <li><b>Extractor</b>: OUTPUT face with {@code containerKey = null}. At tick
 * time, pulls items from the block immediately on the opposite side of the
 * face and feeds them into reachable grid sinks. The extractor has no
 * inventory of its own.</li>
 * <li><b>Inserter</b>: INPUT face with {@code containerKey = null}. Never
 * initiates a transfer; acts only as a BFS sink. When reached, items are
 * pushed into the block immediately on the opposite side of the face.</li>
 * <li><b>Pipe</b>: BIDIRECTIONAL face with {@code containerKey = null}.
 * Transparent relay; never a source or sink.</li>
 * </ul>
 *
 * <p>
 * Extractor and inserter helpers require
 * {@link GridComponent#getOriginPosition()} to be non-null (set by
 * {@link dev.drav.glyphworks.grid.event.ChunkLoadGridGraphEvent} and
 * {@link dev.drav.glyphworks.grid.event.PlaceGridBlockEvent}). Nodes whose
 * origin
 * position has not yet been populated skip the external-inventory step
 * silently.
 */
public final class ItemGridTypeHandler implements GridTypeHandler {

    /** Default max items transferred per tick per source→sink path. */
    public static final float DEFAULT_TRANSFER_RATE = 1.0f;

    @Override
    public String typeId() {
        return "Item";
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer,
            @Nonnull ChunkStore chunkStore,
            @Nonnull GridComponent component,
            @Nonnull Ref<ChunkStore> blockRef) {

        // --- Step 1: collect source containers ---
        // Container node → OUTPUT/BIDIRECTIONAL face + non-null containerKey → own
        // container.
        // Extractor → OUTPUT face + null containerKey → adjacent external block.
        // Pipe face → BIDIRECTIONAL + null containerKey → skipped (no source).
        List<ItemContainer> sourceContainers = new ArrayList<>();
        Vector3i originPos = component.getOriginPosition();

        for (FacePlane face : component.getFaces()) {
            FaceMode mode = face.getMode();
            if (mode != FaceMode.OUTPUT && mode != FaceMode.BIDIRECTIONAL)
                continue;

            String key = face.getContainerKey();
            if (key != null) {
                ItemContainer c = resolveContainer(store, blockRef, key);
                if (c != null && hasItems(c))
                    sourceContainers.add(c);
            } else if (mode == FaceMode.OUTPUT) {
                // Extractor: external block on the opposite side of this face.
                if (originPos == null)
                    continue;
                ItemContainer c = resolveAdjacentContainer(chunkStore, originPos, face);
                if (c != null && hasItems(c))
                    sourceContainers.add(c);
            }
            // BIDIRECTIONAL + null containerKey = pipe face → no source contribution.
        }
        if (sourceContainers.isEmpty())
            return;

        // --- Step 2: BFS to find all reachable sinks ---
        // The GridGraph is always authoritative for adjacency (rebuilt on chunk load
        // from persisted GridComponent.neighbors). Use it for both seeding and relay.
        GridGraph gridGraph = GlyphworksPlugin.get().getGridGraph(chunkStore.getWorld(), component.getGridType());

        // originPos may be null on reload: the tick system's ECS instance is different
        // from the one ChunkLoadGridGraphEvent called setOriginPosition() on. Recover
        // by
        // looking ourselves up via any persisted neighbour's graph adjacency set.
        if (originPos == null && gridGraph != null && !component.getNeighbors().isEmpty()) {
            Vector3i firstNeighbor = component.getNeighbors().iterator().next();
            for (Vector3i candidate : gridGraph.getNeighbors(firstNeighbor)) {
                GridLookup cand = GridLookup.resolve(chunkStore, candidate);
                if (cand != null && blockRef.equals(cand.blockRef())) {
                    originPos = candidate;
                    break;
                }
            }
        }

        // Resolve our own lookup to get the rotation needed for face-based seed
        // filtering.
        GridLookup selfLookup = (originPos != null) ? GridLookup.resolve(chunkStore, originPos) : null;

        // BFSEntry carries the full GridLookup (includes rotation, needed for
        // face-matching),
        // the effective rate along the path, and the origin-position of the node that
        // enqueued this entry so we can identify which face the path arrived through.
        // distance = BFS hop count from this node to the discovered sink.
        record BFSEntry(GridLookup lookup, float rate, Vector3i arrivedFrom, int distance) {
        }

        // SinkEntry originPos is taken from GridLookup (always correct) rather than the
        // transient GridComponent.getOriginPosition() which may be null on a reloaded
        // world.
        record SinkEntry(Ref<ChunkStore> ref, GridComponent comp, FacePlane sinkFace, float rate, int distance,
                Vector3i originPos) {
        }

        Set<Ref<ChunkStore>> visited = new HashSet<>();
        visited.add(blockRef);
        Queue<BFSEntry> queue = new ArrayDeque<>();
        List<SinkEntry> sinks = new ArrayList<>();

        // Seed BFS from all graph-adjacent nodes (always up-to-date).
        Set<Vector3i> seedNeighbors = (gridGraph != null && originPos != null)
                ? gridGraph.getNeighbors(originPos)
                : component.getNeighbors();
        for (Vector3i neighborPos : seedNeighbors) {
            // Skip neighbors that are only reachable through this block's own INPUT faces.
            // A source block (e.g. a bench acting as output) is connected to two separate
            // pipe networks: its output-side (OUTPUT face → inserter) and its input-side
            // (INPUT face ← extractor). Seeding BFS into the input-side network would
            // cause items to be mis-routed into sibling benches' input containers instead
            // of reaching the intended inserter/sink.
            if (selfLookup != null) {
                FacePlane connectingFace = findEntryFace(chunkStore, selfLookup, neighborPos);
                if (connectingFace != null && connectingFace.getMode() == FaceMode.INPUT)
                    continue;
            }
            GridLookup lookup = GridLookup.resolve(chunkStore, neighborPos);
            if (lookup == null)
                continue;
            if (!visited.add(lookup.blockRef()))
                continue;
            float rate = Math.min(component.getTransferRate(), lookup.component().getTransferRate());
            queue.add(new BFSEntry(lookup, rate, originPos, 1));
        }

        while (!queue.isEmpty()) {
            BFSEntry entry = queue.poll();
            GridComponent entryComp = entry.lookup().component();
            Vector3i entryPos = entry.lookup().originPos();

            // A node is a terminal if it has ANY face that is not a pure-pipe face
            // (BIDIRECTIONAL + null containerKey). Terminals do not relay the BFS.
            boolean isTerminal = false;
            for (FacePlane face : entryComp.getFaces()) {
                if (face.getMode() != FaceMode.BIDIRECTIONAL || face.getContainerKey() != null) {
                    isTerminal = true;
                    break;
                }
            }

            if (isTerminal) {
                // Identify the specific face the path arrived through, then bind this
                // sink to that face only. Items will go to exactly one container.
                if (entry.arrivedFrom() != null) {
                    FacePlane entryFace = findEntryFace(chunkStore, entry.lookup(), entry.arrivedFrom());
                    if (entryFace != null) {
                        FaceMode mode = entryFace.getMode();
                        String key = entryFace.getContainerKey();
                        if ((mode == FaceMode.INPUT || mode == FaceMode.BIDIRECTIONAL)
                                && (key != null || mode == FaceMode.INPUT)) {
                            sinks.add(new SinkEntry(
                                    entry.lookup().blockRef(), entryComp, entryFace, entry.rate(), entry.distance(),
                                    entry.lookup().originPos()));
                        }
                    }
                }
                // Do NOT relay BFS through terminals.
                continue;
            }

            // Pure pipe node: relay BFS using the GridGraph for neighbor discovery.
            // Use entry.lookup().originPos() (not entryComp.getOriginPosition()) because
            // the latter is a transient field that may be null at tick time.
            Vector3i myPos = entry.lookup().originPos();
            Set<Vector3i> relayNeighbors = (gridGraph != null)
                    ? gridGraph.getNeighbors(myPos)
                    : entryComp.getNeighbors();
            for (Vector3i neighborPos : relayNeighbors) {
                GridLookup lookup = GridLookup.resolve(chunkStore, neighborPos);
                if (lookup == null)
                    continue;
                if (!visited.add(lookup.blockRef()))
                    continue;
                float rate = Math.min(entry.rate(), lookup.component().getTransferRate());
                queue.add(new BFSEntry(lookup, rate, myPos, entry.distance() + 1));
            }
        }

        if (sinks.isEmpty())
            return;

        // --- Step 3: push items from each source toward the closest available sink ---
        // Sinks are sorted closest-first (by BFS hop count). The accumulator is drained
        // once per source. Items overflow to the next-closest sink only when the
        // current
        // sink cannot accept any more.
        sinks.sort(Comparator.comparingInt(SinkEntry::distance));

        for (ItemContainer srcContainer : sourceContainers) {
            int toTransfer = component.drainAccumulator(component.getTransferRate() * dt * TickingThread.TPS);
            if (toTransfer < 1)
                continue;

            for (SinkEntry sink : sinks) {
                if (toTransfer <= 0)
                    break;
                FacePlane sinkFace = sink.sinkFace();
                String sinkKey = sinkFace.getContainerKey();
                ItemContainer sinkContainer;
                // Use the originPos from GridLookup (captured at BFS time, always correct)
                // rather than sink.comp().getOriginPosition() which may be null on reload.
                Vector3i sinkOriginPos = sink.originPos();
                if (sinkKey != null) {
                    sinkContainer = resolveContainer(store, sink.ref(), sinkKey);
                } else {
                    // Inserter: push into the external block on the opposite side of the face.
                    if (sinkOriginPos == null)
                        continue;
                    sinkContainer = resolveAdjacentContainer(chunkStore, sinkOriginPos, sinkFace);
                }
                if (sinkContainer == null)
                    continue;

                toTransfer -= moveItems(srcContainer, sinkContainer, toTransfer);
            }
        }
    }

    /**
     * Identifies which face of {@code terminal} the BFS path arrived through.
     *
     * <p>
     * For each face, the "outer cell" is:
     * {@code terminalOrigin + rotate(face.position) + rotate(face.normal)}.
     * The face whose outer cell (after filler-cell resolution for multi-block
     * structures) maps to {@code arrivedFromOriginPos} is the entry face.
     */
    @Nullable
    private static FacePlane findEntryFace(
            @Nonnull ChunkStore chunkStore,
            @Nonnull GridLookup terminal,
            @Nonnull Vector3i arrivedFromOriginPos) {
        Vector3i originPos = terminal.originPos();
        for (FacePlane face : terminal.component().getFaces()) {
            BlockFace worldNormal = GridFaceUtil.rotateBlockFace(face.getNormal(), terminal.rotation());
            if (worldNormal == BlockFace.None)
                continue;
            Vector3i worldFaceCell = GridFaceUtil.addOffset(
                    originPos, GridFaceUtil.rotateFacePosition(face.getPosition(), terminal.rotation()));
            Vector3i outsidePos = GridFaceUtil.addOffset(worldFaceCell, worldNormal);
            // Direct match (1×1 blocks).
            if (outsidePos.equals(arrivedFromOriginPos))
                return face;
            // Multi-block: the outer cell may be a filler; resolve to its origin.
            GridLookup outsideLookup = GridLookup.resolve(chunkStore, outsidePos);
            if (outsideLookup != null && outsideLookup.originPos().equals(arrivedFromOriginPos))
                return face;
        }
        return null;
    }

    /**
     * Resolves the inventory container of the block sitting directly on the
     * opposite side of {@code face} relative to {@code originPos}.
     *
     * <p>
     * The face normal is first rotated to world-space using the block's
     * placement rotation (obtained via {@link GridLookup#resolve}), then
     * {@link GridFaceUtil#opposite} is applied to reach the adjacent position.
     * For 1×1 blocks {@code face.getPosition()} is {@code (0,0,0)} and the
     * calculation reduces to {@code originPos + opposite(worldNormal)}.
     *
     * @return the container at the adjacent position, or {@code null} if the
     *         chunk is unloaded, the block has no entity, or no inventory
     *         component.
     */
    @Nullable
    private static ItemContainer resolveAdjacentContainer(
            @Nonnull ChunkStore chunkStore,
            @Nonnull Vector3i originPos,
            @Nonnull FacePlane face) {
        GridLookup self = GridLookup.resolve(chunkStore, originPos);
        if (self == null)
            return null;

        BlockFace worldNormal = GridFaceUtil.rotateBlockFace(face.getNormal(), self.rotation());
        if (worldNormal == BlockFace.None)
            return null;

        Vector3i worldFaceCell = GridFaceUtil.addOffset(
                originPos, GridFaceUtil.rotateFacePosition(face.getPosition(), self.rotation()));
        Vector3i adjacentPos = GridFaceUtil.addOffset(worldFaceCell, GridFaceUtil.opposite(worldNormal));

        return resolveExternalInventory(chunkStore, adjacentPos);
    }

    /**
     * Looks up the item inventory of any block (grid or non-grid) at {@code pos}.
     *
     * <p>
     * Resolution order: {@link ProcessingBenchBlock} (returns combined
     * container) → {@link ItemContainerBlock} (returns its single container).
     *
     * @return the container, or {@code null} if the position is unloaded / has no
     *         inventory component.
     */
    @Nullable
    private static ItemContainer resolveExternalInventory(
            @Nonnull ChunkStore chunkStore,
            @Nonnull Vector3i pos) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        if (chunkRef == null || !chunkRef.isValid())
            return null;

        Store<ChunkStore> worldStore = chunkStore.getStore();
        BlockComponentChunk bcc = worldStore.getComponent(chunkRef, BlockComponentChunk.getComponentType());
        if (bcc == null)
            return null;

        Ref<ChunkStore> blockRef = bcc.getEntityReference(ChunkUtil.indexBlockInColumn(pos.x, pos.y, pos.z));

        // pos may be a filler cell of a multi-block structure (e.g. a double chest that
        // occupies 2×1×1). Resolve to the origin block exactly as GridLookup does.
        if (blockRef == null) {
            WorldChunk worldChunk = chunkStore.getWorld().getChunkIfLoaded(chunkIndex);
            if (worldChunk == null)
                return null;
            int filler = worldChunk.getFiller(pos.x, pos.y, pos.z);
            if (filler == FillerBlockUtil.NO_FILLER)
                return null;
            Vector3i originPos = new Vector3i(
                    pos.x - FillerBlockUtil.unpackX(filler),
                    pos.y - FillerBlockUtil.unpackY(filler),
                    pos.z - FillerBlockUtil.unpackZ(filler));
            long originChunkIndex = ChunkUtil.indexChunkFromBlock(originPos.x, originPos.z);
            Ref<ChunkStore> originChunkRef = chunkStore.getChunkReference(originChunkIndex);
            if (originChunkRef == null || !originChunkRef.isValid())
                return null;
            BlockComponentChunk originBcc = worldStore.getComponent(originChunkRef,
                    BlockComponentChunk.getComponentType());
            if (originBcc == null)
                return null;
            blockRef = originBcc
                    .getEntityReference(ChunkUtil.indexBlockInColumn(originPos.x, originPos.y, originPos.z));
            if (blockRef == null)
                return null;
        }

        ProcessingBenchBlock pbb = worldStore.getComponent(blockRef, ProcessingBenchBlock.getComponentType());
        if (pbb != null)
            return pbb.getItemContainer();

        AutoCraftingBenchBlock acbb = worldStore.getComponent(blockRef, AutoCraftingBenchBlock.getComponentType());
        if (acbb != null)
            return acbb.getItemContainer();

        ItemContainerBlock icb = worldStore.getComponent(blockRef, ItemContainerBlock.getComponentType());
        if (icb != null)
            return icb.getItemContainer();

        return null;
    }

    /**
     * Resolves the {@link ItemContainer} identified by {@code key} on the grid
     * block referenced by {@code ref}.
     * Maps {@code "input"}/{@code "output"}/{@code "fuel"} for
     * {@link ProcessingBenchBlock}; returns the single container for
     * {@link ItemContainerBlock}.
     */
    @Nullable
    private static ItemContainer resolveContainer(
            @Nonnull Store<ChunkStore> store,
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull String key) {
        ProcessingBenchBlock pbb = store.getComponent(ref, ProcessingBenchBlock.getComponentType());
        if (pbb != null) {
            return switch (key) {
                case "input" -> pbb.getInputContainer();
                case "output" -> pbb.getOutputContainer();
                case "fuel" -> pbb.getFuelContainer();
                default -> null;
            };
        }
        AutoCraftingBenchBlock acbb = store.getComponent(ref, AutoCraftingBenchBlock.getComponentType());
        if (acbb != null) {
            return switch (key) {
                case "input" -> acbb.getInputContainer();
                case "output" -> acbb.getOutputContainer();
                default -> null;
            };
        }
        ItemContainerBlock icb = store.getComponent(ref, ItemContainerBlock.getComponentType());
        if (icb != null)
            return icb.getItemContainer();
        return null;
    }

    /**
     * Moves up to {@code maxItems} items from {@code src} to {@code dst},
     * iterating source slots from index 0. Remainder returned to the source slot
     * by the move API is accounted for in the rate counter.
     *
     * @return the number of items actually moved (may be less than {@code maxItems}
     *         if the destination is full or the source runs out)
     */
    private static int moveItems(
            @Nonnull ItemContainer src,
            @Nonnull ItemContainer dst,
            int maxItems) {
        int remaining = maxItems;
        short cap = src.getCapacity();
        for (short slot = 0; slot < cap && remaining > 0; slot++) {
            ItemStack stack = src.getItemStack(slot);
            if (ItemStack.isEmpty(stack))
                continue;
            int toMove = Math.min(stack.getQuantity(), remaining);
            MoveTransaction<ItemStackTransaction> tx = src.moveItemStackFromSlot(slot, toMove, dst);
            if (!tx.succeeded())
                continue;
            ItemStack remainder = tx.getAddTransaction().getRemainder();
            remaining -= toMove - (ItemStack.isEmpty(remainder) ? 0 : remainder.getQuantity());
        }
        return maxItems - remaining;
    }

    /**
     * Returns {@code true} if {@code container} has at least one non-empty slot.
     */
    private static boolean hasItems(@Nonnull ItemContainer container) {
        short cap = container.getCapacity();
        for (short slot = 0; slot < cap; slot++) {
            if (!ItemStack.isEmpty(container.getItemStack(slot)))
                return true;
        }
        return false;
    }

}

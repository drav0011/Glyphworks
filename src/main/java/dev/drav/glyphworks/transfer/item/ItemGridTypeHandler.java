package dev.drav.glyphworks.transfer.item;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.thread.TickingThread;

import dev.drav.glyphworks.grid.component.FaceMode;
import dev.drav.glyphworks.grid.component.FacePlane;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.grid.type.GridTypeHandler;
import dev.drav.glyphworks.grid.util.GridFaceUtil;

/**
 * Per-tick handler for the {@code "Item"} grid type.
 *
 * <p>Handles four kinds of nodes:
 * <ul>
 *   <li><b>Container node</b> (furnace, chest …): OUTPUT/BIDIRECTIONAL face with
 *       a non-null {@code containerKey}. Each tick its container is pushed toward
 *       reachable sinks via BFS.</li>
 *   <li><b>Extractor</b>: OUTPUT face with {@code containerKey = null}. At tick
 *       time, pulls items from the block immediately on the opposite side of the
 *       face and feeds them into reachable grid sinks. The extractor has no
 *       inventory of its own.</li>
 *   <li><b>Inserter</b>: INPUT face with {@code containerKey = null}. Never
 *       initiates a transfer; acts only as a BFS sink. When reached, items are
 *       pushed into the block immediately on the opposite side of the face.</li>
 *   <li><b>Pipe</b>: BIDIRECTIONAL face with {@code containerKey = null}.
 *       Transparent relay; never a source or sink.</li>
 * </ul>
 *
 * <p>Extractor and inserter helpers require
 * {@link GridComponent#getOriginPosition()} to be non-null (set by
 * {@link dev.drav.glyphworks.grid.event.ChunkLoadGridGraphEvent} and
 * {@link dev.drav.glyphworks.grid.event.PlaceGridBlockEvent}). Nodes whose origin
 * position has not yet been populated skip the external-inventory step silently.
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
        // Container node  → OUTPUT/BIDIRECTIONAL face + non-null containerKey → own container.
        // Extractor       → OUTPUT face + null containerKey → adjacent external block.
        // Pipe face       → BIDIRECTIONAL + null containerKey → skipped (no source).
        List<ItemContainer> sourceContainers = new ArrayList<>();
        Vector3i originPos = component.getOriginPosition();

        for (FacePlane face : component.getFaces()) {
            FaceMode mode = face.getMode();
            if (mode != FaceMode.OUTPUT && mode != FaceMode.BIDIRECTIONAL) continue;

            String key = face.getContainerKey();
            if (key != null) {
                ItemContainer c = resolveContainer(store, blockRef, key);
                if (c != null && hasItems(c)) sourceContainers.add(c);
            } else if (mode == FaceMode.OUTPUT) {
                // Extractor: external block on the opposite side of this face.
                if (originPos == null) continue;
                ItemContainer c = resolveAdjacentContainer(chunkStore, originPos, face);
                if (c != null && hasItems(c)) sourceContainers.add(c);
            }
            // BIDIRECTIONAL + null containerKey = pipe face → no source contribution.
        }
        if (sourceContainers.isEmpty()) return;

        // --- Step 2: BFS to find all reachable sinks ---
        // BFSEntry carries the full GridLookup (includes rotation, needed for face-matching),
        // the effective rate along the path, and the origin-position of the node that
        // enqueued this entry so we can identify which face the path arrived through.
        record BFSEntry(GridLookup lookup, float rate, Vector3i arrivedFrom) {}

        // SinkEntry is bound to the single specific face the BFS path arrived through.
        // This prevents items from being routed to every intake container on a multi-face
        // machine regardless of which pipe actually connects there.
        record SinkEntry(Ref<ChunkStore> ref, GridComponent comp, FacePlane sinkFace, float rate) {}

        Set<Ref<ChunkStore>> visited = new HashSet<>();
        visited.add(blockRef);
        Queue<BFSEntry> queue = new ArrayDeque<>();
        List<SinkEntry> sinks = new ArrayList<>();

        for (Vector3i neighborPos : component.getNeighbors()) {
            GridLookup lookup = GridLookup.resolve(chunkStore, neighborPos);
            if (lookup == null) continue;
            if (!visited.add(lookup.blockRef())) continue;
            float rate = Math.min(component.getTransferRate(), lookup.component().getTransferRate());
            queue.add(new BFSEntry(lookup, rate, originPos));
        }

        while (!queue.isEmpty()) {
            BFSEntry entry = queue.poll();
            GridComponent entryComp = entry.lookup().component();

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
                                    entry.lookup().blockRef(), entryComp, entryFace, entry.rate()));
                        }
                    }
                }
                // Do NOT relay BFS through terminals.
                continue;
            }

            // Pure pipe node: relay BFS, passing this pipe's position as arrivedFrom.
            Vector3i myPos = entryComp.getOriginPosition();
            for (Vector3i neighborPos : entryComp.getNeighbors()) {
                GridLookup lookup = GridLookup.resolve(chunkStore, neighborPos);
                if (lookup == null) continue;
                if (!visited.add(lookup.blockRef())) continue;
                float rate = Math.min(entry.rate(), lookup.component().getTransferRate());
                queue.add(new BFSEntry(lookup, rate, myPos));
            }
        }

        if (sinks.isEmpty()) return;

        // --- Step 3: push items from each source to each qualified sink ---
        // Each SinkEntry is already bound to one face, so we push only to that container.
        for (ItemContainer srcContainer : sourceContainers) {
            for (SinkEntry sink : sinks) {
                FacePlane sinkFace = sink.sinkFace();
                String sinkKey = sinkFace.getContainerKey();
                ItemContainer sinkContainer;
                if (sinkKey != null) {
                    sinkContainer = resolveContainer(store, sink.ref(), sinkKey);
                } else {
                    // Inserter: push into the external block on the opposite side of the face.
                    Vector3i sinkOriginPos = sink.comp().getOriginPosition();
                    if (sinkOriginPos == null) continue;
                    sinkContainer = resolveAdjacentContainer(chunkStore, sinkOriginPos, sinkFace);
                }
                if (sinkContainer == null) continue;

                float effectiveRate = Math.min(component.getTransferRate(), sink.rate());
                int toTransfer = component.drainAccumulator(effectiveRate * dt * TickingThread.TPS);
                if (toTransfer < 1) continue;
                moveItems(srcContainer, sinkContainer, toTransfer);
            }
        }
    }

    /**
     * Identifies which face of {@code terminal} the BFS path arrived through.
     *
     * <p>For each face, the "outer cell" is:
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
            if (worldNormal == BlockFace.None) continue;
            Vector3i worldFaceCell = GridFaceUtil.addOffset(
                    originPos, GridFaceUtil.rotateFacePosition(face.getPosition(), terminal.rotation()));
            Vector3i outsidePos = GridFaceUtil.addOffset(worldFaceCell, worldNormal);
            // Direct match (1×1 blocks).
            if (outsidePos.equals(arrivedFromOriginPos)) return face;
            // Multi-block: the outer cell may be a filler; resolve to its origin.
            GridLookup outsideLookup = GridLookup.resolve(chunkStore, outsidePos);
            if (outsideLookup != null && outsideLookup.originPos().equals(arrivedFromOriginPos)) return face;
        }
        return null;
    }

    /**
     * Resolves the inventory container of the block sitting directly on the
     * opposite side of {@code face} relative to {@code originPos}.
     *
     * <p>The face normal is first rotated to world-space using the block's
     * placement rotation (obtained via {@link GridLookup#resolve}), then
     * {@link GridFaceUtil#opposite} is applied to reach the adjacent position.
     * For 1×1 blocks {@code face.getPosition()} is {@code (0,0,0)} and the
     * calculation reduces to {@code originPos + opposite(worldNormal)}.
     *
     * @return the container at the adjacent position, or {@code null} if the
     *         chunk is unloaded, the block has no entity, or no inventory component.
     */
    @Nullable
    private static ItemContainer resolveAdjacentContainer(
            @Nonnull ChunkStore chunkStore,
            @Nonnull Vector3i originPos,
            @Nonnull FacePlane face) {
        GridLookup self = GridLookup.resolve(chunkStore, originPos);
        if (self == null) return null;

        BlockFace worldNormal = GridFaceUtil.rotateBlockFace(face.getNormal(), self.rotation());
        if (worldNormal == BlockFace.None) return null;

        Vector3i worldFaceCell = GridFaceUtil.addOffset(
                originPos, GridFaceUtil.rotateFacePosition(face.getPosition(), self.rotation()));
        Vector3i adjacentPos = GridFaceUtil.addOffset(worldFaceCell, GridFaceUtil.opposite(worldNormal));

        return resolveExternalInventory(chunkStore, adjacentPos);
    }

    /**
     * Looks up the item inventory of any block (grid or non-grid) at {@code pos}.
     *
     * <p>Resolution order: {@link ProcessingBenchBlock} (returns combined
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
        if (chunkRef == null || !chunkRef.isValid()) return null;

        Store<ChunkStore> worldStore = chunkStore.getStore();
        BlockComponentChunk bcc = worldStore.getComponent(chunkRef, BlockComponentChunk.getComponentType());
        if (bcc == null) return null;

        Ref<ChunkStore> blockRef = bcc.getEntityReference(ChunkUtil.indexBlockInColumn(pos.x, pos.y, pos.z));
        if (blockRef == null) return null;

        ProcessingBenchBlock pbb = worldStore.getComponent(blockRef, ProcessingBenchBlock.getComponentType());
        if (pbb != null) return pbb.getItemContainer();

        ItemContainerBlock icb = worldStore.getComponent(blockRef, ItemContainerBlock.getComponentType());
        if (icb != null) return icb.getItemContainer();

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
                case "input"  -> pbb.getInputContainer();
                case "output" -> pbb.getOutputContainer();
                case "fuel"   -> pbb.getFuelContainer();
                default       -> null;
            };
        }
        ItemContainerBlock icb = store.getComponent(ref, ItemContainerBlock.getComponentType());
        if (icb != null) return icb.getItemContainer();
        return null;
    }

    /**
     * Moves up to {@code maxItems} items from {@code src} to {@code dst},
     * iterating source slots from index 0. Remainder returned to the source slot
     * by the move API is accounted for in the rate counter.
     */
    private static void moveItems(
            @Nonnull ItemContainer src,
            @Nonnull ItemContainer dst,
            int maxItems) {
        int remaining = maxItems;
        short cap = src.getCapacity();
        for (short slot = 0; slot < cap && remaining > 0; slot++) {
            ItemStack stack = src.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) continue;
            int toMove = Math.min(stack.getQuantity(), remaining);
            MoveTransaction<ItemStackTransaction> tx = src.moveItemStackFromSlot(slot, toMove, dst);
            if (!tx.succeeded()) continue;
            ItemStack remainder = tx.getAddTransaction().getRemainder();
            remaining -= toMove - (ItemStack.isEmpty(remainder) ? 0 : remainder.getQuantity());
        }
    }

    /** Returns {@code true} if {@code container} has at least one non-empty slot. */
    private static boolean hasItems(@Nonnull ItemContainer container) {
        short cap = container.getCapacity();
        for (short slot = 0; slot < cap; slot++) {
            if (!ItemStack.isEmpty(container.getItemStack(slot))) return true;
        }
        return false;
    }
}

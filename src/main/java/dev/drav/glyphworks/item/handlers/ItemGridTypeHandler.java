package dev.drav.glyphworks.item.handlers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.crafting.component.AutoCraftingBenchBlock;
import dev.drav.glyphworks.crafting.component.AutoProcessingBenchBlock;
import dev.drav.glyphworks.crafting.component.ManaLiquifierBlock;
import dev.drav.glyphworks.grid.component.FacePlane;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.component.GridTypeEntry;
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

    // -------------------------------------------------------------------------
    // Records
    // -------------------------------------------------------------------------

    private record BFSEntry(GridLookup lookup, float rate, Vector3i arrivedFrom, int distance) {
    }

    private record ItemSinkEntry(Ref<ChunkStore> ref, GridComponent comp, FacePlane sinkFace, float rate,
            int distance, Vector3i originPos) {
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer,
            @Nonnull ChunkStore chunkStore,
            @Nonnull GridComponent component,
            @Nonnull GridTypeEntry entry,
            @Nonnull Ref<ChunkStore> blockRef) {

        String typeId = entry.getGridType().id();
        Vector3i originPos = component.getOriginPosition();

        List<ItemContainer> sourceContainers = collectSourceContainers(
                entry, originPos, store, blockRef, chunkStore);
        if (sourceContainers.isEmpty())
            return;

        GridGraph gridGraph = GlyphworksPlugin.get().getGridModule().getGridGraph(chunkStore.getWorld(), entry.getGridType());
        originPos = resolveOriginPosition(originPos, entry, chunkStore, blockRef, gridGraph);

        List<ItemSinkEntry> sinks = findItemSinks(
                entry, originPos, chunkStore, gridGraph, typeId, blockRef);
        if (sinks.isEmpty())
            return;

        pushItemsToSinks(sourceContainers, sinks, entry, store, chunkStore, dt);
    }

    // -------------------------------------------------------------------------
    // Transfer pipeline
    // -------------------------------------------------------------------------

    private static List<ItemContainer> collectSourceContainers(
            @Nonnull GridTypeEntry entry,
            @Nullable Vector3i originPos,
            @Nonnull Store<ChunkStore> store,
            @Nonnull Ref<ChunkStore> blockRef,
            @Nonnull ChunkStore chunkStore) {
        List<ItemContainer> sourceContainers = new ArrayList<>();
        for (FacePlane face : entry.getFaces()) {
            FilterType mode = face.getMode();
            if (!mode.allowOutput())
                continue;

            String key = face.getContainerKey();
            if (key != null) {
                ItemContainer c = resolveContainer(store, blockRef, key);
                if (c != null && hasItems(c))
                    sourceContainers.add(c);
            } else if (mode == FilterType.ALLOW_OUTPUT_ONLY) {
                if (originPos == null)
                    continue;
                ItemContainer c = resolveAdjacentContainer(chunkStore, originPos, face);
                if (c != null && hasItems(c))
                    sourceContainers.add(c);
            }
        }
        return sourceContainers;
    }

    @Nullable
    private static Vector3i resolveOriginPosition(
            @Nullable Vector3i originPos,
            @Nonnull GridTypeEntry entry,
            @Nonnull ChunkStore chunkStore,
            @Nonnull Ref<ChunkStore> blockRef,
            @Nullable GridGraph gridGraph) {
        if (originPos != null || gridGraph == null || entry.getNeighbors().isEmpty())
            return originPos;

        Vector3i firstNeighbor = entry.getNeighbors().iterator().next();
        for (Vector3i candidate : gridGraph.getNeighbors(firstNeighbor)) {
            GridLookup cand = GridLookup.resolve(chunkStore, candidate);
            if (cand != null && blockRef.equals(cand.blockRef()))
                return candidate;
        }
        return null;
    }

    private static List<ItemSinkEntry> findItemSinks(
            @Nonnull GridTypeEntry entry,
            @Nullable Vector3i originPos,
            @Nonnull ChunkStore chunkStore,
            @Nullable GridGraph gridGraph,
            @Nonnull String typeId,
            @Nonnull Ref<ChunkStore> blockRef) {
        GridLookup selfLookup = (originPos != null) ? GridLookup.resolve(chunkStore, originPos) : null;

        Set<Ref<ChunkStore>> visited = new HashSet<>();
        visited.add(blockRef);
        Queue<BFSEntry> queue = new ArrayDeque<>();
        List<ItemSinkEntry> sinks = new ArrayList<>();

        seedItemBFS(entry, originPos, selfLookup, gridGraph, chunkStore, typeId, visited, queue);

        while (!queue.isEmpty()) {
            BFSEntry bfsNode = queue.poll();
            GridTypeEntry nodeEntry = bfsNode.lookup().component().getEntry(typeId);
            if (nodeEntry == null)
                continue;

            if (isTerminalNode(nodeEntry)) {
                classifyItemSink(bfsNode, chunkStore, typeId, sinks);
                continue;
            }

            expandRelayNeighbors(bfsNode, nodeEntry, gridGraph, chunkStore, typeId, visited, queue);
        }

        return sinks;
    }

    private static void seedItemBFS(
            @Nonnull GridTypeEntry entry,
            @Nullable Vector3i originPos,
            @Nullable GridLookup selfLookup,
            @Nullable GridGraph gridGraph,
            @Nonnull ChunkStore chunkStore,
            @Nonnull String typeId,
            @Nonnull Set<Ref<ChunkStore>> visited,
            @Nonnull Queue<BFSEntry> queue) {
        Set<Vector3i> seedNeighbors = (gridGraph != null && originPos != null)
                ? gridGraph.getNeighbors(originPos)
                : entry.getNeighbors();
        for (Vector3i neighborPos : seedNeighbors) {
            if (selfLookup != null) {
                FacePlane connectingFace = findEntryFace(chunkStore, selfLookup, neighborPos, typeId);
                if (connectingFace != null && !connectingFace.getMode().allowOutput())
                    continue;
            }
            GridLookup lookup = GridLookup.resolve(chunkStore, neighborPos);
            if (lookup == null)
                continue;
            if (!visited.add(lookup.blockRef()))
                continue;
            GridTypeEntry neighborEntry = lookup.component().getEntry(typeId);
            if (neighborEntry == null)
                continue;
            float rate = Math.min(entry.getTransferRate(), neighborEntry.getTransferRate());
            queue.add(new BFSEntry(lookup, rate, originPos, 1));
        }
    }

    private static boolean isTerminalNode(@Nonnull GridTypeEntry entry) {
        for (FacePlane face : entry.getFaces()) {
            if (face.getMode() != FilterType.ALLOW_ALL || face.getContainerKey() != null)
                return true;
        }
        return false;
    }

    private static void classifyItemSink(
            @Nonnull BFSEntry bfsNode,
            @Nonnull ChunkStore chunkStore,
            @Nonnull String typeId,
            @Nonnull List<ItemSinkEntry> sinks) {
        if (bfsNode.arrivedFrom() == null)
            return;
        FacePlane entryFace = findEntryFace(chunkStore, bfsNode.lookup(), bfsNode.arrivedFrom(), typeId);
        if (entryFace == null)
            return;
        FilterType mode = entryFace.getMode();
        String key = entryFace.getContainerKey();
        if (mode.allowInput() && (key != null || !mode.allowOutput())) {
            sinks.add(new ItemSinkEntry(
                    bfsNode.lookup().blockRef(), bfsNode.lookup().component(), entryFace,
                    bfsNode.rate(), bfsNode.distance(), bfsNode.lookup().originPos()));
        }
    }

    private static void expandRelayNeighbors(
            @Nonnull BFSEntry bfsNode,
            @Nonnull GridTypeEntry nodeEntry,
            @Nullable GridGraph gridGraph,
            @Nonnull ChunkStore chunkStore,
            @Nonnull String typeId,
            @Nonnull Set<Ref<ChunkStore>> visited,
            @Nonnull Queue<BFSEntry> queue) {
        Vector3i myPos = bfsNode.lookup().originPos();
        Set<Vector3i> relayNeighbors = (gridGraph != null)
                ? gridGraph.getNeighbors(myPos)
                : nodeEntry.getNeighbors();
        for (Vector3i neighborPos : relayNeighbors) {
            GridLookup lookup = GridLookup.resolve(chunkStore, neighborPos);
            if (lookup == null)
                continue;
            if (!visited.add(lookup.blockRef()))
                continue;
            GridTypeEntry neighborEntry = lookup.component().getEntry(typeId);
            if (neighborEntry == null)
                continue;
            float rate = Math.min(bfsNode.rate(), neighborEntry.getTransferRate());
            queue.add(new BFSEntry(lookup, rate, myPos, bfsNode.distance() + 1));
        }
    }

    private static void pushItemsToSinks(
            @Nonnull List<ItemContainer> sourceContainers,
            @Nonnull List<ItemSinkEntry> sinks,
            @Nonnull GridTypeEntry entry,
            @Nonnull Store<ChunkStore> store,
            @Nonnull ChunkStore chunkStore,
            float dt) {
        sinks.sort(Comparator.comparingInt(ItemSinkEntry::distance));

        for (ItemContainer srcContainer : sourceContainers) {
            int toTransfer = entry.drainAccumulator(entry.getTransferRate() * dt * chunkStore.getWorld().getTps());
            if (toTransfer < 1)
                continue;

            for (ItemSinkEntry sink : sinks) {
                if (toTransfer <= 0)
                    break;
                ItemContainer sinkContainer = resolveSinkContainer(
                        sink, store, chunkStore);
                if (sinkContainer == null)
                    continue;
                toTransfer -= moveItems(srcContainer, sinkContainer, toTransfer);
            }
        }
    }

    @Nullable
    private static ItemContainer resolveSinkContainer(
            @Nonnull ItemSinkEntry sink,
            @Nonnull Store<ChunkStore> store,
            @Nonnull ChunkStore chunkStore) {
        String sinkKey = sink.sinkFace().getContainerKey();
        if (sinkKey != null)
            return resolveContainer(store, sink.ref(), sinkKey);
        if (sink.originPos() == null)
            return null;
        return resolveAdjacentContainer(chunkStore, sink.originPos(), sink.sinkFace());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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
            @Nonnull Vector3i arrivedFromOriginPos,
            @Nonnull String typeId) {
        Vector3i originPos = terminal.originPos();
        GridTypeEntry terminalEntry = terminal.component().getEntry(typeId);
        if (terminalEntry == null)
            return null;
        for (FacePlane face : terminalEntry.getFaces()) {
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
     * Resolution order: {@link AutoProcessingBenchBlock} (returns combined
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

        AutoProcessingBenchBlock apbb = worldStore.getComponent(blockRef, AutoProcessingBenchBlock.getComponentType());
        if (apbb != null)
            return apbb.getItemContainer();

        AutoCraftingBenchBlock acbb = worldStore.getComponent(blockRef, AutoCraftingBenchBlock.getComponentType());
        if (acbb != null)
            return acbb.getItemContainer();

        ManaLiquifierBlock mlb = worldStore.getComponent(blockRef, ManaLiquifierBlock.getComponentType());
        if (mlb != null)
            return mlb.getInputContainer();

        ItemContainerBlock icb = worldStore.getComponent(blockRef, ItemContainerBlock.getComponentType());
        if (icb != null)
            return icb.getItemContainer();

        return null;
    }

    /**
     * Resolves the {@link ItemContainer} identified by {@code key} on the grid
     * block referenced by {@code ref}.
     * Maps {@code "input"}/{@code "output"} for
     * {@link AutoProcessingBenchBlock}; returns the single container for
     * {@link ItemContainerBlock}.
     * 
     * TODO: better expansible resolution for containers, cannot be adding component types to this class every time we add a new block with a container.
     */
    @Nullable
    private static ItemContainer resolveContainer(
            @Nonnull Store<ChunkStore> store,
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull String key) {
        AutoProcessingBenchBlock apbb = store.getComponent(ref, AutoProcessingBenchBlock.getComponentType());
        if (apbb != null) {
            return switch (key) {
                case "input" -> apbb.getInputContainer();
                case "output" -> apbb.getOutputContainer();
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
        ManaLiquifierBlock mlb = store.getComponent(ref, ManaLiquifierBlock.getComponentType());
        if (mlb != null) {
            return switch (key) {
                case "input" -> mlb.getInputContainer();
                case "fuel" -> mlb.getFuelContainer();
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


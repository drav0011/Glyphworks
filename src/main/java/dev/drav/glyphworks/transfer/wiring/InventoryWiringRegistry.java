package dev.drav.glyphworks.transfer.wiring;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.annotation.Nonnull;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;

import dev.drav.glyphworks.transfer.component.TransferComponent;
import dev.drav.glyphworks.transfer.lookups.TransferLookup;

/**
 * Maps root block type IDs to {@link InventoryWirer} callbacks and dispatches
 * inventory wiring at placement / chunk load time.
 *
 * <p>Register at plugin init:
 * <pre>
 *   InventoryWiringRegistry.register("Inserter",  IOInventoryWirer::wireInserter);
 *   InventoryWiringRegistry.register("Extractor", IOInventoryWirer::wireExtractor);
 * </pre>
 *
 * <p>Unregistered block types are silently skipped.
 */
public final class InventoryWiringRegistry {

    @FunctionalInterface
    public interface InventoryWirer {
        void wire(TransferComponent transfer, Vector3i pos, ChunkStore chunkStore);
    }

    private static final Map<String, InventoryWirer> REGISTRY = new HashMap<>();

    private InventoryWiringRegistry() {}

    public static void register(@Nonnull String rootBlockTypeId, @Nonnull InventoryWirer wirer) {
        REGISTRY.put(rootBlockTypeId, wirer);
    }

    /**
     * Looks up the root block type at {@code pos}, finds the registered wirer, and calls it.
     * No-op if the block type is unregistered or the chunk is not loaded.
     */
    public static void wire(@Nonnull ChunkStore chunkStore, @Nonnull TransferComponent transfer, @Nonnull Vector3i pos) {
        BlockType blockType = chunkStore.getWorld().getBlockType(pos.x, pos.y, pos.z);
        if (blockType == null) return;

        // Navigate from a state variant back to the root block type.
        String baseKey = blockType.getDefaultStateKey();
        BlockType rootBlockType = (baseKey != null)
                ? BlockType.getAssetMap().getAsset(baseKey)
                : blockType;
        if (rootBlockType == null) return;

        InventoryWirer wirer = REGISTRY.get(rootBlockType.getId());
        if (wirer == null) return;

        wirer.wire(transfer, pos, chunkStore);
    }

    // -------------------------------------------------------------------------
    // Neighbor re-wire helpers
    // -------------------------------------------------------------------------

    private static final int[][] SIX_DIRS = {
        {1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}
    };

    /**
     * Collects the origin cell and every filler cell belonging to the block placed at
     * {@code origin}. Uses a BFS that follows filler offsets pointing back to the origin,
     * so it correctly handles multi-block structures of any size.
     *
     * <p>This method reads {@code getFiller()} data, so it must be called while the block
     * is still present in the world (i.e. before a break is processed).
     */
    public static Set<Vector3i> collectOccupiedCells(
            @Nonnull Vector3i origin, @Nonnull ChunkStore chunkStore) {
        Set<Vector3i> occupied = new HashSet<>();
        occupied.add(origin);

        Queue<Vector3i> queue = new ArrayDeque<>();
        queue.add(origin);

        while (!queue.isEmpty()) {
            Vector3i current = queue.poll();
            for (int[] d : SIX_DIRS) {
                Vector3i neighbor = new Vector3i(
                        current.x + d[0], current.y + d[1], current.z + d[2]);
                if (occupied.contains(neighbor)) continue;

                long chunkIndex = ChunkUtil.indexChunkFromBlock(neighbor.x, neighbor.z);
                WorldChunk worldChunk = chunkStore.getWorld().getChunkIfLoaded(chunkIndex);
                if (worldChunk == null) continue;

                int filler = worldChunk.getFiller(neighbor.x, neighbor.y, neighbor.z);
                if (filler == 0) continue;

                int ox = neighbor.x - FillerBlockUtil.unpackX(filler);
                int oy = neighbor.y - FillerBlockUtil.unpackY(filler);
                int oz = neighbor.z - FillerBlockUtil.unpackZ(filler);
                if (ox == origin.x && oy == origin.y && oz == origin.z) {
                    occupied.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return occupied;
    }

    /**
     * Re-wires every IO block adjacent to the block at {@code pos} (or any of its filler
     * cells). Call at block <em>placement</em> so IO blocks placed before the inventory
     * block retroactively discover the nozzle inventory.
     */
    public static void rewireNeighbors(
            @Nonnull ChunkStore chunkStore, @Nonnull Vector3i pos) {
        rewireOccupied(collectOccupiedCells(pos, chunkStore), chunkStore);
    }

    /**
     * Re-wires every IO block that neighbours any cell in {@code occupied}. Because
     * {@link IOInventoryWirer} now always updates (even to {@code null}), calling this
     * <em>after</em> a block is removed correctly clears stale inventory references on
     * neighbouring IO blocks.
     */
    public static void rewireOccupied(
            @Nonnull Set<Vector3i> occupied, @Nonnull ChunkStore chunkStore) {
        Set<Vector3i> checked = new HashSet<>();
        for (Vector3i cell : occupied) {
            for (int[] d : SIX_DIRS) {
                Vector3i neighbor = new Vector3i(
                        cell.x + d[0], cell.y + d[1], cell.z + d[2]);
                if (occupied.contains(neighbor)) continue; // internal face
                if (!checked.add(neighbor)) continue;      // already checked

                TransferLookup lookup = TransferLookup.resolve(chunkStore, neighbor);
                if (lookup == null) continue;

                wire(chunkStore, lookup.transfer(), neighbor);
            }
        }
    }
}

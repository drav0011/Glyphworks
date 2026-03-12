package dev.drav.glyphworks.grid.state;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.lookup.GridLookup;

/**
 * Maps root block type IDs to their {@link GridStateComputer} and applies visual state
 * updates for any {@link GridComponent} block.
 *
 * <p>Register block types at plugin init:
 * <pre>
 *     GridStateRegistry.register("Pipe", PipeStateComputer::compute);
 * </pre>
 *
 * <p>Unregistered block types are silently skipped — no-op.
 */
public final class GridStateRegistry {
    private static final Map<String, GridStateComputer> REGISTRY = new HashMap<>();

    private GridStateRegistry() {}

    /**
     * Registers a state computer for a root block type identified by its ID string
     * ({@code BlockType.getId()}).
     */
    public static void register(@Nonnull String rootBlockTypeId, @Nonnull GridStateComputer computer) {
        REGISTRY.put(rootBlockTypeId, computer);
    }

    /**
     * Looks up the state computer for the block at {@code pos}, computes the state name,
     * and applies it via {@code chunk.setBlockInteractionState}.
     *
     * <p>No-ops silently if:
     * <ul>
     *   <li>The block type at {@code pos} is not registered</li>
     *   <li>The chunk is not loaded</li>
     *   <li>There is no {@link GridComponent} at {@code pos}</li>
     * </ul>
     */
    public static void applyState(@Nonnull World world, @Nonnull Vector3i pos) {
        BlockType blockType = world.getBlockType(pos.x, pos.y, pos.z);
        if (blockType == null) return;

        // Navigate from a state variant back to the root block type whose ID we key by.
        String baseKey = blockType.getDefaultStateKey();
        BlockType rootBlockType = (baseKey != null)
                ? BlockType.getAssetMap().getAsset(baseKey)
                : blockType;
        if (rootBlockType == null || rootBlockType.getData() == null) return;

        String rootId = rootBlockType.getId();
        GridStateComputer computer = REGISTRY.get(rootId);
        if (computer == null) return; // unregistered block — no-op

        GridLookup lookup = GridLookup.resolve(world.getChunkStore(), pos);
        if (lookup == null) return;

        GridComponent grid = lookup.component();
        String stateName = computer.compute(grid, lookup.originPos(), world);

        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk != null) {
            chunk.setBlockInteractionState(pos.x, pos.y, pos.z, rootBlockType, stateName, true);
        }
    }
}

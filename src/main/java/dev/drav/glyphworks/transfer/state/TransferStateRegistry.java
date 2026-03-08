package dev.drav.glyphworks.transfer.state;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import dev.drav.glyphworks.transfer.component.TransferComponent;
import dev.drav.glyphworks.transfer.lookups.TransferLookup;

/**
 * Maps root block type IDs to their {@link TransferStateComputer} and applies visual state
 * updates generically for any {@link TransferComponent} block.
 *
 * <p>Register block types at plugin init:
 * <pre>
 *     TransferStateRegistry.register("Transfer_PipeNode", PipeStateComputer::compute);
 * </pre>
 *
 * <p>Unregistered block types are silently skipped — no-op.
 */
public final class TransferStateRegistry {

    private static final Logger LOGGER = Logger.getLogger(TransferStateRegistry.class.getName());

    private static final Map<String, TransferStateComputer> REGISTRY = new HashMap<>();

    private TransferStateRegistry() {}

    /**
     * Registers a state computer for a root block type identified by its ID string
     * ({@code BlockType.getId()}).
     */
    public static void register(@Nonnull String rootBlockTypeId, @Nonnull TransferStateComputer computer) {
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
     *   <li>There is no {@link TransferComponent} at {@code pos}</li>
     * </ul>
     */
    public static void applyState(@Nonnull World world, @Nonnull Vector3i pos) {
        BlockType blockType = world.getBlockType(pos.x, pos.y, pos.z);
        if (blockType == null) return;

        // Navigate from a state variant back to the root block type whose ID we key by.
        // getDefaultStateKey() is null on the root and non-null on variants.
        String baseKey = blockType.getDefaultStateKey();
        BlockType rootBlockType = (baseKey != null)
                ? BlockType.getAssetMap().getAsset(baseKey)
                : blockType;
        if (rootBlockType == null || rootBlockType.getData() == null) return;

        String rootId = rootBlockType.getId();
        TransferStateComputer computer = REGISTRY.get(rootId);
        if (computer == null) return; // unregistered block — no-op

        TransferLookup lookup = TransferLookup.resolve(world.getChunkStore(), pos);
        if (lookup == null) return;

        TransferComponent transfer = lookup.transfer();
        String stateName = computer.compute(transfer, pos, world);

        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk != null) {
            chunk.setBlockInteractionState(pos.x, pos.y, pos.z, rootBlockType, stateName, true);
        } else {
            LOGGER.warning("[TransferState] applyState " + pos + " — chunk not loaded, state not applied");
        }
    }
}

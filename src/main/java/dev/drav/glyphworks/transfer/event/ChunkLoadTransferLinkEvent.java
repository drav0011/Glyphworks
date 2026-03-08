package dev.drav.glyphworks.transfer.event;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.transfer.FaceLinkUtil;
import dev.drav.glyphworks.transfer.component.TransferComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Wires transfer network edges for every {@link TransferComponent} that enters
 * the {@link ChunkStore}.
 *
 * <h3>Why a RefChangeSystem instead of iterating the chunk</h3>
 * The ECS archetype index already tracks which entities carry a given component
 * type. {@link RefChangeSystem} is notified directly — no chunk scanning, no
 * iteration over unrelated block entities.
 *
 * <h3>What it does</h3>
 * {@code onComponentAdded} fires whenever a {@code TransferComponent} is
 * attached to a block entity — both on world load (deserialisation) and on
 * placement. In both cases {@link FaceLinkUtil#linkAll} is called:
 * <ul>
 *   <li>Faces whose {@code neighborNodeId} is already set are skipped
 *       (idempotent — placement already linked them via
 *       {@link PlaceTransferableBlockEvent}).</li>
 *   <li>Any face whose neighbor was in an unloaded chunk at placement time will
 *       now find that neighbor loaded and form the missing edge.</li>
 * </ul>
 *
 * <h3>Registration</h3>
 * <pre>
 * this.getChunkStoreRegistry().registerSystem(new ChunkLoadTransferLinkEvent());
 * </pre>
 */
public final class ChunkLoadTransferLinkEvent extends RefChangeSystem<ChunkStore, TransferComponent> {

    @Override
    public ComponentType<ChunkStore, TransferComponent> componentType() {
        return TransferComponent.getComponentType();
    }

    @Override
    public Query<ChunkStore> getQuery() {
        // We only need entities that carry a TransferComponent.
        return Query.and(TransferComponent.getComponentType());
    }

    /**
     * Called by the ECS when a {@code TransferComponent} is added to any block.
     * Covers both chunk load (deserialised blocks) and fresh placement.
     */
    @Override
    public void onComponentAdded(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull TransferComponent transfer,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        ChunkStore chunkStore = store.getExternalData();
        FaceLinkUtil.linkAll(transfer, ref, chunkStore);
        GlyphworksPlugin.get().registerNode(transfer);
    }

    /** No action needed when a component is replaced. */
    @Override
    public void onComponentSet(
            @Nonnull Ref<ChunkStore> ref,
            @Nullable TransferComponent oldTransfer,
            @Nonnull TransferComponent newTransfer,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {}

    /** Called when a chunk unloads — deregister the node from the live index. */
    @Override
    public void onComponentRemoved(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull TransferComponent transfer,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        GlyphworksPlugin.get().unregisterNode(transfer.getNodeId());
    }
}

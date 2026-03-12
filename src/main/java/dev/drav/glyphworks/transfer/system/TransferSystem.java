package dev.drav.glyphworks.transfer.system;

import java.util.List;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.transfer.TransferGraph;
import dev.drav.glyphworks.transfer.component.FacePlane;
import dev.drav.glyphworks.transfer.component.TransferComponent;

/**
 * Per-tick system that pushes items through the transfer network.
 *
 * <p>Each tick, every {@link TransferComponent} with {@code autoPush = true}
 * drives items from its output inventory into the pre-computed sink inventories
 * in closest-first order, up to {@code maxOutputRate} stacks per tick.
 */
public final class TransferSystem extends EntityTickingSystem<ChunkStore> {

    @Override
    public Query<ChunkStore> getQuery() {
        return TransferComponent.getComponentType();
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        TransferComponent source = archetypeChunk.getComponent(index, TransferComponent.getComponentType());
        if (source == null || !source.isAutoPush()) return;

        // Find the output inventory from the first sending face.
        ItemContainer outputInventory = null;
        for (FacePlane face : source.getFaces()) {
            if (face.canSend()) {
                outputInventory = face.getOutputInventory();
                break;
            }
        }
        if (outputInventory == null) return;

        World world = store.getExternalData().getWorld();
        TransferGraph graph = GlyphworksPlugin.get().getGraph(world);
        if (graph == null) return;

        List<TransferGraph.TransferRoute> routes = graph.getRoutes(source.getNodeId());
        if (routes.isEmpty()) return;

        int budget = source.getMaxOutputRate();
        for (TransferGraph.TransferRoute route : routes) {
            if (budget <= 0) break;

            ItemContainer sinkInventory = route.sinkFace().getInputInventory();
            if (sinkInventory == null) continue;

            int routeBudget = Math.min(budget, route.minRate());
            short capacity = outputInventory.getCapacity();
            for (short slot = 0; slot < capacity && routeBudget > 0; slot++) {
                if (ItemStack.isEmpty(outputInventory.getItemStack(slot))) continue;

                MoveTransaction<ItemStackTransaction> tx =
                        outputInventory.moveItemStackFromSlot(slot, sinkInventory);
                if (tx.succeeded()) {
                    routeBudget--;
                    budget--;
                }
            }
        }
    }
}

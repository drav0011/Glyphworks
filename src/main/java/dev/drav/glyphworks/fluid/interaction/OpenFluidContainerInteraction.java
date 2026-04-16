package dev.drav.glyphworks.fluid.interaction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.fluid.FluidItemRegistry;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;

/**
 * Opens a one-slot, read-only container window that displays the fluid amount
 * stored inside the block. The slot holds a stack of the fluid's item
 * representation with a quantity equal to the stored liters, so the client
 * renders it as "x5000" for 5 buckets. The slot is fully locked to prevent
 * any player interaction.
 */
public final class OpenFluidContainerInteraction extends SimpleBlockInteraction {

    @Nonnull
    public static final BuilderCodec<OpenFluidContainerInteraction> CODEC = BuilderCodec.builder(
            OpenFluidContainerInteraction.class,
            OpenFluidContainerInteraction::new,
            SimpleBlockInteraction.CODEC)
            .documentation("Opens a read-only display window showing the fluid amount inside this block.")
            .build();

    @Override
    protected void interactWithBlock(
            @Nonnull World world,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull Vector3i pos,
            @Nonnull CooldownHandler cooldownHandler) {

        Ref<EntityStore> ref = context.getEntity();
        Store<EntityStore> store = ref.getStore();

        Player playerComponent = (Player) commandBuffer.getComponent(ref, Player.getComponentType());
        if (playerComponent == null)
            return;

        Ref<ChunkStore> blockEntityRef = resolveBlockEntity(world, pos);
        if (blockEntityRef == null)
            return;

        FluidContainerComponent fcc = (FluidContainerComponent) world.getChunkStore().getStore()
                .getComponent(blockEntityRef, FluidContainerComponent.getComponentType());
        if (fcc == null)
            return;

        BlockType blockType = world.getBlockType(pos.x, pos.y, pos.z);
        if (blockType == null)
            return;

        WorldChunk worldChunk = world.getChunk(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (worldChunk == null)
            return;

        int rotationIndex = worldChunk.getRotationIndex(pos.x, pos.y, pos.z);

        ItemContainer displayContainer = SimpleItemContainer.getNewContainer((short) 1);
        // DENY_ALL triggers a Hytale engine NPE on shift-click (null transaction in sendUpdate).
        // The item is not stolen — the move fails — but the error is logged. This is a Hytale bug.
        displayContainer.setGlobalFilter(FilterType.DENY_ALL);

        if (fcc.getFluidId() != null) {
            String displayId = FluidItemRegistry.resolveItemId(fcc.getFluidId());
            if (displayId == null)
                displayId = fcc.getFluidId();
            displayContainer.setItemStackForSlot(
                    (short) 0,
                    new ItemStack(displayId, fcc.getAmount(), fcc.getAmount(), fcc.getCapacity(), null),
                    false);
        }

        ContainerBlockWindow window = new ContainerBlockWindow(
                pos.x, pos.y, pos.z, rotationIndex, blockType, displayContainer);
        playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window);
    }

    @Nullable
    private static Ref<ChunkStore> resolveBlockEntity(@Nonnull World world, @Nonnull Vector3i pos) {
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunkRef == null || !chunkRef.isValid())
            return null;

        BlockComponentChunk bcc = (BlockComponentChunk) chunkStore.getStore()
                .getComponent(chunkRef, BlockComponentChunk.getComponentType());
        if (bcc == null)
            return null;

        Ref<ChunkStore> blockEntityRef = bcc.getEntityReference(ChunkUtil.indexBlockInColumn(pos.x, pos.y, pos.z));
        if (blockEntityRef == null || !blockEntityRef.isValid())
            return null;

        return blockEntityRef;
    }

    @Override
    protected void simulateInteractWithBlock(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull World world,
            @Nonnull Vector3i targetBlock) {
    }
}

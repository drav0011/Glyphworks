package dev.drav.glyphworks.item.interaction;

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
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.item.component.ItemSourceComponent;
import dev.drav.glyphworks.util.DeprecatedChunkAccess;

public final class OpenItemSourceInteraction extends SimpleBlockInteraction {

    @Nonnull
    public static final BuilderCodec<OpenItemSourceInteraction> CODEC = BuilderCodec.builder(
            OpenItemSourceInteraction.class,
            OpenItemSourceInteraction::new,
            SimpleBlockInteraction.CODEC)
            .documentation("Opens the item source UI: a selector slot for choosing the item and a read-only display showing the container state.")
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

        Store<ChunkStore> chunkStoreStore = world.getChunkStore().getStore();

        ItemSourceComponent source = (ItemSourceComponent) chunkStoreStore
                .getComponent(blockEntityRef, ItemSourceComponent.getComponentType());
        if (source == null)
            return;

        ItemContainerBlock icb = (ItemContainerBlock) chunkStoreStore
                .getComponent(blockEntityRef, ItemContainerBlock.getComponentType());
        if (icb == null)
            return;

        BlockType blockType = world.getBlockType(pos.x, pos.y, pos.z);
        if (blockType == null)
            return;

        WorldChunk worldChunk = world.getChunk(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (worldChunk == null)
            return;

        int rotationIndex = DeprecatedChunkAccess.getRotationIndex(worldChunk, pos.x, pos.y, pos.z);

        ContainerBlockWindow window = new ContainerBlockWindow(
                pos.x, pos.y, pos.z, rotationIndex, blockType,
                new CombinedItemContainer(source.getSelectorContainer(), icb.getItemContainer()));

        playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Inventory, true, window);
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

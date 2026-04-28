package dev.drav.glyphworks.crafting.interaction;

import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.math.vector.Vector3i;

import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.crafting.component.AutoProcessingBenchBlock;
import dev.drav.glyphworks.crafting.window.AutoProcessingBenchWindow;
import dev.drav.glyphworks.util.DeprecatedChunkAccess;

/**
 * Opens an {@link AutoProcessingBenchWindow} for mana-powered auto processing
 * benches.
 */
public class OpenAutoProcessingBenchInteraction extends SimpleBlockInteraction {

    @Nonnull
    public static final BuilderCodec<OpenAutoProcessingBenchInteraction> CODEC = BuilderCodec.builder(
            OpenAutoProcessingBenchInteraction.class,
            OpenAutoProcessingBenchInteraction::new,
            SimpleBlockInteraction.CODEC)
            .documentation("Opens a processing bench window showing liquid mana as fuel.")
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

        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunkRef == null || !chunkRef.isValid())
            return;

        Store<ChunkStore> chunkStoreStore = chunkStore.getStore();
        BlockComponentChunk blockComponentChunk = (BlockComponentChunk) chunkStoreStore.getComponent(
                chunkRef, BlockComponentChunk.getComponentType());
        if (blockComponentChunk == null)
            return;

        Ref<ChunkStore> blockEntityRef = blockComponentChunk.getEntityReference(
                ChunkUtil.indexBlockInColumn(pos.x, pos.y, pos.z));
        if (blockEntityRef == null || !blockEntityRef.isValid())
            return;

        AutoProcessingBenchBlock apbb = (AutoProcessingBenchBlock) chunkStoreStore.getComponent(
                blockEntityRef, AutoProcessingBenchBlock.getComponentType());
        BenchBlock benchBlock = (BenchBlock) chunkStoreStore.getComponent(
                blockEntityRef, BenchBlock.getComponentType());
        BlockModule.BlockStateInfo blockStateInfo = (BlockModule.BlockStateInfo) chunkStoreStore.getComponent(
                blockEntityRef, BlockModule.BlockStateInfo.getComponentType());

        if (apbb == null || benchBlock == null || blockStateInfo == null)
            return;

        BlockType blockType = world.getBlockType(pos.x, pos.y, pos.z);
        if (blockType == null)
            return;

        UUIDComponent uuidComponent = (UUIDComponent) commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null)
            return;
        UUID uuid = uuidComponent.getUuid();

        WorldChunk worldChunk = world.getChunk(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (worldChunk == null)
            return;
        int rotationIndex = DeprecatedChunkAccess.getRotationIndex(worldChunk, pos.x, pos.y, pos.z);

        int openSoundIndex = blockType.getBench().getLocalOpenSoundEventIndex();
        int closeSoundIndex = blockType.getBench().getLocalCloseSoundEventIndex();

        AutoProcessingBenchWindow window = new AutoProcessingBenchWindow(
                apbb, benchBlock, blockStateInfo,
            pos.x, pos.y, pos.z, rotationIndex, blockType, null);

        Map<UUID, AutoProcessingBenchWindow> windows = apbb.getWindows();
        if (windows.putIfAbsent(uuid, window) != null)
            return;

        if (!playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window)) {
            windows.remove(uuid, window);
            return;
        }

        window.registerCloseEvent(event -> {
            windows.remove(uuid, window);
            BlockType currentBlockType = world.getBlockType(pos);
            if (currentBlockType == null
                    || currentBlockType == BlockType.EMPTY
                    || currentBlockType == BlockType.UNKNOWN)
                return;
            String interactionState = BlockAccessor.getCurrentInteractionState(currentBlockType);
            if (windows.isEmpty()
                    && !"processing".equals(interactionState)
                    && !"process_completed".equals(interactionState)) {
                world.setBlockInteractionState(pos, BenchBlock.getBaseBlockType(currentBlockType),
                        benchBlock.getTierStateName());
            }
            if (closeSoundIndex != 0)
                SoundUtil.playSoundEvent2d(ref, closeSoundIndex, SoundCategory.UI, commandBuffer);
        });

        if (openSoundIndex != 0)
            SoundUtil.playSoundEvent2d(ref, openSoundIndex, SoundCategory.UI, commandBuffer);
    }

    @Override
    protected void simulateInteractWithBlock(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull World world,
            @Nonnull Vector3i targetBlock) {
        // No client-side simulation needed.
    }
}


package dev.drav.glyphworks.grid.interaction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.component.FluidSourceComponent;
import dev.drav.glyphworks.item.component.ItemSourceComponent;

public class ConfigureSourceInteraction extends SimpleBlockInteraction {

    @Nonnull
    public static final BuilderCodec<ConfigureSourceInteraction> CODEC = BuilderCodec.builder(
            ConfigureSourceInteraction.class,
            ConfigureSourceInteraction::new,
            SimpleBlockInteraction.CODEC)
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

        if (itemInHand == null || itemInHand.isEmpty())
            return;

        Player player = resolvePlayer(commandBuffer, context);
        if (player == null || player.getGameMode() != GameMode.Creative)
            return;

        PlayerRef playerRef = resolvePlayerRef(commandBuffer, context);
        if (playerRef == null)
            return;

        Ref<ChunkStore> blockEntityRef = resolveBlockEntity(world, pos);
        if (blockEntityRef == null)
            return;

        Store<ChunkStore> store = world.getChunkStore().getStore();
        String newId = itemInHand.getItemId();

        boolean configured = configureFluidSource(store, blockEntityRef, newId)
                | configureItemSource(store, blockEntityRef, newId);

        if (configured) {
            playerRef.sendMessage(Message.raw("[Glyphworks] Source set to: " + newId));
        }
    }

    private static boolean configureFluidSource(
            @Nonnull Store<ChunkStore> store,
            @Nonnull Ref<ChunkStore> blockEntityRef,
            @Nonnull String newFluidId) {

        FluidSourceComponent fsc = (FluidSourceComponent) store.getComponent(
                blockEntityRef, FluidSourceComponent.getComponentType());
        if (fsc == null)
            return false;

        fsc.setFluidId(newFluidId);

        FluidContainerComponent fcc = (FluidContainerComponent) store.getComponent(
                blockEntityRef, FluidContainerComponent.getComponentType());
        if (fcc != null) {
            fcc.drain(fcc.getAmount());
        }

        return true;
    }

    private static boolean configureItemSource(
            @Nonnull Store<ChunkStore> store,
            @Nonnull Ref<ChunkStore> blockEntityRef,
            @Nonnull String newItemId) {

        ItemSourceComponent isc = (ItemSourceComponent) store.getComponent(
                blockEntityRef, ItemSourceComponent.getComponentType());
        if (isc == null)
            return false;

        isc.setItemId(newItemId);
        return true;
    }

    @Nullable
    private static Player resolvePlayer(
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InteractionContext context) {
        Ref<EntityStore> ref = context.getEntity();
        return (Player) commandBuffer.getComponent(ref, Player.getComponentType());
    }

    @Nullable
    private static PlayerRef resolvePlayerRef(
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InteractionContext context) {
        Ref<EntityStore> ref = context.getEntity();
        return (PlayerRef) commandBuffer.getComponent(ref, PlayerRef.getComponentType());
    }

    @Nullable
    private static Ref<ChunkStore> resolveBlockEntity(@Nonnull World world, @Nonnull Vector3i pos) {
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunkRef == null || !chunkRef.isValid())
            return null;

        Store<ChunkStore> store = chunkStore.getStore();
        BlockComponentChunk bcc = (BlockComponentChunk) store.getComponent(
                chunkRef, BlockComponentChunk.getComponentType());
        if (bcc == null)
            return null;

        Ref<ChunkStore> blockEntityRef = bcc.getEntityReference(
                ChunkUtil.indexBlockInColumn(pos.x, pos.y, pos.z));
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
            @Nonnull Vector3i pos) {
    }
}

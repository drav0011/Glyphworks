package dev.drav.glyphworks.grid.interaction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.crafting.component.ManaLiquifierBlock;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.component.FluidPipeComponent;
import dev.drav.glyphworks.fluid.component.FluidSourceComponent;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.component.GridTypeEntry;
import dev.drav.glyphworks.item.component.ItemSourceComponent;

public class InspectBlockInteraction extends SimpleBlockInteraction {

    @Nonnull
    public static final BuilderCodec<InspectBlockInteraction> CODEC = BuilderCodec.builder(
            InspectBlockInteraction.class,
            InspectBlockInteraction::new,
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

        PlayerRef player = resolvePlayer(commandBuffer, context);
        if (player == null)
            return;

        Ref<ChunkStore> blockEntityRef = resolveBlockEntity(world, pos);
        if (blockEntityRef == null)
            return;

        Store<ChunkStore> store = world.getChunkStore().getStore();
        StringBuilder info = new StringBuilder("[Glyphworks] Block Info:");

        appendFluidInfo(store, blockEntityRef, info);
        appendItemSourceInfo(store, blockEntityRef, info);
        appendFluidSourceInfo(store, blockEntityRef, info);
        appendFluidPipeInfo(store, blockEntityRef, info);
        appendLiquifierInfo(store, blockEntityRef, info);
        appendGridInfo(store, blockEntityRef, info);

        player.sendMessage(Message.raw(info.toString()));
    }

    @Nullable
    private static PlayerRef resolvePlayer(
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

    private static void appendFluidInfo(Store<ChunkStore> store, Ref<ChunkStore> ref, StringBuilder sb) {
        FluidContainerComponent fcc = (FluidContainerComponent) store.getComponent(
                ref, FluidContainerComponent.getComponentType());
        if (fcc == null)
            return;

        String fluid = fcc.getFluidId() != null ? fcc.getFluidId() : "Empty";
        sb.append("\n  Fluid: ").append(fluid)
                .append(" ").append(fcc.getAmount()).append("/").append(fcc.getCapacity()).append("L");
    }

    private static void appendItemSourceInfo(Store<ChunkStore> store, Ref<ChunkStore> ref, StringBuilder sb) {
        ItemSourceComponent isc = (ItemSourceComponent) store.getComponent(
                ref, ItemSourceComponent.getComponentType());
        if (isc == null)
            return;

        sb.append("\n  Item Source: ").append(isc.getItemId()).append(" (infinite)");
    }

    private static void appendFluidSourceInfo(Store<ChunkStore> store, Ref<ChunkStore> ref, StringBuilder sb) {
        FluidSourceComponent fsc = (FluidSourceComponent) store.getComponent(
                ref, FluidSourceComponent.getComponentType());
        if (fsc == null)
            return;

        sb.append("\n  Fluid Source: ").append(fsc.getFluidId()).append(" (infinite)");
    }

    private static void appendFluidPipeInfo(Store<ChunkStore> store, Ref<ChunkStore> ref, StringBuilder sb) {
        FluidPipeComponent fpc = (FluidPipeComponent) store.getComponent(
                ref, FluidPipeComponent.getComponentType());
        if (fpc == null)
            return;

        String locked = fpc.getFluidId() != null ? fpc.getFluidId() : "none";
        sb.append("\n  Fluid Pipe: locked=").append(locked);
    }

    private static void appendLiquifierInfo(Store<ChunkStore> store, Ref<ChunkStore> ref, StringBuilder sb) {
        ManaLiquifierBlock mlb = (ManaLiquifierBlock) store.getComponent(
                ref, ManaLiquifierBlock.getComponentType());
        if (mlb == null)
            return;

        sb.append("\n  Liquifier: progress=").append(String.format("%.1f", mlb.getProcessingProgress()))
                .append("/").append(String.format("%.1f", ManaLiquifierBlock.RECIPE_TIME)).append("s")
                .append(", fuel=").append(String.format("%.1f", mlb.getRemainingFuelEnergy())).append("s");
    }

    private static void appendGridInfo(Store<ChunkStore> store, Ref<ChunkStore> ref, StringBuilder sb) {
        GridComponent gc = (GridComponent) store.getComponent(
                ref, GridComponent.getComponentType());
        if (gc == null)
            return;

        for (GridTypeEntry entry : gc.getEntries()) {
            sb.append("\n  Grid [").append(entry.getGridType().id()).append("]: ")
                    .append(entry.getNeighbors().size()).append(" neighbors, ")
                    .append("rate=").append(entry.getTransferRate());
        }
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

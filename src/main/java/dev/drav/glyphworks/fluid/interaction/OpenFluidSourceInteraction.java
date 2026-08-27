package dev.drav.glyphworks.fluid.interaction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.fluid.util.FluidUtil;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.component.FluidSourceComponent;
import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.container.FluidContainer;

/**
 * Opens a two-slot window for the fluid source block:
 * <ol>
 * <li>The selector slot — player places a fluid item here to choose what the
 * source produces; fully writable ({@code ALLOW_ALL}).</li>
 * <li>The display slot — shows the internal fluid container, always full with
 * the selected fluid; read-only ({@code DENY_ALL}).</li>
 * </ol>
 */
public final class OpenFluidSourceInteraction extends SimpleBlockInteraction {

    @Nonnull
    public static final BuilderCodec<OpenFluidSourceInteraction> CODEC = BuilderCodec.builder(
            OpenFluidSourceInteraction.class,
            OpenFluidSourceInteraction::new,
            SimpleBlockInteraction.CODEC)
            .documentation(
                    "Opens the fluid source UI: a selector slot for choosing the fluid and a read-only display showing the container state.")
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

        Ref<ChunkStore> blockEntityRef = BlockModule.getBlockEntity(world, pos.x, pos.y, pos.z);
        if (blockEntityRef == null)
            return;

        Store<ChunkStore> chunkStoreStore = world.getChunkStore().getStore();

        FluidSourceComponent source = (FluidSourceComponent) chunkStoreStore
                .getComponent(blockEntityRef, FluidSourceComponent.getComponentType());
        if (source == null)
            return;

        FluidContainerComponent fcc = (FluidContainerComponent) chunkStoreStore
                .getComponent(blockEntityRef, FluidContainerComponent.getComponentType());
        if (fcc == null)
            return;

        BlockType blockType = world.getBlockType(pos.x, pos.y, pos.z);
        if (blockType == null)
            return;

        BlockSection blockSection = FluidUtil.getBlockSection(
                world.getChunkStore(), world.getChunkStore().getStore(), pos.x, pos.y, pos.z);
        if (blockSection == null)
            return;

        int rotationIndex = blockSection.getRotationIndex(pos.x, pos.y, pos.z);
        FluidContainer sourceContainer = fcc.getFluidContainer();
        FluidContainer displayContainer = sourceContainer.clone();
        displayContainer.setGlobalFilter(FilterType.DENY_ALL);

        ContainerBlockWindow window = new ContainerBlockWindow(
                pos.x, pos.y, pos.z, rotationIndex, blockType,
                new CombinedItemContainer(source.getSelectorContainer(), displayContainer));

        if (!playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Inventory, true, window)) {
            return;
        }

        EventRegistration<?, ?> displaySyncRegistration = sourceContainer.registerChangeEvent(event ->
            syncDisplayFluidContainer(sourceContainer, displayContainer));

        window.registerCloseEvent(event -> displaySyncRegistration.unregister());
    }

    @Override
    protected void simulateInteractWithBlock(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull World world,
            @Nonnull Vector3i targetBlock) {
    }

    private static void syncDisplayFluidContainer(@Nonnull FluidContainer source, @Nonnull FluidContainer display) {
        display.clear();
        short capacity = source.getCapacity();
        for (short i = 0; i < capacity; i++) {
            FluidStack stack = source.getFluidStack(i);
            if (stack == null || stack.getFluidId() == null) {
                continue;
            }
            display.addFluidStackToSlot(
                    i,
                    new FluidStack(stack.getFluidId(), stack.getAmount()),
                    true,
                    false);
        }
    }
}

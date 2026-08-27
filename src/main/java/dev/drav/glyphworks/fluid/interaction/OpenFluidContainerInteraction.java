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
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.fluid.util.FluidUtil;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.container.FluidContainer;

/**
 * Opens a read-only container window that displays the fluid stored inside the
 * block. The component's own item container (already DENY_ALL, 1 slot) is
 * passed directly to the window — no separate display container needed.
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

        Ref<ChunkStore> blockEntityRef = BlockModule.getBlockEntity(world, pos.x, pos.y, pos.z);
        if (blockEntityRef == null)
            return;

        FluidContainerComponent fcc = (FluidContainerComponent) world.getChunkStore().getStore()
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
            pos.x, pos.y, pos.z, rotationIndex, blockType, displayContainer);
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

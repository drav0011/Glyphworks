package dev.drav.glyphworks.fluid.component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.fluid.FluidItemRegistry;

/**
 * Marks a block as a creative fluid source.
 *
 * <p>
 * The fluid type to produce is determined by whichever fluid item the player
 * places in the single-slot {@link #selectorContainer}. The paired
 * {@link dev.drav.glyphworks.fluid.system.FluidSourceSystem} reads
 * {@link #getSelectedFluidId()} each tick and keeps the block's
 * {@link FluidContainerComponent} filled to capacity with that fluid. When
 * the selector slot is empty the source produces nothing.
 */
public final class FluidSourceComponent implements Component<ChunkStore> {

    public static final BuilderCodec<FluidSourceComponent> CODEC = BuilderCodec
            .builder(FluidSourceComponent.class, () -> new FluidSourceComponent())
            .append(
                    new KeyedCodec<>("Glyphworks_FluidSourceComponent_SelectorContainer", SimpleItemContainer.CODEC),
                    (c, v) -> c.selectorContainer = v,
                    c -> c.selectorContainer)
            .add()
            .build();

    public static ComponentType<ChunkStore, FluidSourceComponent> getComponentType() {
        return GlyphworksPlugin.get().getFluidModule().getFluidSourceComponentType();
    }

    /** One-slot container holding the player's chosen fluid item. Always {@link FilterType#ALLOW_ALL}. */
    private SimpleItemContainer selectorContainer;

    public FluidSourceComponent() {
        this.selectorContainer = new SimpleItemContainer((short) 1);
    }

    public FluidSourceComponent(@Nonnull FluidSourceComponent other) {
        this.selectorContainer = new SimpleItemContainer((short) 1);
        ItemStack existingStack = other.selectorContainer.getItemStack((short) 0);
        if (existingStack != null) {
            this.selectorContainer.setItemStackForSlot((short) 0, existingStack, false);
        }
    }

    /**
     * Returns the fluid asset ID of the item currently in the selector slot, or
     * {@code null} if the slot is empty or holds an item not registered as a fluid.
     */
    @Nullable
    public String getSelectedFluidId() {
        ItemStack stack = selectorContainer.getItemStack((short) 0);
        if (stack == null) {
            return null;
        }
        return FluidItemRegistry.resolveFluidId(stack.getItemId());
    }

    public SimpleItemContainer getSelectorContainer() {
        return selectorContainer;
    }

    @Override
    public FluidSourceComponent clone() {
        return new FluidSourceComponent(this);
    }
}

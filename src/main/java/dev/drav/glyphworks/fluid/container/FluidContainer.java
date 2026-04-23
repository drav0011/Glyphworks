package dev.drav.glyphworks.fluid.container;

import javax.annotation.Nullable;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;

import dev.drav.glyphworks.fluid.FluidStack;

/**
 * A single-slot {@link SimpleItemContainer} that only accepts {@link FluidStack} items.
 *
 * <p>
 * The slot is locked with {@link FilterType#DENY_ALL} — players cannot interact with
 * it through the normal inventory UI. Internal operations (fill, drain, grid transfer)
 * pass {@code filter = false} to {@code setItemStackForSlot}.
 */
public class FluidContainer extends SimpleItemContainer {

    public static final BuilderCodec<FluidContainer> CODEC = BuilderCodec
            .builder(FluidContainer.class, FluidContainer::new)
            .append(
                    new KeyedCodec<>("Glyphworks_FluidItemContainer_Fluid", FluidStack.CODEC),
                    (c, stack) -> { if (stack != null) c.setItemStackForSlot((short) 0, stack, false); },
                    c -> c.getFluid())
            .add()
            .build();

    /** Constructor used by {@link #CODEC} and by {@link dev.drav.glyphworks.fluid.component.FluidContainerComponent}. */
    public FluidContainer() {
        super((short) 1);
        applyFilters();
    }

    /**
     * Returns the fluid stored in slot 0, or {@code null} if the slot is empty.
     */
    @Nullable
    public FluidStack getFluid() {
        ItemStack stack = getItemStack((short) 0);
        if (stack instanceof FluidStack) return (FluidStack) stack;
        return null;
    }

    private void applyFilters() {
        setGlobalFilter(FilterType.DENY_ALL);
        setSlotFilter(FilterActionType.ADD, (short) 0, FluidSlotFilter.INSTANCE);
    }
}

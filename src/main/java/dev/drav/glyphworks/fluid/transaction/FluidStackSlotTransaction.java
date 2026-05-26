package dev.drav.glyphworks.fluid.transaction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ActionType;

import dev.drav.glyphworks.fluid.FluidStack;

public class FluidStackSlotTransaction extends FluidSlotTransaction {
    private final boolean addToExistingSlot;

    @Nullable
    private final FluidStack query;

    @Nullable
    private final FluidStack remainder;

    public FluidStackSlotTransaction(
            boolean succeeded,
            @Nonnull ActionType action,
            short slot,
            @Nullable FluidStack slotBefore,
            @Nullable FluidStack slotAfter,
            @Nullable FluidStack output,
            boolean allOrNothing,
            boolean exactAmount,
            boolean filter,
            boolean addToExistingSlot,
            @Nullable FluidStack query,
            @Nullable FluidStack remainder) {
        super(succeeded, action, slot, slotBefore, slotAfter, output, allOrNothing, exactAmount, filter);
        this.addToExistingSlot = addToExistingSlot;
        this.query = query;
        this.remainder = remainder;
    }

    public boolean isAddToExistingSlot() {
        return addToExistingSlot;
    }

    @Nullable
    public FluidStack getQuery() {
        return query;
    }

    @Nullable
    public FluidStack getRemainder() {
        return remainder;
    }

    @Override
    @Nonnull
    public FluidStackSlotTransaction toParent(ItemContainer parent, short start, ItemContainer container) {
        short newSlot = (short) (start + getSlot());
        return new FluidStackSlotTransaction(succeeded(), getAction(), newSlot, getSlotBefore(), getSlotAfter(),
                getOutput(), isAllOrNothing(), isExactAmount(), isFilter(), addToExistingSlot, query, remainder);
    }

    @Override
    @Nullable
    public FluidStackSlotTransaction fromParent(ItemContainer parent, short start, @Nonnull ItemContainer container) {
        short newSlot = (short) (getSlot() - start);
        if (newSlot < 0 || newSlot >= container.getCapacity()) {
            return null;
        }
        return new FluidStackSlotTransaction(succeeded(), getAction(), newSlot, getSlotBefore(), getSlotAfter(),
                getOutput(), isAllOrNothing(), isExactAmount(), isFilter(), addToExistingSlot, query, remainder);
    }

    @Override
    @Nonnull
    public String toString() {
        return "FluidStackSlotTransaction{addToExistingSlot=" + addToExistingSlot
                + ", query=" + query + ", remainder=" + remainder + "} " + super.toString();
    }
}

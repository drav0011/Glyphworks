package dev.drav.glyphworks.fluid.transaction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.ResourceQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ActionType;

import dev.drav.glyphworks.fluid.FluidStack;

public class FluidResourceSlotTransaction extends FluidSlotTransaction {

    @Nonnull
    private final ResourceQuantity query;

    private final int remainder;
    private final int consumed;

    public FluidResourceSlotTransaction(
            boolean succeeded,
            @Nonnull ActionType action,
            short slot,
            @Nullable FluidStack slotBefore,
            @Nullable FluidStack slotAfter,
            @Nullable FluidStack output,
            boolean allOrNothing,
            boolean exactAmount,
            boolean filter,
            @Nonnull ResourceQuantity query,
            int remainder,
            int consumed) {
        super(succeeded, action, slot, slotBefore, slotAfter, output, allOrNothing, exactAmount, filter);
        this.query = query;
        this.remainder = remainder;
        this.consumed = consumed;
    }

    @Nonnull
    public ResourceQuantity getQuery() {
        return query;
    }

    public int getRemainder() {
        return remainder;
    }

    public int getConsumed() {
        return consumed;
    }

    @Override
    @Nonnull
    public FluidResourceSlotTransaction toParent(ItemContainer parent, short start, ItemContainer container) {
        short newSlot = (short) (start + getSlot());
        return new FluidResourceSlotTransaction(succeeded(), getAction(), newSlot, getSlotBefore(), getSlotAfter(),
                getOutput(), isAllOrNothing(), isExactAmount(), isFilter(), query, remainder, consumed);
    }

    @Override
    @Nullable
    public FluidResourceSlotTransaction fromParent(ItemContainer parent, short start, @Nonnull ItemContainer container) {
        short newSlot = (short) (getSlot() - start);
        if (newSlot < 0 || newSlot >= container.getCapacity()) {
            return null;
        }
        return new FluidResourceSlotTransaction(succeeded(), getAction(), newSlot, getSlotBefore(), getSlotAfter(),
                getOutput(), isAllOrNothing(), isExactAmount(), isFilter(), query, remainder, consumed);
    }

    @Override
    @Nonnull
    public String toString() {
        return "FluidResourceSlotTransaction{query=" + query + ", remainder=" + remainder
                + ", consumed=" + consumed + "} " + super.toString();
    }
}

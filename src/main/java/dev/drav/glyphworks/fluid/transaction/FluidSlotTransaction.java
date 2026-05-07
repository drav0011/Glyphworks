package dev.drav.glyphworks.fluid.transaction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ActionType;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;

import dev.drav.glyphworks.fluid.FluidStack;

public class FluidSlotTransaction implements Transaction {

    public static final FluidSlotTransaction FAILED_ADD = new FluidSlotTransaction(
            false, ActionType.ADD, (short) -1, null, null, null, false, false, false);

    private final boolean succeeded;

    @Nonnull
    private final ActionType action;

    private final short slot;

    @Nullable
    private final FluidStack slotBefore;

    @Nullable
    private final FluidStack slotAfter;

    @Nullable
    private final FluidStack output;

    private final boolean allOrNothing;
    private final boolean exactAmount;
    private final boolean filter;

    public FluidSlotTransaction(
            boolean succeeded,
            @Nonnull ActionType action,
            short slot,
            @Nullable FluidStack slotBefore,
            @Nullable FluidStack slotAfter,
            @Nullable FluidStack output,
            boolean allOrNothing,
            boolean exactAmount,
            boolean filter) {
        this.succeeded = succeeded;
        this.action = action;
        this.slot = slot;
        this.slotBefore = slotBefore;
        this.slotAfter = slotAfter;
        this.output = output;
        this.allOrNothing = allOrNothing;
        this.exactAmount = exactAmount;
        this.filter = filter;
    }

    @Override
    public boolean succeeded() {
        return succeeded;
    }

    @Override
    public boolean wasSlotModified(short slot) {
        return succeeded && this.slot == slot;
    }

    @Nonnull
    public ActionType getAction() {
        return action;
    }

    public short getSlot() {
        return slot;
    }

    @Nullable
    public FluidStack getSlotBefore() {
        return slotBefore;
    }

    @Nullable
    public FluidStack getSlotAfter() {
        return slotAfter;
    }

    @Nullable
    public FluidStack getOutput() {
        return output;
    }

    public boolean isAllOrNothing() {
        return allOrNothing;
    }

    public boolean isExactAmount() {
        return exactAmount;
    }

    public boolean isFilter() {
        return filter;
    }

    @Override
    @Nonnull
    public FluidSlotTransaction toParent(ItemContainer parent, short start, ItemContainer container) {
        short newSlot = (short) (start + slot);
        return new FluidSlotTransaction(succeeded, action, newSlot, slotBefore, slotAfter, output,
                allOrNothing, exactAmount, filter);
    }

    @Override
    @Nullable
    public FluidSlotTransaction fromParent(ItemContainer parent, short start, @Nonnull ItemContainer container) {
        short newSlot = (short) (slot - start);
        if (newSlot < 0 || newSlot >= container.getCapacity()) {
            return null;
        }
        return new FluidSlotTransaction(succeeded, action, newSlot, slotBefore, slotAfter, output,
                allOrNothing, exactAmount, filter);
    }

    @Override
    @Nonnull
    public String toString() {
        return "FluidSlotTransaction{succeeded=" + succeeded + ", action=" + action + ", slot=" + slot
                + ", slotBefore=" + slotBefore + ", slotAfter=" + slotAfter + ", allOrNothing=" + allOrNothing
                + ", exactAmount=" + exactAmount + ", filter=" + filter + "}";
    }
}

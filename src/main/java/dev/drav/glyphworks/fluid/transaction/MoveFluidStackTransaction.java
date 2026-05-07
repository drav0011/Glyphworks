package dev.drav.glyphworks.fluid.transaction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;

import dev.drav.glyphworks.fluid.container.FluidContainer;

public class MoveFluidStackTransaction implements Transaction {

    private final boolean succeeded;

    @Nonnull
    private final FluidStackSlotTransaction removeTransaction;

    @Nonnull
    private final FluidContainer source;

    @Nonnull
    private final FluidStackTransaction addTransaction;

    public MoveFluidStackTransaction(
            boolean succeeded,
            @Nonnull FluidStackSlotTransaction removeTransaction,
            @Nonnull FluidContainer source,
            @Nonnull FluidStackTransaction addTransaction) {
        this.succeeded = succeeded;
        this.removeTransaction = removeTransaction;
        this.source = source;
        this.addTransaction = addTransaction;
    }

    @Override
    public boolean succeeded() {
        return succeeded;
    }

    @Override
    public boolean wasSlotModified(short slot) {
        return succeeded && removeTransaction.wasSlotModified(slot);
    }

    @Nonnull
    public FluidStackSlotTransaction getRemoveTransaction() {
        return removeTransaction;
    }

    @Nonnull
    public FluidContainer getSource() {
        return source;
    }

    @Nonnull
    public FluidStackTransaction getAddTransaction() {
        return addTransaction;
    }

    @Nonnull
    public MoveFluidStackTransaction toInverted(@Nonnull FluidContainer newSource) {
        return new MoveFluidStackTransaction(succeeded, removeTransaction, newSource, addTransaction);
    }

    @Override
    @Nonnull
    public MoveFluidStackTransaction toParent(ItemContainer parent, short start, @Nonnull ItemContainer container) {
        FluidStackSlotTransaction newRemove = removeTransaction.toParent(parent, start, container);
        FluidStackTransaction newAdd = addTransaction.toParent(parent, start, container);
        return new MoveFluidStackTransaction(succeeded, newRemove, source, newAdd);
    }

    @Override
    @Nullable
    public MoveFluidStackTransaction fromParent(ItemContainer parent, short start, @Nonnull ItemContainer container) {
        FluidStackSlotTransaction newRemove = removeTransaction.fromParent(parent, start, container);
        FluidStackTransaction newAdd = addTransaction.fromParent(parent, start, container);
        if (newRemove == null || newAdd == null) {
            return null;
        }
        return new MoveFluidStackTransaction(succeeded, newRemove, source, newAdd);
    }
}

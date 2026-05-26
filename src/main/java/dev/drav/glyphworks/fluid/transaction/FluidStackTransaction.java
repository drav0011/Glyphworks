package dev.drav.glyphworks.fluid.transaction;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ActionType;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;

import dev.drav.glyphworks.fluid.FluidStack;

public class FluidStackTransaction implements Transaction {

    public static final FluidStackTransaction FAILED_ADD = new FluidStackTransaction(
            false, ActionType.ADD, null, null, false, false, Collections.emptyList());

    private final boolean succeeded;

    @Nullable
    private final ActionType action;

    @Nullable
    private final FluidStack query;

    @Nullable
    private final FluidStack remainder;

    private final boolean allOrNothing;
    private final boolean filter;

    @Nonnull
    private final List<FluidStackSlotTransaction> slotTransactions;

    public FluidStackTransaction(
            boolean succeeded,
            @Nullable ActionType action,
            @Nullable FluidStack query,
            @Nullable FluidStack remainder,
            boolean allOrNothing,
            boolean filter,
            @Nonnull List<FluidStackSlotTransaction> slotTransactions) {
        this.succeeded = succeeded;
        this.action = action;
        this.query = query;
        this.remainder = remainder;
        this.allOrNothing = allOrNothing;
        this.filter = filter;
        this.slotTransactions = slotTransactions;
    }

    @Override
    public boolean succeeded() {
        return succeeded;
    }

    @Override
    public boolean wasSlotModified(short slot) {
        if (!succeeded)
            return false;
        for (FluidStackSlotTransaction t : slotTransactions) {
            if (t.succeeded() && t.wasSlotModified(slot))
                return true;
        }
        return false;
    }

    @Nullable
    public ActionType getAction() {
        return action;
    }

    @Nullable
    public FluidStack getQuery() {
        return query;
    }

    @Nullable
    public FluidStack getRemainder() {
        return remainder;
    }

    public boolean isAllOrNothing() {
        return allOrNothing;
    }

    public boolean isFilter() {
        return filter;
    }

    @Nonnull
    public List<FluidStackSlotTransaction> getSlotTransactions() {
        return slotTransactions;
    }

    @Override
    @Nonnull
    public FluidStackTransaction toParent(ItemContainer parent, short start, @Nonnull ItemContainer container) {
        List<FluidStackSlotTransaction> remapped = slotTransactions.stream()
                .map(t -> t.toParent(parent, start, container))
                .collect(Collectors.toList());
        return new FluidStackTransaction(succeeded, action, query, remainder, allOrNothing, filter, remapped);
    }

    @Override
    @Nullable
    public FluidStackTransaction fromParent(ItemContainer parent, short start, @Nonnull ItemContainer container) {
        List<FluidStackSlotTransaction> remapped = slotTransactions.stream()
                .map(t -> t.fromParent(parent, start, container))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (remapped.isEmpty()) {
            return null;
        }
        boolean anySucceeded = remapped.stream().anyMatch(FluidStackSlotTransaction::succeeded);
        return new FluidStackTransaction(anySucceeded, action, query, remainder, allOrNothing, filter, remapped);
    }
}

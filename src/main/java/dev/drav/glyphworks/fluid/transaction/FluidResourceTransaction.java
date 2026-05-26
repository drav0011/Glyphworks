package dev.drav.glyphworks.fluid.transaction;

import com.hypixel.hytale.server.core.inventory.ResourceQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ActionType;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FluidResourceTransaction extends ListTransaction<FluidResourceSlotTransaction> {

    @Nonnull
    private final ActionType action;

    @Nonnull
    private final ResourceQuantity resource;

    private final int remainder;
    private final int consumed;
    private final boolean allOrNothing;
    private final boolean exactAmount;
    private final boolean filter;

    public FluidResourceTransaction(
            boolean succeeded,
            @Nonnull ActionType action,
            @Nonnull ResourceQuantity resource,
            int remainder,
            int consumed,
            boolean allOrNothing,
            boolean exactAmount,
            boolean filter,
            @Nonnull List<FluidResourceSlotTransaction> slotTransactions) {
        super(succeeded, slotTransactions);
        this.action = action;
        this.resource = resource;
        this.remainder = remainder;
        this.consumed = consumed;
        this.allOrNothing = allOrNothing;
        this.exactAmount = exactAmount;
        this.filter = filter;
    }

    @Nonnull
    public ActionType getAction() {
        return action;
    }

    @Nonnull
    public ResourceQuantity getResource() {
        return resource;
    }

    public int getRemainder() {
        return remainder;
    }

    public int getConsumed() {
        return consumed;
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
    public FluidResourceTransaction toParent(ItemContainer parent, short start, @Nonnull ItemContainer container) {
        List<FluidResourceSlotTransaction> remapped = getList().stream()
                .map(t -> t.toParent(parent, start, container))
                .collect(Collectors.toList());
        return new FluidResourceTransaction(succeeded(), action, resource, remainder, consumed,
                allOrNothing, exactAmount, filter, remapped);
    }

    @Override
    @Nullable
    public FluidResourceTransaction fromParent(ItemContainer parent, short start, @Nonnull ItemContainer container) {
        List<FluidResourceSlotTransaction> remapped = getList().stream()
                .map(t -> t.fromParent(parent, start, container))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (remapped.isEmpty()) {
            return null;
        }
        boolean anySucceeded = remapped.stream().anyMatch(FluidResourceSlotTransaction::succeeded);
        return new FluidResourceTransaction(anySucceeded, action, resource, remainder, consumed,
                allOrNothing, exactAmount, filter, remapped);
    }

    @Override
    @Nonnull
    public String toString() {
        return "FluidResourceTransaction{action=" + action + ", resource=" + resource
                + ", remainder=" + remainder + ", consumed=" + consumed
                + ", allOrNothing=" + allOrNothing + ", exactAmount=" + exactAmount
                + ", filter=" + filter + "} " + super.toString();
    }
}

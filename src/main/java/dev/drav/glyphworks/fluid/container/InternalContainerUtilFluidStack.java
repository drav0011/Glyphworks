package dev.drav.glyphworks.fluid.container;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ActionType;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveType;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;

import dev.drav.glyphworks.fluid.FluidStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Collections;

class InternalContainerUtilFluidStack {

    @Nonnull
    protected static ItemStackSlotTransaction internal_addFluidStackToSlot(
            @Nonnull FluidContainer container, short slot, @Nonnull FluidStack fluidStack,
            boolean allOrNothing, boolean filter) {
        ItemContainer.validateSlotIndex(slot, container.getCapacity());
        return (ItemStackSlotTransaction) container.internalWriteAction(() -> {
            int capacityMb = container.getCapacityMbPerSlot();
            ItemStack existing = container.internalGetSlot(slot);

            if (filter && container.internalCantAddToSlot(slot, fluidStack, existing)) {
                return failedAdd(slot, existing, fluidStack, allOrNothing, filter);
            }

            if (ItemStack.isEmpty(existing)) {
                int toAdd = Math.min(capacityMb, fluidStack.getQuantity());
                if (allOrNothing && toAdd < fluidStack.getQuantity()) {
                    return failedAdd(slot, existing, fluidStack, allOrNothing, filter);
                }
                FluidStack placed = fluidStack.withQuantity(toAdd);
                container.internalSetSlot(slot, placed);
                FluidStack remainder = fluidStack.withQuantity(fluidStack.getQuantity() - toAdd);
                return new ItemStackSlotTransaction(true, ActionType.ADD, slot, null, placed, null,
                        allOrNothing, false, filter, false, fluidStack, remainder);
            }

            if (!existing.isStackableWith(fluidStack)) {
                return failedAdd(slot, existing, fluidStack, allOrNothing, filter);
            }

            int current = existing.getQuantity();
            int space = capacityMb - current;
            int toAdd = Math.min(space, fluidStack.getQuantity());
            if (toAdd <= 0 || (allOrNothing && toAdd < fluidStack.getQuantity())) {
                return failedAdd(slot, existing, fluidStack, allOrNothing, filter);
            }

            FluidStack slotNew = ((FluidStack) existing).withQuantity(current + toAdd);
                container.internalSetSlot(slot, slotNew);
            FluidStack remainder = fluidStack.withQuantity(fluidStack.getQuantity() - toAdd);
            return new ItemStackSlotTransaction(true, ActionType.ADD, slot, existing, slotNew, null,
                    allOrNothing, false, filter, true, fluidStack, remainder);
        });
    }

    @Nonnull
    protected static ItemStackTransaction internal_addFluidStack(
            @Nonnull FluidContainer container, @Nonnull FluidStack fluidStack,
            boolean allOrNothing, boolean filter) {
        return (ItemStackTransaction) container.internalWriteAction(() -> {
            int capacityMb = container.getCapacityMbPerSlot();

            if (allOrNothing) {
                int testRemaining = fluidStack.getQuantity();
                testRemaining = testAddToExistingFluidSlots(container, fluidStack, capacityMb, testRemaining, filter);
                testRemaining = testAddToEmptyFluidSlots(container, fluidStack, capacityMb, testRemaining, filter);
                if (testRemaining > 0) {
                    return new ItemStackTransaction(false, ActionType.ADD, fluidStack, fluidStack,
                            allOrNothing, filter, Collections.emptyList());
                }
            }

            ObjectArrayList<ItemStackSlotTransaction> slotTransactions = new ObjectArrayList<>();
            FluidStack remaining = fluidStack;

            for (short i = 0; i < container.getCapacity() && !ItemStack.isEmpty(remaining); i++) {
                ItemStack existing = container.internalGetSlot(i);
                if (existing != null && existing.isStackableWith(remaining)) {
                    ItemStackSlotTransaction t = internal_addToExistingFluidSlot(container, i, remaining, capacityMb, filter);
                    slotTransactions.add(t);
                    remaining = t.getRemainder() instanceof FluidStack fs ? fs : null;
                }
            }

            for (short i = 0; i < container.getCapacity() && !ItemStack.isEmpty(remaining); i++) {
                if (ItemStack.isEmpty(container.internalGetSlot(i))) {
                    ItemStackSlotTransaction t = internal_addToEmptyFluidSlot(container, i, remaining, capacityMb, filter);
                    slotTransactions.add(t);
                    remaining = t.getRemainder() instanceof FluidStack fs ? fs : null;
                }
            }

            return new ItemStackTransaction(true, ActionType.ADD, fluidStack, remaining,
                    allOrNothing, filter, slotTransactions);
        });
    }

    @Nonnull
    protected static ItemStackSlotTransaction internal_removeFluidStackFromSlot(
            @Nonnull FluidContainer container, short slot, int amountMb,
            boolean allOrNothing, boolean filter) {
        ItemContainer.validateSlotIndex(slot, container.getCapacity());
        ItemContainer.validateQuantity(amountMb);
        return (ItemStackSlotTransaction) container.internalWriteAction(() -> {
            if (filter && container.internalCantRemoveFromSlot(slot)) {
                ItemStack existing = container.internalGetSlot(slot);
                return new ItemStackSlotTransaction(false, ActionType.REMOVE, slot, existing, existing, null,
                        allOrNothing, false, filter, false, null,
                        existing != null ? existing.withQuantity(amountMb) : null);
            }

            ItemStack existing = container.internalGetSlot(slot);
            if (!(existing instanceof FluidStack existingFluid)) {
                return new ItemStackSlotTransaction(false, ActionType.REMOVE, slot, existing, existing, null,
                        allOrNothing, false, filter, false, null, null);
            }

            int current = existingFluid.getQuantity();
            int toRemove = Math.min(current, amountMb);
            int quantityRemaining = amountMb - toRemove;

            if (allOrNothing && quantityRemaining > 0) {
                return new ItemStackSlotTransaction(false, ActionType.REMOVE, slot, existing, existing, null,
                        allOrNothing, false, filter, false, null, existingFluid.withQuantity(quantityRemaining));
            }

            FluidStack slotNew = existingFluid.withQuantity(current - toRemove);
                container.internalSetSlot(slot, slotNew);
            FluidStack removed = existingFluid.withQuantity(toRemove);
            FluidStack remainder = existingFluid.withQuantity(quantityRemaining);
            return new ItemStackSlotTransaction(true, ActionType.REMOVE, slot, existing, slotNew, removed,
                    allOrNothing, false, filter, false, null, remainder);
        });
    }

    @Nonnull
    protected static MoveTransaction<ItemStackTransaction> internal_moveFluidStackFromSlot(
            @Nonnull FluidContainer source, short slot, @Nonnull FluidContainer dest,
            boolean allOrNothing, boolean filter) {
        ItemContainer.validateSlotIndex(slot, source.getCapacity());
        return (MoveTransaction<ItemStackTransaction>) source.internalWriteAction(() ->
                dest.internalWriteAction(() -> {
                    if (filter && source.internalCantRemoveFromSlot(slot)) {
                        return null;
                    }

                    ItemStack existing = source.internalRemoveSlot(slot);
                    if (!(existing instanceof FluidStack fluidFrom)) {
                        SlotTransaction removeTransaction = new SlotTransaction(false, ActionType.REMOVE,
                                slot, null, null, null, false, false, filter);
                        return new MoveTransaction<>(false, removeTransaction,
                                MoveType.MOVE_FROM_SELF, dest, ItemStackTransaction.FAILED_ADD);
                    }

                    SlotTransaction removeTransaction = new SlotTransaction(true, ActionType.REMOVE,
                            slot, fluidFrom, null, null, false, false, filter);
                    ItemStackTransaction addTransaction = internal_addFluidStack(dest, fluidFrom, allOrNothing, filter);
                    FluidStack remainder = addTransaction.getRemainder() instanceof FluidStack fs ? fs : null;
                    if (!ItemStack.isEmpty(remainder)) {
                        internal_addFluidStackToSlot(source, slot, remainder, allOrNothing, false);
                    }
                    return new MoveTransaction<>(addTransaction.succeeded(), removeTransaction,
                            MoveType.MOVE_FROM_SELF, dest, addTransaction);
                })
        );
    }

    private static ItemStackSlotTransaction internal_addToExistingFluidSlot(
            FluidContainer container, short slot, FluidStack fluidStack, int capacityMb, boolean filter) {
        ItemStack existing = container.internalGetSlot(slot);
        if (!existing.isStackableWith(fluidStack)) {
            return failedAdd(slot, existing, fluidStack, false, filter);
        }
        if (filter && container.internalCantAddToSlot(slot, fluidStack, existing)) {
            return failedAdd(slot, existing, fluidStack, false, filter);
        }
        int current = existing.getQuantity();
        int space = capacityMb - current;
        int toAdd = Math.min(space, fluidStack.getQuantity());
        if (toAdd <= 0) {
            return failedAdd(slot, existing, fluidStack, false, filter);
        }
        FluidStack slotNew = ((FluidStack) existing).withQuantity(current + toAdd);
        container.internalSetSlot(slot, slotNew);
        FluidStack remainder = fluidStack.withQuantity(fluidStack.getQuantity() - toAdd);
        return new ItemStackSlotTransaction(true, ActionType.ADD, slot, existing, slotNew, null,
                false, false, filter, true, fluidStack, remainder);
    }

    private static ItemStackSlotTransaction internal_addToEmptyFluidSlot(
            FluidContainer container, short slot, FluidStack fluidStack, int capacityMb, boolean filter) {
        ItemStack existing = container.internalGetSlot(slot);
        if (filter && container.internalCantAddToSlot(slot, fluidStack, existing)) {
            return failedAdd(slot, existing, fluidStack, false, filter);
        }
        int toAdd = Math.min(capacityMb, fluidStack.getQuantity());
        FluidStack placed = fluidStack.withQuantity(toAdd);
        container.internalSetSlot(slot, placed);
        FluidStack remainder = fluidStack.withQuantity(fluidStack.getQuantity() - toAdd);
        return new ItemStackSlotTransaction(true, ActionType.ADD, slot, existing, placed, null,
                false, false, filter, false, fluidStack, remainder);
    }

    private static int testAddToExistingFluidSlots(
            FluidContainer container, FluidStack fluidStack, int capacityMb, int remaining, boolean filter) {
        for (short i = 0; i < container.getCapacity() && remaining > 0; i++) {
            ItemStack existing = container.internalGetSlot(i);
            if (existing != null && existing.isStackableWith(fluidStack)) {
                if (!filter || !container.internalCantAddToSlot(i, fluidStack, existing)) {
                    remaining -= Math.min(capacityMb - existing.getQuantity(), remaining);
                }
            }
        }
        return remaining;
    }

    private static int testAddToEmptyFluidSlots(
            FluidContainer container, FluidStack fluidStack, int capacityMb, int remaining, boolean filter) {
        for (short i = 0; i < container.getCapacity() && remaining > 0; i++) {
            ItemStack existing = container.internalGetSlot(i);
            if (ItemStack.isEmpty(existing)) {
                if (!filter || !container.internalCantAddToSlot(i, fluidStack, existing)) {
                    remaining -= Math.min(capacityMb, remaining);
                }
            }
        }
        return remaining;
    }

    private static ItemStackSlotTransaction failedAdd(
            short slot, @Nullable ItemStack slotBefore, FluidStack query, boolean allOrNothing, boolean filter) {
        return new ItemStackSlotTransaction(false, ActionType.ADD, slot, slotBefore, slotBefore, null,
                allOrNothing, false, filter, false, query, query);
    }
}

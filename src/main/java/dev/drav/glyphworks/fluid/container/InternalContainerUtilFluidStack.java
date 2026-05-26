package dev.drav.glyphworks.fluid.container;

import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ActionType;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;

import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.transaction.FluidStackSlotTransaction;
import dev.drav.glyphworks.fluid.transaction.FluidStackTransaction;
import dev.drav.glyphworks.fluid.transaction.MoveFluidStackTransaction;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

class InternalContainerUtilFluidStack {

    protected static int testAddToExistingSlot(
            @Nonnull FluidContainer container, short slot, FluidStack fluidStack, int capacityMb,
            int testAmountRemaining, boolean filter) {
        FluidStack existing = container.internalGetSlot(slot);
        if (!FluidStack.isEmpty(existing) && existing.isStackableWith(fluidStack)) {
            if (!filter || !container.internalCantAddToSlot(slot, fluidStack, existing)) {
                int space = capacityMb - existing.getAmount();
                testAmountRemaining -= Math.min(space, testAmountRemaining);
            }
        }
        return testAmountRemaining;
    }

    private static FluidStackSlotTransaction internal_addToExistingSlot(
            FluidContainer container, short slot, FluidStack fluidStack, int capacityMb, boolean filter) {
        FluidStack existing = container.internalGetSlot(slot);
        if (!existing.isStackableWith(fluidStack)) {
            return failedAdd(slot, existing, fluidStack, false, filter);
        }
        if (filter && container.internalCantAddToSlot(slot, fluidStack, existing)) {
            return failedAdd(slot, existing, fluidStack, false, filter);
        }
        int current = existing.getAmount();
        int space = capacityMb - current;
        int toAdd = Math.min(space, fluidStack.getAmount());
        if (toAdd <= 0) {
            return failedAdd(slot, existing, fluidStack, false, filter);
        }
        FluidStack slotNew = existing.withAmount(current + toAdd);
        container.internalSetSlot(slot, slotNew);
        FluidStack remainder = fluidStack.withAmount(fluidStack.getAmount() - toAdd);
        return new FluidStackSlotTransaction(true, ActionType.ADD, slot, existing, slotNew, null,
                false, false, filter, true, fluidStack, remainder);
    }

    private static FluidStackSlotTransaction internal_addToEmptySlot(
            FluidContainer container, short slot, FluidStack fluidStack, int capacityMb, boolean filter) {
        FluidStack existing = container.internalGetSlot(slot);
        if (filter && container.internalCantAddToSlot(slot, fluidStack, existing)) {
            return failedAdd(slot, existing, fluidStack, false, filter);
        }
        int toAdd = Math.min(capacityMb, fluidStack.getAmount());
        FluidStack placed = fluidStack.withAmount(toAdd);
        container.internalSetSlot(slot, placed);
        FluidStack remainder = fluidStack.withAmount(fluidStack.getAmount() - toAdd);
        return new FluidStackSlotTransaction(true, ActionType.ADD, slot, existing, placed, null,
                false, false, filter, false, fluidStack, remainder);
    }

    protected static int testAddToEmptySlots(
            FluidContainer container, FluidStack fluidStack, int capacityMb, int remaining, boolean filter) {
        for (short i = 0; i < container.getCapacity() && remaining > 0; i++) {
            FluidStack existing = container.internalGetSlot(i);
            if (FluidStack.isEmpty(existing)) {
                if (!filter || !container.internalCantAddToSlot(i, fluidStack, existing)) {
                    remaining -= Math.min(capacityMb, remaining);
                }
            }
        }
        return remaining;
    }

    @Nonnull
    protected static FluidStackSlotTransaction internal_addFluidStackToSlot(
            @Nonnull FluidContainer container, short slot, @Nonnull FluidStack fluidStack,
            boolean allOrNothing, boolean filter) {
        ItemContainer.validateSlotIndex(slot, container.getCapacity());
        return (FluidStackSlotTransaction) container.internalWriteAction(() -> {
            int capacityMb = container.getCapacityMbPerSlot();
            FluidStack existing = container.internalGetSlot(slot);

            if (filter && container.internalCantAddToSlot(slot, fluidStack, existing)) {
                return failedAdd(slot, existing, fluidStack, allOrNothing, filter);
            }

            if (FluidStack.isEmpty(existing)) {
                int toAdd = Math.min(capacityMb, fluidStack.getAmount());
                if (allOrNothing && toAdd < fluidStack.getAmount()) {
                    return failedAdd(slot, existing, fluidStack, allOrNothing, filter);
                }
                FluidStack placed = fluidStack.withAmount(toAdd);
                container.internalSetSlot(slot, placed);
                FluidStack remainder = fluidStack.withAmount(fluidStack.getAmount() - toAdd);
                return new FluidStackSlotTransaction(true, ActionType.ADD, slot, null, placed, null,
                        allOrNothing, false, filter, false, fluidStack, remainder);
            }

            if (!existing.isStackableWith(fluidStack)) {
                return failedAdd(slot, existing, fluidStack, allOrNothing, filter);
            }

            int current = existing.getAmount();
            int space = capacityMb - current;
            int toAdd = Math.min(space, fluidStack.getAmount());
            if (toAdd <= 0 || (allOrNothing && toAdd < fluidStack.getAmount())) {
                return failedAdd(slot, existing, fluidStack, allOrNothing, filter);
            }

            FluidStack slotNew = existing.withAmount(current + toAdd);
            container.internalSetSlot(slot, slotNew);
            FluidStack remainder = fluidStack.withAmount(fluidStack.getAmount() - toAdd);
            return new FluidStackSlotTransaction(true, ActionType.ADD, slot, existing, slotNew, null,
                    allOrNothing, false, filter, true, fluidStack, remainder);
        });
    }

    @Nonnull
    protected static FluidStackSlotTransaction internal_setFluidStackForSlot(
            @Nonnull FluidContainer container, short slot, @Nullable FluidStack fluidStack, boolean filter) {
        ItemContainer.validateSlotIndex(slot, container.getCapacity());
        return (FluidStackSlotTransaction) container.internalWriteAction(() -> {
            FluidStack existing = container.internalGetSlot(slot);
            if (filter && container.internalCantAddToSlot(slot, fluidStack, existing)) {
                return new FluidStackSlotTransaction(false, ActionType.SET, slot, existing, existing, null,
                        false, false, filter, false, fluidStack, fluidStack);
            }
            FluidStack oldFluidStack = container.internalSetSlot(slot, fluidStack);
            return new FluidStackSlotTransaction(true, ActionType.SET, slot, oldFluidStack, fluidStack, null,
                    false, false, filter, false, fluidStack, null);
        });
    }

    @Nonnull
    protected static FluidStackSlotTransaction internal_removeFluidStackFromSlot(
            @Nonnull FluidContainer container, short slot, boolean filter) {
        ItemContainer.validateSlotIndex(slot, container.getCapacity());
        return (FluidStackSlotTransaction) container.internalWriteAction(() -> {
            if (filter && container.internalCantRemoveFromSlot(slot)) {
                FluidStack fluidStack = container.internalGetSlot(slot);
                return new FluidStackSlotTransaction(false, ActionType.REMOVE, slot, fluidStack, fluidStack, null,
                        false, false, filter, false, null, fluidStack);
            }
            FluidStack oldFluidStack = container.internalRemoveSlot(slot);
            return new FluidStackSlotTransaction(true, ActionType.REMOVE, slot, oldFluidStack, null, null,
                    false, false, filter, false, null, null);
        });
    }

    @Nonnull
    protected static FluidStackSlotTransaction internal_removeFluidStackFromSlot(
            @Nonnull FluidContainer container, short slot, int amountMb,
            boolean allOrNothing, boolean filter) {
        ItemContainer.validateSlotIndex(slot, container.getCapacity());
        ItemContainer.validateQuantity(amountMb);
        return (FluidStackSlotTransaction) container.internalWriteAction(() -> {
            if (filter && container.internalCantRemoveFromSlot(slot)) {
                FluidStack existing = container.internalGetSlot(slot);
                return new FluidStackSlotTransaction(false, ActionType.REMOVE, slot, existing, existing, null,
                        allOrNothing, false, filter, false, null,
                        existing != null ? existing.withAmount(amountMb) : null);
            }

            FluidStack existing = container.internalGetSlot(slot);
            if (FluidStack.isEmpty(existing)) {
                return new FluidStackSlotTransaction(false, ActionType.REMOVE, slot, existing, existing, null,
                        allOrNothing, false, filter, false, null, null);
            }

            int current = existing.getAmount();
            int toRemove = Math.min(current, amountMb);
            int quantityRemaining = amountMb - toRemove;

            if (allOrNothing && quantityRemaining > 0) {
                return new FluidStackSlotTransaction(false, ActionType.REMOVE, slot, existing, existing, null,
                        allOrNothing, false, filter, false, null, existing.withAmount(quantityRemaining));
            }

            FluidStack slotNew = existing.withAmount(current - toRemove);
            container.internalSetSlot(slot, slotNew);
            FluidStack removed = existing.withAmount(toRemove);
            FluidStack remainder = existing.withAmount(quantityRemaining);
            return new FluidStackSlotTransaction(true, ActionType.REMOVE, slot, existing, slotNew, removed,
                    allOrNothing, false, filter, false, null, remainder);
        });
    }

    @Nonnull
    protected static FluidStackSlotTransaction internal_removeFluidStackFromSlot(
            @Nonnull FluidContainer container, short slot, @Nullable FluidStack fluidToRemove, int amountMb,
            boolean allOrNothing, boolean filter) {
        return internal_removeFluidStackFromSlot(container, slot, fluidToRemove, amountMb, allOrNothing, filter,
                (a, b) -> a.isStackableWith(b));
    }

    @Nonnull
    protected static FluidStackSlotTransaction internal_removeFluidStackFromSlot(
            @Nonnull FluidContainer container, short slot, @Nullable FluidStack fluidToRemove, int amountMb,
            boolean allOrNothing, boolean filter, BiPredicate<FluidStack, FluidStack> predicate) {
        ItemContainer.validateSlotIndex(slot, container.getCapacity());
        ItemContainer.validateQuantity(amountMb);
        return (FluidStackSlotTransaction) container.internalWriteAction(() -> {
            if (filter && container.internalCantRemoveFromSlot(slot)) {
                FluidStack existing = container.internalGetSlot(slot);
                return new FluidStackSlotTransaction(false, ActionType.REMOVE, slot, existing, existing, null,
                        allOrNothing, false, filter, false, fluidToRemove, fluidToRemove);
            }

            FluidStack existing = container.internalGetSlot(slot);
            if ((FluidStack.isEmpty(existing) && fluidToRemove != null)
                    || ((existing != null && FluidStack.isEmpty(existing))
                            || (existing != null && !predicate.test(existing, fluidToRemove)))) {
                return new FluidStackSlotTransaction(false, ActionType.REMOVE, slot, existing, existing, null,
                        allOrNothing, false, filter, false, fluidToRemove, fluidToRemove);
            }

            int current = existing.getAmount();
            int toRemove = Math.min(current, amountMb);
            int quantityRemaining = amountMb - toRemove;

            if (allOrNothing && quantityRemaining > 0) {
                return new FluidStackSlotTransaction(false, ActionType.REMOVE, slot, existing, existing, null,
                        allOrNothing, false, filter, false, fluidToRemove, existing.withAmount(quantityRemaining));
            }

            FluidStack slotNew = existing.withAmount(current - toRemove);
            container.internalSetSlot(slot, slotNew);
            FluidStack removed = existing.withAmount(toRemove);
            FluidStack remainder = existing.withAmount(quantityRemaining);
            return new FluidStackSlotTransaction(true, ActionType.REMOVE, slot, existing, slotNew, removed,
                    allOrNothing, false, filter, false, fluidToRemove, remainder);
        });
    }

    protected static int testRemoveFluidStackFromSlot(
            @Nonnull FluidContainer container, short slot, FluidStack fluidStack,
            int testAmountRemaining, boolean filter) {
        if (filter && container.internalCantRemoveFromSlot(slot)) {
            return testAmountRemaining;
        }
        FluidStack slotFluidStack = container.internalGetSlot(slot);
        if (!FluidStack.isEmpty(slotFluidStack) && slotFluidStack.isStackableWith(fluidStack)) {
            int amount = slotFluidStack.getAmount();
            testAmountRemaining -= Math.min(amount, testAmountRemaining);
        }
        return testAmountRemaining;
    }

    @Nonnull
    protected static FluidStackTransaction internal_addFluidStack(
            @Nonnull FluidContainer container, @Nonnull FluidStack fluidStack,
            boolean allOrNothing, boolean filter) {
        return (FluidStackTransaction) container.internalWriteAction(() -> {
            int capacityMb = container.getCapacityMbPerSlot();

            if (allOrNothing) {
                int testRemaining = fluidStack.getAmount();
                testRemaining = testAddToExistingSlots(container, fluidStack, capacityMb, testRemaining, filter);
                testRemaining = testAddToEmptySlots(container, fluidStack, capacityMb, testRemaining, filter);
                if (testRemaining > 0) {
                    return new FluidStackTransaction(false, ActionType.ADD, fluidStack, fluidStack,
                            allOrNothing, filter, Collections.emptyList());
                }
            }

            ObjectArrayList<FluidStackSlotTransaction> slotTransactions = new ObjectArrayList<>();
            FluidStack remaining = fluidStack;

            for (short i = 0; i < container.getCapacity() && !FluidStack.isEmpty(remaining); i++) {
                FluidStack existing = container.internalGetSlot(i);
                if (existing != null && existing.isStackableWith(remaining)) {
                    FluidStackSlotTransaction t = internal_addToExistingSlot(container, i, remaining, capacityMb,
                            filter);
                    slotTransactions.add(t);
                    remaining = t.getRemainder();
                }
            }

            for (short i = 0; i < container.getCapacity() && !FluidStack.isEmpty(remaining); i++) {
                if (FluidStack.isEmpty(container.internalGetSlot(i))) {
                    FluidStackSlotTransaction t = internal_addToEmptySlot(container, i, remaining, capacityMb, filter);
                    slotTransactions.add(t);
                    remaining = t.getRemainder();
                }
            }

            return new FluidStackTransaction(true, ActionType.ADD, fluidStack, remaining,
                    allOrNothing, filter, slotTransactions);
        });
    }

    @Nonnull
    protected static ListTransaction<FluidStackTransaction> internal_addFluidStacks(
            @Nonnull FluidContainer container, @Nullable List<FluidStack> fluidStacks,
            boolean allOrNothing, boolean filter) {
        if (fluidStacks == null || fluidStacks.isEmpty()) {
            return ListTransaction.getEmptyTransaction(true);
        }
        return container.internalWriteAction(() -> {
            if (allOrNothing) {
                for (FluidStack fluidStack : fluidStacks) {
                    int capacityMb = container.getCapacityMbPerSlot();
                    int testAmountRemaining = fluidStack.getAmount();
                    testAmountRemaining = testAddToExistingSlots(container, fluidStack, capacityMb, testAmountRemaining,
                            filter);
                    if (testAddToEmptySlots(container, fluidStack, capacityMb, testAmountRemaining, filter) > 0) {
                        return new ListTransaction<FluidStackTransaction>(false,
                                fluidStacks.stream().map(fs -> new FluidStackTransaction(false, ActionType.ADD,
                                        fs, fs, allOrNothing, filter, Collections.emptyList())).toList());
                    }
                }
            }
            ObjectArrayList<FluidStackTransaction> transactions = new ObjectArrayList<>();
            for (FluidStack fluidStack : fluidStacks) {
                transactions.add(internal_addFluidStack(container, fluidStack, allOrNothing, filter));
            }
            return new ListTransaction<FluidStackTransaction>(true, transactions);
        });
    }

    @Nonnull
    protected static ListTransaction<FluidStackSlotTransaction> internal_addFluidStacksOrdered(
            @Nonnull FluidContainer container, short offset, @Nullable List<FluidStack> fluidStacks,
            boolean allOrNothing, boolean filter) {
        if (fluidStacks == null || fluidStacks.isEmpty()) {
            return ListTransaction.getEmptyTransaction(true);
        }
        ItemContainer.validateSlotIndex(offset, container.getCapacity());
        ItemContainer.validateSlotIndex((short) (offset + fluidStacks.size() - 1), container.getCapacity());
        return (ListTransaction<FluidStackSlotTransaction>) container.internalWriteAction(() -> {
            int capacityMb = container.getCapacityMbPerSlot();
            if (allOrNothing) {
                for (short i = 0; i < fluidStacks.size(); i++) {
                    short slot = (short) (offset + i);
                    FluidStack fluidStack = fluidStacks.get(i);
                    int testAmountRemaining = fluidStack.getAmount();
                    FluidStack existingAtSlot = container.internalGetSlot(slot);
                    if (FluidStack.isEmpty(existingAtSlot)) {
                        if (!filter || !container.internalCantAddToSlot(slot, fluidStack, existingAtSlot)) {
                            testAmountRemaining -= Math.min(capacityMb, testAmountRemaining);
                        }
                    } else {
                        testAmountRemaining = testAddToExistingSlot(container, slot, fluidStack, capacityMb,
                                testAmountRemaining, filter);
                    }
                    if (testAmountRemaining > 0) {
                        ObjectArrayList<FluidStackSlotTransaction> failedList = new ObjectArrayList<>();
                        for (short j = 0; j < fluidStacks.size(); j++) {
                            short failSlot = (short) (offset + j);
                            FluidStack failStack = fluidStacks.get(j);
                            failedList.add(
                                    new FluidStackSlotTransaction(false, ActionType.ADD, failSlot, null, null, null,
                                            allOrNothing, false, filter, false, failStack, failStack));
                        }
                        return new ListTransaction<>(false, failedList);
                    }
                }
            }
            ObjectArrayList<FluidStackSlotTransaction> transactions = new ObjectArrayList<>();
            for (short i = 0; i < fluidStacks.size(); i++) {
                short slot = (short) (offset + i);
                transactions
                        .add(internal_addFluidStackToSlot(container, slot, fluidStacks.get(i), allOrNothing, filter));
            }
            return new ListTransaction<FluidStackSlotTransaction>(true, transactions);
        });
    }

    protected static int testAddToExistingSlots(
            FluidContainer container, FluidStack fluidStack, int capacityMb, int remaining, boolean filter) {
        for (short i = 0; i < container.getCapacity() && remaining > 0; i++) {
            FluidStack existing = container.internalGetSlot(i);
            if (existing != null && existing.isStackableWith(fluidStack)) {
                if (!filter || !container.internalCantAddToSlot(i, fluidStack, existing)) {
                    remaining -= Math.min(capacityMb - existing.getAmount(), remaining);
                }
            }
        }
        return remaining;
    }

    @Nonnull
    protected static FluidStackTransaction internal_removeFluidStack(
            @Nonnull FluidContainer container, @Nonnull FluidStack fluidStack,
            boolean allOrNothing, boolean filter) {
        return (FluidStackTransaction) container.internalWriteAction(() -> {
            if (allOrNothing) {
                int testAmountRemaining = testRemoveFluidStackFromFluids(container, fluidStack, fluidStack.getAmount(),
                        filter);
                if (testAmountRemaining > 0) {
                    return new FluidStackTransaction(false, ActionType.REMOVE, fluidStack, fluidStack,
                            allOrNothing, filter, Collections.emptyList());
                }
            }
            ObjectArrayList<FluidStackSlotTransaction> slotTransactions = new ObjectArrayList<>();
            int amountRemaining = fluidStack.getAmount();
            for (short i = 0; i < container.getCapacity() && amountRemaining > 0; i++) {
                FluidStack slotFluidStack = container.internalGetSlot(i);
                if (!FluidStack.isEmpty(slotFluidStack) && slotFluidStack.isStackableWith(fluidStack)) {
                    FluidStackSlotTransaction transaction = internal_removeFluidStackFromSlot(container, i,
                            amountRemaining, false, filter);
                    slotTransactions.add(transaction);
                    amountRemaining = transaction.getRemainder() != null ? transaction.getRemainder().getAmount() : 0;
                }
            }
            FluidStack remainder = amountRemaining > 0 ? fluidStack.withAmount(amountRemaining) : null;
            return new FluidStackTransaction(true, ActionType.REMOVE, fluidStack, remainder,
                    allOrNothing, filter, slotTransactions);
        });
    }

    @Nonnull
    protected static ListTransaction<FluidStackTransaction> internal_removeFluidStacks(
            @Nonnull FluidContainer container, @Nullable List<FluidStack> fluidStacks,
            boolean allOrNothing, boolean filter) {
        if (fluidStacks == null || fluidStacks.isEmpty()) {
            return ListTransaction.getEmptyTransaction(true);
        }
        return container.internalWriteAction(() -> {
            if (allOrNothing) {
                for (FluidStack fluidStack : fluidStacks) {
                    int testAmountRemaining = testRemoveFluidStackFromFluids(container, fluidStack,
                            fluidStack.getAmount(), filter);
                    if (testAmountRemaining > 0) {
                        return new ListTransaction<FluidStackTransaction>(
                                false,
                                fluidStacks.stream()
                                        .map(fs -> new FluidStackTransaction(false,
                                                ActionType.REMOVE, fs, fs, allOrNothing, filter,
                                                Collections.emptyList()))
                                        .toList());
                    }
                }
            }
            ObjectArrayList<FluidStackTransaction> transactions = new ObjectArrayList<>();
            for (FluidStack fluidStack : fluidStacks) {
                transactions.add(internal_removeFluidStack(container, fluidStack, allOrNothing, filter));
            }
            return new ListTransaction<FluidStackTransaction>(true, transactions);
        });
    }

    protected static int testRemoveFluidStackFromFluids(
            @Nonnull FluidContainer container, FluidStack fluidStack, int testAmountRemaining, boolean filter) {
        for (short i = 0; i < container.getCapacity() && testAmountRemaining > 0; i++) {
            testAmountRemaining = testRemoveFluidStackFromSlot(container, i, fluidStack, testAmountRemaining, filter);
        }
        return testAmountRemaining;
    }

    @Nonnull
    protected static MoveFluidStackTransaction internal_moveFluidStackFromSlot(
            @Nonnull FluidContainer source, short slot, @Nonnull FluidContainer dest,
            boolean allOrNothing, boolean filter) {
        ItemContainer.validateSlotIndex(slot, source.getCapacity());
        return (MoveFluidStackTransaction) source.internalWriteAction(() -> dest.internalWriteAction(() -> {
            if (filter && source.internalCantRemoveFromSlot(slot)) {
                FluidStack existing = source.internalGetSlot(slot);
                FluidStackSlotTransaction removeTransaction = new FluidStackSlotTransaction(false, ActionType.REMOVE,
                        slot, existing, existing, null, false, false, filter, false, null, existing);
                return new MoveFluidStackTransaction(false, removeTransaction, source,
                        FluidStackTransaction.FAILED_ADD);
            }

            FluidStack existing = source.internalRemoveSlot(slot);
            if (FluidStack.isEmpty(existing)) {
                FluidStackSlotTransaction removeTransaction = new FluidStackSlotTransaction(false, ActionType.REMOVE,
                        slot, null, null, null, false, false, filter, false, null, null);
                return new MoveFluidStackTransaction(false, removeTransaction, source,
                        FluidStackTransaction.FAILED_ADD);
            }

            FluidStackSlotTransaction removeTransaction = new FluidStackSlotTransaction(true, ActionType.REMOVE,
                    slot, existing, null, null, false, false, filter, false, null, null);
            FluidStackTransaction addTransaction = internal_addFluidStack(dest, existing, allOrNothing, filter);
            FluidStack remainder = addTransaction.getRemainder();
            if (!FluidStack.isEmpty(remainder)) {
                internal_addFluidStackToSlot(source, slot, remainder, allOrNothing, false);
            }
            return new MoveFluidStackTransaction(addTransaction.succeeded(), removeTransaction, source, addTransaction);
        }));
    }

    private static FluidStackSlotTransaction failedAdd(
            short slot, @Nullable FluidStack slotBefore, FluidStack query, boolean allOrNothing, boolean filter) {
        return new FluidStackSlotTransaction(false, ActionType.ADD, slot, slotBefore, slotBefore, null,
                allOrNothing, false, filter, false, query, query);
    }
}

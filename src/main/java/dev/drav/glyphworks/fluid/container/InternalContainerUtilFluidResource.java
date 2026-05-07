package dev.drav.glyphworks.fluid.container;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.ResourceQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ActionType;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;

import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.transaction.FluidResourceSlotTransaction;
import dev.drav.glyphworks.fluid.transaction.FluidResourceTransaction;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

class InternalContainerUtilFluidResource {

    @Nonnull
    protected static FluidResourceSlotTransaction internal_removeFluidResourceFromSlot(
            @Nonnull FluidContainer container,
            short slot,
            @Nonnull ResourceQuantity resource,
            boolean allOrNothing,
            boolean filter) {
        ItemContainer.validateSlotIndex(slot, container.getCapacity());
        ItemContainer.validateQuantity(resource.getQuantity());

        return container.internalWriteAction(() -> {
            if (filter && container.internalCantRemoveFromSlot(slot)) {
                FluidStack existing = container.internalGetSlot(slot);
                return new FluidResourceSlotTransaction(false, ActionType.REMOVE, slot, existing, existing,
                        null, allOrNothing, false, filter, resource, resource.getQuantity(), 0);
            }

            FluidStack existing = container.internalGetSlot(slot);
            if (FluidStack.isEmpty(existing) || !resource.getResourceId().equals(existing.getFluidId())) {
                return new FluidResourceSlotTransaction(false, ActionType.REMOVE, slot, existing, existing,
                        null, allOrNothing, false, filter, resource, resource.getQuantity(), 0);
            }

            int fluidMbAvailable = existing.getAmount();
            int resourceConsumed = Math.min(fluidMbAvailable, resource.getQuantity());
            int resourceRemaining = resource.getQuantity() - resourceConsumed;

            if (allOrNothing && resourceRemaining > 0) {
                return new FluidResourceSlotTransaction(false, ActionType.REMOVE, slot, existing, existing,
                        null, allOrNothing, false, filter, resource, resource.getQuantity(), 0);
            }

            if (resourceConsumed <= 0) {
                return new FluidResourceSlotTransaction(false, ActionType.REMOVE, slot, existing, existing,
                        null, allOrNothing, false, filter, resource, resource.getQuantity(), 0);
            }

            FluidStack slotNew = existing.withAmount(fluidMbAvailable - resourceConsumed);
            container.internalSetSlot(slot, slotNew);

            return new FluidResourceSlotTransaction(true, ActionType.REMOVE, slot, existing, slotNew,
                    null, allOrNothing, false, filter, resource, resourceRemaining, resourceConsumed);
        });
    }

    @Nonnull
    protected static FluidResourceTransaction internal_removeFluidResource(
            @Nonnull FluidContainer container,
            @Nonnull String fluidId,
            int resourceAmount,
            boolean allOrNothing,
            boolean exactAmount,
            boolean filter) {
        return (FluidResourceTransaction) container.internalWriteAction(() -> {
            if (allOrNothing || exactAmount) {
                int testRemaining = testRemoveFluidResourceFromFluids(container, fluidId, resourceAmount, filter);
                if (testRemaining > 0) {
                    return new FluidResourceTransaction(false, ActionType.REMOVE,
                            new ResourceQuantity(fluidId, resourceAmount), resourceAmount, 0,
                            allOrNothing, exactAmount, filter, Collections.emptyList());
                }
            }

            ObjectArrayList<FluidResourceSlotTransaction> slotTransactions = new ObjectArrayList<>();
            int resourceRemaining = resourceAmount;
            int resourceConsumed = 0;

            for (short i = 0; i < container.getCapacity() && resourceRemaining > 0; i++) {
                FluidStack slot = container.internalGetSlot(i);
                if (!FluidStack.isEmpty(slot) && fluidId.equals(slot.getFluidId())) {
                    FluidResourceSlotTransaction transaction = internal_removeFluidResourceFromSlot(
                            container, i, new ResourceQuantity(fluidId, resourceRemaining), false, filter);
                    if (transaction.succeeded()) {
                        slotTransactions.add(transaction);
                        resourceRemaining = transaction.getRemainder();
                        resourceConsumed += transaction.getConsumed();
                    }
                }
            }

            return new FluidResourceTransaction(resourceRemaining != resourceAmount, ActionType.REMOVE,
                    new ResourceQuantity(fluidId, resourceAmount), resourceRemaining, resourceConsumed,
                    allOrNothing, exactAmount, filter, slotTransactions);
        });
    }

    protected static int testRemoveFluidResourceFromFluids(
            @Nonnull FluidContainer container,
            @Nonnull String fluidId,
            int testResourceAmount,
            boolean filter) {
        for (short i = 0; i < container.getCapacity() && testResourceAmount > 0; i++) {
            testResourceAmount = testRemoveFluidResourceFromSlot(container, i, fluidId, testResourceAmount, filter);
        }
        return testResourceAmount;
    }

    protected static int testRemoveFluidResourceFromSlot(
            @Nonnull FluidContainer container,
            short slot,
            @Nonnull String fluidId,
            int testResourceAmount,
            boolean filter) {
        if (filter && container.internalCantRemoveFromSlot(slot)) {
            return testResourceAmount;
        }

        FluidStack slotFluid = container.internalGetSlot(slot);
        if (FluidStack.isEmpty(slotFluid) || !fluidId.equals(slotFluid.getFluidId())) {
            return testResourceAmount;
        }

        return Math.max(0, testResourceAmount - slotFluid.getAmount());
    }

    @Nonnull
    protected static ListTransaction<FluidResourceTransaction> internal_removeFluidResources(
            @Nonnull FluidContainer container,
            @Nullable List<ResourceQuantity> fluidResources,
            boolean allOrNothing,
            boolean exactAmount,
            boolean filter) {
        if (fluidResources == null || fluidResources.isEmpty()) {
            return ListTransaction.getEmptyTransaction(true);
        }

        return (ListTransaction<FluidResourceTransaction>) container.internalWriteAction(() -> {
            if (allOrNothing || exactAmount) {
                for (ResourceQuantity resource : fluidResources) {
                    int testRemaining = testRemoveFluidResourceFromFluids(container, resource.getResourceId(),
                            resource.getQuantity(), filter);
                    if (testRemaining > 0) {
                        return new ListTransaction<>(false, fluidResources.stream()
                                .map(r -> new FluidResourceTransaction(false, ActionType.REMOVE,
                                        new ResourceQuantity(r.getResourceId(), r.getQuantity()), r.getQuantity(), 0,
                                        allOrNothing, exactAmount, filter, Collections.emptyList()))
                                .toList());
                    }
                }
            }

            ObjectArrayList<FluidResourceTransaction> transactions = new ObjectArrayList<>();
            for (ResourceQuantity resource : fluidResources) {
                transactions.add(internal_removeFluidResource(container, resource.getResourceId(),
                        resource.getQuantity(), allOrNothing, exactAmount, filter));
            }
            return new ListTransaction<>(true, transactions);
        });
    }
}

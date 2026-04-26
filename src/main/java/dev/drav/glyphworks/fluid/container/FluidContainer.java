package dev.drav.glyphworks.fluid.container;

import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.Short2ObjectMapCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.ResourceQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MaterialSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ResourceSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.TagSlotTransaction;

import dev.drav.glyphworks.fluid.FluidStack;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;

public class FluidContainer extends SimpleItemContainer {

    public static final BuilderCodec<FluidContainer> CODEC = BuilderCodec
            .builder(FluidContainer.class, FluidContainer::new)
            .append(
                    new KeyedCodec<>("Glyphworks_FluidContainer_Capacity", Codec.SHORT),
                    (c, v) -> c.capacity = v,
                    c -> c.capacity)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_FluidContainer_CapacityMbPerSlot", Codec.INTEGER),
                    (c, v) -> c.capacityMbPerSlot = v,
                    c -> c.capacityMbPerSlot)
            .addValidator(Validators.greaterThan(0))
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_FluidContainer_Items",
                            new Short2ObjectMapCodec<>(FluidStack.CODEC, Short2ObjectOpenHashMap::new, false)),
                    (c, map) -> {
                        c.items = new ItemStack[c.capacity];
                        c.itemsCount = 0;
                        for (Short2ObjectMap.Entry<FluidStack> entry : map.short2ObjectEntrySet()) {
                            short slot = entry.getShortKey();
                            FluidStack stack = entry.getValue();
                            if (slot >= 0 && slot < c.capacity && !ItemStack.isEmpty(stack)) {
                                c.items[slot] = stack;
                                c.itemsCount++;
                            }
                        }
                    },
                    c -> {
                        Short2ObjectOpenHashMap<FluidStack> map = new Short2ObjectOpenHashMap<>();
                        for (short i = 0; i < c.capacity; i++) {
                            if (c.items[i] instanceof FluidStack fs && !ItemStack.isEmpty(fs)) {
                                map.put(i, fs);
                            }
                        }
                        return map;
                    })
            .add()
            .afterDecode(c -> {
                if (c.items == null) {
                    c.items = new ItemStack[c.capacity];
                    c.itemsCount = 0;
                }
            })
            .build();

    private int capacityMbPerSlot;

    public FluidContainer() {
        super((short) 1);
        this.capacityMbPerSlot = 1000;
    }

    public FluidContainer(short slotCount, int capacityMbPerSlot) {
        super(slotCount);
        this.capacityMbPerSlot = capacityMbPerSlot;
    }

    public FluidContainer(@Nonnull FluidContainer other) {
        super(other);
        this.capacityMbPerSlot = other.capacityMbPerSlot;
    }

    @Override
    public FluidContainer clone() {
        return new FluidContainer(this);
    }

    public int getCapacityMbPerSlot() {
        return capacityMbPerSlot;
    }

    @Nullable
    public FluidStack getFluidStack(short slot) {
        ItemStack stack = getItemStack(slot);
        return stack instanceof FluidStack fs ? fs : null;
    }

    // -------------------------------------------------------------------------
    // Fluid-native transaction API
    // -------------------------------------------------------------------------

    @Nonnull
    public ItemStackTransaction addFluidStack(@Nonnull FluidStack fluidStack) {
        return addFluidStack(fluidStack, false, true);
    }

    @Nonnull
    public ItemStackTransaction addFluidStack(@Nonnull FluidStack fluidStack, boolean allOrNothing, boolean filter) {
        ItemStackTransaction transaction = InternalContainerUtilFluidStack.internal_addFluidStack(this, fluidStack,
                allOrNothing, filter);
        sendUpdate(transaction);
        return transaction;
    }

    @Nonnull
    public ItemStackSlotTransaction addFluidStackToSlot(short slot, @Nonnull FluidStack fluidStack) {
        return addFluidStackToSlot(slot, fluidStack, false, true);
    }

    @Nonnull
    public ItemStackSlotTransaction addFluidStackToSlot(short slot, @Nonnull FluidStack fluidStack,
            boolean allOrNothing, boolean filter) {
        ItemStackSlotTransaction transaction = InternalContainerUtilFluidStack.internal_addFluidStackToSlot(this, slot,
                fluidStack, allOrNothing, filter);
        sendUpdate(transaction);
        return transaction;
    }

    @Nonnull
    public ItemStackSlotTransaction removeFluidStackFromSlot(short slot, int amountMb) {
        return removeFluidStackFromSlot(slot, amountMb, true, true);
    }

    @Nonnull
    public ItemStackSlotTransaction removeFluidStackFromSlot(short slot, int amountMb, boolean allOrNothing,
            boolean filter) {
        ItemStackSlotTransaction transaction = InternalContainerUtilFluidStack.internal_removeFluidStackFromSlot(this,
                slot, amountMb, allOrNothing, filter);
        sendUpdate(transaction);
        return transaction;
    }

    @Nonnull
    public MoveTransaction<ItemStackTransaction> moveFluidStackFromSlot(short slot, @Nonnull FluidContainer dest) {
        return moveFluidStackFromSlot(slot, dest, false, true);
    }

    @Nonnull
    public MoveTransaction<ItemStackTransaction> moveFluidStackFromSlot(short slot, @Nonnull FluidContainer dest,
            boolean allOrNothing, boolean filter) {
        MoveTransaction<ItemStackTransaction> transaction = InternalContainerUtilFluidStack
                .internal_moveFluidStackFromSlot(this, slot, dest, allOrNothing, filter);
        sendUpdate(transaction);
        dest.sendUpdate(transaction.toInverted(this));
        return transaction;
    }

    <T> T internalWriteAction(@Nonnull Supplier<T> action) {
        return writeAction(action);
    }

    @Nullable
    ItemStack internalGetSlot(short slot) {
        return internal_getSlot(slot);
    }

    @Nullable
    ItemStack internalSetSlot(short slot, @Nullable ItemStack itemStack) {
        return internal_setSlot(slot, itemStack);
    }

    @Nullable
    ItemStack internalRemoveSlot(short slot) {
        return internal_removeSlot(slot);
    }

    boolean internalCantAddToSlot(short slot, @Nonnull ItemStack itemStack, @Nullable ItemStack slotItemStack) {
        return cantAddToSlot(slot, itemStack, slotItemStack);
    }

    boolean internalCantRemoveFromSlot(short slot) {
        return cantRemoveFromSlot(slot);
    }

    // -------------------------------------------------------------------------
    // Vanilla item-stack methods — unsupported on FluidContainer
    // -------------------------------------------------------------------------

    @Override
    public ItemStackSlotTransaction addItemStackToSlot(short slot, @Nonnull ItemStack itemStack) {
        throw new UnsupportedOperationException("Use addFluidStackToSlot on FluidContainer");
    }

    @Override
    public ItemStackSlotTransaction addItemStackToSlot(short slot, @Nonnull ItemStack itemStack, boolean allOrNothing,
            boolean filter) {
        throw new UnsupportedOperationException("Use addFluidStackToSlot on FluidContainer");
    }

    @Override
    public ItemStackTransaction addItemStack(@Nonnull ItemStack itemStack) {
        throw new UnsupportedOperationException("Use addFluidStack on FluidContainer");
    }

    @Override
    public ItemStackTransaction addItemStack(@Nonnull ItemStack itemStack, boolean allOrNothing, boolean fullStacks,
            boolean filter) {
        throw new UnsupportedOperationException("Use addFluidStack on FluidContainer");
    }

    @Override
    @Nonnull
    public MaterialSlotTransaction removeMaterialFromSlot(short slot, @Nonnull MaterialQuantity material) {
        throw new UnsupportedOperationException("FluidContainer does not support material transactions");
    }

    @Override
    @Nonnull
    public MaterialSlotTransaction removeMaterialFromSlot(short slot, @Nonnull MaterialQuantity material,
            boolean allOrNothing, boolean exactAmount, boolean filter) {
        throw new UnsupportedOperationException("FluidContainer does not support material transactions");
    }

    @Override
    @Nonnull
    public ResourceSlotTransaction removeResourceFromSlot(short slot, @Nonnull ResourceQuantity resource) {
        throw new UnsupportedOperationException("FluidContainer does not support resource transactions");
    }

    @Override
    @Nonnull
    public ResourceSlotTransaction removeResourceFromSlot(short slot, @Nonnull ResourceQuantity resource,
            boolean allOrNothing, boolean exactAmount, boolean filter) {
        throw new UnsupportedOperationException("FluidContainer does not support resource transactions");
    }

    @Override
    @Nonnull
    public TagSlotTransaction removeTagFromSlot(short slot, int tagIndex, int quantity) {
        throw new UnsupportedOperationException("FluidContainer does not support tag transactions");
    }

    @Override
    @Nonnull
    public TagSlotTransaction removeTagFromSlot(short slot, int tagIndex, int quantity, boolean allOrNothing,
            boolean filter) {
        throw new UnsupportedOperationException("FluidContainer does not support tag transactions");
    }

    @Override
    @Nonnull
    public MoveTransaction<ItemStackTransaction> moveItemStackFromSlot(short slot, @Nonnull ItemContainer containerTo) {
        throw new UnsupportedOperationException("Use moveFluidStackFromSlot on FluidContainer");
    }

    @Override
    @Nonnull
    public MoveTransaction<ItemStackTransaction> moveItemStackFromSlot(short slot, @Nonnull ItemContainer containerTo,
            boolean filter) {
        throw new UnsupportedOperationException("Use moveFluidStackFromSlot on FluidContainer");
    }

    @Override
    @Nonnull
    public MoveTransaction<ItemStackTransaction> moveItemStackFromSlot(short slot, @Nonnull ItemContainer containerTo,
            boolean allOrNothing, boolean filter) {
        throw new UnsupportedOperationException("Use moveFluidStackFromSlot on FluidContainer");
    }

    // -------------------------------------------------------------------------
    // Type-safety guard
    // -------------------------------------------------------------------------

    @Override
    protected ItemStack internal_setSlot(short slot, ItemStack itemStack) {
        if (itemStack != null && !(itemStack instanceof FluidStack)) {
            throw new IllegalArgumentException(
                    "FluidContainer only accepts FluidStack, got: " + itemStack.getClass().getSimpleName());
        }
        return super.internal_setSlot(slot, itemStack);
    }
}

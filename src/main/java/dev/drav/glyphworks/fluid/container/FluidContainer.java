package dev.drav.glyphworks.fluid.container;

import java.util.EnumMap;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.Short2ObjectMapCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.transaction.ClearTransaction;

import dev.drav.glyphworks.fluid.event.FluidItemRegistry;

import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.container.filter.FluidSlotFilter;
import dev.drav.glyphworks.fluid.transaction.FluidStackSlotTransaction;
import dev.drav.glyphworks.fluid.transaction.FluidStackTransaction;
import dev.drav.glyphworks.fluid.transaction.MoveFluidStackTransaction;
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
                        c.fluidStacks = new FluidStack[c.capacity];
                        for (Short2ObjectMap.Entry<FluidStack> entry : map.short2ObjectEntrySet()) {
                            short slot = entry.getShortKey();
                            FluidStack stack = entry.getValue();
                            if (slot >= 0 && slot < c.capacity && !FluidStack.isEmpty(stack)) {
                                c.fluidStacks[slot] = stack;
                            }
                        }
                    },
                    c -> {
                        Short2ObjectOpenHashMap<FluidStack> map = new Short2ObjectOpenHashMap<>();
                        for (short i = 0; i < c.capacity; i++) {
                            FluidStack fs = c.fluidStacks[i];
                            if (!FluidStack.isEmpty(fs)) {
                                map.put(i, fs);
                            }
                        }
                        return map;
                    })
            .add()
            .afterDecode(c -> {
                if (c.fluidStacks == null || c.fluidStacks.length != c.capacity) {
                    c.fluidStacks = new FluidStack[c.capacity];
                }
            })
            .build();

    private short capacity;
    private int capacityMbPerSlot;
    private FluidStack[] fluidStacks;

    @Nullable
    private transient EnumMap<FilterActionType, FluidSlotFilter[]> fluidSlotFiltersByAction;

    public FluidContainer() {
        super((short) 1);
        this.capacity = 1;
        this.capacityMbPerSlot = 1000;
        this.fluidStacks = new FluidStack[1];
        this.fluidSlotFiltersByAction = new EnumMap<>(FilterActionType.class);
    }

    public FluidContainer(short capacity, int capacityMbPerSlot) {
        super(capacity);
        this.capacity = capacity;
        this.capacityMbPerSlot = capacityMbPerSlot;
        this.fluidStacks = new FluidStack[capacity];
        this.fluidSlotFiltersByAction = new EnumMap<>(FilterActionType.class);
    }

    public FluidContainer(@Nonnull FluidContainer other) {
        super(other);
        this.capacity = other.capacity;
        this.capacityMbPerSlot = other.capacityMbPerSlot;
        this.fluidStacks = other.fluidStacks.clone();
        this.fluidSlotFiltersByAction = copyFilters(other.fluidSlotFiltersByAction);
    }

    @Override
    public FluidContainer clone() {
        return new FluidContainer(this);
    }

    @Override
    public short getCapacity() {
        return capacity;
    }

    public int getCapacityMbPerSlot() {
        return capacityMbPerSlot;
    }

    public void setSlotFilter(short slot, @Nullable FluidSlotFilter filter) {
        setSlotFilter(FilterActionType.ADD, slot, filter);
    }

    public void setSlotFilter(@Nonnull FilterActionType actionType, short slot, @Nullable FluidSlotFilter filter) {
        ItemContainer.validateSlotIndex(slot, capacity);
        if (fluidSlotFiltersByAction == null) {
            fluidSlotFiltersByAction = new EnumMap<>(FilterActionType.class);
        }
        FluidSlotFilter[] filters = fluidSlotFiltersByAction.get(actionType);
        if (filters == null || filters.length != capacity) {
            filters = new FluidSlotFilter[capacity];
            fluidSlotFiltersByAction.put(actionType, filters);
        }
        filters[slot] = filter;
    }

    @Nullable
    public FluidStack getFluidStack(short slot) {
        if (slot < 0 || slot >= capacity)
            return null;
        return fluidStacks[slot];
    }

    @Override
    @Nullable
    protected ItemStack internal_getSlot(short slot) {
        FluidStack fluidStack = fluidStacks[slot];
        if (FluidStack.isEmpty(fluidStack))
            return null;
        String itemId = FluidItemRegistry.resolveItemId(fluidStack.getFluidId());
        if (itemId == null)
            return null;
        return new ItemStack(itemId, Math.max(1, fluidStack.getAmount()));
    }

    // -------------------------------------------------------------------------
    // Fluid-native transaction API
    // -------------------------------------------------------------------------

    @Nonnull
    public FluidStackTransaction addFluidStack(@Nonnull FluidStack fluidStack) {
        return addFluidStack(fluidStack, false, true);
    }

    @Nonnull
    public FluidStackTransaction addFluidStack(@Nonnull FluidStack fluidStack, boolean allOrNothing, boolean filter) {
        FluidStackTransaction transaction = InternalContainerUtilFluidStack.internal_addFluidStack(
                this, fluidStack, allOrNothing, filter);
        sendUpdate(transaction);
        return transaction;
    }

    @Nonnull
    public FluidStackSlotTransaction addFluidStackToSlot(short slot, @Nonnull FluidStack fluidStack) {
        return addFluidStackToSlot(slot, fluidStack, false, true);
    }

    @Nonnull
    public FluidStackSlotTransaction addFluidStackToSlot(short slot, @Nonnull FluidStack fluidStack,
            boolean allOrNothing, boolean filter) {
        FluidStackSlotTransaction transaction = InternalContainerUtilFluidStack.internal_addFluidStackToSlot(
                this, slot, fluidStack, allOrNothing, filter);
        sendUpdate(transaction);
        return transaction;
    }

    @Nonnull
    public FluidStackSlotTransaction removeFluidStackFromSlot(short slot, int amountMb) {
        return removeFluidStackFromSlot(slot, amountMb, true, true);
    }

    @Nonnull
    public FluidStackSlotTransaction removeFluidStackFromSlot(short slot, int amountMb, boolean allOrNothing,
            boolean filter) {
        FluidStackSlotTransaction transaction = InternalContainerUtilFluidStack.internal_removeFluidStackFromSlot(
                this, slot, amountMb, allOrNothing, filter);
        sendUpdate(transaction);
        return transaction;
    }

    @Nonnull
    public MoveFluidStackTransaction moveFluidStackFromSlot(short slot, @Nonnull FluidContainer dest) {
        return moveFluidStackFromSlot(slot, dest, false, true);
    }

    @Nonnull
    public MoveFluidStackTransaction moveFluidStackFromSlot(short slot, @Nonnull FluidContainer dest,
            boolean allOrNothing, boolean filter) {
        MoveFluidStackTransaction transaction = InternalContainerUtilFluidStack
                .internal_moveFluidStackFromSlot(this, slot, dest, allOrNothing, filter);
        sendUpdate(transaction);
        dest.sendUpdate(transaction.getAddTransaction());
        return transaction;
    }

    // -------------------------------------------------------------------------
    // Package-private backing store access for InternalContainerUtilFluidStack
    // -------------------------------------------------------------------------

    <T> T internalWriteAction(@Nonnull Supplier<T> action) {
        return writeAction(action);
    }

    @Nullable
    FluidStack internalGetSlot(short slot) {
        return fluidStacks[slot];
    }

    @Nullable
    FluidStack internalSetSlot(short slot, @Nullable FluidStack fluidStack) {
        FluidStack previous = fluidStacks[slot];
        fluidStacks[slot] = FluidStack.isEmpty(fluidStack) ? null : fluidStack;
        return previous;
    }

    @Nullable
    FluidStack internalRemoveSlot(short slot) {
        FluidStack previous = fluidStacks[slot];
        fluidStacks[slot] = null;
        return previous;
    }

    boolean internalCantAddToSlot(short slot, @Nonnull FluidStack fluidStack,
            @Nullable FluidStack slotFluidStack) {
        FluidSlotFilter filter = getSlotFilter(FilterActionType.ADD, slot);
        if (filter != null && !filter.test(FilterActionType.ADD, this, slot, fluidStack, slotFluidStack)) {
            return true;
        }
        return cantAddToSlot(slot, null, null);
    }

    boolean internalCantRemoveFromSlot(short slot) {
        FluidStack existing = internalGetSlot(slot);
        FluidSlotFilter filter = getSlotFilter(FilterActionType.REMOVE, slot);
        if (filter != null && !filter.test(FilterActionType.REMOVE, this, slot, null, existing)) {
            return true;
        }
        return cantRemoveFromSlot(slot);
    }

    @Nullable
    private FluidSlotFilter getSlotFilter(@Nonnull FilterActionType actionType, short slot) {
        if (fluidSlotFiltersByAction == null) {
            return null;
        }
        FluidSlotFilter[] filters = fluidSlotFiltersByAction.get(actionType);
        if (filters == null || slot < 0 || slot >= filters.length) {
            return null;
        }
        return filters[slot];
    }

    @Nullable
    private EnumMap<FilterActionType, FluidSlotFilter[]> copyFilters(
            @Nullable EnumMap<FilterActionType, FluidSlotFilter[]> source) {
        if (source == null || source.isEmpty()) {
            return new EnumMap<>(FilterActionType.class);
        }

        EnumMap<FilterActionType, FluidSlotFilter[]> copy = new EnumMap<>(FilterActionType.class);
        for (FilterActionType actionType : source.keySet()) {
            FluidSlotFilter[] filters = source.get(actionType);
            copy.put(actionType, filters != null ? filters.clone() : null);
        }
        return copy;
    }

    // -------------------------------------------------------------------------
    // Clear
    // -------------------------------------------------------------------------

    @Override
    public ClearTransaction clear() {
        writeAction(() -> {
            for (short i = 0; i < capacity; i++) {
                fluidStacks[i] = null;
            }
            return null;
        });
        sendUpdate(ClearTransaction.EMPTY);
        return ClearTransaction.EMPTY;
    }
}

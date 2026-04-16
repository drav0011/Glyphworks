package dev.drav.glyphworks.fluid.component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.fluid.FluidItemRegistry;

/**
 * Stores fluid inside a block as a single-slot item container.
 *
 * <p>
 * Fluid is represented by one {@link ItemStack} in slot 0:
 * <ul>
 *   <li>{@code quantity} — number of whole buckets (ceil of amount / 1 000 mB), always ≥ 1 when non-empty</li>
 *   <li>{@code durability} — exact amount stored in mB</li>
 *   <li>{@code maxDurability} — capacity of this container in mB</li>
 * </ul>
 * The container is locked with {@link FilterType#DENY_ALL} — players cannot
 * interact with it directly. All reads and writes go through the grid or
 * dedicated systems.
 *
 * <p>
 * A container is considered <em>empty</em> when slot 0 holds no stack.
 * On the first fill, the slot is created and the container is locked to that
 * fluid type until it is fully drained.
 */
public class FluidContainerComponent implements Component<ChunkStore> {

    public static final BuilderCodec<FluidContainerComponent> CODEC = BuilderCodec
            .builder(FluidContainerComponent.class, () -> new FluidContainerComponent())
            .append(
                    new KeyedCodec<>("Glyphworks_FluidContainerComponent_CapacityMb", Codec.INTEGER),
                    (c, v) -> c.capacityMb = v,
                    c -> c.capacityMb)
            .addValidator(Validators.greaterThan(0))
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_FluidContainerComponent_Container", SimpleItemContainer.CODEC),
                    (c, v) -> {
                        c.itemContainer = v;
                        c.applyContainerFilters();
                    },
                    c -> c.itemContainer)
            .add()
            .build();

    public static ComponentType<ChunkStore, FluidContainerComponent> getComponentType() {
        return GlyphworksPlugin.get().getFluidModule().getFluidContainerComponentType();
    }

    /** Maximum fluid this container can hold, in mB. Set via JSON asset. */
    private int capacityMb;

    /** Single-slot container holding the fluid item stack. Always DENY_ALL. */
    private SimpleItemContainer itemContainer;

    /** No-arg constructor required by {@link #CODEC}. */
    public FluidContainerComponent() {
        this.itemContainer = new SimpleItemContainer((short) 1);
        applyContainerFilters();
    }

    public FluidContainerComponent(int capacityMb) {
        this.capacityMb = capacityMb;
        this.itemContainer = new SimpleItemContainer((short) 1);
        applyContainerFilters();
    }

    public FluidContainerComponent(@Nonnull FluidContainerComponent other) {
        this.capacityMb = other.capacityMb;
        this.itemContainer = new SimpleItemContainer((short) 1);
        ItemStack existingStack = other.itemContainer.getItemStack((short) 0);
        if (existingStack != null) {
            this.itemContainer.setItemStackForSlot((short) 0, existingStack, false);
        }
        applyContainerFilters();
    }

    private void applyContainerFilters() {
        itemContainer.setGlobalFilter(FilterType.DENY_ALL);
    }

    // ---- accessors ----------------------------------------------------------

    public int getCapacity() {
        return capacityMb;
    }

    /** Returns the amount of fluid stored, in mB. */
    public int getAmount() {
        ItemStack stack = itemContainer.getItemStack((short) 0);
        return stack != null ? (int) stack.getDurability() : 0;
    }

    @Nullable
    public String getFluidId() {
        ItemStack stack = itemContainer.getItemStack((short) 0);
        if (stack == null)
            return null;
        return FluidItemRegistry.resolveFluidId(stack.getItemId());
    }

    /** {@code true} when slot 0 is empty. */
    public boolean isEmpty() {
        return itemContainer.getItemStack((short) 0) == null;
    }

    /** Available space in mB. */
    public int availableSpace() {
        return capacityMb - getAmount();
    }

    /** Returns the backing item container. Always {@link FilterType#DENY_ALL}. */
    public SimpleItemContainer getItemContainer() {
        return itemContainer;
    }

    /**
     * Attempts to add {@code liters} mB of {@code fluidId} to this container.
     *
     * <p>
     * Rejected (returns 0) if the container is locked to a different fluid
     * type, or if {@code fluidId} has no registered item representation.
     * Otherwise up to {@code liters} mB are accepted (capped by available
     * space) and the number actually accepted is returned.
     *
     * @return mB actually added (0 – liters)
     */
    public int fill(String fluidId, int liters) {
        String currentFluidId = getFluidId();
        if (currentFluidId != null && !currentFluidId.equals(fluidId))
            return 0;
        int accepted = Math.min(liters, availableSpace());
        if (accepted <= 0)
            return 0;
        String itemId = FluidItemRegistry.resolveItemId(fluidId);
        if (itemId == null)
            return 0;
        int newAmount = getAmount() + accepted;
        itemContainer.setItemStackForSlot((short) 0, buildStack(itemId, newAmount), false);
        return accepted;
    }

    /**
     * Drains up to {@code liters} mB from this container.
     *
     * <p>
     * If the container becomes empty, slot 0 is cleared automatically.
     *
     * @return mB actually drained (0 – liters)
     */
    public int drain(int liters) {
        ItemStack stack = itemContainer.getItemStack((short) 0);
        if (stack == null)
            return 0;
        int current = (int) stack.getDurability();
        int removed = Math.min(liters, current);
        if (removed <= 0)
            return 0;
        int newAmount = current - removed;
        if (newAmount == 0) {
            itemContainer.removeItemStackFromSlot((short) 0);
        } else {
            itemContainer.setItemStackForSlot((short) 0, buildStack(stack.getItemId(), newAmount), false);
        }
        return removed;
    }

    private ItemStack buildStack(String itemId, int amountMb) {
        int qty = Math.max(1, (int) Math.ceil((double) amountMb / 1000));
        return new ItemStack(itemId, qty, amountMb, capacityMb, null);
    }

    @Override
    @Nullable
    public Component<ChunkStore> clone() {
        return new FluidContainerComponent(this);
    }
}

package dev.drav.glyphworks.fluid.component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.container.FluidContainer;

/**
 * Stores fluid inside a block as a {@link FluidContainer} with a single slot.
 *
 * <p>
 * Fluid is represented as a {@link FluidStack} in slot 0. The container is locked
 * against player interaction — all reads and writes go through the grid or
 * dedicated systems.
 *
 * <p>
 * A container is considered <em>empty</em> when slot 0 holds no stack. On the first
 * fill the slot is locked to that fluid type until it is fully drained.
 */
public class FluidContainerComponent implements Component<ChunkStore> {

    public static final BuilderCodec<FluidContainerComponent> CODEC = BuilderCodec
            .builder(FluidContainerComponent.class, FluidContainerComponent::new)
            .append(
                    new KeyedCodec<>("Glyphworks_FluidContainerComponent_CapacityMb", Codec.INTEGER),
                    (c, v) -> c.capacityMb = v,
                    c -> c.capacityMb)
            .addValidator(Validators.greaterThan(0))
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_FluidContainerComponent_Container", FluidContainer.CODEC),
                    (c, v) -> c.itemContainer = v,
                    c -> c.itemContainer)
            .add()
            .build();

    public static ComponentType<ChunkStore, FluidContainerComponent> getComponentType() {
        return GlyphworksPlugin.get().getFluidModule().getFluidContainerComponentType();
    }

    /** Maximum fluid this container can hold, in mB. Set via JSON asset. */
    private int capacityMb;

    /** Single-slot container holding the fluid stack. Always DENY_ALL. */
    private FluidContainer itemContainer;

    /** No-arg constructor required by {@link #CODEC}. */
    public FluidContainerComponent() {
        this.capacityMb = 1000;
        this.itemContainer = new FluidContainer();
    }

    public FluidContainerComponent(int capacityMb) {
        this.capacityMb = capacityMb;
        this.itemContainer = new FluidContainer();
    }

    public FluidContainerComponent(@Nonnull FluidContainerComponent other) {
        this.capacityMb = other.capacityMb;
        this.itemContainer = new FluidContainer();
        FluidStack existing = other.itemContainer.getFluid();
        if (existing != null) {
            this.itemContainer.setItemStackForSlot((short) 0, existing, false);
        }
    }

    public int getCapacity() {
        return capacityMb;
    }

    /** Returns the fluid currently stored, or {@code null} if the container is empty. */
    @Nullable
    public FluidStack getFluid() {
        return itemContainer.getFluid();
    }

    /** Returns the amount of fluid stored, in mB. */
    public int getAmount() {
        FluidStack fluid = getFluid();
        return fluid != null ? fluid.getAmountMb() : 0;
    }

    @Nullable
    public String getFluidId() {
        FluidStack fluid = getFluid();
        return fluid != null ? fluid.getFluidId() : null;
    }

    /** {@code true} when slot 0 is empty. */
    public boolean isEmpty() {
        return itemContainer.getItemStack((short) 0) == null;
    }

    /** Available space in mB. */
    public int availableSpace() {
        return capacityMb - getAmount();
    }

    /** Returns the backing {@link FluidContainer}. */
    public FluidContainer getItemContainer() {
        return itemContainer;
    }

    /**
     * Attempts to add the fluid in {@code incoming} to this container.
     *
     * <p>
     * Rejected (returns 0) if the container already holds a different fluid type.
     * Otherwise up to {@code incoming.getAmountMb()} mB are accepted, capped by
     * available space.
     *
     * @return mB actually added (0 – incoming.getAmountMb())
     */
    public int fill(@Nonnull FluidStack incoming) {
        String incomingFluidId = incoming.getFluidId();
        if (incomingFluidId == null) return 0;

        String currentFluidId = getFluidId();
        if (currentFluidId != null && !currentFluidId.equals(incomingFluidId)) return 0;

        int accepted = Math.min(incoming.getAmountMb(), availableSpace());
        if (accepted <= 0) return 0;

        int newAmount = getAmount() + accepted;
        itemContainer.setItemStackForSlot((short) 0, new FluidStack(incomingFluidId, newAmount, capacityMb), false);
        return accepted;
    }

    /**
     * Drains up to {@code mB} mB from this container.
     *
     * <p>
     * If the container becomes empty, slot 0 is cleared automatically.
     *
     * @return the drained fluid stack, or {@code null} if the container was already empty
     */
    @Nullable
    public FluidStack drain(int mB) {
        FluidStack current = getFluid();
        if (current == null) return null;

        int removed = Math.min(mB, current.getAmountMb());
        if (removed <= 0) return null;

        int remaining = current.getAmountMb() - removed;
        if (remaining == 0) {
            itemContainer.setItemStackForSlot((short) 0, null, false);
        } else {
            itemContainer.setItemStackForSlot((short) 0, current.withAmount(remaining), false);
        }
        return new FluidStack(current.getFluidId(), removed, capacityMb);
    }

    @Override
    @Nullable
    public Component<ChunkStore> clone() {
        return new FluidContainerComponent(this);
    }
}

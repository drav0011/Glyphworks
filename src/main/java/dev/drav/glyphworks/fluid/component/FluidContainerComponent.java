package dev.drav.glyphworks.fluid.component;

import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;

/**
 * Stores fluid inside a grid-connected block (tank, buffer, etc.).
 *
 * <p>
 * Fields use <b>liters</b> as their unit. One block/bucket occupies 1 000 L.
 *
 * <p>
 * A container is considered <em>empty</em> when {@code amount == 0} and
 * {@code lockedFluidId == null}. On the first fill, {@code lockedFluidId} is
 * set to the incoming fluid's asset ID and the container can only accept that
 * fluid until it is fully drained (at which point both fields reset to 0 /
 * {@code null}).
 */
public class FluidContainerComponent implements Component<ChunkStore> {

    public static final BuilderCodec<FluidContainerComponent> CODEC = BuilderCodec
            .builder(FluidContainerComponent.class, FluidContainerComponent::new)
            .append(
                    new KeyedCodec<>("FluidContainer_Capacity", Codec.INTEGER),
                    (c, v) -> c.capacity = v,
                    c -> c.capacity)
            .add()
            .append(
                    new KeyedCodec<>("FluidContainer_Amount", Codec.INTEGER),
                    (c, v) -> c.amount = v,
                    c -> c.amount)
            .add()
            .append(
                    new KeyedCodec<>("FluidContainer_LockedFluidId", Codec.STRING),
                    (c, v) -> c.lockedFluidId = v,
                    c -> c.lockedFluidId)
            .add()
            .build();

    public static ComponentType<ChunkStore, FluidContainerComponent> getComponentType() {
        return GlyphworksPlugin.get().getFluidContainerComponentType();
    }

    /** Maximum fluid this container can hold, in liters. Set via JSON asset. */
    private int capacity;

    /** Current fluid stored, in liters (0 – capacity). */
    private int amount;

    /**
     * Asset ID of the fluid currently filling this container, or {@code null}
     * when the container is empty. Once set, only this fluid type is accepted
     * until the container is fully drained.
     */
    @Nullable
    private String lockedFluidId;

    /** No-arg constructor required by {@link #CODEC}. */
    public FluidContainerComponent() {
    }

    public FluidContainerComponent(int capacity) {
        this.capacity = capacity;
    }

    // ---- accessors ----------------------------------------------------------

    public int getCapacity() {
        return capacity;
    }

    /** Returns the amount of fluid stored, in liters. */
    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(0, Math.min(amount, capacity));
    }

    @Nullable
    public String getLockedFluidId() {
        return lockedFluidId;
    }

    public void setLockedFluidId(@Nullable String lockedFluidId) {
        this.lockedFluidId = lockedFluidId;
    }

    /** {@code true} when {@code amount == 0} and {@code lockedFluidId == null}. */
    public boolean isEmpty() {
        return amount == 0 && lockedFluidId == null;
    }

    /** Available space in liters. */
    public int availableSpace() {
        return capacity - amount;
    }

    /**
     * Attempts to add {@code liters} of {@code fluidId} to this container.
     *
     * <p>
     * The operation is rejected (returns 0) if the container is locked to a
     * different fluid type. Otherwise up to {@code liters} are accepted
     * (capped by available space), {@code lockedFluidId} is set if it was
     * {@code null}, and the number actually accepted is returned.
     *
     * @return liters actually added (0 – liters)
     */
    public int fill(String fluidId, int liters) {
        if (lockedFluidId != null && !lockedFluidId.equals(fluidId))
            return 0;
        int accepted = Math.min(liters, availableSpace());
        if (accepted <= 0)
            return 0;
        if (lockedFluidId == null)
            lockedFluidId = fluidId;
        amount += accepted;
        return accepted;
    }

    /**
     * Drains up to {@code liters} from this container.
     *
     * <p>
     * If the container becomes empty after draining, {@code lockedFluidId}
     * is cleared automatically.
     *
     * @return liters actually drained (0 – liters)
     */
    public int drain(int liters) {
        int removed = Math.min(liters, amount);
        if (removed <= 0)
            return 0;
        amount -= removed;
        if (amount == 0)
            lockedFluidId = null;
        return removed;
    }

    @Override
    @Nullable
    public Component<ChunkStore> clone() {
        FluidContainerComponent copy = new FluidContainerComponent(capacity);
        copy.amount = 0;
        copy.lockedFluidId = null;
        return copy;
    }
}

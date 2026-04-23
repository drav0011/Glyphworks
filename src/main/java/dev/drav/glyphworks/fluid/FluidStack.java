package dev.drav.glyphworks.fluid;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.server.core.inventory.ItemStack;

/**
 * An {@link ItemStack} that represents a measured amount of a specific fluid.
 *
 * <p>
 * The inherited fields carry fluid semantics:
 * {@code itemId} is the item for this fluid (from {@link FluidItemRegistry})
 * {@code durability} is the exact amount in mB
 * {@code maxDurability} is the container capacity in mB
 * {@code quantity} is the whole display count
 * ({@code ceil(amount / MB_PER_BLOCK)}), always ≥ 1.
 *
 * <p>
 * Because {@code FluidStack} is an {@code ItemStack} it can be stored in any
 * {@link com.hypixel.hytale.server.core.inventory.container.ItemContainer}
 * slot.
 * Use {@code instanceof FluidStack} to distinguish fluid slots from item slots.
 */
public class FluidStack extends ItemStack {

    @Nonnull
    public static final BuilderCodec<FluidStack> CODEC = BuilderCodec
            .builder(FluidStack.class, FluidStack::new)
            .append(
                    new KeyedCodec<>("Glyphworks_FluidStack_Id", Codec.STRING),
                    (s, id) -> s.itemId = id,
                    s -> s.itemId)
            .addValidator(Validators.nonNull())
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_FluidStack_Quantity", Codec.INTEGER),
                    (s, qty) -> s.quantity = qty,
                    s -> s.quantity)
            .addValidator(Validators.greaterThan(0))
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_FluidStack_Durability", Codec.DOUBLE),
                    (s, dur) -> s.durability = dur,
                    s -> s.durability)
            .addValidator(Validators.greaterThanOrEqual(0.0))
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_FluidStack_MaxDurability", Codec.DOUBLE),
                    (s, md) -> s.maxDurability = md,
                    s -> s.maxDurability)
            .addValidator(Validators.greaterThanOrEqual(0.0))
            .add()
            .build();

    /**
     * No-arg constructor required by {@link #CODEC}. Fields are set by codec
     * setters after construction.
     */
    protected FluidStack() {
        super();
    }

    /**
     * Creates a fluid stack for {@code fluidId} holding {@code amountMb} mB
     * inside a container of {@code capacityMb} mB.
     *
     * @throws IllegalArgumentException if {@code fluidId} has no registered bucket
     *                                  item
     */
    public FluidStack(@Nonnull String fluidId, int amountMb, int capacityMb) {
        super(resolveItemId(fluidId), displayBuckets(amountMb), (double) amountMb, (double) capacityMb, null);
    }

    @Nullable
    public String getFluidId() {
        return FluidItemRegistry.resolveFluidId(itemId);
    }

    public int getAmountMb() {
        return (int) getDurability();
    }

    public int getCapacityMb() {
        return (int) getMaxDurability();
    }

    @Override
    public boolean isEmpty() {
        return getAmountMb() == 0;
    }

    @Override
    public boolean isStackableWith(@Nullable ItemStack other) {
        if (!(other instanceof FluidStack))
            return false;
        String myFluidId = getFluidId();
        String otherFluidId = ((FluidStack) other).getFluidId();
        return myFluidId != null && myFluidId.equals(otherFluidId);
    }

    /**
     * Returns a new {@code FluidStack} with {@code amountMb} mB, or {@code null}
     * if {@code amountMb} is zero (matches the {@link ItemStack#withQuantity} →
     * null pattern).
     */
    @Nullable
    public FluidStack withAmount(int amountMb) {
        if (amountMb <= 0)
            return null;
        String fluidId = getFluidId();
        if (fluidId == null)
            return null;
        return new FluidStack(fluidId, amountMb, getCapacityMb());
    }

    /**
     * Returns {@code stack} cast as a {@code FluidStack} if it already is one;
     * otherwise constructs a new {@code FluidStack} if the item ID maps to a known
     * fluid. Returns {@code null} if the stack is null or not a fluid item.
     */
    @Nullable
    public static FluidStack fromItemStack(@Nullable ItemStack stack) {
        if (stack == null)
            return null;
        if (stack instanceof FluidStack)
            return (FluidStack) stack;
        String fluidId = FluidItemRegistry.resolveFluidId(stack.getItemId());
        if (fluidId == null)
            return null;
        return new FluidStack(fluidId, (int) stack.getDurability(), (int) stack.getMaxDurability());
    }

    private static String resolveItemId(String fluidId) {
        String itemId = FluidItemRegistry.resolveItemId(fluidId);
        if (itemId == null) {
            throw new IllegalArgumentException("No item registered for fluid: " + fluidId);
        }
        return itemId;
    }

    private static int displayBuckets(int amountMb) {
        return Math.max(1, (int) Math.ceil(amountMb / 1000.0));
    }
}

package dev.drav.glyphworks.fluid;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.server.core.io.NetworkSerializable;

import dev.drav.glyphworks.fluid.protocol.FluidPacket;

public class FluidStack implements NetworkSerializable<FluidPacket> {

    @Nonnull
    public static final FluidStack[] EMPTY_ARRAY = new FluidStack[0];

    @Nonnull
    public static final BuilderCodec<FluidStack> CODEC = BuilderCodec
            .builder(FluidStack.class, FluidStack::new)
            .append(
                    new KeyedCodec<>("Glyphworks_FluidStack_Id", Codec.STRING),
                    (s, id) -> s.fluidId = id,
                    s -> s.fluidId)
            .addValidator(Validators.nonNull())
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_FluidStack_Amount", Codec.INTEGER),
                    (s, amt) -> s.amount = amt,
                    s -> s.amount)
            .addValidator(Validators.greaterThanOrEqual(0))
            .add()
            .build();

    private String fluidId;
    private int amount;

    FluidStack() {
    }

    public FluidStack(@Nonnull String fluidId, int amount) {
        if (fluidId == null)
            throw new IllegalArgumentException("fluidId cannot be null");
        if (amount < 0)
            throw new IllegalArgumentException("amount cannot be negative");
        this.fluidId = fluidId;
        this.amount = amount;
    }

    @Nonnull
    public String getFluidId() {
        return fluidId;
    }

    public int getAmount() {
        return amount;
    }

    public boolean isEmpty() {
        return amount <= 0;
    }

    public boolean isStackableWith(@Nullable FluidStack other) {
        return other != null && fluidId.equals(other.fluidId);
    }

    @Nullable
    public FluidStack withAmount(int amount) {
        if (amount <= 0)
            return null;
        return new FluidStack(fluidId, amount);
    }

    @Override
    public FluidPacket toPacket() {
        return new FluidPacket(fluidId, amount);
    }

    public static boolean isEmpty(@Nullable FluidStack stack) {
        return stack == null || stack.isEmpty();
    }

    @Override
    public String toString() {
        return "FluidStack{fluidId='" + fluidId + "', amount=" + amount + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof FluidStack other))
            return false;
        return amount == other.amount && fluidId.equals(other.fluidId);
    }

    @Override
    public int hashCode() {
        return 31 * fluidId.hashCode() + amount;
    }
}

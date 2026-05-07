package dev.drav.glyphworks.fluid.protocol;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.drav.glyphworks.fluid.FluidStack;

public class FluidPacket {

    @Nonnull
    public final String fluidId;

    public final int amount;

    public FluidPacket(@Nonnull String fluidId, int amount) {
        this.fluidId = fluidId;
        this.amount = amount;
    }

    @Nullable
    public static FluidPacket of(@Nullable FluidStack stack) {
        if (stack == null || stack.isEmpty())
            return null;
        return new FluidPacket(stack.getFluidId(), stack.getAmount());
    }
}

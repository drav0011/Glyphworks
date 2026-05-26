package dev.drav.glyphworks.fluid.util;

import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.ItemStack;

import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.event.FluidItemRegistry;

public final class FluidStackConversions {

    private FluidStackConversions() {
    }

    @Nullable
    public static FluidStack fromItemStack(@Nullable ItemStack stack) {
        if (stack == null)
            return null;
        String fluidId = FluidItemRegistry.resolveFluidId(stack.getItemId());
        if (fluidId == null)
            return null;
        return new FluidStack(fluidId, stack.getQuantity());
    }

    @Nullable
    public static ItemStack toItemStack(@Nullable FluidStack stack) {
        if (FluidStack.isEmpty(stack))
            return null;
        String itemId = FluidItemRegistry.resolveItemId(stack.getFluidId());
        if (itemId == null)
            return null;
        return new ItemStack(itemId, stack.getAmount());
    }
}

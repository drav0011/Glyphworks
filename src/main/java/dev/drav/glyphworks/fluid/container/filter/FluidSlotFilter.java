package dev.drav.glyphworks.fluid.container.filter;

import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;

import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.container.FluidContainer;

@FunctionalInterface
public interface FluidSlotFilter {

    FluidSlotFilter ALLOW = (actionType, container, slot, incoming, existing) -> true;

    FluidSlotFilter DENY = (actionType, container, slot, incoming, existing) -> false;

    boolean test(
            FilterActionType actionType,
            FluidContainer container,
            short slot,
            @Nullable FluidStack incoming,
            @Nullable FluidStack existing);
}

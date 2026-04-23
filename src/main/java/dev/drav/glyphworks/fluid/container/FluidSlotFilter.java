package dev.drav.glyphworks.fluid.container;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import dev.drav.glyphworks.fluid.FluidStack;

/**
 * Restricts a container slot to {@link FluidStack} items only.
 *
 * <p>
 * On ADD, the incoming item must be a {@code FluidStack} and must be stackable
 * with (i.e. the same fluid type as) any fluid already in the slot. REMOVE and
 * DROP are always allowed — the global {@code DENY_ALL} filter on fluid containers
 * prevents player interaction; internal operations that drain the slot pass
 * {@code filter = false}.
 */
public final class FluidSlotFilter implements SlotFilter {

    public static final FluidSlotFilter INSTANCE = new FluidSlotFilter();

    private FluidSlotFilter() {}

    @Override
    public boolean test(FilterActionType actionType, ItemContainer container, short slot, ItemStack itemStack) {
        if (actionType != FilterActionType.ADD) return true;
        if (!(itemStack instanceof FluidStack)) return false;

        ItemStack existing = container.getItemStack(slot);
        if (ItemStack.isEmpty(existing)) return true;

        return ((FluidStack) itemStack).isStackableWith(existing);
    }
}

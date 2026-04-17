package dev.drav.glyphworks.crafting.window;

import java.util.Collections;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.builtin.crafting.window.ProcessingBenchWindow;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.fluid.component.FluidContainerComponent;

/**
 * Processing-style bench window for grid-connected {@link ProcessingBenchBlock}
 * machines that run on liquid mana instead of item fuel.
 *
 * <p>
 * The fuel slot (slot 0) is driven by the block's {@link FluidContainerComponent}:
 * the fluid item is shown as the fuel icon and {@code fuelTime} tracks
 * {@code amount / capacity}. A change event on the fluid container keeps both
 * live without requiring a custom tick system — the vanilla
 * {@link ProcessingBenchBlock} tick still pushes {@code progress} and
 * {@code active} updates via the normal {@link BenchBlock} window registry.
 */
public final class AutoProcessingBenchWindow extends ProcessingBenchWindow {

    @Nonnull
    private final FluidContainerComponent fluidContainer;

    @Nonnull
    private final CombinedItemContainer itemContainerWithFluid;

    @Nullable
    private EventRegistration<?, ?> fluidRegistration;

    public AutoProcessingBenchWindow(
            @Nonnull ProcessingBenchBlock pbb,
            @Nonnull BenchBlock benchBlock,
            @Nonnull BlockModule.BlockStateInfo blockStateInfo,
            int x, int y, int z, int rotationIndex,
            @Nonnull BlockType blockType,
            @Nonnull FluidContainerComponent fluidContainer) {
        super(pbb, benchBlock, blockStateInfo, x, y, z, rotationIndex, blockType);
        this.fluidContainer = fluidContainer;
        this.itemContainerWithFluid = new CombinedItemContainer(
                fluidContainer.getItemContainer(),
                pbb.getInputContainer(),
                pbb.getOutputContainer());

        // Slot 0 is the fluid slot — override vanilla's "no fuel" defaults.
        setProcessingFuelSlots(Collections.singleton((short) 0));
        setMaxFuel(1);
        buildFluidFuelWindowData();
    }

    // ── ItemContainerWindow ────────────────────────────────────────────────────

    @Override
    @Nonnull
    public CombinedItemContainer getItemContainer() {
        return itemContainerWithFluid;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    protected boolean onOpen0(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!super.onOpen0(ref, store))
            return false;
        fluidRegistration = fluidContainer.getItemContainer().registerChangeEvent(event -> {
            buildFluidFuelWindowData();
        });
        return true;
    }

    @Override
    public void onClose0(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        super.onClose0(ref, componentAccessor);
        if (fluidRegistration != null) {
            fluidRegistration.unregister();
            fluidRegistration = null;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void buildFluidFuelWindowData() {
        JsonArray fuelArr = new JsonArray();
        JsonObject fuelSlot = new JsonObject();
        fuelSlot.addProperty("resourceTypeId", "");

        ItemStack stack = fluidContainer.getItemContainer().getItemStack((short) 0);
        fuelSlot.addProperty("icon", stack != null ? stack.getItemId() : "");
        fuelArr.add(fuelSlot);
        windowData.add("fuel", fuelArr);

        float fuelTime = fluidContainer.getCapacity() > 0
                ? (float) fluidContainer.getAmount() / fluidContainer.getCapacity()
                : 0.0f;
        setFuelTime(fuelTime);
    }
}

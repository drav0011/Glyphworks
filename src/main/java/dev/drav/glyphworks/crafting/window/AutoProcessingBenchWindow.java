package dev.drav.glyphworks.crafting.window;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.builtin.crafting.window.BenchWindow;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.window.TierUpgradeAction;
import com.hypixel.hytale.protocol.packets.window.WindowAction;
import com.hypixel.hytale.protocol.packets.window.WindowType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.ProcessingBench;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ItemContainerWindow;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.crafting.component.AutoProcessingBenchBlock;
import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.container.FluidContainer;

/**
 * Processing window for {@link AutoProcessingBenchBlock} machines running on
 * liquid mana.
 *
 * <p>
 * Slot order is fluid-input, item-input, item-output, fluid-output.
 *
 * <p>
 * Progress is pushed each tick by
 * {@link dev.drav.glyphworks.crafting.system.AutoProcessingBenchSystem} via
 * {@link #setProgress(float)}.
 */
public final class AutoProcessingBenchWindow extends BenchWindow implements ItemContainerWindow {

    @Nonnull
    private final AutoProcessingBenchBlock apbb;

    @Nonnull
    private final BlockModule.BlockStateInfo blockStateInfo;

    @Nonnull
    private final CombinedItemContainer itemContainer;

    @Nullable
    private final FluidContainerComponent fluidContainer;

    @Nullable
    private EventRegistration<?, ?> inventoryRegistration;

    @Nullable
    private EventRegistration<?, ?> inputRegistration;

    @Nullable
    private EventRegistration<?, ?> outputRegistration;

    private float progress;

    public AutoProcessingBenchWindow(
            @Nonnull AutoProcessingBenchBlock apbb,
            @Nonnull BenchBlock benchBlock,
            @Nonnull BlockModule.BlockStateInfo blockStateInfo,
            int x, int y, int z, int rotationIndex,
            @Nonnull BlockType blockType,
            @Nullable FluidContainerComponent fluidContainer) {
        super(WindowType.Processing, x, y, z, rotationIndex, blockType, benchBlock);
        this.apbb = apbb;
        this.blockStateInfo = blockStateInfo;
        this.fluidContainer = fluidContainer;

        List<ItemContainer> containers = new ArrayList<>();
        FluidContainer fluidFuelCopy = buildDeniedFluidCopy(apbb.getFluidFuelContainer());
        if (fluidFuelCopy != null) {
            containers.add(fluidFuelCopy);
        }

        ItemContainer itemFuel = apbb.getItemFuelContainer();
        if (itemFuel != null) {
            containers.add(itemFuel);
        }

        FluidContainer fluidInputCopy = buildDeniedFluidCopy(apbb.getFluidInputContainer());
        if (fluidInputCopy != null) {
            containers.add(fluidInputCopy);
        }

        ItemContainer itemInput = apbb.getItemInputContainer();
        if (itemInput != null) {
            containers.add(itemInput);
        }

        ItemContainer itemOutput = apbb.getItemOutputContainer();
        if (itemOutput != null) {
            containers.add(itemOutput);
        }

        FluidContainer fluidOutputCopy = buildDeniedFluidCopy(apbb.getFluidOutputContainer());
        if (fluidOutputCopy != null) {
            containers.add(fluidOutputCopy);
        }

        this.itemContainer = new CombinedItemContainer(containers.toArray(ItemContainer[]::new));

        this.progress = apbb.getInputProgress();

        windowData.addProperty("active", Boolean.TRUE);
        windowData.addProperty("progress", Float.valueOf(this.progress));
        windowData.addProperty("maxFuel", Integer.valueOf(1));
        windowData.addProperty("fuelTime", Float.valueOf(1.0f));
        windowData.addProperty("processingSlots", Integer.valueOf(0));
        windowData.addProperty("processingFuelSlots",
                Integer.valueOf(apbb.getFuel() != null ? apbb.getFuel().getCapacity() : 0));

        buildFuelWindowData();
        buildInputWindowData(blockType, benchBlock.getTierLevel());
        buildOutputWindowData(blockType, benchBlock.getTierLevel());
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    protected boolean onOpen0(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!super.onOpen0(ref, store))
            return false;

        Player playerComponent = (Player) store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null)
            return false;

        windowData.add("inventoryHints", new JsonArray());
        inventoryRegistration = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST)
                .registerChangeEvent(event -> {
                    windowData.add("inventoryHints", new JsonArray());
                    invalidate();
                });

        ItemContainer fuel = apbb.getFuel();
        if (fuel != null) {
            inputRegistration = fuel.registerChangeEvent(event -> {
                buildFuelWindowData();
                invalidate();
            });
        }

        ItemContainer output = apbb.getOutput();
        if (output != null) {
            outputRegistration = output.registerChangeEvent(event -> {
                buildFuelWindowData();
                invalidate();
            });
        }

        return true;
    }

    @Override
    public void onClose0(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        super.onClose0(ref, componentAccessor);
        if (inventoryRegistration != null) {
            inventoryRegistration.unregister();
            inventoryRegistration = null;
        }
        if (inputRegistration != null) {
            inputRegistration.unregister();
            inputRegistration = null;
        }
        if (outputRegistration != null) {
            outputRegistration.unregister();
            outputRegistration = null;
        }
    }

    // ── ItemContainerWindow ────────────────────────────────────────────────────

    @Override
    @Nonnull
    public CombinedItemContainer getItemContainer() {
        return itemContainer;
    }

    // ── Progress ───────────────────────────────────────────────────────────────

    public void setProgress(float progress) {
        if (this.progress == progress)
            return;
        this.progress = progress;
        windowData.addProperty("progress", Float.valueOf(progress));
        invalidate();
    }

    // ── Window actions ─────────────────────────────────────────────────────────

    @Override
    public void handleAction(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull WindowAction action) {
        if (!(action instanceof TierUpgradeAction))
            return;
        CraftingManager craftingManager = (CraftingManager) store.getComponent(ref,
                CraftingManager.getComponentType());
        if (craftingManager != null && craftingManager.startTierUpgrade(ref, store, this)) {
            if (bench.getBenchUpgradeSoundEventIndex() != 0) {
                SoundUtil.playSoundEvent2d(ref, bench.getBenchUpgradeSoundEventIndex(), SoundCategory.UI, store);
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private void buildFuelWindowData() {
        JsonArray fuelArr = new JsonArray();
        float fuelTime = 1.0f;

        ItemContainer fuel = apbb.getFuel();
        if (fuel == null) {
            windowData.addProperty("fuelTime", Float.valueOf(fuelTime));
            windowData.add("fuel", fuelArr);
            return;
        }

        if (fuel instanceof FluidContainer fc) {
            if (fc.getCapacity() > 0) {
                int totalMb = 0;
                for (short i = 0; i < fc.getCapacity(); i++) {
                    FluidStack s = fc.getFluidStack(i);
                    if (s != null)
                        totalMb += s.getQuantity();
                }
                int maxMb = (int) fc.getCapacity() * fc.getCapacityMbPerSlot();
                fuelTime = maxMb > 0 ? (float) totalMb / maxMb : 0.0f;

                for (short i = 0; i < fc.getCapacity(); i++) {
                    FluidStack stack = fc.getFluidStack(i);
                    JsonObject fuelSlot = new JsonObject();
                    fuelSlot.addProperty("resourceTypeId", "");
                    fuelSlot.addProperty("icon", stack != null ? stack.getFluidId() : "");
                    fuelArr.add(fuelSlot);
                }
            }
        } else {
            short capacity = fuel.getCapacity();
            for (short i = 0; i < capacity; i++) {
                ItemStack stack = fuel.getItemStack(i);
                JsonObject fuelSlot = new JsonObject();
                fuelSlot.addProperty("resourceTypeId", "");
                fuelSlot.addProperty("icon", stack != null ? stack.getItemId() : "");
                fuelArr.add(fuelSlot);
            }
        }

        windowData.addProperty("fuelTime", Float.valueOf(fuelTime));
        windowData.add("fuel", fuelArr);
    }

    private void buildInputWindowData(@Nonnull BlockType blockType, int tierLevel) {
        if (!(blockType.getBench() instanceof ProcessingBench pb))
            return;
        ProcessingBench.ProcessingSlot[] inputs = pb.getInput(tierLevel);
        if (inputs == null || inputs.length == 0)
            return;
        JsonArray inputArr = new JsonArray();
        for (ProcessingBench.ProcessingSlot slot : inputs) {
            if (slot == null)
                continue;
            JsonObject slotObj = new JsonObject();
            slotObj.addProperty("icon", slot.getIcon());
            inputArr.add(slotObj);
        }
        windowData.add("input", inputArr);
    }

    private void buildOutputWindowData(@Nonnull BlockType blockType, int tierLevel) {
        ItemContainer output = apbb.getOutput();
        int outputSlotsCount = output != null ? output.getCapacity() : 0;
        windowData.addProperty("outputSlotsCount", Integer.valueOf(outputSlotsCount));
    }

    @Nullable
    private static FluidContainer buildDeniedFluidCopy(@Nullable FluidContainer source) {
        if (source == null) {
            return null;
        }

        FluidContainer copy = source.clone();
        copy.setGlobalFilter(FilterType.DENY_ALL);
        return copy;
    }
}

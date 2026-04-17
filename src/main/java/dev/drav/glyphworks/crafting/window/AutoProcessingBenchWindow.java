package dev.drav.glyphworks.crafting.window;

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
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.crafting.component.AutoProcessingBenchBlock;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;

/**
 * Processing window for {@link AutoProcessingBenchBlock} machines running on
 * liquid mana.
 *
 * <p>
 * Slot 0 is the mana fuel slot (read-only view of the fluid container).
 * Slots 1..N are input slots. The remaining slots are output slots.
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
    private EventRegistration<?, ?> fluidRegistration;

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

        ItemContainer fuelSlotContainer = fluidContainer != null
                ? fluidContainer.getItemContainer()
                : buildDummyFuelContainer();
        ItemContainer input = apbb.getInputContainer();
        ItemContainer output = apbb.getOutputContainer();
        this.itemContainer = (input != null && output != null)
                ? new CombinedItemContainer(fuelSlotContainer, input, output)
                : new CombinedItemContainer(fuelSlotContainer);

        this.progress = apbb.getCraftingProgress();

        windowData.addProperty("active", Boolean.TRUE);
        windowData.addProperty("progress", Float.valueOf(this.progress));
        windowData.addProperty("maxFuel", Integer.valueOf(1));
        windowData.addProperty("fuelTime", Float.valueOf(1.0f));
        windowData.addProperty("processingSlots", Integer.valueOf(0));
        windowData.addProperty("processingFuelSlots", Integer.valueOf(1));

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

        Inventory inventory = playerComponent.getInventory();
        windowData.add("inventoryHints", new JsonArray());
        inventoryRegistration = inventory.getCombinedHotbarFirst().registerChangeEvent(event -> {
            windowData.add("inventoryHints", new JsonArray());
            invalidate();
        });

        if (fluidContainer != null) {
            fluidRegistration = fluidContainer.getItemContainer().registerChangeEvent(event -> {
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
        if (fluidRegistration != null) {
            fluidRegistration.unregister();
            fluidRegistration = null;
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
        JsonObject fuelSlot = new JsonObject();
        fuelSlot.addProperty("resourceTypeId", "");

        if (fluidContainer != null) {
            ItemStack stack = fluidContainer.getItemContainer().getItemStack((short) 0);
            fuelSlot.addProperty("icon", stack != null ? stack.getItemId() : "");

            float fuelTime = fluidContainer.getCapacity() > 0
                    ? (float) fluidContainer.getAmount() / fluidContainer.getCapacity()
                    : 0.0f;
            windowData.addProperty("fuelTime", Float.valueOf(fuelTime));
        } else {
            fuelSlot.addProperty("icon", "");
        }

        fuelArr.add(fuelSlot);
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
        if (!(blockType.getBench() instanceof ProcessingBench pb))
            return;
        windowData.addProperty("outputSlotsCount", Integer.valueOf(pb.getOutputSlotsCount(tierLevel)));
    }

    private static ItemContainer buildDummyFuelContainer() {
        ItemContainer dummy = SimpleItemContainer.getNewContainer((short) 1);
        dummy.setGlobalFilter(FilterType.ALLOW_OUTPUT_ONLY);
        return dummy;
    }
}

package dev.drav.glyphworks.crafting.window;

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
import com.hypixel.hytale.protocol.packets.window.SetActiveAction;
import com.hypixel.hytale.protocol.packets.window.TierUpgradeAction;
import com.hypixel.hytale.protocol.packets.window.WindowAction;
import com.hypixel.hytale.protocol.packets.window.WindowType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ItemContainerWindow;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.crafting.component.AutoCraftingBenchBlock;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;

/**
 * Processing-style monitor window for {@link AutoCraftingBenchBlock}.
 *
 * <p>
 * Displays the bench's combined input + output container and a live progress
 * bar. The "active" toggle is repurposed: sending {@link SetActiveAction}{@code
 * (false)} acts as a "Change Recipe" button — it clears the locked recipe,
 * ejects
 * all input items, and closes the window so the player can pick a new recipe.
 *
 * <p>
 * Progress is pushed each server tick by
 * {@link dev.drav.glyphworks.crafting.system.AutoCraftingBenchSystem} via
 * {@link #setProgress(float)}.
 */
public final class AutoCraftingBenchMonitorWindow extends BenchWindow implements ItemContainerWindow {

    @Nonnull
    private final AutoCraftingBenchBlock acbb;

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

    public AutoCraftingBenchMonitorWindow(
            @Nonnull AutoCraftingBenchBlock acbb,
            @Nonnull BenchBlock benchBlock,
            @Nonnull BlockModule.BlockStateInfo blockStateInfo,
            int x, int y, int z, int rotationIndex,
            @Nonnull BlockType blockType,
            @Nullable FluidContainerComponent fluidContainer) {
        super(WindowType.Processing, x, y, z, rotationIndex, blockType, benchBlock);
        this.acbb = acbb;
        this.blockStateInfo = blockStateInfo;
        this.fluidContainer = fluidContainer;

        // Build slot layout: fuel | input | output.
        // Fuel slot is either the live fluid container or a locked-empty dummy.
        ItemContainer fuelSlotContainer = fluidContainer != null
                ? fluidContainer.getItemContainer()
                : buildDummyFuelContainer();
        ItemContainer input = acbb.getInputContainer();
        ItemContainer output = acbb.getOutputContainer();
        this.itemContainer = (input != null && output != null)
                ? new CombinedItemContainer(fuelSlotContainer, input, output)
                : new CombinedItemContainer(fuelSlotContainer);

        this.progress = computeProgress(acbb);

        // Fields expected by the Processing client renderer.
        windowData.addProperty("active", Boolean.TRUE);
        windowData.addProperty("progress", Float.valueOf(this.progress));
        windowData.addProperty("maxFuel", Integer.valueOf(1));
        windowData.addProperty("fuelTime", Float.valueOf(1.0f));
        windowData.addProperty("processingSlots", Integer.valueOf(0));
        windowData.addProperty("processingFuelSlots", Integer.valueOf(1)); // slot 0 active
        buildFuelWindowData();

        String lockedId = acbb.getLockedRecipeId();
        if (lockedId != null) {
            windowData.addProperty("lockedRecipeId", lockedId);
        }

        // Build input slot definitions from the locked recipe's ingredient list.
        // Each slot needs at minimum an "icon" key for the Processing renderer.
        JsonArray inputArr = new JsonArray();
        CraftingRecipe locked = acbb.getLockedRecipe();
        if (locked != null) {
            List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(locked);
            for (MaterialQuantity mat : inputs) {
                JsonObject slot = new JsonObject();
                slot.addProperty("icon", mat.getItemId() != null ? mat.getItemId()
                        : (mat.getResourceTypeId() != null ? mat.getResourceTypeId() : ""));
                inputArr.add(slot);
            }
        }
        windowData.add("input", inputArr);
        windowData.addProperty("outputSlotsCount", Integer.valueOf(4)); // matches AutoCraftingBenchBlock.OUTPUT_SLOTS
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
        inventoryRegistration = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST).registerChangeEvent(event -> {
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

    /**
     * Called each tick by
     * {@link dev.drav.glyphworks.crafting.system.AutoCraftingBenchSystem}.
     */
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
        if (action instanceof TierUpgradeAction) {
            CraftingManager craftingManager = (CraftingManager) store.getComponent(ref,
                    CraftingManager.getComponentType());
            if (craftingManager != null && craftingManager.startTierUpgrade(ref, store, this)) {
                World world = store.getExternalData().getWorld();
                setBlockInteractionState(BENCH_UPGRADING, world);
                if (bench.getBenchUpgradeSoundEventIndex() != 0) {
                    SoundUtil.playSoundEvent2d(ref, bench.getBenchUpgradeSoundEventIndex(), SoundCategory.UI, store);
                }
            }
            return;
        }
        if (!(action instanceof SetActiveAction))
            return;
        SetActiveAction setActive = (SetActiveAction) action;
        if (setActive.state)
            return; // "activate" — bench always auto-runs; ignore

        World world = store.getExternalData().getWorld();
        acbb.setLockedRecipe(null, blockStateInfo, world, x, y, z, blockType, rotationIndex);

        windowData.addProperty("active", Boolean.FALSE);
        invalidate(); // push active=false to the client before we close

        if (bench.getFailedSoundEventIndex() != 0) {
            SoundUtil.playSoundEvent2d(ref, bench.getFailedSoundEventIndex(), SoundCategory.UI, store);
        }

        this.close(ref, store);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static ItemContainer buildDummyFuelContainer() {
        ItemContainer dummy = SimpleItemContainer.getNewContainer((short) 1);
        dummy.setGlobalFilter(FilterType.ALLOW_OUTPUT_ONLY);
        return dummy;
    }

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
            windowData.addProperty("fuelTime", Float.valueOf(1.0f));
        }

        fuelArr.add(fuelSlot);
        windowData.add("fuel", fuelArr);
    }

    private static float computeProgress(@Nonnull AutoCraftingBenchBlock acbb) {
        var recipe = acbb.getLockedRecipe();
        if (recipe == null || recipe.getTimeSeconds() <= 0.0f)
            return 0.0f;
        return Math.min(acbb.getCraftingProgress() / recipe.getTimeSeconds(), 1.0f);
    }
}

package dev.drav.glyphworks.crafting.component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.ProcessingBench;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.crafting.system.AutoProcessingBenchSystems;
import dev.drav.glyphworks.crafting.util.FluidRecipeUtil;
import dev.drav.glyphworks.crafting.window.AutoProcessingBenchWindow;
import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.container.FluidContainer;
import dev.drav.glyphworks.fluid.event.FluidItemRegistry;

/**
 * Full state component for mana-powered automated processing benches.
 *
 * <p>
 * Owns all processing state: input / output containers, crafting progress, and
 * the currently matched recipe. The tick logic lives in
 * {@link dev.drav.glyphworks.crafting.system.AutoProcessingBenchSystem}.
 */
public final class AutoProcessingBenchBlock implements Component<ChunkStore> {

    @Nonnull
    public static final BuilderCodec<AutoProcessingBenchBlock> CODEC = BuilderCodec
            .builder(AutoProcessingBenchBlock.class, AutoProcessingBenchBlock::new)
            .append(
                    new KeyedCodec<>("Glyphworks_AutoProcessingBenchBlock_FuelPolicy",
                            new EnumCodec<>(FuelPolicy.class)),
                    (b, v) -> b.fuelPolicy = v,
                    b -> b.fuelPolicy)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_AutoProcessingBenchBlock_FluidFuelSlots",
                            new ArrayCodec<>(FluidSlot.CODEC, FluidSlot[]::new)),
                    (b, v) -> b.fluidFuelSlots = v,
                    b -> b.fluidFuelSlots)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_AutoProcessingBenchBlock_FluidInputSlots",
                            new ArrayCodec<>(FluidSlot.CODEC, FluidSlot[]::new)),
                    (b, v) -> b.fluidInputSlots = v,
                    b -> b.fluidInputSlots)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_AutoProcessingBenchBlock_FluidOutputSlots",
                            new ArrayCodec<>(FluidSlot.CODEC, FluidSlot[]::new)),
                    (b, v) -> b.fluidOutputSlots = v,
                    b -> b.fluidOutputSlots)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_AutoProcessingBenchBlock_FluidFuelContainer", FluidContainer.CODEC),
                    (b, v) -> b.fluidFuelContainer = v,
                    b -> b.fluidFuelContainer)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_AutoProcessingBenchBlock_ItemFuelContainer", ItemContainer.CODEC),
                    (b, v) -> b.itemFuelContainer = v,
                    b -> b.itemFuelContainer)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_AutoProcessingBenchBlock_FluidInputContainer", FluidContainer.CODEC),
                    (b, v) -> b.fluidInputContainer = v,
                    b -> b.fluidInputContainer)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_AutoProcessingBenchBlock_ItemInputContainer", ItemContainer.CODEC),
                    (b, v) -> b.itemInputContainer = v,
                    b -> b.itemInputContainer)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_AutoProcessingBenchBlock_FluidOutputContainer", FluidContainer.CODEC),
                    (b, v) -> b.fluidOutputContainer = v,
                    b -> b.fluidOutputContainer)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_AutoProcessingBenchBlock_ItemOutputContainer", ItemContainer.CODEC),
                    (b, v) -> b.itemOutputContainer = v,
                    b -> b.itemOutputContainer)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_AutoProcessingBenchBlock_Progress", Codec.DOUBLE),
                    (b, v) -> b.inputProgress = v.floatValue(),
                    b -> Double.valueOf(b.inputProgress))
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_AutoProcessingBenchBlock_FuelTime", Codec.DOUBLE),
                    (b, v) -> b.fuelTime = v.floatValue(),
                    b -> Double.valueOf(b.fuelTime))
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_AutoProcessingBenchBlock_RecipeId", Codec.STRING),
                    (b, v) -> b.recipeId = v,
                    b -> b.recipeId)
            .add()
            .build();

    // ── Serialised fields ──────────────────────────────────────────────────────

    @Nonnull
    private FuelPolicy fuelPolicy = FuelPolicy.ANY;
    @Nonnull
    private FluidSlot[] fluidFuelSlots = new FluidSlot[0];
    @Nonnull
    private FluidSlot[] fluidInputSlots = new FluidSlot[0];
    @Nonnull
    private FluidSlot[] fluidOutputSlots = new FluidSlot[0];
    @Nullable
    private FluidContainer fluidFuelContainer;
    @Nullable
    private ItemContainer itemFuelContainer;
    @Nullable
    private FluidContainer fluidInputContainer;
    @Nullable
    private ItemContainer itemInputContainer;
    @Nullable
    private FluidContainer fluidOutputContainer;
    @Nullable
    private ItemContainer itemOutputContainer;
    private float inputProgress = 0.0f;
    private float fuelTime = 0.0f;
    @Nullable
    private String recipeId;

    // ── Transient / runtime ────────────────────────────────────────────────────

    @Nullable
    private transient ProcessingBench processingBench;
    @Nullable
    private transient CombinedItemContainer itemContainer;
    @Nullable
    private transient CombinedItemContainer windowContainer;
    private transient boolean active = false;

    @Nonnull
    private transient Map<UUID, AutoProcessingBenchWindow> windows = new ConcurrentHashMap<>();

    public AutoProcessingBenchBlock() {
    }

    public AutoProcessingBenchBlock(@Nonnull AutoProcessingBenchBlock other) {
        this.fuelPolicy = other.fuelPolicy;
        this.fluidFuelSlots = Arrays.copyOf(other.fluidFuelSlots, other.fluidFuelSlots.length);
        this.fluidInputSlots = Arrays.copyOf(other.fluidInputSlots, other.fluidInputSlots.length);
        this.fluidOutputSlots = Arrays.copyOf(other.fluidOutputSlots, other.fluidOutputSlots.length);
        this.itemFuelContainer = other.itemFuelContainer;
        this.itemInputContainer = other.itemInputContainer;
        this.fluidFuelContainer = other.fluidFuelContainer;
        this.fluidInputContainer = other.fluidInputContainer;
        this.fluidOutputContainer = other.fluidOutputContainer;
        this.itemOutputContainer = other.itemOutputContainer;
        this.inputProgress = other.inputProgress;
        this.fuelTime = other.fuelTime;
        this.recipeId = other.recipeId;
    }

    public static ComponentType<ChunkStore, AutoProcessingBenchBlock> getComponentType() {
        return GlyphworksPlugin.get().getCraftingModule().getAutoProcessingBenchBlockComponentType();
    }

    // ── Container setup ────────────────────────────────────────────────────────

    public boolean initializeBenchConfig(@Nonnull BlockType blockType) {
        if (!(blockType.getBench() instanceof ProcessingBench pb)) {
            return false;
        }
        this.processingBench = pb;
        return true;
    }

    public void setupSlots(
            @Nonnull World world,
            @Nonnull BenchBlock benchBlock,
            @Nonnull BlockModule.BlockStateInfo blockStateInfo,
            int blockX, int blockY, int blockZ,
            @Nonnull BlockType blockType,
            int rotationIndex) {
        AutoProcessingBenchSystems.initializeBenchSlots(
                this,
                world,
                benchBlock,
                blockStateInfo,
                blockX,
                blockY,
                blockZ,
                blockType,
                rotationIndex);
    }

    public void applyInputFilters(@Nonnull ProcessingBench pb, int tierLevel) {
        List<CraftingRecipe> recipes = CraftingPlugin.getBenchRecipes(pb);
        List<MaterialQuantity> validIngredients = new ArrayList<>();
        for (CraftingRecipe recipe : recipes) {
            if (!recipe.isRestrictedByBenchTierLevel(pb.getId(), tierLevel)) {
                for (MaterialQuantity mat : CraftingManager.getInputMaterials(recipe)) {
                    if (!FluidRecipeUtil.isFluidIngredient(mat))
                        validIngredients.add(mat);
                }
            }
        }
        short cap = itemInputContainer.getCapacity();
        for (short i = 0; i < cap; i++) {
            itemInputContainer.setSlotFilter(FilterActionType.ADD, i, (actionType, container, slotIndex, stack) -> {
                if (stack == null)
                    return true;
                for (MaterialQuantity mat : validIngredients) {
                    if (CraftingManager.matches(mat, stack))
                        return true;
                }
                return false;
            });
        }
    }

    /**
     * Applies selector-owned recipe slot layout to this runtime component.
     *
     * <p>
     * Used by {@link AutoCraftingBenchBlock} so recipe selection and slot
     * filters live in the selector component while container ownership stays
     * exclusively in the processing component.
     */
    public void applyExternalRecipeLayout(
            @Nullable CraftingRecipe selectedRecipe,
            @Nonnull BlockModule.BlockStateInfo blockStateInfo,
            @Nonnull World world,
            int blockX,
            int blockY,
            int blockZ) {
        AutoProcessingBenchSystems.applyExternalRecipeLayout(
                this,
                selectedRecipe,
                blockStateInfo,
                world,
                blockX,
                blockY,
                blockZ);
    }

    // ── Recipe detection ───────────────────────────────────────────────────────

    public void updateRecipe(@Nonnull BenchBlock benchBlock) {
        CraftingRecipe recipe = findMatchingRecipe(benchBlock.getTierLevel());
        recipeId = recipe != null ? recipe.getId() : null;
    }

    @Nullable
    public CraftingRecipe resolveCurrentRecipe(@Nonnull BenchBlock benchBlock) {
        CraftingRecipe recipe = getRecipe();

        if (recipe != null && isReadyToCraft(recipe)) {
            return recipe;
        }

        updateRecipe(benchBlock);
        return getRecipe();
    }

    @Nullable
    private CraftingRecipe findMatchingRecipe(int tierLevel) {
        if (processingBench == null)
            return null;

        List<CraftingRecipe> recipes = CraftingPlugin.getBenchRecipes(processingBench);
        if (recipes.isEmpty())
            return null;

        CraftingRecipe best = null;
        int bestInputCount = -1;

        for (CraftingRecipe recipe : recipes) {
            if (recipe.isRestrictedByBenchTierLevel(processingBench.getId(), tierLevel))
                continue;
            List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(recipe);
            if (inputs.isEmpty())
                continue;
            List<MaterialQuantity> itemInputs = FluidRecipeUtil.itemParts(inputs);
            boolean itemsReady = itemInputs.isEmpty()
                    || (itemInputContainer != null
                            && !itemInputContainer.getSlotMaterialsToRemove(itemInputs, true, true).isEmpty());
            if (itemsReady && hasEnoughFluidInputs(recipe)) {
                if (inputs.size() > bestInputCount) {
                    bestInputCount = inputs.size();
                    best = recipe;
                }
            }
        }

        return best;
    }

    // ── Crafting checks ────────────────────────────────────────────────────────

    public boolean isReadyToCraft(@Nonnull CraftingRecipe recipe) {
        List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(recipe);
        if (inputs.isEmpty())
            return false;
        List<MaterialQuantity> itemInputs = FluidRecipeUtil.itemParts(inputs);
        boolean itemsReady = itemInputs.isEmpty()
                || (itemInputContainer != null
                        && !itemInputContainer.getSlotMaterialsToRemove(itemInputs, true, true).isEmpty());
        return itemsReady && hasEnoughFluidInputs(recipe);
    }

    private boolean hasEnoughFluidInputs(
            @Nonnull CraftingRecipe recipe) {
        List<MaterialQuantity> fluidInputs = FluidRecipeUtil.fluidParts(CraftingManager.getInputMaterials(recipe));
        if (fluidInputs.isEmpty())
            return true;
        if (fluidInputContainer == null || fluidInputContainer.getCapacity() == 0)
            return false;
        for (MaterialQuantity required : fluidInputs) {
            String fluidId = FluidRecipeUtil.fluidId(required);
            if (fluidId == null)
                return false;
            int totalMb = 0;
            for (short i = 0; i < fluidInputContainer.getCapacity(); i++) {
                FluidStack s = fluidInputContainer.getFluidStack(i);
                if (s != null && fluidId.equals(s.getFluidId()))
                    totalMb += s.getAmount();
            }
            if (totalMb < FluidRecipeUtil.fluidMb(required))
                return false;
        }
        return true;
    }

    public boolean isFuelFluid(@Nonnull FluidStack fluidStack,
            @Nonnull String requiredResourceTypeId) {
        String itemId = FluidItemRegistry.resolveItemId(fluidStack.getFluidId());
        Item item = itemId != null ? (Item) Item.getAssetMap().getAsset(itemId) : null;
        return item != null
                && item.getFuelQuality() > 0.0
                && hasResourceType(item, requiredResourceTypeId);
    }

    public boolean hasResourceType(@Nonnull Item item, @Nonnull String resourceTypeId) {
        ItemResourceType[] resourceTypes = item.getResourceTypes();
        if (resourceTypes == null || resourceTypes.length == 0) {
            return false;
        }

        for (ItemResourceType resourceType : resourceTypes) {
            if (resourceType != null && resourceTypeId.equals(resourceType.id)) {
                return true;
            }
        }

        return false;
    }

    @Nonnull
    public String getFuelSlotResourceTypeId(short slotIndex, @Nonnull String fallbackResourceTypeId) {
        if (processingBench == null || processingBench.getFuel() == null || slotIndex < 0
                || slotIndex >= processingBench.getFuel().length) {
            return fallbackResourceTypeId;
        }

        String configuredResourceTypeId = processingBench.getFuel()[slotIndex].getResourceTypeId();
        return configuredResourceTypeId != null && !configuredResourceTypeId.isBlank()
                ? configuredResourceTypeId
                : fallbackResourceTypeId;
    }

    @Nonnull
    public String getConfiguredFluidFuelSlotResourceTypeId(short slotIndex, @Nonnull String fallbackResourceTypeId) {
        if (slotIndex < 0 || slotIndex >= fluidFuelSlots.length) {
            return fallbackResourceTypeId;
        }
        FluidSlot slot = fluidFuelSlots[slotIndex];
        if (slot == null) {
            return fallbackResourceTypeId;
        }
        String configuredResourceTypeId = slot.getResourceTypeId();
        return configuredResourceTypeId != null && !configuredResourceTypeId.isBlank()
                ? configuredResourceTypeId
                : fallbackResourceTypeId;
    }

    @Nullable
    public String getConfiguredFluidInputSlotResourceTypeId(short slotIndex) {
        if (slotIndex < 0 || slotIndex >= fluidInputSlots.length) {
            return null;
        }
        FluidSlot slot = fluidInputSlots[slotIndex];
        return slot != null ? slot.getResourceTypeId() : null;
    }

    public int getConfiguredFluidFuelSlotCapacityMb(short slotIndex) {
        return getConfiguredSlotCapacityMb(fluidFuelSlots, slotIndex);
    }

    public int getConfiguredFluidInputSlotCapacityMb(short slotIndex) {
        return getConfiguredSlotCapacityMb(fluidInputSlots, slotIndex);
    }

    public int getConfiguredFluidOutputSlotCapacityMb(short slotIndex) {
        return getConfiguredSlotCapacityMb(fluidOutputSlots, slotIndex);
    }

    private int getConfiguredSlotCapacityMb(@Nonnull FluidSlot[] slots, short slotIndex) {
        validateSlotIndex(slots, slotIndex);
        FluidSlot slot = slots[slotIndex];
        if (slot == null) {
            throw new IllegalStateException("Missing fluid slot configuration at index " + slotIndex);
        }
        int capacity = slot.getCapacityMbPerSlot();
        if (capacity <= 0) {
            throw new IllegalStateException("Invalid fluid slot capacity at index " + slotIndex + ": " + capacity);
        }
        return capacity;
    }

    private int getMaxSlotCapacityMb(@Nonnull FluidSlot[] slots) {
        int max = 0;
        for (short i = 0; i < slots.length; i++) {
            max = Math.max(max, getConfiguredSlotCapacityMb(slots, i));
        }
        if (max <= 0) {
            throw new IllegalStateException("No configured fluid slot capacities were found");
        }
        return max;
    }

    private void validateSlotIndex(@Nonnull FluidSlot[] slots, short slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.length) {
            throw new IndexOutOfBoundsException(
                    "Fluid slot index " + slotIndex + " is out of bounds for length " + slots.length);
        }
    }

    public int getMaxFluidFuelSlotCapacityMb() {
        return getMaxSlotCapacityMb(fluidFuelSlots);
    }

    public int getMaxFluidInputSlotCapacityMb() {
        return getMaxSlotCapacityMb(fluidInputSlots);
    }

    public int getMaxFluidOutputSlotCapacityMb() {
        return getMaxSlotCapacityMb(fluidOutputSlots);
    }

    public short getConfiguredFluidFuelSlotsCount() {
        return (short) Math.max(0, fluidFuelSlots.length);
    }

    public short getConfiguredFluidInputSlotsCount() {
        return (short) Math.max(0, fluidInputSlots.length);
    }

    public short getConfiguredFluidOutputSlotsCount() {
        return (short) Math.max(0, fluidOutputSlots.length);
    }

    public int getExistingFluidAmount(@Nonnull ItemContainer container, short slotIndex, @Nonnull String fluidId) {
        if (!(container instanceof FluidContainer fluidContainer)) {
            return 0;
        }
        FluidStack existing = fluidContainer.getFluidStack(slotIndex);
        if (existing == null || !fluidId.equals(existing.getFluidId())) {
            return 0;
        }
        return existing.getAmount();
    }

    @Nullable
    public static CombinedItemContainer buildNullableCombined(@Nullable ItemContainer... containers) {
        List<ItemContainer> present = new ArrayList<>(containers.length);
        for (ItemContainer container : containers) {
            if (container != null) {
                present.add(container);
            }
        }
        if (present.isEmpty()) {
            return null;
        }
        return new CombinedItemContainer(present.toArray(ItemContainer[]::new));
    }

    public static void ejectAndClearContainer(@Nullable ItemContainer container, @Nonnull List<ItemStack> ejected) {
        if (container == null) {
            return;
        }
        if (container instanceof FluidContainer) {
            container.clear();
            return;
        }
        ejected.addAll(container.dropAllItemStacks());
    }

    // ── Progress / windows ─────────────────────────────────────────────────────

    public void sendProgress(float normalised) {
        if (!windows.isEmpty()) {
            windows.values().forEach(w -> w.setProgress(normalised));
        }
    }

    // ── Getters / setters ──────────────────────────────────────────────────────

    @Nonnull
    public FuelPolicy getFuelPolicy() {
        return fuelPolicy;
    }

    public float getFuelTime() {
        return fuelTime;
    }

    public void setFuelTime(float fuelTime) {
        this.fuelTime = fuelTime;
    }

    @Nullable
    public FluidContainer getFluidFuelContainer() {
        return fluidFuelContainer;
    }

    @Nullable
    public ItemContainer getItemFuelContainer() {
        return itemFuelContainer;
    }

    public void setItemFuelContainer(@Nullable ItemContainer itemFuelContainer) {
        this.itemFuelContainer = itemFuelContainer;
    }

    @Nullable
    public ItemContainer getFuelContainer() {
        return itemFuelContainer;
    }

    public int getFluidInputSlotsCount() {
        return fluidInputContainer != null ? fluidInputContainer.getCapacity() : 0;
    }

    public int getFluidOutputSlotsCount() {
        return fluidOutputContainer != null ? fluidOutputContainer.getCapacity() : 0;
    }

    @Nullable
    public FluidContainer getFluidInputContainer() {
        return fluidInputContainer;
    }

    public void setFluidInputContainer(@Nullable FluidContainer fluidInputContainer) {
        this.fluidInputContainer = fluidInputContainer;
    }

    @Nullable
    public ItemContainer getItemInputContainer() {
        return itemInputContainer;
    }

    public void setItemInputContainer(@Nullable ItemContainer itemInputContainer) {
        this.itemInputContainer = itemInputContainer;
    }

    @Nullable
    public FluidContainer getFluidOutputContainer() {
        return fluidOutputContainer;
    }

    public void setFluidOutputContainer(@Nullable FluidContainer fluidOutputContainer) {
        this.fluidOutputContainer = fluidOutputContainer;
    }

    @Nullable
    public ItemContainer getItemOutputContainer() {
        return itemOutputContainer;
    }

    public void setItemOutputContainer(@Nullable ItemContainer itemOutputContainer) {
        this.itemOutputContainer = itemOutputContainer;
    }

    public void setFluidFuelContainer(@Nullable FluidContainer fluidFuelContainer) {
        this.fluidFuelContainer = fluidFuelContainer;
    }

    /**
     * Unified input container facade combining items and fluids.
     * Allows recipes to have arbitrary mixes of items and fluids without
     * knowing internal slot layout.
     */
    @Nullable
    public ItemContainer getInput() {
        if (itemInputContainer == null && fluidInputContainer == null)
            return null;
        if (itemInputContainer == null)
            return fluidInputContainer;
        if (fluidInputContainer == null)
            return itemInputContainer;
        return new CombinedItemContainer(fluidInputContainer, itemInputContainer);
    }

    @Nullable
    public ItemContainer getFuel() {
        if (itemFuelContainer == null && fluidFuelContainer == null)
            return null;
        if (itemFuelContainer == null)
            return fluidFuelContainer;
        if (fluidFuelContainer == null)
            return itemFuelContainer;
        return new CombinedItemContainer(fluidFuelContainer, itemFuelContainer);
    }

    /**
     * Unified output container facade combining items and fluids.
     * Allows recipes to have arbitrary mixes of items and fluids without
     * knowing internal slot layout.
     */
    @Nullable
    public ItemContainer getOutput() {
        if (itemOutputContainer == null && fluidOutputContainer == null)
            return null;
        if (itemOutputContainer == null)
            return fluidOutputContainer;
        if (fluidOutputContainer == null)
            return itemOutputContainer;
        return new CombinedItemContainer(fluidOutputContainer, itemOutputContainer);
    }

    public void setFuelPolicy(@Nonnull FuelPolicy fuelPolicy) {
        this.fuelPolicy = fuelPolicy;
    }

    public float getInputProgress() {
        return inputProgress;
    }

    public void setInputProgress(float inputProgress) {
        this.inputProgress = inputProgress;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Nullable
    public String getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(@Nullable String recipeId) {
        this.recipeId = recipeId;
    }

    @Nullable
    public CraftingRecipe getRecipe() {
        if (recipeId == null || recipeId.isBlank()) {
            return null;
        }
        return (CraftingRecipe) CraftingRecipe.getAssetMap().getAsset(recipeId);
    }

    public void setRecipe(@Nullable CraftingRecipe recipe) {
        this.recipeId = recipe != null ? recipe.getId() : null;
    }

    public void clearCurrentRecipe() {
        setRecipeId(null);
        this.inputProgress = 0.0f;
        this.active = false;
    }

    @Nullable
    public CombinedItemContainer getItemContainer() {
        return itemContainer;
    }

    public void setItemContainer(@Nullable CombinedItemContainer itemContainer) {
        this.itemContainer = itemContainer;
    }

    @Nullable
    public CombinedItemContainer getWindowContainer() {
        return windowContainer;
    }

    public void setWindowContainer(@Nullable CombinedItemContainer windowContainer) {
        this.windowContainer = windowContainer;
    }

    @Nullable
    public ProcessingBench getProcessingBench() {
        return processingBench;
    }

    @Nonnull
    public Map<UUID, AutoProcessingBenchWindow> getWindows() {
        return windows;
    }

    @Override
    @Nullable
    public Component<ChunkStore> clone() {
        return new AutoProcessingBenchBlock(this);
    }
}

package dev.drav.glyphworks.crafting.component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.ProcessingBench;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MaterialTransaction;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.crafting.util.FluidRecipeUtil;
import dev.drav.glyphworks.crafting.window.AutoProcessingBenchWindow;
import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;

/**
 * Full state component for mana-powered automated processing benches.
 *
 * <p>
 * Owns all processing state: input / output containers, crafting progress, and
 * the currently matched recipe. The tick logic lives in
 * {@link dev.drav.glyphworks.crafting.system.AutoProcessingBenchSystem}.
 */
public final class AutoProcessingBenchBlock implements Component<ChunkStore> {

    // ── CODEC ──────────────────────────────────────────────────────────────────

    @Nonnull
    public static final BuilderCodec<AutoProcessingBenchBlock> CODEC = BuilderCodec
            .builder(AutoProcessingBenchBlock.class, AutoProcessingBenchBlock::new)
            .append(
                    new KeyedCodec<>("Glyphworks_AutoProcessingBenchBlock_ManaConsumptionRate", Codec.FLOAT),
                    (b, v) -> b.manaConsumptionRate = v,
                    b -> b.manaConsumptionRate)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_AutoProcessingBenchBlock_InputContainer", ItemContainer.CODEC),
                    (b, v) -> b.inputContainer = v,
                    b -> b.inputContainer)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_AutoProcessingBenchBlock_OutputContainer", ItemContainer.CODEC),
                    (b, v) -> b.outputContainer = v,
                    b -> b.outputContainer)
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_AutoProcessingBenchBlock_Progress", Codec.DOUBLE),
                    (b, v) -> b.craftingProgress = v.floatValue(),
                    b -> Double.valueOf(b.craftingProgress))
            .add()
            .append(
                    new KeyedCodec<>("Glyphworks_AutoProcessingBenchBlock_RecipeId", Codec.STRING),
                    (b, v) -> b.recipeId = v,
                    b -> b.recipeId)
            .add()
            .build();

    // ── Serialised fields ──────────────────────────────────────────────────────

    private float manaConsumptionRate = 1.0f;
    @Nullable private ItemContainer inputContainer;
    @Nullable private ItemContainer outputContainer;
    private float craftingProgress = 0.0f;
    @Nullable private String recipeId;

    // ── Transient / runtime ────────────────────────────────────────────────────

    @Nullable private transient ProcessingBench processingBench;
    @Nullable private transient CraftingRecipe currentRecipe;
    @Nullable private transient CombinedItemContainer itemContainer;
    private transient boolean isCrafting = false;

    @Nonnull
    private transient Map<UUID, AutoProcessingBenchWindow> windows = new ConcurrentHashMap<>();

    public AutoProcessingBenchBlock() {
    }

    public AutoProcessingBenchBlock(@Nonnull AutoProcessingBenchBlock other) {
        this.manaConsumptionRate = other.manaConsumptionRate;
        this.inputContainer = other.inputContainer;
        this.outputContainer = other.outputContainer;
        this.craftingProgress = other.craftingProgress;
        this.recipeId = other.recipeId;
    }

    public static ComponentType<ChunkStore, AutoProcessingBenchBlock> getComponentType() {
        return GlyphworksPlugin.get().getCraftingModule().getAutoProcessingBenchBlockComponentType();
    }

    // ── Container setup ────────────────────────────────────────────────────────

    public void setupContainers(
            @Nonnull BlockModule.BlockStateInfo blockStateInfo,
            @Nonnull BenchBlock benchBlock,
            @Nonnull World world,
            int blockX, int blockY, int blockZ,
            @Nonnull BlockType blockType) {

        if (!(blockType.getBench() instanceof ProcessingBench pb)) {
            return;
        }
        this.processingBench = pb;

        int tierLevel = benchBlock.getTierLevel();
        short inputSlots = (short) pb.getInput(tierLevel).length;
        short outputSlots = (short) pb.getOutputSlotsCount(tierLevel);

        List<ItemStack> ejected = new ArrayList<>();

        outputContainer = ItemContainer.ensureContainerCapacity(
                outputContainer, outputSlots, SimpleItemContainer::getNewContainer, ejected);
        outputContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());
        outputContainer.setGlobalFilter(FilterType.ALLOW_OUTPUT_ONLY);

        inputContainer = ItemContainer.ensureContainerCapacity(
                inputContainer, inputSlots, SimpleItemContainer::getNewContainer, ejected);
        inputContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());
        applyInputFilters(pb, tierLevel);

        itemContainer = new CombinedItemContainer(inputContainer, outputContainer);

        if (recipeId != null) {
            currentRecipe = (CraftingRecipe) CraftingRecipe.getAssetMap().getAsset(recipeId);
        }

        if (!ejected.isEmpty()) {
            Store<EntityStore> entityStore = world.getEntityStore().getStore();
            Holder<EntityStore>[] holders = ejectItems(entityStore, ejected, blockX, blockY, blockZ);
            if (holders.length > 0) {
                world.execute(() -> entityStore.addEntities(holders, AddReason.SPAWN));
            }
        }
    }

    private void applyInputFilters(@Nonnull ProcessingBench pb, int tierLevel) {
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
        short cap = inputContainer.getCapacity();
        for (short i = 0; i < cap; i++) {
            inputContainer.setSlotFilter(FilterActionType.ADD, i, (actionType, container, slotIndex, stack) -> {
                if (stack == null) return true;
                for (MaterialQuantity mat : validIngredients) {
                    if (CraftingManager.matches(mat, stack)) return true;
                }
                return false;
            });
        }
    }

    // ── Recipe detection ───────────────────────────────────────────────────────

    @Nullable
    public CraftingRecipe findMatchingRecipe(int tierLevel, @Nullable FluidContainerComponent fcc) {
        if (processingBench == null || inputContainer == null || inputContainer.getCapacity() == 0)
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
                    || !inputContainer.getSlotMaterialsToRemove(itemInputs, true, true).isEmpty();
            if (itemsReady && hasEnoughFluidInputs(recipe, fcc)) {
                if (inputs.size() > bestInputCount) {
                    bestInputCount = inputs.size();
                    best = recipe;
                }
            }
        }

        return best;
    }

    // ── Crafting checks ────────────────────────────────────────────────────────

    public boolean isReadyToCraft(@Nonnull CraftingRecipe recipe, @Nullable FluidContainerComponent fcc) {
        if (inputContainer == null || inputContainer.getCapacity() == 0)
            return false;
        List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(recipe);
        if (inputs.isEmpty())
            return false;
        List<MaterialQuantity> itemInputs = FluidRecipeUtil.itemParts(inputs);
        boolean itemsReady = itemInputs.isEmpty()
                || !inputContainer.getSlotMaterialsToRemove(itemInputs, true, true).isEmpty();
        return itemsReady && hasEnoughFluidInputs(recipe, fcc);
    }

    public boolean canFitOutput(@Nonnull CraftingRecipe recipe, @Nullable FluidContainerComponent fcc) {
        if (outputContainer == null)
            return false;
        MaterialQuantity[] rawOutputs = recipe.getOutputs();
        List<MaterialQuantity> outputs = rawOutputs != null ? Arrays.asList(rawOutputs) : List.of();

        List<MaterialQuantity> itemOutputs = FluidRecipeUtil.itemParts(outputs);
        List<ItemStack> itemStacks = new ArrayList<>();
        for (MaterialQuantity mat : itemOutputs) {
            ItemStack stack = mat.toItemStack();
            if (stack != null && !stack.isEmpty())
                itemStacks.add(stack);
        }
        if (!outputContainer.canAddItemStacks(itemStacks, false, false))
            return false;

        return canFitFluidOutputs(outputs, fcc);
    }

    public void completeCraft(
            @Nonnull CraftingRecipe recipe,
            @Nullable FluidContainerComponent fcc,
            @Nonnull Store<EntityStore> entityStore,
            int blockX, int blockY, int blockZ) throws MatchException {

        List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(recipe);
        List<MaterialQuantity> itemInputs = FluidRecipeUtil.itemParts(inputs);
        List<MaterialQuantity> fluidInputs = FluidRecipeUtil.fluidParts(inputs);

        MaterialQuantity[] rawOutputs = recipe.getOutputs();
        List<MaterialQuantity> outputList = rawOutputs != null ? Arrays.asList(rawOutputs) : List.of();
        List<MaterialQuantity> itemOutputs = FluidRecipeUtil.itemParts(outputList);
        List<MaterialQuantity> fluidOutputs = FluidRecipeUtil.fluidParts(outputList);

        if (!itemInputs.isEmpty()) {
            ListTransaction<MaterialTransaction> removeTx = inputContainer.removeMaterials(itemInputs, true, true, true);
            if (!removeTx.succeeded())
                return;
        }

        for (MaterialQuantity fluidInput : fluidInputs) {
            if (fcc != null)
                fcc.drain(FluidRecipeUtil.fluidMb(fluidInput));
        }

        craftingProgress = 0.0f;
        isCrafting = false;

        List<ItemStack> itemStacks = new ArrayList<>();
        for (MaterialQuantity mat : itemOutputs) {
            ItemStack stack = mat.toItemStack();
            if (stack != null && !stack.isEmpty())
                itemStacks.add(stack);
        }
        ListTransaction<ItemStackTransaction> addTx = outputContainer.addItemStacks(itemStacks, false, false, false);
        List<ItemStack> remainder = new ArrayList<>();
        for (ItemStackTransaction tx : addTx.getList()) {
            ItemStack rem = tx.getRemainder();
            if (rem != null && !rem.isEmpty())
                remainder.add(rem);
        }
        if (!remainder.isEmpty()) {
            Holder<EntityStore>[] holders = ejectItems(entityStore, remainder, blockX, blockY, blockZ);
            entityStore.addEntities(holders, AddReason.SPAWN);
        }

        for (MaterialQuantity fluidOutput : fluidOutputs) {
            if (fcc != null) {
                String outFluidId = FluidRecipeUtil.fluidId(fluidOutput);
                if (outFluidId != null)
                    fcc.fill(new FluidStack(outFluidId, FluidRecipeUtil.fluidMb(fluidOutput), fcc.getCapacity()));
            }
        }
    }

    private boolean hasEnoughFluidInputs(
            @Nonnull CraftingRecipe recipe, @Nullable FluidContainerComponent fcc) {
        List<MaterialQuantity> fluidInputs = FluidRecipeUtil.fluidParts(CraftingManager.getInputMaterials(recipe));
        if (fluidInputs.isEmpty())
            return true;
        if (fcc == null || fcc.isEmpty())
            return false;
        if (fluidInputs.size() > 1)
            return false;
        MaterialQuantity required = fluidInputs.get(0);
        return java.util.Objects.equals(FluidRecipeUtil.fluidId(required), fcc.getFluidId())
                && fcc.getAmount() >= FluidRecipeUtil.fluidMb(required);
    }

    private boolean canFitFluidOutputs(
            @Nonnull List<MaterialQuantity> outputs, @Nullable FluidContainerComponent fcc) {
        List<MaterialQuantity> fluidOutputs = FluidRecipeUtil.fluidParts(outputs);
        if (fluidOutputs.isEmpty())
            return true;
        if (fcc == null)
            return false;
        if (fluidOutputs.size() > 1)
            return false;
        MaterialQuantity fluidOutput = fluidOutputs.get(0);
        String outputFluidId = FluidRecipeUtil.fluidId(fluidOutput);
        int outputMb = FluidRecipeUtil.fluidMb(fluidOutput);
        String currentFluidId = fcc.getFluidId();
        if (currentFluidId != null && !currentFluidId.equals(outputFluidId))
            return false;
        return fcc.availableSpace() >= outputMb;
    }

    // ── Progress / windows ─────────────────────────────────────────────────────

    public void sendProgress(float normalised) {
        if (!windows.isEmpty()) {
            windows.values().forEach(w -> w.setProgress(normalised));
        }
    }

    // ── Getters / setters ──────────────────────────────────────────────────────

    public float getManaConsumptionRate() {
        return manaConsumptionRate;
    }

    public void setManaConsumptionRate(float manaConsumptionRate) {
        this.manaConsumptionRate = manaConsumptionRate;
    }

    public float getCraftingProgress() {
        return craftingProgress;
    }

    public void setCraftingProgress(float craftingProgress) {
        this.craftingProgress = craftingProgress;
    }

    public boolean isCrafting() {
        return isCrafting;
    }

    public void setCrafting(boolean crafting) {
        this.isCrafting = crafting;
    }

    @Nullable
    public String getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(@Nullable String recipeId) {
        this.recipeId = recipeId;
        this.currentRecipe = recipeId != null
                ? (CraftingRecipe) CraftingRecipe.getAssetMap().getAsset(recipeId)
                : null;
    }

    @Nullable
    public CraftingRecipe getCurrentRecipe() {
        return currentRecipe;
    }

    public void setCurrentRecipe(@Nullable CraftingRecipe currentRecipe) {
        this.currentRecipe = currentRecipe;
        this.recipeId = currentRecipe != null ? currentRecipe.getId() : null;
    }

    @Nullable
    public ItemContainer getInputContainer() {
        return inputContainer;
    }

    @Nullable
    public ItemContainer getOutputContainer() {
        return outputContainer;
    }

    @Nullable
    public CombinedItemContainer getItemContainer() {
        return itemContainer;
    }

    @Nullable
    public ProcessingBench getProcessingBench() {
        return processingBench;
    }

    @Nonnull
    public Map<UUID, AutoProcessingBenchWindow> getWindows() {
        return windows;
    }

    // ── Item ejection ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    Holder<EntityStore>[] ejectItems(
            @Nonnull ComponentAccessor<EntityStore> accessor,
            @Nonnull List<ItemStack> items,
            int blockX, int blockY, int blockZ) {
        if (items.isEmpty())
            return new Holder[0];
        Vector3d dropPos = new Vector3d(blockX + 0.5, blockY + 0.5, blockZ + 0.5);
        return ItemComponent.generateItemDrops(accessor, items, dropPos, Rotation3f.ZERO);
    }

    @Override
    @Nullable
    public Component<ChunkStore> clone() {
        return new AutoProcessingBenchBlock(this);
    }
}

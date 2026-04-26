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
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.ProcessingBench;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.ResourceQuantity;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.inventory.container.filter.ResourceFilter;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MaterialTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ResourceTransaction;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.crafting.util.FluidRecipeUtil;
import dev.drav.glyphworks.crafting.window.AutoProcessingBenchWindow;
import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.container.FluidContainer;

/**
 * Full state component for mana-powered automated processing benches.
 *
 * <p>
 * Owns all processing state: input / output containers, crafting progress, and
 * the currently matched recipe. The tick logic lives in
 * {@link dev.drav.glyphworks.crafting.system.AutoProcessingBenchSystem}.
 */
public final class AutoProcessingBenchBlock implements Component<ChunkStore> {

    private static final String DEFAULT_ITEM_FUEL_RESOURCE_TYPE_ID = "Fuel";
    private static final String FLUID_FUEL_RESOURCE_TYPE_ID = "Glyphworks_Fluid_Fuel";
    private static final int BASE_FLUID_FUEL_MB_PER_TICK = 10;

    // ── CODEC ──────────────────────────────────────────────────────────────────

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
    private transient CraftingRecipe recipe;
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

        if (!initializeBenchConfig(blockType)) {
            return;
        }

        ProcessingBench pb = processingBench;
        if (pb == null) {
            return;
        }

        int tierLevel = benchBlock.getTierLevel();
        ProcessingBench.ProcessingSlot[] benchFuelSlots = pb.getFuel();
        short benchFuelSlotCount = (short) (benchFuelSlots != null ? benchFuelSlots.length : 0);
        short itemFuelSlots = benchFuelSlotCount;
        short inputSlots = (short) pb.getInput(tierLevel).length;
        short outputSlots = (short) pb.getOutputSlotsCount(tierLevel);

        short configuredFluidFuelSlots = (short) Math.max(0, fluidFuelSlots.length);
        short configuredFluidInputSlots = (short) Math.max(0, fluidInputSlots.length);
        short configuredFluidOutputSlots = (short) Math.max(0, fluidOutputSlots.length);

        List<ItemStack> ejected = new ArrayList<>();

        itemFuelContainer = ItemContainer.ensureContainerCapacity(
                itemFuelContainer, itemFuelSlots, SimpleItemContainer::getNewContainer, ejected);
        itemFuelContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());
        for (short i = 0; i < itemFuelContainer.getCapacity(); i++) {
            String requiredResourceTypeId = getFuelSlotResourceTypeId(i, DEFAULT_ITEM_FUEL_RESOURCE_TYPE_ID);
            itemFuelContainer.setSlotFilter(
                    FilterActionType.ADD,
                    i,
                    new ResourceFilter(new ResourceQuantity(requiredResourceTypeId, 1)));
        }

        itemOutputContainer = ItemContainer.ensureContainerCapacity(
                itemOutputContainer, outputSlots, SimpleItemContainer::getNewContainer, ejected);
        itemOutputContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());
        itemOutputContainer.setGlobalFilter(FilterType.ALLOW_OUTPUT_ONLY);

        itemInputContainer = ItemContainer.ensureContainerCapacity(
                itemInputContainer, inputSlots, SimpleItemContainer::getNewContainer, ejected);
        itemInputContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());
        applyInputFilters(pb, tierLevel);

        if (configuredFluidFuelSlots > 0) {
            fluidFuelContainer = (FluidContainer) ItemContainer.ensureContainerCapacity(
                    fluidFuelContainer,
                    configuredFluidFuelSlots,
                    s -> new FluidContainer(s, getMaxSlotCapacityMb(fluidFuelSlots)),
                    ejected);
            fluidFuelContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());
            for (short i = 0; i < fluidFuelContainer.getCapacity(); i++) {
                String requiredResourceTypeId = getConfiguredFluidFuelSlotResourceTypeId(i,
                        FLUID_FUEL_RESOURCE_TYPE_ID);
                fluidFuelContainer.setSlotFilter(FilterActionType.ADD,
                        i,
                        (actionType, container, slotIndex, stack) -> {
                            if (!(stack instanceof FluidStack fluidStack)) {
                                return stack == null;
                            }
                            if (!isFuelFluid(fluidStack, requiredResourceTypeId)) {
                                return false;
                            }
                            int slotCapacity = getConfiguredFluidFuelSlotCapacityMb(slotIndex);
                            int existing = getExistingFluidAmount(container, slotIndex, fluidStack.getFluidId());
                            return existing + fluidStack.getQuantity() <= slotCapacity;
                        });
            }
        } else {
            fluidFuelContainer = null;
        }

        if (configuredFluidInputSlots > 0) {
            fluidInputContainer = (FluidContainer) ItemContainer.ensureContainerCapacity(
                    fluidInputContainer,
                    configuredFluidInputSlots,
                    s -> new FluidContainer(s, getMaxSlotCapacityMb(fluidInputSlots)),
                    ejected);
            fluidInputContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());
            for (short i = 0; i < fluidInputContainer.getCapacity(); i++) {
                String requiredResourceTypeId = getConfiguredFluidInputSlotResourceTypeId(i);
                if (requiredResourceTypeId == null || requiredResourceTypeId.isBlank()) {
                    continue;
                }
                fluidInputContainer.setSlotFilter(FilterActionType.ADD,
                        i,
                        (actionType, container, slotIndex, stack) -> {
                            if (!(stack instanceof FluidStack fluidStack)) {
                                return stack == null;
                            }
                            Item item = fluidStack.getItem();
                            if (item == null || !hasResourceType(item, requiredResourceTypeId)) {
                                return false;
                            }
                            int slotCapacity = getConfiguredFluidInputSlotCapacityMb(slotIndex);
                            int existing = getExistingFluidAmount(container, slotIndex, fluidStack.getFluidId());
                            return existing + fluidStack.getQuantity() <= slotCapacity;
                        });
            }
        } else {
            fluidInputContainer = null;
        }

        if (configuredFluidOutputSlots > 0) {
            fluidOutputContainer = (FluidContainer) ItemContainer.ensureContainerCapacity(
                    fluidOutputContainer,
                    configuredFluidOutputSlots,
                    s -> new FluidContainer(s, getMaxSlotCapacityMb(fluidOutputSlots)),
                    ejected);
            fluidOutputContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());
        } else {
            fluidOutputContainer = null;
        }

        itemContainer = new CombinedItemContainer(itemFuelContainer, itemInputContainer, itemOutputContainer);
        windowContainer = new CombinedItemContainer(
                fluidFuelContainer,
                itemFuelContainer,
                fluidInputContainer,
                itemInputContainer,
                itemOutputContainer,
                fluidOutputContainer);

        if (recipeId != null) {
            recipe = (CraftingRecipe) CraftingRecipe.getAssetMap().getAsset(recipeId);
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

    // ── Recipe detection ───────────────────────────────────────────────────────

    public void updateRecipe(@Nonnull BenchBlock benchBlock) {
        recipe = findMatchingRecipe(benchBlock.getTierLevel());
        recipeId = recipe != null ? recipe.getId() : null;
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

    public boolean canFitOutput(@Nonnull CraftingRecipe recipe) {
        if (itemOutputContainer == null)
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
        if (!itemOutputContainer.canAddItemStacks(itemStacks, false, false))
            return false;

        return canFitFluidOutputs(outputs);
    }

    public void completeCraft(
            @Nonnull CraftingRecipe recipe,
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
            if (itemInputContainer == null) {
                return;
            }
            ListTransaction<MaterialTransaction> removeTx = itemInputContainer.removeMaterials(itemInputs, true, true,
                    true);
            if (!removeTx.succeeded())
                return;
        }

        for (MaterialQuantity fluidInput : fluidInputs) {
            String fluidId = FluidRecipeUtil.fluidId(fluidInput);
            if (fluidId == null || fluidInputContainer == null)
                continue;
            int remaining = FluidRecipeUtil.fluidMb(fluidInput);
            for (short i = 0; i < fluidInputContainer.getCapacity() && remaining > 0; i++) {
                FluidStack s = fluidInputContainer.getFluidStack(i);
                if (s == null || !fluidId.equals(s.getFluidId()))
                    continue;
                int consume = Math.min(remaining, s.getQuantity());
                fluidInputContainer.removeFluidStackFromSlot(i, consume, false, false);
                remaining -= consume;
            }
        }

        inputProgress = 0.0f;
        active = false;

        List<ItemStack> itemStacks = new ArrayList<>();
        for (MaterialQuantity mat : itemOutputs) {
            ItemStack stack = mat.toItemStack();
            if (stack != null && !stack.isEmpty())
                itemStacks.add(stack);
        }
        ListTransaction<ItemStackTransaction> addTx = itemOutputContainer.addItemStacks(itemStacks, false, false,
                false);
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
            String outFluidId = FluidRecipeUtil.fluidId(fluidOutput);
            if (outFluidId == null || fluidOutputContainer == null)
                continue;
            addFluidOutputRespectingSlotCapacity(outFluidId, FluidRecipeUtil.fluidMb(fluidOutput));
        }
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
                    totalMb += s.getQuantity();
            }
            if (totalMb < FluidRecipeUtil.fluidMb(required))
                return false;
        }
        return true;
    }

    private boolean canFitFluidOutputs(
            @Nonnull List<MaterialQuantity> outputs) {
        List<MaterialQuantity> fluidOutputs = FluidRecipeUtil.fluidParts(outputs);
        if (fluidOutputs.isEmpty())
            return true;
        if (fluidOutputContainer == null || fluidOutputContainer.getCapacity() == 0)
            return false;
        for (MaterialQuantity fluidOutput : fluidOutputs) {
            String outputFluidId = FluidRecipeUtil.fluidId(fluidOutput);
            if (outputFluidId == null)
                return false;
            int outputMb = FluidRecipeUtil.fluidMb(fluidOutput);
            int available = 0;
            for (short i = 0; i < fluidOutputContainer.getCapacity(); i++) {
                FluidStack s = fluidOutputContainer.getFluidStack(i);
                if (s == null)
                    available += getConfiguredFluidOutputSlotCapacityMb(i);
                else if (outputFluidId.equals(s.getFluidId()))
                    available += getConfiguredFluidOutputSlotCapacityMb(i) - s.getQuantity();
            }
            if (available < outputMb)
                return false;
        }
        return true;
    }

    private void addFluidOutputRespectingSlotCapacity(@Nonnull String fluidId, int amountMb) throws MatchException {
        if (fluidOutputContainer == null || amountMb <= 0) {
            return;
        }

        int remaining = amountMb;
        for (short i = 0; i < fluidOutputContainer.getCapacity() && remaining > 0; i++) {
            FluidStack existing = fluidOutputContainer.getFluidStack(i);
            if (existing != null && !fluidId.equals(existing.getFluidId())) {
                continue;
            }

            int slotCapacity = getConfiguredFluidOutputSlotCapacityMb(i);
            int existingAmount = existing != null ? existing.getQuantity() : 0;
            int space = slotCapacity - existingAmount;
            if (space <= 0) {
                continue;
            }

            int toAdd = Math.min(space, remaining);
            ItemStackSlotTransaction tx = fluidOutputContainer.addFluidStackToSlot(
                    i,
                    new FluidStack(fluidId, toAdd, slotCapacity),
                    false,
                    false);
            if (!tx.succeeded()) {
                throw new MatchException("failed to add fluid output to slot", null);
            }
            remaining -= toAdd;
        }

        if (remaining > 0) {
            throw new MatchException("insufficient fluid output slot capacity", null);
        }
    }

    public boolean consumeFuelForDuration(float duration) {
        if (duration <= 0.0f) {
            return true;
        }
        if (!hasFuelSlots()) {
            return true;
        }

        float consumed = 0.0f;
        if (fuelTime > 0.0f) {
            float use = Math.min(fuelTime, duration);
            fuelTime -= use;
            consumed += use;
        }

        while (consumed < duration && consumeOneFuel() >= 0) {
            float use = Math.min(fuelTime, duration - consumed);
            fuelTime -= use;
            consumed += use;
        }

        return consumed >= duration;
    }

    private int consumeOneFuel() {
        if (fuelPolicy == FuelPolicy.ALL) {
            return consumeAllFuel();
        }
        return consumeAnyFuel();
    }

    private boolean hasFuelSlots() {
        boolean hasItemFuel = itemFuelContainer != null && itemFuelContainer.getCapacity() > 0;
        boolean hasFluidFuel = fluidFuelContainer != null && fluidFuelContainer.getCapacity() > 0;
        return hasItemFuel || hasFluidFuel;
    }

    private int consumeAllFuel() {
        boolean hasItemFuel = itemFuelContainer != null && itemFuelContainer.getCapacity() > 0;
        boolean hasFluidFuel = fluidFuelContainer != null && fluidFuelContainer.getCapacity() > 0;

        if (hasItemFuel && !hasRequiredItemFuelInEverySlot())
            return -1;
        if (hasFluidFuel && !hasRequiredFluidFuelInEverySlot())
            return -1;

        float itemGain = hasItemFuel ? consumeAllItemFuelAmount() : 0.0f;
        float fluidGain = hasFluidFuel ? consumeAllFluidFuelAmount() : 0.0f;

        float gained;
        if (hasItemFuel && hasFluidFuel) {
            gained = Math.min(itemGain, fluidGain);
        } else {
            gained = hasItemFuel ? itemGain : fluidGain;
        }

        if (gained <= 0.0f) {
            return -1;
        }

        fuelTime += gained;
        return 0;
    }

    private boolean hasRequiredItemFuelInEverySlot() {
        if (itemFuelContainer == null || itemFuelContainer.getCapacity() == 0) {
            return false;
        }

        short capacity = itemFuelContainer.getCapacity();
        for (short i = 0; i < capacity; i++) {
            ItemStack stack = itemFuelContainer.getItemStack(i);
            if (stack == null || stack.isEmpty()) {
                return false;
            }

            Item item = stack.getItem();
            if (item == null || item.getFuelQuality() <= 0.0) {
                return false;
            }

            String requiredResourceTypeId = getFuelSlotResourceTypeId(i, DEFAULT_ITEM_FUEL_RESOURCE_TYPE_ID);
            if (!hasResourceType(item, requiredResourceTypeId)) {
                return false;
            }
        }

        return true;
    }

    private boolean hasRequiredFluidFuelInEverySlot() {
        if (fluidFuelContainer == null || fluidFuelContainer.getCapacity() == 0) {
            return false;
        }

        short capacity = fluidFuelContainer.getCapacity();
        for (short i = 0; i < capacity; i++) {
            FluidStack fluidStack = fluidFuelContainer.getFluidStack(i);
            String requiredResourceTypeId = getConfiguredFluidFuelSlotResourceTypeId(i, FLUID_FUEL_RESOURCE_TYPE_ID);
            if (fluidStack == null || !isFuelFluid(fluidStack, requiredResourceTypeId)) {
                return false;
            }

            int requiredMb = fluidFuelCostMbPerTick(fluidStack);
            if (requiredMb <= 0 || fluidStack.getQuantity() < requiredMb) {
                return false;
            }
        }

        return true;
    }

    private float consumeAllItemFuelAmount() {
        if (!hasRequiredItemFuelInEverySlot()) {
            return 0.0f;
        }

        float totalGain = 0.0f;
        short capacity = itemFuelContainer.getCapacity();
        for (short i = 0; i < capacity; i++) {
            float slotGain = consumeOneItemFuelAmountFromSlot(i);
            if (slotGain <= 0.0f) {
                return 0.0f;
            }
            totalGain += slotGain;
        }

        return totalGain;
    }

    private float consumeAllFluidFuelAmount() {
        if (!hasRequiredFluidFuelInEverySlot()) {
            return 0.0f;
        }

        float totalGain = 0.0f;
        short capacity = fluidFuelContainer.getCapacity();
        for (short i = 0; i < capacity; i++) {
            float slotGain = consumeOneFluidFuelAmountFromSlot(i);
            if (slotGain <= 0.0f) {
                return 0.0f;
            }
            totalGain += slotGain;
        }

        return totalGain;
    }

    private int consumeAnyFuel() {
        int result = consumeOneItemFuel();
        if (result >= 0) {
            return result;
        }

        int fluidResult = consumeOneFluidFuel();
        return fluidResult;
    }

    private int consumeOneItemFuel() {
        float fuelAmount = consumeOneItemFuelAmount();
        if (fuelAmount <= 0.0f) {
            return -1;
        }

        fuelTime += fuelAmount;
        return 0;
    }

    private int consumeOneFluidFuel() {
        float fuelAmount = consumeOneFluidFuelAmount();
        if (fuelAmount <= 0.0f) {
            return -1;
        }

        fuelTime += fuelAmount;
        return 0;
    }

    private float consumeOneItemFuelAmount() {
        if (itemFuelContainer == null || itemFuelContainer.getCapacity() == 0) {
            return 0.0f;
        }

        short capacity = itemFuelContainer.getCapacity();
        for (short i = 0; i < capacity; i++) {
            ItemStack stack = itemFuelContainer.getItemStack(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            String requiredResourceTypeId = getFuelSlotResourceTypeId(i, DEFAULT_ITEM_FUEL_RESOURCE_TYPE_ID);
            ResourceTransaction transaction = itemFuelContainer.removeResource(
                    new ResourceQuantity(requiredResourceTypeId, 1),
                    true,
                    true,
                    true);
            if (transaction.getRemainder() > 0) {
                continue;
            }

            Item consumedItem = stack.getItem();
            double fuelQuality = consumedItem != null ? consumedItem.getFuelQuality() : 0.0;
            if (fuelQuality <= 0.0) {
                continue;
            }

            return (float) (transaction.getConsumed() * fuelQuality);
        }

        return 0.0f;
    }

    private float consumeOneFluidFuelAmount() {
        if (fluidFuelContainer == null || fluidFuelContainer.getCapacity() == 0) {
            return 0.0f;
        }

        for (short i = 0; i < fluidFuelContainer.getCapacity(); i++) {
            FluidStack fluidStack = fluidFuelContainer.getFluidStack(i);
            String requiredResourceTypeId = getConfiguredFluidFuelSlotResourceTypeId(i, FLUID_FUEL_RESOURCE_TYPE_ID);
            if (fluidStack == null) {
                continue;
            }

            boolean fuelFluid = isFuelFluid(fluidStack, requiredResourceTypeId);

            if (!fuelFluid) {
                continue;
            }

            int requiredMb = fluidFuelCostMbPerTick(fluidStack);
            if (requiredMb <= 0 || fluidStack.getQuantity() < requiredMb) {
                continue;
            }

            ItemStackSlotTransaction transaction = fluidFuelContainer.removeFluidStackFromSlot(i, requiredMb, true,
                    false);
            if (!transaction.succeeded()) {
                continue;
            }

            return 1.0f;
        }

        return 0.0f;
    }

    private float consumeOneItemFuelAmountFromSlot(short slotIndex) {
        if (itemFuelContainer == null || slotIndex < 0 || slotIndex >= itemFuelContainer.getCapacity()) {
            return 0.0f;
        }

        ItemStack stack = itemFuelContainer.getItemStack(slotIndex);
        if (stack == null || stack.isEmpty()) {
            return 0.0f;
        }

        Item item = stack.getItem();
        if (item == null) {
            return 0.0f;
        }

        String requiredResourceTypeId = getFuelSlotResourceTypeId(slotIndex, DEFAULT_ITEM_FUEL_RESOURCE_TYPE_ID);
        if (!hasResourceType(item, requiredResourceTypeId)) {
            return 0.0f;
        }

        double fuelQuality = item.getFuelQuality();
        if (fuelQuality <= 0.0) {
            return 0.0f;
        }

        itemFuelContainer.removeItemStackFromSlot(slotIndex, 1);
        return (float) fuelQuality;
    }

    private float consumeOneFluidFuelAmountFromSlot(short slotIndex) {
        if (fluidFuelContainer == null || slotIndex < 0 || slotIndex >= fluidFuelContainer.getCapacity()) {
            return 0.0f;
        }

        FluidStack fluidStack = fluidFuelContainer.getFluidStack(slotIndex);
        String requiredResourceTypeId = getConfiguredFluidFuelSlotResourceTypeId(slotIndex,
                FLUID_FUEL_RESOURCE_TYPE_ID);
        if (fluidStack == null || !isFuelFluid(fluidStack, requiredResourceTypeId)) {
            return 0.0f;
        }

        int requiredMb = fluidFuelCostMbPerTick(fluidStack);
        if (requiredMb <= 0 || fluidStack.getQuantity() < requiredMb) {
            return 0.0f;
        }

        ItemStackSlotTransaction transaction = fluidFuelContainer.removeFluidStackFromSlot(
                slotIndex, requiredMb, true, false);
        if (!transaction.succeeded()) {
            return 0.0f;
        }

        return 1.0f;
    }

    private int fluidFuelCostMbPerTick(@Nonnull FluidStack fluidStack) {
        Item item = fluidStack.getItem();
        double fuelQuality = item != null ? item.getFuelQuality() : 0.0;
        if (fuelQuality <= 0.0) {
            return BASE_FLUID_FUEL_MB_PER_TICK;
        }

        return Math.max(1, (int) Math.round(BASE_FLUID_FUEL_MB_PER_TICK / fuelQuality));
    }

    private boolean isFuelFluid(@Nonnull FluidStack fluidStack,
            @Nonnull String requiredResourceTypeId) {
        Item item = fluidStack.getItem();
        return item != null
                && item.getFuelQuality() > 0.0
                && hasResourceType(item, requiredResourceTypeId);
    }

    private boolean hasResourceType(@Nonnull Item item, @Nonnull String resourceTypeId) {
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
    private String getFuelSlotResourceTypeId(short slotIndex, @Nonnull String fallbackResourceTypeId) {
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
    private String getConfiguredFluidFuelSlotResourceTypeId(short slotIndex, @Nonnull String fallbackResourceTypeId) {
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
    private String getConfiguredFluidInputSlotResourceTypeId(short slotIndex) {
        if (slotIndex < 0 || slotIndex >= fluidInputSlots.length) {
            return null;
        }
        FluidSlot slot = fluidInputSlots[slotIndex];
        return slot != null ? slot.getResourceTypeId() : null;
    }

    private int getConfiguredFluidFuelSlotCapacityMb(short slotIndex) {
        return getConfiguredSlotCapacityMb(fluidFuelSlots, slotIndex);
    }

    private int getConfiguredFluidInputSlotCapacityMb(short slotIndex) {
        return getConfiguredSlotCapacityMb(fluidInputSlots, slotIndex);
    }

    private int getConfiguredFluidOutputSlotCapacityMb(short slotIndex) {
        return getConfiguredSlotCapacityMb(fluidOutputSlots, slotIndex);
    }

    private int getConfiguredSlotCapacityMb(@Nonnull FluidSlot[] slots, short slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.length) {
            return 4000;
        }
        FluidSlot slot = slots[slotIndex];
        return slot != null ? slot.getCapacityMbPerSlot() : 4000;
    }

    private int getMaxSlotCapacityMb(@Nonnull FluidSlot[] slots) {
        int max = 4000;
        for (FluidSlot slot : slots) {
            if (slot != null) {
                max = Math.max(max, slot.getCapacityMbPerSlot());
            }
        }
        return max;
    }

    private int getExistingFluidAmount(@Nonnull ItemContainer container, short slotIndex, @Nonnull String fluidId) {
        if (!(container instanceof FluidContainer fluidContainer)) {
            return 0;
        }
        FluidStack existing = fluidContainer.getFluidStack(slotIndex);
        if (existing == null || !fluidId.equals(existing.getFluidId())) {
            return 0;
        }
        return existing.getQuantity();
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

    @Nullable
    public FluidContainer getFluidFuelContainer() {
        return fluidFuelContainer;
    }

    @Nullable
    public ItemContainer getItemFuelContainer() {
        return itemFuelContainer;
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

    @Nullable
    public ItemContainer getItemInputContainer() {
        return itemInputContainer;
    }

    @Nullable
    public FluidContainer getFluidOutputContainer() {
        return fluidOutputContainer;
    }

    @Nullable
    public ItemContainer getItemOutputContainer() {
        return itemOutputContainer;
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
        this.recipe = recipeId != null
                ? (CraftingRecipe) CraftingRecipe.getAssetMap().getAsset(recipeId)
                : null;
    }

    @Nullable
    public CraftingRecipe getRecipe() {
        return recipe;
    }

    public void setRecipe(@Nullable CraftingRecipe recipe) {
        this.recipe = recipe;
        this.recipeId = recipe != null ? recipe.getId() : null;
    }

    public void clearCurrentRecipe() {
        setRecipe(null);
    }

    @Nullable
    public CombinedItemContainer getItemContainer() {
        return itemContainer;
    }

    @Nullable
    public CombinedItemContainer getWindowContainer() {
        return windowContainer;
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

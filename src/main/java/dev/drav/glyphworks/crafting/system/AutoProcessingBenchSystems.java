package dev.drav.glyphworks.crafting.system;

import org.joml.Vector3i;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.ProcessingBench;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.player.windows.WindowManager;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.ResourceQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.inventory.container.filter.ResourceFilter;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MaterialTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ResourceTransaction;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.crafting.component.AutoProcessingBenchBlock;
import dev.drav.glyphworks.crafting.component.FuelPolicy;
import dev.drav.glyphworks.crafting.util.FluidRecipeUtil;
import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.container.FluidContainer;
import dev.drav.glyphworks.fluid.event.FluidItemRegistry;
import dev.drav.glyphworks.fluid.transaction.FluidStackSlotTransaction;

public final class AutoProcessingBenchSystems {

    private static final String DEFAULT_ITEM_FUEL_RESOURCE_TYPE_ID = "Fuel";
    private static final String FLUID_FUEL_RESOURCE_TYPE_ID = "Glyphworks_Fluid_Fuel";
    private static final short DEFAULT_SELECTOR_OUTPUT_SLOTS = 2;

    private AutoProcessingBenchSystems() {
    }

    public static void initializeBenchSlots(
            @Nonnull AutoProcessingBenchBlock bench,
            @Nonnull World world,
            @Nonnull BenchBlock benchBlock,
            @Nonnull BlockModule.BlockStateInfo blockStateInfo,
            int blockX,
            int blockY,
            int blockZ,
            @Nonnull BlockType blockType,
            int rotationIndex) {
        bench.initializeBenchConfig(blockType);
        int tierLevel = benchBlock.getTierLevel();

        short itemFuelSlots = 0;
        short inputSlots = 0;
        short outputSlots = DEFAULT_SELECTOR_OUTPUT_SLOTS;
        if (bench.getProcessingBench() != null) {
            ProcessingBench.ProcessingSlot[] benchFuelSlots = bench.getProcessingBench().getFuel();
            itemFuelSlots = (short) (benchFuelSlots != null ? benchFuelSlots.length : 0);
            inputSlots = (short) bench.getProcessingBench().getInput(tierLevel).length;
            outputSlots = (short) bench.getProcessingBench().getOutputSlotsCount(tierLevel);
        }

        short configuredFluidFuelSlots = bench.getConfiguredFluidFuelSlotsCount();
        short configuredFluidInputSlots = bench.getConfiguredFluidInputSlotsCount();
        short configuredFluidOutputSlots = bench.getConfiguredFluidOutputSlotsCount();

        List<ItemStack> ejected = new ArrayList<>();

        if (itemFuelSlots > 0) {
            ItemContainer itemFuelContainer = ItemContainer.ensureContainerCapacity(
                    bench.getItemFuelContainer(),
                    itemFuelSlots,
                    SimpleItemContainer::getNewContainer,
                    ejected);
            itemFuelContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());
            for (short i = 0; i < itemFuelContainer.getCapacity(); i++) {
                String requiredType = bench.getFuelSlotResourceTypeId(i, DEFAULT_ITEM_FUEL_RESOURCE_TYPE_ID);
                itemFuelContainer.setSlotFilter(
                        FilterActionType.ADD,
                        i,
                        new ResourceFilter(new ResourceQuantity(requiredType, 1)));
            }
            bench.setItemFuelContainer(itemFuelContainer);
        } else {
            AutoProcessingBenchBlock.ejectAndClearContainer(bench.getItemFuelContainer(), ejected);
            bench.setItemFuelContainer(null);
        }

        ItemContainer itemOutputContainer = ItemContainer.ensureContainerCapacity(
                bench.getItemOutputContainer(),
                outputSlots,
                SimpleItemContainer::getNewContainer,
                ejected);
        itemOutputContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());
        itemOutputContainer.setGlobalFilter(FilterType.ALLOW_OUTPUT_ONLY);
        bench.setItemOutputContainer(itemOutputContainer);

        if (inputSlots > 0) {
            ItemContainer itemInputContainer = ItemContainer.ensureContainerCapacity(
                    bench.getItemInputContainer(),
                    inputSlots,
                    SimpleItemContainer::getNewContainer,
                    ejected);
            itemInputContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());
            bench.setItemInputContainer(itemInputContainer);

            if (bench.getProcessingBench() != null) {
                bench.applyInputFilters(bench.getProcessingBench(), tierLevel);
            }
        } else {
            AutoProcessingBenchBlock.ejectAndClearContainer(bench.getItemInputContainer(), ejected);
            bench.setItemInputContainer(null);
        }

        if (configuredFluidFuelSlots > 0) {
            FluidContainer fluidFuelContainer = (FluidContainer) ItemContainer.ensureContainerCapacity(
                    bench.getFluidFuelContainer(),
                    configuredFluidFuelSlots,
                    s -> new FluidContainer(s, bench.getMaxFluidFuelSlotCapacityMb()),
                    ejected);
            fluidFuelContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());
            for (short i = 0; i < fluidFuelContainer.getCapacity(); i++) {
                String requiredType = bench.getConfiguredFluidFuelSlotResourceTypeId(i, FLUID_FUEL_RESOURCE_TYPE_ID);
                fluidFuelContainer.setSlotFilter(
                        FilterActionType.ADD,
                        i,
                        (actionType, container, slot, incoming, existing) -> {
                            if (incoming == null || !isFuelFluidForResource(incoming, requiredType)) {
                                return false;
                            }
                            int slotCapacity = bench.getConfiguredFluidFuelSlotCapacityMb(slot);
                            int existingAmount = existing != null ? existing.getAmount() : 0;
                            return existingAmount + incoming.getAmount() <= slotCapacity;
                        });
            }
            bench.setFluidFuelContainer(fluidFuelContainer);
        } else {
            bench.setFluidFuelContainer(null);
        }

        if (configuredFluidInputSlots > 0) {
            FluidContainer fluidInputContainer = (FluidContainer) ItemContainer.ensureContainerCapacity(
                    bench.getFluidInputContainer(),
                    configuredFluidInputSlots,
                    s -> new FluidContainer(s, bench.getMaxFluidInputSlotCapacityMb()),
                    ejected);
            fluidInputContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());
            bench.setFluidInputContainer(fluidInputContainer);
        } else {
            bench.setFluidInputContainer(null);
        }

        if (configuredFluidOutputSlots > 0) {
            FluidContainer fluidOutputContainer = (FluidContainer) ItemContainer.ensureContainerCapacity(
                    bench.getFluidOutputContainer(),
                    configuredFluidOutputSlots,
                    s -> new FluidContainer(s, bench.getMaxFluidOutputSlotCapacityMb()),
                    ejected);
            fluidOutputContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());
            bench.setFluidOutputContainer(fluidOutputContainer);
        } else {
            bench.setFluidOutputContainer(null);
        }

        bench.setItemContainer(AutoProcessingBenchBlock.buildNullableCombined(
                bench.getItemFuelContainer(),
                bench.getItemInputContainer(),
                bench.getItemOutputContainer()));
        bench.setWindowContainer(AutoProcessingBenchBlock.buildNullableCombined(
                bench.getFluidFuelContainer(),
                bench.getItemFuelContainer(),
                bench.getFluidInputContainer(),
                bench.getItemInputContainer(),
                bench.getItemOutputContainer(),
                bench.getFluidOutputContainer()));

        if (bench.getRecipeId() != null) {
            CraftingRecipe recipe = (CraftingRecipe) CraftingRecipe.getAssetMap().getAsset(bench.getRecipeId());
            bench.setRecipeId(recipe != null ? recipe.getId() : null);
        }

        if (!ejected.isEmpty()) {
            Store<EntityStore> entityStore = world.getEntityStore().getStore();
            Vector3d dropPos = new Vector3d(blockX + 0.5, blockY + 0.5, blockZ + 0.5);
            Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(entityStore, ejected, dropPos,
                    Rotation3f.ZERO);
            if (holders.length > 0) {
                world.execute(() -> entityStore.addEntities(holders, AddReason.SPAWN));
            }
        }
    }

    public static void applyExternalRecipeLayout(
            @Nonnull AutoProcessingBenchBlock bench,
            @Nullable CraftingRecipe selectedRecipe,
            @Nonnull BlockModule.BlockStateInfo blockStateInfo,
            @Nonnull World world,
            int blockX,
            int blockY,
            int blockZ) {
        List<ItemStack> ejected = new ArrayList<>();
        List<MaterialQuantity> inputMaterials = selectedRecipe != null
                ? CraftingManager.getInputMaterials(selectedRecipe)
                : List.of();
        List<MaterialQuantity> outputMaterials = selectedRecipe != null && selectedRecipe.getOutputs() != null
                ? List.of(selectedRecipe.getOutputs())
                : List.of();

        List<MaterialQuantity> itemInputs = FluidRecipeUtil.itemParts(inputMaterials);
        List<MaterialQuantity> fluidInputs = FluidRecipeUtil.fluidParts(inputMaterials);
        List<MaterialQuantity> fluidOutputs = FluidRecipeUtil.fluidParts(outputMaterials);

        short itemInputSlots = (short) itemInputs.size();
        short fluidInputSlots = (short) fluidInputs.size();
        short fluidOutputSlots = (short) fluidOutputs.size();

        if (itemInputSlots > 0) {
            ItemContainer itemInputContainer = ItemContainer.ensureContainerCapacity(
                    bench.getItemInputContainer(),
                    itemInputSlots,
                    SimpleItemContainer::getNewContainer,
                    ejected);
            itemInputContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());

            for (short i = 0; i < itemInputs.size(); i++) {
                MaterialQuantity material = itemInputs.get(i);
                itemInputContainer.setSlotFilter(
                        FilterActionType.ADD,
                        i,
                        (actionType, container, slotIndex, stack) -> {
                            if (stack == null) {
                                return true;
                            }
                            return CraftingManager.matches(material, stack);
                        });
            }

            bench.setItemInputContainer(itemInputContainer);
        } else {
            AutoProcessingBenchBlock.ejectAndClearContainer(bench.getItemInputContainer(), ejected);
            bench.setItemInputContainer(null);
        }

        short outputSlots = bench.getItemOutputContainer() != null
                ? bench.getItemOutputContainer().getCapacity()
                : DEFAULT_SELECTOR_OUTPUT_SLOTS;
        ItemContainer itemOutputContainer = ItemContainer.ensureContainerCapacity(
                bench.getItemOutputContainer(),
                outputSlots,
                SimpleItemContainer::getNewContainer,
                ejected);
        itemOutputContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());
        itemOutputContainer.setGlobalFilter(FilterType.ALLOW_OUTPUT_ONLY);
        bench.setItemOutputContainer(itemOutputContainer);

        if (fluidInputSlots > 0) {
            FluidContainer fluidInputContainer = (FluidContainer) ItemContainer.ensureContainerCapacity(
                    bench.getFluidInputContainer(),
                    fluidInputSlots,
                    s -> new FluidContainer(s, bench.getMaxFluidInputSlotCapacityMb()),
                    ejected);
            fluidInputContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());

            bench.setFluidInputContainer(fluidInputContainer);
        } else {
            bench.setFluidInputContainer(null);
        }

        if (fluidOutputSlots > 0) {
            FluidContainer fluidOutputContainer = (FluidContainer) ItemContainer.ensureContainerCapacity(
                    bench.getFluidOutputContainer(),
                    fluidOutputSlots,
                    s -> new FluidContainer(s, bench.getMaxFluidOutputSlotCapacityMb()),
                    ejected);
            fluidOutputContainer.registerChangeEvent(EventPriority.LAST, e -> blockStateInfo.markNeedsSaving());
            bench.setFluidOutputContainer(fluidOutputContainer);
        } else {
            bench.setFluidOutputContainer(null);
        }

        bench.setItemContainer(AutoProcessingBenchBlock.buildNullableCombined(
                bench.getItemFuelContainer(),
                bench.getItemInputContainer(),
                bench.getItemOutputContainer()));
        bench.setWindowContainer(AutoProcessingBenchBlock.buildNullableCombined(
                bench.getFluidFuelContainer(),
                bench.getItemFuelContainer(),
                bench.getFluidInputContainer(),
                bench.getItemInputContainer(),
                bench.getItemOutputContainer(),
                bench.getFluidOutputContainer()));

        if (!ejected.isEmpty()) {
            Store<EntityStore> entityStore = world.getEntityStore().getStore();
            Vector3d dropPos = new Vector3d(blockX + 0.5, blockY + 0.5, blockZ + 0.5);
            Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(entityStore, ejected, dropPos,
                    Rotation3f.ZERO);
            if (holders.length > 0) {
                world.execute(() -> entityStore.addEntities(holders, AddReason.SPAWN));
            }
        }

        blockStateInfo.markNeedsSaving();
    }

    private static boolean isFuelFluidForResource(
            @Nonnull FluidStack fluidStack,
            @Nonnull String requiredResourceTypeId) {
        Item item = resolveFluidItem(fluidStack);
        return item != null
                && item.getFuelQuality() > 0.0
                && hasResourceType(item, requiredResourceTypeId);
    }

    @Nullable
    private static Item resolveFluidItem(@Nonnull FluidStack fluidStack) {
        String itemId = FluidItemRegistry.resolveItemId(fluidStack.getFluidId());
        return itemId == null ? null : (Item) Item.getAssetMap().getAsset(itemId);
    }

    private static boolean hasResourceType(
            @Nonnull Item item,
            @Nonnull String resourceTypeId) {
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

    public static final class Setup extends RefSystem<ChunkStore> {

        private final ComponentType<ChunkStore, BlockStateInfo> blockStateInfoType = BlockModule.BlockStateInfo
                .getComponentType();

        @Override
        public Query<ChunkStore> getQuery() {
            return AutoProcessingBenchBlock.getComponentType();
        }

        @Override
        public void onEntityAdded(
                @Nonnull Ref<ChunkStore> ref,
                @Nonnull AddReason reason,
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

            AutoProcessingBenchBlock apbb = commandBuffer.getComponent(ref,
                    AutoProcessingBenchBlock.getComponentType());
            BlockStateInfo blockStateInfo = commandBuffer.getComponent(ref, blockStateInfoType);
            if (apbb == null || blockStateInfo == null)
                return;

            Ref<ChunkStore> sectionRef = blockStateInfo.getSectionRef();
            if (!sectionRef.isValid())
                return;

            Vector3i blockPos = new Vector3i();
            if (!blockStateInfo.fillWorldPos(commandBuffer, blockPos))
                return;
            int blockX = blockPos.x;
            int localY = blockPos.y;
            int blockZ = blockPos.z;

            BlockSection blockSection = commandBuffer.getComponent(sectionRef, BlockSection.getComponentType());
            if (blockSection == null)
                return;
            int blockId = blockSection.get(blockX, localY, blockZ);
            BlockType blockType = (BlockType) BlockType.getAssetMap().getAsset(blockId);
            if (blockType == null)
                return;

            BenchBlock benchBlock = commandBuffer.getComponent(ref, BenchBlock.getComponentType());
            if (benchBlock == null)
                return;

            World world = commandBuffer.getExternalData().getWorld();
            int rotationIndex = blockSection.getRotationIndex(blockX, localY, blockZ);
            initializeBenchSlots(apbb, world, benchBlock, blockStateInfo, blockX, localY, blockZ, blockType,
                    rotationIndex);
        }

        @Override
        public void onEntityRemove(
                @Nonnull Ref<ChunkStore> ref,
                @Nonnull RemoveReason reason,
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

            BlockStateInfo blockStateInfo = commandBuffer.getComponent(ref, blockStateInfoType);

            if (reason == RemoveReason.UNLOAD) {
                if (blockStateInfo != null)
                    blockStateInfo.markNeedsSaving();
                return;
            }

            AutoProcessingBenchBlock apbb = commandBuffer.getComponent(ref,
                    AutoProcessingBenchBlock.getComponentType());
            if (apbb == null || blockStateInfo == null)
                return;

            WindowManager.closeAndRemoveAll(apbb.getWindows());

            List<ItemStack> items = new ArrayList<>();
            collectDrops(apbb.getItemFuelContainer(), items);
            collectDrops(apbb.getItemInputContainer(), items);
            collectDrops(apbb.getItemOutputContainer(), items);
            if (items.isEmpty())
                return;

            Vector3i blockPos = new Vector3i();
            if (!blockStateInfo.fillWorldPos(commandBuffer, blockPos))
                return;
            int blockX = blockPos.x;
            int localY = blockPos.y;
            int blockZ = blockPos.z;

            World world = commandBuffer.getExternalData().getWorld();
            Store<EntityStore> entityStore = world.getEntityStore().getStore();
            Vector3d dropPos = new Vector3d(blockX + 0.5d, localY, blockZ + 0.5d);

            Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(entityStore, items, dropPos,
                    Rotation3f.ZERO);
            if (holders.length > 0)
                world.execute(() -> entityStore.addEntities(holders, AddReason.SPAWN));
        }

        private static void collectDrops(@Nullable ItemContainer container, @Nonnull List<ItemStack> drops) {
            if (container == null) {
                return;
            }
            drops.addAll(container.dropAllItemStacks());
        }
    }

    public static final class Tick extends EntityTickingSystem<ChunkStore> {

        @Override
        public Query<ChunkStore> getQuery() {
            return AutoProcessingBenchBlock.getComponentType();
        }

        @Override
        public void tick(
                float dt,
                int index,
                @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> commandBuffer) throws MatchException {

            AutoProcessingBenchBlock apbb = archetypeChunk.getComponent(index,
                    AutoProcessingBenchBlock.getComponentType());
            if (apbb == null)
                return;

            BenchBlock benchBlock = archetypeChunk.getComponent(index, BenchBlock.getComponentType());
            if (benchBlock == null)
                return;

            CraftingRecipe recipe = resolveRecipe(apbb, benchBlock);
            if (recipe == null) {
                resetProgress(apbb);
                return;
            }

            float recipeTime = recipe.getTimeSeconds();
            if (recipeTime <= 0.0f)
                recipeTime = 1.0f;

            BlockModule.BlockStateInfo blockStateInfo = archetypeChunk.getComponent(
                    index, BlockModule.BlockStateInfo.getComponentType());
            if (blockStateInfo == null)
                return;

            Vector3i benchPos = new Vector3i();
            if (!blockStateInfo.fillWorldPos(store, benchPos))
                return;

            int blockX = benchPos.x;
            int blockY = benchPos.y;
            int blockZ = benchPos.z;

            if (apbb.getInputProgress() >= recipeTime) {
                if (isReadyToCraft(apbb, recipe) && canFitOutput(apbb, recipe)) {
                    World world = store.getExternalData().getWorld();
                    completeCraft(apbb, recipe, world.getEntityStore().getStore(), blockX, blockY, blockZ);
                }
                return;
            }

            if (isReadyToCraft(apbb, recipe)) {
                if (!consumeFuelForDuration(apbb, dt)) {
                    return;
                }

                apbb.setActive(true);
                float newProgress = Math.min(apbb.getInputProgress() + dt, recipeTime);
                apbb.setInputProgress(newProgress);
                float normalizedProgress = newProgress / recipeTime;
                apbb.sendProgress(normalizedProgress);

                if (newProgress >= recipeTime
                        && canFitOutput(apbb, recipe)
                        && isReadyToCraft(apbb, recipe)) {
                    World world = store.getExternalData().getWorld();
                    completeCraft(apbb, recipe, world.getEntityStore().getStore(), blockX, blockY, blockZ);
                    apbb.sendProgress(0.0f);
                }
            } else {
                resetProgress(apbb);
            }
        }

        @Nullable
        private CraftingRecipe resolveRecipe(
                @Nonnull AutoProcessingBenchBlock apbb,
                @Nonnull BenchBlock benchBlock) {
            CraftingRecipe recipe = apbb.getRecipe();
            if (recipe != null && isReadyToCraft(apbb, recipe)) {
                return recipe;
            }

            CraftingRecipe matchingRecipe = findMatchingRecipe(apbb, benchBlock);
            apbb.setRecipeId(matchingRecipe != null ? matchingRecipe.getId() : null);
            return matchingRecipe;
        }

        private void resetProgress(@Nonnull AutoProcessingBenchBlock apbb) {
            if (apbb.getInputProgress() > 0.0f) {
                apbb.setInputProgress(0.0f);
                apbb.setActive(false);
                apbb.sendProgress(0.0f);
            }
        }

        @Nullable
        private CraftingRecipe findMatchingRecipe(
                @Nonnull AutoProcessingBenchBlock apbb,
                @Nonnull BenchBlock benchBlock) {
            if (apbb.getProcessingBench() == null) {
                return null;
            }

            List<CraftingRecipe> recipes = CraftingPlugin.getBenchRecipes(apbb.getProcessingBench());
            if (recipes.isEmpty()) {
                return null;
            }

            CraftingRecipe bestRecipe = null;
            int bestInputCount = -1;

            for (CraftingRecipe recipe : recipes) {
                if (recipe.isRestrictedByBenchTierLevel(apbb.getProcessingBench().getId(), benchBlock.getTierLevel())) {
                    continue;
                }

                List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(recipe);
                if (inputs.isEmpty()) {
                    continue;
                }

                List<MaterialQuantity> itemInputs = FluidRecipeUtil.itemParts(inputs);
                boolean itemsReady = itemInputs.isEmpty() || hasRequiredItems(apbb, itemInputs);
                if (!itemsReady || !hasEnoughFluidInputs(apbb, recipe)) {
                    continue;
                }

                if (inputs.size() > bestInputCount) {
                    bestInputCount = inputs.size();
                    bestRecipe = recipe;
                }
            }

            return bestRecipe;
        }

        private boolean isReadyToCraft(
                @Nonnull AutoProcessingBenchBlock apbb,
                @Nonnull CraftingRecipe recipe) {
            List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(recipe);
            if (inputs.isEmpty()) {
                return false;
            }

            List<MaterialQuantity> itemInputs = FluidRecipeUtil.itemParts(inputs);
            boolean itemsReady = itemInputs.isEmpty() || hasRequiredItems(apbb, itemInputs);
            return itemsReady && hasEnoughFluidInputs(apbb, recipe);
        }

        private boolean hasRequiredItems(
                @Nonnull AutoProcessingBenchBlock apbb,
                @Nonnull List<MaterialQuantity> itemInputs) {
            ItemContainer itemInputContainer = apbb.getItemInputContainer();
            return itemInputContainer != null
                    && !itemInputContainer.getSlotMaterialsToRemove(itemInputs, true, true).isEmpty();
        }

        private boolean hasEnoughFluidInputs(
                @Nonnull AutoProcessingBenchBlock apbb,
                @Nonnull CraftingRecipe recipe) {
            List<MaterialQuantity> fluidInputs = FluidRecipeUtil.fluidParts(CraftingManager.getInputMaterials(recipe));
            if (fluidInputs.isEmpty()) {
                return true;
            }

            FluidContainer fluidInputContainer = apbb.getFluidInputContainer();
            if (fluidInputContainer == null || fluidInputContainer.getCapacity() == 0) {
                return false;
            }

            for (MaterialQuantity requiredFluid : fluidInputs) {
                String fluidId = FluidRecipeUtil.fluidId(requiredFluid);
                if (fluidId == null) {
                    return false;
                }

                int totalFluidAmount = 0;
                for (short i = 0; i < fluidInputContainer.getCapacity(); i++) {
                    FluidStack fluidStack = fluidInputContainer.getFluidStack(i);
                    if (fluidStack != null && fluidId.equals(fluidStack.getFluidId())) {
                        totalFluidAmount += fluidStack.getAmount();
                    }
                }

                if (totalFluidAmount < FluidRecipeUtil.fluidMb(requiredFluid)) {
                    return false;
                }
            }

            return true;
        }

        private boolean canFitOutput(
                @Nonnull AutoProcessingBenchBlock apbb,
                @Nonnull CraftingRecipe recipe) {
            ItemContainer itemOutputContainer = apbb.getItemOutputContainer();
            if (itemOutputContainer == null) {
                return false;
            }

            MaterialQuantity[] rawOutputs = recipe.getOutputs();
            List<MaterialQuantity> outputs = rawOutputs != null ? List.of(rawOutputs) : List.of();

            List<MaterialQuantity> itemOutputs = FluidRecipeUtil.itemParts(outputs);
            List<ItemStack> itemStacks = new ArrayList<>();
            for (MaterialQuantity material : itemOutputs) {
                ItemStack stack = material.toItemStack();
                if (stack != null && !stack.isEmpty()) {
                    itemStacks.add(stack);
                }
            }

            if (!itemOutputContainer.canAddItemStacks(itemStacks, false, false)) {
                return false;
            }

            return canFitFluidOutputs(apbb, outputs);
        }

        private boolean canFitFluidOutputs(
                @Nonnull AutoProcessingBenchBlock apbb,
                @Nonnull List<MaterialQuantity> outputs) {
            List<MaterialQuantity> fluidOutputs = FluidRecipeUtil.fluidParts(outputs);
            if (fluidOutputs.isEmpty()) {
                return true;
            }

            FluidContainer fluidOutputContainer = apbb.getFluidOutputContainer();
            if (fluidOutputContainer == null || fluidOutputContainer.getCapacity() == 0) {
                return false;
            }

            for (MaterialQuantity fluidOutput : fluidOutputs) {
                String outputFluidId = FluidRecipeUtil.fluidId(fluidOutput);
                if (outputFluidId == null) {
                    return false;
                }

                int outputAmountMb = FluidRecipeUtil.fluidMb(fluidOutput);
                int available = 0;
                for (short i = 0; i < fluidOutputContainer.getCapacity(); i++) {
                    FluidStack slot = fluidOutputContainer.getFluidStack(i);
                    if (slot == null) {
                        available += apbb.getConfiguredFluidOutputSlotCapacityMb(i);
                    } else if (outputFluidId.equals(slot.getFluidId())) {
                        available += apbb.getConfiguredFluidOutputSlotCapacityMb(i) - slot.getAmount();
                    }
                }

                if (available < outputAmountMb) {
                    return false;
                }
            }

            return true;
        }

        private void completeCraft(
                @Nonnull AutoProcessingBenchBlock apbb,
                @Nonnull CraftingRecipe recipe,
                @Nonnull Store<EntityStore> entityStore,
                int blockX,
                int blockY,
                int blockZ) throws MatchException {
            List<MaterialQuantity> inputs = CraftingManager.getInputMaterials(recipe);
            List<MaterialQuantity> itemInputs = FluidRecipeUtil.itemParts(inputs);
            List<MaterialQuantity> fluidInputs = FluidRecipeUtil.fluidParts(inputs);

            MaterialQuantity[] rawOutputs = recipe.getOutputs();
            List<MaterialQuantity> outputList = rawOutputs != null ? List.of(rawOutputs) : List.of();
            List<MaterialQuantity> itemOutputs = FluidRecipeUtil.itemParts(outputList);
            List<MaterialQuantity> fluidOutputs = FluidRecipeUtil.fluidParts(outputList);

            ItemContainer itemInputContainer = apbb.getItemInputContainer();
            FluidContainer fluidInputContainer = apbb.getFluidInputContainer();
            ItemContainer itemOutputContainer = apbb.getItemOutputContainer();
            FluidContainer fluidOutputContainer = apbb.getFluidOutputContainer();

            if (!itemInputs.isEmpty()) {
                if (itemInputContainer == null) {
                    return;
                }

                ListTransaction<MaterialTransaction> removeTx = itemInputContainer.removeMaterials(
                        itemInputs,
                        true,
                        true,
                        true);
                if (!removeTx.succeeded()) {
                    return;
                }
            }

            if (fluidInputContainer != null) {
                for (MaterialQuantity fluidInput : fluidInputs) {
                    String fluidId = FluidRecipeUtil.fluidId(fluidInput);
                    if (fluidId == null) {
                        continue;
                    }

                    int remaining = FluidRecipeUtil.fluidMb(fluidInput);
                    for (short i = 0; i < fluidInputContainer.getCapacity() && remaining > 0; i++) {
                        FluidStack slot = fluidInputContainer.getFluidStack(i);
                        if (slot == null || !fluidId.equals(slot.getFluidId())) {
                            continue;
                        }

                        int consume = Math.min(remaining, slot.getAmount());
                        fluidInputContainer.removeFluidStackFromSlot(i, consume, false, false);
                        remaining -= consume;
                    }
                }
            }

            apbb.setInputProgress(0.0f);
            apbb.setActive(false);

            if (itemOutputContainer != null) {
                List<ItemStack> outputStacks = new ArrayList<>();
                for (MaterialQuantity material : itemOutputs) {
                    ItemStack stack = material.toItemStack();
                    if (stack != null && !stack.isEmpty()) {
                        outputStacks.add(stack);
                    }
                }

                ListTransaction<ItemStackTransaction> addTx = itemOutputContainer.addItemStacks(
                        outputStacks,
                        false,
                        false,
                        false);
                List<ItemStack> remainder = new ArrayList<>();
                for (ItemStackTransaction tx : addTx.getList()) {
                    ItemStack rem = tx.getRemainder();
                    if (rem != null && !rem.isEmpty()) {
                        remainder.add(rem);
                    }
                }

                if (!remainder.isEmpty()) {
                    Vector3d dropPos = new Vector3d(blockX + 0.5, blockY + 0.5, blockZ + 0.5);
                    Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(entityStore, remainder, dropPos,
                            Rotation3f.ZERO);
                    if (holders.length > 0) {
                        entityStore.addEntities(holders, AddReason.SPAWN);
                    }
                }
            }

            if (fluidOutputContainer != null) {
                for (MaterialQuantity fluidOutput : fluidOutputs) {
                    String outFluidId = FluidRecipeUtil.fluidId(fluidOutput);
                    if (outFluidId == null) {
                        continue;
                    }

                    addFluidOutputRespectingSlotCapacity(
                            apbb,
                            fluidOutputContainer,
                            outFluidId,
                            FluidRecipeUtil.fluidMb(fluidOutput));
                }
            }
        }

        private void addFluidOutputRespectingSlotCapacity(
                @Nonnull AutoProcessingBenchBlock apbb,
                @Nonnull FluidContainer fluidOutputContainer,
                @Nonnull String fluidId,
                int amountMb) throws MatchException {
            if (amountMb <= 0) {
                return;
            }

            int remaining = amountMb;
            for (short i = 0; i < fluidOutputContainer.getCapacity() && remaining > 0; i++) {
                FluidStack existing = fluidOutputContainer.getFluidStack(i);
                if (existing != null && !fluidId.equals(existing.getFluidId())) {
                    continue;
                }

                int slotCapacity = apbb.getConfiguredFluidOutputSlotCapacityMb(i);
                int existingAmount = existing != null ? existing.getAmount() : 0;
                int space = slotCapacity - existingAmount;
                if (space <= 0) {
                    continue;
                }

                int toAdd = Math.min(space, remaining);
                FluidStackSlotTransaction tx = fluidOutputContainer.addFluidStackToSlot(
                        i,
                        new FluidStack(fluidId, toAdd),
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

        private boolean consumeFuelForDuration(
                @Nonnull AutoProcessingBenchBlock apbb,
                float duration) {
            if (duration <= 0.0f) {
                return true;
            }
            if (!hasFuelSlots(apbb)) {
                return true;
            }

            float consumed = 0.0f;
            float fuelTime = apbb.getFuelTime();
            if (fuelTime > 0.0f) {
                float use = Math.min(fuelTime, duration);
                fuelTime -= use;
                consumed += use;
                apbb.setFuelTime(fuelTime);
            }

            while (consumed < duration && consumeOneFuel(apbb) >= 0) {
                fuelTime = apbb.getFuelTime();
                float use = Math.min(fuelTime, duration - consumed);
                fuelTime -= use;
                consumed += use;
                apbb.setFuelTime(fuelTime);
            }

            return consumed >= duration;
        }

        private int consumeOneFuel(@Nonnull AutoProcessingBenchBlock apbb) {
            if (apbb.getFuelPolicy() == FuelPolicy.ALL) {
                return consumeAllFuel(apbb);
            }
            return consumeAnyFuel(apbb);
        }

        private boolean hasFuelSlots(@Nonnull AutoProcessingBenchBlock apbb) {
            ItemContainer itemFuelContainer = apbb.getItemFuelContainer();
            FluidContainer fluidFuelContainer = apbb.getFluidFuelContainer();
            boolean hasItemFuel = itemFuelContainer != null && itemFuelContainer.getCapacity() > 0;
            boolean hasFluidFuel = fluidFuelContainer != null && fluidFuelContainer.getCapacity() > 0;
            return hasItemFuel || hasFluidFuel;
        }

        private int consumeAllFuel(@Nonnull AutoProcessingBenchBlock apbb) {
            ItemContainer itemFuelContainer = apbb.getItemFuelContainer();
            FluidContainer fluidFuelContainer = apbb.getFluidFuelContainer();
            boolean hasItemFuel = itemFuelContainer != null && itemFuelContainer.getCapacity() > 0;
            boolean hasFluidFuel = fluidFuelContainer != null && fluidFuelContainer.getCapacity() > 0;

            if (hasItemFuel && !hasRequiredItemFuelInEverySlot(apbb)) {
                return -1;
            }
            if (hasFluidFuel && !hasRequiredFluidFuelInEverySlot(apbb)) {
                return -1;
            }

            float itemGain = hasItemFuel ? consumeAllItemFuelAmount(apbb) : 0.0f;
            float fluidGain = hasFluidFuel ? consumeAllFluidFuelAmount(apbb) : 0.0f;
            float gained = hasItemFuel && hasFluidFuel
                    ? Math.min(itemGain, fluidGain)
                    : (hasItemFuel ? itemGain : fluidGain);

            if (gained <= 0.0f) {
                return -1;
            }

            apbb.setFuelTime(apbb.getFuelTime() + gained);
            return 0;
        }

        private boolean hasRequiredItemFuelInEverySlot(@Nonnull AutoProcessingBenchBlock apbb) {
            ItemContainer itemFuelContainer = apbb.getItemFuelContainer();
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

                String requiredType = apbb.getFuelSlotResourceTypeId(i, "Fuel");
                if (!hasResourceType(item, requiredType)) {
                    return false;
                }
            }

            return true;
        }

        private boolean hasRequiredFluidFuelInEverySlot(@Nonnull AutoProcessingBenchBlock apbb) {
            FluidContainer fluidFuelContainer = apbb.getFluidFuelContainer();
            if (fluidFuelContainer == null || fluidFuelContainer.getCapacity() == 0) {
                return false;
            }

            short capacity = fluidFuelContainer.getCapacity();
            for (short i = 0; i < capacity; i++) {
                FluidStack stack = fluidFuelContainer.getFluidStack(i);
                String requiredType = apbb.getConfiguredFluidFuelSlotResourceTypeId(i, "Glyphworks_Fluid_Fuel");
                if (stack == null || !isFuelFluid(stack, requiredType)) {
                    return false;
                }

                int requiredMb = fluidFuelCostMbPerTick(stack);
                if (requiredMb <= 0 || stack.getAmount() < requiredMb) {
                    return false;
                }
            }

            return true;
        }

        private float consumeAllItemFuelAmount(@Nonnull AutoProcessingBenchBlock apbb) {
            if (!hasRequiredItemFuelInEverySlot(apbb)) {
                return 0.0f;
            }

            ItemContainer itemFuelContainer = apbb.getItemFuelContainer();
            if (itemFuelContainer == null) {
                return 0.0f;
            }

            float totalGain = 0.0f;
            short capacity = itemFuelContainer.getCapacity();
            for (short i = 0; i < capacity; i++) {
                float slotGain = consumeOneItemFuelAmountFromSlot(apbb, i);
                if (slotGain <= 0.0f) {
                    return 0.0f;
                }
                totalGain += slotGain;
            }
            return totalGain;
        }

        private float consumeAllFluidFuelAmount(@Nonnull AutoProcessingBenchBlock apbb) {
            if (!hasRequiredFluidFuelInEverySlot(apbb)) {
                return 0.0f;
            }

            FluidContainer fluidFuelContainer = apbb.getFluidFuelContainer();
            if (fluidFuelContainer == null) {
                return 0.0f;
            }

            float totalGain = 0.0f;
            short capacity = fluidFuelContainer.getCapacity();
            for (short i = 0; i < capacity; i++) {
                float slotGain = consumeOneFluidFuelAmountFromSlot(apbb, i);
                if (slotGain <= 0.0f) {
                    return 0.0f;
                }
                totalGain += slotGain;
            }
            return totalGain;
        }

        private int consumeAnyFuel(@Nonnull AutoProcessingBenchBlock apbb) {
            int itemResult = consumeOneItemFuel(apbb);
            if (itemResult >= 0) {
                return itemResult;
            }

            return consumeOneFluidFuel(apbb);
        }

        private int consumeOneItemFuel(@Nonnull AutoProcessingBenchBlock apbb) {
            float amount = consumeOneItemFuelAmount(apbb);
            if (amount <= 0.0f) {
                return -1;
            }

            apbb.setFuelTime(apbb.getFuelTime() + amount);
            return 0;
        }

        private int consumeOneFluidFuel(@Nonnull AutoProcessingBenchBlock apbb) {
            float amount = consumeOneFluidFuelAmount(apbb);
            if (amount <= 0.0f) {
                return -1;
            }

            apbb.setFuelTime(apbb.getFuelTime() + amount);
            return 0;
        }

        private float consumeOneItemFuelAmount(@Nonnull AutoProcessingBenchBlock apbb) {
            ItemContainer itemFuelContainer = apbb.getItemFuelContainer();
            if (itemFuelContainer == null || itemFuelContainer.getCapacity() == 0) {
                return 0.0f;
            }

            short capacity = itemFuelContainer.getCapacity();
            for (short i = 0; i < capacity; i++) {
                ItemStack stack = itemFuelContainer.getItemStack(i);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }

                String requiredType = apbb.getFuelSlotResourceTypeId(i, "Fuel");
                ResourceTransaction transaction = itemFuelContainer.removeResource(
                        new ResourceQuantity(requiredType, 1),
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

        private float consumeOneFluidFuelAmount(@Nonnull AutoProcessingBenchBlock apbb) {
            FluidContainer fluidFuelContainer = apbb.getFluidFuelContainer();
            if (fluidFuelContainer == null || fluidFuelContainer.getCapacity() == 0) {
                return 0.0f;
            }

            for (short i = 0; i < fluidFuelContainer.getCapacity(); i++) {
                FluidStack stack = fluidFuelContainer.getFluidStack(i);
                if (stack == null) {
                    continue;
                }

                String requiredType = apbb.getConfiguredFluidFuelSlotResourceTypeId(i, "Glyphworks_Fluid_Fuel");
                if (!isFuelFluid(stack, requiredType)) {
                    continue;
                }

                int requiredMb = fluidFuelCostMbPerTick(stack);
                if (requiredMb <= 0 || stack.getAmount() < requiredMb) {
                    continue;
                }

                FluidStackSlotTransaction tx = fluidFuelContainer.removeFluidStackFromSlot(i, requiredMb, true, false);
                if (!tx.succeeded()) {
                    continue;
                }

                return 1.0f;
            }

            return 0.0f;
        }

        private float consumeOneItemFuelAmountFromSlot(
                @Nonnull AutoProcessingBenchBlock apbb,
                short slotIndex) {
            ItemContainer itemFuelContainer = apbb.getItemFuelContainer();
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

            String requiredType = apbb.getFuelSlotResourceTypeId(slotIndex, "Fuel");
            if (!hasResourceType(item, requiredType)) {
                return 0.0f;
            }

            double fuelQuality = item.getFuelQuality();
            if (fuelQuality <= 0.0) {
                return 0.0f;
            }

            itemFuelContainer.removeItemStackFromSlot(slotIndex, 1);
            return (float) fuelQuality;
        }

        private float consumeOneFluidFuelAmountFromSlot(
                @Nonnull AutoProcessingBenchBlock apbb,
                short slotIndex) {
            FluidContainer fluidFuelContainer = apbb.getFluidFuelContainer();
            if (fluidFuelContainer == null || slotIndex < 0 || slotIndex >= fluidFuelContainer.getCapacity()) {
                return 0.0f;
            }

            FluidStack stack = fluidFuelContainer.getFluidStack(slotIndex);
            String requiredType = apbb.getConfiguredFluidFuelSlotResourceTypeId(slotIndex, "Glyphworks_Fluid_Fuel");
            if (stack == null || !isFuelFluid(stack, requiredType)) {
                return 0.0f;
            }

            int requiredMb = fluidFuelCostMbPerTick(stack);
            if (requiredMb <= 0 || stack.getAmount() < requiredMb) {
                return 0.0f;
            }

            FluidStackSlotTransaction tx = fluidFuelContainer.removeFluidStackFromSlot(slotIndex, requiredMb, true,
                    false);
            if (!tx.succeeded()) {
                return 0.0f;
            }

            return 1.0f;
        }

        private int fluidFuelCostMbPerTick(@Nonnull FluidStack fluidStack) {
            Item item = resolveFluidItem(fluidStack);
            double fuelQuality = item != null ? item.getFuelQuality() : 0.0;
            if (fuelQuality <= 0.0) {
                return 10;
            }
            return Math.max(1, (int) Math.round(10 / fuelQuality));
        }

        private boolean isFuelFluid(
                @Nonnull FluidStack fluidStack,
                @Nonnull String requiredResourceTypeId) {
            return AutoProcessingBenchSystems.isFuelFluidForResource(fluidStack, requiredResourceTypeId);
        }

        @Nullable
        private Item resolveFluidItem(@Nonnull FluidStack fluidStack) {
            String itemId = FluidItemRegistry.resolveItemId(fluidStack.getFluidId());
            return itemId == null ? null : (Item) Item.getAssetMap().getAsset(itemId);
        }

        private boolean hasResourceType(
                @Nonnull Item item,
                @Nonnull String resourceTypeId) {
            return AutoProcessingBenchSystems.hasResourceType(item, resourceTypeId);
        }

    }
}

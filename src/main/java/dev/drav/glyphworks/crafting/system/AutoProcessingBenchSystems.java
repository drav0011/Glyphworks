package dev.drav.glyphworks.crafting.system;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
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
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.player.windows.WindowManager;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.crafting.component.AutoProcessingBenchBlock;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.util.DeprecatedChunkAccess;

public final class AutoProcessingBenchSystems {

    private AutoProcessingBenchSystems() {}

    public static final class Setup extends RefSystem<ChunkStore> {

        private final ComponentType<ChunkStore, BlockStateInfo> blockStateInfoType =
                BlockModule.BlockStateInfo.getComponentType();

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

            AutoProcessingBenchBlock apbb = commandBuffer.getComponent(ref, AutoProcessingBenchBlock.getComponentType());
            BlockStateInfo blockStateInfo = commandBuffer.getComponent(ref, blockStateInfoType);
            if (apbb == null || blockStateInfo == null)
                return;

            Ref<ChunkStore> chunkRef = blockStateInfo.getChunkRef();
            if (!chunkRef.isValid())
                return;

            BlockChunk blockChunk = commandBuffer.getComponent(chunkRef, BlockChunk.getComponentType());
            if (blockChunk == null)
                return;

            int blockIndex = blockStateInfo.getIndex();
            int localX = ChunkUtil.xFromBlockInColumn(blockIndex);
            int localY = ChunkUtil.yFromBlockInColumn(blockIndex);
            int localZ = ChunkUtil.zFromBlockInColumn(blockIndex);
            int blockX = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(), localX);
            int blockZ = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(), localZ);

            BlockSection blockSection = DeprecatedChunkAccess.getSection(blockChunk, localY);
            int blockId = blockSection.get(localX, localY, localZ);
            BlockType blockType = (BlockType) BlockType.getAssetMap().getAsset(blockId);
            if (blockType == null)
                return;

            BenchBlock benchBlock = commandBuffer.getComponent(ref, BenchBlock.getComponentType());
            if (benchBlock == null)
                return;

            World world = commandBuffer.getExternalData().getWorld();
            apbb.setupContainers(blockStateInfo, benchBlock, world, blockX, localY, blockZ, blockType);
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

            AutoProcessingBenchBlock apbb = commandBuffer.getComponent(ref, AutoProcessingBenchBlock.getComponentType());
            if (apbb == null || blockStateInfo == null)
                return;

            CombinedItemContainer combined = apbb.getItemContainer();
            if (combined == null)
                return;

            WindowManager.closeAndRemoveAll(apbb.getWindows());

            List<ItemStack> items = combined.dropAllItemStacks();
            if (items.isEmpty())
                return;

            Ref<ChunkStore> chunkRef = blockStateInfo.getChunkRef();
            if (!chunkRef.isValid())
                return;

            BlockChunk blockChunk = commandBuffer.getComponent(chunkRef, BlockChunk.getComponentType());
            if (blockChunk == null)
                return;

            int blockIndex = blockStateInfo.getIndex();
            int localX = ChunkUtil.xFromBlockInColumn(blockIndex);
            int localY = ChunkUtil.yFromBlockInColumn(blockIndex);
            int localZ = ChunkUtil.zFromBlockInColumn(blockIndex);
            int blockX = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(), localX);
            int blockZ = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(), localZ);

            World world = commandBuffer.getExternalData().getWorld();
            Store<EntityStore> entityStore = world.getEntityStore().getStore();
            Vector3d dropPos = new Vector3d(blockX + 0.5d, localY, blockZ + 0.5d);

            Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(entityStore, items, dropPos, Rotation3f.ZERO);
            if (holders.length > 0)
                world.execute(() -> entityStore.addEntities(holders, AddReason.SPAWN));
        }
    }

    public static final class Tick extends EntityTickingSystem<ChunkStore> {

        private static final String MANA_FLUID_ID = "Mana_Source";

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

            AutoProcessingBenchBlock apbb = archetypeChunk.getComponent(index, AutoProcessingBenchBlock.getComponentType());
            if (apbb == null)
                return;

            BenchBlock benchBlock = archetypeChunk.getComponent(index, BenchBlock.getComponentType());
            if (benchBlock == null)
                return;

            CraftingRecipe recipe = resolveRecipe(apbb, benchBlock.getTierLevel());
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

            int[] coords = resolveCoords(store, blockStateInfo);
            if (coords == null)
                return;

            int blockX = coords[0];
            int blockY = coords[1];
            int blockZ = coords[2];

            if (apbb.getCraftingProgress() >= recipeTime) {
                if (apbb.isReadyToCraft(recipe) && apbb.canFitOutput(recipe)) {
                    World world = store.getExternalData().getWorld();
                    apbb.completeCraft(recipe, world.getEntityStore().getStore(), blockX, blockY, blockZ);
                }
                return;
            }

            if (apbb.isReadyToCraft(recipe)) {
                float manaRate = apbb.getManaConsumptionRate();
                if (manaRate > 0.0f) {
                    FluidContainerComponent fluid = archetypeChunk.getComponent(
                            index, FluidContainerComponent.getComponentType());
                    if (fluid == null || fluid.isEmpty() || !MANA_FLUID_ID.equals(fluid.getFluidId()))
                        return;
                    int cost = Math.max(1, Math.round(manaRate));
                    if (fluid.getAmount() < cost)
                        return;
                    fluid.drain(cost);
                }

                apbb.setCrafting(true);
                float newProgress = Math.min(apbb.getCraftingProgress() + dt, recipeTime);
                apbb.setCraftingProgress(newProgress);
                apbb.sendProgress(newProgress / recipeTime);

                if (newProgress >= recipeTime && apbb.canFitOutput(recipe) && apbb.isReadyToCraft(recipe)) {
                    World world = store.getExternalData().getWorld();
                    apbb.completeCraft(recipe, world.getEntityStore().getStore(), blockX, blockY, blockZ);
                    apbb.sendProgress(0.0f);
                }
            } else {
                resetProgress(apbb);
            }
        }

        @Nullable
        private CraftingRecipe resolveRecipe(@Nonnull AutoProcessingBenchBlock apbb, int tierLevel) {
            CraftingRecipe current = apbb.getCurrentRecipe();
            if (current != null && apbb.isReadyToCraft(current))
                return current;
            CraftingRecipe found = apbb.findMatchingRecipe(tierLevel);
            if (found != current)
                apbb.setCurrentRecipe(found);
            return found;
        }

        private void resetProgress(@Nonnull AutoProcessingBenchBlock apbb) {
            if (apbb.getCraftingProgress() > 0.0f) {
                apbb.setCraftingProgress(0.0f);
                apbb.setCrafting(false);
                apbb.sendProgress(0.0f);
            }
        }

        @Nullable
        private int[] resolveCoords(
                @Nonnull Store<ChunkStore> store,
                @Nonnull BlockModule.BlockStateInfo blockStateInfo) {
            Ref<ChunkStore> chunkRef = blockStateInfo.getChunkRef();
            if (!chunkRef.isValid())
                return null;
            BlockChunk blockChunk = store.getComponent(chunkRef, BlockChunk.getComponentType());
            if (blockChunk == null)
                return null;
            int idx = blockStateInfo.getIndex();
            int localX = ChunkUtil.xFromBlockInColumn(idx);
            int localY = ChunkUtil.yFromBlockInColumn(idx);
            int localZ = ChunkUtil.zFromBlockInColumn(idx);
            return new int[] {
                ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(), localX),
                localY,
                ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(), localZ)
            };
        }
    }
}

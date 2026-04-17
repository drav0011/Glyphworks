package dev.drav.glyphworks.crafting.system;

import java.util.List;

import javax.annotation.Nonnull;

import org.joml.Vector3d;

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

import dev.drav.glyphworks.crafting.component.AutoCraftingBenchBlock;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.util.DeprecatedChunkAccess;

public final class AutoCraftingBenchSystems {

    private AutoCraftingBenchSystems() {}

    public static final class Setup extends RefSystem<ChunkStore> {

        private final ComponentType<ChunkStore, BlockStateInfo> blockStateInfoType =
                BlockModule.BlockStateInfo.getComponentType();

        @Override
        public Query<ChunkStore> getQuery() {
            return AutoCraftingBenchBlock.getComponentType();
        }

        @Override
        public void onEntityAdded(
                @Nonnull Ref<ChunkStore> ref,
                @Nonnull AddReason reason,
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

            AutoCraftingBenchBlock acbb = commandBuffer.getComponent(ref, AutoCraftingBenchBlock.getComponentType());
            BlockStateInfo blockStateInfo = commandBuffer.getComponent(ref, blockStateInfoType);
            if (acbb == null || blockStateInfo == null)
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

            int rotationIndex = blockSection.getRotationIndex(localX, localY, localZ);
            World world = commandBuffer.getExternalData().getWorld();
            acbb.setupContainers(blockStateInfo, world, blockX, localY, blockZ, blockType, rotationIndex);
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

            AutoCraftingBenchBlock acbb = commandBuffer.getComponent(ref, AutoCraftingBenchBlock.getComponentType());
            if (acbb == null || blockStateInfo == null)
                return;

            CombinedItemContainer combined = acbb.getItemContainer();
            if (combined == null)
                return;

            WindowManager.closeAndRemoveAll(acbb.getWindows());

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

        private static final float DEFAULT_RECIPE_TIME = 1.0f;
        private static final String MANA_FLUID_ID = "Glyphworks_Fluid_Mana";

        @Override
        public Query<ChunkStore> getQuery() {
            return AutoCraftingBenchBlock.getComponentType();
        }

        @Override
        public void tick(
                float dt,
                int index,
                @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> commandBuffer) throws MatchException {

            AutoCraftingBenchBlock acbb = archetypeChunk.getComponent(index, AutoCraftingBenchBlock.getComponentType());
            if (acbb == null)
                return;

            CraftingRecipe recipe = acbb.getLockedRecipe();
            if (recipe == null)
                return;

            // Standard crafting recipes declare timeSeconds == 0 (instant). We convert
            // them to timed cycles, so substitute a sensible default duration instead.
            float recipeTime = recipe.getTimeSeconds();
            if (recipeTime <= 0.0f)
                recipeTime = DEFAULT_RECIPE_TIME;

            BlockModule.BlockStateInfo blockStateInfo = archetypeChunk.getComponent(
                    index, BlockModule.BlockStateInfo.getComponentType());
            if (blockStateInfo == null)
                return;

            Ref<ChunkStore> chunkRef = blockStateInfo.getChunkRef();
            if (!chunkRef.isValid())
                return;

            BlockChunk blockChunk = store.getComponent(chunkRef, BlockChunk.getComponentType());
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

            int rotationIndex = blockSection.getRotationIndex(localX, localY, localZ);
            World world = store.getExternalData().getWorld();
            Store<EntityStore> entityStore = world.getEntityStore().getStore();

            if (acbb.getCraftingProgress() >= recipeTime) {
                if (acbb.isReadyToCraft() && acbb.canFitOutput())
                    acbb.completeCraft(entityStore, blockX, localY, blockZ, blockType, rotationIndex);
                return;
            }

            if (acbb.isReadyToCraft()) {
                float manaRate = acbb.getManaConsumptionRate();
                if (manaRate > 0.0f) {
                    FluidContainerComponent fluid = archetypeChunk.getComponent(
                            index, FluidContainerComponent.getComponentType());
                    if (fluid == null || fluid.isEmpty() || !MANA_FLUID_ID.equals(fluid.getFluidId()))
                        return;
                    int manaCost = Math.max(1, Math.round(manaRate));
                    if (fluid.getAmount() < manaCost)
                        return;
                    fluid.drain(manaCost);
                }

                acbb.setCrafting(true);
                float newProgress = Math.min(acbb.getCraftingProgress() + dt, recipeTime);
                acbb.setCraftingProgress(newProgress);
                acbb.sendProgress(newProgress / recipeTime);

                if (newProgress >= recipeTime && acbb.canFitOutput() && acbb.isReadyToCraft()) {
                    acbb.completeCraft(entityStore, blockX, localY, blockZ, blockType, rotationIndex);
                    acbb.sendProgress(0.0f);
                }
            } else {
                if (acbb.getCraftingProgress() > 0.0f) {
                    acbb.setCraftingProgress(0.0f);
                    acbb.setCrafting(false);
                }
            }
        }
    }
}

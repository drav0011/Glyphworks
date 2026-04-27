package dev.drav.glyphworks.crafting.system;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.crafting.component.AutoCraftingBenchBlock;
import dev.drav.glyphworks.crafting.component.AutoProcessingBenchBlock;
import dev.drav.glyphworks.util.BlockCoordsUtil;

public final class AutoCraftingBenchSystems {

    private AutoCraftingBenchSystems() {
    }

    public static final class Tick extends EntityTickingSystem<ChunkStore> {

        @Override
        public Query<ChunkStore> getQuery() {
            return Query.and(
                    AutoCraftingBenchBlock.getComponentType(),
                    AutoProcessingBenchBlock.getComponentType(),
                    BlockModule.BlockStateInfo.getComponentType());
        }

        @Override
        public void tick(
                float dt,
                int index,
                @Nonnull ArchetypeChunk<ChunkStore> chunk,
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> commandBuffer) throws MatchException {

            AutoCraftingBenchBlock acbb = chunk.getComponent(index, AutoCraftingBenchBlock.getComponentType());
            AutoProcessingBenchBlock apbb = chunk.getComponent(index, AutoProcessingBenchBlock.getComponentType());
            BlockStateInfo blockStateInfo = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType());
            if (acbb == null || apbb == null || blockStateInfo == null) {
                return;
            }

            String desiredRecipeId = normalizeRecipeId(acbb.getLockedRecipeId());
            String currentRecipeId = normalizeRecipeId(apbb.getRecipeId());
            if (Objects.equals(desiredRecipeId, currentRecipeId)) {
                if (desiredRecipeId != null) {
                    return;
                }
                ItemContainer inputContainer = apbb.getItemInputContainer();
                if (inputContainer == null || inputContainer.getCapacity() == 0) {
                    return;
                }
            }

            int[] coords = BlockCoordsUtil.resolveCoords(store, blockStateInfo);
            if (coords == null) {
                return;
            }

            World world = store.getExternalData().getWorld();
            int blockX = coords[0];
            int blockY = coords[1];
            int blockZ = coords[2];

            if (desiredRecipeId == null) {
                apbb.clearCurrentRecipe();
                AutoProcessingBenchSystems.applyExternalRecipeLayout(
                        apbb,
                        null,
                        blockStateInfo,
                        world,
                        blockX,
                        blockY,
                        blockZ);
                return;
            }

            apbb.setRecipeId(desiredRecipeId);
            CraftingRecipe selectedRecipe = acbb.getLockedRecipe();
            if (selectedRecipe == null || !desiredRecipeId.equals(selectedRecipe.getId())) {
                selectedRecipe = (CraftingRecipe) CraftingRecipe.getAssetMap().getAsset(desiredRecipeId);
            }
            AutoProcessingBenchSystems.applyExternalRecipeLayout(
                    apbb,
                    selectedRecipe,
                    blockStateInfo,
                    world,
                    blockX,
                    blockY,
                    blockZ);
        }

        @Nullable
        private static String normalizeRecipeId(@Nullable String recipeId) {
            if (recipeId == null || recipeId.isBlank()) {
                return null;
            }
            return recipeId;
        }
    }
}

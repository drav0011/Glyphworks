package dev.drav.glyphworks.crafting.system;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.crafting.component.AutoCraftingBenchBlock;

/**
 * Per-tick crafting system for {@link AutoCraftingBenchBlock}.
 *
 * <p>
 * Each tick, if a recipe is locked and the required ingredients are present:
 * <ol>
 * <li>Progress is advanced by {@code dt} seconds.</li>
 * <li>When progress reaches the recipe's time, one craft cycle completes:
 * inputs are consumed and outputs are placed in the output container.</li>
 * <li>If the output container is full when the cycle would complete, progress
 * is clamped at the recipe time and inputs are <em>not</em> consumed until
 * space becomes available.</li>
 * <li>If ingredients are removed mid-cycle, progress resets to 0.</li>
 * </ol>
 *
 * <p>
 * Standard workbench-type
 * {@link com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe}s
 * report {@code getTimeSeconds() == 0} (instant craft). For auto-processing
 * we assign them {@link #DEFAULT_RECIPE_TIME} so the bench has a measurable
 * cycle instead of exiting immediately.
 */
public final class AutoCraftingBenchSystem extends EntityTickingSystem<ChunkStore> {

    /** Processing time used for crafting recipes that declare 0 (instant) time. */
    private static final float DEFAULT_RECIPE_TIME = 1.0f;

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

        // Standard crafting recipes (workbench type) declare timeSeconds == 0 because
        // they are supposed to be instant. We convert them to processing recipes, so we
        // substitute a sensible default cycle time instead of exiting early.
        float recipeTime = recipe.getTimeSeconds();
        if (recipeTime <= 0.0f)
            recipeTime = DEFAULT_RECIPE_TIME;

        // Resolve block coordinates and context — required for completeCraft's item
        // ejection.
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

        BlockSection blockSection = blockChunk.getSectionAtBlockY(localY);
        int blockId = blockSection.get(localX, localY, localZ);
        BlockType blockType = (BlockType) BlockType.getAssetMap().getAsset(blockId);
        if (blockType == null)
            return;

        int rotationIndex = blockSection.getRotationIndex(localX, localY, localZ);
        World world = store.getExternalData().getWorld();
        Store<EntityStore> entityStore = world.getEntityStore().getStore();

        // ── Step 1: if progress is already at the recipe time (held because output
        // was full), try to complete now that space may have freed up.
        if (acbb.getCraftingProgress() >= recipeTime) {
            if (acbb.isReadyToCraft() && acbb.canFitOutput()) {
                acbb.completeCraft(entityStore, blockX, localY, blockZ, blockType, rotationIndex);
                // progress is now 0; fall through to start the next cycle below
            } else {
                return; // still waiting for output space or items were removed
            }
        }

        // ── Step 2: advance progress when all ingredients are present.
        if (acbb.isReadyToCraft()) {
            acbb.setCrafting(true);
            float newProgress = Math.min(acbb.getCraftingProgress() + dt, recipeTime);
            acbb.setCraftingProgress(newProgress);

            // Push normalised progress to open monitor windows.
            acbb.sendProgress(newProgress / recipeTime);

            // Complete immediately if we just reached the recipe time this tick.
            if (newProgress >= recipeTime && acbb.canFitOutput() && acbb.isReadyToCraft()) {
                acbb.completeCraft(entityStore, blockX, localY, blockZ, blockType, rotationIndex);
                // progress reset to 0 inside completeCraft
                acbb.sendProgress(0.0f);
            }
        } else {
            // Not enough ingredients — pause and reset progress.
            if (acbb.getCraftingProgress() > 0.0f) {
                acbb.setCraftingProgress(0.0f);
                acbb.setCrafting(false);
            }
        }
    }
}

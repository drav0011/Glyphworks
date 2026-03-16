package dev.drav.glyphworks.crafting.window;

import javax.annotation.Nonnull;

import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.window.CraftingWindow;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.window.CraftRecipeAction;
import com.hypixel.hytale.protocol.packets.window.WindowAction;
import com.hypixel.hytale.protocol.packets.window.WindowType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.logging.Logger;

import dev.drav.glyphworks.crafting.component.AutoCraftingBenchBlock;

/**
 * Recipe-selector window for {@link AutoCraftingBenchBlock}.
 *
 * <p>Uses the {@link WindowType#BasicCrafting} renderer so the player sees the
 * full workbench recipe browser. When the player clicks any recipe,
 * {@link AutoCraftingBenchBlock#setLockedRecipe} is called to lock it into the
 * bench and this window is closed automatically. The player can then re-open the
 * bench to see the {@link AutoCraftingBenchMonitorWindow}.
 */
public final class AutoCraftingBenchSelectWindow extends CraftingWindow {

    private static final Logger LOGGER = Logger.getLogger(AutoCraftingBenchSelectWindow.class.getName());

    @Nonnull
    private final AutoCraftingBenchBlock acbb;

    @Nonnull
    private final BlockModule.BlockStateInfo blockStateInfo;

    public AutoCraftingBenchSelectWindow(
            @Nonnull AutoCraftingBenchBlock acbb,
            @Nonnull BenchBlock benchBlock,
            @Nonnull BlockModule.BlockStateInfo blockStateInfo,
            int x, int y, int z, int rotationIndex,
            @Nonnull BlockType blockType) {
        super(WindowType.BasicCrafting, x, y, z, rotationIndex, blockType, benchBlock);
        this.acbb = acbb;
        this.blockStateInfo = blockStateInfo;
    }

    /**
     * Intercepts {@link CraftRecipeAction}: locks the chosen recipe into the bench
     * and closes this window. All other actions are silently ignored.
     */
    @Override
    public void handleAction(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull WindowAction action) {
        if (!(action instanceof CraftRecipeAction)) return;
        CraftRecipeAction craftAction = (CraftRecipeAction) action;
        String recipeId = craftAction.recipeId;
        if (recipeId == null) return;

        CraftingRecipe recipe = (CraftingRecipe) CraftingRecipe.getAssetMap().getAsset(recipeId);
        if (recipe == null) return;

        LOGGER.info("[AutoCraftingBench] Recipe selected: id=" + recipeId
                + ", output=" + (recipe.getOutputs() != null ? recipe.getOutputs() : "none")
                + ", time=" + recipe.getTimeSeconds() + "s");

        World world = store.getExternalData().getWorld();
        acbb.setLockedRecipe(recipeId, blockStateInfo, world, x, y, z, blockType, rotationIndex);

        // Signal the client's crafting state machine that the "craft" was accepted,
        // preventing a NullReferenceException when the window is closed immediately
        // after. Vanilla SimpleCraftingWindow always sends this before any state change.
        setBlockInteractionState(CRAFT_COMPLETED, world);

        if (bench.getCompletedSoundEventIndex() != 0) {
            SoundUtil.playSoundEvent2d(ref, bench.getCompletedSoundEventIndex(), SoundCategory.UI, store);
        }

        // Close the selector — player can re-open the bench to see the monitor window.
        this.close(ref, store);
    }
}

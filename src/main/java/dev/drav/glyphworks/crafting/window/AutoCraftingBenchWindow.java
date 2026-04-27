package dev.drav.glyphworks.crafting.window;

import javax.annotation.Nonnull;

import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.builtin.crafting.window.CraftingWindow;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.window.CraftRecipeAction;
import com.hypixel.hytale.protocol.packets.window.TierUpgradeAction;
import com.hypixel.hytale.protocol.packets.window.WindowAction;
import com.hypixel.hytale.protocol.packets.window.WindowType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.crafting.component.AutoCraftingBenchBlock;

/**
 * Recipe-selector window for {@link AutoCraftingBenchBlock}.
 *
 * <p>
 * Uses the {@link WindowType#BasicCrafting} renderer so the player sees the
 * full workbench recipe browser. When the player clicks any recipe,
 * {@link AutoCraftingBenchBlock#setLockedRecipe} is called to lock it into the
 * bench and this window is closed automatically. The player can then re-open
 * the bench to see the {@link AutoProcessingBenchWindow}.
 */
public final class AutoCraftingBenchWindow extends CraftingWindow {

    @Nonnull
    private final AutoCraftingBenchBlock acbb;

    @Nonnull
    public AutoCraftingBenchWindow(
            @Nonnull AutoCraftingBenchBlock acbb,
            @Nonnull BenchBlock benchBlock,
            int x, int y, int z, int rotationIndex,
            @Nonnull BlockType blockType) {
        super(WindowType.BasicCrafting, x, y, z, rotationIndex, blockType, benchBlock);
        this.acbb = acbb;
    }

    /**
     * Intercepts {@link CraftRecipeAction}: locks the chosen recipe into the bench
     * and closes this window.
     * Intercepts {@link TierUpgradeAction}: delegates to the vanilla upgrade flow.
     * All other actions are silently ignored.
     */
    @Override
    public void handleAction(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull WindowAction action) {
        switch (action) {
            case TierUpgradeAction _ -> {
                CraftingManager craftingManager = (CraftingManager) store.getComponent(ref,
                        CraftingManager.getComponentType());

                if (craftingManager != null && craftingManager.startTierUpgrade(ref, store, this)) {
                    World world = store.getExternalData().getWorld();

                    setBlockInteractionState(BENCH_UPGRADING, world);

                    if (bench.getBenchUpgradeSoundEventIndex() != 0) {
                        SoundUtil.playSoundEvent2d(ref, bench.getBenchUpgradeSoundEventIndex(), SoundCategory.UI,
                                store);
                    }
                }
            }
            case CraftRecipeAction craftAction -> {
                String recipeId = craftAction.recipeId;
                if (recipeId == null)
                    return;

                CraftingRecipe recipe = (CraftingRecipe) CraftingRecipe.getAssetMap().getAsset(recipeId);
                if (recipe == null)
                    return;

                World world = store.getExternalData().getWorld();
                acbb.setLockedRecipe(recipeId);

                // Signal the client's crafting state machine that the "craft" was accepted,
                // preventing a NullReferenceException when the window is closed immediately
                // after. Vanilla SimpleCraftingWindow always sends this before any state
                // change.
                setBlockInteractionState(CRAFT_COMPLETED, world);

                if (bench.getCompletedSoundEventIndex() != 0) {
                    SoundUtil.playSoundEvent2d(ref, bench.getCompletedSoundEventIndex(), SoundCategory.UI, store);
                }

                // Close the selector; the next open shows the shared processing window.
                this.close(ref, store);
            }
            default -> {
            }
        }
    }
}

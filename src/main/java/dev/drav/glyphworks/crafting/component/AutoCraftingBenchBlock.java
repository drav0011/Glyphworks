package dev.drav.glyphworks.crafting.component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;

/**
 * Selector-only component for auto crafting benches.
 *
 * <p>
 * Owns only recipe lock state. Runtime
 * containers and processing state live exclusively in
 * {@link AutoProcessingBenchBlock}.
 */
public final class AutoCraftingBenchBlock implements Component<ChunkStore> {

    @Nonnull
    public static final BuilderCodec<AutoCraftingBenchBlock> CODEC = BuilderCodec
            .builder(AutoCraftingBenchBlock.class, AutoCraftingBenchBlock::new)
            .append(
                    new KeyedCodec<>("Glyphworks_AutoCraftingBenchBlock_LockedRecipeId", Codec.STRING),
                    (b, v) -> b.lockedRecipeId = v,
                    b -> b.lockedRecipeId)
            .add()
            .build();

    @Nullable
    private String lockedRecipeId;

    @Nullable
    public AutoCraftingBenchBlock() {
    }

    public AutoCraftingBenchBlock(@Nonnull AutoCraftingBenchBlock other) {
        this.lockedRecipeId = other.lockedRecipeId;
    }

    public static ComponentType<ChunkStore, AutoCraftingBenchBlock> getComponentType() {
        return GlyphworksPlugin.get().getCraftingModule().getAutoCraftingBenchBlockComponentType();
    }

    public void setLockedRecipe(@Nullable String recipeId) {
        lockedRecipeId = (recipeId == null || recipeId.isBlank()) ? null : recipeId;
    }

    @Nullable
    public String getLockedRecipeId() {
        return lockedRecipeId;
    }

    @Nullable
    public CraftingRecipe getLockedRecipe() {
        if (lockedRecipeId == null || lockedRecipeId.isBlank()) {
            return null;
        }
        return (CraftingRecipe) CraftingRecipe.getAssetMap().getAsset(lockedRecipeId);
    }

    @Override
    @Nullable
    public Component<ChunkStore> clone() {
        return new AutoCraftingBenchBlock(this);
    }
}

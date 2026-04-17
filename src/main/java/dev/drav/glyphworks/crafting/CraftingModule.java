package dev.drav.glyphworks.crafting;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksModule;
import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.crafting.component.AutoCraftingBenchBlock;
import dev.drav.glyphworks.crafting.component.AutoProcessingBenchBlock;
import dev.drav.glyphworks.crafting.component.ManaLiquifierBlock;
import dev.drav.glyphworks.crafting.interaction.OpenAutoCraftingBenchInteraction;
import dev.drav.glyphworks.crafting.interaction.OpenAutoProcessingBenchInteraction;
import dev.drav.glyphworks.crafting.system.AutoCraftingBenchSystems;
import dev.drav.glyphworks.crafting.system.AutoProcessingBenchSystems;
import dev.drav.glyphworks.crafting.system.ManaLiquifierSystems;
import dev.drav.glyphworks.crafting.tests.AutoCraftingBenchFlowTests;
import dev.drav.glyphworks.crafting.tests.AutoCraftingBenchGridTests;
import dev.drav.glyphworks.crafting.tests.AutoCraftingBenchTests;
import dev.drav.glyphworks.crafting.tests.ManaLiquifierSystemTests;
import dev.drav.glyphworks.crafting.tests.component.AutoProcessingBenchBlockTests;

/**
 * Sub-plugin that owns the auto-crafting bench component,
 * its interaction codec, and the three crafting/bench systems.
 *
 * <p>
 * The Item grid type itself is registered by
 * {@link dev.drav.glyphworks.item.ItemModule}.
 */
public final class CraftingModule extends GlyphworksModule {

    private ComponentType<ChunkStore, AutoCraftingBenchBlock> autoCraftingBenchBlockComponentType;
    private ComponentType<ChunkStore, AutoProcessingBenchBlock> autoProcessingBenchBlockComponentType;
    private ComponentType<ChunkStore, ManaLiquifierBlock> manaLiquifierBlockComponentType;

    public ComponentType<ChunkStore, AutoCraftingBenchBlock> getAutoCraftingBenchBlockComponentType() {
        return autoCraftingBenchBlockComponentType;
    }

    public ComponentType<ChunkStore, AutoProcessingBenchBlock> getAutoProcessingBenchBlockComponentType() {
        return autoProcessingBenchBlockComponentType;
    }

    public ComponentType<ChunkStore, ManaLiquifierBlock> getManaLiquifierBlockComponentType() {
        return manaLiquifierBlockComponentType;
    }

    @Override
    public void setup(@Nonnull GlyphworksPlugin plugin) {
        this.autoCraftingBenchBlockComponentType = plugin.getChunkStoreRegistry().registerComponent(
                AutoCraftingBenchBlock.class,
                "Glyphworks_AutoCraftingBenchBlock",
                AutoCraftingBenchBlock.CODEC);

        this.autoProcessingBenchBlockComponentType = plugin.getChunkStoreRegistry().registerComponent(
                AutoProcessingBenchBlock.class,
                "Glyphworks_AutoProcessingBenchBlock",
                AutoProcessingBenchBlock.CODEC);

        plugin.getCodecRegistry(Interaction.CODEC).register(
                "OpenAutoCraftingBench",
                OpenAutoCraftingBenchInteraction.class,
                OpenAutoCraftingBenchInteraction.CODEC);

        plugin.getCodecRegistry(Interaction.CODEC).register(
                "OpenAutoProcessingBench",
                OpenAutoProcessingBenchInteraction.class,
                OpenAutoProcessingBenchInteraction.CODEC);

        this.manaLiquifierBlockComponentType = plugin.getChunkStoreRegistry().registerComponent(
                ManaLiquifierBlock.class,
                "Glyphworks_ManaLiquifierBlock",
                ManaLiquifierBlock.CODEC);
    }

    @Override
    public void start(@Nonnull GlyphworksPlugin plugin) {
        plugin.getChunkStoreRegistry().registerSystem(new AutoCraftingBenchSystems.Setup());
        plugin.getChunkStoreRegistry().registerSystem(new AutoCraftingBenchSystems.Tick());
        plugin.getChunkStoreRegistry().registerSystem(new AutoProcessingBenchSystems.Setup());
        plugin.getChunkStoreRegistry().registerSystem(new AutoProcessingBenchSystems.Tick());
        plugin.getChunkStoreRegistry().registerSystem(new ManaLiquifierSystems.Setup());
        plugin.getChunkStoreRegistry().registerSystem(new ManaLiquifierSystems.Tick());
    }

    @Override
    public void setupTests() {
        AutoCraftingBenchTests.register("crafting");
        AutoCraftingBenchFlowTests.register("crafting");
        AutoCraftingBenchGridTests.register("crafting");
        ManaLiquifierSystemTests.register("crafting");
        AutoProcessingBenchBlockTests.register("crafting");
    }
}

package dev.drav.glyphworks.crafting;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksModule;
import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.crafting.component.AutoCraftingBenchBlock;
import dev.drav.glyphworks.crafting.component.ManaLiquifierBlock;
import dev.drav.glyphworks.crafting.interaction.OpenAutoCraftingBenchInteraction;
import dev.drav.glyphworks.crafting.system.AutoCraftingBenchSetupSystem;
import dev.drav.glyphworks.crafting.system.AutoCraftingBenchSystem;
import dev.drav.glyphworks.crafting.system.ManaLiquifierSetupSystem;
import dev.drav.glyphworks.crafting.system.ManaLiquifierSystem;
import dev.drav.glyphworks.crafting.system.ProcessingBenchAutoStartSystem;
import dev.drav.glyphworks.crafting.tests.AutoCraftingBenchFlowTests;
import dev.drav.glyphworks.crafting.tests.AutoCraftingBenchGridTests;
import dev.drav.glyphworks.crafting.tests.AutoCraftingBenchTests;

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
    private ComponentType<ChunkStore, ManaLiquifierBlock> manaLiquifierBlockComponentType;

    public ComponentType<ChunkStore, AutoCraftingBenchBlock> getAutoCraftingBenchBlockComponentType() {
        return autoCraftingBenchBlockComponentType;
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

        plugin.getCodecRegistry(Interaction.CODEC).register(
                "OpenAutoCraftingBench",
                OpenAutoCraftingBenchInteraction.class,
                OpenAutoCraftingBenchInteraction.CODEC);

        this.manaLiquifierBlockComponentType = plugin.getChunkStoreRegistry().registerComponent(
                ManaLiquifierBlock.class,
                "Glyphworks_ManaLiquifierBlock",
                ManaLiquifierBlock.CODEC);
    }

    @Override
    public void start(@Nonnull GlyphworksPlugin plugin) {
        plugin.getChunkStoreRegistry().registerSystem(new ProcessingBenchAutoStartSystem());
        plugin.getChunkStoreRegistry().registerSystem(new AutoCraftingBenchSetupSystem());
        plugin.getChunkStoreRegistry().registerSystem(new AutoCraftingBenchSystem());
        plugin.getChunkStoreRegistry().registerSystem(new ManaLiquifierSetupSystem());
        plugin.getChunkStoreRegistry().registerSystem(new ManaLiquifierSystem());
    }

    @Override
    public void setupTests() {
        AutoCraftingBenchTests.register("crafting");
        AutoCraftingBenchFlowTests.register("crafting");
        AutoCraftingBenchGridTests.register("crafting");
    }
}

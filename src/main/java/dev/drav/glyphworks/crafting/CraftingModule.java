package dev.drav.glyphworks.crafting;

import javax.annotation.Nonnull;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ResourceType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksModule;
import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.crafting.component.AutoCraftingBenchBlock;
import dev.drav.glyphworks.crafting.component.AutoProcessingBenchBlock;
import dev.drav.glyphworks.crafting.event.ResourceTypeRegistry;
import dev.drav.glyphworks.crafting.interaction.OpenAutoCraftingBenchInteraction;
import dev.drav.glyphworks.crafting.interaction.OpenAutoProcessingBenchInteraction;
import dev.drav.glyphworks.crafting.system.AutoCraftingBenchSystems;
import dev.drav.glyphworks.crafting.system.AutoProcessingBenchSystems;
import dev.drav.glyphworks.crafting.tests.system.AutoCraftingBenchRecipeLockTests;
import dev.drav.glyphworks.crafting.tests.system.AutoProcessingBenchFlowTests;
import dev.drav.glyphworks.crafting.tests.system.AutoProcessingBenchFuelTests;
import dev.drav.glyphworks.crafting.tests.system.AutoProcessingBenchGridTests;
import dev.drav.glyphworks.crafting.tests.system.AutoProcessingBenchSetupTests;

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

    public ComponentType<ChunkStore, AutoCraftingBenchBlock> getAutoCraftingBenchBlockComponentType() {
        return autoCraftingBenchBlockComponentType;
    }

    public ComponentType<ChunkStore, AutoProcessingBenchBlock> getAutoProcessingBenchBlockComponentType() {
        return autoProcessingBenchBlockComponentType;
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

        plugin.getEventRegistry().register(LoadedAssetsEvent.class, Item.class,
                ResourceTypeRegistry::onItemsLoaded);

        plugin.getEventRegistry().register(LoadedAssetsEvent.class, ResourceType.class,
                ResourceTypeRegistry::onResourceTypesLoaded);
    }

    @Override
    public void start(@Nonnull GlyphworksPlugin plugin) {
        plugin.getChunkStoreRegistry().registerSystem(new AutoCraftingBenchSystems.Tick());
        plugin.getChunkStoreRegistry().registerSystem(new AutoProcessingBenchSystems.Setup());
        plugin.getChunkStoreRegistry().registerSystem(new AutoProcessingBenchSystems.Tick());
    }

    @Override
    public void setupTests() {
        AutoProcessingBenchSetupTests.register("crafting");
        AutoProcessingBenchFlowTests.register("crafting");
        AutoCraftingBenchRecipeLockTests.register("crafting");
        AutoProcessingBenchGridTests.register("crafting");
        AutoProcessingBenchFuelTests.register("crafting");
    }
}

package dev.drav.glyphworks;

import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.crafting.CraftingModule;
import dev.drav.glyphworks.crafting.component.AutoCraftingBenchBlock;
import dev.drav.glyphworks.crafting.component.ManaLiquifierBlock;
import dev.drav.glyphworks.fluid.FluidModule;
import dev.drav.glyphworks.item.ItemModule;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.component.FluidSinkComponent;
import dev.drav.glyphworks.fluid.component.FluidSourceComponent;
import dev.drav.glyphworks.fluid.component.FluidPipeComponent;
import dev.drav.glyphworks.fluid.component.FluidPlacerComponent;
import dev.drav.glyphworks.fluid.component.FluidRemoverComponent;
import dev.drav.glyphworks.item.component.ItemSinkComponent;
import dev.drav.glyphworks.item.component.ItemSourceComponent;
import dev.drav.glyphworks.item.component.BlockMinerComponent;
import dev.drav.glyphworks.item.component.BlockPlacerComponent;
import dev.drav.glyphworks.item.component.ItemPickerComponent;
import dev.drav.glyphworks.item.component.ItemDropperComponent;
import dev.drav.glyphworks.grid.GridModule;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.test.TestModule;
import dev.drav.glyphworks.test.runner.TestRunnerComponent;

public class GlyphworksPlugin extends JavaPlugin {
    private static final Logger LOGGER = Logger.getLogger(GlyphworksPlugin.class.getName());
    private static GlyphworksPlugin instance;

    private GridModule gridModule;
    private FluidModule fluidModule;
    private ItemModule itemModule;
    private CraftingModule craftingModule;
    private TestModule testModule;

    private List<GlyphworksModule> modules;

    public GlyphworksPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    public static GlyphworksPlugin get() {
        return instance;
    }

    @Override
    protected void setup() {
        LOGGER.info("[Glyphworks] setup()...");
        instance = this;

        gridModule = new GridModule();
        fluidModule = new FluidModule();
        itemModule = new ItemModule();
        craftingModule = new CraftingModule();
        testModule = new TestModule();
        modules = List.of(gridModule, fluidModule, itemModule, craftingModule, testModule);

        for (GlyphworksModule module : modules) {
            module.setup(this);
        }

        LOGGER.info("[Glyphworks] setup() complete.");
    }

    @Override
    protected void start() {
        LOGGER.info("[Glyphworks] start() — plugin is live.");
        for (GlyphworksModule module : modules) {
            module.start(this);
        }
    }

    // -------------------------------------------------------------------------
    // Grid graph state — delegated to GridModule
    // -------------------------------------------------------------------------

    /**
     * Returns all registered modules. Used by
     * {@link dev.drav.glyphworks.test.TestModule} to call setupTests().
     */
    public List<GlyphworksModule> getModules() {
        return modules;
    }

    @Nullable
    public GridGraph getGridGraph(World world, GridType type) {
        return gridModule.getGridGraph(world, type);
    }

    public GridGraph getOrCreateGridGraph(World world, GridType type) {
        return gridModule.getOrCreateGridGraph(world, type);
    }

    @Nonnull
    public Collection<GridGraph> getAllGridGraphs(World world) {
        return gridModule.getAllGridGraphs(world);
    }

    // -------------------------------------------------------------------------
    // Component type accessors — delegated to each sub-plugin
    // -------------------------------------------------------------------------

    public ComponentType<ChunkStore, GridComponent> getGridComponentType() {
        return gridModule.getGridComponentType();
    }

    public ComponentType<ChunkStore, AutoCraftingBenchBlock> getAutoCraftingBenchBlockComponentType() {
        return craftingModule.getAutoCraftingBenchBlockComponentType();
    }

    public ComponentType<ChunkStore, ManaLiquifierBlock> getManaLiquifierBlockComponentType() {
        return craftingModule.getManaLiquifierBlockComponentType();
    }

    public ComponentType<ChunkStore, FluidContainerComponent> getFluidContainerComponentType() {
        return fluidModule.getFluidContainerComponentType();
    }

    public ComponentType<ChunkStore, FluidPipeComponent> getFluidPipeComponentType() {
        return fluidModule.getFluidPipeComponentType();
    }

    public ComponentType<ChunkStore, FluidRemoverComponent> getFluidRemoverComponentType() {
        return fluidModule.getFluidRemoverComponentType();
    }

    public ComponentType<ChunkStore, FluidPlacerComponent> getFluidPlacerComponentType() {
        return fluidModule.getFluidPlacerComponentType();
    }

    public ComponentType<ChunkStore, FluidSourceComponent> getFluidSourceComponentType() {
        return fluidModule.getFluidSourceComponentType();
    }

    public ComponentType<ChunkStore, FluidSinkComponent> getFluidSinkComponentType() {
        return fluidModule.getFluidSinkComponentType();
    }

    public ComponentType<ChunkStore, ItemSourceComponent> getItemSourceComponentType() {
        return itemModule.getItemSourceComponentType();
    }

    public ComponentType<ChunkStore, ItemSinkComponent> getItemSinkComponentType() {
        return itemModule.getItemSinkComponentType();
    }

    public ComponentType<ChunkStore, BlockMinerComponent> getBlockMinerComponentType() {
        return itemModule.getBlockMinerComponentType();
    }

    public ComponentType<ChunkStore, BlockPlacerComponent> getBlockPlacerComponentType() {
        return itemModule.getBlockPlacerComponentType();
    }

    public ComponentType<ChunkStore, ItemPickerComponent> getItemPickerComponentType() {
        return itemModule.getItemPickerComponentType();
    }

    public ComponentType<ChunkStore, ItemDropperComponent> getItemDropperComponentType() {
        return itemModule.getItemDropperComponentType();
    }

    public ComponentType<EntityStore, TestRunnerComponent> getTestRunnerComponentType() {
        return testModule.getTestRunnerComponentType();
    }
}

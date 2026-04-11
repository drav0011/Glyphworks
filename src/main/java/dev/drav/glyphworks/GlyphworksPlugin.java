package dev.drav.glyphworks;

import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import dev.drav.glyphworks.crafting.CraftingModule;
import dev.drav.glyphworks.fluid.FluidModule;
import dev.drav.glyphworks.grid.GridModule;
import dev.drav.glyphworks.item.ItemModule;
import dev.drav.glyphworks.test.TestModule;

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

    // -------------------------------------------------------------------------
    // Module accessors
    // -------------------------------------------------------------------------

    public GridModule getGridModule() {
        return gridModule;
    }

    public FluidModule getFluidModule() {
        return fluidModule;
    }

    public ItemModule getItemModule() {
        return itemModule;
    }

    public CraftingModule getCraftingModule() {
        return craftingModule;
    }

    public TestModule getTestModule() {
        return testModule;
    }
}

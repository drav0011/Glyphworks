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

public class GlyphworksPlugin extends JavaPlugin {
    private static final Logger LOGGER = Logger.getLogger(GlyphworksPlugin.class.getName());
    private static GlyphworksPlugin instance;

    private GridModule gridModule;
    private FluidModule fluidModule;
    private ItemModule itemModule;
    private CraftingModule craftingModule;

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
        modules = List.of(gridModule, fluidModule, itemModule, craftingModule);

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
    // Module accessors
    // -------------------------------------------------------------------------

    public List<GlyphworksModule> getModules() {
        return modules;
    }

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
}

package dev.drav.glyphworks.test;

import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.test.command.TestCommands;
import dev.drav.glyphworks.test.headless.HeadlessTestLauncher;
import dev.drav.glyphworks.test.runner.TestRunnerComponent;
import dev.drav.glyphworks.test.runner.TestRunnerSystem;

public final class GlyphworksTestPlugin extends JavaPlugin {
    private static final Logger LOGGER = Logger.getLogger(GlyphworksTestPlugin.class.getName());
    private static GlyphworksTestPlugin instance;

    private ComponentType<EntityStore, TestRunnerComponent> testRunnerComponentType;

    public GlyphworksTestPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    public static GlyphworksTestPlugin get() {
        return instance;
    }

    @Override
    protected void setup() {
        LOGGER.info("[GlyphworksTests] setup()...");
        instance = this;

        this.testRunnerComponentType = getEntityStoreRegistry().registerComponent(
                TestRunnerComponent.class, TestRunnerComponent::new);

        TestRegistrations.registerAll();
        getCommandRegistry().registerCommand(new TestCommands());

        LOGGER.info("[GlyphworksTests] setup() complete.");
    }

    @Override
    protected void start() {
        LOGGER.info("[GlyphworksTests] start() — plugin is live.");

        getEntityStoreRegistry().registerSystem(new TestRunnerSystem());

        if (HeadlessTestLauncher.isEnabled()) {
            Universe.get().getUniverseReady().thenRun(HeadlessTestLauncher::launch);
        }
    }

    public ComponentType<EntityStore, TestRunnerComponent> getTestRunnerComponentType() {
        return testRunnerComponentType;
    }
}

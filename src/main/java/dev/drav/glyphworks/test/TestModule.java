package dev.drav.glyphworks.test;

import java.util.List;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.world.events.AllWorldsLoadedEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.GlyphworksModule;
import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.test.command.TestCommands;
import dev.drav.glyphworks.test.headless.HeadlessTestLauncher;
import dev.drav.glyphworks.test.runner.TestRunnerComponent;
import dev.drav.glyphworks.test.runner.TestRunnerSystem;
import dev.drav.glyphworks.test.tests.TestFrameworkTests;

/**
 * Sub-plugin that owns the in-game test framework: the runner component,
 * all registered test suites, the {@code /gtest} command, and the runner
 * system.
 */
public final class TestModule extends GlyphworksModule {

    private ComponentType<EntityStore, TestRunnerComponent> testRunnerComponentType;

    public ComponentType<EntityStore, TestRunnerComponent> getTestRunnerComponentType() {
        return testRunnerComponentType;
    }

    private List<GlyphworksModule> modules;

    @Override
    public void setup(@Nonnull GlyphworksPlugin plugin) {
        this.testRunnerComponentType = plugin.getEntityStoreRegistry().registerComponent(
                TestRunnerComponent.class, TestRunnerComponent::new);

        // Let each module register its own tests.
        this.modules = plugin.getModules();
        for (GlyphworksModule module : modules) {
            module.setupTests();
        }

        plugin.getCommandRegistry().registerCommand(new TestCommands());
    }

    @Override
    public void start(@Nonnull GlyphworksPlugin plugin) {
        plugin.getEntityStoreRegistry().registerSystem(new TestRunnerSystem());

        // If headless test properties are set, fire the launcher once all worlds are ready.
        if (HeadlessTestLauncher.isEnabled()) {
            HytaleServer.get().getEventBus().register(AllWorldsLoadedEvent.class, event -> {
                HeadlessTestLauncher.launch();
            });
        }
    }

    @Override
    public void setupTests() {
        TestFrameworkTests.register("smoke");
    }
}

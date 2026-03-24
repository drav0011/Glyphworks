package dev.drav.glyphworks.test;

import java.util.List;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.GlyphworksModule;
import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.test.command.GlyphTestCommand;
import dev.drav.glyphworks.test.tests.BasicBlockTests;
import dev.drav.glyphworks.test.tests.FluidTests;

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

        plugin.getCommandRegistry().registerCommand(new GlyphTestCommand());
    }

    @Override
    public void start(@Nonnull GlyphworksPlugin plugin) {
        plugin.getEntityStoreRegistry().registerSystem(new TestRunnerSystem());
    }

    @Override
    public void setupTests() {
        BasicBlockTests.register();
        FluidTests.register();
    }
}

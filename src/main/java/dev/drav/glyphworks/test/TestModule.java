package dev.drav.glyphworks.test;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.GlyphworksModule;
import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.test.command.GlyphTestCommand;
import dev.drav.glyphworks.test.suite.BasicBlockTests;
import dev.drav.glyphworks.test.suite.FluidTests;
import dev.drav.glyphworks.test.suite.PipeConnectionTests;

/**
 * Sub-plugin that owns the in-game test framework: the runner component,
 * all registered test suites, the {@code /gtest} command, and the runner system.
 */
public final class TestModule extends GlyphworksModule {

    private ComponentType<EntityStore, TestRunnerComponent> testRunnerComponentType;

    public ComponentType<EntityStore, TestRunnerComponent> getTestRunnerComponentType() {
        return testRunnerComponentType;
    }

    @Override
    public void setup(@Nonnull GlyphworksPlugin plugin) {
        this.testRunnerComponentType = plugin.getEntityStoreRegistry().registerComponent(
                TestRunnerComponent.class, TestRunnerComponent::new);

        BasicBlockTests.register();
        FluidTests.register();
        PipeConnectionTests.register();

        plugin.getCommandRegistry().registerCommand(new GlyphTestCommand());
    }

    @Override
    public void start(@Nonnull GlyphworksPlugin plugin) {
        plugin.getEntityStoreRegistry().registerSystem(new TestRunnerSystem());
    }
}

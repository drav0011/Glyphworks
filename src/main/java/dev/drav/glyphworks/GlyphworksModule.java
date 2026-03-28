package dev.drav.glyphworks;

import javax.annotation.Nonnull;

/**
 * Modular registration unit loaded by {@link GlyphworksPlugin}.
 * <p>
 * Each module owns its domain's component types, systems, event listeners
 * and commands. The main plugin creates every module, then delegates the
 * {@link #setup} and {@link #start} lifecycle calls to them in order, while
 * preserving the same public API on {@link GlyphworksPlugin} for all existing
 * callers.
 */
public abstract class GlyphworksModule {

    /**
     * Called during {@link GlyphworksPlugin#setup()}. Register components, events
     * and commands here.
     */
    public abstract void setup(@Nonnull GlyphworksPlugin plugin);

    /**
     * Called during {@link GlyphworksPlugin#start()}. Register ticking systems
     * here. Defaults to a no-op.
     */
    public void start(@Nonnull GlyphworksPlugin plugin) {
    }

    /**
     * Override to register module-specific test suites. Called from
     * {@link dev.drav.glyphworks.test.TestModule}.
     */
    public void setupTests() {
    }
}

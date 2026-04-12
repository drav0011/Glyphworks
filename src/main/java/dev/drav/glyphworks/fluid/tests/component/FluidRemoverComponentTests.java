package dev.drav.glyphworks.fluid.tests.component;

import org.joml.Vector3i;

import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.fluid.component.FluidRemoverComponent;
import dev.drav.glyphworks.fluid.tests.FluidTestUtil;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Two-suite persistence test for {@link FluidRemoverComponent}.
 *
 * <p>
 * Verifies that the JSON-configured {@code targetNormal} (Down by default)
 * and {@code targetPosition} survive a server restart.
 *
 * <pre>
 *   # Phase 1
 *   $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.persistence.phase=setup -Dglyphworks.test.persistence.suite=fluid_remover_persistence_setup"
 *   ./gradlew runServer
 *
 *   # Phase 2
 *   $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.persistence.phase=assert -Dglyphworks.test.persistence.suite=fluid_remover_persistence_assert"
 *   ./gradlew runServer
 * </pre>
 */
public final class FluidRemoverComponentTests {

    private static final String REMOVER_ID = "Glyphworks_Fluid_Remover";

    private FluidRemoverComponentTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSetupSuite());
        TestRegistry.register(moduleId, buildAssertSuite());
    }

    // ── Setup suite ───────────────────────────────────────────────────────────

    private static TestSuite buildSetupSuite() {
        return new TestSuite("fluid_remover_persistence_setup")
                .persistence("setup")
                .test(setupRemoverState());
    }

    private static TestCase setupRemoverState() {
        return new TestCase("fluid_remover_persists_target", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    FluidTestUtil.setBlockWithRotation(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(),
                            REMOVER_ID, FluidTestUtil.ROTATION_DOWN);
                }))
                .step(Steps.waitUntil(ctx -> getRemover(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null,
                        ctx -> 5 * ctx.getWorld().getTps(), "remover block entity initialised"))
                .step(Steps.assertThat(ctx -> {
                    FluidRemoverComponent frc = getRemover(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return frc != null
                            && BlockFace.Down == frc.getTargetNormal()
                            && new Vector3i(0, 0, 0).equals(frc.getTargetPosition());
                }, "baseline: remover targetNormal=Down, targetPosition=(0,0,0) before server stop"));
    }

    // ── Assert suite ──────────────────────────────────────────────────────────

    private static TestSuite buildAssertSuite() {
        return new TestSuite("fluid_remover_persistence_assert")
                .persistence("assert")
                .test(assertRemoverState());
    }

    private static TestCase assertRemoverState() {
        return new TestCase("fluid_remover_persists_target", 3, 3, 3)
                .step(Steps.waitUntil(ctx -> getRemover(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null,
                        ctx -> 5 * ctx.getWorld().getTps(), "remover reloaded after restart"))
                .step(Steps.assertThat(ctx -> {
                    FluidRemoverComponent frc = getRemover(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return frc != null
                            && BlockFace.Down == frc.getTargetNormal()
                            && new Vector3i(0, 0, 0).equals(frc.getTargetPosition());
                }, "FluidRemoverComponent persists targetNormal and targetPosition across server restart"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static FluidRemoverComponent getRemover(World world, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(world.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return world.getChunkStore().getStore().getComponent(lu.blockRef(), FluidRemoverComponent.getComponentType());
    }
}

package dev.drav.glyphworks.item.tests.component;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.item.component.ItemSourceComponent;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Two-suite persistence test for {@link ItemSourceComponent}.
 *
 * <pre>
 *   # Phase 1
 *   $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.persistence.phase=setup -Dglyphworks.test.persistence.suite=item_source_persistence_setup"
 *   ./gradlew runServer
 *
 *   # Phase 2
 *   $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.persistence.phase=assert -Dglyphworks.test.persistence.suite=item_source_persistence_assert"
 *   ./gradlew runServer
 * </pre>
 */
public final class ItemSourceComponentTests {

    private static final String SOURCE_ID    = "Glyphworks_Item_Source";
    private static final String CHANGED_ITEM = "Rock_Basalt";

    private ItemSourceComponentTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSetupSuite());
        TestRegistry.register(moduleId, buildAssertSuite());
    }

    // ── Setup suite ───────────────────────────────────────────────────────────

    private static TestSuite buildSetupSuite() {
        return new TestSuite("item_source_persistence_setup")
                .test(setupSourceState());
    }

    private static TestCase setupSourceState() {
        return new TestCase("item_source_persists_item_id", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    ctx.getWorld().setBlock(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), SOURCE_ID);
                }))
                .step(Steps.waitUntil(ctx -> getSource(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null,
                        ctx -> 5 * ctx.getWorld().getTps(), "source block entity initialised"))
                .step(Steps.run(ctx -> {
                    ItemSourceComponent isc = getSource(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (isc != null)
                        isc.setItemId(CHANGED_ITEM);
                }))
                .step(Steps.assertThat(ctx -> {
                    ItemSourceComponent isc = getSource(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return isc != null && CHANGED_ITEM.equals(isc.getItemId());
                }, "baseline: source itemId is " + CHANGED_ITEM + " before server stop"));
    }

    // ── Assert suite ──────────────────────────────────────────────────────────

    private static TestSuite buildAssertSuite() {
        return new TestSuite("item_source_persistence_assert")
                .test(assertSourceState());
    }

    private static TestCase assertSourceState() {
        return new TestCase("item_source_persists_item_id", 3, 3, 3)
                .step(Steps.waitUntil(ctx -> getSource(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null,
                        ctx -> 5 * ctx.getWorld().getTps(), "source reloaded after restart"))
                .step(Steps.assertThat(ctx -> {
                    ItemSourceComponent isc = getSource(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return isc != null && CHANGED_ITEM.equals(isc.getItemId());
                }, "ItemSourceComponent persists itemId across server restart"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static ItemSourceComponent getSource(World world, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(world.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return world.getChunkStore().getStore().getComponent(lu.blockRef(), ItemSourceComponent.getComponentType());
    }
}

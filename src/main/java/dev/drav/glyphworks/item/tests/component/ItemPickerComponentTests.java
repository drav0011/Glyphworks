package dev.drav.glyphworks.item.tests.component;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.item.component.ItemPickerComponent;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Two-suite persistence test for {@link ItemPickerComponent}.
 *
 * <pre>
 *   # Phase 1
 *   $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.persistence.phase=setup -Dglyphworks.test.persistence.suite=item_picker_persistence_setup"
 *   ./gradlew runServer
 *
 *   # Phase 2
 *   $env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.persistence.phase=assert -Dglyphworks.test.persistence.suite=item_picker_persistence_assert"
 *   ./gradlew runServer
 * </pre>
 */
public final class ItemPickerComponentTests {

    private static final String PICKER_ID     = "Glyphworks_Item_Picker";
    private static final int    CHANGED_RADIUS = 7;

    private ItemPickerComponentTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSetupSuite());
        TestRegistry.register(moduleId, buildAssertSuite());
    }

    // ── Setup suite ───────────────────────────────────────────────────────────

    private static TestSuite buildSetupSuite() {
        return new TestSuite("item_picker_persistence_setup")
                .test(setupPickerState());
    }

    private static TestCase setupPickerState() {
        return new TestCase("item_picker_persists_radius", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    ctx.getWorld().setBlock(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), PICKER_ID);
                }))
                .step(Steps.waitUntil(ctx -> getPicker(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null,
                        ctx -> 5 * ctx.getWorld().getTps(), "picker block entity initialised"))
                .step(Steps.run(ctx -> {
                    ItemPickerComponent ipc = getPicker(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (ipc != null)
                        ipc.setRadius(CHANGED_RADIUS);
                }))
                .step(Steps.assertThat(ctx -> {
                    ItemPickerComponent ipc = getPicker(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return ipc != null && ipc.getRadius() == CHANGED_RADIUS;
                }, "baseline: picker radius is " + CHANGED_RADIUS + " before server stop"));
    }

    // ── Assert suite ──────────────────────────────────────────────────────────

    private static TestSuite buildAssertSuite() {
        return new TestSuite("item_picker_persistence_assert")
                .test(assertPickerState());
    }

    private static TestCase assertPickerState() {
        return new TestCase("item_picker_persists_radius", 3, 3, 3)
                .step(Steps.waitUntil(ctx -> getPicker(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null,
                        ctx -> 5 * ctx.getWorld().getTps(), "picker reloaded after restart"))
                .step(Steps.assertThat(ctx -> {
                    ItemPickerComponent ipc = getPicker(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return ipc != null && ipc.getRadius() == CHANGED_RADIUS;
                }, "ItemPickerComponent persists radius across server restart"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static ItemPickerComponent getPicker(World world, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(world.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return world.getChunkStore().getStore().getComponent(lu.blockRef(), ItemPickerComponent.getComponentType());
    }
}

package dev.drav.glyphworks.util;

import java.util.EnumSet;
import java.util.List;

import org.joml.Vector3d;
import org.joml.Vector3i;

import com.hypixel.hytale.builtin.triggervolumes.EntityTargetType;
import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.effect.builtin.rules.NoBuildRule;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.builtin.triggervolumes.shape.BoxShape;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.item.ItemTestUtil;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "trigger_volume_guard"} — machines must honour the always-active
 * deny rules a trigger volume projects over their target cell.
 *
 * <p>
 * Mirrors the layout of {@code block_placer_system}: the placer sits at the
 * centre of a 3×3×3 area and targets the cell directly below it. Here that
 * target cell is wrapped in a volume carrying a {@code NoBuild} rule, so the
 * placer must leave both the world and its own container untouched.
 */
public final class TriggerVolumeGuardTests {

    private static final String PLACER_ID = "Glyphworks_Block_Placer";
    private static final String BLOCK_ITEM_ID = "Rock_Stone";
    private static final String VOLUME_ID = "glyphworks_test_no_build";

    private TriggerVolumeGuardTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("trigger_volume_guard")
                .test(placerRespectsNoBuildVolume());
    }

    // -------------------------------------------------------------------------
    // Test: a NoBuild volume over the target cell stops the placer
    // -------------------------------------------------------------------------

    private static TestCase placerRespectsNoBuildVolume() {
        return new TestCase("placer_respects_no_build_volume", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ctx.getWorld().setBlock(bx, by, bz, PLACER_ID);
                    registerNoBuildVolume(ctx.getWorld(), bx, by - 1, bz);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    if (icb != null)
                        ItemTestUtil.seedItems(icb.getItemContainer(), BLOCK_ITEM_ID, 1);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    // Target cell must still be empty.
                    if (ItemTestUtil.getBlockId(ctx.getWorld(), bx, by - 1, bz) != 0)
                        return false;
                    // And the item must not have been consumed.
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    return icb != null
                            && ItemTestUtil.countItemsOfType(icb.getItemContainer(), BLOCK_ITEM_ID) == 1;
                }, "BlockPlacerSystem places nothing inside a NoBuild trigger volume"))
                .step(Steps.afterFinish(ctx -> {
                    TriggerVolumeManager manager = resolveManager(ctx.getWorld());
                    if (manager != null)
                        manager.unregister(VOLUME_ID);
                }));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Wraps the single cell at {@code (x, y, z)} in a rules-active NoBuild volume. */
    private static void registerNoBuildVolume(World world, int x, int y, int z) {
        TriggerVolumeManager manager = resolveManager(world);
        if (manager == null) {
            throw new IllegalStateException(
                    "TriggerVolumes plugin is not loaded — the guard cannot be exercised.");
        }

        // BoxShape bounds are local to the entry position, so centre the entry on
        // the cell and size the box to that cell alone. Anything larger would also
        // enclose the placer sitting directly above, and the test would then pass
        // even if the guard wrongly consulted the machine's own position.
        VolumeEntry entry = new VolumeEntry(
                VOLUME_ID,
                world.getName(),
                new Vector3d(x + 0.5d, y + 0.5d, z + 0.5d),
                new BoxShape(new Vector3d(-0.5, -0.5, -0.5), new Vector3d(0.5, 0.5, 0.5)),
                List.of(),
                EnumSet.of(EntityTargetType.PLAYER),
                true);

        entry.getRules().add(new NoBuildRule());
        entry.setRulesActive(true);
        entry.setEnabled(true);

        manager.register(VOLUME_ID, entry);
    }

    private static TriggerVolumeManager resolveManager(World world) {
        TriggerVolumesPlugin plugin = TriggerVolumesPlugin.get();
        if (plugin == null) {
            return null;
        }
        return world.getEntityStore().getStore().getResource(plugin.getManagerResourceType());
    }
}

package dev.drav.glyphworks.item.system;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;

import dev.drav.glyphworks.item.ItemTestUtil;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "item_picker_system"} — integration tests for
 * {@link dev.drav.glyphworks.item.system.ItemPickerSystem}.
 *
 * <p>
 * The picker block is placed at the centre of a 3×3×3 area:
 * {@code (ox+1, oy+1, oz+1)}. Its default pickup radius is 3 blocks, so a
 * half-range of 3.5 blocks is used when checking AABB containment.
 *
 * <p>
 * Item entities are spawned via {@link ItemTestUtil#spawnItemEntity} which
 * schedules the spawn through {@link com.hypixel.hytale.server.core.universe.world.World#execute}.
 * The entity is present by the first tick after the step completes.
 */
public final class ItemPickerSystemTests {

    private static final String PICKER_ID = "Glyphworks_Item_Picker";
    private static final String ITEM_ID = "Rock_Stone_Cobble";

    private ItemPickerSystemTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("item_picker_system")
                .test(pickerCollectsNearbyItem())
                .test(pickerIgnoresDistantItem());
    }

    // -------------------------------------------------------------------------
    // Test 1: picker sucks up a nearby item entity into its container
    // -------------------------------------------------------------------------

    private static TestCase pickerCollectsNearbyItem() {
        return new TestCase("picker_collects_nearby_item", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ctx.getWorld().setBlock(bx, by, bz, PICKER_ID);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    // Spawn item entity directly adjacent to the picker block (within radius 3).
                    ItemTestUtil.spawnItemEntity(
                            ctx.getWorld(), ctx.getStore(),
                            bx + 0.5, by, bz + 0.5,
                            ITEM_ID, 1);
                }))
                .step(Steps.waitUntil(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    return icb != null && ItemTestUtil.countItems(icb.getItemContainer()) > 0;
                }, ctx -> 5 * ctx.getWorld().getTps(), "picker collected the item entity into the container"));
    }

    // -------------------------------------------------------------------------
    // Test 2: picker leaves item entities that are outside its radius untouched
    // -------------------------------------------------------------------------

    private static TestCase pickerIgnoresDistantItem() {
        return new TestCase("picker_ignores_distant_item", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ctx.getWorld().setBlock(bx, by, bz, PICKER_ID);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    // Spawn item entity 10 blocks away — well outside the default radius of 3.
                    ItemTestUtil.spawnItemEntity(
                            ctx.getWorld(), ctx.getStore(),
                            bx + 10.5, by, bz + 0.5,
                            ITEM_ID, 1);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    return icb != null && ItemTestUtil.countItems(icb.getItemContainer()) == 0;
                }, "ItemPickerSystem ignores item entities outside its pickup radius"));
    }
}

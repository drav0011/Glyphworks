package dev.drav.glyphworks.util;

import java.util.function.BiPredicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;
import org.joml.Vector3i;

import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.effect.builtin.rules.AbstractDenyRule;
import com.hypixel.hytale.builtin.triggervolumes.effect.builtin.rules.NoBuildRule;
import com.hypixel.hytale.builtin.triggervolumes.effect.builtin.rules.NoDestroyRule;
import com.hypixel.hytale.builtin.triggervolumes.effect.builtin.rules.NoHarvestRule;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.server.core.universe.world.World;

/**
 * Resolves the always-active trigger-volume deny rules that gate world
 * mutation.
 *
 * <p>
 * Machines act on the world without a player behind the action, so nothing in
 * the engine applies a region's build/destroy rules on their behalf — every
 * automated placement or break has to ask first. A machine standing outside a
 * protected region can still reach a target cell inside it, so the rule lookup
 * uses the <em>target</em> cell rather than the machine's own position.
 *
 * <p>
 * Lookups fail open: the trigger-volume plugin is only an optional dependency,
 * so a world without a manager imposes no restrictions rather than blocking
 * every machine.
 */
public final class TriggerVolumeGuard {

    private TriggerVolumeGuard() {
    }

    /**
     * Whether a block or fluid may be placed into {@code pos}.
     *
     * @param blockId asset id of what is being placed, matched against each
     *                rule's block exception list. Fluids are block assets in
     *                their own right, so a fluid id is the correct value here
     *                and matches exception entries the same way. {@code null}
     *                skips exception matching and so is denied by any active
     *                rule
     */
    public static boolean canBuild(@Nonnull World world, @Nonnull Vector3i pos, @Nullable String blockId) {
        return allowed(world, pos, NoBuildRule.class, blockId, NoBuildRule::isBlockExcepted);
    }

    /** Whether the block at {@code pos} may be removed. */
    public static boolean canDestroy(@Nonnull World world, @Nonnull Vector3i pos, @Nullable String blockId) {
        return allowed(world, pos, NoDestroyRule.class, blockId, NoDestroyRule::isBlockExcepted);
    }

    /** Whether the block at {@code pos} may be harvested for its drops. */
    public static boolean canHarvest(@Nonnull World world, @Nonnull Vector3i pos, @Nullable String blockId) {
        return allowed(world, pos, NoHarvestRule.class, blockId, NoHarvestRule::isBlockExcepted);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static <T extends AbstractDenyRule> boolean allowed(
            @Nonnull World world,
            @Nonnull Vector3i pos,
            @Nonnull Class<T> ruleType,
            @Nullable String blockId,
            @Nonnull BiPredicate<T, String> isExcepted) {

        TriggerVolumeManager manager = resolveManager(world);
        if (manager == null) {
            return true;
        }

        Vector3d center = new Vector3d(pos.x + 0.5d, pos.y + 0.5d, pos.z + 0.5d);

        // Machines run this every tick, and the overwhelmingly common answer is
        // "no rule here" — take the non-allocating probe before building a list.
        if (!manager.hasActiveRule(center, ruleType)) {
            return true;
        }

        for (T rule : manager.getActiveRules(center, ruleType)) {
            if (blockId != null && isExcepted.test(rule, blockId)) {
                continue;
            }
            return false;
        }
        return true;
    }

    @Nullable
    private static TriggerVolumeManager resolveManager(@Nonnull World world) {
        TriggerVolumesPlugin plugin = TriggerVolumesPlugin.get();
        if (plugin == null) {
            return null;
        }
        return world.getEntityStore().getStore().getResource(plugin.getManagerResourceType());
    }
}

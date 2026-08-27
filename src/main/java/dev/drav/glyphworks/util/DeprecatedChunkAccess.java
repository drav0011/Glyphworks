package dev.drav.glyphworks.util;

import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;

/**
 * Single entry-point for the deprecated chunk APIs Glyphworks still needs.
 *
 * <p>
 * As of Hytale 0.6 only {@link BlockSection#invalidateBlock(int, int, int)}
 * remains without a non-deprecated replacement (the engine offers no other way
 * to re-mark a single block as changed for client replication). Routing the
 * call through here keeps the suppression annotation in one file.
 */
@SuppressWarnings("deprecation")
public final class DeprecatedChunkAccess {

    private DeprecatedChunkAccess() {
    }

    public static void invalidateBlock(BlockSection blockSection, int wx, int wy, int wz) {
        blockSection.invalidateBlock(wx, wy, wz);
    }
}

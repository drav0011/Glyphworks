package dev.drav.glyphworks.util;

import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;

/**
 * Single entry-point for chunk APIs that are
 * {@code @Deprecated(forRemoval = false)}.
 *
 * <p>
 * The Hytale engine has deprecated its direct section-access methods on
 * {@link BlockChunk} and {@link WorldChunk} but has not yet provided stable
 * replacements. All callers in Glyphworks route through here so the suppression
 * annotation stays in one file rather than being scattered across the codebase.
 */
@SuppressWarnings("deprecation")
public final class DeprecatedChunkAccess {

    private DeprecatedChunkAccess() {
    }

    public static BlockSection getSection(BlockChunk blockChunk, int y) {
        return blockChunk.getSectionAtBlockY(y);
    }

    public static int getRotationIndex(WorldChunk worldChunk, int x, int y, int z) {
        return worldChunk.getBlockChunk().getSectionAtBlockY(y).getRotationIndex(x, y, z);
    }

    public static int getFiller(WorldChunk worldChunk, int x, int y, int z) {
        return worldChunk.getBlockChunk().getSectionAtBlockY(y).getFiller(x, y, z);
    }

    public static void invalidateBlock(BlockSection blockSection, int wx, int wy, int wz) {
        blockSection.invalidateBlock(wx, wy, wz);
    }
}

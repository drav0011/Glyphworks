package dev.drav.glyphworks.grid.tests;

import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.grid.type.GridType;

/** Shared grid-test helpers used across multiple test suites. */
public final class GridTestUtil {

    private GridTestUtil() {
    }

    /**
     * Returns {@code true} when the graph for {@code type} contains a direct
     * edge from {@code a} to {@code b}.
     */
    public static boolean connected(World world, Vector3i a, Vector3i b, GridType type) {
        GridGraph graph = GlyphworksPlugin.get().getGridModule().getGridGraph(world, type);

        if (graph == null) {
            return false;
        }

        return graph.getNeighbors(a).contains(b);
    }

    /**
     * Resolves the {@link dev.drav.glyphworks.grid.component.GridComponent} at
     * {@code pos} via {@link GridLookup}, or {@code null} if the block is absent
     * or has no grid component.
     */
    @Nullable
    public static GridLookup lookup(World world, Vector3i pos) {
        return GridLookup.resolve(world.getChunkStore(), pos);
    }
}


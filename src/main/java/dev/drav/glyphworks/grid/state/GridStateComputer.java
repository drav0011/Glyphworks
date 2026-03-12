package dev.drav.glyphworks.grid.state;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.grid.component.GridComponent;

/**
 * Computes the block-state name for a {@link GridComponent} block at a given position.
 * Registered per root block type ID in {@link GridStateRegistry}.
 *
 * <p>Examples:
 * <ul>
 *   <li>Pipe → {@code "NS"}, {@code "NEU"}, {@code "Single"}</li>
 * </ul>
 */
@FunctionalInterface
public interface GridStateComputer {
    /**
     * @return the state name to apply (e.g. {@code "NS"}, {@code "Single"})
     */
    String compute(GridComponent grid, Vector3i pos, World world);
}

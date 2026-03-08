package dev.drav.glyphworks.transfer;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.transfer.component.TransferComponent;

/**
 * Computes the block-state name for a {@link TransferComponent} block at a given position.
 * Registered per root block type ID in {@link TransferStateRegistry}.
 *
 * <p>Examples:
 * <ul>
 *   <li>Pipe → {@code "NS"}, {@code "NEU"}, {@code "Single"} etc.</li>
 *   <li>Furnace → {@code "Active"} / {@code "Idle"}</li>
 * </ul>
 */
@FunctionalInterface
public interface TransferStateComputer {
    /**
     * @return the state name to apply (e.g. {@code "NS"}, {@code "Single"}, {@code "Active"})
     */
    String compute(TransferComponent transfer, Vector3i pos, World world);
}

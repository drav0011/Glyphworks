package dev.drav.glyphworks.transfer;

import com.hypixel.hytale.math.vector.Vector3i;

/**
 * Callback fired by {@link FaceLinkUtil} for every world position whose visual block state
 * may have changed as a result of a link or unlink operation.
 *
 * <p>Callers supply a lambda that routes to {@link TransferStateRegistry#applyState}.
 * Pass {@code null} to suppress all visual updates (e.g. on chunk load).
 */
@FunctionalInterface
public interface BlockStateNotifier {
    void onChanged(Vector3i pos);
}

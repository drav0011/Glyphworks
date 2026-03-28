package dev.drav.glyphworks.grid.system;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import org.joml.Vector3i;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.chunk.systems.ChunkSystems;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.event.BreakGridBlockEvent;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

/**
 * Detects block changes in every loaded chunk section each tick and
 * automatically wires or removes grid connections — regardless of whether the
 * change came from a player, {@code world.setBlock()}, a {@code /set} command,
 * a prefab paste, or any other source.
 *
 * <h3>Mechanism</h3>
 * {@link BlockSection} accumulates the flat section-local index of every
 * changed block in an internal {@code changedPositions} set. This system drains
 * that set (via {@link BlockSection#getAndClearChangedPositions()}) before
 * {@link ChunkSystems.ReplicateChanges} does, processes each changed position,
 * then puts the indices back with {@link BlockSection#invalidateBlock} so the
 * replication system still sends the changes to clients.
 *
 * <h3>Per-position logic</h3>
 * <ul>
 * <li>If {@link GridLookup} resolves at the new position → a grid block has
 * been placed or replaced; call {@link PlaceGridBlockEvent#connectBlock}.</li>
 * <li>If {@link GridLookup} returns {@code null} but the {@link GridGraph}
 * still contains that position → a grid block was removed; call
 * {@link BreakGridBlockEvent#disconnectBlock}.</li>
 * <li>Otherwise → ordinary non-grid block change; ignore.</li>
 * </ul>
 */
public final class BlockChangeGridSystem extends EntityTickingSystem<ChunkStore> {

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(
                ChunkSection.getComponentType(),
                BlockSection.getComponentType());
    }

    /**
     * Run <em>before</em> {@link ChunkSystems.ReplicateChanges} so we see the
     * changed positions before they are drained for network replication.
     */
    @Override
    public Set<Dependency<ChunkStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.BEFORE, ChunkSystems.ReplicateChanges.class));
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        ChunkSection section = archetypeChunk.getComponent(index, ChunkSection.getComponentType());
        BlockSection blockSection = archetypeChunk.getComponent(index, BlockSection.getComponentType());
        if (section == null || blockSection == null)
            return;

        // Drain changed positions — we own this set until we put indices back.
        IntOpenHashSet changed = blockSection.getAndClearChangedPositions();
        if (changed.isEmpty())
            return;

        World world = store.getExternalData().getWorld();

        // Section base world coordinates.
        int baseX = ChunkUtil.minBlock(section.getX());
        int baseY = ChunkUtil.minBlock(section.getY());
        int baseZ = ChunkUtil.minBlock(section.getZ());

        IntIterator iter = changed.intIterator();
        while (iter.hasNext()) {
            int flatIdx = iter.nextInt();

            int wx = baseX + ChunkUtil.xFromIndex(flatIdx);
            int wy = baseY + ChunkUtil.yFromIndex(flatIdx);
            int wz = baseZ + ChunkUtil.zFromIndex(flatIdx);
            Vector3i pos = new Vector3i(wx, wy, wz);

            GridLookup lookup = GridLookup.resolve(world.getChunkStore(), pos);

            if (lookup != null) {
                // A grid block now exists here — connect it.
                // The engine's ±1 block-change notification already updates the visual
                // connected-block state for adjacent pipes; no forceConnectedBlockUpdate
                // needed.
                PlaceGridBlockEvent.connectBlock(world, pos);
            } else {
                // No grid block here — check if one was just removed from the graph.
                // The block entity is already gone so GridLookup.resolve returns null and
                // disconnectBlock() would be a no-op. Use the graph's adjacency instead.
                for (GridGraph graph : GlyphworksPlugin.get().getAllGridGraphs(world)) {
                    if (!graph.contains(pos))
                        continue;
                    Set<Vector3i> graphNeighbors = new HashSet<>(graph.getNeighbors(pos));
                    graph.removeNode(pos);
                    // Remove this position from each surviving neighbour's component set.
                    for (Vector3i neighborPos : graphNeighbors) {
                        GridLookup neighborLookup = GridLookup.resolve(world.getChunkStore(), neighborPos);
                        if (neighborLookup != null) {
                            neighborLookup.component().removeNeighbor(pos);
                        }
                    }
                    // Deferred rebuild: by the time commandBuffer.run() fires the broken
                    // block's entity is gone. Clearing stale neighbor sets then re-running
                    // connectBlock rebuilds them correctly from the live world.
                    if (!graphNeighbors.isEmpty()) {
                        commandBuffer.run(_ -> {
                            for (Vector3i n : graphNeighbors) {
                                GridLookup nl = GridLookup.resolve(world.getChunkStore(), n);
                                if (nl == null)
                                    continue;
                                nl.component().setNeighbors(new HashSet<>());
                                PlaceGridBlockEvent.connectBlock(world, n);
                            }
                        });
                    }
                    break; // a position belongs to at most one grid type
                }
            }

            // Restore the index so ReplicateChanges still sends this block to clients.
            blockSection.invalidateBlock(wx, wy, wz);
        }
    }
}

package dev.drav.glyphworks.transfer.fluid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.thread.TickingThread;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.component.FluidPipeComponent;
import dev.drav.glyphworks.grid.component.FaceMode;
import dev.drav.glyphworks.grid.component.FacePlane;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.grid.type.GridTypeHandler;
import dev.drav.glyphworks.grid.util.GridFaceUtil;

/**
 * Per-tick handler for the {@code "Fluid"} grid type.
 *
 * <p>
 * Two kinds of nodes are recognised:
 * <ul>
 * <li><b>Tank</b>: OUTPUT/BIDIRECTIONAL face with a non-null
 * {@code containerKey}, paired with a {@link FluidContainerComponent}.
 * Sources the BFS when it has fluid; acts as a sink when it has
 * space.</li>
 * <li><b>Pipe</b>: BIDIRECTIONAL face with {@code containerKey = null}.
 * Transparent relay. An optional {@link FluidPipeComponent} tracks
 * which fluid currently occupies the pipe; incompatible fluids are
 * blocked. Traversed pipes are locked to the flowing fluid after a
 * successful transfer.</li>
 * </ul>
 *
 * <p>
 * Fluid amounts are tracked in <b>liters</b>. Transfer rate is expressed
 * in liters per tick. World-fluid interaction (Remover / Placer) is handled
 * by a separate system outside the grid.
 */
public final class FluidGridTypeHandler implements GridTypeHandler {

    @Override
    public String typeId() {
        return "Fluid";
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer,
            @Nonnull ChunkStore chunkStore,
            @Nonnull GridComponent component,
            @Nonnull Ref<ChunkStore> blockRef) {

        // ---- Step 1: recover originPos -----------------------------------
        Vector3i originPos = component.getOriginPosition();
        GridGraph gridGraph = GlyphworksPlugin.get()
                .getGridGraph(chunkStore.getWorld(), component.getGridType());

        if (originPos == null && gridGraph != null && !component.getNeighbors().isEmpty()) {
            Vector3i firstNeighbor = component.getNeighbors().iterator().next();
            for (Vector3i candidate : gridGraph.getNeighbors(firstNeighbor)) {
                GridLookup cand = GridLookup.resolve(chunkStore, candidate);
                if (cand != null && blockRef.equals(cand.blockRef())) {
                    originPos = candidate;
                    break;
                }
            }
        }

        // ---- Step 2: collect sources ------------------------------------
        // A source is either:
        // (a) a tank face (OUTPUT / BIDIR + non-null containerKey +
        // FluidContainerComponent with fluid), or
        // (b) a Remover face (OUTPUT + null containerKey + adjacent world fluid block).
        //
        // Multiple faces of different fluid types are grouped separately and each
        // gets its own BFS pass.

        record FluidSource(String fluidId, int maxAvailable, FluidContainerComponent fcc) {
        }

        GridLookup selfLookup = (originPos != null) ? GridLookup.resolve(chunkStore, originPos) : null;

        List<FluidSource> sources = new ArrayList<>();
        for (FacePlane face : component.getFaces()) {
            FaceMode mode = face.getMode();
            if (mode != FaceMode.OUTPUT && mode != FaceMode.BIDIRECTIONAL)
                continue;

            String key = face.getContainerKey();
            if (key != null) {
                // Tank: fluid is stored in FluidContainerComponent on this block.
                FluidContainerComponent fcc = store.getComponent(blockRef, FluidContainerComponent.getComponentType());
                if (fcc == null || fcc.isEmpty() || fcc.getLockedFluidId() == null)
                    continue;
                sources.add(new FluidSource(fcc.getLockedFluidId(), fcc.getAmount(), fcc));
            }
            // BIDIRECTIONAL/OUTPUT + null containerKey → pipe face or world-IO block, not a
            // grid source.
        }
        if (sources.isEmpty()) {
            return;
        }

        // ---- Step 3 & 4: BFS + transfer (once per source) ---------------

        record BFSEntry(GridLookup lookup, float rate, Vector3i arrivedFrom, int distance) {
        }

        record SinkEntry(
                Ref<ChunkStore> ref,
                GridComponent comp,
                FacePlane sinkFace,
                float rate,
                int distance,
                Vector3i originPos,
                FluidContainerComponent fcc) {
        }

        for (FluidSource source : sources) {
            // Accumulator-based budget (liters this tick).
            int toTransfer = component.drainAccumulator(component.getTransferRate() * dt * TickingThread.TPS);
            if (toTransfer < 1) {
                continue;
            }

            // -- BFS -------------------------------------------------------
            Set<Ref<ChunkStore>> visited = new HashSet<>();
            visited.add(blockRef);
            Queue<BFSEntry> queue = new ArrayDeque<>();
            List<SinkEntry> sinks = new ArrayList<>();
            // All pure-pipe nodes traversed; locked to sourceFluid after transfer.
            List<Ref<ChunkStore>> traversedPipes = new ArrayList<>();

            Set<Vector3i> seedNeighbors = (gridGraph != null && originPos != null)
                    ? gridGraph.getNeighbors(originPos)
                    : component.getNeighbors();

            final Vector3i finalOriginPos = originPos;
            for (Vector3i neighborPos : seedNeighbors) {
                // Skip seeds reachable only through INPUT faces (same logic as item handler).
                if (selfLookup != null) {
                    FacePlane connectingFace = findEntryFace(chunkStore, selfLookup, neighborPos);
                    if (connectingFace != null && connectingFace.getMode() == FaceMode.INPUT)
                        continue;
                }
                GridLookup lookup = GridLookup.resolve(chunkStore, neighborPos);
                if (lookup == null)
                    continue;
                if (!visited.add(lookup.blockRef()))
                    continue;
                float rate = Math.min(component.getTransferRate(), lookup.component().getTransferRate());
                queue.add(new BFSEntry(lookup, rate, finalOriginPos, 1));
            }

            while (!queue.isEmpty()) {
                BFSEntry entry = queue.poll();
                GridComponent entryComp = entry.lookup().component();
                Vector3i entryPos = entry.lookup().originPos();

                // Determine if this is a terminal (any non-pure-pipe face).
                boolean isTerminal = false;
                for (FacePlane face : entryComp.getFaces()) {
                    if (face.getMode() != FaceMode.BIDIRECTIONAL || face.getContainerKey() != null) {
                        isTerminal = true;
                        break;
                    }
                }

                if (isTerminal) {
                    // Classify as a sink if reached through an INPUT or BIDIRECTIONAL face.
                    if (entry.arrivedFrom() != null) {
                        FacePlane entryFace = findEntryFace(chunkStore, entry.lookup(), entry.arrivedFrom());
                        if (entryFace != null) {
                            FaceMode mode = entryFace.getMode();
                            String key = entryFace.getContainerKey();

                            if (mode == FaceMode.INPUT || mode == FaceMode.BIDIRECTIONAL) {
                                if (key != null) {
                                    // Tank sink: check FluidContainerComponent compatibility.
                                    FluidContainerComponent fcc = store.getComponent(
                                            entry.lookup().blockRef(), FluidContainerComponent.getComponentType());
                                    if (fcc != null
                                            && fcc.availableSpace() > 0
                                            && (fcc.getLockedFluidId() == null
                                                    || fcc.getLockedFluidId().equals(source.fluidId()))) {
                                        sinks.add(new SinkEntry(
                                                entry.lookup().blockRef(), entryComp, entryFace,
                                                entry.rate(), entry.distance(),
                                                entry.lookup().originPos(), fcc));
                                    }
                                }
                            }
                        }
                    }
                    continue; // terminals do not relay
                }

                // Pure-pipe relay: check FluidPipeComponent compatibility.
                FluidPipeComponent fpc = store.getComponent(entry.lookup().blockRef(),
                        FluidPipeComponent.getComponentType());
                if (fpc != null && !fpc.accepts(source.fluidId()))
                    continue; // incompatible pipe

                traversedPipes.add(entry.lookup().blockRef());

                Set<Vector3i> relayNeighbors = (gridGraph != null)
                        ? gridGraph.getNeighbors(entryPos)
                        : entryComp.getNeighbors();
                for (Vector3i neighborPos : relayNeighbors) {
                    GridLookup lookup = GridLookup.resolve(chunkStore, neighborPos);
                    if (lookup == null)
                        continue;
                    if (!visited.add(lookup.blockRef()))
                        continue;
                    float rate = Math.min(entry.rate(), lookup.component().getTransferRate());
                    queue.add(new BFSEntry(lookup, rate, entryPos, entry.distance() + 1));
                }
            }

            if (sinks.isEmpty()) {
                continue;
            }

            // -- Transfer --------------------------------------------------
            sinks.sort(Comparator.comparingInt(SinkEntry::distance));

            int remaining = Math.min(toTransfer, source.maxAvailable());
            int totalMoved = 0;

            for (SinkEntry sink : sinks) {
                if (remaining <= 0)
                    break;

                int canAccept = Math.min(remaining, sink.fcc().availableSpace());
                int moved = sink.fcc().fill(source.fluidId(), canAccept);

                remaining -= moved;
                totalMoved += moved;
            }

            if (totalMoved <= 0)
                continue;

            // -- Drain source ----------------------------------------------
            source.fcc().drain(totalMoved);

            // -- Lock traversed pipes to this fluid -----------------------
            for (Ref<ChunkStore> pipeRef : traversedPipes) {
                FluidPipeComponent fpc = store.getComponent(pipeRef, FluidPipeComponent.getComponentType());
                if (fpc != null && fpc.getLockedFluidId() == null) {
                    fpc.setLockedFluidId(source.fluidId());
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Identifies which face of {@code terminal} the BFS path arrived through.
     * Mirrors the same helper in {@code ItemGridTypeHandler}.
     */
    @Nullable
    private static FacePlane findEntryFace(
            @Nonnull ChunkStore chunkStore,
            @Nonnull GridLookup terminal,
            @Nonnull Vector3i arrivedFromOriginPos) {
        Vector3i originPos = terminal.originPos();
        for (FacePlane face : terminal.component().getFaces()) {
            BlockFace worldNormal = GridFaceUtil.rotateBlockFace(face.getNormal(), terminal.rotation());
            if (worldNormal == BlockFace.None)
                continue;
            Vector3i worldFaceCell = GridFaceUtil.addOffset(
                    originPos, GridFaceUtil.rotateFacePosition(face.getPosition(), terminal.rotation()));
            Vector3i outsidePos = GridFaceUtil.addOffset(worldFaceCell, worldNormal);
            if (outsidePos.equals(arrivedFromOriginPos))
                return face;
            GridLookup outsideLookup = GridLookup.resolve(chunkStore, outsidePos);
            if (outsideLookup != null && outsideLookup.originPos().equals(arrivedFromOriginPos))
                return face;
        }
        return null;
    }
}

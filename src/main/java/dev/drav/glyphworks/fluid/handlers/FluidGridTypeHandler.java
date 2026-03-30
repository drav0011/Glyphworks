package dev.drav.glyphworks.fluid.handlers;

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

    /**
     * Per-tick transfer pass for a single fluid-grid node.
     *
     * <p>
     * <b>Pipe lock lifecycle</b>: when fluid first flows through a
     * {@link FluidPipeComponent}, the pipe is locked to that fluid type
     * ({@link FluidPipeComponent#setFluidId}). The lock persists until the pipe
     * block is broken and replaced — the replacement starts from a fresh,
     * uncontaminated {@code FluidPipeComponent} (null {@code fluidId}). There
     * is no drain-triggered auto-unlock; this matches the EnderIO network-lock
     * semantics.
     *
     * <p>
     * <b>Transfer atomicity</b>: the transfer is performed in two passes.
     * The first pass plans how much fluid each sink will receive without
     * mutating any state. The second pass drains the source first, then fills
     * each sink, so fluid can never be created from nothing if a later step
     * fails.
     */
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

        // ---- Step 1b: skip non-root nodes --------------------------------
        // Each connected network has one root node (arbitrary but stable).
        // Only the root runs the full transfer pass on behalf of the whole network;
        // all other nodes in the same network return immediately here.
        // The root drains accumulators for every source it finds, so no fluid
        // budget is silently lost by skipping non-root ticks.
        if (gridGraph != null && originPos != null) {
            Vector3i componentRoot = gridGraph.getComponentRoot(originPos);
            if (componentRoot != null && !componentRoot.equals(originPos)) {
                return;
            }
        }

        // ---- Step 2: collect sources ------------------------------------
        // Walk every node in this network and find all tank faces that have fluid.
        // The root does this on behalf of the entire component so that non-root
        // source tanks are still drained even though their own tick() returned early.

        record FluidSource(String fluidId, int maxAvailable, FluidContainerComponent fcc,
                GridLookup sourceLookup) {
        }

        // Members of this component — falls back to just the current node when the
        // graph is unavailable (e.g. during chunk load before graph is ready).
        Set<Vector3i> networkMembers;
        if (gridGraph != null && originPos != null) {
            Vector3i root = gridGraph.getComponentRoot(originPos);
            networkMembers = (root != null) ? gridGraph.getComponentMembers(root) : Set.of(originPos);
        } else {
            networkMembers = (originPos != null) ? Set.of(originPos) : Set.of();
        }

        List<FluidSource> sources = new ArrayList<>();
        for (Vector3i memberPos : networkMembers) {
            GridLookup memberLookup = GridLookup.resolve(chunkStore, memberPos);
            if (memberLookup == null)
                continue;
            GridComponent memberComp = memberLookup.component();
            for (FacePlane face : memberComp.getFaces()) {
                FaceMode mode = face.getMode();
                if (mode != FaceMode.OUTPUT && mode != FaceMode.BIDIRECTIONAL)
                    continue;
                String key = face.getContainerKey();
                if (key != null) {
                    // Tank: fluid is stored in FluidContainerComponent on this block.
                    FluidContainerComponent fcc = store.getComponent(
                            memberLookup.blockRef(), FluidContainerComponent.getComponentType());
                    if (fcc == null || fcc.isEmpty() || fcc.getFluidId() == null)
                        continue;
                    sources.add(new FluidSource(fcc.getFluidId(), fcc.getAmount(), fcc, memberLookup));
                }
                // BIDIRECTIONAL/OUTPUT + null containerKey → pipe face or world-IO block,
                // not a grid source.
            }
        }

        // Sort sources by world position (X → Y → Z) so that the source iteration
        // order is deterministic regardless of HashSet bucket layout. This ensures
        // that when two sources with incompatible fluids share a merged network, the
        // source at the lower coordinate always wins the bridge lock.
        sources.sort(Comparator.comparingInt((FluidSource s) -> s.sourceLookup().originPos().x)
                .thenComparingInt(s -> s.sourceLookup().originPos().y)
                .thenComparingInt(s -> s.sourceLookup().originPos().z));

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
            GridLookup sourceLookup = source.sourceLookup();
            Vector3i sourcePos = sourceLookup.originPos();
            GridComponent sourceComp = sourceLookup.component();

            // Drain accumulator from the source node's own component (not the root's),
            // so the per-node transfer-rate budget is correctly maintained even though
            // this node's own tick() returned early.
            int toTransfer = sourceComp.drainAccumulator(sourceComp.getTransferRate() * dt * TickingThread.TPS);
            if (toTransfer < 1) {
                continue;
            }

            // -- BFS -------------------------------------------------------
            Set<Ref<ChunkStore>> visited = new HashSet<>();
            visited.add(sourceLookup.blockRef());
            Queue<BFSEntry> queue = new ArrayDeque<>();
            List<SinkEntry> sinks = new ArrayList<>();
            // All pure-pipe nodes traversed; locked to sourceFluid after transfer.
            List<Ref<ChunkStore>> traversedPipes = new ArrayList<>();

            Set<Vector3i> seedNeighbors = (gridGraph != null)
                    ? gridGraph.getNeighbors(sourcePos)
                    : sourceComp.getNeighbors();

            for (Vector3i neighborPos : seedNeighbors) {
                // Skip seeds reachable only through INPUT faces on the source node.
                FacePlane connectingFace = findEntryFace(chunkStore, sourceLookup, neighborPos);
                if (connectingFace != null && connectingFace.getMode() == FaceMode.INPUT)
                    continue;
                GridLookup lookup = GridLookup.resolve(chunkStore, neighborPos);
                if (lookup == null)
                    continue;
                if (!visited.add(lookup.blockRef()))
                    continue;
                float rate = Math.min(sourceComp.getTransferRate(), lookup.component().getTransferRate());
                queue.add(new BFSEntry(lookup, rate, sourcePos, 1));
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
                                            && (fcc.getFluidId() == null
                                                    || fcc.getFluidId().equals(source.fluidId()))) {
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

            // -- Transfer (two-pass atomic: plan → drain → fill) ----------
            sinks.sort(Comparator.comparingInt(SinkEntry::distance));

            record PlannedTransfer(FluidContainerComponent fcc, int amount) {
            }

            List<PlannedTransfer> transfers = new ArrayList<>();
            int budget = Math.min(toTransfer, source.maxAvailable());
            int remaining = budget;

            for (SinkEntry sink : sinks) {
                if (remaining <= 0)
                    break;
                int amount = Math.min(remaining, sink.fcc().availableSpace());
                if (amount > 0) {
                    transfers.add(new PlannedTransfer(sink.fcc(), amount));
                    remaining -= amount;
                }
            }

            if (transfers.isEmpty())
                continue;

            int totalMoved = budget - remaining;

            // -- Drain source first, then fill sinks ----------------------
            source.fcc().drain(totalMoved);

            for (PlannedTransfer t : transfers) {
                t.fcc().fill(source.fluidId(), t.amount());
            }

            // -- Lock traversed pipes to this fluid -----------------------
            for (Ref<ChunkStore> pipeRef : traversedPipes) {
                FluidPipeComponent fpc = store.getComponent(pipeRef, FluidPipeComponent.getComponentType());
                if (fpc != null && fpc.getFluidId() == null) {
                    fpc.setFluidId(source.fluidId());
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

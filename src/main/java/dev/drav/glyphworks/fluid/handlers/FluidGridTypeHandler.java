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
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.component.FluidPipeComponent;
import dev.drav.glyphworks.grid.component.FacePlane;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.component.GridTypeEntry;
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
    // Records
    // -------------------------------------------------------------------------

    private record FluidSource(FluidStack fluid, FluidContainerComponent fcc, GridLookup sourceLookup) {
    }

    private record BFSEntry(GridLookup lookup, float rate, Vector3i arrivedFrom, int distance) {
    }

    private record FluidSinkEntry(
            Ref<ChunkStore> ref,
            GridComponent comp,
            FacePlane sinkFace,
            float rate,
            int distance,
            Vector3i originPos,
            FluidContainerComponent fcc) {
    }

    private record PlannedTransfer(FluidContainerComponent fcc, int amount) {
    }

    private record FluidBFSResult(List<FluidSinkEntry> sinks, List<Ref<ChunkStore>> traversedPipes) {
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    /**
     * Per-tick transfer pass for a single fluid-grid node.
     *
     * <p>
     * Only the root node of each connected network runs the full transfer pass.
     * Non-root nodes return immediately. The root collects all fluid sources in
     * the network, then for each source runs a BFS toward compatible sinks and
     * executes a two-pass atomic transfer (plan then drain-fill).
     *
     * <p>
     * <b>Pipe lock lifecycle</b>: when fluid first flows through a
     * {@link FluidPipeComponent}, the pipe is locked to that fluid type.
     * The lock persists until the pipe block is broken and replaced.
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
            @Nonnull GridTypeEntry entry,
            @Nonnull Ref<ChunkStore> blockRef) {

        String typeId = entry.getGridType().id();
        GridGraph gridGraph = GlyphworksPlugin.get().getGridModule()
                .getGridGraph(chunkStore.getWorld(), entry.getGridType());
        Vector3i originPos = resolveOriginPosition(component, entry, chunkStore, blockRef, gridGraph);

        if (shouldSkipNonRoot(gridGraph, originPos))
            return;

        List<FluidSource> sources = collectFluidSources(originPos, gridGraph, chunkStore, store, typeId);
        if (sources.isEmpty())
            return;

        for (FluidSource source : sources) {
            transferFluid(source, chunkStore, store, gridGraph, typeId, dt);
        }
    }

    // -------------------------------------------------------------------------
    // Transfer pipeline
    // -------------------------------------------------------------------------

    @Nullable
    private static Vector3i resolveOriginPosition(
            @Nonnull GridComponent component,
            @Nonnull GridTypeEntry entry,
            @Nonnull ChunkStore chunkStore,
            @Nonnull Ref<ChunkStore> blockRef,
            @Nullable GridGraph gridGraph) {
        Vector3i originPos = component.getOriginPosition();
        if (originPos != null || gridGraph == null || entry.getNeighbors().isEmpty())
            return originPos;

        Vector3i firstNeighbor = entry.getNeighbors().iterator().next();
        for (Vector3i candidate : gridGraph.getNeighbors(firstNeighbor)) {
            GridLookup cand = GridLookup.resolve(chunkStore, candidate);
            if (cand != null && blockRef.equals(cand.blockRef()))
                return candidate;
        }
        return null;
    }

    private static boolean shouldSkipNonRoot(@Nullable GridGraph gridGraph, @Nullable Vector3i originPos) {
        if (gridGraph == null || originPos == null)
            return false;
        Vector3i componentRoot = gridGraph.getComponentRoot(originPos);
        return componentRoot != null && !componentRoot.equals(originPos);
    }

    private static List<FluidSource> collectFluidSources(
            @Nullable Vector3i originPos,
            @Nullable GridGraph gridGraph,
            @Nonnull ChunkStore chunkStore,
            @Nonnull Store<ChunkStore> store,
            @Nonnull String typeId) {
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
            GridTypeEntry memberEntry = memberLookup.component().getEntry(typeId);
            if (memberEntry == null)
                continue;
            for (FacePlane face : memberEntry.getFaces()) {
                FilterType mode = face.getMode();
                if (!mode.allowOutput())
                    continue;
                if (face.getContainerKey() == null)
                    continue;
                FluidContainerComponent fcc = store.getComponent(
                        memberLookup.blockRef(), FluidContainerComponent.getComponentType());
                if (fcc == null)
                    continue;
                FluidStack fluid = fcc.getFluid();
                if (fluid == null)
                    continue;
                sources.add(new FluidSource(fluid, fcc, memberLookup));
            }
        }

        sources.sort(Comparator.comparingInt((FluidSource s) -> s.sourceLookup().originPos().x)
                .thenComparingInt(s -> s.sourceLookup().originPos().y)
                .thenComparingInt(s -> s.sourceLookup().originPos().z));
        return sources;
    }

    private void transferFluid(
            @Nonnull FluidSource source,
            @Nonnull ChunkStore chunkStore,
            @Nonnull Store<ChunkStore> store,
            @Nullable GridGraph gridGraph,
            @Nonnull String typeId,
            float dt) {
        GridTypeEntry sourceEntry = source.sourceLookup().component().getEntry(typeId);
        if (sourceEntry == null)
            return;
        int toTransfer = sourceEntry.drainAccumulator(
                sourceEntry.getTransferRate() * dt * chunkStore.getWorld().getTps());
        if (toTransfer < 1)
            return;

        FluidBFSResult bfs = findFluidSinks(source, chunkStore, store, gridGraph, typeId);
        if (bfs.sinks().isEmpty())
            return;

        executeFluidTransfer(source, toTransfer, bfs, store);
    }

    private FluidBFSResult findFluidSinks(
            @Nonnull FluidSource source,
            @Nonnull ChunkStore chunkStore,
            @Nonnull Store<ChunkStore> store,
            @Nullable GridGraph gridGraph,
            @Nonnull String typeId) {
        GridLookup sourceLookup = source.sourceLookup();
        Vector3i sourcePos = sourceLookup.originPos();
        GridTypeEntry sourceEntry = sourceLookup.component().getEntry(typeId);

        Set<Ref<ChunkStore>> visited = new HashSet<>();
        visited.add(sourceLookup.blockRef());
        Queue<BFSEntry> queue = new ArrayDeque<>();
        List<FluidSinkEntry> sinks = new ArrayList<>();
        List<Ref<ChunkStore>> traversedPipes = new ArrayList<>();

        seedBFS(sourceEntry, sourcePos, sourceLookup, gridGraph, chunkStore, typeId, visited, queue);

        while (!queue.isEmpty()) {
            BFSEntry bfsNode = queue.poll();
            GridTypeEntry entryCompEntry = bfsNode.lookup().component().getEntry(typeId);
            if (entryCompEntry == null)
                continue;

            if (isTerminalNode(entryCompEntry)) {
                classifyFluidSink(bfsNode, source, chunkStore, store, typeId, sinks);
                continue;
            }

            FluidPipeComponent fpc = store.getComponent(bfsNode.lookup().blockRef(),
                    FluidPipeComponent.getComponentType());
            if (fpc != null && !fpc.accepts(source.fluid()))
                continue;

            traversedPipes.add(bfsNode.lookup().blockRef());
            expandRelayNeighbors(bfsNode, entryCompEntry, gridGraph, chunkStore, typeId, visited, queue);
        }

        return new FluidBFSResult(sinks, traversedPipes);
    }

    private static void seedBFS(
            @Nonnull GridTypeEntry sourceEntry,
            @Nonnull Vector3i sourcePos,
            @Nonnull GridLookup sourceLookup,
            @Nullable GridGraph gridGraph,
            @Nonnull ChunkStore chunkStore,
            @Nonnull String typeId,
            @Nonnull Set<Ref<ChunkStore>> visited,
            @Nonnull Queue<BFSEntry> queue) {
        Set<Vector3i> seedNeighbors = (gridGraph != null)
                ? gridGraph.getNeighbors(sourcePos)
                : sourceEntry.getNeighbors();
        for (Vector3i neighborPos : seedNeighbors) {
            FacePlane connectingFace = findEntryFace(chunkStore, sourceLookup, neighborPos, typeId);
            if (connectingFace != null && !connectingFace.getMode().allowOutput())
                continue;
            GridLookup lookup = GridLookup.resolve(chunkStore, neighborPos);
            if (lookup == null)
                continue;
            if (!visited.add(lookup.blockRef()))
                continue;
            GridTypeEntry seedNeighborEntry = lookup.component().getEntry(typeId);
            if (seedNeighborEntry == null)
                continue;
            float rate = Math.min(sourceEntry.getTransferRate(), seedNeighborEntry.getTransferRate());
            queue.add(new BFSEntry(lookup, rate, sourcePos, 1));
        }
    }

    private static boolean isTerminalNode(@Nonnull GridTypeEntry entry) {
        for (FacePlane face : entry.getFaces()) {
            if (face.getMode() != FilterType.ALLOW_ALL || face.getContainerKey() != null)
                return true;
        }
        return false;
    }

    private static void classifyFluidSink(
            @Nonnull BFSEntry bfsNode,
            @Nonnull FluidSource source,
            @Nonnull ChunkStore chunkStore,
            @Nonnull Store<ChunkStore> store,
            @Nonnull String typeId,
            @Nonnull List<FluidSinkEntry> sinks) {
        if (bfsNode.arrivedFrom() == null)
            return;
        FacePlane entryFace = findEntryFace(chunkStore, bfsNode.lookup(), bfsNode.arrivedFrom(), typeId);
        if (entryFace == null)
            return;
        FilterType mode = entryFace.getMode();
        if (!mode.allowInput())
            return;
        if (entryFace.getContainerKey() == null)
            return;
        FluidContainerComponent fcc = store.getComponent(
                bfsNode.lookup().blockRef(), FluidContainerComponent.getComponentType());
        if (fcc == null || fcc.availableSpace() <= 0)
            return;
        FluidStack sinkFluid = fcc.getFluid();
        if (sinkFluid != null && !sinkFluid.isStackableWith(source.fluid()))
            return;
        sinks.add(new FluidSinkEntry(
                bfsNode.lookup().blockRef(), bfsNode.lookup().component(), entryFace,
                bfsNode.rate(), bfsNode.distance(),
                bfsNode.lookup().originPos(), fcc));
    }

    private static void expandRelayNeighbors(
            @Nonnull BFSEntry bfsNode,
            @Nonnull GridTypeEntry entryCompEntry,
            @Nullable GridGraph gridGraph,
            @Nonnull ChunkStore chunkStore,
            @Nonnull String typeId,
            @Nonnull Set<Ref<ChunkStore>> visited,
            @Nonnull Queue<BFSEntry> queue) {
        Vector3i entryPos = bfsNode.lookup().originPos();
        Set<Vector3i> relayNeighbors = (gridGraph != null)
                ? gridGraph.getNeighbors(entryPos)
                : entryCompEntry.getNeighbors();
        for (Vector3i neighborPos : relayNeighbors) {
            GridLookup lookup = GridLookup.resolve(chunkStore, neighborPos);
            if (lookup == null)
                continue;
            if (!visited.add(lookup.blockRef()))
                continue;
            GridTypeEntry relayNeighborEntry = lookup.component().getEntry(typeId);
            if (relayNeighborEntry == null)
                continue;
            float rate = Math.min(bfsNode.rate(), relayNeighborEntry.getTransferRate());
            queue.add(new BFSEntry(lookup, rate, entryPos, bfsNode.distance() + 1));
        }
    }

    private static void executeFluidTransfer(
            @Nonnull FluidSource source,
            int toTransfer,
            @Nonnull FluidBFSResult bfs,
            @Nonnull Store<ChunkStore> store) {
        String fluidId = source.fluid().getFluidId();

        List<FluidContainerComponent> pipeFccs = collectPipeFccs(bfs.traversedPipes(), store);
        int poolAmount = 0;
        int poolCapacity = 0;
        for (FluidContainerComponent fcc : pipeFccs) {
            poolAmount += fcc.getAmount();
            poolCapacity += fcc.getCapacity();
        }

        if (poolCapacity > 0) {
            int fromSource = Math.min(toTransfer, Math.min(source.fluid().getAmountMb(), poolCapacity - poolAmount));
            if (fromSource > 0) {
                source.fcc().drain(fromSource);
                poolAmount += fromSource;
            }
        } else {
            poolAmount = executeDirectTransfer(source, toTransfer, bfs.sinks());
            lockPipes(bfs.traversedPipes(), fluidId, store);
            return;
        }

        if (poolAmount > 0) {
            poolAmount = drainPoolToSinks(fluidId, poolAmount, bfs.sinks());
        }

        distributeToPipes(fluidId, poolAmount, pipeFccs);
        lockPipes(bfs.traversedPipes(), fluidId, store);
    }

    private static List<FluidContainerComponent> collectPipeFccs(
            @Nonnull List<Ref<ChunkStore>> pipeRefs,
            @Nonnull Store<ChunkStore> store) {
        List<FluidContainerComponent> result = new ArrayList<>();
        for (Ref<ChunkStore> ref : pipeRefs) {
            FluidContainerComponent fcc = store.getComponent(ref, FluidContainerComponent.getComponentType());
            if (fcc != null)
                result.add(fcc);
        }
        return result;
    }

    private static int executeDirectTransfer(
            @Nonnull FluidSource source,
            int toTransfer,
            @Nonnull List<FluidSinkEntry> sinks) {
        List<FluidSinkEntry> sorted = new ArrayList<>(sinks);
        sorted.sort(Comparator.comparingInt(FluidSinkEntry::distance));

        List<PlannedTransfer> transfers = new ArrayList<>();
        int budget = Math.min(toTransfer, source.fluid().getAmountMb());
        int remaining = budget;

        for (FluidSinkEntry sink : sorted) {
            if (remaining <= 0)
                break;
            int amount = Math.min(remaining, sink.fcc().availableSpace());
            if (amount > 0) {
                transfers.add(new PlannedTransfer(sink.fcc(), amount));
                remaining -= amount;
            }
        }
        if (transfers.isEmpty())
            return 0;

        int totalMoved = budget - remaining;
        source.fcc().drain(totalMoved);

        String fluidId = source.fluid().getFluidId();
        for (PlannedTransfer t : transfers) {
            t.fcc().fill(new FluidStack(fluidId, t.amount(), t.fcc().getCapacity()));
        }
        return 0;
    }

    private static int drainPoolToSinks(
            @Nonnull String fluidId,
            int poolAmount,
            @Nonnull List<FluidSinkEntry> sinks) {
        List<FluidSinkEntry> sorted = new ArrayList<>(sinks);
        sorted.sort(Comparator.comparingInt(FluidSinkEntry::distance));

        for (FluidSinkEntry sink : sorted) {
            if (poolAmount <= 0)
                break;
            int amount = Math.min(poolAmount, sink.fcc().availableSpace());
            if (amount > 0) {
                sink.fcc().fill(new FluidStack(fluidId, amount, sink.fcc().getCapacity()));
                poolAmount -= amount;
            }
        }
        return poolAmount;
    }

    private static void distributeToPipes(
            @Nonnull String fluidId,
            int totalAmount,
            @Nonnull List<FluidContainerComponent> pipeFccs) {
        for (FluidContainerComponent fcc : pipeFccs) {
            fcc.drain(fcc.getCapacity());
        }
        for (FluidContainerComponent fcc : pipeFccs) {
            if (totalAmount <= 0)
                break;
            int filled = fcc.fill(new FluidStack(fluidId, totalAmount, fcc.getCapacity()));
            totalAmount -= filled;
        }
    }

    private static void lockPipes(
            @Nonnull List<Ref<ChunkStore>> pipeRefs,
            @Nonnull String fluidId,
            @Nonnull Store<ChunkStore> store) {
        for (Ref<ChunkStore> pipeRef : pipeRefs) {
            FluidPipeComponent fpc = store.getComponent(pipeRef, FluidPipeComponent.getComponentType());
            if (fpc != null && fpc.getFluidId() == null) {
                fpc.setFluidId(fluidId);
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
            @Nonnull Vector3i arrivedFromOriginPos,
            @Nonnull String typeId) {
        Vector3i originPos = terminal.originPos();
        GridTypeEntry terminalEntry = terminal.component().getEntry(typeId);
        if (terminalEntry == null)
            return null;
        for (FacePlane face : terminalEntry.getFaces()) {
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

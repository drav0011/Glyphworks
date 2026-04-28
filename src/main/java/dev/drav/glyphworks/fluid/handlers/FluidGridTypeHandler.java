package dev.drav.glyphworks.fluid.handlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.math.vector.Vector3i;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.crafting.component.AutoProcessingBenchBlock;
import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.component.FluidPipeComponent;
import dev.drav.glyphworks.fluid.container.FluidContainer;
import dev.drav.glyphworks.grid.component.FacePlane;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.component.GridTypeEntry;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.grid.type.GridTypeHandler;

/**
 * Per-tick handler for the {@code "Fluid"} grid type.
 *
 * <p>
 * All connected pipe nodes in a network form a single virtual pool for one
 * fluid type. Machine nodes with OUTPUT faces push fluid into the pool; machine
 * nodes with INPUT faces pull fluid from the pool. After each tick the pool
 * amount is redistributed equally across all pipe containers so that a network
 * split leaves fluid balanced rather than concentrated at one end.
 *
 * <p>
 * Pool nodes are identified by having only {@code ALLOW_ALL} faces. Tanks and
 * relay pipes both qualify: they participate in the shared virtual pool and
 * their containers hold fluid in transit. Machine nodes (sources, sinks,
 * benches) have at least one non-{@code ALLOW_ALL} face ({@code AllowOutput}
 * or {@code AllowInput}) and interact with the pool via rate-limited push/pull.
 *
 * <p>
 * World-fluid interaction (Remover / Placer) is handled by separate systems
 * outside the grid.
 */
public final class FluidGridTypeHandler implements GridTypeHandler {

    @Override
    public String typeId() {
        return "Fluid";
    }

    // -------------------------------------------------------------------------
    // Data
    // -------------------------------------------------------------------------

    private static final class PoolState {
        final List<FluidContainer> pipeContainers = new ArrayList<>();
        final List<Ref<ChunkStore>> pipeRefs = new ArrayList<>();
        final List<MachineNode> producers = new ArrayList<>();
        final List<MachineNode> consumers = new ArrayList<>();
        @Nullable
        String poolFluidId = null;
        int poolAmount = 0;
        int poolCapacity = 0;
    }

    private record MachineNode(FluidContainer container, FacePlane face, GridTypeEntry entry) {
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
            @Nonnull GridTypeEntry entry,
            @Nonnull Ref<ChunkStore> blockRef) {

        String typeId = entry.getGridType().id();
        GridGraph gridGraph = GlyphworksPlugin.get().getGridModule()
                .getGridGraph(chunkStore.getWorld(), entry.getGridType());
        Vector3i originPos = resolveOriginPosition(component, entry, chunkStore, blockRef, gridGraph);

        if (shouldSkipNonRoot(gridGraph, originPos))
            return;

        PoolState pool = computePool(originPos, gridGraph, chunkStore, store, typeId);

        if (pool.pipeContainers.isEmpty() && pool.producers.isEmpty() && pool.consumers.isEmpty())
            return;

        Set<FluidContainer> pushedContainers = pushMachinesToPool(pool, dt, chunkStore);
        pullPoolToMachines(pool, dt, chunkStore, pushedContainers);
        redistributePool(pool);
        lockPipes(pool, store);
    }

    // -------------------------------------------------------------------------
    // Pool computation
    // -------------------------------------------------------------------------

    @Nonnull
    private static PoolState computePool(
            @Nullable Vector3i originPos,
            @Nullable GridGraph gridGraph,
            @Nonnull ChunkStore chunkStore,
            @Nonnull Store<ChunkStore> store,
            @Nonnull String typeId) {

        PoolState pool = new PoolState();

        // Pass 1: register nodes; collect pipe locks to determine pool fluid type
        // before counting fluid so incompatible fluid is not absorbed into the pool.
        for (Vector3i pos : networkMembers(originPos, gridGraph)) {
            GridLookup lookup = GridLookup.resolve(chunkStore, pos);
            if (lookup == null)
                continue;
            GridTypeEntry memberEntry = lookup.component().getEntry(typeId);
            if (memberEntry == null)
                continue;

            if (isPipeNode(memberEntry)) {
                pool.pipeRefs.add(lookup.blockRef());
                FluidContainerComponent fcc = store.getComponent(lookup.blockRef(),
                        FluidContainerComponent.getComponentType());
                if (fcc != null)
                    pool.pipeContainers.add(fcc.getFluidContainer());
                if (pool.poolFluidId == null) {
                    FluidPipeComponent fpc = store.getComponent(lookup.blockRef(),
                            FluidPipeComponent.getComponentType());
                    if (fpc != null)
                        pool.poolFluidId = fpc.getFluidId();
                }
            } else {
                addMachineToPool(pool, lookup, memberEntry, store);
            }
        }

        // If no pipe is locked, derive fluid type from content.
        if (pool.poolFluidId == null)
            pool.poolFluidId = deriveFluidId(pool.pipeContainers);

        // Pass 2: count fluid and capacity now that the pool type is known.
        for (FluidContainer container : pool.pipeContainers) {
            pool.poolCapacity += containerTotalCapacity(container);
            pool.poolAmount += pool.poolFluidId != null
                    ? fluidAmountOf(container, pool.poolFluidId)
                    : containerFluidAmount(container);
        }

        return pool;
    }

    private static void addMachineToPool(
            @Nonnull PoolState pool,
            @Nonnull GridLookup lookup,
            @Nonnull GridTypeEntry memberEntry,
            @Nonnull Store<ChunkStore> store) {

        for (FacePlane face : memberEntry.getFaces()) {
            FilterType mode = face.getMode();
            FluidContainer container = resolveFluidContainer(store, lookup.blockRef(), face.getContainerKey(), mode);
            if (container == null)
                continue;

            if (mode.allowOutput())
                pool.producers.add(new MachineNode(container, face, memberEntry));
            if (mode.allowInput())
                pool.consumers.add(new MachineNode(container, face, memberEntry));
        }
    }

    // -------------------------------------------------------------------------
    // Push / Pull
    // -------------------------------------------------------------------------

    @Nonnull
    private static Set<FluidContainer> pushMachinesToPool(
            @Nonnull PoolState pool,
            float dt,
            @Nonnull ChunkStore chunkStore) {

        Set<FluidContainer> pushedContainers = Collections.newSetFromMap(new IdentityHashMap<>());

        for (MachineNode producer : pool.producers) {
            if (pool.poolAmount >= pool.poolCapacity)
                break;

            String fluidToPush = pool.poolFluidId != null ? pool.poolFluidId : firstFluidId(producer.container());
            if (fluidToPush == null)
                continue;

            int available = fluidAmountOf(producer.container(), fluidToPush);
            if (available <= 0)
                continue;

            int budget = producer.face().drainAccumulator(
                    producer.entry().getTransferRate() * dt * chunkStore.getWorld().getTps());
            if (budget < 1)
                continue;

            int toPush = Math.min(budget, Math.min(available, pool.poolCapacity - pool.poolAmount));
            int drained = drainFromContainer(producer.container(), fluidToPush, toPush);
            if (drained > 0) {
                pool.poolAmount += drained;
                pushedContainers.add(producer.container());
                if (pool.poolFluidId == null)
                    pool.poolFluidId = fluidToPush;
            }
        }

        return pushedContainers;
    }

    private static void pullPoolToMachines(
            @Nonnull PoolState pool,
            float dt,
            @Nonnull ChunkStore chunkStore,
            @Nonnull Set<FluidContainer> pushedContainers) {

        if (pool.poolAmount <= 0 || pool.poolFluidId == null)
            return;

        for (MachineNode consumer : pool.consumers) {
            if (pool.poolAmount <= 0)
                break;

            if (pushedContainers.contains(consumer.container()))
                continue;

            int budget = consumer.face().drainAccumulator(
                    consumer.entry().getTransferRate() * dt * chunkStore.getWorld().getTps());
            if (budget < 1)
                continue;

            int space = spaceForFluid(consumer.container(), pool.poolFluidId);
            if (space <= 0)
                continue;

            int toPull = Math.min(budget, Math.min(pool.poolAmount, space));
            FluidStack toAdd = new FluidStack(pool.poolFluidId, toPull, consumer.container().getCapacityMbPerSlot());
            ItemStackTransaction tx = consumer.container().addFluidStack(toAdd);
            FluidStack remainder = tx.getRemainder() instanceof FluidStack fs ? fs : null;
            int added = toPull - (remainder != null ? remainder.getQuantity() : 0);
            pool.poolAmount -= added;
        }
    }

    // -------------------------------------------------------------------------
    // Redistribute
    // -------------------------------------------------------------------------

    private static void redistributePool(@Nonnull PoolState pool) {
        if (pool.poolFluidId == null)
            return;

        // Drain only pool-typed fluid; incompatible fluid in a container is left
        // intact.
        for (FluidContainer pipe : pool.pipeContainers) {
            int amount = fluidAmountOf(pipe, pool.poolFluidId);
            if (amount > 0)
                drainFromContainer(pipe, pool.poolFluidId, amount);
        }

        if (pool.poolAmount <= 0 || pool.pipeContainers.isEmpty())
            return;

        // Only redistribute to containers that can accept the pool fluid type.
        List<FluidContainer> eligible = new ArrayList<>();
        long eligibleCapacity = 0;
        for (FluidContainer pipe : pool.pipeContainers) {
            if (spaceForFluid(pipe, pool.poolFluidId) > 0) {
                eligible.add(pipe);
                eligibleCapacity += containerTotalCapacity(pipe);
            }
        }

        if (eligible.isEmpty() || eligibleCapacity <= 0)
            return;

        int distributed = 0;
        for (int i = 0; i < eligible.size(); i++) {
            FluidContainer pipe = eligible.get(i);
            int share;
            if (i == eligible.size() - 1) {
                share = pool.poolAmount - distributed;
            } else {
                share = (int) ((long) pool.poolAmount * containerTotalCapacity(pipe) / eligibleCapacity);
            }
            if (share <= 0)
                continue;
            pipe.addFluidStack(new FluidStack(pool.poolFluidId, share, pipe.getCapacityMbPerSlot()), false, false);
            distributed += share;
        }
    }

    // -------------------------------------------------------------------------
    // Lock
    // -------------------------------------------------------------------------

    private static void lockPipes(@Nonnull PoolState pool, @Nonnull Store<ChunkStore> store) {
        if (pool.poolFluidId == null)
            return;
        for (Ref<ChunkStore> pipeRef : pool.pipeRefs) {
            FluidPipeComponent fpc = store.getComponent(pipeRef, FluidPipeComponent.getComponentType());
            if (fpc != null && fpc.getFluidId() == null)
                fpc.setFluidId(pool.poolFluidId);
        }
    }

    // -------------------------------------------------------------------------
    // Origin / root helpers (unchanged from previous model)
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

    @Nonnull
    private static Set<Vector3i> networkMembers(
            @Nullable Vector3i originPos,
            @Nullable GridGraph gridGraph) {
        if (originPos == null)
            return Set.of();
        if (gridGraph == null)
            return Set.of(originPos);
        Vector3i root = gridGraph.getComponentRoot(originPos);
        return root != null ? gridGraph.getComponentMembers(root) : Set.of(originPos);
    }

    private static boolean isPipeNode(@Nonnull GridTypeEntry entry) {
        for (FacePlane face : entry.getFaces()) {
            if (face.getMode() != FilterType.ALLOW_ALL)
                return false;
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Container helpers
    // -------------------------------------------------------------------------

    private static int containerTotalCapacity(@Nonnull FluidContainer container) {
        return container.getCapacity() * container.getCapacityMbPerSlot();
    }

    private static int containerFluidAmount(@Nonnull FluidContainer container) {
        int total = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            FluidStack stack = container.getFluidStack(slot);
            if (stack != null)
                total += stack.getQuantity();
        }
        return total;
    }

    private static int fluidAmountOf(@Nonnull FluidContainer container, @Nonnull String fluidId) {
        int total = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            FluidStack stack = container.getFluidStack(slot);
            if (stack != null && fluidId.equals(stack.getFluidId()))
                total += stack.getQuantity();
        }
        return total;
    }

    private static int spaceForFluid(@Nonnull FluidContainer container, @Nonnull String fluidId) {
        int total = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            FluidStack stack = container.getFluidStack(slot);
            if (stack == null) {
                total += container.getCapacityMbPerSlot();
            } else if (fluidId.equals(stack.getFluidId())) {
                total += Math.max(0, container.getCapacityMbPerSlot() - stack.getQuantity());
            }
        }
        return total;
    }

    private static int drainFromContainer(
            @Nonnull FluidContainer container,
            @Nonnull String fluidId,
            int amount) {
        int remaining = amount;
        for (short slot = 0; slot < container.getCapacity() && remaining > 0; slot++) {
            FluidStack stack = container.getFluidStack(slot);
            if (stack == null || !fluidId.equals(stack.getFluidId()))
                continue;
            ItemStackSlotTransaction tx = container.removeFluidStackFromSlot(slot, remaining, false, false);
            if (tx.succeeded()) {
                FluidStack removed = tx.getOutput() instanceof FluidStack fs ? fs : null;
                if (removed != null)
                    remaining -= removed.getQuantity();
            }
        }
        return amount - remaining;
    }

    @Nullable
    private static String firstFluidId(@Nonnull FluidContainer container) {
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            FluidStack stack = container.getFluidStack(slot);
            if (stack != null)
                return stack.getFluidId();
        }
        return null;
    }

    @Nullable
    private static String deriveFluidId(@Nonnull List<FluidContainer> containers) {
        for (FluidContainer container : containers) {
            String id = firstFluidId(container);
            if (id != null)
                return id;
        }
        return null;
    }

    // TODO: better expansible resolution for containers, cannot be adding component
    // types to this class every time we add a new block with a container.
    @Nullable
    private static FluidContainer resolveFluidContainer(
            @Nonnull Store<ChunkStore> store,
            @Nonnull Ref<ChunkStore> ref,
            @Nullable String key,
            @Nonnull FilterType mode) {
        AutoProcessingBenchBlock apbb = store.getComponent(ref, AutoProcessingBenchBlock.getComponentType());
        if (apbb != null) {
            if (key == null)
                return null;
            return switch (key) {
                case "fluidfuel" -> apbb.getFluidFuelContainer();
                case "fluidinput" -> apbb.getFluidInputContainer();
                case "fluidoutput" -> apbb.getFluidOutputContainer();
                default -> null;
            };
        }

        FluidContainerComponent fcc = store.getComponent(ref, FluidContainerComponent.getComponentType());
        return fcc != null ? fcc.getFluidContainer() : null;
    }
}

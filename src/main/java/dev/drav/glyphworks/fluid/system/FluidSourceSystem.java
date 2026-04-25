package dev.drav.glyphworks.fluid.system;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.component.FluidSourceComponent;
import dev.drav.glyphworks.fluid.container.FluidContainer;
import dev.drav.glyphworks.fluid.FluidStack;

/**
 * Ticking system for creative fluid source blocks.
 *
 * <p>
 * Each tick, for every block that has both a
 * {@link FluidSourceComponent} and a {@link FluidContainerComponent},
 * the system forces the container to be completely full with the fluid type
 * specified by the source component. This simulates an infinite fluid supply.
 */
public final class FluidSourceSystem extends EntityTickingSystem<ChunkStore> {

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(
                FluidSourceComponent.getComponentType(),
                FluidContainerComponent.getComponentType());
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> chunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        FluidSourceComponent source = chunk.getComponent(index, FluidSourceComponent.getComponentType());
        if (source == null) {
            return;
        }

        FluidContainerComponent fcc = chunk.getComponent(index, FluidContainerComponent.getComponentType());
        if (fcc == null) {
            return;
        }

        FluidContainer fc = fcc.getFluidContainer();
        fc.clear();

        String selectedFluidId = source.getSelectedFluidId();
        if (selectedFluidId == null) {
            return;
        }

        int slotCapacityMb = fc.getCapacityMbPerSlot();
        for (short slot = 0; slot < fc.getCapacity(); slot++) {
            FluidStack maxStack = new FluidStack(selectedFluidId, slotCapacityMb, slotCapacityMb);
            fc.addFluidStackToSlot(slot, maxStack, true, false);
        }
    }
}

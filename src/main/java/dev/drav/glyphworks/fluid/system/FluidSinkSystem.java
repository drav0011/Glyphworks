package dev.drav.glyphworks.fluid.system;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.component.FluidSinkComponent;

/**
 * Ticking system for creative fluid sink blocks.
 *
 * <p>
 * Each tick, for every block that has both a {@link FluidSinkComponent}
 * and a {@link FluidContainerComponent}, the system drains the container
 * completely, destroying whatever fluid the grid transferred into it. This
 * simulates an infinite drain with no back-pressure.
 */
public final class FluidSinkSystem extends EntityTickingSystem<ChunkStore> {

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(
                FluidSinkComponent.getComponentType(),
                FluidContainerComponent.getComponentType());
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> chunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        FluidContainerComponent fcc = chunk.getComponent(index, FluidContainerComponent.getComponentType());
        if (fcc == null) {
            return;
        }

        fcc.drain(fcc.getAmount());
    }
}

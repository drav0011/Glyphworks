package dev.drav.glyphworks.crafting.system;

import javax.annotation.Nonnull;

import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.crafting.component.AutoProcessingBenchBlock;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;

/**
 * Automatically activates a {@link ProcessingBenchBlock} each tick when it is
 * an {@link AutoProcessingBenchBlock} (mana-powered) machine that has items in
 * the input container and sufficient liquid mana to run.
 *
 * <p>
 * Deactivation is handled by {@link ProcessingBenchManaSystem} when mana runs
 * out, and by the vanilla {@code ProcessingBenchBlock} tick when input is empty.
 */
public final class ProcessingBenchAutoStartSystem extends EntityTickingSystem<ChunkStore> {

    /** Required fluid ID for mana consumption. */
    private static final String MANA_FLUID_ID = "Glyphworks_Fluid_Mana";

    public ProcessingBenchAutoStartSystem() {
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(ProcessingBenchBlock.getComponentType(), AutoProcessingBenchBlock.getComponentType());
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        ProcessingBenchBlock pbb = archetypeChunk.getComponent(index, ProcessingBenchBlock.getComponentType());
        // Skip if already running or bench config not yet initialised.
        if (pbb == null || pbb.isActive() || pbb.getProcessingBench() == null)
            return;

        BenchBlock benchBlock = archetypeChunk.getComponent(index, BenchBlock.getComponentType());
        if (benchBlock == null)
            return;

        // Require at least one item in the input container.
        if (pbb.getInputContainer().isEmpty())
            return;

        // Require mana in the fluid container if one is present.
        FluidContainerComponent fluidContainer = archetypeChunk.getComponent(
                index, FluidContainerComponent.getComponentType());
        if (fluidContainer != null) {
            if (fluidContainer.isEmpty() || !MANA_FLUID_ID.equals(fluidContainer.getFluidId()))
                return;
        }

        BlockModule.BlockStateInfo blockStateInfo = archetypeChunk.getComponent(index,
                BlockModule.BlockStateInfo.getComponentType());
        pbb.setActive(true, benchBlock, blockStateInfo);
    }
}

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
 * Drains liquid mana from the bench's {@link FluidContainerComponent} each
 * tick while a {@link ProcessingBenchBlock} is actively processing.
 *
 * <p>
 * When mana runs out mid-process the bench is deactivated. The
 * {@link ProcessingBenchAutoStartSystem} will re-start it automatically once
 * mana is replenished and items are still present.
 */
public final class ProcessingBenchManaSystem extends EntityTickingSystem<ChunkStore> {

    private static final String MANA_FLUID_ID = "Glyphworks_Fluid_Mana";
    private static final int DEFAULT_DRAIN_PER_TICK = 1;

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(ProcessingBenchBlock.getComponentType(), FluidContainerComponent.getComponentType());
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        ProcessingBenchBlock pbb = archetypeChunk.getComponent(index, ProcessingBenchBlock.getComponentType());
        if (pbb == null)
            return;

        FluidContainerComponent manaContainer = archetypeChunk.getComponent(index, FluidContainerComponent.getComponentType());
        if (manaContainer == null)
            return;

        int drain = drainAmount(archetypeChunk, index);
        if (hasSufficientMana(manaContainer, drain)) {
            if (pbb.isActive())
                manaContainer.drain(drain);
            return;
        }

        drainRemainingMana(manaContainer);
        // For fuel-less benches, ProcessingBenchBlock.advanceProcessing ignores
        // isActive() and advances progress unconditionally. Resetting inputProgress
        // to 0 each tick prevents recipe completion when mana is absent.
        pbb.setInputProgress(0.0f);
        stopBench(pbb, archetypeChunk, index);
    }

    private int drainAmount(@Nonnull ArchetypeChunk<ChunkStore> archetypeChunk, int index) {
        AutoProcessingBenchBlock apbb = archetypeChunk.getComponent(
                index, AutoProcessingBenchBlock.getComponentType());
        if (apbb == null)
            return DEFAULT_DRAIN_PER_TICK;
        return Math.max(1, Math.round(apbb.getManaConsumptionRate()));
    }

    private boolean hasSufficientMana(@Nonnull FluidContainerComponent manaContainer, int required) {
        return !manaContainer.isEmpty()
                && MANA_FLUID_ID.equals(manaContainer.getFluidId())
                && manaContainer.getAmount() >= required;
    }

    private void drainRemainingMana(@Nonnull FluidContainerComponent manaContainer) {
        if (!manaContainer.isEmpty() && MANA_FLUID_ID.equals(manaContainer.getFluidId())) {
            manaContainer.drain(manaContainer.getAmount());
        }
    }

    private void stopBench(
            @Nonnull ProcessingBenchBlock pbb,
            @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
            int index) {

        BenchBlock benchBlock = archetypeChunk.getComponent(index, BenchBlock.getComponentType());
        BlockModule.BlockStateInfo blockStateInfo = archetypeChunk.getComponent(
                index, BlockModule.BlockStateInfo.getComponentType());

        if (benchBlock != null && blockStateInfo != null) {
            pbb.setActive(false, benchBlock, blockStateInfo);
        }
    }
}

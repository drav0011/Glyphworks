package dev.drav.glyphworks.crafting;

import dev.drav.glyphworks.crafting.system.AutoCraftingBenchRecipeLockTests;
import dev.drav.glyphworks.crafting.system.AutoProcessingBenchFlowTests;
import dev.drav.glyphworks.crafting.system.AutoProcessingBenchFuelTests;
import dev.drav.glyphworks.crafting.system.AutoProcessingBenchGridTests;
import dev.drav.glyphworks.crafting.system.AutoProcessingBenchSetupTests;

public final class CraftingTestRegistrations {

    private CraftingTestRegistrations() {
    }

    public static void registerAll() {
        AutoProcessingBenchSetupTests.register("crafting");
        AutoProcessingBenchFlowTests.register("crafting");
        AutoCraftingBenchRecipeLockTests.register("crafting");
        AutoProcessingBenchGridTests.register("crafting");
        AutoProcessingBenchFuelTests.register("crafting");
    }
}
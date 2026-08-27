package dev.drav.glyphworks.test;

import dev.drav.glyphworks.crafting.CraftingTestRegistrations;
import dev.drav.glyphworks.fluid.FluidTestRegistrations;
import dev.drav.glyphworks.grid.GridTestRegistrations;
import dev.drav.glyphworks.item.ItemTestRegistrations;
import dev.drav.glyphworks.test.tests.TestFrameworkTests;
import dev.drav.glyphworks.util.TriggerVolumeGuardTests;

public final class TestRegistrations {

    private TestRegistrations() {
    }

    public static void registerAll() {
        TestFrameworkTests.register("smoke");
        GridTestRegistrations.registerAll();
        FluidTestRegistrations.registerAll();
        ItemTestRegistrations.registerAll();
        CraftingTestRegistrations.registerAll();
        TriggerVolumeGuardTests.register("protection");
    }
}
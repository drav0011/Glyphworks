package dev.drav.glyphworks.fluid;

import dev.drav.glyphworks.fluid.component.FluidContainerComponentTests;
import dev.drav.glyphworks.fluid.component.FluidPipeComponentTests;
import dev.drav.glyphworks.fluid.component.FluidPlacerComponentTests;
import dev.drav.glyphworks.fluid.component.FluidRemoverComponentTests;
import dev.drav.glyphworks.fluid.component.FluidSourceComponentTests;
import dev.drav.glyphworks.fluid.system.FluidGridTransferTests;
import dev.drav.glyphworks.fluid.system.FluidPlacerSystemTests;
import dev.drav.glyphworks.fluid.system.FluidRemoverSystemTests;
import dev.drav.glyphworks.fluid.system.FluidSinkSystemTests;
import dev.drav.glyphworks.fluid.system.FluidSourceSystemTests;

public final class FluidTestRegistrations {

    private FluidTestRegistrations() {
    }

    public static void registerAll() {
        FluidGridTransferTests.register("fluid");
        FluidSourceSystemTests.register("fluid");
        FluidSinkSystemTests.register("fluid");
        FluidPlacerSystemTests.register("fluid");
        FluidRemoverSystemTests.register("fluid");
        FluidContainerComponentTests.register("fluid");
        FluidPipeComponentTests.register("fluid");
        FluidSourceComponentTests.register("fluid");
        FluidPlacerComponentTests.register("fluid");
        FluidRemoverComponentTests.register("fluid");
    }
}
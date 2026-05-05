package dev.drav.glyphworks.grid;

import dev.drav.glyphworks.grid.component.GridComponentTests;
import dev.drav.glyphworks.grid.component.GridFaceUtilTests;
import dev.drav.glyphworks.grid.system.GridBlockChangeTests;
import dev.drav.glyphworks.grid.system.GridConnectionTests;
import dev.drav.glyphworks.grid.system.GridGraphTests;
import dev.drav.glyphworks.grid.system.PipeConnectionTests;

public final class GridTestRegistrations {

    private GridTestRegistrations() {
    }

    public static void registerAll() {
        PipeConnectionTests.register("grid");
        GridGraphTests.register("grid");
        GridConnectionTests.register("grid");
        GridFaceUtilTests.register("grid");
        GridComponentTests.register("grid");
        GridBlockChangeTests.register("grid");
    }
}
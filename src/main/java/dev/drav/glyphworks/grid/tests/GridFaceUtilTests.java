package dev.drav.glyphworks.grid.tests;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockFace;

import dev.drav.glyphworks.grid.component.FaceMode;
import dev.drav.glyphworks.grid.util.GridFaceUtil;
import dev.drav.glyphworks.test.Steps;
import dev.drav.glyphworks.test.TestCase;
import dev.drav.glyphworks.test.TestRegistry;
import dev.drav.glyphworks.test.TestSuite;

/**
 * Suite {@code "grid_face_util"} — tests {@link GridFaceUtil} static helpers.
 *
 * <p>All tests are purely in-memory (no block placement, no world interaction).
 * Each {@link TestCase} uses a {@code 1×1×1} area and consists of a single
 * {@link Steps#assertThat} step, making the suite instant to run.
 */
public final class GridFaceUtilTests {

    private GridFaceUtilTests() {}

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("grid_face_util")
                .test(linkableBidirBidir())
                .test(linkableInputOutput())
                .test(linkableOutputInput())
                .test(linkableInputBidir())
                .test(linkableOutputBidir())
                .test(notLinkableInputInput())
                .test(notLinkableOutputOutput())
                .test(notLinkableClosedAny())
                .test(oppositeAllFaces())
                .test(addOffsetAllFaces());
    }

    // -------------------------------------------------------------------------
    // areLinkable — compatible pairs (should return true)
    // -------------------------------------------------------------------------

    private static TestCase linkableBidirBidir() {
        return new TestCase("linkable_bidir_bidir", 1, 1, 1)
                .step(Steps.assertThat(
                        ctx -> GridFaceUtil.areLinkable(FaceMode.BIDIRECTIONAL, FaceMode.BIDIRECTIONAL),
                        "areLinkable(BIDIRECTIONAL, BIDIRECTIONAL) == true"));
    }

    private static TestCase linkableInputOutput() {
        return new TestCase("linkable_input_output", 1, 1, 1)
                .step(Steps.assertThat(
                        ctx -> GridFaceUtil.areLinkable(FaceMode.INPUT, FaceMode.OUTPUT),
                        "areLinkable(INPUT, OUTPUT) == true"));
    }

    private static TestCase linkableOutputInput() {
        return new TestCase("linkable_output_input", 1, 1, 1)
                .step(Steps.assertThat(
                        ctx -> GridFaceUtil.areLinkable(FaceMode.OUTPUT, FaceMode.INPUT),
                        "areLinkable(OUTPUT, INPUT) == true"));
    }

    private static TestCase linkableInputBidir() {
        return new TestCase("linkable_input_bidir", 1, 1, 1)
                .step(Steps.assertThat(
                        ctx -> GridFaceUtil.areLinkable(FaceMode.INPUT, FaceMode.BIDIRECTIONAL)
                            && GridFaceUtil.areLinkable(FaceMode.BIDIRECTIONAL, FaceMode.INPUT),
                        "areLinkable(INPUT, BIDIR) and areLinkable(BIDIR, INPUT) are both true"));
    }

    private static TestCase linkableOutputBidir() {
        return new TestCase("linkable_output_bidir", 1, 1, 1)
                .step(Steps.assertThat(
                        ctx -> GridFaceUtil.areLinkable(FaceMode.OUTPUT, FaceMode.BIDIRECTIONAL)
                            && GridFaceUtil.areLinkable(FaceMode.BIDIRECTIONAL, FaceMode.OUTPUT),
                        "areLinkable(OUTPUT, BIDIR) and areLinkable(BIDIR, OUTPUT) are both true"));
    }

    // -------------------------------------------------------------------------
    // areLinkable — incompatible pairs (should return false)
    // -------------------------------------------------------------------------

    private static TestCase notLinkableInputInput() {
        return new TestCase("not_linkable_input_input", 1, 1, 1)
                .step(Steps.assertThat(
                        ctx -> !GridFaceUtil.areLinkable(FaceMode.INPUT, FaceMode.INPUT),
                        "areLinkable(INPUT, INPUT) == false"));
    }

    private static TestCase notLinkableOutputOutput() {
        return new TestCase("not_linkable_output_output", 1, 1, 1)
                .step(Steps.assertThat(
                        ctx -> !GridFaceUtil.areLinkable(FaceMode.OUTPUT, FaceMode.OUTPUT),
                        "areLinkable(OUTPUT, OUTPUT) == false"));
    }

    private static TestCase notLinkableClosedAny() {
        return new TestCase("not_linkable_closed_any", 1, 1, 1)
                .step(Steps.assertThat(
                        ctx -> !GridFaceUtil.areLinkable(FaceMode.CLOSED, FaceMode.BIDIRECTIONAL)
                            && !GridFaceUtil.areLinkable(FaceMode.BIDIRECTIONAL, FaceMode.CLOSED)
                            && !GridFaceUtil.areLinkable(FaceMode.CLOSED, FaceMode.INPUT)
                            && !GridFaceUtil.areLinkable(FaceMode.CLOSED, FaceMode.OUTPUT),
                        "areLinkable returns false for any pair involving CLOSED"));
    }

    // -------------------------------------------------------------------------
    // opposite() — correct inverse for all six cardinal faces
    // -------------------------------------------------------------------------

    private static TestCase oppositeAllFaces() {
        return new TestCase("opposite_all_faces", 1, 1, 1)
                .step(Steps.assertThat(
                        ctx -> GridFaceUtil.opposite(BlockFace.North) == BlockFace.South
                            && GridFaceUtil.opposite(BlockFace.South) == BlockFace.North
                            && GridFaceUtil.opposite(BlockFace.East)  == BlockFace.West
                            && GridFaceUtil.opposite(BlockFace.West)  == BlockFace.East
                            && GridFaceUtil.opposite(BlockFace.Up)    == BlockFace.Down
                            && GridFaceUtil.opposite(BlockFace.Down)  == BlockFace.Up,
                        "opposite() returns the correct inverse for all 6 cardinal BlockFace values"));
    }

    // -------------------------------------------------------------------------
    // addOffset(Vector3i, BlockFace) — moves position correctly per face
    // -------------------------------------------------------------------------

    private static TestCase addOffsetAllFaces() {
        return new TestCase("add_offset_all_faces", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    Vector3i o = new Vector3i(0, 0, 0);
                    return GridFaceUtil.addOffset(o, BlockFace.North).equals(new Vector3i( 0,  0, -1))
                        && GridFaceUtil.addOffset(o, BlockFace.South).equals(new Vector3i( 0,  0,  1))
                        && GridFaceUtil.addOffset(o, BlockFace.East) .equals(new Vector3i( 1,  0,  0))
                        && GridFaceUtil.addOffset(o, BlockFace.West) .equals(new Vector3i(-1,  0,  0))
                        && GridFaceUtil.addOffset(o, BlockFace.Up)   .equals(new Vector3i( 0,  1,  0))
                        && GridFaceUtil.addOffset(o, BlockFace.Down) .equals(new Vector3i( 0, -1,  0));
                }, "addOffset(origin, face) moves position by exactly one step in the expected direction"));
    }
}

package dev.drav.glyphworks.grid.tests.component;

import org.joml.Vector3i;

import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;

import dev.drav.glyphworks.grid.component.FaceMode;
import dev.drav.glyphworks.grid.component.FacePlane;
import dev.drav.glyphworks.grid.component.GridTypeEntry;
import dev.drav.glyphworks.grid.util.GridFaceUtil;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "grid_face_util"} — tests {@link GridFaceUtil} static helpers.
 *
 * <p>
 * All tests are purely in-memory (no block placement, no world interaction).
 * Each {@link TestCase} uses a {@code 1×1×1} area and consists of a single
 * {@link Steps#assertThat} step, making the suite instant to run.
 */
public final class GridFaceUtilTests {

    private GridFaceUtilTests() {
    }

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
                .test(addOffsetAllFaces())
                .test(rotateBlockFaceIdentityNoOp())
                .test(rotateBlockFaceNoneInput())
                .test(rotateBlockFaceYawNinety())
                .test(rotateBlockFaceYawOneEighty())
                .test(rotateFacePositionIdentityNoOp())
                .test(rotateFacePositionYawNinety())
                .test(addOffsetVec())
                .test(findMatchingFaceFound())
                .test(findMatchingFaceWrongNormal())
                .test(findMatchingFaceWrongPosition());
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
                                && GridFaceUtil.opposite(BlockFace.East) == BlockFace.West
                                && GridFaceUtil.opposite(BlockFace.West) == BlockFace.East
                                && GridFaceUtil.opposite(BlockFace.Up) == BlockFace.Down
                                && GridFaceUtil.opposite(BlockFace.Down) == BlockFace.Up,
                        "opposite() returns the correct inverse for all 6 cardinal BlockFace values"));
    }

    // -------------------------------------------------------------------------
    // addOffset(Vector3i, BlockFace) — moves position correctly per face
    // -------------------------------------------------------------------------

    private static TestCase addOffsetAllFaces() {
        return new TestCase("add_offset_all_faces", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    Vector3i o = new Vector3i(0, 0, 0);
                    return GridFaceUtil.addOffset(o, BlockFace.North).equals(new Vector3i(0, 0, -1))
                            && GridFaceUtil.addOffset(o, BlockFace.South).equals(new Vector3i(0, 0, 1))
                            && GridFaceUtil.addOffset(o, BlockFace.East).equals(new Vector3i(1, 0, 0))
                            && GridFaceUtil.addOffset(o, BlockFace.West).equals(new Vector3i(-1, 0, 0))
                            && GridFaceUtil.addOffset(o, BlockFace.Up).equals(new Vector3i(0, 1, 0))
                            && GridFaceUtil.addOffset(o, BlockFace.Down).equals(new Vector3i(0, -1, 0));
                }, "addOffset(origin, face) moves position by exactly one step in the expected direction"));
    }

    // -------------------------------------------------------------------------
    // rotateBlockFace — identity rotation is a no-op for all cardinal faces
    // -------------------------------------------------------------------------

    private static TestCase rotateBlockFaceIdentityNoOp() {
        return new TestCase("rotate_block_face_identity_no_op", 1, 1, 1)
                .step(Steps.assertThat(
                        ctx -> GridFaceUtil.rotateBlockFace(BlockFace.North, RotationTuple.NONE) == BlockFace.North
                                && GridFaceUtil.rotateBlockFace(BlockFace.South, RotationTuple.NONE) == BlockFace.South
                                && GridFaceUtil.rotateBlockFace(BlockFace.East, RotationTuple.NONE) == BlockFace.East
                                && GridFaceUtil.rotateBlockFace(BlockFace.West, RotationTuple.NONE) == BlockFace.West
                                && GridFaceUtil.rotateBlockFace(BlockFace.Up, RotationTuple.NONE) == BlockFace.Up
                                && GridFaceUtil.rotateBlockFace(BlockFace.Down, RotationTuple.NONE) == BlockFace.Down,
                        "rotateBlockFace with NONE rotation returns the same face for all 6 cardinal directions"));
    }

    // -------------------------------------------------------------------------
    // rotateBlockFace — BlockFace.None always returns BlockFace.None
    // -------------------------------------------------------------------------

    private static TestCase rotateBlockFaceNoneInput() {
        return new TestCase("rotate_block_face_none_input", 1, 1, 1)
                .step(Steps.assertThat(
                        ctx -> GridFaceUtil.rotateBlockFace(BlockFace.None, RotationTuple.NONE) == BlockFace.None
                                && GridFaceUtil.rotateBlockFace(BlockFace.None,
                                        RotationTuple.of(Rotation.Ninety, Rotation.None,
                                                Rotation.None)) == BlockFace.None,
                        "rotateBlockFace(None, ...) always returns BlockFace.None regardless of rotation"));
    }

    // -------------------------------------------------------------------------
    // rotateBlockFace — yaw 90° maps N→W, E→N, S→E, W→S, Up and Down unchanged
    // -------------------------------------------------------------------------

    private static TestCase rotateBlockFaceYawNinety() {
        return new TestCase("rotate_block_face_yaw_ninety", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    RotationTuple yaw90 = RotationTuple.of(Rotation.Ninety, Rotation.None, Rotation.None);
                    return GridFaceUtil.rotateBlockFace(BlockFace.North, yaw90) == BlockFace.West
                            && GridFaceUtil.rotateBlockFace(BlockFace.East, yaw90) == BlockFace.North
                            && GridFaceUtil.rotateBlockFace(BlockFace.South, yaw90) == BlockFace.East
                            && GridFaceUtil.rotateBlockFace(BlockFace.West, yaw90) == BlockFace.South
                            && GridFaceUtil.rotateBlockFace(BlockFace.Up, yaw90) == BlockFace.Up
                            && GridFaceUtil.rotateBlockFace(BlockFace.Down, yaw90) == BlockFace.Down;
                }, "rotateBlockFace with yaw=90\u00b0: N\u2192W, E\u2192N, S\u2192E, W\u2192S, Up and Down unchanged"));
    }

    // -------------------------------------------------------------------------
    // rotateBlockFace — yaw 180° maps N→S, S→N, E→W, W→E, Up and Down unchanged
    // -------------------------------------------------------------------------

    private static TestCase rotateBlockFaceYawOneEighty() {
        return new TestCase("rotate_block_face_yaw_one_eighty", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    RotationTuple yaw180 = RotationTuple.of(Rotation.OneEighty, Rotation.None, Rotation.None);
                    return GridFaceUtil.rotateBlockFace(BlockFace.North, yaw180) == BlockFace.South
                            && GridFaceUtil.rotateBlockFace(BlockFace.South, yaw180) == BlockFace.North
                            && GridFaceUtil.rotateBlockFace(BlockFace.East, yaw180) == BlockFace.West
                            && GridFaceUtil.rotateBlockFace(BlockFace.West, yaw180) == BlockFace.East
                            && GridFaceUtil.rotateBlockFace(BlockFace.Up, yaw180) == BlockFace.Up
                            && GridFaceUtil.rotateBlockFace(BlockFace.Down, yaw180) == BlockFace.Down;
                }, "rotateBlockFace with yaw=180\u00b0: N\u2192S, S\u2192N, E\u2192W, W\u2192E, Up and Down unchanged"));
    }

    // -------------------------------------------------------------------------
    // rotateFacePosition — identity rotation is a no-op
    // -------------------------------------------------------------------------

    private static TestCase rotateFacePositionIdentityNoOp() {
        return new TestCase("rotate_face_position_identity_no_op", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    Vector3i pos = new Vector3i(3, 7, -2);
                    Vector3i result = GridFaceUtil.rotateFacePosition(pos, RotationTuple.NONE);
                    return result.equals(pos);
                }, "rotateFacePosition with NONE rotation returns the position unchanged"));
    }

    // -------------------------------------------------------------------------
    // rotateFacePosition — yaw 90° rotates the offset vector correctly
    // -------------------------------------------------------------------------

    private static TestCase rotateFacePositionYawNinety() {
        return new TestCase("rotate_face_position_yaw_ninety", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    RotationTuple yaw90 = RotationTuple.of(Rotation.Ninety, Rotation.None, Rotation.None);
                    // rotateY Ninety: (x,y,z) -> (z,y,-x)
                    // (1,0,0) -> (0,0,-1)
                    // (0,0,1) -> (1,0,0)
                    return GridFaceUtil.rotateFacePosition(new Vector3i(1, 0, 0), yaw90).equals(new Vector3i(0, 0, -1))
                            && GridFaceUtil.rotateFacePosition(new Vector3i(0, 0, 1), yaw90)
                                    .equals(new Vector3i(1, 0, 0));
                }, "rotateFacePosition with yaw=90\u00b0: (1,0,0)\u2192(0,0,-1), (0,0,1)\u2192(1,0,0)"));
    }

    // -------------------------------------------------------------------------
    // addOffset(Vector3i, Vector3i) — adds component-wise
    // -------------------------------------------------------------------------

    private static TestCase addOffsetVec() {
        return new TestCase("add_offset_vec", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    Vector3i result = GridFaceUtil.addOffset(new Vector3i(1, 2, 3), new Vector3i(10, 20, 30));
                    return result.equals(new Vector3i(11, 22, 33));
                }, "addOffset(pos, vec) adds vector components to the position"));
    }

    // -------------------------------------------------------------------------
    // findMatchingFace — face is found when normal and world position both match
    // -------------------------------------------------------------------------

    private static TestCase findMatchingFaceFound() {
        return new TestCase("find_matching_face_found", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    GridTypeEntry entry = new GridTypeEntry();
                    entry.addFace(new FacePlane(new Vector3i(0, 0, 0), BlockFace.East, FaceMode.BIDIRECTIONAL));
                    return GridFaceUtil.findMatchingFace(
                            entry, new Vector3i(0, 0, 0), RotationTuple.NONE,
                            BlockFace.East, new Vector3i(0, 0, 0)) != null;
                }, "findMatchingFace returns the face when normal and world position both match"));
    }

    // -------------------------------------------------------------------------
    // findMatchingFace — returns null when the required normal does not match
    // -------------------------------------------------------------------------

    private static TestCase findMatchingFaceWrongNormal() {
        return new TestCase("find_matching_face_wrong_normal", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    GridTypeEntry entry = new GridTypeEntry();
                    entry.addFace(new FacePlane(new Vector3i(0, 0, 0), BlockFace.East, FaceMode.BIDIRECTIONAL));
                    return GridFaceUtil.findMatchingFace(
                            entry, new Vector3i(0, 0, 0), RotationTuple.NONE,
                            BlockFace.North, new Vector3i(0, 0, 0)) == null;
                }, "findMatchingFace returns null when the required normal does not match any face"));
    }

    // -------------------------------------------------------------------------
    // findMatchingFace — returns null when normal matches but world position does
    // not
    // -------------------------------------------------------------------------

    private static TestCase findMatchingFaceWrongPosition() {
        return new TestCase("find_matching_face_wrong_position", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    GridTypeEntry entry = new GridTypeEntry();
                    entry.addFace(new FacePlane(new Vector3i(0, 0, 0), BlockFace.East, FaceMode.BIDIRECTIONAL));
                    return GridFaceUtil.findMatchingFace(
                            entry, new Vector3i(0, 0, 0), RotationTuple.NONE,
                            BlockFace.East, new Vector3i(5, 5, 5)) == null;
                }, "findMatchingFace returns null when the normal matches but the world position does not"));
    }
}

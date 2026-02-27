package dev.drav.glyphworks.transfer.component;

/**
 * Defines the connection mode for a side of a transfer block.
 * Each side (north, south, east, west, up, down) can have one of these modes.
 */
public enum FaceMode {
    /**
     * This side can only receive items (acts as input).
     * Creates incoming edges when adjacent to OUTPUT or BIDIRECTIONAL sides.
     */
    INPUT,

    /**
     * This side can only send items (acts as output).
     * Creates outgoing edges when adjacent to INPUT or BIDIRECTIONAL sides.
     */
    OUTPUT,

    /**
     * This side can both send and receive items.
     * Creates bidirectional edges with other BIDIRECTIONAL sides.
     * Creates appropriate directional edges with INPUT/OUTPUT sides.
     */
    BIDIRECTIONAL,

    /**
     * This side does not connect to anything.
     * No edges are created for this side.
     */
    CLOSED
}


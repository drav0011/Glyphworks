package dev.drav.glyphworks.transfer.graph;

import java.util.UUID;

/**
 * A directed connection between two transfer nodes in the {@link WorldGraph}.
 *
 * <p>Edges are created when neighbouring blocks with a {@code TransferComponent}
 * are placed next to each other and their faces collide.
 *
 * <p>Bidirectional edges are stored as a single {@code GraphEdge} with
 * {@link #isBidirectional()} == {@code true}. {@link WorldGraph} will automatically
 * create the matching reverse direction when the edge is inserted.
 *
 * <p><b>Note:</b> Transfer rate limiting is handled by each node's {@code maxInputRate}
 * and {@code maxOutputRate} fields, not by edges.
 */
public class GraphEdge {

    private final UUID              from;
    private final UUID              to;
    private final boolean           bidirectional;

    /**
     * Runtime toggle. When {@code false} no resources flow across this edge,
     * but it stays in the graph and can be re-enabled without a topology rebuild.
     */
    private boolean enabled;

    // -----------------------------------------------------------------
    //  Constructor
    // -----------------------------------------------------------------

    /**
     * @param from            Source node id.
     * @param to              Destination node id.
     * @param bidirectional   Whether items may flow in both directions.
     */
    public GraphEdge(
            UUID    from,
            UUID    to,
            boolean bidirectional
    ) {
        this.from            = from;
        this.to              = to;
        this.bidirectional   = bidirectional;
        this.enabled         = true;
    }

    // -----------------------------------------------------------------
    //  Accessors
    // -----------------------------------------------------------------

    public UUID    getFrom()            { return from; }
    public UUID    getTo()              { return to; }
    public boolean isBidirectional()    { return bidirectional; }
    public boolean isEnabled()          { return enabled; }
    public void    setEnabled(boolean e){ this.enabled = e; }
}

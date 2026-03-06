# Glyphworks

A Hytale server plugin that adds a **networked item transfer system** — machines, pipes, and configurable per-face routing built entirely on the Hytale component and connected-block APIs.

---

## Concept

Glyphworks lets you link machines and storage together using **Transfer Pipes**. Pipes are standard 1×1×1 blocks and expose exactly six faces. Machines and storage blocks, however, can occupy any footprint (e.g. 2×2×1, 3×5×3) and therefore expose a variable number of connectable faces — one per block-sized cell on each outer surface. Every face on any node can be independently configured as an input, output, bidirectional connection, or closed. Pipes connect nodes into a network and the system automatically routes items from sources to sinks each tick.

```
[ Furnace (OUTPUT) ] ──pipe──> [ Chest (INPUT) ]

[ Farm (OUTPUT) ]
        │
       pipe
        │
        ├──> [ Storage A (INPUT) ]
        └──> [ Storage B (INPUT) ]
```

---

## Network Nodes

Every block that participates in the network carries a **`TransferComponent`**. It stores:

| Property | Description |
|---|---|
| `nodeId` | Unique UUID identifying this node across the world |
| `maxOutputRate` | Max item stacks pushed out per tick |
| `maxInputRate` | Max item stacks accepted per tick |
| `autoPush` | If true, the system initiates transfers from this node automatically |
| `autoPull` | If true, the system initiates pulls into this node automatically |
| `faces` | Per-face connection state (see Face Modes below) |

At runtime, two `ItemContainer` references are wired in by the owning block:

- **`inputInventory`** — where incoming items land
- **`outputInventory`** — where outgoing items are taken from

Both can point to the same inventory (e.g. a chest) or to separate ones (e.g. a machine with distinct input and output slots).

---

## Face Modes

Each face on a node can be independently configured by right-clicking with an appropriate tool. A 1×1×1 node has six faces; larger multi-block structures expose one face per outer cell, so a 2×2×1 block has 12 connectable faces (4 top, 4 bottom, 2×2 on each of its four sides). The mode controls how that face participates in the network graph:

| Mode | Description |
|---|---|
| `INPUT` | This face only receives items. Connects to adjacent OUTPUT or BIDIRECTIONAL faces. |
| `OUTPUT` | This face only sends items. Connects to adjacent INPUT or BIDIRECTIONAL faces. |
| `BIDIRECTIONAL` | This face both sends and receives. |
| `CLOSED` | This face does not connect. No edge is created. |

When two adjacent blocks have compatible face modes on their shared side, a directed graph edge is formed between their nodes.

---

## Transfer Pipes

**Transfer Pipes** (`Transfer_PipeNode`) are the connective tissue of the network. They are passive — they carry no inventory of their own — and act purely as graph edges between nodes.

Pipes use Hytale's **connected-block system** to automatically choose the correct model based on which of their six faces has a neighbouring pipe or machine connection. The visual shape updates in real time as the network is built or broken.

### Pipe shapes

Every combination of connected faces maps to a dedicated model:

| Connections | Shape |
|---|---|
| Alone | Core (floating node) |
| 1 face | End Cap |
| 2 opposite faces (horizontal) | Straight |
| Up + Down | Straight Vertical |
| 2 adjacent horizontal faces | Elbow |
| 1 horizontal + Up | Elbow Up |
| 1 horizontal + Down | Elbow Down |
| 3 horizontal faces | T Junction |
| 2 opposite horizontal + Up | T Up |
| 2 opposite horizontal + Down | T Down |
| 1 horizontal + Up + Down | T Vertical |
| 2 opposite horizontal + Up + Down | T Up Down |
| 1 horizontal + Up + Down (L-shape) | Corner Up / Corner Down |
| 3 horizontal + Up | T Junction Up |
| 3 horizontal + Down | T Junction Down |
| 3 horizontal + Up + Down | T Junction Vertical |
| 4 horizontal | Cross |
| 4 horizontal + Up | Cross Up |
| 4 horizontal + Down | Cross Down |
| All 6 faces | All |

---

## Transfer Flow

Each tick the `TransferSystem` runs over all active nodes with `autoPush` enabled:

1. **Find sources** — nodes where `canSend()` is true (has items, has OUTPUT/BIDIRECTIONAL faces).
2. **Traverse the graph** — follow outgoing edges through pipes to reach sink nodes where `canReceive()` is true.
3. **Move items** — call Hytale's native `ItemContainer.moveItemStackFromSlot` up to `min(source.maxOutputRate, sink.maxInputRate)` stacks.

The effective transfer rate is always the minimum of the source output rate and the sink input rate, preventing bottlenecks from being bypassed.

---

## Block Lifecycle

The transfer graph is **embedded in the components themselves** — each `FacePlane` stores a `neighborNodeId` (UUID) pointing to the adjacent node it is connected to. There is no separate graph object; the persisted component data *is* the graph.

Four event handlers maintain this embedded graph:

| Event | Action |
|---|---|
| `PlaceTransferableBlockEvent` | Generates faces from block bounds, calls `FaceLinkUtil.linkAll` to connect to any loaded neighbours |
| `BreakTransferableBlockEvent` | Calls `FaceLinkUtil.unlinkAll` — clears `neighborNodeId` on this block and on all neighbour faces pointing to it |
| `UseTransferableBlockEvent` | Cycles the clicked face's mode, calls `FaceLinkUtil.relinkFace` to update the single changed edge |
| `ChunkLoadTransferLinkEvent` | On every chunk column load, calls `FaceLinkUtil.linkAll` for each `TransferComponent` in that chunk — resolves cross-chunk connections where one side's chunk was not loaded at placement time |

`FaceLinkUtil.linkAll` is always safe to call: it skips faces that already have a `neighborNodeId` set, so duplicate calls are harmless.

Node identity, rates, face positions, modes, and neighbour UUIDs are all serialised into the chunk via the Hytale component store. On restart the full edge graph is restored from disk automatically. The chunk-load handler then fills in any edges that could not be formed because neighbour chunks were still absent.

---

## Project Structure

```
src/main/java/dev/drav/glyphworks/
├── GlyphworksPlugin.java          — plugin entry point, component registration
└── transfer/
    ├── FaceLinkUtil.java           — helper to resolve face↔neighbour adjacency
    ├── component/
    │   ├── TransferComponent.java  — per-block network node, serialised state
    │   ├── FaceKey.java            — enum: NORTH, SOUTH, EAST, WEST, UP, DOWN
    │   ├── FaceMode.java           — enum: INPUT, OUTPUT, BIDIRECTIONAL, CLOSED
    │   └── FacePlane.java          — a face key + its current mode, codec included
    ├── event/
    │   ├── PlaceTransferableBlockEvent.java
    │   ├── BreakTransferableBlockEvent.java
    │   ├── UseTransferableBlockEvent.java
    │   └── ChunkLoadTransferLinkEvent.java  — re-links cross-chunk edges on chunk load
    └── system/
        └── TransferSystem.java     — tick system: traverses graph, moves items

src/main/resources/
├── Common/Blocks/Glyphworks/Pipe/  — all pipe .blockymodel files
└── Server/Item/
    ├── CustomConnectedBlockTemplates/
    │   └── PipeConnectedBlockTemplate.json
    └── Items/Cloth/Wool/
        └── Transfer_PipeNode.json
```

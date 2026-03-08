# Hitbox → Face Mapping

## Goal

Any `TransferComponent` block declares its faces directly in JSON — block-relative, with a `hitboxIndex` binding each face to a specific hitbox box. Clicking that box cycles the face's `FaceMode`. Non-listed boxes are inert. Works for any block shape — pipes, benches, machines.

---

## Core Principle

**All face geometry is stored block-relative and remains block-relative throughout the world save.** World-absolute coordinates are computed on demand via `blockPos + relMin` / `blockPos + relMax` — never persisted.

---

## Status

| File | Status |
|---|---|
| `FacePlane.java` | ✅ Done |
| `FaceKey.java` | ✅ Deleted |
| `TransferComponent.java` | ✅ Done |
| `FaceLinkUtil.java` | ⏳ Needs notifier + compatibility check |
| `BlockStateNotifier.java` | ⏳ New interface |
| `TransferStateComputer.java` | ⏳ New interface |
| `TransferStateRegistry.java` | ⏳ New class |
| `PipeStateUtil.java` → `PipeStateComputer.java` | ⏳ Rename + strip generic plumbing |
| `generate-pipe-template.js` + generated assets | ✅ Done |
| `ChunkLoadTransferLinkEvent.java` | ✅ Done |
| `UseTransferableBlockEvent.java` | ⏳ Update to new call pattern |
| `PlaceTransferableBlockEvent.java` | ⏳ Logic commented out — needs enabling |
| `BreakTransferableBlockEvent.java` | ⏳ Logic commented out — needs enabling |

---

## `FacePlane` — implemented

### Fields

| Field | Persisted | Description |
|---|---|---|
| `relMin` | ✅ | Block-origin-relative min corner |
| `relMax` | ✅ | Block-origin-relative max corner |
| `hitboxIndex` | ✅ | Detail-box index that triggers this face (`-1` = non-interactable) |
| `mode` | ✅ | Current `FaceMode` — mutable |
| `neighborNodeId` | ✅ | UUID of linked neighbor node — mutable |
| `inputInventory` | ❌ transient | Wired at runtime |
| `outputInventory` | ❌ transient | Wired at runtime |

### Codec keys

| Key | Type |
|---|---|
| `FacePlane_RelMin` | `Vector3i.CODEC` |
| `FacePlane_RelMax` | `Vector3i.CODEC` |
| `FacePlane_HitboxIndex` | `INTEGER` |
| `FacePlane_Mode` | `EnumCodec<FaceMode>` |
| `FacePlane_NeighborNodeId` | `STRING` (nullable UUID) |

### World-coordinate helpers

```java
public Vector3i getWorldMin(Vector3i blockPos) { ... }
public Vector3i getWorldMax(Vector3i blockPos) { ... }
```

`FaceKey` and `getFaceKey()` are **gone** — neighbor matching now compares `Vector3i` directly.

### Constructor validation

The 4-arg constructor throws `IllegalArgumentException` if any axis span is negative or > 1:

```java
int dx = relMax.x - relMin.x; // must be 0 or 1
int dy = relMax.y - relMin.y;
int dz = relMax.z - relMin.z;
```

---

## Block-local coordinate convention

**North = +Z, South = −Z, East = +X, West = −X**

| Face | `hitboxIndex` | `relMin` | `relMax` |
|---|---|---|---|
| South (−Z) | 1 | (0, 0, 0) | (1, 1, 0) |
| North (+Z) | 2 | (0, 0, 1) | (1, 1, 1) |
| East (+X)  | 3 | (1, 0, 0) | (1, 1, 1) |
| West (−X)  | 4 | (0, 0, 0) | (0, 1, 1) |
| Up (+Y)    | 5 | (0, 1, 0) | (1, 1, 1) |
| Down (−Y)  | 6 | (0, 0, 0) | (1, 0, 1) |

The hitbox box ordering in `Pipe_Full.json` is: index 0 = center, 1 = S, 2 = N, 3 = E, 4 = W, 5 = U, 6 = D.

---

## `FaceKey` — deleted

`FaceKey.java` is gone. All neighbor face matching in `FaceLinkUtil` and `PipeStateUtil` compares `Vector3i` directly:

```java
nf.getWorldMin(neighborPos).equals(worldMin) && nf.getWorldMax(neighborPos).equals(worldMax)
```

---

## `TransferComponent` — implemented

```java
private Map<Integer, FacePlane> faces;  // keyed by hitboxIndex
```

- `setFace(int hitboxIndex, FacePlane)`
- `removeFace(int hitboxIndex)`
- `setFaceInventory(int hitboxIndex, ItemContainer)`
- `Transfer_DefaultFaceMode` **removed** — faces already carry their mode from JSON.
- Codec serialises `Transfer_Faces` as a `SetCodec<FacePlane>` (set, not map), then builds the map on decode via `face.getHitboxIndex()`.

---

## Block JSON — Pipe

```json
"Transfer_Faces": [
  { "FacePlane_HitboxIndex": 1, "FacePlane_RelMin": {"X":0,"Y":0,"Z":0}, "FacePlane_RelMax": {"X":1,"Y":1,"Z":0}, "FacePlane_Mode": "Bidirectional" },
  { "FacePlane_HitboxIndex": 2, "FacePlane_RelMin": {"X":0,"Y":0,"Z":1}, "FacePlane_RelMax": {"X":1,"Y":1,"Z":1}, "FacePlane_Mode": "Bidirectional" },
  { "FacePlane_HitboxIndex": 3, "FacePlane_RelMin": {"X":1,"Y":0,"Z":0}, "FacePlane_RelMax": {"X":1,"Y":1,"Z":1}, "FacePlane_Mode": "Bidirectional" },
  { "FacePlane_HitboxIndex": 4, "FacePlane_RelMin": {"X":0,"Y":0,"Z":0}, "FacePlane_RelMax": {"X":0,"Y":1,"Z":1}, "FacePlane_Mode": "Bidirectional" },
  { "FacePlane_HitboxIndex": 5, "FacePlane_RelMin": {"X":0,"Y":1,"Z":0}, "FacePlane_RelMax": {"X":1,"Y":1,"Z":1}, "FacePlane_Mode": "Bidirectional" },
  { "FacePlane_HitboxIndex": 6, "FacePlane_RelMin": {"X":0,"Y":0,"Z":0}, "FacePlane_RelMax": {"X":1,"Y":0,"Z":1}, "FacePlane_Mode": "Bidirectional" }
]
```

Faces are defined once in the block type JSON. On placement the ECS copies this component via `clone()` — no runtime geometry calculation. `neighborNodeId` is `null` on first placement.

---

## `FaceLinkUtil` — implemented

All public methods take `Vector3i blockPos`:

```java
linkAll(TransferComponent, Ref<ChunkStore>, Vector3i blockPos, ChunkStore)
unlinkAll(TransferComponent, Ref<ChunkStore>, Vector3i blockPos, ChunkStore)
relinkFace(TransferComponent, Ref<ChunkStore>, FacePlane, Vector3i blockPos, ChunkStore)
```

Neighbor face matching is a linear scan (max 6 iterations):

```java
Vector3i worldMin = face.getWorldMin(blockPos);
Vector3i worldMax = face.getWorldMax(blockPos);
for (FacePlane nf : neighbor.getFaces().values()) {
    if (nf.getWorldMin(neighborPos).equals(worldMin) && nf.getWorldMax(neighborPos).equals(worldMax)) { ... }
}
```

> **Planned change:** `FacePlane.checkModeCompatibility` will be added to `tryLinkAt` — currently links form unconditionally on geometry match. Mode filtering at transfer-tick time via `canSend()`/`canReceive()` continues as-is.

---

## `PipeStateUtil` → `PipeStateComputer` — planned split

Will be refactored:
- **Delete**: `updatePipeState`, `updateNeighborPipeStates`, `getTransferComponent` — all move into `TransferStateRegistry`
- **Keep + rename file**: `computeStateName`, `getFaceByDirection`, `OFFSETS`, `LABELS` — these are pipe-specific logic only, become `PipeStateComputer.compute`
- File rename: `PipeStateUtil.java` → `PipeStateComputer.java`

`getFaceByDirection` matches faces by computing the expected world-absolute boundary from `(dx, dy, dz)` and comparing directly against each face's `getWorldMin(pos)` / `getWorldMax(pos)`.

`OFFSETS` and the Javadoc use the correct convention:

```
N = NORTH (+Z)   offset (0,  0, +1)
S = SOUTH (-Z)   offset (0,  0, -1)
```

---

## `ChunkLoadTransferLinkEvent` — implemented

`blockPos` is derived from `BlockModule.BlockStateInfo` on the block entity:

```java
BlockModule.BlockStateInfo stateInfo = store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
int idx = stateInfo.getIndex(); // blockInColumnIndex
int localX = ChunkUtil.xFromBlockInColumn(idx);
int localY = ChunkUtil.yFromBlockInColumn(idx);
int localZ = ChunkUtil.zFromBlockInColumn(idx);
WorldChunk worldChunk = store.getComponent(stateInfo.getChunkRef(), WorldChunk.getComponentType());
long chunkIndex = worldChunk.getIndex();
Vector3i blockPos = new Vector3i(
    ChunkUtil.worldCoordFromLocalCoord(ChunkUtil.xOfChunkIndex(chunkIndex), localX),
    localY,
    ChunkUtil.worldCoordFromLocalCoord(ChunkUtil.zOfChunkIndex(chunkIndex), localZ));
FaceLinkUtil.linkAll(transfer, ref, blockPos, chunkStore);
```

---

## `UseTransferableBlockEvent` — implemented

Raycasts the block hitboxes, looks up the face by `hit.detailBoxIndex`, cycles mode, relinks:

```java
FacePlane face = transfer.getFaces().get(hit.detailBoxIndex);
if (face == null) return; // non-interactable box (e.g. center)
face.setMode(nextMode(face.getMode()));
FaceLinkUtil.relinkFace(transfer, blockRef, face, pos, chunkStore,
    p -> TransferStateRegistry.applyState(world, p));
// No separate updatePipeState calls — notifier covers self + affected neighbor
```

Mode cycle: `BIDIRECTIONAL → INPUT → OUTPUT → CLOSED → BIDIRECTIONAL`

> **Pending**: `FaceLinkUtil.relinkFace` does not yet accept a notifier — update required.

---

## `PlaceTransferableBlockEvent` + `BreakTransferableBlockEvent` — pending

Both files have the core logic written but **commented out** pending `TransferStateRegistry` + notifier implementation. Final call patterns:

**Place:**
```java
FaceLinkUtil.linkAll(transfer, blockRef, pos, chunkStore,
    p -> TransferStateRegistry.applyState(world, p));
GlyphworksPlugin.get().registerNode(transfer);
// No separate updatePipeState / updateNeighborPipeStates — notifier covers both
```

**Break:**
```java
FaceLinkUtil.unlinkAll(transfer, blockRef, pos, chunkStore,
    p -> commandBuffer.run(_ -> TransferStateRegistry.applyState(world, p)));
GlyphworksPlugin.get().unregisterNode(transfer.getNodeId());
// Deferred via commandBuffer — avoids chunk read during modification
// Notifier NOT called for breakPos — block is gone
```

---

## Multi-Block Note

Multi-block transfer nodes are supported by defining multiple `FacePlane` entries, each still covering a single 1×1 unit cell. A 2×2×1 bench with a fully-open North (+Z) face would declare up to 4 face planes:

```json
{ "FacePlane_HitboxIndex": 2, "FacePlane_RelMin": {"X":0,"Y":0,"Z":1}, "FacePlane_RelMax": {"X":1,"Y":1,"Z":1}, ... },
{ "FacePlane_HitboxIndex": ..., "FacePlane_RelMin": {"X":1,"Y":0,"Z":1}, "FacePlane_RelMax": {"X":2,"Y":1,"Z":1}, ... },
{ "FacePlane_HitboxIndex": ..., "FacePlane_RelMin": {"X":0,"Y":1,"Z":1}, "FacePlane_RelMax": {"X":1,"Y":2,"Z":1}, ... },
{ "FacePlane_HitboxIndex": ..., "FacePlane_RelMin": {"X":1,"Y":1,"Z":1}, "FacePlane_RelMax": {"X":2,"Y":2,"Z":1}, ... }
```

The span per axis is still 0–1 (constructor validation holds). Coordinates are still block-relative. Each face plane can have its own `FaceMode` and links independently. The unit-cell constraint is intentional — it keeps linking logic, neighbor matching, and transfer routing uniform regardless of block size.

---

## Generic state update — design

### Overview

`FaceLinkUtil` stays completely World-agnostic. Any block with a `TransferComponent` can have its own visual state logic (pipes show `NS`/`NEU`/etc., a furnace might show `Active`/`Idle`, a chest passes `null`). The plumbing is:

```
FaceLinkUtil (generic linking)
    → fires BlockStateNotifier (callback interface)
        → calls TransferStateRegistry.applyState(world, pos)
            → looks up TransferStateComputer by root block type ID
                → PipeStateComputer.compute(...) → state name string
                    → chunk.setBlockInteractionState(...)
```

---

### New interfaces and classes

**`BlockStateNotifier`** — `dev.drav.glyphworks.transfer`
```java
@FunctionalInterface
public interface BlockStateNotifier {
    void onChanged(Vector3i pos);
}
```
`FaceLinkUtil` accepts this as `@Nullable` — pass `null` to skip all state updates (chunk load, tests).

---

**`TransferStateComputer`** — `dev.drav.glyphworks.transfer`
```java
@FunctionalInterface
public interface TransferStateComputer {
    String compute(TransferComponent transfer, Vector3i pos, World world);
}
```
Block-type-specific state name logic. `PipeStateComputer.compute` is the first implementation.

---

**`TransferStateRegistry`** — `dev.drav.glyphworks.transfer`
```java
public final class TransferStateRegistry {
    // Register at plugin init by root block type ID string (BlockType.getId())
    public static void register(String rootBlockTypeId, TransferStateComputer computer);

    // Called by the BlockStateNotifier lambda in every event handler
    public static void applyState(World world, Vector3i pos);
}
```

`applyState` internal flow:
```
1. world.getBlockType(pos) → null check
2. getDefaultStateKey() → navigate to root block type (same as current updatePipeState)
3. rootBlockType.getId() → look up in registry → null = no-op (unregistered block)
4. get TransferComponent at pos → null = no-op
5. computer.compute(transfer, pos, world) → state name
6. world.getChunkIfLoaded(...).setBlockInteractionState(..., stateName, true)
```

The `"Single"` guard is gone — replaced by registry membership.

---

**`PipeStateComputer`** — `dev.drav.glyphworks.transfer` (rename of `PipeStateUtil`)

Keeps only:
- `computeStateName(TransferComponent, Vector3i, World) → String` (exposed as `compute` to match `TransferStateComputer`)
- `getFaceByDirection` (private helper)
- `OFFSETS`, `LABELS` constants

Deletes: `updatePipeState`, `updateNeighborPipeStates`, `getTransferComponent`.

---

### Plugin registration

In `GlyphworksPlugin.onLoad` (or equivalent init point):
```java
TransferStateRegistry.register(
    "Glyphworks:Transfer_PipeNode",   // BlockType.getId() of the root block type
    PipeStateComputer::compute
);
// Future:
// TransferStateRegistry.register("Glyphworks:Transfer_FurnaceNode", FurnaceStateComputer::compute);
```

---

### Compatibility matrix (resolved)

Gates on `FacePlane.checkModeCompatibility(modeA, modeB)` — already implemented, returns `null` for incompatible pairs:

| Face A | Face B | Compatible? | `EdgeType` returned |
|---|---|---|---|
| `BIDIRECTIONAL` | `BIDIRECTIONAL` | ✅ | `BIDIRECTIONAL` |
| `BIDIRECTIONAL` | `INPUT` | ✅ | `A_TO_B` |
| `BIDIRECTIONAL` | `OUTPUT` | ✅ | `B_TO_A` |
| `INPUT` | `OUTPUT` | ✅ | `B_TO_A` |
| `OUTPUT` | `INPUT` | ✅ | `A_TO_B` |
| `INPUT` | `INPUT` | ❌ | `null` |
| `OUTPUT` | `OUTPUT` | ❌ | `null` |
| `CLOSED` | anything | ❌ | `null` |

Added to `tryLinkAt` after geometry match, before setting `neighborNodeId`:
```java
EdgeType edge = FacePlane.checkModeCompatibility(face.getMode(), neighborFace.getMode());
if (edge == null) return false;
```

`canSend()`/`canReceive()` require wired inventories — **not** used at link time, only at transfer-tick time.

---

### Updated `FaceLinkUtil` signatures

```java
linkAll  (TransferComponent, Ref<ChunkStore>, Vector3i blockPos, ChunkStore, @Nullable BlockStateNotifier)
unlinkAll(TransferComponent, Ref<ChunkStore>, Vector3i blockPos, ChunkStore, @Nullable BlockStateNotifier)
relinkFace(TransferComponent, Ref<ChunkStore>, FacePlane, Vector3i blockPos, ChunkStore, @Nullable BlockStateNotifier)
```

---

### When the notifier fires

| Operation | Notifier called for |
|---|---|
| `linkAll` — link formed for a face | `blockPos` (once) + `neighborPos` per linked neighbor |
| `linkAll` — no link formed (CLOSED / no neighbor / incompatible) | nothing |
| `unlinkAll` — face was linked | `neighborPos` per unlinked neighbor; **not** `blockPos` (block is gone) |
| `relinkFace` — always | `blockPos` (mode changed regardless of link outcome) |
| `relinkFace` — old link broken | old `neighborPos` |
| `relinkFace` — new link formed | new `neighborPos` (may equal old) |

Positions are collected in a local `Set<Vector3i>` per call and deduped before firing — avoids double-updating a neighbor when two faces touch it.

---

### Chunk load

```java
FaceLinkUtil.linkAll(transfer, ref, blockPos, chunkStore, null);
// null notifier — state persisted correctly, no visual update needed.
// Cross-chunk arm gaps: handled when the adjacent chunk's blocks also fire linkAll on their load.
```



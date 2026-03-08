# Hitbox → Face Mapping Design

## Goal

Any `TransferComponent` block declares its faces directly in JSON — block-relative, with a `hitboxIndex` binding each face to a specific hitbox box. Clicking that box cycles the face's `FaceMode`. Non-listed boxes are inert. Works for any block shape — pipes, benches, machines.

---

## Core Principle

**All face geometry is stored block-relative and remains block-relative throughout the world save.** World-absolute coordinates are never persisted — they are computed on demand via `blockPos + relMin` / `blockPos + relMax` in the single caller contexts that need them (O(1), no allocation path needed in hot loops).

---

## `FacePlane` — redesigned

No `FaceTemplate` class. `FacePlane` itself is the definition. The codec stores relative coords.

### Fields

| Field | Persisted | Description |
|---|---|---|
| `relMin` (was `planeMin`) | ✅ | Block-origin-relative min corner (integer, in blocks) |
| `relMax` (was `planeMax`) | ✅ | Block-origin-relative max corner |
| `hitboxIndex` | ✅ | Which detail box triggers this face (`-1` = non-interactable) |
| `mode` | ✅ | Current `FaceMode` — mutable |
| `neighborNodeId` | ✅ | UUID of linked neighbor node — mutable |
| `inputInventory` | ❌ transient | Wired at runtime |
| `outputInventory` | ❌ transient | Wired at runtime |

### Codec key renames

| Old key | New key |
|---|---|
| `FacePlane_Min` | `FacePlane_RelMin` |
| `FacePlane_Max` | `FacePlane_RelMax` |
| *(new)* | `FacePlane_HitboxIndex` |

### Computed helpers (not persisted, no fields)

```java
public Vector3i getWorldMin(Vector3i blockPos) {
    return new Vector3i(blockPos.x + relMin.x, blockPos.y + relMin.y, blockPos.z + relMin.z);
}
public Vector3i getWorldMax(Vector3i blockPos) {
    return new Vector3i(blockPos.x + relMax.x, blockPos.y + relMax.y, blockPos.z + relMax.z);
}
public FaceKey getFaceKey(Vector3i blockPos) {
    return new FaceKey(getWorldMin(blockPos), getWorldMax(blockPos));
}
```

`getFaceKey()` (no-arg, existing) is removed since it relied on stored absolute coords.

---

## Block-local coordinate convention (block origin = 0,0,0)

**Correct convention: North = +Z, South = −Z, East = +X, West = −X**

| Face       | `relMin`  | `relMax`  |
|------------|-----------|----------|
| North (+Z) | (0, 0, 1) | (1, 1, 1) |
| South (−Z) | (0, 0, 0) | (1, 1, 0) |
| East (+X)  | (1, 0, 0) | (1, 1, 1) |
| West (−X)  | (0, 0, 0) | (0, 1, 1) |
| Up (+Y)    | (0, 1, 0) | (1, 1, 1) |
| Down (−Y)  | (0, 0, 0) | (1, 0, 1) |

---

## `TransferComponent.faces` — key changes to `Map<Integer, FacePlane>`

Previously `Map<FaceKey, FacePlane>` keyed by world-absolute boundary. Now keyed by `hitboxIndex` since that's the stable per-block identity.

```java
// was: Map<FaceKey, FacePlane>  keyed by world boundary
private Map<Integer, FacePlane> faces;  // keyed by hitboxIndex
```

`setFace(FaceKey, FacePlane)` → `setFace(int hitboxIndex, FacePlane)`.  
`Transfer_Faces` codec entry continues to serialize the `FacePlane` collection; element ordering is stable enough for a list codec.

`Transfer_DefaultFaceMode` and all other fields unchanged.

---

## Block JSON — Pipe example

```json
"TransferComponent": {
  "Transfer_MaxOutputRate": 64,
  "Transfer_MaxInputRate": 64,
  "Transfer_AutoPush": false,
  "Transfer_AutoPull": false,
  "Transfer_DefaultFaceMode": "Bidirectional",
  "Transfer_Faces": [
    { "FacePlane_HitboxIndex": 1, "FacePlane_RelMin": {"X":0,"Y":0,"Z":0}, "FacePlane_RelMax": {"X":1,"Y":1,"Z":0}, "FacePlane_Mode": "Bidirectional" },
    { "FacePlane_HitboxIndex": 2, "FacePlane_RelMin": {"X":0,"Y":0,"Z":1}, "FacePlane_RelMax": {"X":1,"Y":1,"Z":1}, "FacePlane_Mode": "Bidirectional" },
    { "FacePlane_HitboxIndex": 3, "FacePlane_RelMin": {"X":1,"Y":0,"Z":0}, "FacePlane_RelMax": {"X":1,"Y":1,"Z":1}, "FacePlane_Mode": "Bidirectional" },
    { "FacePlane_HitboxIndex": 4, "FacePlane_RelMin": {"X":0,"Y":0,"Z":0}, "FacePlane_RelMax": {"X":0,"Y":1,"Z":1}, "FacePlane_Mode": "Bidirectional" },
    { "FacePlane_HitboxIndex": 5, "FacePlane_RelMin": {"X":0,"Y":1,"Z":0}, "FacePlane_RelMax": {"X":1,"Y":1,"Z":1}, "FacePlane_Mode": "Bidirectional" },
    { "FacePlane_HitboxIndex": 6, "FacePlane_RelMin": {"X":0,"Y":0,"Z":0}, "FacePlane_RelMax": {"X":1,"Y":0,"Z":1}, "FacePlane_Mode": "Bidirectional" }
  ]
}
```

Faces are defined once in the block type JSON. On placement the ECS copies this component (via `clone()`) into the placed block entity — no runtime geometry calculation needed. The faces are already correct relative to block origin; `neighborNodeId` is `null` on first placement.

---

## `FaceLinkUtil` — signature change

Every method that uses face boundary geometry needs the block's world position to compute it:

```java
// Old
public static void linkAll(TransferComponent transfer, Ref<ChunkStore> blockRef, ChunkStore chunkStore)

// New
public static void linkAll(TransferComponent transfer, Ref<ChunkStore> blockRef, Vector3i blockPos, ChunkStore chunkStore)
```

**`tryLink` — internal change.** Constant-axis derivation was `face.getPlaneMin()` vs `face.getPlaneMax()`. Becomes:

```java
Vector3i worldMin = face.getWorldMin(blockPos);
Vector3i worldMax = face.getWorldMax(blockPos);
// then same constant-axis logic to find posA / posB
```

**`tryLinkAt` — neighbor face lookup change.** Was `neighbor.getFaces().get(face.getFaceKey())` (O(1) by world key). Becomes a linear scan over the neighbor's faces comparing world-absolute keys:

```java
FaceKey worldKey = face.getFaceKey(blockPos);
FacePlane neighborFace = null;
for (FacePlane nf : neighbor.getFaces().values()) {
    if (nf.getFaceKey(neighborPos).equals(worldKey)) { neighborFace = nf; break; }
}
```

Max 6–7 faces per block — complexity is O(1) in practice.

**`clearNeighborSide` — same `blockPos` threaded through.**

**All call sites** (`PlaceTransferableBlockEvent`, `UseTransferableBlockEvent`, `ChunkLoadTransferLinkEvent`) need to supply `blockPos`.

> **`ChunkLoadTransferLinkEvent` note:** `onComponentAdded` receives a `Ref<ChunkStore>` but not an explicit block position. This needs investigation — either the engine exposes a reverse lookup from block entity ref → world position, or blockPos is derived from iterating the `BlockComponentChunk`. To be resolved during implementation.

---

## `PlaceTransferableBlockEvent` — simplified

Remove `initAndLogAllFaces`, `getBlockBoundsSize`, `BlockBoundingBoxes`, `WorldChunk` entirely. Faces come pre-defined from the JSON. On first placement just check `faces.isEmpty()` is now irrelevant — the component already has all faces from `clone()`; neighborNodeIds are all null (unlinked) which is correct.

```java
// Before: initAndLogAllFaces call + getBlockBoundsSize + BlockBoundingBoxes lookup
// After: nothing — faces already defined via JSON + clone()

FaceLinkUtil.linkAll(transfer, blockRef, pos, chunkStore);
GlyphworksPlugin.get().registerNode(transfer);
PipeStateUtil.updatePipeState(world, pos);
PipeStateUtil.updateNeighborPipeStates(world, pos);
```

---

## `UseTransferableBlockEvent` — interaction logic

```java
// O(1) lookup by hitboxIndex
FacePlane face = transfer.getFaces().get(hit.detailBoxIndex);
if (face == null) return; // non-interactable box

face.setMode(nextMode(face.getMode()));
FaceLinkUtil.relinkFace(transfer, blockRef, face, pos, chunkStore);
PipeStateUtil.updatePipeState(world, pos);
PipeStateUtil.updateNeighborPipeStates(world, pos);
```

```java
private static FaceMode nextMode(FaceMode current) {
    return switch (current) {
        case BIDIRECTIONAL -> FaceMode.INPUT;
        case INPUT         -> FaceMode.OUTPUT;
        case OUTPUT        -> FaceMode.CLOSED;
        case CLOSED        -> FaceMode.BIDIRECTIONAL;
    };
}
```

---

## What Changes vs. What Stays

| | Outcome |
|---|---|
| `FaceLinkUtil` | All public methods gain `Vector3i blockPos` param; internal world-coord derivation uses `face.getWorldMin/Max(blockPos)` instead of stored absolute coords; neighbor lookup becomes O(n) linear scan (n ≤ 7) |
| `PipeStateUtil` | `getFaceByDirection` currently uses absolute face coords — needs `blockPos` threaded through or equivalent; `updatePipeState` already has `pos` so low impact |
| `FaceKey` | Unchanged as a value type — now always computed, never stored |
| `FaceMode` | Unchanged |
| `FacePlane` | `planeMin`/`planeMax` → `relMin`/`relMax`; codec keys renamed; add `hitboxIndex` field + codec entry; remove no-arg `getFaceKey()`; add `getWorldMin(blockPos)`, `getWorldMax(blockPos)`, `getFaceKey(blockPos)` helpers |
| `TransferComponent.faces` | `Map<FaceKey, FacePlane>` → `Map<Integer, FacePlane>` (keyed by hitboxIndex); `setFace` signature change |
| `PlaceTransferableBlockEvent` | Remove `initAndLogAllFaces`, `getBlockBoundsSize`, `BlockBoundingBoxes`, `WorldChunk` imports; faces come from component clone |
| `UseTransferableBlockEvent` | Replace logging skeleton with O(1) hitboxIndex lookup + mode cycle + relink + state update |
| Block JSON | `Transfer_Faces` is now the canonical definition in the block type JSON; codec key renames (`RelMin`/`RelMax`/`HitboxIndex`) |

---

## Multi-Block Note

A 2×2×1 bench with a single North face would have `relMax.x = 2, relMax.y = 2`. The world-absolute key computation is identical — just adds a larger offset. `FaceLinkUtil` neighbor matching logic is unchanged in structure.

---

## Convention Corrections Needed in Existing Code

The following files currently use the **wrong** N/S convention (`N = −Z`, `S = +Z`) which is the opposite of the correct standard (`N = +Z`, `S = −Z`). These must be corrected as part of this implementation.

### `PipeStateUtil.java`

```java
// WRONG (current)
{ 0, 0, -1 }, // 0: N  (North = -Z, Minecraft/Hytale standard)
{ 0, 0, 1 },  // 1: S  (South = +Z)

// CORRECT
{ 0, 0, 1 },  // 0: N  (North = +Z)
{ 0, 0, -1 }, // 1: S  (South = -Z)
```

Also update the class-level Javadoc comment block:
```
// WRONG: N = NORTH (-Z)   offset (0,  0, -1)
// CORRECT: N = NORTH (+Z)  offset (0,  0, +1)
```

The `LABELS` array and state name logic are unchanged — only the offsets and their paired comments swap.

**Impact:** `computeStateName` builds the state string by checking which offsets have a neighbor. Swapping N/S offsets means a neighbor at +Z will now produce an `"N"` letter in the state name, which matches the renamed model files from the generator below. State names like `"NS"`, `"NE"` etc. remain valid — the geometry just becomes correct.

### `generate-pipe-template.js`

`ARM_BOXES` has N arm at Z 0–0.3125 (low-Z = −Z) and S arm at Z 0.6875–1 (high-Z = +Z). Both keys and the `DIRECTIONS` entries need swapping:

```js
// WRONG (current)
N: { Min: { X: 0.375, Y: 0.375, Z: 0      }, Max: { X: 0.625, Y: 0.625, Z: 0.3125 } },
S: { Min: { X: 0.375, Y: 0.375, Z: 0.6875 }, Max: { X: 0.625, Y: 0.625, Z: 1      } },

// CORRECT
S: { Min: { X: 0.375, Y: 0.375, Z: 0      }, Max: { X: 0.625, Y: 0.625, Z: 0.3125 } },
N: { Min: { X: 0.375, Y: 0.375, Z: 0.6875 }, Max: { X: 0.625, Y: 0.625, Z: 1      } },
```

`DIRECTIONS` — swap `name` and `position.Z` for the N/S entries:

```js
// WRONG
{ name: 'N', bit: 5, position: { X: 0, Y: 0, Z: -1 }, nodeIdx: 3 },
{ name: 'S', bit: 4, position: { X: 0, Y: 0, Z:  1 }, nodeIdx: 2 },

// CORRECT
{ name: 'N', bit: 5, position: { X: 0, Y: 0, Z:  1 }, nodeIdx: 2 },
{ name: 'S', bit: 4, position: { X: 0, Y: 0, Z: -1 }, nodeIdx: 3 },
```

**Impact:** Re-running the generator renames all `Pipe_N*` / `Pipe_S*` model files and state definitions correctly. `Transfer_PipeNode.json` is fully regenerated. The hitboxIndex ordering of boxes in `Pipe_Full.json` does **not** change — CENTER is still index 0, the first arm written is still the low-Z arm (now correctly labeled `S`), so the face geometry in `Transfer_Faces` JSON entries remains valid.

---
description: "Agent coding instructions — Block and Item JSON Configuration. Apply these rules when writing or reviewing Hytale block and item assets."
applyTo: "**"
---

# Block and Item JSON Configuration

Apply these principles consistently when writing, reviewing, or suggesting code changes.

## Core Rules

### Item JSONs define both the item and its block type
- Each block/item is a single JSON file under `Server/Item/Items/<Namespace>/<Module>/`.
- The file defines `TranslationProperties`, `Categories`, optional `Recipe`, and the `BlockType` object.
- The `BlockType` object configures material, rendering, hitbox, model, and block entity components.

### Every block type needs Material, DrawType, and HitboxType
- `Material` — physical properties: `"Solid"`, `"Liquid"`, `"Replaceable"`.
- `DrawType` — rendering mode: `"Model"` for custom models, `"Leaf"`, `"Foliage"`, `"Transparent"`.
- `HitboxType` — collision shape: `"Tank"` for full-block, or other predefined shapes.
- Missing any of these causes the block to render or collide incorrectly.

### Block entity components use codec identifier keys
- Under `BlockType.BlockEntity.Components`, each entry is keyed by the component's registration name.
- Field values inside use the `KeyedCodec` identifier strings from the component's `BuilderCodec`.
- These must match exactly — a typo in a JSON key silently drops the field.

### Each block gets its own model folder
- Model assets live under `Common/Blocks/<Namespace>/<Module>/<BlockName>/`.
- Each folder contains at minimum `Default.blockymodel` and `Default.png`.
- Never share a model folder between two different blocks.

### Reference models with relative paths from the asset root
- `CustomModel` uses a path relative to `Common/`: e.g., `"Blocks/Glyphworks/Fluid/Tank/Default.blockymodel"`.
- `CustomModelTexture` is an array of weighted texture entries referencing paths similarly.

### Use Opacity for transparency
- `"Opaque"` — fully solid, occludes adjacent faces (default).
- `"Transparent"` — see-through, does not occlude adjacent faces (glass, pipes).
- `"None"` — invisible collision-only block.

### Use VariantRotation for directional blocks
- Controls how the block model rotates based on placement direction.
- `"DoublePipe"` for blocks that connect on two axes, `"None"` for fixed orientation.

### Organize items with Categories
- `Categories` is an array of strings for grouping items in the creative inventory.
- Use dot-separated namespaced categories: `["Glyphworks.Fluid"]`, `["Glyphworks.Items"]`.

### Translation keys follow a standard pattern
- `TranslationProperties.Name` uses the format `"server.items.<BlockId>.name"`.
- The `BlockId` matches the JSON filename without extension.

### FacePlane configuration for grid-connected blocks
- `FacePlane_Position` — the block-local offset `{"X": 0, "Y": 0, "Z": 0}` for single-block nodes.
- `FacePlane_Normal` — the direction the face points: `"Up"`, `"Down"`, `"North"`, `"South"`, `"East"`, `"West"`.
- `FacePlane_Mode` — transfer direction: `"Input"`, `"Output"`, `"Bidirectional"`, `"Closed"`.
- `FacePlane_ContainerKey` — `null` for external-reference blocks (extractors/inserters); a named key for storage blocks.

## Code Examples

### Complete block entity JSON with grid and storage components

```json
{
  "TranslationProperties": {
    "Name": "server.items.MyMod_Fluid_Tank.name"
  },
  "Categories": ["MyMod.Fluid"],
  "BlockType": {
    "Material": "Solid",
    "DrawType": "Model",
    "Opacity": "Transparent",
    "VariantRotation": "DoublePipe",
    "HitboxType": "Tank",
    "CustomModel": "Blocks/MyMod/Fluid/Tank/Default.blockymodel",
    "CustomModelTexture": [{
      "Texture": "Blocks/MyMod/Fluid/Tank/Default.png",
      "Weight": 1
    }],
    "BlockEntity": {
      "Components": {
        "MyMod_GridComponent": {
          "MyMod_GridComponent_Entries": [{
            "MyMod_GridEntry_Type": "Fluid",
            "MyMod_GridEntry_TransferRate": 25,
            "MyMod_GridEntry_Faces": [{
              "MyMod_Face_Position": {"X": 0, "Y": 0, "Z": 0},
              "MyMod_Face_Normal": "Up",
              "MyMod_Face_Mode": "Bidirectional",
              "MyMod_Face_ContainerKey": "tank"
            }]
          }]
        },
        "MyMod_FluidContainerComponent": {
          "MyMod_FluidContainer_Capacity": 1000,
          "MyMod_FluidContainer_Amount": 0,
          "MyMod_FluidContainer_FluidId": null
        }
      }
    }
  }
}
```

### Minimal block without a block entity

```json
{
  "TranslationProperties": {
    "Name": "server.items.MyMod_Decorative_Crate.name"
  },
  "Categories": ["MyMod.Decorative"],
  "BlockType": {
    "Material": "Solid",
    "DrawType": "Model",
    "HitboxType": "Tank",
    "CustomModel": "Blocks/MyMod/Decorative/Crate/Default.blockymodel",
    "CustomModelTexture": [{
      "Texture": "Blocks/MyMod/Decorative/Crate/Default.png",
      "Weight": 1
    }]
  }
}
```

### Asset folder structure

```
Common/Blocks/MyMod/
  Fluid/
    Tank/
      Default.blockymodel
      Default.png
    Pipe/
      Default.blockymodel
      Default.png
  Item/
    Container/
      Default.blockymodel
      Default.png

Server/Item/Items/MyMod/
  Fluid/
    MyMod_Fluid_Tank.json
    MyMod_Fluid_Pipe.json
  Item/
    MyMod_Item_Container.json
```

### JSON key must match codec identifier exactly

**Broken — silent field drop:**
```json
"FluidContainer_capacity": 1000
```

**Correct — matches KeyedCodec identifier:**
```json
"MyMod_FluidContainer_Capacity": 1000
```

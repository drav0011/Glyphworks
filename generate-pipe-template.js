/**
 * Using this for now, ideally will change to a model with conditional rendering of cubes in the models
 * or if not still using blockstates with the minimum amount of models rotating them in 3D to position correctly
 */

const fs = require('fs');
const path = require('path');

const BASE = __dirname;
const ITEM_MODELS_DIR  = path.join(BASE, 'src/main/resources/Common/Blocks/Glyphworks/Item/Pipe');
const FLUID_MODELS_DIR = path.join(BASE, 'src/main/resources/Common/Blocks/Glyphworks/Fluid/Pipe');
const FLUID_PIPE_PATH = path.join(BASE, 'src/main/resources/Server/Item/Items/Glyphworks/Fluid/Glyphworks_Fluid_Pipe.json');
const ITEM_PIPE_PATH  = path.join(BASE, 'src/main/resources/Server/Item/Items/Glyphworks/Item/Glyphworks_Item_Pipe.json');
const HITBOXES_DIR = path.join(BASE, 'src/main/resources/Server/Item/Block/Hitboxes/Glyphworks/Pipe');

const TEMPLATE_MODEL = path.join(BASE, 'Pipe_Template.blockymodel');

// ─── Delete all existing models except the template and texture ───────────────
function cleanModelsDir(dir) {
  const old = fs.readdirSync(dir).filter(f => f !== 'Pipe.png');
  for (const f of old) fs.unlinkSync(path.join(dir, f));
  if (old.length) console.log(`Deleted ${old.length} old generated model files from ${path.basename(dir)}`);
}
cleanModelsDir(ITEM_MODELS_DIR);
cleanModelsDir(FLUID_MODELS_DIR);

// ─── Hitbox box definitions (block-local 0-1 coords derived from model nodes) ─
// Model space: X/Z ∈ [-16, +16], Y ∈ [0, 32]; pivot = box center + offset.
const CENTER_BOX = { Min: { X: 0.3125, Y: 0.3125, Z: 0.3125 }, Max: { X: 0.6875, Y: 0.6875, Z: 0.6875 } };
const ARM_BOXES = {
  // N = world North = -Z → arm sits at the -Z face of the block (Z: 0 to 0.3125)
  N: { Min: { X: 0.375, Y: 0.375, Z: 0 }, Max: { X: 0.625, Y: 0.625, Z: 0.3125 } },
  // S = world South = +Z → arm sits at the +Z face of the block (Z: 0.6875 to 1)
  S: { Min: { X: 0.375, Y: 0.375, Z: 0.6875 }, Max: { X: 0.625, Y: 0.625, Z: 1 } },
  E: { Min: { X: 0.6875, Y: 0.375, Z: 0.375 }, Max: { X: 1, Y: 0.625, Z: 0.625 } },
  W: { Min: { X: 0, Y: 0.375, Z: 0.375 }, Max: { X: 0.3125, Y: 0.625, Z: 0.625 } },
  U: { Min: { X: 0.375, Y: 0.6875, Z: 0.375 }, Max: { X: 0.625, Y: 1, Z: 0.625 } },
  D: { Min: { X: 0.375, Y: 0, Z: 0.375 }, Max: { X: 0.625, Y: 0.3125, Z: 0.625 } },
};

// ─── Clean up old hitbox files ────────────────────────────────────────────────
if (!fs.existsSync(HITBOXES_DIR)) fs.mkdirSync(HITBOXES_DIR, { recursive: true });
const oldHitboxFiles = fs.readdirSync(HITBOXES_DIR).filter(f => f.startsWith('Pipe_') && f.endsWith('.json'));
for (const f of oldHitboxFiles) fs.unlinkSync(path.join(HITBOXES_DIR, f));
if (oldHitboxFiles.length) console.log(`Deleted ${oldHitboxFiles.length} old hitbox files`);

// ─── Bit layout: bit5=N, bit4=S, bit3=E, bit2=W, bit1=U, bit0=D ─────────────
// N = world North = -Z;  S = world South = +Z
// Template node order: 0=center, 1=up, 2=down, 3=north, 4=south, 5=east, 6=west
const DIRECTIONS = [
  { name: 'N', bit: 5, position: { X: 0, Y: 0, Z: -1 }, nodeIdx: 3 },
  { name: 'S', bit: 4, position: { X: 0, Y: 0, Z: 1 }, nodeIdx: 4 },
  { name: 'E', bit: 3, position: { X: 1, Y: 0, Z: 0 }, nodeIdx: 6 },
  { name: 'W', bit: 2, position: { X: -1, Y: 0, Z: 0 }, nodeIdx: 5 },
  { name: 'U', bit: 1, position: { X: 0, Y: 1, Z: 0 }, nodeIdx: 1 },
  { name: 'D', bit: 0, position: { X: 0, Y: -1, Z: 0 }, nodeIdx: 2 },
];

const templateModel = JSON.parse(fs.readFileSync(TEMPLATE_MODEL, 'utf8'));

function shapeNameFor(mask) {
  if (mask === 0) return 'Single';
  return DIRECTIONS.filter(d => (mask >> d.bit) & 1).map(d => d.name).join('');
}

// ─── Generate all 64 combinations into both module dirs ───────────────────────
const itemStateDefs  = {};
const fluidStateDefs = {};

for (let mask = 0; mask < 64; mask++) {
  const name = shapeNameFor(mask);

  // Blockymodel — clone Pipe_Template, toggle arm visibility
  const model = JSON.parse(JSON.stringify(templateModel));
  model.nodes[0].shape.visible = true; // center always on
  for (const dir of DIRECTIONS) {
    model.nodes[dir.nodeIdx].shape.visible = !!((mask >> dir.bit) & 1);
  }

  const modelFileName = `Pipe_${name}.blockymodel`;
  const modelJson = JSON.stringify(model, null, 2);
  fs.writeFileSync(path.join(ITEM_MODELS_DIR,  modelFileName), modelJson, 'utf8');
  fs.writeFileSync(path.join(FLUID_MODELS_DIR, modelFileName), modelJson, 'utf8');

  const hitboxBoxes = [CENTER_BOX];
  for (const dir of DIRECTIONS) {
    if ((mask >> dir.bit) & 1) hitboxBoxes.push(ARM_BOXES[dir.name]);
  }
  fs.writeFileSync(path.join(HITBOXES_DIR, `Pipe_${name}.json`), JSON.stringify({ Boxes: hitboxBoxes }, null, 2), 'utf8');

  itemStateDefs[name]  = { HitboxType: `Pipe_${name}`, CustomModel: `Blocks/Glyphworks/Item/Pipe/${modelFileName}` };
  fluidStateDefs[name] = { HitboxType: `Pipe_${name}`, CustomModel: `Blocks/Glyphworks/Fluid/Pipe/${modelFileName}` };
}

console.log(`Generated ${Object.keys(itemStateDefs).length} .blockymodel files in Item/Pipe and Fluid/Pipe`);

// ─── Build pipe JSON for a given grid type ───────────────────────────────────
function buildGridFaces() {
  return ['South', 'North', 'East', 'West', 'Up', 'Down'].map(normal => ({
    "Glyphworks_FacePlane_Position": { "X": 0, "Y": 0, "Z": 0 },
    "Glyphworks_FacePlane_Normal": normal,
    "Glyphworks_FacePlane_Mode": "AllowAll"
  }));
}

function buildPipeItem(config, stateDefs) {
  const { gridType, transferRate, itemId, category, benchCategory, modelBasePath, extraComponents, interactions } = config;

  const components = {
    ...extraComponents,
    "Glyphworks_GridComponent": {
      "Glyphworks_GridComponent_Entries": [
        {
          "Glyphworks_GridTypeEntry_Type": gridType,
          "Glyphworks_GridTypeEntry_TransferRate": transferRate,
          "Glyphworks_GridTypeEntry_Faces": buildGridFaces()
        }
      ]
    }
  };

  return {
    TranslationProperties: {
      Name: `server.items.${itemId}.name`,
      Description: `server.items.${itemId}.description`
    },
    Categories: [category],
    Recipe: {
      Input: [{ ResourceTypeId: "Glyphworks_Empty_Rune", Quantity: 6 }],
      Output: [{ ItemId: itemId, Quantity: 10 }],
      OutputQuantity: 10,
      BenchRequirement: [{ Id: "GlyphImprinter", Type: "Crafting", Categories: [benchCategory] }],
      TimeSeconds: 3
    },
    BlockType: {
      Material: 'Solid',
      DrawType: 'Model',
      BlockSoundSetId: 'Stone',
      Opacity: 'Transparent',
      ConnectedBlockRuleSet: { Type: 'Pipe' },
      CustomModel: `${modelBasePath}/Pipe_Single.blockymodel`,
      CustomModelTexture: [{ Texture: `${modelBasePath}/Pipe.png`, Weight: 1 }],
      BlockEntity: { Components: components },
      CustomModelScale: 1,
      HitboxType: 'Pipe',
      Flags: {},
      State: { Definitions: stateDefs },
      ...(interactions ? { Interactions: interactions } : {}),
      Gathering: { Breaking: { GatherType: 'Benches' } }
    },
    PlayerAnimationsId: 'Block',
    Icon: `Icons/ItemsGenerated/${itemId}.png`,
    Scale: 1,
    IconProperties: {
      Scale: 0.58823,
      Rotation: [22.5, 45, 22.5],
      Translation: [0, -13.5]
    }
  };
}

fs.writeFileSync(FLUID_PIPE_PATH, JSON.stringify(buildPipeItem({
  gridType: 'Fluid',
  transferRate: 25,
  itemId: 'Glyphworks_Fluid_Pipe',
  category: 'Glyphworks.Fluid',
  benchCategory: 'GlyphImprinter_Fluid',
  modelBasePath: 'Blocks/Glyphworks/Fluid/Pipe',
  interactions: { Use: "Open_Fluid_Container" },
  extraComponents: {
    "Glyphworks_FluidPipeComponent": {},
    "Glyphworks_FluidContainerComponent": {
      "Glyphworks_FluidContainerComponent_Container": {
        "Glyphworks_FluidContainer_Capacity": 1,
        "Glyphworks_FluidContainer_CapacityMbPerSlot": 500
      }
    }
  }
}, fluidStateDefs), null, 2), 'utf8');
console.log(`Wrote Glyphworks_Fluid_Pipe.json`);

fs.writeFileSync(ITEM_PIPE_PATH, JSON.stringify(buildPipeItem({
  gridType: 'Item',
  transferRate: 0.2,
  itemId: 'Glyphworks_Item_Pipe',
  category: 'Glyphworks.Items',
  benchCategory: 'GlyphImprinter_Items',
  modelBasePath: 'Blocks/Glyphworks/Item/Pipe',
  extraComponents: {}
}, itemStateDefs), null, 2), 'utf8');
console.log(`Wrote Glyphworks_Item_Pipe.json with ${Object.keys(itemStateDefs).length} state definitions`);

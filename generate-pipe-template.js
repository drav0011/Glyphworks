/**
 * Using this for now, ideally will change to a model with conditional rendering of cubes in the models
 * or if not still using blockstates with the minimum amount of models rotating them in 3D to position correctly
 */

const fs = require('fs');
const path = require('path');

const BASE = __dirname;
const MODELS_DIR = path.join(BASE, 'src/main/resources/Common/Blocks/Glyphworks/Pipe');
const ITEM_PATH = path.join(BASE, 'src/main/resources/Server/Item/Items/Glyphworks/Pipe/Pipe.json');
const HITBOXES_DIR = path.join(BASE, 'src/main/resources/Server/Item/Block/Hitboxes/Glyphworks/Pipe');

const TEMPLATE_MODEL = path.join(MODELS_DIR, 'Pipe_Template.blockymodel');

// ─── Delete all existing models except the template ──────────────────────────
const oldFiles = fs.readdirSync(MODELS_DIR).filter(f => f !== 'Pipe_Template.blockymodel' && f !== 'Pipe.png');
for (const f of oldFiles) fs.unlinkSync(path.join(MODELS_DIR, f));
if (oldFiles.length) console.log(`Deleted ${oldFiles.length} old generated model files`);

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
// nodeIdx 3 is the -Z arm in the template model; nodeIdx 2 is the +Z arm.
const DIRECTIONS = [
  { name: 'N', bit: 5, position: { X: 0, Y: 0, Z: -1 }, nodeIdx: 3 },
  { name: 'S', bit: 4, position: { X: 0, Y: 0, Z: 1 }, nodeIdx: 2 },
  { name: 'E', bit: 3, position: { X: 1, Y: 0, Z: 0 }, nodeIdx: 5 },
  { name: 'W', bit: 2, position: { X: -1, Y: 0, Z: 0 }, nodeIdx: 4 },
  { name: 'U', bit: 1, position: { X: 0, Y: 1, Z: 0 }, nodeIdx: 1 },
  { name: 'D', bit: 0, position: { X: 0, Y: -1, Z: 0 }, nodeIdx: 6 },
];

const MODEL_TEXTURE = [{ Texture: 'Blocks/Glyphworks/Pipe/Pipe.png', Weight: 1 }];

const templateModel = JSON.parse(fs.readFileSync(TEMPLATE_MODEL, 'utf8'));

function shapeNameFor(mask) {
  if (mask === 0) return 'Single';
  return DIRECTIONS.filter(d => (mask >> d.bit) & 1).map(d => d.name).join('');
}

// ─── Generate all 64 combinations ────────────────────────────────────────────
const stateDefs = {};

for (let mask = 0; mask < 64; mask++) {
  const name = shapeNameFor(mask);

  // Blockymodel — clone Pipe_Template, toggle arm visibility
  const model = JSON.parse(JSON.stringify(templateModel));
  model.nodes[0].shape.visible = true; // center always on
  for (const dir of DIRECTIONS) {
    model.nodes[dir.nodeIdx].shape.visible = !!((mask >> dir.bit) & 1);
  }

  const modelFileName = `Pipe_${name}.blockymodel`;
  fs.writeFileSync(path.join(MODELS_DIR, modelFileName), JSON.stringify(model, null, 2), 'utf8');

  const hitboxBoxes = [CENTER_BOX];
  for (const dir of DIRECTIONS) {
    if ((mask >> dir.bit) & 1) hitboxBoxes.push(ARM_BOXES[dir.name]);
  }
  fs.writeFileSync(path.join(HITBOXES_DIR, `Pipe_${name}.json`), JSON.stringify({ Boxes: hitboxBoxes }, null, 2), 'utf8');

  stateDefs[name] = {
    HitboxType: `Pipe_${name}`,
    CustomModel: `Blocks/Glyphworks/Pipe/${modelFileName}`,
  };
}

console.log(`Generated ${Object.keys(stateDefs).length} .blockymodel files and hitboxes`);

// ─── Write Pipe.json from scratch ───────────────────────────────────────────
const item = {
  TranslationProperties: { Name: 'server.items.Pipe.name' },
  Categories: ['Blocks.Deco'],
  BlockType: {
    Material: 'Solid',
    DrawType: 'Model',
    BlockSoundSetId: 'Stone',
    Opacity: 'Transparent',
    ConnectedBlockRuleSet: { Type: 'Pipe' },
    CustomModel: 'Blocks/Glyphworks/Pipe/Pipe_Single.blockymodel',
    CustomModelTexture: MODEL_TEXTURE,
    BlockEntity: {
      Components: {
        "GridComponent": {
          "GridComponent_Entries": [
            {
              "GridTypeEntry_Type": "Item",
              "GridTypeEntry_Faces": [
                {
                  "FacePlane_Position": { "X": 0, "Y": 0, "Z": 0 },
                  "FacePlane_Normal": "South",
                  "FacePlane_Mode": "Bidirectional"
                },
                {
                  "FacePlane_Position": { "X": 0, "Y": 0, "Z": 0 },
                  "FacePlane_Normal": "North",
                  "FacePlane_Mode": "Bidirectional"
                },
                {
                  "FacePlane_Position": { "X": 0, "Y": 0, "Z": 0 },
                  "FacePlane_Normal": "East",
                  "FacePlane_Mode": "Bidirectional"
                },
                {
                  "FacePlane_Position": { "X": 0, "Y": 0, "Z": 0 },
                  "FacePlane_Normal": "West",
                  "FacePlane_Mode": "Bidirectional"
                },
                {
                  "FacePlane_Position": { "X": 0, "Y": 0, "Z": 0 },
                  "FacePlane_Normal": "Up",
                  "FacePlane_Mode": "Bidirectional"
                },
                {
                  "FacePlane_Position": { "X": 0, "Y": 0, "Z": 0 },
                  "FacePlane_Normal": "Down",
                  "FacePlane_Mode": "Bidirectional"
                }
              ]
            }
          ]
        }
      },
    },
    CustomModelScale: 1,
    HitboxType: 'Pipe',
    Flags: {},
    State: { Definitions: stateDefs },
  },
  PlayerAnimationsId: 'Block',
  Icon: 'Icons/ItemsGenerated/Deco_Cauldron_Big.png',
  Scale: 1,
};

fs.writeFileSync(ITEM_PATH, JSON.stringify(item, null, 2), 'utf8');
console.log(`Wrote Pipe.json with ${Object.keys(stateDefs).length} state definitions`);

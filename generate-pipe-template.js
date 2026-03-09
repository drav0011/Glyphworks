/**
 * Using this for now, ideally will change to a model with conditional rendering of cubes in the models
 * or if not still using blockstates with the minimum amount of models rotating them in 3D to position correctly
 */

const fs = require('fs');
const path = require('path');

const BASE        = __dirname;
const MODELS_DIR  = path.join(BASE, 'src/main/resources/Common/Blocks/Glyphworks/Pipe');
const ITEM_PATH   = path.join(BASE, 'src/main/resources/Server/Item/Items/Glyphworks/Pipe/Pipe.json');
const HITBOXES_DIR = path.join(BASE, 'src/main/resources/Server/Item/Block/Hitboxes/Glyphworks');

const TEMPLATE_MODEL = path.join(MODELS_DIR, 'Pipe_Template.blockymodel');
const OLD_ALL_MODEL  = path.join(MODELS_DIR, 'Pipe_All.blockymodel');

// ─── Rename Pipe_All → Pipe_Template if needed ───────────────────────────────
if (!fs.existsSync(TEMPLATE_MODEL) && fs.existsSync(OLD_ALL_MODEL)) {
  fs.renameSync(OLD_ALL_MODEL, TEMPLATE_MODEL);
  console.log('Renamed Pipe_All.blockymodel → Pipe_Template.blockymodel');
}

// ─── Delete all existing models except the template ──────────────────────────
const oldFiles = fs.readdirSync(MODELS_DIR).filter(f => f !== 'Pipe_Template.blockymodel' && f !== 'pipe.png');
for (const f of oldFiles) fs.unlinkSync(path.join(MODELS_DIR, f));
if (oldFiles.length) console.log(`Deleted ${oldFiles.length} old generated model files`);

// ─── Hitbox box definitions (block-local 0-1 coords derived from model nodes) ─
// Model space: X/Z ∈ [-16, +16], Y ∈ [0, 32]; pivot = box center + offset.
const CENTER_BOX = { Min: { X: 0.3125, Y: 0.3125, Z: 0.3125 }, Max: { X: 0.6875, Y: 0.6875, Z: 0.6875 } };
const ARM_BOXES = {
  S: { Min: { X: 0.375,  Y: 0.375,  Z: 0      }, Max: { X: 0.625,  Y: 0.625,  Z: 0.3125 } },
  N: { Min: { X: 0.375,  Y: 0.375,  Z: 0.6875 }, Max: { X: 0.625,  Y: 0.625,  Z: 1      } },
  E: { Min: { X: 0.6875, Y: 0.375,  Z: 0.375  }, Max: { X: 1,      Y: 0.625,  Z: 0.625  } },
  W: { Min: { X: 0,      Y: 0.375,  Z: 0.375  }, Max: { X: 0.3125, Y: 0.625,  Z: 0.625  } },
  U: { Min: { X: 0.375,  Y: 0.6875, Z: 0.375  }, Max: { X: 0.625,  Y: 1,      Z: 0.625  } },
  D: { Min: { X: 0.375,  Y: 0,      Z: 0.375  }, Max: { X: 0.625,  Y: 0.3125, Z: 0.625  } },
};

// ─── Clean up old hitbox files ────────────────────────────────────────────────
if (!fs.existsSync(HITBOXES_DIR)) fs.mkdirSync(HITBOXES_DIR, { recursive: true });
const oldHitboxFiles = fs.readdirSync(HITBOXES_DIR).filter(f => f.startsWith('Pipe_') && f.endsWith('.json'));
for (const f of oldHitboxFiles) fs.unlinkSync(path.join(HITBOXES_DIR, f));
if (oldHitboxFiles.length) console.log(`Deleted ${oldHitboxFiles.length} old hitbox files`);

// ─── Bit layout: bit5=N, bit4=S, bit3=E, bit2=W, bit1=U, bit0=D ─────────────
const DIRECTIONS = [
  { name: 'N', bit: 5, position: { X:  0, Y:  0, Z:  1 }, nodeIdx: 2 },
  { name: 'S', bit: 4, position: { X:  0, Y:  0, Z: -1 }, nodeIdx: 3 },
  { name: 'E', bit: 3, position: { X:  1, Y:  0, Z:  0 }, nodeIdx: 5 },
  { name: 'W', bit: 2, position: { X: -1, Y:  0, Z:  0 }, nodeIdx: 4 },
  { name: 'U', bit: 1, position: { X:  0, Y:  1, Z:  0 }, nodeIdx: 1 },
  { name: 'D', bit: 0, position: { X:  0, Y: -1, Z:  0 }, nodeIdx: 6 },
];

const MODEL_TEXTURE = [{ Texture: 'Blocks/Glyphworks/Pipe/pipe.png', Weight: 1 }];

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

  stateDefs[name] = {
    CustomModel: `Blocks/Glyphworks/Pipe/${modelFileName}`,
  };
}

console.log(`Generated ${Object.keys(stateDefs).length} .blockymodel files`);

// ─── Write single full hitbox (all arms always present) ───────────────────────
const fullHitboxBoxes = [CENTER_BOX, ...Object.values(ARM_BOXES)];
fs.writeFileSync(path.join(HITBOXES_DIR, 'Pipe.json'), JSON.stringify({ Boxes: fullHitboxBoxes }, null, 2), 'utf8');
console.log('Wrote Pipe.json hitbox (center + all 6 arms)');

// ─── Write Pipe.json from scratch ───────────────────────────────────────────
const item = {
  TranslationProperties: { Name: 'server.items.Pipe.name' },
  Categories: ['Blocks.Deco'],
  BlockType: {
    Material: 'Solid',
    DrawType: 'Model',
    BlockSoundSetId: 'Stone',
    Opacity: 'Transparent',
    CustomModel: 'Blocks/Glyphworks/Pipe/Pipe_Single.blockymodel',
    CustomModelTexture: MODEL_TEXTURE,
    BlockEntity: {
      Components: {
        TransferComponent: {
          Transfer_MaxOutputRate: 64,
          Transfer_MaxInputRate: 64,
          Transfer_AutoPush: false,
          Transfer_AutoPull: false,
          Transfer_DefaultFaceMode: 'Bidirectional',
          // Hitbox index layout in Pipe.json: 0=Center, 1=S(-Z), 2=N(+Z), 3=E(+X), 4=W(-X), 5=U(+Y), 6=D(-Y)
          Transfer_Faces: [
            { FacePlane_HitboxIndex: 1, FacePlane_RelMin: {X:0, Y:0, Z:0}, FacePlane_RelMax: {X:1, Y:1, Z:0}, FacePlane_Mode: 'Bidirectional' }, // S (-Z)
            { FacePlane_HitboxIndex: 2, FacePlane_RelMin: {X:0, Y:0, Z:1}, FacePlane_RelMax: {X:1, Y:1, Z:1}, FacePlane_Mode: 'Bidirectional' }, // N (+Z)
            { FacePlane_HitboxIndex: 3, FacePlane_RelMin: {X:1, Y:0, Z:0}, FacePlane_RelMax: {X:1, Y:1, Z:1}, FacePlane_Mode: 'Bidirectional' }, // E (+X)
            { FacePlane_HitboxIndex: 4, FacePlane_RelMin: {X:0, Y:0, Z:0}, FacePlane_RelMax: {X:0, Y:1, Z:1}, FacePlane_Mode: 'Bidirectional' }, // W (-X)
            { FacePlane_HitboxIndex: 5, FacePlane_RelMin: {X:0, Y:1, Z:0}, FacePlane_RelMax: {X:1, Y:1, Z:1}, FacePlane_Mode: 'Bidirectional' }, // U (+Y)
            { FacePlane_HitboxIndex: 6, FacePlane_RelMin: {X:0, Y:0, Z:0}, FacePlane_RelMax: {X:1, Y:0, Z:1}, FacePlane_Mode: 'Bidirectional' }, // D (-Y)
          ],
        },
      },
    },
    CustomModelScale: 1,
    HitboxType: 'Pipe',
    Flags: {},
    State: { Definitions: stateDefs },
    Interactions: { Secondary: 'Pipe_Use' },
  },
  PlayerAnimationsId: 'Block',
  Icon: 'Icons/ItemsGenerated/Deco_Cauldron_Big.png',
  Scale: 1,
};

fs.writeFileSync(ITEM_PATH, JSON.stringify(item, null, 2), 'utf8');
console.log(`Wrote Pipe.json with ${Object.keys(stateDefs).length} state definitions`);

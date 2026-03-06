const fs = require('fs');
const path = require('path');

const BASE        = __dirname;
const MODELS_DIR  = path.join(BASE, 'src/main/resources/Common/Blocks/Glyphworks/Pipe');
const TMPL_DIR    = path.join(BASE, 'src/main/resources/Server/Item/CustomConnectedBlockTemplates');
const ITEM_PATH   = path.join(BASE, 'src/main/resources/Server/Item/Items/Glyphworks/Pipe/Transfer_PipeNode.json');
const TMPL_OUT    = path.join(TMPL_DIR, 'PipeConnectedBlockTemplate.json');
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
  N: { Min: { X: 0.375,  Y: 0.375,  Z: 0      }, Max: { X: 0.625,  Y: 0.625,  Z: 0.3125 } },
  S: { Min: { X: 0.375,  Y: 0.375,  Z: 0.6875 }, Max: { X: 0.625,  Y: 0.625,  Z: 1      } },
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
  { name: 'N', bit: 5, position: { X:  0, Y:  0, Z: -1 }, oppositeFace: 'South', nodeIdx: 3 }, // north
  { name: 'S', bit: 4, position: { X:  0, Y:  0, Z:  1 }, oppositeFace: 'North', nodeIdx: 2 }, // south
  { name: 'E', bit: 3, position: { X:  1, Y:  0, Z:  0 }, oppositeFace: 'West',  nodeIdx: 5 }, // east
  { name: 'W', bit: 2, position: { X: -1, Y:  0, Z:  0 }, oppositeFace: 'East',  nodeIdx: 4 }, // west
  { name: 'U', bit: 1, position: { X:  0, Y:  1, Z:  0 }, oppositeFace: 'Down',  nodeIdx: 1 }, // up
  { name: 'D', bit: 0, position: { X:  0, Y: -1, Z:  0 }, oppositeFace: 'Up',    nodeIdx: 6 }, // down
];

const FACE_TAGS     = { North: ['PipeConnection'], South: ['PipeConnection'], East: ['PipeConnection'], West: ['PipeConnection'], Up: ['PipeConnection'], Down: ['PipeConnection'] };
const MODEL_TEXTURE = [{ Texture: 'Blocks/Glyphworks/Pipe/pipe.png', Weight: 1 }];

const templateModel = JSON.parse(fs.readFileSync(TEMPLATE_MODEL, 'utf8'));

function shapeNameFor(mask) {
  if (mask === 0) return 'Single';
  return DIRECTIONS.filter(d => (mask >> d.bit) & 1).map(d => d.name).join('');
}

// ─── Generate all 64 combinations ────────────────────────────────────────────
const shapes        = {};
const stateDefs     = {};
const shapePatterns = {};

for (let mask = 0; mask < 64; mask++) {
  const name = shapeNameFor(mask);

  // PipeConnectedBlockTemplate shape entry
  if (mask === 0) {
    shapes[name] = { FaceTags: { ...FACE_TAGS }, PatternsToMatchAnyOf: [] };
  } else {
    shapes[name] = {
      FaceTags: { ...FACE_TAGS },
      PatternsToMatchAnyOf: [{
        Type: 'Custom',
        TransformRulesToOrientation: false,
        RulesToMatch: DIRECTIONS.map(d => ({
          Position: d.position,
          IncludeOrExclude: ((mask >> d.bit) & 1) ? 'Include' : 'Exclude',
          FaceTags: { [d.oppositeFace]: ['PipeConnection'] },
        })),
      }],
    };
  }

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
    HitboxType:         `Pipe_${name}`,
    CustomModel:        `Blocks/Glyphworks/Pipe/${modelFileName}`,
    CustomModelTexture: MODEL_TEXTURE,
    DrawType:           'Model',
    Material:           'Solid',
  };

  shapePatterns[name] = `*Transfer_PipeNode_State_Definitions_${name}`;
}

console.log(`Generated ${Object.keys(stateDefs).length} .blockymodel files and hitboxes`);

// ─── Write PipeConnectedBlockTemplate.json ───────────────────────────────────
fs.writeFileSync(TMPL_OUT, JSON.stringify({
  MaterialName: 'Pipe',
  ConnectsToOtherMaterials: true,
  DefaultShape: 'Single',
  Shapes: shapes,
}, null, 2), 'utf8');
console.log('Wrote PipeConnectedBlockTemplate.json');

// ─── Write Transfer_PipeNode.json from scratch ───────────────────────────────
const item = {
  TranslationProperties: { Name: 'server.items.Transfer_PipeNode.name' },
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
        },
      },
    },
    CustomModelScale: 1,
    HitboxType: 'Pipe_Single',
    VariantRotation: 'Wall',
    Flags: {},
    FaceTags: {
      North: ['PipeConnection'],
      South: ['PipeConnection'],
      East:  ['PipeConnection'],
      West:  ['PipeConnection'],
      Up:    ['PipeConnection'],
      Down:  ['PipeConnection'],
    },
    State: { Definitions: stateDefs },
    ConnectedBlockRuleSet: {
      Type: 'CustomTemplate',
      TemplateShapeBlockPatterns: shapePatterns,
      TemplateShapeAssetId: 'PipeConnectedBlockTemplate',
    },
  },
  PlayerAnimationsId: 'Block',
  Icon: 'Icons/ItemsGenerated/Deco_Cauldron_Big.png',
  Scale: 1,
};

fs.writeFileSync(ITEM_PATH, JSON.stringify(item, null, 2), 'utf8');
console.log(`Wrote Transfer_PipeNode.json with ${Object.keys(stateDefs).length} state definitions`);

# Glyphworks — Gameplay Instructions

## Getting Started

Glyphworks begins inside **vanilla Hytale** using a **Tier 3 Workbench**. From there you unlock two crafting stations that gate everything else in the mod:

- **Rune Press Bench** — shapes raw materials into empty runes.
- **Glyph Imprinter Bench** — crafts Glyphworks machines, blocks, and components.

Once you have the Rune Press Bench, use it to produce **empty runes** (Fire, Ice, Life, Light, Void, Water). Take those to the **Imbuer Bench** — also crafted at the Glyph Imprinter — to imbue them into their filled counterparts. Imbued runes are the core crafting ingredient for everything else the mod adds.

---

## Liquid Mana

All automated machines require **liquid mana** to operate. Mana is a fluid that must be produced and piped to each machine.

To generate it:

1. Craft a **Mana Liquifier Machine** at the Glyph Imprinter.
2. Feed it **elemental essences** (any type) and fuel to melt them into mana.
3. Run fluid pipes from the Liquifier to every machine that needs mana.

Without a mana supply a machine will not process recipes even if its item inputs are full. It is used as the 

---

## Automated Crafting Machines

The mod provides automated versions of every vanilla crafting and processing benches (Furnace, Workbench, Tannery, and others), plus two Glyphworks-exclusive machines:

- **Rune Press Machine** — automated version of the Rune Press Bench.
- **Imbuer Machine** — automated version of the Imbuer Bench.

All automated machines share the same behaviour:

- They **pull inputs automatically** from any item pipe connected to an input face.
- They **push outputs automatically** into any item pipe connected to an output face.
- They consume **liquid mana** from a connected fluid pipe network to operate. The Mana Liquifier converts elemental essences into liquid mana using fuel items.

---

## Item Network

Item pipes connect machines, containers, extractors, and inserters into a network. Items move through the network each tick, routing toward the **closest reachable sink** first (by pipe hops).

### Blocks

| Block | Role |
|---|---|
| **Item Pipe** | Relay. Connects adjacent pipes automatically. No inventory. |
| **Item Extractor** | Pulls items from the inventory of the block directly below and feeds them into the network. |
| **Item Inserter** | Receives items from the network and pushes them into the inventory of the block directly below. |
| **Item Picker** | Collects item entities dropped on the ground within a 3-block radius and pushes them into the network. |
| **Item Dropper** | Ejects items from the network as dropped entities on the ground. |
| **Block Miner** | Automatically mines the block directly below and outputs the drops into the network. |
| **Block Placer** | Places blocks from the network directly below itself. |


### Extractors and Inserters

These two blocks let the item network interact with blocks that are not natively part of the network like chests.

- Place an **Extractor** above (or adjacent to) a non-network block. The extractor pulls items out of that block's inventory and pushes them into the connected pipe network.
- Place an **Inserter** above (or adjacent to) a non-network block. The inserter receives items from the pipe network and deposits them into that block's inventory.

---

## Fluid Network

Fluid pipes do not buffer items in discrete parcels. Instead, **all pipes and tanks connected in the same network act as a shared pool** — fluid equalises across the whole network rather than moving directionally hop-by-hop.

### Blocks

| Block | Role |
|---|---|
| **Fluid Pipe** | Network relay. Holds up to 500 mb as part of the shared pool. Connects to adjacent pipes automatically. |
| **Fluid Tank** | Storage. Holds up to 16,000 mb of any single fluid. |
| **Fluid Mana** | The liquid mana fluid type consumed by automated machines. |
| **Mana Liquifier Machine** | Converts elemental essences into liquid mana using fuel items. Outputs mana directly into a connected fluid network. |
| **Fluid Placer** | Places the stored fluid as a world block directly below. Pulls from the network. |
| **Fluid Remover** | Collects the fluid block directly below and pushes it into the network. |

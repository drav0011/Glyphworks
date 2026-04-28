<div align="center">

![Glyphworks](assets/images/title.png)

#### Rune inscribed - mana powered automation

</div>


Craft the **Glyph Imprinter** and **Bench Press** in the Workbench to start with the mod!

**Glyphworks** goal is to evolve alongside the game - modules, mechanics, and content will grow and change as Hytale does.

---

## Features

### Items
Automatic item transfer between containers and world blocks. Pipes, extractors, inserters, miners, and placers for routing items through your builds.

### Fluids
Richer fluid behavior and world interactions. Tanks, pipes, extractors, and inserters for moving fluids between blocks.

### Crafting
Automated crafting benches that pull from and push to connected inventories. Full fluid support in recipes lets machines consume or produce fluids as part of a craft.

---

## Roadmap

#### Beta *(current)*
- Core grid network system
- Item pipes, extractors, inserters, miners, and placers
- Fluid tanks, pipes, extractors, and inserters
- Automated crafting benches with fluid recipe support

#### Update 1: Foundations
- Fluid module and fluid grid polish
    - Separate FluidStacks into it's own NetworkSerializable and decouple item logic while keeping similar
    - Clearer indication pipes hold fluids (currently it might seems some fluid is lost when actually is in the pipe)
- Item transfer rework — pipes stay, but transfer logic gets more control and cleaner gameplay
    - Directionality, filters, splitters, mergers
- More animations and textures
- Testing infrastructure extracted from the release build
- UI? (Would prefer to wait until noesis)

#### Update 2: Assembly
- ???

#### Update 3: ???
- ???

#### Beyond: ???
- ???

---

## Build & Run

```bash
./gradlew build        # compile + jar
./gradlew runServer    # start local dev server
```

---

## Commands

### Debug

`/glyphworks:graph [--type <id>] [--verbose]`

Prints the in-memory grid graph for the current world. Without flags, shows a summary per registered type (node count, edge count, connected component count). `--verbose` lists every node and its neighbours.

### Test

```
/glyphworks:test run [--module=<name>] [--suite=<name>] [--test=<name>] [--no-cleanup]
/glyphworks:test purge
```

Aliases: `gw:test run`, `gw:test purge`. Operator-only.

`--no-cleanup` keeps the test world alive after the run so you can inspect it. Use `purge` to destroy all leftover test worlds.

---

## Testing

### Headless

Set `JAVA_TOOL_OPTIONS` before launching. The server exits automatically when the run finishes (`0` = all passed, `1` = any failure).

```powershell
# Run all tests
$env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.all=true" ; ./gradlew runServer ; Remove-Item Env:JAVA_TOOL_OPTIONS

# Run a specific module
$env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.module=smoke" ; ./gradlew runServer ; Remove-Item Env:JAVA_TOOL_OPTIONS

# Run a specific suite within a module
$env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.module=smoke -Dglyphworks.test.suite=mySuite" ; ./gradlew runServer ; Remove-Item Env:JAVA_TOOL_OPTIONS

# Run a single test
$env:JAVA_TOOL_OPTIONS="-Dglyphworks.test.module=smoke -Dglyphworks.test.suite=mySuite -Dglyphworks.test.name=myTest" ; ./gradlew runServer ; Remove-Item Env:JAVA_TOOL_OPTIONS
```
